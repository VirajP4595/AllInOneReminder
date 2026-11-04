package app.allinonereminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.allinonereminder.MainActivity
import app.allinonereminder.data.repository.ItemRepository
import app.allinonereminder.data.repository.MealAnchorRepository
import app.allinonereminder.data.repository.MealSessionRepository
import app.allinonereminder.data.repository.OccurrenceRepository
import app.allinonereminder.data.repository.ScheduleRepository
import app.allinonereminder.data.repository.SettingsRepository
import app.allinonereminder.model.Occurrence
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.schedule.SchedulingEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val itemRepository: ItemRepository,
    private val scheduleRepository: ScheduleRepository,
    private val mealAnchorRepository: MealAnchorRepository,
    private val mealSessionRepository: MealSessionRepository,
    private val occurrenceRepository: OccurrenceRepository,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val ACTION_ALARM_FIRE = "app.allinonereminder.action.ALARM_FIRE"
        const val EXTRA_OCCURRENCE_IDS = "extra_occurrence_ids"
        const val REQUEST_CODE_ALARM = 1001
        const val REQUEST_CODE_SHOW_INTENT = 1002
    }

    private val mutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun rescheduleAsync(callerReason: String = "") {
        scope.launch {
            reschedule(callerReason)
        }
    }

    suspend fun reschedule(callerReason: String = ""): RescheduleResult = mutex.withLock {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val deviceId = settingsRepository.getOrCreateDeviceId()

        // 1. Materialise occurrences: now - 6h ... now + 48h
        val windowFrom = now.minus(6, ChronoUnit.HOURS)
        val windowTo = now.plus(48, ChronoUnit.HOURS)

        val items = itemRepository.getAllActiveItemsSync()
        val schedules = scheduleRepository.getAllActiveSchedulesSync()
        val anchors = items.map { it.profileId }.distinct().flatMap {
            mealAnchorRepository.getAnchorsSync(it)
        }
        val todayStr = LocalDate.now(zone).toString()
        val mealSessions = items.map { it.profileId }.distinct().flatMap {
            mealSessionRepository.getSessionsForDateSync(it, todayStr)
        }

        val plannedList = SchedulingEngine.plan(
            items = items,
            schedules = schedules,
            anchors = anchors,
            mealSessions = mealSessions,
            from = windowFrom,
            to = windowTo,
            deviceZone = zone,
            now = now
        )

        val existingOccurrences = occurrenceRepository.getOccurrencesBetweenSync(
            windowFrom.toEpochMilli(),
            windowTo.toEpochMilli()
        ).associateBy { it.id }

        val occurrencesToUpsert = mutableListOf<Occurrence>()

        for (planned in plannedList) {
            val existing = existingOccurrences[planned.id]
            if (existing != null) {
                // NEVER overwrite acted-on occurrences (TAKEN, SKIPPED, MISSED)
                if (existing.status == OccurrenceStatus.TAKEN ||
                    existing.status == OccurrenceStatus.SKIPPED ||
                    existing.status == OccurrenceStatus.MISSED
                ) {
                    continue
                }
                // If it's deferred or pending, update due time or status if meal moved it
                if (existing.status != planned.status || existing.dueAt != planned.dueAt.toEpochMilli()) {
                    occurrencesToUpsert.add(
                        existing.copy(
                            dueAt = planned.dueAt.toEpochMilli(),
                            status = planned.status,
                            updatedAt = now.toEpochMilli()
                        )
                    )
                }
            } else {
                occurrencesToUpsert.add(
                    Occurrence(
                        id = planned.id,
                        itemId = planned.itemId,
                        scheduleId = planned.scheduleId,
                        profileId = planned.profileId,
                        plannedAt = planned.plannedAt.toEpochMilli(),
                        dueAt = planned.dueAt.toEpochMilli(),
                        status = planned.status,
                        ringCount = 0,
                        deviceId = deviceId,
                        updatedAt = now.toEpochMilli()
                    )
                )
            }
        }

        if (occurrencesToUpsert.isNotEmpty()) {
            occurrenceRepository.upsertOccurrences(occurrencesToUpsert)
        }

        // 2. Mark PENDING / SNOOZED / RINGING / DEFERRED_MEAL rows past grace period as MISSED
        val unresolved = occurrenceRepository.getUnresolvedSync()
        val itemMap = items.associateBy { it.id }

        for (occ in unresolved) {
            val item = itemMap[occ.itemId]
            val graceMillis = (item?.graceMinutes ?: 60) * 60_000L
            val effectiveBaseline = if (occ.status == OccurrenceStatus.DEFERRED_MEAL) {
                maxOf(occ.plannedAt, occ.dueAt)
            } else {
                occ.plannedAt
            }
            if (now.toEpochMilli() > (effectiveBaseline + graceMillis)) {
                occurrenceRepository.updateOccurrence(
                    occ.copy(
                        status = OccurrenceStatus.MISSED,
                        updatedAt = now.toEpochMilli()
                    )
                )
            }
        }

        // 3. Find earliest dueAt among unresolved rows and register AlarmManager.setAlarmClock()
        val updatedUnresolved = occurrenceRepository.getUnresolvedSync()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val earliestOccurrence = updatedUnresolved.minByOrNull { it.dueAt }

        if (earliestOccurrence != null) {
            // Group occurrences due at the same minute
            val dueMinute = earliestOccurrence.dueAt / 60_000L
            val simultaneousOccurrences = updatedUnresolved.filter {
                (it.dueAt / 60_000L) == dueMinute
            }
            val ids = simultaneousOccurrences.map { it.id }.toTypedArray()

            val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_ALARM_FIRE
                putExtra(EXTRA_OCCURRENCE_IDS, ids)
            }
            val alarmPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_ALARM,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val showIntent = Intent(context, MainActivity::class.java)
            val showPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE_SHOW_INTENT,
                showIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmClockInfo = AlarmManager.AlarmClockInfo(earliestOccurrence.dueAt, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, alarmPendingIntent)

            RescheduleResult(
                registered = true,
                dueAtEpochMillis = earliestOccurrence.dueAt,
                occurrenceCount = ids.size
            )
        } else {
            // No unresolved alarms; cancel previous alarm
            val cancelIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_ALARM_FIRE
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_ALARM,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(cancelPendingIntent)

            RescheduleResult(
                registered = false,
                dueAtEpochMillis = null,
                occurrenceCount = 0
            )
        }
    }
}

data class RescheduleResult(
    val registered: Boolean,
    val dueAtEpochMillis: Long?,
    val occurrenceCount: Int
)

