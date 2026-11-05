package app.allinonereminder.sync

/**
 * Outbox/pull sync engine.
 *
 * - Push sends dirty rows in batches; the server rejects stale versions.
 * - A rejection triggers a pull and merge, then a retry with a bumped local version when the local
 *   row still wins last-writer-wins.
 * - Pull decrypts every row of a page before applying it and advances the cursor only after the
 *   local commit, so an interrupted pull can safely replay.
 * - Occurrence actions and change logs are append-only and merged by event id.
 */
class SyncEngine(
    private val local: LocalStore,
    private val remote: RemoteStore,
    private val cipher: RowCipher,
    private val actionCodec: ActionCodec,
    private val deviceId: String,
    private val batchSize: Int = 200,
    private val pullOverlap: Long = DEFAULT_PULL_OVERLAP,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
        require(batchSize > 0) { "batchSize must be > 0" }
        require(pullOverlap >= 0) { "pullOverlap must be >= 0" }
    }

    fun pushOnce(): PushStats {
        var pushed = 0
        var rejected = 0
        var rounds = 0
        while (rounds < MAX_PUSH_ROUNDS) {
            val dirty = local.dirtyRows(batchSize)
            if (dirty.isEmpty()) break
            val before = signature(dirty)
            val result = remote.push(dirty.map(::toRemote))
            local.markPushed(result.accepted)
            pushed += result.accepted.size
            rejected += result.rejected.size
            rounds++
            if (result.rejected.isEmpty()) continue

            pullOnce()
            if (signature(local.dirtyRows(batchSize)) == before) break
        }
        return PushStats(pushed, rejected, rounds)
    }

    /**
     * Pulls from `committed - pullOverlap`, not from `committed`. `server_seq` is assigned when a
     * server transaction starts, not when it commits, so a row with a lower seq can become visible
     * after a higher one was already pulled. Re-reading a small overlap window picks those rows up;
     * replays are no-ops because the merge is idempotent. Only rows that are new or actually change
     * local state count as `applied`.
     */
    fun pullOnce(): PullStats {
        val committed = local.lastPulledSeq()
        var cursor = maxOf(0L, committed - pullOverlap)
        var applied = 0
        var pages = 0
        var high = committed
        while (true) {
            val page = remote.pull(cursor, batchSize)
            check(page.nextSeq >= cursor) { "remote cursor moved backwards" }
            val decrypted = page.rows.map { row -> row to cipher.decrypt(ciphertext(row), aad(row)) }
            decrypted.forEach { (row, payload) ->
                val changed = applyRemote(row, payload)
                if (changed || row.serverSeq > committed) applied++
            }
            high = maxOf(high, page.nextSeq)
            local.markPullCommitted(high)
            pages++
            val previous = cursor
            cursor = page.nextSeq
            if (!page.hasMore || page.rows.isEmpty()) break
            check(cursor > previous) { "remote page did not advance the cursor" }
        }
        return PullStats(applied, pages, high)
    }

    fun syncOnce(): SyncStats {
        val push = pushOnce()
        val pull = pullOnce()
        return SyncStats(push.pushed, pull.applied, push.rounds)
    }

    /** Push/pull until no dirty rows and no new remote rows, bounded by [maxRounds]. */
    fun syncUntilIdle(maxRounds: Int = 10): SyncStats {
        require(maxRounds > 0) { "maxRounds must be > 0" }
        var pushed = 0
        var pulled = 0
        var totalPushRounds = 0
        repeat(maxRounds) {
            val push = pushOnce()
            val pull = pullOnce()
            pushed += push.pushed
            pulled += pull.applied
            totalPushRounds += push.rounds
            if (local.dirtyRows(1).isEmpty() && pull.applied == 0 && push.rejected == 0) {
                return SyncStats(pushed, pulled, totalPushRounds)
            }
        }
        return SyncStats(pushed, pulled, totalPushRounds)
    }

    /** All occurrence events known locally, including events pulled from other devices. */
    fun actionLog(): ActionLog = ActionLog(
        local.allRows()
            .filter { it.key.entityType == EntityTypes.OCCURRENCE_ACTION }
            .map { actionCodec.decode(it.payload) },
    )

    /** Cross-device double-dose guard input. */
    fun lastTakenAt(itemId: String): Long? = actionLog().lastTakenAt(itemId)

    /**
     * Purges tombstones older than 30 days only when every active device has pulled past the
     * tombstone's `server_seq`.
     */
    fun purgeTombstones(now: Long = clock()): Int {
        // A device that has not synced for DEVICE_SILENCE_MILLIS no longer holds tombstones back; if
        // it ever returns it must do a full resync instead of an incremental pull.
        val active = remote.deviceCursors().filter { cursor ->
            cursor.lastSeenAt == null || now - cursor.lastSeenAt <= DEVICE_SILENCE_MILLIS
        }
        val minimumCursor = (active.map { it.lastPullSeq } + local.lastPulledSeq()).min()
        val cutoff = now - TOMBSTONE_RETENTION_MILLIS
        val candidates = local.allRows().filter { row ->
            row.deleted &&
                row.serverSeq > 0 &&
                row.updatedAt <= cutoff &&
                row.serverSeq <= minimumCursor
        }
        candidates.forEach { local.purge(it.key) }
        return candidates.size
    }

    private fun applyRemote(remoteRow: RemoteRow, payload: ByteArray): Boolean {
        val existing = local.get(remoteRow.key)
        when (remoteRow.key.entityType) {
            EntityTypes.OCCURRENCE_ACTION, EntityTypes.CHANGE_LOG -> {
                // Append-only: an event id is immutable. Never overwrite an existing event.
                if (existing == null) {
                    local.upsert(remoteToLocal(remoteRow, payload, dirty = false))
                    true
                } else {
                    false
                }
            }

            else -> mergeLastWriterWins(existing, remoteRow, payload)
        }
    }

    private fun mergeLastWriterWins(existing: LocalRow?, remoteRow: RemoteRow, payload: ByteArray): Boolean {
        if (existing == null) {
            local.upsert(remoteToLocal(remoteRow, payload, dirty = false))
            return true
        }
        val order = compare(existing, remoteRow)
        return when {
            order < 0 -> {
                local.upsert(remoteToLocal(remoteRow, payload, dirty = false))
                true
            }
            order == 0 -> if (existing.dirty) {
                local.upsert(existing.copy(dirty = false, serverSeq = remoteRow.serverSeq))
                true
            } else if (existing.serverSeq != remoteRow.serverSeq) {
                local.upsert(existing.copy(serverSeq = remoteRow.serverSeq))
                true
            } else {
                false
            }

            else -> if (existing.dirty && existing.version <= remoteRow.version) {
                // The local value wins LWW but the server rejected it as stale. Bumping the version
                // makes the next push acceptable without discarding the local edit.
                local.upsert(existing.copy(version = remoteRow.version + 1))
                true
            } else {
                false
            }
        }
    }

    private fun toRemote(row: LocalRow): RemoteRow {
        val aad = aad(row.key, row.version, row.keyVersion)
        val encrypted = cipher.encrypt(row.payload, aad)
        return RemoteRow(
            key = row.key,
            version = row.version,
            updatedAt = row.updatedAt,
            updatedByDevice = row.updatedByDevice,
            deleted = row.deleted,
            keyVersion = row.keyVersion,
            nonce = encrypted.nonce,
            ciphertext = encrypted.ciphertext,
            serverSeq = 0,
        )
    }

    private fun remoteToLocal(row: RemoteRow, payload: ByteArray, dirty: Boolean): LocalRow = LocalRow(
        key = row.key,
        version = row.version,
        updatedAt = row.updatedAt,
        updatedByDevice = row.updatedByDevice,
        deleted = row.deleted,
        keyVersion = row.keyVersion,
        payload = payload,
        dirty = dirty,
        serverSeq = row.serverSeq,
    )

    private fun ciphertext(row: RemoteRow): EncryptedRow = EncryptedRow(row.nonce, row.ciphertext)

    private fun aad(row: RemoteRow): RowAad =
        aad(row.key, row.version, row.keyVersion)

    private fun aad(key: RowKey, version: Long, keyVersion: Int): RowAad =
        RowAad(key.householdId, key.entityType, key.rowId, version, keyVersion)

    /** Total order `(version, updated_at, device_id)`, as required by the spec. */
    private fun compare(localRow: LocalRow, remoteRow: RemoteRow): Int {
        val version = localRow.version.compareTo(remoteRow.version)
        if (version != 0) return version
        val updatedAt = localRow.updatedAt.compareTo(remoteRow.updatedAt)
        if (updatedAt != 0) return updatedAt
        return localRow.updatedByDevice.compareTo(remoteRow.updatedByDevice)
    }

    private fun signature(rows: List<LocalRow>): Map<RowKey, Long> = rows.associate { it.key to it.version }

    data class PushStats(val pushed: Int, val rejected: Int, val rounds: Int)

    data class PullStats(val applied: Int, val pages: Int, val cursor: Long)

    data class SyncStats(val pushed: Int, val pulled: Int, val pushRounds: Int)

    companion object {
        const val TOMBSTONE_RETENTION_MILLIS: Long = 30L * 24 * 60 * 60 * 1000
        const val DEFAULT_PULL_OVERLAP: Long = 1_000
        const val DEVICE_SILENCE_MILLIS: Long = 60L * 24 * 60 * 60 * 1000
        private const val MAX_PUSH_ROUNDS = 100
    }
}
