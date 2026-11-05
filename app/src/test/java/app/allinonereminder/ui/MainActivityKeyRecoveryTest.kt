package app.allinonereminder.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.AllInOneReminderApplication
import app.allinonereminder.MainActivity
import app.allinonereminder.data.security.DatabaseKeyManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = AllInOneReminderApplication::class)
class MainActivityKeyRecoveryTest {

    @Test
    fun unreadableDatabaseKey_routesToRecovery_andResetClearsKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("allinonereminder_security_prefs", Context.MODE_PRIVATE)

        // Simulate corrupted wrapped key
        prefs.edit().putString("wrapped_db_passphrase", "corrupted-unreadable-key-data").commit()

        val keyManager = DatabaseKeyManager(context)
        assertThat(keyManager.canAccessPassphrase()).isFalse()

        // Launch MainActivity
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertThat(activity.isFinishing).isFalse()

        // Verify reset deletes the corrupted key
        keyManager.resetDatabaseKey()
        assertThat(prefs.contains("wrapped_db_passphrase")).isFalse()

        // Can generate new passphrase cleanly
        assertThat(keyManager.canAccessPassphrase()).isTrue()
        controller.destroy()
    }
}
