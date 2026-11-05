package app.allinonereminder.gamify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StreakTest {

    private val start = LocalDate.of(2026, 1, 5) // Monday, ISO week 2

    private fun streaks(vararg records: OccurrenceRecord): List<ItemStreak> =
        Gamification.compute(
            GamificationInput(records.toList(), ZoneOffset.UTC),
        ).streaks

    private fun day(
        date: LocalDate,
        status: OccurrenceStatus,
        itemId: String = "item-1",
        skipReason: SkipReason? = null,
        suffix: String = "",
    ): OccurrenceRecord = Fx.occurrence(
        id = "$itemId-$date$suffix",
        itemId = itemId,
        itemType = ItemType.HABIT,
        dueAt = date.atTime(9, 0).toInstant(ZoneOffset.UTC).toString(),
        status = status,
        actedAt = if (status == OccurrenceStatus.TAKEN) {
            date.atTime(9, 0).toInstant(ZoneOffset.UTC).toString()
        } else {
            null
        },
        skipReason = skipReason,
    )

    @Test
    fun `consecutive taken days build a current and longest streak`() {
        val result = streaks(
            *Fx.dailyTaken("item-1", ItemType.HABIT, start, 3).toTypedArray(),
        ).single()

        assertEquals(3, result.current)
        assertEquals(3, result.longest)
        assertEquals(0, result.freezesUsed)
    }

    @Test
    fun `a missed day uses the one weekly freeze`() {
        val result = streaks(
            day(start, OccurrenceStatus.TAKEN),
            day(start.plusDays(1), OccurrenceStatus.MISSED),
            day(start.plusDays(2), OccurrenceStatus.TAKEN),
        ).single()

        assertEquals(2, result.current)
        assertEquals(2, result.longest)
        assertEquals(1, result.freezesUsed)
    }

    @Test
    fun `a second missed day in the same ISO week breaks the streak`() {
        val result = streaks(
            day(start, OccurrenceStatus.TAKEN),
            day(start.plusDays(1), OccurrenceStatus.TAKEN),
            day(start.plusDays(2), OccurrenceStatus.MISSED),
            day(start.plusDays(3), OccurrenceStatus.MISSED),
            day(start.plusDays(4), OccurrenceStatus.TAKEN),
        ).single()

        assertEquals(1, result.current)
        assertEquals(2, result.longest)
        assertEquals(1, result.freezesUsed)
    }

    @Test
    fun `a new ISO week provides a fresh freeze`() {
        val result = streaks(
            day(start, OccurrenceStatus.TAKEN),
            day(start.plusDays(1), OccurrenceStatus.MISSED),
            day(start.plusDays(2), OccurrenceStatus.TAKEN),
            day(start.plusDays(3), OccurrenceStatus.TAKEN),
            day(start.plusDays(4), OccurrenceStatus.TAKEN),
            day(start.plusDays(5), OccurrenceStatus.TAKEN),
            day(start.plusDays(6), OccurrenceStatus.TAKEN),
            day(start.plusDays(7), OccurrenceStatus.MISSED),
            day(start.plusDays(8), OccurrenceStatus.TAKEN),
        ).single()

        assertEquals(7, result.current)
        assertEquals(7, result.longest)
        assertEquals(2, result.freezesUsed)
    }

    @Test
    fun `a doctor stopped skip neither breaks nor extends the streak`() {
        val result = streaks(
            day(start, OccurrenceStatus.TAKEN),
            day(
                start.plusDays(1),
                OccurrenceStatus.SKIPPED,
                skipReason = SkipReason.DOCTOR_STOPPED_IT,
            ),
            day(start.plusDays(2), OccurrenceStatus.TAKEN),
        ).single()

        assertEquals(2, result.current)
        assertEquals(2, result.longest)
        assertEquals(0, result.freezesUsed)
    }

    @Test
    fun `a run of doctor stopped days preserves the streak without extending it`() {
        val result = streaks(
            day(start, OccurrenceStatus.TAKEN),
            day(
                start.plusDays(1),
                OccurrenceStatus.SKIPPED,
                skipReason = SkipReason.DOCTOR_STOPPED_IT,
            ),
            day(
                start.plusDays(2),
                OccurrenceStatus.SKIPPED,
                skipReason = SkipReason.DOCTOR_STOPPED_IT,
            ),
            day(start.plusDays(3), OccurrenceStatus.TAKEN),
        ).single()

        assertEquals(2, result.current)
        assertEquals(2, result.longest)
    }

    @Test
    fun `a duplicate action day breaks the streak and consumes the freeze`() {
        val result = streaks(
            day(start, OccurrenceStatus.TAKEN),
            day(start.plusDays(1), OccurrenceStatus.DUPLICATE_ACTION),
            day(start.plusDays(2), OccurrenceStatus.TAKEN),
        ).single()

        assertEquals(2, result.current)
        assertEquals(2, result.longest)
        assertEquals(1, result.freezesUsed)
    }

    @Test
    fun `an item with no completed day has a zero streak`() {
        val result = streaks(
            day(start, OccurrenceStatus.MISSED),
        ).single()

        assertEquals(0, result.current)
        assertEquals(0, result.longest)
        assertEquals(0, result.freezesUsed)
    }

    @Test
    fun `a calendar gap with no occurrence does not break the streak`() {
        val result = streaks(
            day(start, OccurrenceStatus.TAKEN),
            day(start.plusDays(2), OccurrenceStatus.TAKEN),
        ).single()

        assertEquals(2, result.current)
        assertEquals(2, result.longest)
    }

    @Test
    fun `a day with a taken occurrence and a doctor stopped skip counts as completed`() {
        val result = streaks(
            day(start, OccurrenceStatus.TAKEN),
            day(
                start,
                OccurrenceStatus.SKIPPED,
                skipReason = SkipReason.DOCTOR_STOPPED_IT,
                suffix = "-doctor",
            ),
        ).single()

        assertEquals(1, result.current)
        assertEquals(1, result.longest)
    }

    @Test
    fun `streaks are tracked per item`() {
        val otherStart = LocalDate.of(2026, 2, 2)
        val result = streaks(
            *Fx.dailyTaken("item-a", ItemType.HABIT, start, 3).toTypedArray(),
            *Fx.dailyTaken("item-b", ItemType.HABIT, otherStart, 1).toTypedArray(),
        )

        assertEquals(listOf("item-a", "item-b"), result.map { it.itemId })
        assertEquals(listOf(3, 1), result.map { it.current })
    }
}
