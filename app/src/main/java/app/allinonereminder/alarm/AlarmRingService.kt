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
import android.speech.tts.TextToSpeech
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.ProfileRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.Criticality
import app.allinonereminder.model.Item
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.Profile
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.effectiveDoseAmount
import app.allinonereminder.health.ExerciseVerifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AlarmRingService : Service() {

    companion object {
        const val ACTION_STOP_OCCURRENCE = "app.allinonereminder.action.STOP_OCCURRENCE"
        const val ACTION_STOP_ALL = "app.allinonereminder.action.STOP_ALL"
        const val ACTION_SILENCE = "app.allinonereminder.action.SILENCE"
        const val EXTRA_OCCURRENCE_ID = "extra_occurrence_id"

        /** Unanswered rings before a dose is marked missed: normal items, and "must not miss" items. */
        const val MAX_RINGS_NORMAL = 3
        const val MAX_RINGS_CRITICAL = 5

        /** Tests replace this with a controllable dispatcher so background work is deterministic. */
        @androidx.annotation.VisibleForTesting
        var coroutineDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default

        /** Stops the sound and vibration but keeps the group, so the screen stays usable without noise. */
        fun silence(context: Context) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                action = ACTION_SILENCE
            }
            context.startService(intent)
        }

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
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var alarmCoordinator: AlarmCoordinator

    @Inject
    lateinit var exerciseVerifier: ExerciseVerifier

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }
    private val scope = CoroutineScope(coroutineDispatcher + SupervisorJob() + exceptionHandler)

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
                        val content = if (remainingOccs.size > 1) {
                            "Due for $profileNames: ${remainingItems.joinToString(", ") { it.name }}"
                        } else {
                            val firstItem = remainingItems.firstOrNull()
                            val schedule = remainingOccs.firstOrNull()?.let { scheduleRepository.getScheduleSync(it.scheduleId) }
                            val dose = if (firstItem != null) effectiveDoseAmount(schedule, firstItem) else ""
                            "Due for $profileNames: $dose${firstItem?.dosageUnit ?: ""}".trim()
                        }
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

        if (action == ACTION_SILENCE) {
            stopSoundAndVibration()
            if (activeOccurrenceIds.isEmpty()) {
                stopForeground(true)
                stopSelf()
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

    private suspend fun handleRinging(requestedIds: Array<String>) {
        val requested = requestedIds.mapNotNull { occurrenceRepository.getByIdSync(it) }

        // Health Connect: an exercise the phone already saw is completed instead of ringing.
        val verifiedIds = try {
            exerciseVerifier.completeSatisfied(requested)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptySet()
        }
        val occurrences = requested.filter { it.id !in verifiedIds }
        if (verifiedIds.isNotEmpty()) {
            activeOccurrenceIds.removeAll(verifiedIds)
            alarmCoordinator.reschedule("HealthConnect: verified before ringing")
        }
        val occurrenceIds = occurrences.map { it.id }.toTypedArray()
        if (occurrences.isEmpty()) {
            if (requested.isEmpty() || activeOccurrenceIds.isEmpty()) {
                stopSoundAndVibration()
                stopForeground(true)
                stopSelf()
            }
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
            val schedule = occurrences.firstOrNull()?.let { scheduleRepository.getScheduleSync(it.scheduleId) }
            val dose = if (item != null) effectiveDoseAmount(schedule, item) else ""
            "$dose ${item?.dosageUnit ?: ""} - $profileNames"
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

        // Show the alarm over whatever app is in use (needs "Display over other apps"); the
        // notification's full-screen intent still covers the locked-screen case.
        if (highestCriticality != Criticality.GENTLE) {
            AlarmLauncher.launch(this@AlarmRingService, occurrenceIds)
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

        // Sound & Vibration based on criticality:
        // GENTLE items must play a single notification sound with no full-screen alarm and no looping alarm
        if (highestCriticality == Criticality.GENTLE) {
            playSingleGentleSound(chosenSoundUri(settings.soundGentle))
        } else {
            val soundSetting = if (highestCriticality == Criticality.CRITICAL) settings.soundCritical else settings.soundNormal
            startLoopingAlarmSound(chosenSoundUri(soundSetting))
            if (settings.vibrationEnabled) {
                startVibration()
            }
            if (settings.speakRemindersEnabled) {
                speakReminders(occurrences, items, profiles)
            }
        }

        // Auto-silence after ringDurationSeconds (default 120s)
        val silenceDelayMillis = settings.ringDurationSeconds * 1000L
        silenceRunnable = Runnable {
            scope.launch {
                stopSoundAndVibration()
                // Nobody answered. Ring again after reRingMinutes (moves remindAt, R2-1), but only up
                // to a cap: after that many unanswered rings the dose is marked missed and shows on
                // Today ("Taken after all" is still possible), instead of ringing every few minutes.
                val reRingMinutes = items.minOfOrNull { it.reRingMinutes } ?: 5
                val nextReRing = System.currentTimeMillis() + reRingMinutes * 60_000L
                val itemsById = items.associateBy { it.id }
                for (id in activeOccurrenceIds) {
                    val current = occurrenceRepository.getByIdSync(id)
                    if (current?.status == OccurrenceStatus.RINGING) {
                        val cap = maxRingsFor(itemsById[current.itemId]?.criticality)
                        if (current.ringCount >= cap) {
                            occurrenceRepository.markMissedTargeted(current.id)
                        } else {
                            occurrenceRepository.updateOccurrence(
                                current.copy(
                                    status = OccurrenceStatus.PENDING,
                                    remindAt = nextReRing,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
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

    /** Unanswered rings (first ring included) before a dose is marked missed. */
    private fun maxRingsFor(criticality: Criticality?): Int =
        if (criticality == Criticality.CRITICAL) MAX_RINGS_CRITICAL else MAX_RINGS_NORMAL

    /** The user's picked sound, or null for the phone default (also for old values like "alarm"). */
    private fun chosenSoundUri(setting: String?): android.net.Uri? =
        setting?.takeIf { it.contains("://") }?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }

    private fun startLoopingAlarmSound(chosen: android.net.Uri? = null) {
        val fallback = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        // A picked sound can disappear (file deleted, no access); fall back to the default.
        for (uri in listOfNotNull(chosen, fallback).distinct()) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, uri)
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
                return
            } catch (_: Exception) {
                runCatching { mediaPlayer?.release() }
                mediaPlayer = null
            }
        }
    }

    private fun playSingleGentleSound(chosen: android.net.Uri? = null) {
        try {
            val alertUri = chosen ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
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

    private fun speakReminders(
        occurrences: List<Occurrence>,
        items: List<Item>,
        profiles: List<Profile>
    ) {
        scope.launch {
            val itemsById = items.associateBy { it.id }
            val profilesById = profiles.associateBy { it.id }
            val phrases = occurrences.mapNotNull { occ ->
                val item = itemsById[occ.itemId] ?: return@mapNotNull null
                val profile = profilesById[occ.profileId]
                val schedule = scheduleRepository.getScheduleSync(occ.scheduleId)

                val profileName = profile?.name
                val doseAmount = effectiveDoseAmount(schedule, item)
                val doseWithUnit = listOf(doseAmount, item.dosageUnit).filter { it.isNotBlank() }.joinToString(" ")
                val itemAndDose = listOf(item.name, doseWithUnit).filter { it.isNotBlank() }.joinToString(", ")

                val relation = schedule?.relation
                val meal = schedule?.meal
                val timing = when {
                    schedule?.anchorType == ScheduleAnchorType.MEAL && relation != null && meal != null -> {
                        val rel = relation.name.lowercase()
                        val mealName = meal.name.lowercase()
                        "$rel $mealName"
                    }
                    else -> null
                }
                val instructions = item.instructions?.takeIf { it.isNotBlank() }
                val timingAndInstructions = listOfNotNull(timing, instructions).joinToString(", ")

                val parts = listOfNotNull(
                    profileName?.takeIf { it.isNotBlank() },
                    itemAndDose.takeIf { it.isNotBlank() },
                    timingAndInstructions.takeIf { it.isNotBlank() }
                )
                parts.joinToString(", ")
            }

            if (phrases.isEmpty()) return@launch

            val speechText = phrases.joinToString(". ")

            handler.post {
                try {
                    if (textToSpeech == null) {
                        textToSpeech = TextToSpeech(applicationContext) { status ->
                            if (status == TextToSpeech.SUCCESS) {
                                isTtsReady = true
                                textToSpeech?.language = Locale.getDefault()
                                // Speak on the alarm stream so it is heard when media volume is low.
                                textToSpeech?.setAudioAttributes(
                                    android.media.AudioAttributes.Builder()
                                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build()
                                )
                                textToSpeech?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "reminder_speech")
                            }
                        }
                    } else if (isTtsReady) {
                        textToSpeech?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "reminder_speech")
                    }
                } catch (_: Exception) {
                }
            }
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
        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        silenceRunnable?.let { handler.removeCallbacks(it) }
        stopSoundAndVibration()
        isTtsReady = false
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {
        }
        textToSpeech = null
        super.onDestroy()
    }
}
