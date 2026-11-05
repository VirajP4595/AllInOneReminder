package app.allinonereminder.vitals

import java.time.Instant

/** The vital signs this module understands. */
enum class VitalKind {
    BLOOD_PRESSURE,
    GLUCOSE,
    WEIGHT,
    SPO2,
    TEMPERATURE,
    PULSE,
    CUSTOM,
}

enum class GlucoseContext { FASTING, POST_MEAL, RANDOM }

enum class GlucoseUnit { MG_PER_DL, MMOL_PER_L }

enum class WeightUnit { KG, LB }

enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

/**
 * Which component of a reading a target or trend refers to. `PRIMARY` is the
 * main value: systolic for blood pressure, mg/dL for glucose, kg for weight,
 * percent for SpO2, Celsius for temperature and bpm for pulse.
 */
enum class VitalField {
    PRIMARY,
    BP_SYSTOLIC,
    BP_DIASTOLIC,
    BP_PULSE,
}

/**
 * One vital reading in canonical units (mg/dL, kg, %, Celsius, bpm, mmHg).
 * Values are validated separately by [VitalValidation]; the type does not
 * reject out-of-range or non-finite numbers so that bad input can be shown
 * to the user instead of crashing.
 */
sealed class VitalReading {
    abstract val id: String
    abstract val recordedAt: Instant
    abstract val kind: VitalKind
    abstract val primaryValue: Double

    data class BloodPressure(
        override val id: String,
        override val recordedAt: Instant,
        val systolic: Int,
        val diastolic: Int,
        val pulse: Int? = null,
    ) : VitalReading() {
        override val kind: VitalKind get() = VitalKind.BLOOD_PRESSURE
        override val primaryValue: Double get() = systolic.toDouble()
    }

    data class Glucose(
        override val id: String,
        override val recordedAt: Instant,
        val mgPerDl: Double,
        val context: GlucoseContext,
    ) : VitalReading() {
        override val kind: VitalKind get() = VitalKind.GLUCOSE
        override val primaryValue: Double get() = mgPerDl
    }

    data class Weight(
        override val id: String,
        override val recordedAt: Instant,
        val kg: Double,
    ) : VitalReading() {
        override val kind: VitalKind get() = VitalKind.WEIGHT
        override val primaryValue: Double get() = kg
    }

    data class SpO2(
        override val id: String,
        override val recordedAt: Instant,
        val percent: Int,
    ) : VitalReading() {
        override val kind: VitalKind get() = VitalKind.SPO2
        override val primaryValue: Double get() = percent.toDouble()
    }

    data class Temperature(
        override val id: String,
        override val recordedAt: Instant,
        val celsius: Double,
    ) : VitalReading() {
        override val kind: VitalKind get() = VitalKind.TEMPERATURE
        override val primaryValue: Double get() = celsius
    }

    data class Pulse(
        override val id: String,
        override val recordedAt: Instant,
        val bpm: Int,
    ) : VitalReading() {
        override val kind: VitalKind get() = VitalKind.PULSE
        override val primaryValue: Double get() = bpm.toDouble()
    }

    data class Custom(
        override val id: String,
        override val recordedAt: Instant,
        val name: String,
        val unit: String,
        val value: Double,
    ) : VitalReading() {
        override val kind: VitalKind get() = VitalKind.CUSTOM
        override val primaryValue: Double get() = value
    }

    /** The value selected by a target or trend, or null when it does not exist. */
    fun valueOf(field: VitalField): Double? = when (field) {
        VitalField.PRIMARY,
        VitalField.BP_SYSTOLIC,
        -> primaryValue
        VitalField.BP_DIASTOLIC ->
            (this as? BloodPressure)?.diastolic?.toDouble()
        VitalField.BP_PULSE ->
            (this as? BloodPressure)?.pulse?.toDouble()
    }

    companion object {
        fun glucose(
            id: String,
            recordedAt: Instant,
            value: Double,
            unit: GlucoseUnit,
            context: GlucoseContext,
        ): Glucose = Glucose(
            id = id,
            recordedAt = recordedAt,
            mgPerDl = UnitConversion.glucoseToMgPerDl(value, unit),
            context = context,
        )

        fun weight(
            id: String,
            recordedAt: Instant,
            value: Double,
            unit: WeightUnit,
        ): Weight = Weight(
            id = id,
            recordedAt = recordedAt,
            kg = UnitConversion.weightToKg(value, unit),
        )

        fun temperature(
            id: String,
            recordedAt: Instant,
            value: Double,
            unit: TemperatureUnit,
        ): Temperature = Temperature(
            id = id,
            recordedAt = recordedAt,
            celsius = UnitConversion.temperatureToCelsius(value, unit),
        )
    }
}
