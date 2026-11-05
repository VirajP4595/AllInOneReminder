package app.allinonereminder.crypto

import java.nio.charset.StandardCharsets

/**
 * Associated data for one encrypted row.
 *
 * The encoding is exactly `household_id|entity_type|row_id|version|key_version` as required by the
 * Phase 2 spec. A row copied to another id, entity, household or version therefore fails to decrypt.
 */
data class RowAad(
    val householdId: String,
    val entityType: String,
    val rowId: String,
    val version: Long,
    val keyVersion: Int,
) {
    init {
        require(householdId.isNotBlank()) { "householdId must not be blank" }
        require(entityType.isNotBlank()) { "entityType must not be blank" }
        require(rowId.isNotBlank()) { "rowId must not be blank" }
        require(version >= 0) { "version must be >= 0" }
        require(keyVersion >= 1) { "keyVersion must be >= 1" }
        require('|' !in householdId && '|' !in entityType && '|' !in rowId) {
            "associated data fields must not contain '|'"
        }
    }

    fun canonicalBytes(): ByteArray =
        "$householdId|$entityType|$rowId|$version|$keyVersion".toByteArray(StandardCharsets.UTF_8)
}

/** Associated data for an encrypted photo. */
data class PhotoAad(
    val householdId: String,
    val photoId: String,
    val keyVersion: Int,
) {
    init {
        require(householdId.isNotBlank()) { "householdId must not be blank" }
        require(photoId.isNotBlank()) { "photoId must not be blank" }
        require(keyVersion >= 1) { "keyVersion must be >= 1" }
        require('|' !in householdId && '|' !in photoId) {
            "associated data fields must not contain '|'"
        }
    }

    fun canonicalBytes(): ByteArray =
        "$householdId|$photoId|$keyVersion".toByteArray(StandardCharsets.UTF_8)
}
