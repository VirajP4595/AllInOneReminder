package app.allinonereminder.vitals

import java.time.Instant
import java.time.temporal.ChronoUnit

enum class TrendWindow(val days: Long) {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
}

data class TrendSummary(
    val count: Int,
    val min: Double,
    val average: Double,
    val max: Double,
)

/**
 * Rolling summaries ending at the supplied instant. A reading exactly on the
 * cutoff is included; future readings are ignored. Empty input returns null.
 */
object VitalTrends {

    fun summarize(
        readings: List<VitalReading>,
        kind: VitalKind,
        window: TrendWindow,
        now: Instant,
        field: VitalField = VitalField.PRIMARY,
    ): TrendSummary? {
        val cutoff = now.minus(window.days, ChronoUnit.DAYS)
        val values = readings
            .asSequence()
            .filter { it.kind == kind }
            .filter { !it.recordedAt.isBefore(cutoff) }
            .filter { !it.recordedAt.isAfter(now) }
            .mapNotNull { it.valueOf(field) }
            .toList()

        if (values.isEmpty()) return null

        return TrendSummary(
            count = values.size,
            min = values.min(),
            average = values.average(),
            max = values.max(),
        )
    }
}
