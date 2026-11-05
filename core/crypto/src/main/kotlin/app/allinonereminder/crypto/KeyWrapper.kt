package app.allinonereminder.crypto

/**
 * Wraps the household key for local storage.
 *
 * Android (P2-B1) implements this with an AES-GCM key in the Android Keystore. This module stays
 * Android-free, so it only defines the contract: implementations must never log the key or the
 * wrapped bytes, and [unwrap] must fail when the ciphertext is tampered with.
 */
interface KeyWrapper {
    fun wrap(key: HouseholdKey): ByteArray

    fun unwrap(wrapped: ByteArray): HouseholdKey
}
