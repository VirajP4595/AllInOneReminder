package app.allinonereminder.gamify

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DstTest {

    private val london = ZoneId.of("Europe/London")
    private val newYork = ZoneId.of("America/New_York")

    private fun dayLength(zone: ZoneId, date: LocalDate): Duration =
        Duration.between(
            date.atStartOfDay(zone),
            date.plusDays(1).atStartOfDay(zone),
        )

    private fun localTaken(
        zone: ZoneId,
        date: LocalDate,
        hour: Int = 9,
        itemId: String = "water",
    ): OccurrenceRecord {
        val at = date.atTime(hour, 0).atZone(zone).toInstant()
        return OccurrenceRecord(
            occurrenceId = "$itemId-$date-$hour",
            itemId = itemId,
            itemType = ItemType.WATER_DRINK,
            dueAt = at,
            status = OccurrenceStatus.TAKEN,
            actedAt = at,
        )
    }

    private fun localRecord(
        zone: ZoneId,
        date: LocalDate,
        hour: Int,
        minute: Int,
        id: String,
    ): OccurrenceRecord {
        val at = date.atTime(hour, minute).atZone(zone).toInstant()
        return OccurrenceRecord(
            occurrenceId = id,
            itemId = "water",
            itemType = ItemType.WATER_DRINK,
            dueAt = at,
            status = OccurrenceStatus.TAKEN,
            actedAt = at,
        )
    }

    private fun streakOverDst(
        zone: ZoneId,
        dstDate: LocalDate,
        expectedHours: Long,
    ) {
        assertEquals(Duration.ofHours(expectedHours), dayLength(zone, dstDate))

        val dates = listOf(
            dstDate.minusDays(1),
            dstDate,
            dstDate.plusDays(1),
        )
        val result = Gamification.compute(
            GamificationInput(dates.map { localTaken(zone, it) }, zone),
        )

        assertEquals(dates, result.rings.map { it.date })
        assertEquals(3, result.streaks.single().current)
        assertEquals(3, result.streaks.single().longest)
        assertEquals(100, result.rings[1].hydration.percent)
        assertEquals(1, result.rings[1].hydration.total)
    }

    @Test
    fun `Europe London spring forward day is twenty three hours and keeps a three day streak`() {
        streakOverDst(london, LocalDate.of(2026, 3, 29), expectedHours = 23)
    }

    @Test
    fun `Europe London fall back day is twenty five hours and keeps a three day streak`() {
        streakOverDst(london, LocalDate.of(2026, 10, 25), expectedHours = 25)
    }

    @Test
    fun `America New York spring forward day is twenty three hours and keeps a three day streak`() {
        streakOverDst(newYork, LocalDate.of(2026, 3, 8), expectedHours = 23)
    }

    @Test
    fun `America New York fall back day is twenty five hours and keeps a three day streak`() {
        streakOverDst(newYork, LocalDate.of(2026, 11, 1), expectedHours = 25)
    }

    @Test
    fun `both ends of the London spring forward day belong to the same local date`() {
        val date = LocalDate.of(2026, 3, 29)
        val result = Gamification.compute(
            GamificationInput(
                listOf(
                    localRecord(london, date, hour = 0, minute = 30, id = "start"),
                    localRecord(london, date, hour = 23, minute = 30, id = "end"),
                ),
                london,
            ),
        )

        val rings = result.rings.single()
        assertEquals(date, rings.date)
        assertEquals(2, rings.hydration.total)
        assertEquals(2, rings.hydration.onTime)
        assertEquals(100, rings.hydration.percent)
        assertEquals(20, result.totalPoints)
    }

    @Test
    fun `both occurrences of the repeated London fall back hour share one local date`() {
        val date = LocalDate.of(2026, 10, 25)
        val first = Instant.parse("2026-10-25T00:30:00Z")
        val second = Instant.parse("2026-10-25T01:30:00Z")

        assertEquals(date, first.atZone(london).toLocalDate())
        assertEquals(date, second.atZone(london).toLocalDate())

        val result = Gamification.compute(
            GamificationInput(
                listOf(
                    OccurrenceRecord(
                        occurrenceId = "first",
                        itemId = "water",
                        itemType = ItemType.WATER_DRINK,
                        dueAt = first,
                        status = OccurrenceStatus.TAKEN,
                        actedAt = first,
                    ),
                    OccurrenceRecord(
                        occurrenceId = "second",
                        itemId = "water",
                        itemType = ItemType.WATER_DRINK,
                        dueAt = second,
                        status = OccurrenceStatus.TAKEN,
                        actedAt = second,
                    ),
                ),
                london,
            ),
        )

        val rings = result.rings.single()
        assertEquals(date, rings.date)
        assertEquals(2, rings.hydration.total)
        assertEquals(100, rings.hydration.percent)
        assertEquals(20, result.totalPoints)
    }
}
