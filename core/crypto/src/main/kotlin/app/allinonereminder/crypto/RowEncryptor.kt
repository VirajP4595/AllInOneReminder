package app.allinonereminder.crypto

import com.google.crypto.tink.subtle.AesGcmJce

/** An encrypted row as stored by the backend: a 12-byte GCM nonce plus ciphertext and tag. */
data class EncryptedRow(
    val nonce: ByteArray,
    val ciphertext: ByteArray,
) {
    init {
        require(nonce.size == NONCE_BYTES) { "nonce must be $NONCE_BYTES bytes" }
        require(ciphertext.isNotEmpty()) { "ciphertext must not be empty" }
    }

    companion object {
        const val NONCE_BYTES: Int = 12
    }
}

/**
 * Row encryption with Tink's AES-256-GCM.
 *
 * Tink returns `nonce || ciphertext || tag`; the nonce is split out so it can be stored in the
 * `sync_rows.nonce` column. Decryption reassembles the exact Tink wire format.
 */
class RowEncryptor(private val key: HouseholdKey) {
    private val aead: AesGcmJce = AesGcmJce(key.copyKeyBytes())

    fun encrypt(plaintext: ByteArray, aad: RowAad): EncryptedRow {
        require(aad.keyVersion == key.version) {
            "associated data key version does not match the household key"
        }
        val sealed = aead.encrypt(plaintext, aad.canonicalBytes())
        return EncryptedRow(
            nonce = sealed.copyOfRange(0, EncryptedRow.NONCE_BYTES),
            ciphertext = sealed.copyOfRange(EncryptedRow.NONCE_BYTES, sealed.size),
        )
    }

    fun decrypt(row: EncryptedRow, aad: RowAad): ByteArray {
        require(aad.keyVersion == key.version) {
            "associated data key version does not match the household key"
        }
        val sealed = row.nonce + row.ciphertext
        return aead.decrypt(sealed, aad.canonicalBytes())
    }
}
