package app.allinonereminder.gamify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.measureNanoTime

class PerformanceTest {

    @Test
    fun `one year of history for twenty items computes in under two hundred milliseconds`() {
        val start = LocalDate.of(2026, 1, 1)
        val records = (1..20).flatMap { item ->
            Fx.dailyTaken("item-$item", ItemType.HABIT, start, days = 365)
        }
        val input = GamificationInput(records, ZoneId.of("Europe/London"))

        repeat(3) { Gamification.compute(input) }

        val elapsedNanos = measureNanoTime { Gamification.compute(input) }

        assertTrue(
            elapsedNanos < 200_000_000L,
            "expected under 200 ms but took ${elapsedNanos / 1_000_000} ms",
        )
        assertEquals(20, Gamification.compute(input).streaks.size)
    }
}
