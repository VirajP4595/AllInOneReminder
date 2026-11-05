package app.allinonereminder.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException

class RowEncryptorTest {
    private val key = HouseholdKey.fromBytes(ByteArray(32) { it.toByte() }, version = 3)
    private val aad = RowAad("household-1", "item", "row-1", version = 7, keyVersion = 3)
    private val encryptor = RowEncryptor(key)

    @Test
    fun `round trips a row`() {
        val plaintext = "Metformin 500 mg".toByteArray()
        val encrypted = encryptor.encrypt(plaintext, aad)

        assertArrayEquals(plaintext, encryptor.decrypt(encrypted, aad))
        assertEquals(EncryptedRow.NONCE_BYTES, encrypted.nonce.size)
        assertFalse(encrypted.nonce.contentEquals(encrypted.ciphertext))
    }

    @Test
    fun `round trips an empty row`() {
        val encrypted = encryptor.encrypt(ByteArray(0), aad)
        assertArrayEquals(ByteArray(0), encryptor.decrypt(encrypted, aad))
    }

    @Test
    fun `uses a fresh nonce for every encryption`() {
        val first = encryptor.encrypt("same".toByteArray(), aad)
        val second = encryptor.encrypt("same".toByteArray(), aad)

        assertFalse(first.nonce.contentEquals(second.nonce))
        assertFalse(first.ciphertext.contentEquals(second.ciphertext))
    }

    @Test
    fun `rejects swapped row id`() {
        val encrypted = encryptor.encrypt("secret".toByteArray(), aad)
        assertThrows(GeneralSecurityException::class.java) {
            encryptor.decrypt(encrypted, aad.copy(rowId = "row-2"))
        }
    }

    @Test
    fun `rejects changed version`() {
        val encrypted = encryptor.encrypt("secret".toByteArray(), aad)
        assertThrows(GeneralSecurityException::class.java) {
            encryptor.decrypt(encrypted, aad.copy(version = 8))
        }
    }

    @Test
    fun `rejects another household`() {
        val encrypted = encryptor.encrypt("secret".toByteArray(), aad)
        assertThrows(GeneralSecurityException::class.java) {
            encryptor.decrypt(encrypted, aad.copy(householdId = "household-2"))
        }
    }

    @Test
    fun `rejects another entity type`() {
        val encrypted = encryptor.encrypt("secret".toByteArray(), aad)
        assertThrows(GeneralSecurityException::class.java) {
            encryptor.decrypt(encrypted, aad.copy(entityType = "profile"))
        }
    }

    @Test
    fun `rejects tampered ciphertext`() {
        val encrypted = encryptor.encrypt("secret".toByteArray(), aad)
        val tampered = encrypted.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertThrows(GeneralSecurityException::class.java) {
            encryptor.decrypt(encrypted.copy(ciphertext = tampered), aad)
        }
    }

    @Test
    fun `rejects tampered nonce`() {
        val encrypted = encryptor.encrypt("secret".toByteArray(), aad)
        val tampered = encrypted.nonce.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }

        assertThrows(GeneralSecurityException::class.java) {
            encryptor.decrypt(encrypted.copy(nonce = tampered), aad)
        }
    }

    @Test
    fun `rejects mismatched key version before touching ciphertext`() {
        val encrypted = encryptor.encrypt("secret".toByteArray(), aad)

        assertThrows(IllegalArgumentException::class.java) {
            encryptor.decrypt(encrypted, aad.copy(keyVersion = 4))
        }
        assertThrows(IllegalArgumentException::class.java) {
            encryptor.encrypt("secret".toByteArray(), aad.copy(keyVersion = 4))
        }
    }

    @Test
    fun `validates the household key`() {
        assertThrows(IllegalArgumentException::class.java) { HouseholdKey.fromBytes(ByteArray(31)) }
        assertThrows(IllegalArgumentException::class.java) { HouseholdKey.fromBytes(ByteArray(32), 0) }
        assertEquals("HouseholdKey(version=3)", key.toString())
    }

    @Test
    fun `rotates to fresh key material`() {
        val next = key.rotate()

        assertEquals(4, next.version)
        assertFalse(key.copyKeyBytes().contentEquals(next.copyKeyBytes()))
    }

    @Test
    fun `validates associated data`() {
        assertThrows(IllegalArgumentException::class.java) { RowAad(" ", "item", "row", 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { RowAad("h", " ", "row", 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { RowAad("h", "item", " ", 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { RowAad("h", "item", "row", -1, 1) }
        assertThrows(IllegalArgumentException::class.java) { RowAad("h", "item", "row", 1, 0) }
        assertThrows(IllegalArgumentException::class.java) { RowAad("h|1", "item", "row", 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { PhotoAad(" ", "photo", 1) }
        assertThrows(IllegalArgumentException::class.java) { PhotoAad("h", " ", 1) }
        assertThrows(IllegalArgumentException::class.java) { PhotoAad("h", "photo", 0) }
        assertThrows(IllegalArgumentException::class.java) { PhotoAad("h|1", "photo", 1) }
    }

    @Test
    fun `rejects malformed encrypted rows`() {
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedRow(ByteArray(11), ByteArray(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedRow(ByteArray(12), ByteArray(0))
        }
    }
}
