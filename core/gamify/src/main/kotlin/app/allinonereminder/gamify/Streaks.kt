package app.allinonereminder.gamify

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields

internal object StreakCalculator {

    private enum class DayState { COMPLETED, NEUTRAL, BROKEN }

    fun streaks(
        occurrences: List<OccurrenceRecord>,
        zone: ZoneId,
    ): List<ItemStreak> =
        occurrences
            .groupBy { it.itemId }
            .toSortedMap()
            .map { (itemId, records) -> streak(itemId, records, zone) }

    private fun streak(
        itemId: String,
        records: List<OccurrenceRecord>,
        zone: ZoneId,
    ): ItemStreak {
        val byDay = records
            .groupBy { it.dueAt.atZone(zone).toLocalDate() }
            .toSortedMap()

        var current = 0
        var longest = 0
        var freezesUsed = 0
        val frozenWeeks = mutableSetOf<Pair<Int, Int>>()

        for ((date, dayRecords) in byDay) {
            when (dayState(dayRecords)) {
                DayState.COMPLETED -> {
                    current += 1
                    if (current > longest) longest = current
                }
                DayState.NEUTRAL -> Unit
                DayState.BROKEN -> {
                    val week = isoWeek(date)
                    if (current > 0 && frozenWeeks.add(week)) {
                        freezesUsed += 1
                    } else {
                        current = 0
                    }
                }
            }
        }

        return ItemStreak(itemId, current, longest, freezesUsed)
    }

    private fun dayState(records: List<OccurrenceRecord>): DayState {
        var hasTaken = false
        var broken = false

        for (recordsForOccurrence in records.groupBy { it.occurrenceId }.values) {
            val taken = recordsForOccurrence.any {
                it.status == OccurrenceStatus.TAKEN
            }
            if (taken) {
                hasTaken = true
                continue
            }

            val onlyDoctorStopped = recordsForOccurrence.all {
                it.status == OccurrenceStatus.SKIPPED &&
                    it.skipReason == SkipReason.DOCTOR_STOPPED_IT
            }
            if (!onlyDoctorStopped) {
                broken = true
            }
        }

        return when {
            broken -> DayState.BROKEN
            hasTaken -> DayState.COMPLETED
            else -> DayState.NEUTRAL
        }
    }

    private fun isoWeek(date: LocalDate): Pair<Int, Int> {
        val fields = WeekFields.ISO
        return date.get(fields.weekBasedYear()) to
            date.get(fields.weekOfWeekBasedYear())
    }
}
