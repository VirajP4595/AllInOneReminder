package app.allinonereminder.crypto

import com.google.crypto.tink.subtle.Random

/**
 * The household key (HK) used to encrypt every synced row and photo.
 *
 * The key bytes are intentionally never exposed through [toString], and this is not a data class so
 * generated `toString`/`hashCode` implementations cannot leak key material into logs.
 */
class HouseholdKey private constructor(
    val version: Int,
    private val keyBytes: ByteArray,
) {
    init {
        require(version >= 1) { "key version must be >= 1" }
        require(keyBytes.size == KEY_BYTES) { "household key must be $KEY_BYTES bytes" }
    }

    /** Returns a defensive copy of the key material. Callers must not log or persist it unwrapped. */
    fun copyKeyBytes(): ByteArray = keyBytes.copyOf()

    /** Returns the next key version with fresh random key material. */
    fun rotate(): HouseholdKey = generate(version + 1)

    override fun toString(): String = "HouseholdKey(version=$version)"

    companion object {
        const val KEY_BYTES: Int = 32

        fun generate(version: Int = 1): HouseholdKey = HouseholdKey(version, Random.randBytes(KEY_BYTES))

        fun fromBytes(bytes: ByteArray, version: Int = 1): HouseholdKey = HouseholdKey(version, bytes.copyOf())
    }
}
