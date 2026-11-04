package app.allinonereminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import app.allinonereminder.R
import app.allinonereminder.model.Criticality

object AlarmNotificationHelper {
    const val CHANNEL_CRITICAL = "alarms_critical"
    const val CHANNEL_NORMAL = "alarms_normal"
    const val CHANNEL_GENTLE = "alarms_gentle"
    const val CHANNEL_MEAL = "meal_session"

    const val NOTIFICATION_ID_RINGING = 2001
    const val NOTIFICATION_ID_MEAL = 2002

    fun buildPlaceholderNotification(context: Context): Notification {
        createNotificationChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_NORMAL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Medicine reminder")
            .setContentText("Checking reminder details...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alarmAudioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()

            val defaultAlarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // Critical channel
            val criticalChannel = NotificationChannel(
                CHANNEL_CRITICAL,
                "Critical Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Full-screen loud alarms for critical medications"
                setSound(defaultAlarmSound, alarmAudioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            // Normal channel
            val normalChannel = NotificationChannel(
                CHANNEL_NORMAL,
                "Normal Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Standard alarms for daily medicines and appointments"
                setSound(defaultAlarmSound, alarmAudioAttributes)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            // Gentle channel
            val gentleChannel = NotificationChannel(
                CHANNEL_GENTLE,
                "Gentle Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gentle sound without full-screen for water and habits"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            // Meal session ongoing channel
            val mealChannel = NotificationChannel(
                CHANNEL_MEAL,
                "Meal Sessions",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification while having a meal"
            }

            nm.createNotificationChannels(listOf(criticalChannel, normalChannel, gentleChannel, mealChannel))
        }
    }

    fun buildRingingNotification(
        context: Context,
        occurrenceIds: Array<String>,
        title: String,
        content: String,
        criticality: Criticality = Criticality.NORMAL
    ): Notification {
        val channelId = when (criticality) {
            Criticality.CRITICAL -> CHANNEL_CRITICAL
            Criticality.NORMAL -> CHANNEL_NORMAL
            Criticality.GENTLE -> CHANNEL_GENTLE
        }

        // Full screen intent opens AlarmActivity
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, occurrenceIds)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            3001,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification Action: Taken
        val takenIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_NOTIFICATION_TAKEN
            putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, occurrenceIds)
        }
        val takenPendingIntent = PendingIntent.getBroadcast(
            context,
            3002,
            takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Notification Action: Snooze
        val snoozeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_NOTIFICATION_SNOOZE
            putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, occurrenceIds)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            3003,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Public notification on secure lock screen (§19: "No medicine data in notification text on secure lock screen")
        val publicNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Medicine reminder")
            .setContentText("You have a scheduled reminder")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(if (criticality == Criticality.GENTLE) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(android.R.drawable.checkbox_on_background, "Taken", takenPendingIntent)
            .addAction(android.R.drawable.ic_menu_recent_history, "Snooze", snoozePendingIntent)

        if (criticality != Criticality.GENTLE) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        } else {
            builder.setContentIntent(fullScreenPendingIntent)
        }

        return builder.build()
    }
}

