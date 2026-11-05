package app.allinonereminder.crypto

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** BIP-39 errors. Messages never contain a mnemonic word, because words are key material. */
sealed class MnemonicException(message: String) : IllegalArgumentException(message) {
    class InvalidWordCount(val count: Int) : MnemonicException(
        "mnemonic must have 12, 15, 18, 21 or 24 words, not $count",
    )

    class UnknownWord(val index: Int) : MnemonicException(
        "mnemonic word at index $index is not in the BIP-39 English wordlist",
    )

    class InvalidEntropySize(val bytes: Int) : MnemonicException(
        "entropy must be 16, 20, 24, 28 or 32 bytes, not $bytes",
    )

    class ChecksumMismatch : MnemonicException("mnemonic checksum does not match")
}

/**
 * BIP-39 English mnemonic encoding. The wordlist is bundled at `src/main/resources/bip39-english.txt`.
 *
 * Only the checksum uses the JDK's SHA-256; all key encryption and key agreement in this module uses
 * Google Tink. No cryptographic primitive is hand-rolled.
 */
object Bip39 {
    private const val WORDLIST_RESOURCE = "/bip39-english.txt"
    private const val WORD_BITS = 11
    private const val WORD_COUNT = 2048
    private val VALID_ENTROPY_BYTES = setOf(16, 20, 24, 28, 32)

    private val wordlist: List<String> by lazy {
        val stream = Bip39::class.java.getResourceAsStream(WORDLIST_RESOURCE)
            ?: error("missing BIP-39 wordlist resource")
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        }.also { words ->
            check(words.size == WORD_COUNT) { "BIP-39 wordlist must contain $WORD_COUNT words" }
        }
    }

    fun encode(entropy: ByteArray): List<String> {
        if (entropy.size !in VALID_ENTROPY_BYTES) {
            throw MnemonicException.InvalidEntropySize(entropy.size)
        }
        val entropyBits = entropy.size * 8
        val checksumBitCount = entropyBits / 32
        val checksum = checksumBits(entropy)
        val totalBits = entropyBits + checksumBitCount
        val indices = IntArray(totalBits / WORD_BITS)
        for (word in indices.indices) {
            var value = 0
            for (offset in 0 until WORD_BITS) {
                val bit = word * WORD_BITS + offset
                val set = if (bit < entropyBits) {
                    bitAt(entropy, bit)
                } else {
                    (checksum ushr (checksumBitCount - (bit - entropyBits) - 1)) and 1
                }
                value = (value shl 1) or set
            }
            indices[word] = value
        }
        return indices.map { wordlist[it] }
    }

    fun decode(words: List<String>): ByteArray {
        if (words.size !in setOf(12, 15, 18, 21, 24)) {
            throw MnemonicException.InvalidWordCount(words.size)
        }
        val normalized = words.map { normalize(it) }
        val indices = IntArray(normalized.size) { position ->
            wordlist.indexOf(normalized[position]).also { index ->
                if (index < 0) throw MnemonicException.UnknownWord(position)
            }
        }
        val totalBits = normalized.size * WORD_BITS
        val checksumBitCount = totalBits / 33
        val entropyBits = totalBits - checksumBitCount
        val entropy = ByteArray(entropyBits / 8)
        for (bit in 0 until entropyBits) {
            if (bitAt(indices, bit) != 0) {
                entropy[bit / 8] = (entropy[bit / 8].toInt() or (1 shl (7 - bit % 8))).toByte()
            }
        }
        val expected = checksumBits(entropy)
        var actual = 0
        for (offset in 0 until checksumBitCount) {
            actual = (actual shl 1) or bitAt(indices, entropyBits + offset)
        }
        if (actual != expected) throw MnemonicException.ChecksumMismatch()
        return entropy
    }

    fun decode(mnemonic: String): ByteArray = decode(mnemonic.trim().split(WHITESPACE))

    private fun normalize(word: String): String =
        Normalizer.normalize(word, Normalizer.Form.NFKD).lowercase(Locale.ROOT)

    private fun checksumBits(entropy: ByteArray): Int {
        val hash = MessageDigest.getInstance("SHA-256").digest(entropy)
        val bits = entropy.size * 8 / 32
        return (hash[0].toInt() ushr (8 - bits)) and ((1 shl bits) - 1)
    }

    private fun bitAt(bytes: ByteArray, bit: Int): Int =
        (bytes[bit / 8].toInt() ushr (7 - bit % 8)) and 1

    private fun bitAt(indices: IntArray, bit: Int): Int =
        (indices[bit / WORD_BITS] ushr (WORD_BITS - 1 - bit % WORD_BITS)) and 1

    private val WHITESPACE = Regex("\\s+")
}

/**
 * The household key as 24 BIP-39 English words, with a QR payload for printing/scanning.
 *
 * The words are key material: [toString] deliberately omits them.
 */
class RecoveryPhrase private constructor(private val words: List<String>) {
    fun words(): List<String> = words.toList()

    fun toMnemonic(): String = words.joinToString(" ")

    fun toQrPayload(): String = "$QR_PREFIX:$QR_VERSION:${toMnemonic()}"

    override fun toString(): String = "RecoveryPhrase(${words.size} words)"

    companion object {
        const val WORD_COUNT: Int = 24
        const val QR_PREFIX: String = "airrecovery"
        const val QR_VERSION: String = "v1"

        fun fromKey(key: HouseholdKey): RecoveryPhrase =
            RecoveryPhrase(Bip39.encode(key.copyKeyBytes()))

        fun fromWords(mnemonic: String): RecoveryPhrase {
            val decoded = Bip39.decode(mnemonic)
            require(decoded.size == HouseholdKey.KEY_BYTES) {
                "recovery phrase must encode a 256-bit household key"
            }
            return RecoveryPhrase(Bip39.encode(decoded))
        }

        fun fromQrPayload(payload: String): RecoveryPhrase {
            val prefix = "$QR_PREFIX:$QR_VERSION:"
            require(payload.startsWith(prefix)) { "not a supported recovery QR payload" }
            return fromWords(payload.removePrefix(prefix))
        }

        fun restore(mnemonic: String, keyVersion: Int = 1): HouseholdKey {
            val entropy = Bip39.decode(mnemonic)
            require(entropy.size == HouseholdKey.KEY_BYTES) {
                "recovery phrase must encode a 256-bit household key"
            }
            return HouseholdKey.fromBytes(entropy, keyVersion)
        }
    }
}
