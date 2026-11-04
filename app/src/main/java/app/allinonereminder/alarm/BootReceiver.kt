package app.allinonereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alarmCoordinator: AlarmCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        alarmCoordinator.rescheduleAsync("BootReceiver: $action")
    }
}

