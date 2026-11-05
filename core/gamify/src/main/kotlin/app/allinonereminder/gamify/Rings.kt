package app.allinonereminder.gamify

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

internal object RingsCalculator {

    private val ON_TIME_WINDOW: Duration = Duration.ofMinutes(30)

    fun rings(
        occurrences: List<OccurrenceRecord>,
        zone: ZoneId,
    ): List<DailyRings> =
        occurrences
            .groupBy { it.dueAt.atZone(zone).toLocalDate() }
            .toSortedMap()
            .map { (date, dayRecords) ->
                DailyRings(
                    date = date,
                    meds = ring(dayRecords, RingCategory.MEDS),
                    hydration = ring(dayRecords, RingCategory.HYDRATION),
                    movement = ring(dayRecords, RingCategory.MOVEMENT),
                )
            }

    private fun ring(
        records: List<OccurrenceRecord>,
        category: RingCategory,
    ): RingProgress {
        val byOccurrence = records
            .filter { it.itemType.ringCategory == category }
            .groupBy { it.occurrenceId }

        val total = byOccurrence.size
        val onTime = byOccurrence.values.count { recordsForOccurrence ->
            val taken = recordsForOccurrence
                .filter { it.status == OccurrenceStatus.TAKEN }
                .minByOrNull { it.actedAt ?: Instant.MAX }
                ?: return@count false
            val actedAt = taken.actedAt ?: return@count false
            Duration.between(taken.dueAt, actedAt).abs() <= ON_TIME_WINDOW
        }

        return RingProgress(
            onTime = onTime,
            total = total,
            percent = percent(onTime, total),
        )
    }

    private fun percent(onTime: Int, total: Int): Int =
        if (total == 0) {
            100
        } else {
            ((onTime * 100.0) / total).roundToInt().coerceIn(0, 100)
        }
}
