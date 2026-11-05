package app.allinonereminder.sync

/**
 * Local storage boundary. Track B implements this over Room; tests use the in-memory fake.
 */
interface LocalStore {
    /** Dirty rows, oldest first, at most [limit]. */
    fun dirtyRows(limit: Int): List<LocalRow>

    fun get(key: RowKey): LocalRow?

    fun allRows(): List<LocalRow>

    /** Inserts or replaces the row exactly as given. */
    fun upsert(row: LocalRow)

    /** Clears `dirty` for rows the server accepted and records their `server_seq`. */
    fun markPushed(accepted: List<RemoteRow>)

    /** Cursor of the last successfully committed pull. */
    fun lastPulledSeq(): Long

    /**
     * Advances the pull cursor. The engine calls this only after every row of the page has been
     * decrypted and applied locally.
     */
    fun markPullCommitted(cursor: Long)

    fun purge(key: RowKey)
}

/** Server boundary. Track B implements this over Supabase REST; no HTTP client lives here. */
interface RemoteStore {
    fun push(rows: List<RemoteRow>): PushResult

    fun pull(sinceSeq: Long, limit: Int): PullPage

    /** All active devices and their last pulled `server_seq`. */
    fun deviceCursors(): List<DeviceCursor>
}

data class PushResult(
    val accepted: List<RemoteRow>,
    val rejected: List<RowKey>,
)

data class PullPage(
    val rows: List<RemoteRow>,
    val nextSeq: Long,
    val hasMore: Boolean,
)

/**
 * Row encryption boundary, matching `/core/crypto`'s `RowEncryptor`. Kept local so this module's
 * standalone build has no dependency on another module; Track B maps the two types one-to-one.
 */
interface RowCipher {
    fun encrypt(plaintext: ByteArray, aad: RowAad): EncryptedRow

    fun decrypt(row: EncryptedRow, aad: RowAad): ByteArray
}

/**
 * Encodes `OccurrenceAction` into the encrypted row payload. Track B implements it with the app's
 * serializer; tests use a tiny text codec.
 */
interface ActionCodec {
    fun encode(action: OccurrenceAction): ByteArray

    fun decode(payload: ByteArray): OccurrenceAction
}
