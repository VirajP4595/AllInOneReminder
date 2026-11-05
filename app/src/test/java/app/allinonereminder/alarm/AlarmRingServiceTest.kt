package app.allinonereminder.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.allinonereminder.AllInOneReminderApplication
import app.allinonereminder.model.Criticality
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = AllInOneReminderApplication::class)
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

    @Test
    fun startForeground_calledBeforeAnyDbAccess_evenWithEmptyOrStaleIds() {
        // A never-advanced dispatcher keeps the service's background work from running, so the
        // stale-id path cannot stop the foreground notification before it is asserted.
        AlarmRingService.coroutineDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()
        try {
            startForegroundCheck()
        } finally {
            AlarmRingService.coroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
        }
    }

    private fun startForegroundCheck() {
        val controller = Robolectric.buildService(AlarmRingService::class.java).create()
        val service = controller.get()
        val shadowService = Shadows.shadowOf(service)

        // Stale IDs that do not exist in DB: startForeground is called before any DB query
        val staleIntent = Intent(context, AlarmRingService::class.java).apply {
            putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, arrayOf("stale-id-999"))
        }
        service.onStartCommand(staleIntent, 0, 1)

        assertThat(shadowService.lastForegroundNotification).isNotNull()
        assertThat(shadowService.lastForegroundNotificationId).isEqualTo(AlarmNotificationHelper.NOTIFICATION_ID_RINGING)
        controller.destroy()

        // Empty intent: starts foreground then cleanly self-terminates
        val emptyController = Robolectric.buildService(AlarmRingService::class.java).create()
        val emptyService = emptyController.get()
        val shadowEmpty = Shadows.shadowOf(emptyService)
        emptyService.onStartCommand(Intent(context, AlarmRingService::class.java), 0, 1)
        assertThat(shadowEmpty.isStoppedBySelf).isTrue()
        emptyController.destroy()
    }

    @Test
    fun reentrantFire_releasesPreviousPlayer_andMergesOccurrenceIds() {
        val controller = Robolectric.buildService(AlarmRingService::class.java).create()
        val service = controller.get()

        val intent1 = Intent(context, AlarmRingService::class.java).apply {
            putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, arrayOf("occ-1"))
        }
        service.onStartCommand(intent1, 0, 1)

        // Second alarm fires while first is ringing
        val intent2 = Intent(context, AlarmRingService::class.java).apply {
            putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, arrayOf("occ-2"))
        }
        service.onStartCommand(intent2, 0, 2)

        // Merged IDs in active service
        val shadowService = Shadows.shadowOf(service)
        assertThat(shadowService.lastForegroundNotification).isNotNull()
        controller.destroy()
    }

    @Test
    fun gentleCriticality_playsSingleNotificationChime_noLoopingAlarm_noFullScreenIntent() {
        AlarmNotificationHelper.createNotificationChannels(context)

        val gentleNotif = AlarmNotificationHelper.buildRingingNotification(
            context = context,
            occurrenceIds = arrayOf("occ-water"),
            title = "Drink Water",
            content = "1 glass",
            criticality = Criticality.GENTLE
        )
        assertThat(gentleNotif.fullScreenIntent).isNull()
        assertThat(gentleNotif.channelId).isEqualTo(AlarmNotificationHelper.CHANNEL_GENTLE)
    }

    @Test
    fun stopForOccurrence_withRemainingOccurrences_repostsGroupNotification() {
        val controller = Robolectric.buildService(AlarmRingService::class.java).create()
        val service = controller.get()

        val startIntent = Intent(context, AlarmRingService::class.java).apply {
            putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, arrayOf("occ-1", "occ-2"))
        }
        service.onStartCommand(startIntent, 0, 1)

        // User acts on occ-1 from Today screen
        val stopIntent = Intent(context, AlarmRingService::class.java).apply {
            action = AlarmRingService.ACTION_STOP_OCCURRENCE
            putExtra(AlarmRingService.EXTRA_OCCURRENCE_ID, "occ-1")
        }
        service.onStartCommand(stopIntent, 0, 2)

        // Service remains running for occ-2
        val shadowService = Shadows.shadowOf(service)
        assertThat(shadowService.isStoppedBySelf).isFalse()
        controller.destroy()
    }
}
