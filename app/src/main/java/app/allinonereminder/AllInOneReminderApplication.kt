package app.allinonereminder

import android.app.Application
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.alarm.AlarmNotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AllInOneReminderApplication : Application() {

    @Inject
    lateinit var alarmCoordinator: AlarmCoordinator

    override fun onCreate() {
        super.onCreate()
        app.allinonereminder.ui.components.AppTime.refresh(this)
        AlarmNotificationHelper.createNotificationChannels(this)
        alarmCoordinator.rescheduleAsync("AppStartup")
    }
}
