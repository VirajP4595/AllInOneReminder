package app.allinonereminder.gamify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class BadgesTest {

    private val start = LocalDate.of(2026, 1, 5)

    private fun badges(
        records: List<OccurrenceRecord>,
        steps: List<DailySteps> = emptyList(),
    ): List<BadgeAward> =
        Gamification.compute(
            GamificationInput(records, ZoneOffset.UTC, steps),
        ).badges

    @Test
    fun `streak badges are awarded at seven, thirty and one hundred days`() {
        val records = Fx.dailyTaken("item-100", ItemType.HABIT, start, 100) +
            Fx.dailyTaken("item-7", ItemType.HABIT, start, 7)

        val result = badges(records)
        val hundred = result.filter { it.itemId == "item-100" }.map { it.badge }
        val seven = result.filter { it.itemId == "item-7" }.map { it.badge }

        assertEquals(
            listOf(Badge.STREAK_7, Badge.STREAK_30, Badge.STREAK_100),
            hundred,
        )
        assertEquals(listOf(Badge.STREAK_7), seven)
    }

    @Test
    fun `hydration hero requires seven consecutive complete hydration days`() {
        val sevenDays = badges(
            Fx.dailyTaken("water", ItemType.WATER_DRINK, start, 7),
        )
        val sixDays = badges(
            Fx.dailyTaken("water", ItemType.WATER_DRINK, start, 6),
        )

        assertTrue(sevenDays.any { it.badge == Badge.HYDRATION_HERO })
        assertFalse(sixDays.any { it.badge == Badge.HYDRATION_HERO })
    }

    @Test
    fun `hydration hero needs a hydration occurrence on every day of the run`() {
        val records = Fx.dailyTaken("water", ItemType.WATER_DRINK, start, 6) +
            Fx.dailyTaken("pill", ItemType.MEDICINE, start.plusDays(6), 1)

        val result = badges(records)

        assertFalse(result.any { it.badge == Badge.HYDRATION_HERO })
    }

    @Test
    fun `perfect week requires all rings at one hundred percent for seven days`() {
        val perfect = Fx.dailyTaken("med", ItemType.MEDICINE, start, 7) +
            Fx.dailyTaken("water", ItemType.WATER_DRINK, start, 7) +
            Fx.dailyTaken("walk", ItemType.EXERCISE, start, 7)

        val missedOne = Fx.dailyTaken("med", ItemType.MEDICINE, start, 7)
            .toMutableList()
            .also { records ->
                records[3] = Fx.occurrence(
                    id = "med-${start.plusDays(3)}",
                    itemId = "med",
                    itemType = ItemType.MEDICINE,
                    dueAt = start.plusDays(3).atTime(9, 0)
                        .toInstant(ZoneOffset.UTC).toString(),
                    status = OccurrenceStatus.MISSED,
                )
            } +
            Fx.dailyTaken("water", ItemType.WATER_DRINK, start, 7) +
            Fx.dailyTaken("walk", ItemType.EXERCISE, start, 7)

        assertTrue(badges(perfect).any { it.badge == Badge.PERFECT_WEEK })
        assertFalse(badges(missedOne).any { it.badge == Badge.PERFECT_WEEK })
    }

    @Test
    fun `a gap between perfect days breaks the perfect week run`() {
        val records = Fx.dailyTaken("med", ItemType.MEDICINE, start, 4) +
            Fx.dailyTaken("med", ItemType.MEDICINE, start.plusDays(5), 4) +
            Fx.dailyTaken("water", ItemType.WATER_DRINK, start, 4) +
            Fx.dailyTaken("water", ItemType.WATER_DRINK, start.plusDays(5), 4) +
            Fx.dailyTaken("walk", ItemType.EXERCISE, start, 4) +
            Fx.dailyTaken("walk", ItemType.EXERCISE, start.plusDays(5), 4)

        assertFalse(badges(records).any { it.badge == Badge.PERFECT_WEEK })
    }

    @Test
    fun `ten thousand steps awards the steps badge from the optional input`() {
        val reached = badges(
            emptyList(),
            listOf(DailySteps(start, 10_000)),
        )
        val justShort = badges(
            emptyList(),
            listOf(DailySteps(start, 9_999)),
        )

        assertTrue(reached.any { it.badge == Badge.STEPS_10K })
        assertFalse(justShort.any { it.badge == Badge.STEPS_10K })
    }

    @Test
    fun `a hydrated week awards all three of its badges in a deterministic order`() {
        val result = badges(
            Fx.dailyTaken("water", ItemType.WATER_DRINK, start, 7),
        )

        assertEquals(
            listOf(
                BadgeAward(Badge.STREAK_7, "water"),
                BadgeAward(Badge.HYDRATION_HERO),
                BadgeAward(Badge.PERFECT_WEEK),
            ),
            result,
        )
    }
}
