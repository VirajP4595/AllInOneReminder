package app.allinonereminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
import app.allinonereminder.widget.TodayWidgetUpdater
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
        const val ACTION_ALARM_FIRE = "app.allinonereminder.ACTION_ALARM_FIRE"
        const val EXTRA_OCCURRENCE_IDS = "occurrence_ids"
        const val REQUEST_CODE_ALARM = 1001
        const val REQUEST_CODE_SHOW_INTENT = 1002
    }

    private val mutex = Mutex()
    private val exceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
        CoroutineScope(Dispatchers.IO).launch {
            settingsRepository.recordRescheduleError(throwable.javaClass.simpleName, System.currentTimeMillis())
        }
    }
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exceptionHandler)

    fun rescheduleAsync(callerReason: String = "") {
        scope.launch {
            reschedule(callerReason)
        }
    }

    suspend fun reschedule(callerReason: String = ""): RescheduleResult {
        return mutex.withLock {
            try {
                val result = doReschedule(callerReason)
                settingsRepository.clearRescheduleError()
                settingsRepository.setNextAlarmAt(if (result.registered) result.dueAtEpochMillis else null)
                result
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                settingsRepository.recordRescheduleError(t.javaClass.simpleName, System.currentTimeMillis())
                // Try again in the background instead of waiting for the user to tap Retry.
                runCatching { RescheduleRetryWorker.enqueue(context) }
                throw t
            }
        }
    }

    private suspend fun doReschedule(callerReason: String): RescheduleResult {
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

        val plannedIds = plannedList.map { it.id }.toSet()
        val activeItemIds = items.map { it.id }.toSet()
        val activeScheduleIds = schedules.map { it.id }.toSet()

        // 1. Reconcile: mark SUPERSEDED for every unresolved row whose id isn't in the new plan,
        // or whose item/schedule/profile was deleted, or whose schedule time was updated (R2-8)
        val existingUnresolved = occurrenceRepository.getUnresolvedSync()
        val scheduleMap = schedules.associateBy { it.id }
        val obsoleteIds = existingUnresolved.filter { occ ->
            val item = itemRepository.getItemSync(occ.itemId)
            val schedule = scheduleMap[occ.scheduleId]
            if (item == null || !item.active) {
                true
            } else if (schedule != null && schedule.deletedAt != null) {
                true
            } else if (schedule != null && schedule.updatedAt > occ.updatedAt && occ.id !in plannedIds) {
                true
            } else if (occ.status == OccurrenceStatus.PENDING && occ.plannedAt >= now.toEpochMilli() && occ.id !in plannedIds) {
                true
            } else {
                false
            }
        }.map { it.id }

        if (obsoleteIds.isNotEmpty()) {
            occurrenceRepository.markSupersededTargeted(obsoleteIds)
        }

        // Look up existing rows by planned id (WHERE id IN (...)), not by a dueAt window
        val existingOccurrences = if (plannedList.isNotEmpty()) {
            occurrenceRepository.getByIdsSync(plannedList.map { it.id }).associateBy { it.id }
        } else {
            emptyMap()
        }

        val occurrencesToUpsert = mutableListOf<Occurrence>()

        for (planned in plannedList) {
            val existing = existingOccurrences[planned.id]
            if (existing != null) {
                // NEVER overwrite acted-on occurrences (TAKEN, SKIPPED, MISSED, SUPERSEDED)
                if (existing.status == OccurrenceStatus.TAKEN ||
                    existing.status == OccurrenceStatus.SKIPPED ||
                    existing.status == OccurrenceStatus.MISSED ||
                    existing.status == OccurrenceStatus.SUPERSEDED
                ) {
                    continue
                }
                // Re-planning only touches untouched PENDING rows (ringCount == 0, R2-1)
                // Rows that are SNOOZED, DEFERRED_MEAL, RINGING, or PENDING with ringCount > 0
                // keep their dueAt and remindAt.
                if (existing.status == OccurrenceStatus.PENDING && existing.ringCount == 0) {
                    if (existing.dueAt != planned.dueAt.toEpochMilli() ||
                        existing.plannedAt != planned.plannedAt.toEpochMilli() ||
                        existing.remindAt != planned.dueAt.toEpochMilli()
                    ) {
                        occurrencesToUpsert.add(
                            existing.copy(
                                plannedAt = planned.plannedAt.toEpochMilli(),
                                dueAt = planned.dueAt.toEpochMilli(),
                                remindAt = planned.dueAt.toEpochMilli(),
                                updatedAt = now.toEpochMilli()
                            )
                        )
                    }
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
                        remindAt = planned.dueAt.toEpochMilli(),
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

        // 2. Mark MISSED when now > max(plannedAt, dueAt) + graceMinutes, for all unresolved statuses
        val unresolvedAfterReconcile = occurrenceRepository.getUnresolvedSync()
        val itemMap = items.associateBy { it.id }

        for (occ in unresolvedAfterReconcile) {
            val item = itemMap[occ.itemId]
            val graceMillis = (item?.graceMinutes ?: 60) * 60_000L
            val effectiveAnchor = maxOf(occ.plannedAt, occ.dueAt)
            if (now.toEpochMilli() > (effectiveAnchor + graceMillis)) {
                occurrenceRepository.markMissedTargeted(occ.id)
            }
        }

        // 3. Find earliest remindAt among unresolved rows and register AlarmManager (R2-1)
        val updatedUnresolved = occurrenceRepository.getUnresolvedSync()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Rows the ring service is ringing right now are owned by it (its auto-silence puts them back
        // on one shared re-ring time). Counting them here would fire the alarm again immediately
        // each time anything is acted on, which is what made one set of reminders ring several times.
        // The ownership expires so a row left RINGING by a crash is still rescheduled.
        val nowMillis = now.toEpochMilli()
        val schedulable = updatedUnresolved.filterNot {
            it.status == OccurrenceStatus.RINGING && nowMillis - it.updatedAt < RINGING_OWNERSHIP_MILLIS
        }
        val earliestOccurrence = schedulable.minByOrNull { it.remindAt }

        val result = if (earliestOccurrence != null) {
            // Reminders due within a few minutes of each other ring together, once
            val bundleEnd = earliestOccurrence.remindAt + BUNDLE_WINDOW_MILLIS
            val simultaneousOccurrences = schedulable.filter { it.remindAt <= bundleEnd }
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

            // Clamp trigger time: coordinator must never register a trigger time earlier than now
            val triggerTime = maxOf(now.toEpochMilli(), earliestOccurrence.remindAt)

            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            try {
                if (canScheduleExact) {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, alarmPendingIntent)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, alarmPendingIntent)
                }
            } catch (e: SecurityException) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, alarmPendingIntent)
                } catch (e2: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, alarmPendingIntent)
                }
            }

            RescheduleResult(
                registered = true,
                dueAtEpochMillis = triggerTime,
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
                dueAtEpochMillis = 0L,
                occurrenceCount = 0
            )
        }

        TodayWidgetUpdater.update(context)

        return result
    }
}

/** Reminders due within this long of the earliest one are rung together as one group. */
private const val BUNDLE_WINDOW_MILLIS = 5 * 60_000L

/** A RINGING row younger than this belongs to the ring service and is not rescheduled. */
private const val RINGING_OWNERSHIP_MILLIS = 12 * 60_000L

data class RescheduleResult(
    val registered: Boolean,
    val dueAtEpochMillis: Long,
    val occurrenceCount: Int
)
