package app.allinonereminder.gamify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class PointsTest {

    private fun ledger(vararg records: OccurrenceRecord): List<PointsEntry> =
        Gamification.compute(
            GamificationInput(records.toList(), ZoneOffset.UTC),
        ).ledger

    @Test
    fun `habit taken at either edge of the thirty minute window earns ten`() {
        val entries = ledger(
            Fx.taken(
                "early-edge",
                itemType = ItemType.HABIT,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T08:30:00Z",
            ),
            Fx.taken(
                "late-edge",
                itemType = ItemType.HABIT,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:30:00Z",
            ),
        )

        assertEquals(
            listOf(10, 10),
            entries.map { it.points },
        )
        assertEquals(
            listOf(PointsReason.HABIT_ON_TIME, PointsReason.HABIT_ON_TIME),
            entries.map { it.reason },
        )
    }

    @Test
    fun `habit taken late but inside grace earns five`() {
        val entries = ledger(
            Fx.taken(
                "just-late",
                itemType = ItemType.HABIT,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:31:00Z",
            ),
            Fx.taken(
                "grace-edge",
                itemType = ItemType.HABIT,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T10:00:00Z",
                graceMinutes = 60,
            ),
        )

        assertEquals(listOf(5, 5), entries.map { it.points })
        assertEquals(
            listOf(
                PointsReason.HABIT_LATE_IN_GRACE,
                PointsReason.HABIT_LATE_IN_GRACE,
            ),
            entries.map { it.reason },
        )
    }

    @Test
    fun `habit taken after grace earns zero`() {
        val entries = ledger(
            Fx.taken(
                "after-grace",
                itemType = ItemType.HABIT,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T10:01:00Z",
                graceMinutes = 60,
            ),
        )

        assertEquals(0, entries.single().points)
        assertEquals(PointsReason.OUTSIDE_WINDOW, entries.single().reason)
    }

    @Test
    fun `habit taken more than thirty minutes early earns zero`() {
        val entries = ledger(
            Fx.taken(
                "too-early",
                itemType = ItemType.HABIT,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T08:29:00Z",
            ),
        )

        assertEquals(0, entries.single().points)
        assertEquals(PointsReason.OUTSIDE_WINDOW, entries.single().reason)
    }

    @Test
    fun `negative grace cannot award late habit points`() {
        val entries = ledger(
            Fx.taken(
                "negative-grace",
                itemType = ItemType.HABIT,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:31:00Z",
                graceMinutes = -5,
            ),
        )

        assertEquals(0, entries.single().points)
        assertEquals(PointsReason.OUTSIDE_WINDOW, entries.single().reason)
    }

    @Test
    fun `medication taken inside thirty minutes earns five`() {
        val entries = ledger(
            Fx.taken(
                "med-early-edge",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T08:30:00Z",
            ),
            Fx.taken(
                "med-late-edge",
                itemType = ItemType.INJECTION,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:30:00Z",
            ),
        )

        assertEquals(listOf(5, 5), entries.map { it.points })
        assertEquals(
            listOf(
                PointsReason.MEDICATION_ON_TIME,
                PointsReason.MEDICATION_ON_TIME,
            ),
            entries.map { it.reason },
        )
    }

    @Test
    fun `late medicine earns zero even inside grace`() {
        val entries = ledger(
            Fx.taken(
                "late-med",
                itemType = ItemType.MEDICINE,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:31:00Z",
                graceMinutes = 120,
            ),
        )

        assertEquals(0, entries.single().points)
        assertEquals(PointsReason.OUTSIDE_WINDOW, entries.single().reason)
    }

    @Test
    fun `duplicate action earns zero`() {
        val entries = ledger(
            Fx.occurrence(
                id = "duplicate",
                itemType = ItemType.HABIT,
                status = OccurrenceStatus.DUPLICATE_ACTION,
            ),
        )

        assertEquals(0, entries.single().points)
        assertEquals(PointsReason.DUPLICATE_ACTION, entries.single().reason)
    }

    @Test
    fun `a taken record and a duplicate for the same occurrence make one ledger entry`() {
        val entries = ledger(
            Fx.taken(
                id = "same-occurrence",
                itemType = ItemType.HABIT,
                dueAt = "2026-01-05T09:00:00Z",
                actedAt = "2026-01-05T09:00:00Z",
            ),
            Fx.occurrence(
                id = "same-occurrence",
                itemType = ItemType.HABIT,
                status = OccurrenceStatus.DUPLICATE_ACTION,
            ),
        )

        assertEquals(1, entries.size)
        assertEquals(10, entries.single().points)
    }

    @Test
    fun `skipped occurrences earn zero with the right reason`() {
        val entries = ledger(
            Fx.occurrence(
                id = "skipped",
                status = OccurrenceStatus.SKIPPED,
                skipReason = SkipReason.OTHER,
            ),
            Fx.occurrence(
                id = "doctor",
                status = OccurrenceStatus.SKIPPED,
                skipReason = SkipReason.DOCTOR_STOPPED_IT,
            ),
            Fx.occurrence(
                id = "missed",
                status = OccurrenceStatus.MISSED,
            ),
            Fx.occurrence(
                id = "pending",
                status = OccurrenceStatus.PENDING,
            ),
        )

        assertEquals(listOf(0, 0, 0, 0), entries.map { it.points })
        assertEquals(
            listOf(
                PointsReason.DOCTOR_STOPPED,
                PointsReason.NOT_TAKEN,
                PointsReason.NOT_TAKEN,
                PointsReason.NOT_TAKEN,
            ),
            entries.map { it.reason },
        )
    }

    @Test
    fun `appointments and custom items are not gamified`() {
        val entries = ledger(
            Fx.taken(
                id = "appointment",
                itemType = ItemType.APPOINTMENT,
                actedAt = "2026-01-05T09:00:00Z",
            ),
            Fx.taken(
                id = "custom",
                itemType = ItemType.CUSTOM,
                actedAt = "2026-01-05T09:00:00Z",
            ),
        )

        assertEquals(listOf(0, 0), entries.map { it.points })
        assertEquals(
            listOf(PointsReason.NOT_GAMIFIED, PointsReason.NOT_GAMIFIED),
            entries.map { it.reason },
        )
    }

    @Test
    fun `a taken record without actedAt earns zero and is counted`() {
        val entries = ledger(
            Fx.occurrence(
                id = "no-action-time",
                itemType = ItemType.MEDICINE,
                status = OccurrenceStatus.TAKEN,
                actedAt = null,
            ),
        )

        assertEquals(0, entries.single().points)
        assertEquals(PointsReason.MISSING_ACTED_AT, entries.single().reason)
    }
}
