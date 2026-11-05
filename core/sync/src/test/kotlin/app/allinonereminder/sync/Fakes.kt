package app.allinonereminder.sync

import java.io.IOException
import java.nio.charset.StandardCharsets

class FakeRowCipher : RowCipher {
    override fun encrypt(plaintext: ByteArray, aad: RowAad): EncryptedRow {
        val prefix = canon(aad).toByteArray(StandardCharsets.UTF_8)
        return EncryptedRow(ByteArray(12) { 7 }, prefix + byteArrayOf(0) + plaintext)
    }

    override fun decrypt(row: EncryptedRow, aad: RowAad): ByteArray {
        val prefix = canon(aad).toByteArray(StandardCharsets.UTF_8)
        if (row.ciphertext.size < prefix.size + 1 ||
            !row.ciphertext.copyOfRange(0, prefix.size).contentEquals(prefix)
        ) {
            throw SecurityException("associated data does not match")
        }
        return row.ciphertext.copyOfRange(prefix.size + 1, row.ciphertext.size)
    }

    private fun canon(aad: RowAad): String =
        "${aad.householdId}|${aad.entityType}|${aad.rowId}|${aad.version}|${aad.keyVersion}"
}

class TestActionCodec : ActionCodec {
    override fun encode(action: OccurrenceAction): ByteArray = listOf(
        action.eventId,
        action.occurrenceRef,
        action.itemId,
        action.kind.name,
        action.actedAt.toString(),
        action.deviceId,
        action.duplicateOfEventId ?: "",
    ).joinToString("|").toByteArray(StandardCharsets.UTF_8)

    override fun decode(payload: ByteArray): OccurrenceAction {
        val parts = payload.decodeToString().split('|')
        return OccurrenceAction(
            eventId = parts[0],
            occurrenceRef = parts[1],
            itemId = parts[2],
            kind = ActionKind.valueOf(parts[3]),
            actedAt = parts[4].toLong(),
            deviceId = parts[5],
            duplicateOfEventId = parts[6].ifEmpty { null },
        )
    }
}

class InMemoryLocalStore(initial: List<LocalRow> = emptyList()) : LocalStore {
    private val rows = LinkedHashMap<RowKey, LocalRow>()
    private var cursor = 0L

    var failNextPullCommit: Boolean = false
    var commitCalls: Int = 0
        private set

    init {
        initial.forEach { rows[it.key] = it }
    }

    override fun dirtyRows(limit: Int): List<LocalRow> = rows.values
        .filter { it.dirty }
        .sortedWith(compareBy({ it.updatedAt }, { it.key.rowId }))
        .take(limit)

    override fun get(key: RowKey): LocalRow? = rows[key]

    override fun allRows(): List<LocalRow> = rows.values.toList()

    override fun upsert(row: LocalRow) {
        rows[row.key] = row
    }

    override fun markPushed(accepted: List<RemoteRow>) {
        accepted.forEach { acceptedRow ->
            val current = rows[acceptedRow.key] ?: return@forEach
            if (current.version == acceptedRow.version &&
                current.updatedAt == acceptedRow.updatedAt &&
                current.updatedByDevice == acceptedRow.updatedByDevice
            ) {
                rows[acceptedRow.key] = current.copy(dirty = false, serverSeq = acceptedRow.serverSeq)
            }
        }
    }

    override fun lastPulledSeq(): Long = cursor

    override fun markPullCommitted(cursor: Long) {
        if (failNextPullCommit) {
            failNextPullCommit = false
            throw IOException("simulated interruption before cursor commit")
        }
        this.cursor = cursor
        commitCalls++
    }

    override fun purge(key: RowKey) {
        rows.remove(key)
    }

    fun put(row: LocalRow) {
        rows[row.key] = row
    }

    fun cursorNow(): Long = cursor
}

