package app.allinonereminder.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecoveryPhraseTest {
    // Official BIP-39 test vectors (trezor/python-mnemonic vectors.json, entropy 000...).
    private val zeroTwelve = "abandon abandon abandon abandon abandon abandon " +
        "abandon abandon abandon abandon abandon about"
    private val zeroTwentyFour = (1..23).joinToString(" ") { "abandon" } + " art"

    @Test
    fun `matches the official 12 word BIP-39 vector`() {
        assertEquals(zeroTwelve, Bip39.encode(ByteArray(16)).joinToString(" "))
        assertArrayEquals(ByteArray(16), Bip39.decode(zeroTwelve))
    }

    @Test
    fun `matches the official 24 word BIP-39 vector`() {
        assertEquals(zeroTwentyFour, Bip39.encode(ByteArray(32)).joinToString(" "))
        assertArrayEquals(ByteArray(32), Bip39.decode(zeroTwentyFour))
    }

    @Test
    fun `recovery phrase round trips through words and QR`() {
        val key = HouseholdKey.fromBytes(ByteArray(32) { (it * 11 + 1).toByte() }, version = 9)
        val phrase = RecoveryPhrase.fromKey(key)

        assertEquals(24, phrase.words().size)
        assertEquals(24, phrase.toMnemonic().split(" ").size)
        assertArrayEquals(key.copyKeyBytes(), RecoveryPhrase.restore(phrase.toMnemonic(), 9).copyKeyBytes())
        assertEquals(9, RecoveryPhrase.restore(phrase.toMnemonic(), 9).version)
        assertEquals(phrase.toMnemonic(), RecoveryPhrase.fromWords(phrase.toMnemonic()).toMnemonic())
        assertEquals(phrase.toMnemonic(), RecoveryPhrase.fromQrPayload(phrase.toQrPayload()).toMnemonic())
    }

    @Test
    fun `a one word typo that is still a wordlist word fails the checksum`() {
        val words = RecoveryPhrase.fromKey(HouseholdKey.fromBytes(ByteArray(32))).words().toMutableList()
        // "abandon" is a valid word, so this must fail on checksum rather than wordlist lookup.
        words[words.lastIndex] = "abandon"

        assertThrows(MnemonicException.ChecksumMismatch::class.java) {
            Bip39.decode(words)
        }
    }

    @Test
    fun `an unknown word is rejected without including the word in the error`() {
        val words = RecoveryPhrase.fromKey(HouseholdKey.fromBytes(ByteArray(32))).words().toMutableList()
        words[4] = "notaword"

        val error = assertThrows(MnemonicException.UnknownWord::class.java) { Bip39.decode(words) }
        assertEquals(4, error.index)
        assertFalse(error.message!!.contains("notaword"))
    }

    @Test
    fun `rejects an invalid word count`() {
        val error = assertThrows(MnemonicException.InvalidWordCount::class.java) {
            Bip39.decode("abandon ability able")
        }
        assertEquals(3, error.count)
    }

    @Test
    fun `rejects invalid entropy sizes`() {
        val error = assertThrows(MnemonicException.InvalidEntropySize::class.java) {
            Bip39.encode(ByteArray(17))
        }
        assertEquals(17, error.bytes)
    }

    @Test
    fun `rejects a 12 word phrase as a household recovery phrase`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryPhrase.fromWords(zeroTwelve)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryPhrase.restore(zeroTwelve)
        }
    }

    @Test
    fun `rejects a malformed recovery QR payload`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryPhrase.fromQrPayload("airpair:v1:123")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryPhrase.fromQrPayload("airrecovery:v2:$zeroTwentyFour")
        }
    }

    @Test
    fun `normalises case and whitespace`() {
        val normalized = zeroTwentyFour.uppercase().replace(" ", "   ")

        assertArrayEquals(ByteArray(32), Bip39.decode(normalized))
        assertEquals(zeroTwentyFour, RecoveryPhrase.fromWords(normalized).toMnemonic())
    }

    @Test
    fun `does not expose the words in toString`() {
        val phrase = RecoveryPhrase.fromKey(HouseholdKey.fromBytes(ByteArray(32)))
        val text = phrase.toString()

        assertTrue(text.contains("24 words"))
        assertFalse(text.contains(phrase.words().first()) && text.contains(phrase.words().last()))
    }
}
