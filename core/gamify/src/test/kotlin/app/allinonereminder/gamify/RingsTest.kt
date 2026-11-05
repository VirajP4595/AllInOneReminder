package app.allinonereminder.gamify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class RingsTest {

    private fun rings(
        vararg records: OccurrenceRecord,
        zone: ZoneId = ZoneOffset.UTC,
    ): List<DailyRings> =
        Gamification.compute(
            GamificationInput(records.toList(), zone),
        ).rings

    @Test
    fun `rings count only on-time taken occurrences`() {
        val result = rings(
            Fx.taken(
                "on-time",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:00:00Z",
            ),
            Fx.taken(
                "late",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:31:00Z",
            ),
            Fx.occurrence(
                id = "missed",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T09:00:00Z",
                status = OccurrenceStatus.MISSED,
            ),
        ).single()

        assertEquals(1, result.meds.onTime)
        assertEquals(3, result.meds.total)
        assertEquals(33, result.meds.percent)
    }

    @Test
    fun `hydration and movement rings use their own item types`() {
        val result = rings(
            Fx.taken(
                "water",
                itemType = ItemType.WATER_DRINK,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:00:00Z",
            ),
            Fx.occurrence(
                id = "exercise",
                itemType = ItemType.EXERCISE,
                dueAt = "2026-01-05T09:00:00Z",
                status = OccurrenceStatus.MISSED,
            ),
        ).single()

        assertEquals(100, result.hydration.percent)
        assertEquals(1, result.hydration.total)
        assertEquals(0, result.movement.percent)
        assertEquals(1, result.movement.total)
    }

    @Test
    fun `a category with no occurrences is one hundred percent`() {
        val result = rings(
            Fx.taken(
                "med",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:00:00Z",
            ),
        ).single()

        assertEquals(100, result.hydration.percent)
        assertEquals(0, result.hydration.total)
        assertEquals(100, result.movement.percent)
        assertEquals(0, result.movement.total)
    }

    @Test
    fun `a day with only non-ring items still produces a rings row`() {
        val result = rings(
            Fx.taken(
                "habit",
                itemType = ItemType.HABIT,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:00:00Z",
            ),
        ).single()

        assertEquals(0, result.meds.total)
        assertEquals(0, result.hydration.total)
        assertEquals(0, result.movement.total)
    }

    @Test
    fun `duplicate actions count in the denominator but not as on time`() {
        val result = rings(
            Fx.taken(
                "taken",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:00:00Z",
            ),
            Fx.occurrence(
                id = "duplicate",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T09:00:00Z",
                status = OccurrenceStatus.DUPLICATE_ACTION,
            ),
        ).single()

        assertEquals(1, result.meds.onTime)
        assertEquals(2, result.meds.total)
        assertEquals(50, result.meds.percent)
    }

    @Test
    fun `a taken record without actedAt is not on time`() {
        val result = rings(
            Fx.occurrence(
                id = "no-action-time",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T09:00:00Z",
                status = OccurrenceStatus.TAKEN,
                actedAt = null,
            ),
        ).single()

        assertEquals(0, result.meds.onTime)
        assertEquals(1, result.meds.total)
        assertEquals(0, result.meds.percent)
    }

    @Test
    fun `rings group by local date in the requested zone`() {
        val result = rings(
            Fx.taken(
                "first",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T18:45:00Z",
                actedAt = "2026-01-05T18:45:00Z",
            ),
            Fx.taken(
                "second",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T19:15:00Z",
                actedAt = "2026-01-05T19:15:00Z",
            ),
            zone = ZoneId.of("Asia/Kolkata"),
        ).single()

        assertEquals(LocalDate.of(2026, 1, 6), result.date)
        assertEquals(2, result.meds.total)
        assertEquals(100, result.meds.percent)
    }

    @Test
    fun `percentages round to the nearest integer`() {
        val result = rings(
            Fx.taken("a", itemType = ItemType.MEDICINE, dueAt = "2026-01-05T09:00:00Z"),
            Fx.taken("b", itemType = ItemType.MEDICINE, dueAt = "2026-01-05T09:00:00Z"),
            Fx.occurrence(
                id = "c",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T09:00:00Z",
                status = OccurrenceStatus.MISSED,
            ),
        ).single()

        assertEquals(2, result.meds.onTime)
        assertEquals(3, result.meds.total)
        assertEquals(67, result.meds.percent)
    }
}
