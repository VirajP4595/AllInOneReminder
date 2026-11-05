package app.allinonereminder.crypto

/** An encrypted row together with the associated data it was written with. */
data class EncryptedRowWithAad(
    val aad: RowAad,
    val row: EncryptedRow,
)

/** A row re-encrypted for the next household key version. */
data class RotatedRow(
    val aad: RowAad,
    val row: EncryptedRow,
)

/**
 * Pure key-rotation function used when a member is removed.
 *
 * It decrypts every row with the current key and re-encrypts it with the next key version. The
 * returned sequence is lazy, so callers can stream a large household through it without holding all
 * plaintext at once. Nothing is mutated in place.
 */
object HouseholdKeyRotation {
    fun rotateRows(
        currentKey: HouseholdKey,
        nextKey: HouseholdKey,
        rows: Sequence<EncryptedRowWithAad>,
    ): Sequence<RotatedRow> {
        require(nextKey.version == currentKey.version + 1) {
            "next key version must be exactly one greater than the current version"
        }
        val reader = RowEncryptor(currentKey)
        val writer = RowEncryptor(nextKey)
        return rows.map { encrypted ->
            val plaintext = reader.decrypt(encrypted.row, encrypted.aad)
            val nextAad = encrypted.aad.copy(keyVersion = nextKey.version)
            RotatedRow(nextAad, writer.encrypt(plaintext, nextAad))
        }
    }

    fun rotateRows(
        currentKey: HouseholdKey,
        nextKey: HouseholdKey,
        rows: List<EncryptedRowWithAad>,
    ): List<RotatedRow> = rotateRows(currentKey, nextKey, rows.asSequence()).toList()
}
