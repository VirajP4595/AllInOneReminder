package app.allinonereminder.vitals

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ValidationTest {

    private val at = Instant.parse("2026-06-30T09:00:00Z")

    private fun bp(
        systolic: Int,
        diastolic: Int,
        pulse: Int? = null,
    ): VitalReading.BloodPressure = VitalReading.BloodPressure(
        id = "bp",
        recordedAt = at,
        systolic = systolic,
        diastolic = diastolic,
        pulse = pulse,
    )

    private fun assertValid(reading: VitalReading) {
        val errors = VitalValidation.validate(reading)
        assertTrue(errors.isEmpty(), "expected valid but got $errors")
        assertTrue(VitalValidation.isValid(reading))
    }

    private fun assertInvalid(reading: VitalReading, field: String) {
        val errors = VitalValidation.validate(reading)
        assertFalse(errors.isEmpty(), "expected invalid for field $field")
        assertTrue(
            errors.any { it.field == field },
            "expected an error for $field but got $errors",
        )
        assertFalse(VitalValidation.isValid(reading))
    }

    @Test
    fun `blood pressure plausibility boundaries are inclusive`() {
        assertValid(bp(50, 30))
        assertValid(bp(260, 30))
        assertValid(bp(260, 160))
        assertValid(bp(120, 30))
        assertValid(bp(200, 160))

        assertInvalid(bp(49, 30), "systolic")
        assertInvalid(bp(261, 30), "systolic")
        assertInvalid(bp(120, 29), "diastolic")
        assertInvalid(bp(120, 161), "diastolic")
    }

    @Test
    fun `blood pressure systolic must be greater than diastolic`() {
        assertInvalid(bp(120, 120), "systolic")
        assertInvalid(bp(120, 121), "systolic")
        assertValid(bp(121, 120))
    }

    @Test
    fun `blood pressure pulse is optional and uses the pulse range`() {
        assertValid(bp(120, 80))
        assertValid(bp(120, 80, 20))
        assertValid(bp(120, 80, 300))
        assertInvalid(bp(120, 80, 19), "pulse")
        assertInvalid(bp(120, 80, 301), "pulse")
    }

    @Test
    fun `glucose boundaries are inclusive`() {
        assertValid(
            VitalReading.Glucose("g", at, 20.0, GlucoseContext.RANDOM),
        )
        assertValid(
            VitalReading.Glucose("g", at, 600.0, GlucoseContext.FASTING),
        )
        assertInvalid(
            VitalReading.Glucose("g", at, 19.99, GlucoseContext.RANDOM),
            "glucose",
        )
        assertInvalid(
            VitalReading.Glucose("g", at, 600.01, GlucoseContext.POST_MEAL),
            "glucose",
        )
    }

    @Test
    fun `weight boundaries are inclusive`() {
        assertValid(VitalReading.Weight("w", at, 1.0))
        assertValid(VitalReading.Weight("w", at, 400.0))
        assertInvalid(VitalReading.Weight("w", at, 0.99), "weight")
        assertInvalid(VitalReading.Weight("w", at, 400.01), "weight")
    }

    @Test
    fun `spo2 boundaries are inclusive`() {
        assertValid(VitalReading.SpO2("s", at, 50))
        assertValid(VitalReading.SpO2("s", at, 100))
        assertInvalid(VitalReading.SpO2("s", at, 49), "spo2")
        assertInvalid(VitalReading.SpO2("s", at, 101), "spo2")
    }

    @Test
    fun `temperature boundaries are inclusive`() {
        assertValid(VitalReading.Temperature("t", at, 30.0))
        assertValid(VitalReading.Temperature("t", at, 45.0))
        assertInvalid(VitalReading.Temperature("t", at, 29.99), "temperature")
        assertInvalid(VitalReading.Temperature("t", at, 45.01), "temperature")
    }

    @Test
    fun `pulse boundaries are inclusive`() {
        assertValid(VitalReading.Pulse("p", at, 20))
        assertValid(VitalReading.Pulse("p", at, 300))
        assertInvalid(VitalReading.Pulse("p", at, 19), "pulse")
        assertInvalid(VitalReading.Pulse("p", at, 301), "pulse")
    }

    @Test
    fun `non finite numbers are rejected`() {
        assertInvalid(
            VitalReading.Weight("w", at, Double.NaN),
            "weight",
        )
        assertInvalid(
            VitalReading.Weight("w", at, Double.POSITIVE_INFINITY),
            "weight",
        )
        assertInvalid(
            VitalReading.Glucose(
                "g",
                at,
                Double.NEGATIVE_INFINITY,
                GlucoseContext.RANDOM,
            ),
            "glucose",
        )
        assertInvalid(
            VitalReading.Temperature("t", at, Double.NaN),
            "temperature",
        )
        assertInvalid(
            VitalReading.Custom("c", at, "Mood", "score", Double.NaN),
            "value",
        )
    }

    @Test
    fun `custom readings require a name and a unit`() {
        assertInvalid(
            VitalReading.Custom("c", at, " ", "score", 4.0),
            "name",
        )
        assertInvalid(
            VitalReading.Custom("c", at, "Mood", "", 4.0),
            "unit",
        )
        assertValid(
            VitalReading.Custom("c", at, "Mood", "score", 4.0),
        )
    }

    @Test
    fun `a valid reading of every type passes`() {
        assertValid(bp(120, 80, 72))
        assertValid(
            VitalReading.Glucose("g", at, 100.0, GlucoseContext.FASTING),
        )
        assertValid(VitalReading.Weight("w", at, 70.0))
        assertValid(VitalReading.SpO2("s", at, 98))
        assertValid(VitalReading.Temperature("t", at, 36.6))
        assertValid(VitalReading.Pulse("p", at, 72))
        assertValid(VitalReading.Custom("c", at, "Mood", "score", 4.0))
    }
}
