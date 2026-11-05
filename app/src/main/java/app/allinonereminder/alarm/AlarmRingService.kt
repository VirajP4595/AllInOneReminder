package app.allinonereminder.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.OccurrenceStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingService : Service() {

    companion object {
        const val ACTION_STOP_OCCURRENCE = "app.allinonereminder.action.STOP_OCCURRENCE"
        const val ACTION_STOP_ALL = "app.allinonereminder.action.STOP_ALL"
        const val EXTRA_OCCURRENCE_ID = "extra_occurrence_id"

        fun stopForOccurrence(context: Context, occurrenceId: String) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                action = ACTION_STOP_OCCURRENCE
                putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            }
            context.startService(intent)
        }

        fun stopAll(context: Context) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                action = ACTION_STOP_ALL
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var occurrenceRepository: OccurrenceRepository

    @Inject
    lateinit var itemRepository: ItemRepository

    @Inject
    lateinit var profileRepository: ProfileRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var alarmCoordinator: AlarmCoordinator

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)

    private var silenceRunnable: Runnable? = null
    private val activeOccurrenceIds = Collections.synchronizedSet(mutableSetOf<String>())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // (a) Start foreground synchronously at the top of onStartCommand with a placeholder notification
        // to prevent ForegroundServiceDidNotStartInTimeException
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AlarmNotificationHelper.NOTIFICATION_ID_RINGING,
                AlarmNotificationHelper.buildPlaceholderNotification(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(
                AlarmNotificationHelper.NOTIFICATION_ID_RINGING,
                AlarmNotificationHelper.buildPlaceholderNotification(this)
            )
        }

        val action = intent?.action

        // (c) Stop service whenever the last ringing item is acted on from any surface
        if (action == ACTION_STOP_OCCURRENCE) {
            val occId = intent.getStringExtra(EXTRA_OCCURRENCE_ID)
            if (occId != null) {
                activeOccurrenceIds.remove(occId)
            }
            if (activeOccurrenceIds.isEmpty()) {
                stopSoundAndVibration()
                silenceRunnable?.let { handler.removeCallbacks(it) }
                stopForeground(true)
                stopSelf()
            } else {
                // Re-post group notification for remaining ids so actions remain visible (R2-7)
                scope.launch {
                    val remainingOccs = activeOccurrenceIds.mapNotNull { occurrenceRepository.getByIdSync(it) }
                    val remainingItems = remainingOccs.mapNotNull { itemRepository.getItemSync(it.itemId) }
                    val remainingProfiles = remainingOccs.mapNotNull { profileRepository.getProfileSync(it.profileId) }
                    if (remainingOccs.isNotEmpty() && remainingItems.isNotEmpty()) {
                        val highestCrit = when {
                            remainingItems.any { it.criticality == Criticality.CRITICAL } -> Criticality.CRITICAL
                            remainingItems.any { it.criticality == Criticality.NORMAL } -> Criticality.NORMAL
                            else -> Criticality.GENTLE
                        }
                        val title = if (remainingOccs.size > 1) "${remainingOccs.size} Reminders Due" else "${remainingItems.firstOrNull()?.name ?: "Medicine"} reminder"
                        val profileNames = remainingProfiles.map { it.name }.distinct().joinToString(", ")
                        val content = if (remainingOccs.size > 1) "Due for $profileNames: ${remainingItems.joinToString(", ") { it.name }}" else "Due for $profileNames: ${remainingItems.firstOrNull()?.dosageAmount ?: ""}${remainingItems.firstOrNull()?.dosageUnit ?: ""}".trim()
                        val notification = AlarmNotificationHelper.buildRingingNotification(
                            this@AlarmRingService,
                            activeOccurrenceIds.toTypedArray(),
                            title,
                            content,
                            highestCrit
                        )
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(AlarmNotificationHelper.NOTIFICATION_ID_RINGING, notification)
                    }
                }
            }
            return START_NOT_STICKY
        }

        if (action == ACTION_STOP_ALL) {
            activeOccurrenceIds.clear()
            stopSoundAndVibration()
            silenceRunnable?.let { handler.removeCallbacks(it) }
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        val newIds = intent?.getStringArrayExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS)
        if (newIds.isNullOrEmpty()) {
            if (activeOccurrenceIds.isEmpty()) {
                stopForeground(true)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        // (b) Make ringing re-entrant: stop and release current player, vibration and runnable,
        // then merge the occurrence ids into one group.
        silenceRunnable?.let { handler.removeCallbacks(it) }
        stopSoundAndVibration()
        activeOccurrenceIds.addAll(newIds)

        scope.launch {
            handleRinging(activeOccurrenceIds.toTypedArray())
        }

        return START_NOT_STICKY
    }

    private suspend fun handleRinging(occurrenceIds: Array<String>) {
        val occurrences = occurrenceIds.mapNotNull { occurrenceRepository.getByIdSync(it) }
        if (occurrences.isEmpty()) {
            stopForeground(true)
            stopSelf()
            return
        }

        val items = occurrences.mapNotNull { itemRepository.getItemSync(it.itemId) }
        val profiles = occurrences.mapNotNull { profileRepository.getProfileSync(it.profileId) }

        // Use highest criticality in the group (CRITICAL > NORMAL > GENTLE)
        val highestCriticality = when {
            items.any { it.criticality == Criticality.CRITICAL } -> Criticality.CRITICAL
            items.any { it.criticality == Criticality.NORMAL } -> Criticality.NORMAL
            else -> Criticality.GENTLE
        }

        val title = if (occurrences.size > 1) {
            "${occurrences.size} Reminders Due"
        } else {
            "${items.firstOrNull()?.name ?: "Medicine"} reminder"
        }

        val profileNames = profiles.map { it.name }.distinct().joinToString(", ")
        val content = if (occurrences.size > 1) {
            "For $profileNames"
        } else {
            val item = items.firstOrNull()
            "${item?.dosageAmount ?: ""} ${item?.dosageUnit ?: ""} - $profileNames"
        }

        val notification = AlarmNotificationHelper.buildRingingNotification(
            context = this@AlarmRingService,
            occurrenceIds = occurrenceIds,
            title = title,
            content = content,
            criticality = highestCriticality
        )

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(AlarmNotificationHelper.NOTIFICATION_ID_RINGING, notification)

        // Mark occurrences as RINGING and increment ringCount
        val now = System.currentTimeMillis()
        for (occ in occurrences) {
            occurrenceRepository.updateOccurrence(
                occ.copy(
                    status = OccurrenceStatus.RINGING,
                    ringCount = occ.ringCount + 1,
                    updatedAt = now
                )
            )
        }

        val settings = settingsRepository.settingsFlow.first()

        // Sound & Vibration based on criticality:
        // GENTLE items must play a single notification sound with no full-screen alarm and no looping alarm
        if (highestCriticality == Criticality.GENTLE) {
            playSingleGentleSound()
        } else {
            startLoopingAlarmSound()
            if (settings.vibrationEnabled) {
                startVibration()
            }
        }

        // Auto-silence after ringDurationSeconds (default 120s)
        val silenceDelayMillis = settings.ringDurationSeconds * 1000L
        silenceRunnable = Runnable {
            scope.launch {
                stopSoundAndVibration()
                // Update remaining ringing items to PENDING with reRingMinutes (moves remindAt, R2-1)
                val reRingMinutes = items.minOfOrNull { it.reRingMinutes } ?: 5
                val nextReRing = System.currentTimeMillis() + reRingMinutes * 60_000L
                for (id in activeOccurrenceIds) {
                    val current = occurrenceRepository.getByIdSync(id)
                    if (current?.status == OccurrenceStatus.RINGING) {
                        occurrenceRepository.updateOccurrence(
                            current.copy(
                                status = OccurrenceStatus.PENDING,
                                remindAt = nextReRing,
                                ringCount = current.ringCount + 1,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                activeOccurrenceIds.clear()
                alarmCoordinator.reschedule("AlarmRingService: Auto-silenced")
                stopForeground(true)
                stopSelf()
            }
        }
        handler.postDelayed(silenceRunnable!!, silenceDelayMillis)
    }

    private fun startLoopingAlarmSound() {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
        }
    }

    private fun playSingleGentleSound() {
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false
                setOnCompletionListener {
                    try {
                        it.release()
                    } catch (_: Exception) {}
                    if (mediaPlayer == it) {
                        mediaPlayer = null
                    }
                }
                prepare()
                start()
            }
        } catch (_: Exception) {
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopSoundAndVibration() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {
        }
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        silenceRunnable?.let { handler.removeCallbacks(it) }
        stopSoundAndVibration()
        super.onDestroy()
    }
}
