package app.allinonereminder.gamify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class GamificationTest {

    private val start = LocalDate.of(2026, 1, 5)

    @Test
    fun `empty input produces no ledger streaks rings or badges`() {
        val result = Gamification.compute(
            GamificationInput(emptyList(), ZoneOffset.UTC),
        )

        assertTrue(result.ledger.isEmpty())
        assertTrue(result.streaks.isEmpty())
        assertTrue(result.rings.isEmpty())
        assertTrue(result.badges.isEmpty())
        assertEquals(0, result.totalPoints)
    }

    @Test
    fun `total points is the sum of the ledger entries`() {
        val records = Fx.dailyTaken("habit", ItemType.HABIT, start, days = 2) +
            Fx.dailyTaken("med", ItemType.MEDICINE, start, days = 1)

        val result = Gamification.compute(
            GamificationInput(records, ZoneOffset.UTC),
        )

        assertEquals(25, result.totalPoints)
        assertEquals(result.ledger.sumOf { it.points }, result.totalPoints)
        assertEquals(
            listOf(10, 10, 5),
            result.ledger.map { it.points },
        )
    }

    @Test
    fun `record order does not change any output`() {
        val records = Fx.dailyTaken("water", ItemType.WATER_DRINK, start, days = 8) +
            Fx.dailyTaken("med", ItemType.MEDICINE, start, days = 8) +
            Fx.occurrence(
                id = "missed",
                itemId = "walk",
                itemType = ItemType.EXERCISE,
                dueAt = start.atTime(9, 0).toInstant(ZoneOffset.UTC).toString(),
                status = OccurrenceStatus.MISSED,
            )

        val input = GamificationInput(records, ZoneOffset.UTC)
        val expected = Gamification.compute(input)
        val shuffled = Gamification.compute(
            input.copy(occurrences = records.shuffled(Random(1234))),
        )

        assertEquals(expected, shuffled)
    }

    @Test
    fun `criticality is accepted but only timing decides points`() {
        val critical = Fx.taken(
            id = "critical",
            itemType = ItemType.MEDICINE,
            dueAt = "2026-01-05T09:00:00Z",
            actedAt = "2026-01-05T09:00:00Z",
        ).copy(criticality = Criticality.CRITICAL)
        val gentle = critical.copy(
            occurrenceId = "gentle",
            criticality = Criticality.GENTLE,
        )

        val ledger = Gamification.compute(
            GamificationInput(listOf(critical, gentle), ZoneOffset.UTC),
        ).ledger

        assertEquals(listOf(5, 5), ledger.map { it.points })
        assertEquals(
            listOf(
                PointsReason.MEDICATION_ON_TIME,
                PointsReason.MEDICATION_ON_TIME,
            ),
            ledger.map { it.reason },
        )
    }

    @Test
    fun `a duplicate record never changes the taken points for the occurrence`() {
        val taken = Fx.taken(
            id = "dose",
            itemType = ItemType.MEDICINE,
            dueAt = "2026-01-05T09:00:00Z",
            actedAt = "2026-01-05T09:00:00Z",
        )
        val duplicate = Fx.occurrence(
            id = "dose",
            itemType = ItemType.MEDICINE,
            dueAt = "2026-01-05T09:00:00Z",
            status = OccurrenceStatus.DUPLICATE_ACTION,
        )

        val result = Gamification.compute(
            GamificationInput(listOf(duplicate, taken), ZoneOffset.UTC),
        )

        assertEquals(5, result.totalPoints)
        assertEquals(1, result.ledger.size)
    }
}
