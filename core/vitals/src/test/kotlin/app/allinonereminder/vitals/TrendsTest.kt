package app.allinonereminder.vitals

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS

class TrendsTest {

    private val now = Instant.parse("2026-06-30T12:00:00Z")

    private fun weight(id: String, daysAgo: Long, kg: Double): VitalReading =
        VitalReading.Weight(
            id = id,
            recordedAt = now.minus(daysAgo, DAYS),
            kg = kg,
        )

    private fun bloodPressure(
        id: String,
        systolic: Int,
        diastolic: Int,
        pulse: Int? = null,
    ): VitalReading = VitalReading.BloodPressure(
        id = id,
        recordedAt = now,
        systolic = systolic,
        diastolic = diastolic,
        pulse = pulse,
    )

    @Test
    fun `empty input has no summary`() {
        TrendWindow.entries.forEach { window ->
            assertNull(
                VitalTrends.summarize(
                    emptyList(),
                    VitalKind.WEIGHT,
                    window,
                    now,
                ),
            )
        }
    }

    @Test
    fun `a single reading has min average and max equal to its value`() {
        val summary = VitalTrends.summarize(
            listOf(weight("only", 1, 72.5)),
            VitalKind.WEIGHT,
            TrendWindow.SEVEN_DAYS,
            now,
        )

        assertEquals(1, summary?.count)
        assertEquals(72.5, summary?.min ?: 0.0, 0.0)
        assertEquals(72.5, summary?.average ?: 0.0, 0.0)
        assertEquals(72.5, summary?.max ?: 0.0, 0.0)
    }

    @Test
    fun `many readings give the min average max and count`() {
        val summary = VitalTrends.summarize(
            listOf(
                weight("a", 3, 80.0),
                weight("b", 1, 60.0),
                weight("c", 2, 70.0),
            ),
            VitalKind.WEIGHT,
            TrendWindow.SEVEN_DAYS,
            now,
        )

        assertEquals(3, summary?.count)
        assertEquals(60.0, summary?.min ?: 0.0, 0.0)
        assertEquals(70.0, summary?.average ?: 0.0, 0.0)
        assertEquals(80.0, summary?.max ?: 0.0, 0.0)
    }

    @Test
    fun `seven day window includes the cutoff and excludes older readings`() {
        val summary = VitalTrends.summarize(
            listOf(
                weight("today", 0, 70.0),
                weight("edge", 7, 71.0),
                weight("old", 8, 99.0),
            ),
            VitalKind.WEIGHT,
            TrendWindow.SEVEN_DAYS,
            now,
        )

        assertEquals(2, summary?.count)
        assertEquals(70.0, summary?.min ?: 0.0, 0.0)
        assertEquals(71.0, summary?.max ?: 0.0, 0.0)
    }

    @Test
    fun `future readings are ignored`() {
        val future = VitalReading.Weight(
            id = "future",
            recordedAt = now.plusSeconds(1),
            kg = 99.0,
        )
        val summary = VitalTrends.summarize(
            listOf(weight("now", 0, 70.0), future),
            VitalKind.WEIGHT,
            TrendWindow.SEVEN_DAYS,
            now,
        )

        assertEquals(1, summary?.count)
        assertEquals(70.0, summary?.max ?: 0.0, 0.0)
    }

    @Test
    fun `thirty and ninety day windows use their own cutoffs`() {
        val readings = listOf(
            weight("recent", 10, 70.0),
            weight("thirty-edge", 30, 71.0),
            weight("thirty-old", 31, 91.0),
            weight("ninety-edge", 90, 72.0),
            weight("ninety-old", 91, 92.0),
        )

        val thirty = VitalTrends.summarize(
            readings,
            VitalKind.WEIGHT,
            TrendWindow.THIRTY_DAYS,
            now,
        )
        val ninety = VitalTrends.summarize(
            readings,
            VitalKind.WEIGHT,
            TrendWindow.NINETY_DAYS,
            now,
        )

        assertEquals(2, thirty?.count)
        assertEquals(71.0, thirty?.max ?: 0.0, 0.0)
        assertEquals(4, ninety?.count)
        assertEquals(91.0, ninety?.max ?: 0.0, 0.0)
    }

    @Test
    fun `only readings of the requested kind are summarized`() {
        val summary = VitalTrends.summarize(
            listOf(
                weight("w", 1, 70.0),
                VitalReading.SpO2("s", now.minus(1, DAYS), 98),
            ),
            VitalKind.WEIGHT,
            TrendWindow.SEVEN_DAYS,
            now,
        )

        assertEquals(1, summary?.count)
        assertEquals(70.0, summary?.average ?: 0.0, 0.0)
    }

    @Test
    fun `all readings outside the window produce no summary`() {
        assertNull(
            VitalTrends.summarize(
                listOf(weight("old", 8, 70.0)),
                VitalKind.WEIGHT,
                TrendWindow.SEVEN_DAYS,
                now,
            ),
        )
    }

    @Test
    fun `blood pressure fields summarize systolic diastolic and pulse`() {
        val readings = listOf(
            bloodPressure("a", 120, 80, 70),
            bloodPressure("b", 130, 85, 75),
        )

        val systolic = VitalTrends.summarize(
            readings,
            VitalKind.BLOOD_PRESSURE,
            TrendWindow.SEVEN_DAYS,
            now,
        )
        val diastolic = VitalTrends.summarize(
            readings,
            VitalKind.BLOOD_PRESSURE,
            TrendWindow.SEVEN_DAYS,
            now,
            field = VitalField.BP_DIASTOLIC,
        )
        val pulse = VitalTrends.summarize(
            readings,
            VitalKind.BLOOD_PRESSURE,
            TrendWindow.SEVEN_DAYS,
            now,
            field = VitalField.BP_PULSE,
        )

        assertEquals(120.0, systolic?.min ?: 0.0, 0.0)
        assertEquals(130.0, systolic?.max ?: 0.0, 0.0)
        assertEquals(80.0, diastolic?.min ?: 0.0, 0.0)
        assertEquals(85.0, diastolic?.max ?: 0.0, 0.0)
        assertEquals(70.0, pulse?.min ?: 0.0, 0.0)
        assertEquals(75.0, pulse?.max ?: 0.0, 0.0)
    }

    @Test
    fun `a missing blood pressure pulse yields no pulse summary`() {
        assertNull(
            VitalTrends.summarize(
                listOf(bloodPressure("a", 120, 80)),
                VitalKind.BLOOD_PRESSURE,
                TrendWindow.SEVEN_DAYS,
                now,
                field = VitalField.BP_PULSE,
            ),
        )
    }
}
