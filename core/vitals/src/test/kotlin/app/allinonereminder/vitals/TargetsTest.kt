package app.allinonereminder.vitals

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class TargetsTest {

    private val at = Instant.parse("2026-06-30T09:00:00Z")

    private fun weight(value: Double): VitalReading.Weight =
        VitalReading.Weight("w", at, value)

    private fun bloodPressure(pulse: Int? = null): VitalReading.BloodPressure =
        VitalReading.BloodPressure("bp", at, 120, 80, pulse)

    @Test
    fun `no user target means no flags`() {
        assertTrue(TargetChecks.evaluate(weight(70.0), emptyList()).isEmpty())
    }

    @Test
    fun `a user range flags below within and above with inclusive edges`() {
        val target = UserTarget(
            kind = VitalKind.WEIGHT,
            range = TargetRange(60.0, 80.0),
        )

        assertEquals(
            TargetStatus.BELOW,
            TargetChecks.evaluate(weight(59.99), listOf(target))
                .single().status,
        )
        assertEquals(
            TargetStatus.WITHIN,
            TargetChecks.evaluate(weight(60.0), listOf(target))
                .single().status,
        )
        assertEquals(
            TargetStatus.WITHIN,
            TargetChecks.evaluate(weight(80.0), listOf(target))
                .single().status,
        )
        assertEquals(
            TargetStatus.ABOVE,
            TargetChecks.evaluate(weight(80.01), listOf(target))
                .single().status,
        )
    }

    @Test
    fun `a target for another kind is ignored`() {
        val target = UserTarget(
            kind = VitalKind.GLUCOSE,
            range = TargetRange(70.0, 130.0),
        )

        assertTrue(
            TargetChecks.evaluate(weight(70.0), listOf(target)).isEmpty(),
        )
    }

    @Test
    fun `blood pressure targets can select systolic diastolic and pulse`() {
        val systolic = UserTarget(
            VitalKind.BLOOD_PRESSURE,
            TargetRange(90.0, 130.0),
            VitalField.PRIMARY,
            "systolic",
        )
        val diastolic = UserTarget(
            VitalKind.BLOOD_PRESSURE,
            TargetRange(60.0, 90.0),
            VitalField.BP_DIASTOLIC,
            "diastolic",
        )
        val pulse = UserTarget(
            VitalKind.BLOOD_PRESSURE,
            TargetRange(50.0, 100.0),
            VitalField.BP_PULSE,
            "pulse",
        )

        val checks = TargetChecks.evaluate(
            bloodPressure(pulse = 72),
            listOf(diastolic, pulse, systolic),
        )

        assertEquals(
            listOf(
                TargetStatus.WITHIN,
                TargetStatus.WITHIN,
                TargetStatus.WITHIN,
            ),
            checks.map { it.status },
        )
        assertEquals(
            listOf("systolic", "diastolic", "pulse"),
            checks.map { it.target.label },
        )
    }

    @Test
    fun `a missing pulse produces no pulse check`() {
        val target = UserTarget(
            VitalKind.BLOOD_PRESSURE,
            TargetRange(50.0, 100.0),
            VitalField.BP_PULSE,
            "pulse",
        )

        assertTrue(
            TargetChecks.evaluate(bloodPressure(), listOf(target)).isEmpty(),
        )
    }

    @Test
    fun `checks are sorted by field and then label`() {
        val labelB = UserTarget(
            VitalKind.WEIGHT,
            TargetRange(60.0, 80.0),
            VitalField.PRIMARY,
            "b",
        )
        val labelA = UserTarget(
            VitalKind.WEIGHT,
            TargetRange(60.0, 80.0),
            VitalField.PRIMARY,
            "a",
        )

        val checks = TargetChecks.evaluate(
            weight(70.0),
            listOf(labelB, labelA),
        )

        assertEquals(listOf("a", "b"), checks.map { it.target.label })
    }

    @Test
    fun `a target range with low above high is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TargetRange(80.0, 60.0)
        }
        assertTrue(TargetRange(60.0, 60.0).contains(60.0))
    }
}
