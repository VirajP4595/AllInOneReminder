package app.allinonereminder.schedule

import kotlin.math.roundToInt

object StockCalculator {

    /**
     * Calculates days of supply based on current stock, units per dose, and the number
     * of planned occurrences over the next 14 days.
     * Days of supply = stock / dailyUnitsNeeded,
     * where dailyUnitsNeeded = (plannedCount / 14.0) * unitsPerDose.
     */
    fun calculateDaysOfSupply(
        currentStock: Double,
        unitsPerDose: Double?,
        plannedOccurrencesCountIn14Days: Int
    ): Double? {
        val clampedStock = maxOf(0.0, currentStock)
        if (plannedOccurrencesCountIn14Days <= 0) return null
        val units = if (unitsPerDose != null && unitsPerDose > 0.0) unitsPerDose else 1.0
        val averageDosesPerDay = plannedOccurrencesCountIn14Days / 14.0
        val dailyUnits = averageDosesPerDay * units
        if (dailyUnits <= 0.0) return null
        return clampedStock / dailyUnits
    }

    /**
     * Formats stock for display (e.g., "12 left, about 6 days", or "12 left").
     * Display stock is always clamped at >= 0.
     */
    fun formatStockStatus(
        currentStock: Double,
        unitsPerDose: Double?,
        plannedOccurrencesCountIn14Days: Int
    ): String {
        val clamped = maxOf(0.0, currentStock)
        val stockStr = if (clamped % 1.0 == 0.0) clamped.toInt().toString() else clamped.toString()
        val days = calculateDaysOfSupply(clamped, unitsPerDose, plannedOccurrencesCountIn14Days)
        return if (days != null) {
            val daysInt = days.roundToInt()
            "$stockStr left, about $daysInt days"
        } else {
            "$stockStr left"
        }
    }

    /**
     * True if current stock is at or below threshold OR days of supply is 5 or fewer.
     */
    fun isLowStock(
        currentStock: Double,
        refillThreshold: Double?,
        daysOfSupply: Double?
    ): Boolean {
        if (refillThreshold != null && currentStock <= refillThreshold) {
            return true
        }
        if (daysOfSupply != null && daysOfSupply <= 5.0) {
            return true
        }
        return false
    }

    fun plan14DaysCounts(
        items: List<app.allinonereminder.model.Item>,
        schedules: List<app.allinonereminder.model.Schedule>,
        anchors: List<app.allinonereminder.model.MealAnchor> = emptyList(),
        now: java.time.Instant = java.time.Instant.now(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    ): Map<String, Int> {
        val planned = SchedulingEngine.plan(
            items = items,
            schedules = schedules,
            anchors = anchors,
            from = now,
            to = now.plus(14, java.time.temporal.ChronoUnit.DAYS),
            deviceZone = zone,
            now = now
        )
        return planned.groupingBy { it.itemId }.eachCount()
    }
}
