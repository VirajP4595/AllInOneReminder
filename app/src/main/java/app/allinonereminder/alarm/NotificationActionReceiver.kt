package app.allinonereminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.MealSessionRepository
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.MealRelation
import app.allinonereminder.model.MealType
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.ScheduleAnchorType
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
        const val ACTION_NOTIFICATION_MEAL_DONE = "app.allinonereminder.action.NOTIFICATION_MEAL_DONE"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_MEAL_TYPE = "extra_meal_type"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_FINISHED_AT = "extra_finished_at"
    }

    @Inject
    lateinit var occurrenceRepository: OccurrenceRepository

    @Inject
    lateinit var itemRepository: ItemRepository

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    @Inject
    lateinit var mealSessionRepository: MealSessionRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var alarmCoordinator: AlarmCoordinator

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        scope.launch {
            handleAction(context, intent)
        }
    }

    suspend fun handleAction(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val now = Instant.now()

        if (action == ACTION_NOTIFICATION_MEAL_DONE) {
            val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
            val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
            val mealTypeStr = intent.getStringExtra(EXTRA_MEAL_TYPE)
            val mealType = mealTypeStr?.let { runCatching { MealType.valueOf(it) }.getOrNull() }

            val finishedAtExtra = intent.getLongExtra(EXTRA_FINISHED_AT, -1L)
            val endedAt = if (finishedAtExtra > 0) {
                if (sessionId != null) {
                    mealSessionRepository.endMealSession(sessionId, finishedAtExtra)
                }
                finishedAtExtra
            } else {
                if (sessionId != null) {
                    mealSessionRepository.endMealSession(sessionId, now.toEpochMilli())
                }
                now.toEpochMilli()
            }
            AlarmNotificationHelper.cancelMealNotification(context)

            if (profileId != null && mealType != null) {
                val unresolved = occurrenceRepository.getUnresolvedSync().filter { it.profileId == profileId }
                val schedules = scheduleRepository.getAllActiveSchedulesSync().associateBy { it.id }

                for (occ in unresolved) {
                    val sched = schedules[occ.scheduleId]
                    if (sched != null && sched.anchorType == ScheduleAnchorType.MEAL && sched.meal == mealType) {
                        if (sched.relation == MealRelation.AFTER || occ.status == OccurrenceStatus.DEFERRED_MEAL) {
                            val offsetMillis = (sched.offsetMinutes ?: 0) * 60_000L
                            val newDue = endedAt + offsetMillis
                            occurrenceRepository.updateOccurrence(
                                occ.copy(
                                    status = OccurrenceStatus.PENDING,
                                    plannedAt = newDue,
                                    dueAt = newDue,
                                    updatedAt = now.toEpochMilli()
                                )
                            )
                        }
                    }
                }
            }

            alarmCoordinator.reschedule("NotificationAction: Meal Done")
            return
        }

        val occurrenceIds = intent.getStringArrayExtra(AlarmCoordinator.EXTRA_OCCURRENCE_IDS) ?: return
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
                    return
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

    private fun stopService(context: Context) {
        val serviceIntent = Intent(context, AlarmRingService::class.java)
        context.stopService(serviceIntent)
    }
}

