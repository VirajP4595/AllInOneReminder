package app.allinonereminder.gamify

import java.time.Duration
import java.time.Instant

internal object PointsCalculator {

    private val ON_TIME_WINDOW: Duration = Duration.ofMinutes(30)

    fun ledger(occurrences: List<OccurrenceRecord>): List<PointsEntry> =
        occurrences
            .groupBy { it.occurrenceId }
            .toSortedMap()
            .map { (occurrenceId, records) -> entry(occurrenceId, records) }

    private fun entry(
        occurrenceId: String,
        records: List<OccurrenceRecord>,
    ): PointsEntry {
        val itemId = records.minOf { it.itemId }
        val taken = records
            .filter { it.status == OccurrenceStatus.TAKEN }
            .minByOrNull { it.actedAt ?: Instant.MAX }

        if (taken != null) {
            return takenPoints(occurrenceId, itemId, taken)
        }

        val reason = when {
            records.any { it.skipReason == SkipReason.DOCTOR_STOPPED_IT } ->
                PointsReason.DOCTOR_STOPPED
            records.any { it.status == OccurrenceStatus.DUPLICATE_ACTION } ->
                PointsReason.DUPLICATE_ACTION
            else -> PointsReason.NOT_TAKEN
        }
        return PointsEntry(occurrenceId, itemId, 0, reason)
    }

    private fun takenPoints(
        occurrenceId: String,
        itemId: String,
        record: OccurrenceRecord,
    ): PointsEntry {
        if (!record.itemType.isHabitItem && !record.itemType.isMedication) {
            return PointsEntry(occurrenceId, itemId, 0, PointsReason.NOT_GAMIFIED)
        }

        val actedAt = record.actedAt
            ?: return PointsEntry(
                occurrenceId,
                itemId,
                0,
                PointsReason.MISSING_ACTED_AT,
            )

        val delta = Duration.between(record.dueAt, actedAt)
        val onTime = delta.abs() <= ON_TIME_WINDOW

        return when {
            record.itemType.isHabitItem && onTime ->
                PointsEntry(occurrenceId, itemId, 10, PointsReason.HABIT_ON_TIME)
            record.itemType.isHabitItem && isLateWithinGrace(record, actedAt, delta) ->
                PointsEntry(
                    occurrenceId,
                    itemId,
                    5,
                    PointsReason.HABIT_LATE_IN_GRACE,
                )
            record.itemType.isMedication && onTime ->
                PointsEntry(
                    occurrenceId,
                    itemId,
                    5,
                    PointsReason.MEDICATION_ON_TIME,
                )
            else -> PointsEntry(
                occurrenceId,
                itemId,
                0,
                PointsReason.OUTSIDE_WINDOW,
            )
        }
    }

    private fun isLateWithinGrace(
        record: OccurrenceRecord,
        actedAt: Instant,
        delta: Duration,
    ): Boolean {
        if (delta <= Duration.ZERO) return false
        val grace = record.graceMinutes.coerceAtLeast(0).toLong()
        return !actedAt.isAfter(record.dueAt.plusSeconds(grace * 60))
    }
}
