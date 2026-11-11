package app.allinonereminder.ui.permissions

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import app.allinonereminder.MainActivity
import app.allinonereminder.alarm.AlarmCoordinator
import app.allinonereminder.alarm.AlarmReceiver

data class PermissionItem(
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val fixIntent: Intent?
)

object PermissionChecker {

    fun checkAll(context: Context): List<PermissionItem> {
        val list = mutableListOf<PermissionItem>()

        // 1. Notifications
        val notifGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val notifIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        list.add(
            PermissionItem(
                title = "Notifications",
                description = "Required to show reminders and sound alarms",
                isGranted = notifGranted,
                fixIntent = notifIntent
            )
        )

        // 2. Exact Alarms (Android 12+ / API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val exactGranted = am.canScheduleExactAlarms()
            val exactIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            list.add(
                PermissionItem(
                    title = "Exact Alarms",
                    description = "Required to ring reminders precisely on time",
                    isGranted = exactGranted,
                    fixIntent = exactIntent
                )
            )
        }

        // 3. Full-Screen Intent (Android 14+ / API 34+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val fsiGranted = nm.canUseFullScreenIntent()
            val fsiIntent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            list.add(
                PermissionItem(
                    title = "Full-Screen Reminders",
                    description = "Required to display alarms on a locked phone",
                    isGranted = fsiGranted,
                    fixIntent = fsiIntent
                )
            )
        }

        // 4. Display over other apps: lets the alarm open on top of whatever app is in use
        list.add(
            PermissionItem(
                title = "Display Over Other Apps",
                description = "Required for the alarm to cover the screen while you use another app. " +
                    "Without it, alarms only appear as a small banner at the top",
                isGranted = Settings.canDrawOverlays(context),
                fixIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            )
        )

        // 5. Do Not Disturb must let alarms through (spec §16)
        val nmDnd = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val alarmsAllowed = runCatching {
            when (nmDnd.currentInterruptionFilter) {
                NotificationManager.INTERRUPTION_FILTER_NONE -> false
                NotificationManager.INTERRUPTION_FILTER_PRIORITY ->
                    (nmDnd.notificationPolicy.priorityCategories and
                        NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) != 0
                else -> true
            }
        }.getOrDefault(true)
        val zenIntent = Intent("android.settings.ZEN_MODE_SETTINGS")
        val dndIntent = if (zenIntent.resolveActivity(context.packageManager) != null) zenIntent
        else Intent(Settings.ACTION_SOUND_SETTINGS)
        list.add(
            PermissionItem(
                title = "Do Not Disturb",
                description = "Do Not Disturb is on and blocks alarms. Allow alarms in its exceptions, or turn it off",
                isGranted = alarmsAllowed,
                fixIntent = dndIntent
            )
        )

        // 6. Battery Optimization Ignored
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)
        val batteryIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        list.add(
            PermissionItem(
                title = "Battery Optimization",
                description = "Allows alarms to ring without being killed by battery saver",
                isGranted = batteryIgnored,
                fixIntent = batteryIntent
            )
        )

        return list
    }

    fun getOemAutostartInstructions(): Pair<String, String> {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> {
                "Xiaomi / Redmi / POCO (MIUI / HyperOS)" to
                        "1. Open Security app > Manage apps > All-in-One Reminder\n" +
                        "2. Enable 'Autostart'\n" +
                        "3. In Battery saver, choose 'No restrictions'\n" +
                        "4. Other permissions: enable 'Show on Lock screen' and 'Display pop-up windows while running in the background'"
            }
            manufacturer.contains("samsung") -> {
                "Samsung (One UI)" to
                        "1. Settings > Apps > All-in-One Reminder > Battery\n" +
                        "2. Select 'Unrestricted'\n" +
                        "3. Settings > Battery and device care > Battery > Background usage limits > Never sleeping apps > Add All-in-One Reminder"
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                "Oppo / Realme (ColorOS / Realme UI)" to
                        "1. Settings > Apps (App management) > App list > All-in-One Reminder\n" +
                        "2. Enable 'Auto launch' (ColorOS 11: Settings > Apps > Auto launch) and 'Allow background activity'\n" +
                        "3. In Battery > App battery management, turn off 'Pause app activity if unused'\n" +
                        "4. Permissions > Other permissions (or Notifications): allow 'Show on Lock screen' / 'Lock screen notifications' and 'Floating windows'"
            }
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> {
                "Vivo / iQOO (Funtouch OS / OriginOS)" to
                        "1. Settings > Apps & permissions > Permission management > Autostart\n" +
                        "2. Turn on All-in-One Reminder\n" +
                        "3. In Battery > Background power consumption management, select 'High background power usage'\n" +
                        "4. Permissions > Other: allow 'Display on lock screen' and 'Display pop-up windows'"
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                "Huawei / Honor (EMUI / MagicOS)" to
                        "1. Settings > Battery > App launch > All-in-One Reminder\n" +
                        "2. Turn off 'Manage automatically', then turn on Auto-launch, Secondary launch and Run in background\n" +
                        "3. Settings > Apps > All-in-One Reminder > Notifications: allow lock screen notifications and banners"
            }
            manufacturer.contains("motorola") || manufacturer.contains("lenovo") -> {
                "Motorola (My UX)" to
                        "1. Settings > Apps > All-in-One Reminder > App battery usage\n" +
                        "2. Choose 'Unrestricted' (or turn off 'Optimize')\n" +
                        "3. Allow background usage and 'Display over other apps'"
            }
            manufacturer.contains("oneplus") -> {
                "OnePlus (OxygenOS)" to
                        "1. Settings > Apps > App management > All-in-One Reminder > Battery usage\n" +
                        "2. Allow background activity and auto-launch\n" +
                        "3. In Optimize battery use, set to 'Don't optimize'\n" +
                        "4. Notifications: allow on the lock screen; allow 'Floating windows'"
            }
            else -> {
                "Standard Android Device" to
                        "Ensure battery optimization is set to 'Unrestricted' and background activity is permitted."
            }
        }
    }
}

