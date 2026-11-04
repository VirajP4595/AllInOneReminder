package app.allinonereminder.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseKeyManagerTest {

    @Test
    fun getOrCreatePassphrase_isConsistentAcrossCalls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager1 = DatabaseKeyManager(context, keyAlias = "test_db_key_1")
        val key1 = manager1.getOrCreatePassphrase()
        assertThat(key1).hasLength(32)

        // Subsequent call returns identical decrypted 32-byte key
        val manager2 = DatabaseKeyManager(context, keyAlias = "test_db_key_1")
        val key2 = manager2.getOrCreatePassphrase()
        assertThat(key2).isEqualTo(key1)
    }

    @Test
    fun getOrCreatePassphrase_differentAliasesHaveDifferentKeys() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val managerA = DatabaseKeyManager(context, keyAlias = "alias_a")
        val keyA = managerA.getOrCreatePassphrase()

        // Clear shared preferences so managerB generates a fresh key
        context.getSharedPreferences("allinonereminder_security_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        val managerB = DatabaseKeyManager(context, keyAlias = "alias_b")
        val keyB = managerB.getOrCreatePassphrase()

        assertThat(keyA).isNotEqualTo(keyB)
    }
}

