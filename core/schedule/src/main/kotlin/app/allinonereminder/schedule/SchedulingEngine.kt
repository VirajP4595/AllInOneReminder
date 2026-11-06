package app.allinonereminder.schedule

import app.allinonereminder.model.DayType
import app.allinonereminder.model.Item
import app.allinonereminder.model.MealAnchor
import app.allinonereminder.model.MealRelation
import app.allinonereminder.model.MealSession
import app.allinonereminder.model.OccurrenceStatus
import app.allinonereminder.model.RecurrenceType
import app.allinonereminder.model.Schedule
import app.allinonereminder.model.ScheduleAnchorType
import app.allinonereminder.model.TimePolicy
import app.allinonereminder.model.UuidV5
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class PlannedOccurrence(
    val id: String,
    val itemId: String,
    val scheduleId: String,
    val profileId: String,
    val plannedAt: Instant,
    val dueAt: Instant,
    val status: OccurrenceStatus = OccurrenceStatus.PENDING,
    val ringCount: Int = 0
)

data class OccurrenceGroup(
    val dueAtMinute: Instant,
    val occurrences: List<PlannedOccurrence>
)

object SchedulingEngine {

    fun plan(
        items: List<Item>,
        schedules: List<Schedule>,
        anchors: List<MealAnchor>,
        mealSessions: List<MealSession> = emptyList(),
        from: Instant,
        to: Instant,
        deviceZone: ZoneId,
        now: Instant = from
    ): List<PlannedOccurrence> {
        val activeItems = items.filter { it.active && it.deletedAt == null }.associateBy { it.id }
        if (activeItems.isEmpty() || schedules.isEmpty()) return emptyList()

        // Index anchors by profileId -> (DayType, MealType) -> minuteOfDay
        val anchorMap = anchors.groupBy { it.profileId }.mapValues { (_, list) ->
            list.associate { (it.dayType to it.meal) to it.minuteOfDay }
        }

        // Index meal sessions by profileId -> (dateString, MealType) -> session
        val sessionMap = mealSessions.groupBy { it.profileId }.mapValues { (_, list) ->
            list.associate { (it.date to it.meal) to it }
        }

        val result = mutableListOf<PlannedOccurrence>()

        for (schedule in schedules) {
            if (schedule.deletedAt != null) continue
            if (schedule.recurrence == RecurrenceType.PRN) continue // as-needed items have no planned doses
            val item = activeItems[schedule.itemId] ?: continue

            val evalZone = if (item.timePolicy == TimePolicy.HOME_ZONE) {
                runCatching { ZoneId.of(item.homeZoneId) }.getOrDefault(deviceZone)
            } else {
                deviceZone
            }

            val itemStartDate = LocalDate.parse(item.startDate)
            val itemEndDate = item.endDate?.let { LocalDate.parse(it) }

            // Range of local dates covering window [from, to) with 1 day padding
            val windowStartDate = from.atZone(evalZone).toLocalDate().minusDays(1)
            val windowEndDate = to.atZone(evalZone).toLocalDate().plusDays(1)

            val iterStart = if (itemStartDate > windowStartDate) itemStartDate else windowStartDate
            val iterEnd = if (itemEndDate != null && itemEndDate < windowEndDate) itemEndDate else windowEndDate

            var currentDate = iterStart
            while (!currentDate.isAfter(iterEnd)) {
                if (isWithinValidity(schedule, currentDate) && matchesRecurrence(schedule, currentDate)) {
                    val minutes = resolveMinutesOfDay(schedule, currentDate, item.profileId, anchorMap)
                    for (minute in minutes) {
                        val secondOfDay = (minute * 60L).coerceIn(0L, 86399L)
                        val ldt = LocalDateTime.of(currentDate, LocalTime.ofSecondOfDay(secondOfDay))
                        val plannedInstant = toSafeInstant(ldt, evalZone)

                        var dueInstant = plannedInstant
                        var status = OccurrenceStatus.PENDING

                        if (schedule.anchorType == ScheduleAnchorType.MEAL && schedule.meal != null) {
                            val session = sessionMap[item.profileId]?.get(currentDate.toString() to schedule.meal)
                            if (session != null) {
                                val sessionStart = Instant.ofEpochMilli(session.startedAt)
                                val sessionEnd = session.endedAt?.let { Instant.ofEpochMilli(it) }

                                when (schedule.relation) {
                                    MealRelation.BEFORE, MealRelation.WITH, null -> {
                                        // Due now at startedAt if computed time is later than startedAt
                                        if (dueInstant > sessionStart) {
                                            dueInstant = sessionStart
                                        }
                                    }
                                    MealRelation.AFTER -> {
                                        val offsetDuration = Duration.ofMinutes(schedule.offsetMinutes?.toLong() ?: 0L)
                                        if (sessionEnd != null) {
                                            dueInstant = sessionEnd.plus(offsetDuration)
                                            status = OccurrenceStatus.PENDING
                                        } else {
                                            // Open session: safety fallback of startedAt + 90 min + offset
                                            val fallback = sessionStart.plus(90, ChronoUnit.MINUTES).plus(offsetDuration)
                                            if (now.isBefore(sessionStart.plus(90, ChronoUnit.MINUTES))) {
                                                status = OccurrenceStatus.DEFERRED_MEAL
                                            } else {
                                                status = OccurrenceStatus.PENDING
                                            }
                                            dueInstant = fallback
                                        }
                                    }
                                }
                            }
                        }

                        // Check window inclusion
                        if (!dueInstant.isBefore(from) && dueInstant.isBefore(to)) {
                            val id = UuidV5.generate(UuidV5.OCCURRENCE_NAMESPACE, "${schedule.id}_$ldt")
                            result.add(
                                PlannedOccurrence(
                                    id = id,
                                    itemId = item.id,
                                    scheduleId = schedule.id,
                                    profileId = item.profileId,
                                    plannedAt = plannedInstant,
                                    dueAt = dueInstant,
                                    status = status
                                )
                            )
                        }
                    }
                }
                currentDate = currentDate.plusDays(1)
            }
        }

        return result.sortedBy { it.dueAt }
    }

