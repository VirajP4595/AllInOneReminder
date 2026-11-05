package app.allinonereminder.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException

class RotationTest {
    private val oldKey = HouseholdKey.fromBytes(ByteArray(32) { 1 }, version = 1)
    private val newKey = HouseholdKey.fromBytes(ByteArray(32) { 2 }, version = 2)

    private fun encryptedRow(rowId: String, plaintext: String, version: Long = 1): EncryptedRowWithAad {
        val aad = RowAad("household-1", "item", rowId, version, keyVersion = oldKey.version)
        return EncryptedRowWithAad(aad, RowEncryptor(oldKey).encrypt(plaintext.toByteArray(), aad))
    }

    @Test
    fun `rotation re-encrypts every row for the next key version`() {
        val rows = listOf(
            encryptedRow("row-1", "first", version = 1),
            encryptedRow("row-2", "second", version = 4),
            encryptedRow("row-3", "third", version = 9),
        )

        val rotated = HouseholdKeyRotation.rotateRows(oldKey, newKey, rows)

        assertEquals(3, rotated.size)
        val newReader = RowEncryptor(newKey)
        rows.zip(rotated).forEachIndexed { index, (old, new) ->
            assertEquals(old.aad.rowId, new.aad.rowId)
            assertEquals(old.aad.version, new.aad.version)
            assertEquals(2, new.aad.keyVersion)
            assertEquals(listOf("first", "second", "third")[index], newReader.decrypt(new.row, new.aad).decodeToString())
        }
    }

    @Test
    fun `lazy rotation works over a sequence`() {
        val rows = sequenceOf(encryptedRow("row-1", "first"), encryptedRow("row-2", "second"))

        val rotated = HouseholdKeyRotation.rotateRows(oldKey, newKey, rows).toList()

        assertEquals(2, rotated.size)
        assertEquals("second", RowEncryptor(newKey).decrypt(rotated[1].row, rotated[1].aad).decodeToString())
    }

    @Test
    fun `old ciphertext is rejected under the new key`() {
        val oldRow = encryptedRow("row-1", "first")
        val rotated = HouseholdKeyRotation.rotateRows(oldKey, newKey, listOf(oldRow)).single()

        assertThrows(GeneralSecurityException::class.java) {
            RowEncryptor(newKey).decrypt(oldRow.row, oldRow.aad.copy(keyVersion = 2))
        }
        assertThrows(GeneralSecurityException::class.java) {
            RowEncryptor(newKey).decrypt(rotated.row, rotated.aad.copy(version = 2))
        }
    }

    @Test
    fun `rotation refuses to skip or repeat a key version`() {
        val sameVersion = HouseholdKey.fromBytes(ByteArray(32) { 3 }, version = 1)
        val farVersion = HouseholdKey.fromBytes(ByteArray(32) { 4 }, version = 3)

        assertThrows(IllegalArgumentException::class.java) {
            HouseholdKeyRotation.rotateRows(oldKey, sameVersion, listOf(encryptedRow("row", "x")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HouseholdKeyRotation.rotateRows(oldKey, farVersion, listOf(encryptedRow("row", "x")))
        }
    }

    @Test
    fun `rotation fails on a tampered row instead of silently skipping it`() {
        val row = encryptedRow("row-1", "first")
        val tampered = row.copy(
            row = row.row.copy(
                ciphertext = row.row.ciphertext.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() },
            ),
        )

        assertThrows(GeneralSecurityException::class.java) {
            HouseholdKeyRotation.rotateRows(oldKey, newKey, listOf(tampered)).toList()
        }
    }

    @Test
    fun `rotation preserves plaintext exactly`() {
        val payload = ByteArray(300) { (it % 127).toByte() }
        val aad = RowAad("household-1", "profile", "row-bytes", 2, keyVersion = 1)
        val row = EncryptedRowWithAad(aad, RowEncryptor(oldKey).encrypt(payload, aad))

        val rotated = HouseholdKeyRotation.rotateRows(oldKey, newKey, sequenceOf(row)).single()

        assertArrayEquals(payload, RowEncryptor(newKey).decrypt(rotated.row, rotated.aad))
    }
}
