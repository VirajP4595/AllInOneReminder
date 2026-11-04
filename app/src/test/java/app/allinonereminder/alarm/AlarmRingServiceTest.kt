package app.allinonereminder.alarm

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.model.Criticality
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AlarmRingServiceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun placeholderNotification_buildsValidOngoingAlarmNotification() {
        val placeholder = AlarmNotificationHelper.buildPlaceholderNotification(context)
        assertThat(placeholder).isNotNull()
        assertThat(placeholder.channelId).isEqualTo(AlarmNotificationHelper.CHANNEL_NORMAL)
    }

    @Test
    fun buildRingingNotification_criticalityLevels_configuresFullScreenIntentAndChannels() {
        AlarmNotificationHelper.createNotificationChannels(context)

        // 1. Critical
        val criticalNotif = AlarmNotificationHelper.buildRingingNotification(
            context = context,
            occurrenceIds = arrayOf("occ-1"),
            title = "Critical Med",
            content = "Take now",
            criticality = Criticality.CRITICAL
        )
        assertThat(criticalNotif.channelId).isEqualTo(AlarmNotificationHelper.CHANNEL_CRITICAL)
        assertThat(criticalNotif.fullScreenIntent).isNotNull()

        // 2. Normal
        val normalNotif = AlarmNotificationHelper.buildRingingNotification(
            context = context,
            occurrenceIds = arrayOf("occ-2"),
            title = "Daily Vitamin",
            content = "Take with water",
            criticality = Criticality.NORMAL
        )
        assertThat(normalNotif.channelId).isEqualTo(AlarmNotificationHelper.CHANNEL_NORMAL)
        assertThat(normalNotif.fullScreenIntent).isNotNull()

        // 3. Gentle (must NOT set fullScreenIntent per §16)
        val gentleNotif = AlarmNotificationHelper.buildRingingNotification(
            context = context,
            occurrenceIds = arrayOf("occ-3"),
            title = "Drink Water",
            content = "Stay hydrated",
            criticality = Criticality.GENTLE
        )
        assertThat(gentleNotif.channelId).isEqualTo(AlarmNotificationHelper.CHANNEL_GENTLE)
        assertThat(gentleNotif.fullScreenIntent).isNull()
    }
}

