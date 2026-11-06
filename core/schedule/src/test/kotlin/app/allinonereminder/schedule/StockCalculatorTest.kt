package app.allinonereminder.schedule

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StockCalculatorTest {

    @Test
    fun calculateDaysOfSupply_normalCase() {
        // 12 units in stock, 1.0 unit per dose, 28 occurrences over 14 days (2/day)
        // 12 / 2.0 = 6.0 days
        val days = StockCalculator.calculateDaysOfSupply(
            currentStock = 12.0,
            unitsPerDose = 1.0,
            plannedOccurrencesCountIn14Days = 28
        )
        assertThat(days).isNotNull()
        assertThat(days!!).isWithin(0.001).of(6.0)
    }

    @Test
    fun formatStockStatus_producesExpectedText() {
        val status = StockCalculator.formatStockStatus(
            currentStock = 12.0,
            unitsPerDose = 1.0,
            plannedOccurrencesCountIn14Days = 28
        )
        assertThat(status).isEqualTo("12 left, about 6 days")
    }

    @Test
    fun formatStockStatus_clampsNegativeStockToZero() {
        val status = StockCalculator.formatStockStatus(
            currentStock = -3.0,
            unitsPerDose = 1.0,
            plannedOccurrencesCountIn14Days = 14
        )
        assertThat(status).isEqualTo("0 left, about 0 days")
    }

    @Test
    fun formatStockStatus_withDecimalStock() {
        val status = StockCalculator.formatStockStatus(
            currentStock = 12.5,
            unitsPerDose = 0.5,
            plannedOccurrencesCountIn14Days = 14
        )
        assertThat(status).isEqualTo("12.5 left, about 25 days")
    }

    @Test
    fun calculateDaysOfSupply_invalidUnits_defaultsToOne() {
        val days = StockCalculator.calculateDaysOfSupply(
            currentStock = 14.0,
            unitsPerDose = -1.0,
            plannedOccurrencesCountIn14Days = 14
        )
        assertThat(days).isNotNull()
        assertThat(days!!).isWithin(0.001).of(14.0)
    }

    @Test
    fun plan14DaysCounts_plansCorrectOccurrences() {
        val now = java.time.Instant.parse("2026-01-01T00:00:00Z")
        val zone = java.time.ZoneId.of("UTC")
        val item = app.allinonereminder.model.Item(
            id = "item-1",
            profileId = "prof-1",
            type = app.allinonereminder.model.ItemType.MEDICINE,
            name = "Aspirin",
            dosageAmount = "100",
            dosageUnit = "mg",
            criticality = app.allinonereminder.model.Criticality.NORMAL,
            graceMinutes = 60,
            reRingMinutes = 5,
            timePolicy = app.allinonereminder.model.TimePolicy.LOCAL_WALL_CLOCK,
            homeZoneId = "UTC",
            startDate = "2026-01-01",
            active = true,
            createdAt = 0L,
            updatedAt = 0L
        )
        val schedule = app.allinonereminder.model.Schedule(
            id = "sched-1",
            itemId = "item-1",
            anchorType = app.allinonereminder.model.ScheduleAnchorType.FIXED,
            minuteOfDay = 8 * 60,
            recurrence = app.allinonereminder.model.RecurrenceType.DAILY,
            anchorDate = "2026-01-01",
            createdAt = 0L,
            updatedAt = 0L
        )

        val counts = StockCalculator.plan14DaysCounts(
            items = listOf(item),
            schedules = listOf(schedule),
            now = now,
            zone = zone
        )

        assertThat(counts["item-1"]).isEqualTo(14)
    }

    @Test
    fun calculateDaysOfSupply_noPlannedDoses_returnsNull() {
        val days = StockCalculator.calculateDaysOfSupply(
            currentStock = 10.0,
            unitsPerDose = 1.0,
            plannedOccurrencesCountIn14Days = 0
        )
        assertThat(days).isNull()

        val status = StockCalculator.formatStockStatus(
            currentStock = 10.0,
            unitsPerDose = 1.0,
            plannedOccurrencesCountIn14Days = 0
        )
        assertThat(status).isEqualTo("10 left")
    }

    @Test
    fun calculateDaysOfSupply_halfTablet() {
        // 10 tablets in stock, 0.5 per dose, 14 doses in 14 days (1 dose/day = 0.5 tablet/day)
        // 10 / 0.5 = 20 days
        val days = StockCalculator.calculateDaysOfSupply(
            currentStock = 10.0,
            unitsPerDose = 0.5,
            plannedOccurrencesCountIn14Days = 14
        )
        assertThat(days).isNotNull()
        assertThat(days!!).isWithin(0.001).of(20.0)
    }

    @Test
    fun isLowStock_triggersOnRefillThresholdOrFiveDaysOrLess() {
        // Below threshold (threshold 5, stock 4)
        assertThat(StockCalculator.isLowStock(4.0, refillThreshold = 5.0, daysOfSupply = 10.0)).isTrue()

        // Above threshold, but daysOfSupply <= 5.0
        assertThat(StockCalculator.isLowStock(10.0, refillThreshold = 5.0, daysOfSupply = 4.5)).isTrue()

        // Neither condition met
        assertThat(StockCalculator.isLowStock(10.0, refillThreshold = 5.0, daysOfSupply = 8.0)).isFalse()

        // Days of supply exactly 5.0
        assertThat(StockCalculator.isLowStock(10.0, refillThreshold = null, daysOfSupply = 5.0)).isTrue()
    }
}