class FakeServer : RemoteStore {
    private val rows = LinkedHashMap<RowKey, RemoteRow>()
    private val cursors = LinkedHashMap<String, Long>()
    private val seen = LinkedHashMap<String, Long?>()
    private var sequence = 0L
    var failPushAfter: Int? = null
    var failPullOnCall: Int? = null
    private var pullCalls = 0

    override fun push(rows: List<RemoteRow>): PushResult {
        val accepted = mutableListOf<RemoteRow>()
        val rejected = mutableListOf<RowKey>()
        var acceptedThisCall = 0
        rows.forEach { row ->
            val existing = this.rows[row.key]
            if (existing != null && row.version <= existing.version) {
                rejected += row.key
            } else {
                sequence++
                val stored = row.copy(serverSeq = sequence)
                this.rows[row.key] = stored
                accepted += stored
                acceptedThisCall++
                if (failPushAfter == acceptedThisCall) {
                    failPushAfter = null
                    throw IOException("simulated interruption during push")
                }
            }
        }
        return PushResult(accepted, rejected)
    }

    override fun pull(sinceSeq: Long, limit: Int): PullPage {
        pullCalls++
        if (failPullOnCall == pullCalls) {
            failPullOnCall = null
            throw IOException("simulated interruption during pull")
        }
        val page = rows.values
            .filter { it.serverSeq > sinceSeq }
            .sortedBy { it.serverSeq }
            .take(limit)
        val next = page.lastOrNull()?.serverSeq ?: sinceSeq
        val hasMore = rows.values.any { it.serverSeq > next }
        return PullPage(page, next, hasMore)
    }

    override fun deviceCursors(): List<DeviceCursor> = cursors.map { DeviceCursor(it.key, it.value, seen[it.key]) }

    fun recordCursor(deviceId: String, seq: Long, lastSeenAt: Long? = null) {
        cursors[deviceId] = seq
        seen[deviceId] = lastSeenAt
    }

    /**
     * Stores a row with an explicit `server_seq`, simulating a transaction that took its sequence
     * value early but committed after a higher one was already visible.
     */
    fun commitWithSeq(row: RemoteRow, seq: Long) {
        rows[row.key] = row.copy(serverSeq = seq)
        if (seq > sequence) sequence = seq
    }

    fun row(key: RowKey): RemoteRow? = rows[key]

    fun allRows(): List<RemoteRow> = rows.values.toList()

    fun sequence(): Long = sequence
}

fun testRow(
    rowId: String = "row-1",
    entityType: String = "item",
    householdId: String = "household-1",
    version: Long = 1,
    updatedAt: Long = 1_000,
    device: String = "device-a",
    payload: String = "value-1",
    dirty: Boolean = true,
    deleted: Boolean = false,
    serverSeq: Long = 0,
    keyVersion: Int = 1,
): LocalRow = LocalRow(
    key = RowKey(householdId, entityType, rowId),
    version = version,
    updatedAt = updatedAt,
    updatedByDevice = device,
    deleted = deleted,
    keyVersion = keyVersion,
    payload = payload.toByteArray(StandardCharsets.UTF_8),
    dirty = dirty,
    serverSeq = serverSeq,
)

fun actionRow(
    action: OccurrenceAction,
    version: Long = 1,
    updatedAt: Long = action.actedAt,
    dirty: Boolean = true,
): LocalRow = LocalRow(
    key = RowKey("household-1", EntityTypes.OCCURRENCE_ACTION, action.eventId),
    version = version,
    updatedAt = updatedAt,
    updatedByDevice = action.deviceId,
    deleted = false,
    keyVersion = 1,
    payload = TestActionCodec().encode(action),
    dirty = dirty,
)

fun engine(
    local: LocalStore,
    server: RemoteStore,
    deviceId: String,
    batchSize: Int = 200,
): SyncEngine = SyncEngine(local, server, FakeRowCipher(), TestActionCodec(), deviceId, batchSize)

fun payloadOf(row: LocalRow?): String = row?.payload?.decodeToString() ?: ""
