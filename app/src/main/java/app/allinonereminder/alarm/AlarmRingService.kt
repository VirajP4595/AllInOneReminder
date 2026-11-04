package app.allinonereminder.alarm

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingService : Service() {

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
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var silenceRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val occurrenceIds = intent?.getStringArrayExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS)
        if (occurrenceIds.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            handleRinging(occurrenceIds)
        }

        return START_NOT_STICKY
    }

    private suspend fun handleRinging(occurrenceIds: Array<String>) {
        val occurrences = occurrenceIds.mapNotNull { occurrenceRepository.getByIdSync(it) }
        if (occurrences.isEmpty()) {
            stopSelf()
            return
        }

        val firstOcc = occurrences.first()
        val item = itemRepository.getItemSync(firstOcc.itemId)
        val profile = profileRepository.getProfileSync(firstOcc.profileId)

        val title = if (occurrences.size > 1) {
            "${occurrences.size} Reminders Due"
        } else {
            "${item?.name ?: "Medicine"} reminder"
        }

        val content = if (occurrences.size > 1) {
            "For ${profile?.name ?: "Family"}"
        } else {
            "${item?.dosageAmount ?: ""} ${item?.dosageUnit ?: ""} - ${profile?.name ?: ""}"
        }

        val criticality = item?.criticality ?: Criticality.NORMAL
        val notification = AlarmNotificationHelper.buildRingingNotification(
            context = this@AlarmRingService,
            occurrenceIds = occurrenceIds,
            title = title,
            content = content,
            criticality = criticality
        )

        // Start foreground with proper FGS type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AlarmNotificationHelper.NOTIFICATION_ID_RINGING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(AlarmNotificationHelper.NOTIFICATION_ID_RINGING, notification)
        }

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

        // Play Sound
        if (criticality != Criticality.GENTLE || settings.soundGentle.isNotEmpty()) {
            startAlarmSound()
        }

        // Vibrate
        if (settings.vibrationEnabled) {
            startVibration()
        }

        // Auto-silence after ringDurationSeconds (default 120s)
        val silenceDelayMillis = settings.ringDurationSeconds * 1000L
        silenceRunnable = Runnable {
            scope.launch {
                stopSoundAndVibration()
                // Update to PENDING with next re-ring time if within grace
                val reRingMinutes = item?.reRingMinutes ?: 5
                val nextReRing = System.currentTimeMillis() + reRingMinutes * 60_000L
                for (occ in occurrences) {
                    val current = occurrenceRepository.getByIdSync(occ.id)
                    if (current?.status == OccurrenceStatus.RINGING) {
                        occurrenceRepository.updateOccurrence(
                            current.copy(
                                status = OccurrenceStatus.PENDING,
                                dueAt = nextReRing,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                alarmCoordinator.reschedule("AlarmRingService: Auto-silenced")
                stopSelf()
            }
        }
        handler.postDelayed(silenceRunnable!!, silenceDelayMillis)
    }

    private fun startAlarmSound() {
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
            // Silently fall back if sound file fails to load
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

