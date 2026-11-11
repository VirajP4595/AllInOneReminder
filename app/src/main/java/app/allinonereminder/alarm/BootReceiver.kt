package app.allinonereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }

    @Inject
    lateinit var alarmCoordinator: AlarmCoordinator

    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        // Exported for system broadcasts; ignore anything else another app might send.
        if (action !in HANDLED_ACTIONS) return
        val pendingResult = goAsync()

        val handler = Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            try {
                pendingResult.finish()
            } catch (_: Exception) {}
        }
        // Safety timeout under 10 seconds to satisfy Android broadcast receiver limits
        handler.postDelayed(timeoutRunnable, 9000L)

        scope.launch {
            try {
                withTimeout(8500L) {
                    alarmCoordinator.reschedule("BootReceiver: $action")
                }
            } catch (_: Exception) {
            } finally {
                handler.removeCallbacks(timeoutRunnable)
                try {
                    pendingResult.finish()
                } catch (_: Exception) {}
            }
        }
    }
}
