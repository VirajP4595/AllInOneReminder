package app.allinonereminder.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class PhotoCipherTest {
    private val key = HouseholdKey.fromBytes(ByteArray(32) { (it * 3).toByte() }, version = 2)
    private val cipher = PhotoCipher(key)
    private val aad = PhotoAad("household-1", "photo-1", keyVersion = 2)

    @Test
    fun `round trips a small photo`() {
        val photo = ByteArray(200) { (it % 251).toByte() }
        assertArrayEquals(photo, cipher.decryptBytes(cipher.encryptBytes(photo, aad), aad))
    }

    @Test
    fun `round trips a photo larger than one segment`() {
        val photo = ByteArray(PhotoCipher.CIPHERTEXT_SEGMENT_BYTES * 5 + 17) { (it * 31).toByte() }
        val encrypted = cipher.encryptBytes(photo, aad)

        assertArrayEquals(photo, cipher.decryptBytes(encrypted, aad))
    }

    @Test
    fun `streams through caller supplied streams`() {
        val photo = "a photo".toByteArray()
        val encrypted = ByteArrayOutputStream()
        cipher.encrypt(ByteArrayInputStream(photo), encrypted, aad)

        val decrypted = ByteArrayOutputStream()
        cipher.decrypt(ByteArrayInputStream(encrypted.toByteArray()), decrypted, aad)

        assertArrayEquals(photo, decrypted.toByteArray())
    }

    @Test
    fun `round trips an empty photo`() {
        assertArrayEquals(ByteArray(0), cipher.decryptBytes(cipher.encryptBytes(ByteArray(0), aad), aad))
    }

    @Test
    fun `rejects tampered photo ciphertext`() {
        val encrypted = cipher.encryptBytes(ByteArray(64) { 1 }, aad)
        encrypted[encrypted.size / 2] = (encrypted[encrypted.size / 2].toInt() xor 1).toByte()

        assertThrows(IOException::class.java) {
            cipher.decryptBytes(encrypted, aad)
        }
    }

    @Test
    fun `rejects the wrong photo associated data`() {
        val encrypted = cipher.encryptBytes(ByteArray(64) { 1 }, aad)

        assertThrows(IOException::class.java) {
            cipher.decryptBytes(encrypted, aad.copy(photoId = "photo-2"))
        }
    }

    @Test
    fun `rejects a mismatched key version`() {
        assertThrows(IllegalArgumentException::class.java) {
            cipher.encryptBytes(ByteArray(1), aad.copy(keyVersion = 3))
        }
        assertThrows(IllegalArgumentException::class.java) {
            cipher.decryptBytes(ByteArray(1), aad.copy(keyVersion = 3))
        }
    }
}
