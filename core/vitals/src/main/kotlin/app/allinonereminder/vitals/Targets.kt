package app.allinonereminder.vitals

data class TargetRange(
    val low: Double,
    val high: Double,
) {
    init {
        require(low <= high) { "target low must not exceed high" }
    }

    fun contains(value: Double): Boolean = value in low..high
}

/**
 * A range typed by the user (for example their own blood-pressure or glucose
 * goal). There are no built-in ranges: a reading is flagged only when the user
 * has supplied a matching target.
 */
data class UserTarget(
    val kind: VitalKind,
    val range: TargetRange,
    val field: VitalField = VitalField.PRIMARY,
    val label: String = "",
)

enum class TargetStatus { BELOW, WITHIN, ABOVE }

data class RangeCheck(
    val readingId: String,
    val target: UserTarget,
    val status: TargetStatus,
)

object TargetChecks {

    fun evaluate(
        reading: VitalReading,
        targets: List<UserTarget>,
    ): List<RangeCheck> =
        targets
            .filter { it.kind == reading.kind }
            .mapNotNull { target ->
                val value = reading.valueOf(target.field) ?: return@mapNotNull null
                val status = when {
                    value < target.range.low -> TargetStatus.BELOW
                    value > target.range.high -> TargetStatus.ABOVE
                    else -> TargetStatus.WITHIN
                }
                RangeCheck(reading.id, target, status)
            }
            .sortedWith(
                compareBy({ it.target.field.ordinal }, { it.target.label }),
            )
}
