package app.allinonereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceIds = intent.getStringArrayExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS) ?: return

        // Acquire partial wake lock for 10 seconds to ensure foreground service startup
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AllInOneReminder:AlarmReceiverWakeLock"
        )
        wakeLock.acquire(10_000L)

        val serviceIntent = Intent(context, AlarmRingService::class.java).apply {
            putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, occurrenceIds)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}

