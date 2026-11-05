package app.allinonereminder.crypto

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Streaming AEAD for photos (Tink AES-GCM-HKDF), so a large file never sits fully in memory.
 */
class PhotoCipher(private val key: HouseholdKey) {
    private val streaming = AesGcmHkdfStreaming(
        key.copyKeyBytes(),
        "HmacSha256",
        DERIVED_KEY_BYTES,
        CIPHERTEXT_SEGMENT_BYTES,
        0,
    )

    fun encrypt(plaintext: InputStream, ciphertext: OutputStream, aad: PhotoAad) {
        require(aad.keyVersion == key.version) {
            "associated data key version does not match the household key"
        }
        streaming.newEncryptingStream(ciphertext, aad.canonicalBytes()).use { out ->
            plaintext.copyTo(out)
        }
    }

    fun decrypt(ciphertext: InputStream, plaintext: OutputStream, aad: PhotoAad) {
        require(aad.keyVersion == key.version) {
            "associated data key version does not match the household key"
        }
        streaming.newDecryptingStream(ciphertext, aad.canonicalBytes()).use { input ->
            input.copyTo(plaintext)
        }
    }

    fun encryptBytes(plaintext: ByteArray, aad: PhotoAad): ByteArray {
        val out = ByteArrayOutputStream()
        encrypt(ByteArrayInputStream(plaintext), out, aad)
        return out.toByteArray()
    }

    fun decryptBytes(ciphertext: ByteArray, aad: PhotoAad): ByteArray {
        val out = ByteArrayOutputStream()
        decrypt(ByteArrayInputStream(ciphertext), out, aad)
        return out.toByteArray()
    }

    companion object {
        const val DERIVED_KEY_BYTES: Int = 32
        const val CIPHERTEXT_SEGMENT_BYTES: Int = 4096
    }
}