    private fun isWithinValidity(schedule: Schedule, date: LocalDate): Boolean {
        val from = schedule.validFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (from != null && date.isBefore(from)) return false
        val until = schedule.validUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (until != null && date.isAfter(until)) return false
        return true
    }

    private fun matchesRecurrence(schedule: Schedule, date: LocalDate): Boolean {
        return when (schedule.recurrence) {
            RecurrenceType.DAILY -> true
            RecurrenceType.WEEKDAYS -> date.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY
            RecurrenceType.EVERY_N_DAYS -> {
                val anchor = LocalDate.parse(schedule.anchorDate)
                if (date.isBefore(anchor)) false
                else {
                    val days = ChronoUnit.DAYS.between(anchor, date)
                    days % (schedule.intervalN?.coerceAtLeast(1) ?: 1) == 0L
                }
            }
            RecurrenceType.EVERY_N_WEEKS -> {
                val anchor = LocalDate.parse(schedule.anchorDate)
                val anchorMon = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val currentMon = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeks = ChronoUnit.WEEKS.between(anchorMon, currentMon)
                if (weeks < 0 || weeks % (schedule.intervalN?.coerceAtLeast(1) ?: 1) != 0L) {
                    false
                } else {
                    val mask = schedule.weekdayMask ?: 127
                    val bit = 1 shl (date.dayOfWeek.value - 1)
                    (bit and mask) != 0
                }
            }
            RecurrenceType.MONTHLY -> {
                val dayOfMonth = schedule.dayOfMonth
                val nthWeek = schedule.nthWeek
                when {
                    dayOfMonth != null -> {
                        // 31 becomes 30 in April and 28/29 in February.
                        val targetDay = dayOfMonth.coerceAtMost(date.lengthOfMonth())
                        date.dayOfMonth == targetDay
                    }
                    nthWeek != null -> {
                        val mask = schedule.weekdayMask ?: 0
                        val bit = 1 shl (date.dayOfWeek.value - 1)
                        if ((bit and mask) == 0) {
                            false
                        } else {
                            val week = nthWeek.coerceIn(1, 5)
                            if (week == 5) {
                                // Last occurrence of this weekday in the month.
                                date.plusWeeks(1).month != date.month
                            } else {
                                date.dayOfMonth in ((week - 1) * 7 + 1)..(week * 7)
                            }
                        }
                    }
                    else -> false
                }
            }
            // INTERVAL schedules every day; the dose times are produced by resolveMinutesOfDay.
            RecurrenceType.INTERVAL -> true
            // PRN is filtered out before planning; keep this branch for exhaustiveness.
            RecurrenceType.PRN -> false
            RecurrenceType.ONCE -> {
                schedule.onceDate?.let { LocalDate.parse(it) == date } ?: false
            }
        }
    }

