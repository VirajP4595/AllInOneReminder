package app.allinonereminder.sync

/** Identity of one synced row. */
data class RowKey(
    val householdId: String,
    val entityType: String,
    val rowId: String,
) {
    init {
        require(householdId.isNotBlank() && entityType.isNotBlank() && rowId.isNotBlank()) {
            "row key fields must not be blank"
        }
    }
}

/** Associated data, mirroring `/core/crypto`'s `RowAad`. */
data class RowAad(
    val householdId: String,
    val entityType: String,
    val rowId: String,
    val version: Long,
    val keyVersion: Int,
)

/** Encrypted payload, mirroring `/core/crypto`'s `EncryptedRow`. */
data class EncryptedRow(
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

/**
 * A row as held locally. `payload` is plaintext; the store never sees ciphertext for local writes.
 *
 * Every local write must bump [version] and set [dirty] to true (Track B's Room implementation owns
 * that rule; this module provides the outbox and the merge).
 */
data class LocalRow(
    val key: RowKey,
    val version: Long,
    val updatedAt: Long,
    val updatedByDevice: String,
    val deleted: Boolean,
    val keyVersion: Int,
    val payload: ByteArray,
    val dirty: Boolean,
    val serverSeq: Long = 0,
)

/** An encrypted row as stored on the backend. */
data class RemoteRow(
    val key: RowKey,
    val version: Long,
    val updatedAt: Long,
    val updatedByDevice: String,
    val deleted: Boolean,
    val keyVersion: Int,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val serverSeq: Long,
)

/** A device's pull position, used by tombstone purging. */
data class DeviceCursor(
    val deviceId: String,
    val lastPullSeq: Long,
    /** Epoch millis of the device's last sync, or null when the server does not know. */
    val lastSeenAt: Long? = null,
)

enum class ActionKind {
    TAKEN,
    SKIPPED,
    DUPLICATE_ACTION,
}

/**
 * Append-only occurrence event. Event ids are UUIDs, so two devices can never overwrite each other;
 * the earliest TAKEN/SKIPPED per occurrence wins and later actions are shown as DUPLICATE_ACTION.
 */
data class OccurrenceAction(
    val eventId: String,
    val occurrenceRef: String,
    val itemId: String,
    val kind: ActionKind,
    val actedAt: Long,
    val deviceId: String,
    val duplicateOfEventId: String? = null,
) {
    init {
        require(eventId.isNotBlank() && occurrenceRef.isNotBlank() && itemId.isNotBlank()) {
            "action ids must not be blank"
        }
        require(actedAt >= 0) { "actedAt must be >= 0" }
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
    }
}

/** Entity types that are append-only and merged by event id instead of last-writer-wins. */
object EntityTypes {
    const val OCCURRENCE_ACTION = "occurrence_action"
    const val CHANGE_LOG = "change_log"
}
