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

    @Inject
    lateinit var alarmCoordinator: AlarmCoordinator

    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
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
