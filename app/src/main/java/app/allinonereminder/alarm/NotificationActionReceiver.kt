package app.allinonereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.schedule.SchedulingEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_NOTIFICATION_TAKEN = "app.allinonereminder.action.NOTIFICATION_TAKEN"
        const val ACTION_NOTIFICATION_SNOOZE = "app.allinonereminder.action.NOTIFICATION_SNOOZE"
    }

    @Inject
    lateinit var occurrenceRepository: OccurrenceRepository

    @Inject
    lateinit var itemRepository: ItemRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var alarmCoordinator: AlarmCoordinator

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        val occurrenceIds = intent.getStringArrayExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS) ?: return
        val action = intent.action ?: return

        scope.launch {
            val now = Instant.now()
            val occurrences = occurrenceIds.mapNotNull { occurrenceRepository.getByIdSync(it) }

            when (action) {
                ACTION_NOTIFICATION_TAKEN -> {
                    // Double-dose check: ensure guard cannot be bypassed from notification action (§19)
                    var needsWarning = false
                    for (occ in occurrences) {
                        val item = itemRepository.getItemSync(occ.itemId) ?: continue
                        val lastTaken = occurrenceRepository.getLastTakenBeforeSync(item.id, now.toEpochMilli())
                        val lastTakenInstant = lastTaken?.actedAt?.let { Instant.ofEpochMilli(it) }
                        if (SchedulingEngine.doseWarning(item, lastTakenInstant, now) != null) {
                            needsWarning = true
                            break
                        }
                    }

                    if (needsWarning) {
                        // Launch AlarmActivity to force user to confirm through the blocking dialog
                        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
                            putExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS, occurrenceIds)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(activityIntent)
                        return@launch
                    }

                    // No warning: mark all taken
                    for (occ in occurrences) {
                        occurrenceRepository.updateOccurrence(
                            occ.copy(
                                status = OccurrenceStatus.TAKEN,
                                actedAt = now.toEpochMilli(),
                                updatedAt = now.toEpochMilli()
                            )
                        )
                    }
                    stopService(context)
                    alarmCoordinator.reschedule("NotificationAction: Taken")
                }

                ACTION_NOTIFICATION_SNOOZE -> {
                    val settings = settingsRepository.settingsFlow.first()
                    val snoozeMillis = settings.defaultSnoozeMinutes * 60_000L
                    val newDueAt = now.toEpochMilli() + snoozeMillis

                    for (occ in occurrences) {
                        occurrenceRepository.updateOccurrence(
                            occ.copy(
                                status = OccurrenceStatus.SNOOZED,
                                dueAt = newDueAt,
                                updatedAt = now.toEpochMilli()
                            )
                        )
                    }
                    stopService(context)
                    alarmCoordinator.reschedule("NotificationAction: Snooze")
                }
            }
        }
    }

    private fun stopService(context: Context) {
        val serviceIntent = Intent(context, AlarmRingService::class.java)
        context.stopService(serviceIntent)
    }
}