    /**
     * Fixed INTERVAL schedules produce several doses a day: windowStart + k * intervalHours up to
     * and including windowEnd. Every other schedule produces at most one dose.
     */
    private fun resolveMinutesOfDay(
        schedule: Schedule,
        date: LocalDate,
        profileId: String,
        anchorMap: Map<String, Map<Pair<DayType, app.allinonereminder.model.MealType>, Int>>
    ): List<Int> {
        if (schedule.recurrence == RecurrenceType.INTERVAL && schedule.anchorType == ScheduleAnchorType.FIXED) {
            val step = (schedule.intervalHours ?: 1).coerceAtLeast(1) * 60
            val start = (schedule.windowStartMinute ?: schedule.minuteOfDay ?: return emptyList())
                .coerceIn(0, 1439)
            val end = (schedule.windowEndMinute ?: start).coerceIn(0, 1439)
            if (start > end) return emptyList()
            return generateSequence(start) { it + step }
                .takeWhile { it <= end }
                .toList()
        }
        val minute = resolveMinuteOfDay(schedule, date, profileId, anchorMap) ?: return emptyList()
        return listOf(minute)
    }

    private fun resolveMinuteOfDay(
        schedule: Schedule,
        date: LocalDate,
        profileId: String,
        anchorMap: Map<String, Map<Pair<DayType, app.allinonereminder.model.MealType>, Int>>
    ): Int? {
        return when (schedule.anchorType) {
            ScheduleAnchorType.FIXED -> schedule.minuteOfDay
            ScheduleAnchorType.MEAL -> {
                val meal = schedule.meal ?: return null
                val dayType = if (date.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                    DayType.WEEKEND
                } else {
                    DayType.WEEKDAY
                }
                val anchorMinute = anchorMap[profileId]?.get(dayType to meal) ?: return null
                val offset = schedule.offsetMinutes ?: 0
                when (schedule.relation) {
                    MealRelation.BEFORE -> anchorMinute - offset
                    MealRelation.WITH -> anchorMinute
                    MealRelation.AFTER -> anchorMinute + offset
                    null -> anchorMinute
                }
            }
        }
    }

    private fun toSafeInstant(localDateTime: LocalDateTime, zone: ZoneId): Instant {
        val rules = zone.rules
        val validOffsets = rules.getValidOffsets(localDateTime)
        val zdt = when {
            validOffsets.isEmpty() -> {
                // Nonexistent local time (gap): shift forward by the gap length
                val transition = rules.getTransition(localDateTime)
                localDateTime.plus(transition.duration)
                    .atOffset(transition.offsetAfter)
                    .atZoneSameInstant(zone)
            }
            validOffsets.size > 1 -> {
                // Ambiguous local time (overlap): use earlier offset as per spec
                val earlierOffset = if (validOffsets[0].totalSeconds >= validOffsets[1].totalSeconds) {
                    validOffsets[0]
                } else {
                    validOffsets[1]
                }
                ZonedDateTime.ofLocal(localDateTime, zone, earlierOffset)
            }
            else -> {
                ZonedDateTime.of(localDateTime, zone)
            }
        }
        return zdt.toInstant()
    }

    fun group(occurrences: List<PlannedOccurrence>): List<OccurrenceGroup> {
        return occurrences.groupBy { it.dueAt.truncatedTo(ChronoUnit.MINUTES) }
            .map { (minute, list) -> OccurrenceGroup(minute, list) }
            .sortedBy { it.dueAtMinute }
    }

    fun doseWarning(item: Item, lastTakenAt: Instant?, now: Instant): Duration? {
        val minGap = item.minGapMinutes ?: return null
        if (minGap <= 0 || lastTakenAt == null) return null
        val elapsed = Duration.between(lastTakenAt, now)
        val gapDuration = Duration.ofMinutes(minGap.toLong())
        return if (elapsed < gapDuration) {
            gapDuration.minus(elapsed)
        } else {
            null
        }
    }
}

