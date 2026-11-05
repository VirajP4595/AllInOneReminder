package app.allinonereminder.vitals

data class ValidationError(
    val field: String,
    val message: String,
)

/**
 * Plausibility checks that reject typos. These are deliberately wide limits,
 * not medical "normal" ranges, and the module never interprets a reading.
 */
object VitalValidation {

    val SYSTOLIC_MMHG: ClosedFloatingPointRange<Double> = 50.0..260.0
    val DIASTOLIC_MMHG: ClosedFloatingPointRange<Double> = 30.0..160.0
    val PULSE_BPM: ClosedFloatingPointRange<Double> = 20.0..300.0
    val GLUCOSE_MG_DL: ClosedFloatingPointRange<Double> = 20.0..600.0
    val WEIGHT_KG: ClosedFloatingPointRange<Double> = 1.0..400.0
    val SPO2_PERCENT: ClosedFloatingPointRange<Double> = 50.0..100.0
    val TEMPERATURE_CELSIUS: ClosedFloatingPointRange<Double> = 30.0..45.0

    fun validate(reading: VitalReading): List<ValidationError> =
        buildList {
            when (reading) {
                is VitalReading.BloodPressure -> {
                    range("systolic", reading.systolic.toDouble(), SYSTOLIC_MMHG)
                    range("diastolic", reading.diastolic.toDouble(), DIASTOLIC_MMHG)
                    if (reading.systolic <= reading.diastolic) {
                        add(
                            ValidationError(
                                "systolic",
                                "systolic must be greater than diastolic",
                            ),
                        )
                    }
                    reading.pulse?.let {
                        range("pulse", it.toDouble(), PULSE_BPM)
                    }
                }
                is VitalReading.Glucose ->
                    range("glucose", reading.mgPerDl, GLUCOSE_MG_DL)
                is VitalReading.Weight ->
                    range("weight", reading.kg, WEIGHT_KG)
                is VitalReading.SpO2 ->
                    range("spo2", reading.percent.toDouble(), SPO2_PERCENT)
                is VitalReading.Temperature ->
                    range("temperature", reading.celsius, TEMPERATURE_CELSIUS)
                is VitalReading.Pulse ->
                    range("pulse", reading.bpm.toDouble(), PULSE_BPM)
                is VitalReading.Custom -> {
                    if (reading.name.isBlank()) {
                        add(ValidationError("name", "custom name is required"))
                    }
                    if (reading.unit.isBlank()) {
                        add(ValidationError("unit", "custom unit is required"))
                    }
                    finite("value", reading.value)
                }
            }
        }

    fun isValid(reading: VitalReading): Boolean = validate(reading).isEmpty()

    private fun MutableList<ValidationError>.range(
        field: String,
        value: Double,
        allowed: ClosedFloatingPointRange<Double>,
    ) {
        if (!value.isFinite()) {
            add(ValidationError(field, "value must be finite"))
            return
        }
        if (value < allowed.start || value > allowed.endInclusive) {
            add(
                ValidationError(
                    field,
                    "value must be between ${allowed.start} and ${allowed.endInclusive}",
                ),
            )
        }
    }

    private fun MutableList<ValidationError>.finite(field: String, value: Double) {
        if (!value.isFinite()) {
            add(ValidationError(field, "value must be finite"))
        }
    }
}
