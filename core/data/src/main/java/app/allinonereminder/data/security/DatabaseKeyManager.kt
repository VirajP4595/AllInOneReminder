package app.allinonereminder.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class UnreadableDatabaseKeyException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class DatabaseKeyManager(
    private val context: Context,
    private val keyAlias: String = KEYSTORE_ALIAS
) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "allinonereminder_sqlcipher_master_key"
        private const val PREFS_NAME = "allinonereminder_security_prefs"
        private const val PREF_WRAPPED_DB_KEY = "wrapped_db_passphrase"
        private const val AES_GCM_NOPADDING = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val DB_KEY_SIZE_BYTES = 32
        private const val TEST_KEYSTORE_PREFS = "test_keystore_prefs"
    }

    private val isAndroidKeyStoreAvailable: Boolean =
        Security.getProvider(ANDROID_KEYSTORE) != null

    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wrappedBase64 = prefs.getString(PREF_WRAPPED_DB_KEY, null)

        val masterKey = getOrCreateMasterKey()

        if (wrappedBase64 != null) {
            try {
                val combined = Base64.decode(wrappedBase64, Base64.NO_WRAP)
                val buffer = ByteBuffer.wrap(combined)
                val iv = ByteArray(GCM_IV_LENGTH_BYTES)
                buffer.get(iv)
                val ciphertext = ByteArray(buffer.remaining())
                buffer.get(ciphertext)

                val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
                cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
                return cipher.doFinal(ciphertext)
            } catch (e: Exception) {
                throw UnreadableDatabaseKeyException(
                    "Database encryption key cannot be decrypted by current keystore master key. Data is unreadable and must be restored from backup.",
                    e
                )
            }
        }

        // Generate new 32 random bytes
        val randomBytes = ByteArray(DB_KEY_SIZE_BYTES)
        SecureRandom().nextBytes(randomBytes)

        val cipher = Cipher.getInstance(AES_GCM_NOPADDING)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(randomBytes)

        val combined = ByteBuffer.allocate(iv.size + ciphertext.size)
            .put(iv)
            .put(ciphertext)
            .array()

        val encoded = Base64.encodeToString(combined, Base64.NO_WRAP)
        prefs.edit().putString(PREF_WRAPPED_DB_KEY, encoded).apply()

        return randomBytes
    }

    @Synchronized
    fun canAccessPassphrase(): Boolean {
        return try {
            getOrCreatePassphrase()
            true
        } catch (e: UnreadableDatabaseKeyException) {
            false
        }
    }

    @Synchronized
    fun resetDatabaseKey() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(PREF_WRAPPED_DB_KEY).apply()
        if (isAndroidKeyStoreAvailable) {
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                if (keyStore.containsAlias(keyAlias)) {
                    keyStore.deleteEntry(keyAlias)
                }
            } catch (_: Exception) {}
        } else {
            val testPrefs = context.getSharedPreferences(TEST_KEYSTORE_PREFS, Context.MODE_PRIVATE)
            testPrefs.edit().remove("master_key_$keyAlias").apply()
        }
    }

    private fun getOrCreateMasterKey(): SecretKey {
        return if (isAndroidKeyStoreAvailable) {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
            val entry = keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry
            entry.secretKey
        } else {
            // JVM / Robolectric unit test fallback: store a software AES master key in test prefs
            val testPrefs = context.getSharedPreferences(TEST_KEYSTORE_PREFS, Context.MODE_PRIVATE)
            val existingKeyBase64 = testPrefs.getString("master_key_$keyAlias", null)
            if (existingKeyBase64 != null) {
                val raw = Base64.decode(existingKeyBase64, Base64.NO_WRAP)
                SecretKeySpec(raw, "AES")
            } else {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                val key = keyGen.generateKey()
                val encoded = Base64.encodeToString(key.encoded, Base64.NO_WRAP)
                testPrefs.edit().putString("master_key_$keyAlias", encoded).apply()
                key
            }
        }
    }
}
