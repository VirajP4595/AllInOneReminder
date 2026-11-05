package app.allinonereminder.vitals

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.math.abs

class UnitsTest {

    private val at = Instant.parse("2026-06-30T09:00:00Z")

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(
            abs(expected - actual) <= 0.1,
            "expected $expected but was $actual",
        )
    }

    @Test
    fun `glucose conversion uses the 18 point 0156 factor`() {
        assertEquals(5.5507, UnitConversion.mgPerDlToMmolPerL(100.0), 0.0001)
        assertEquals(100.0, UnitConversion.mmolPerLToMgPerDl(5.5507), 0.001)
        assertEquals(
            100.0,
            UnitConversion.glucoseToMgPerDl(100.0, GlucoseUnit.MG_PER_DL),
            0.0,
        )
        assertEquals(
            5.5507,
            UnitConversion.glucoseFromMgPerDl(
                100.0,
                GlucoseUnit.MMOL_PER_L,
            ),
            0.0001,
        )
        assertEquals(
            100.0,
            UnitConversion.glucoseFromMgPerDl(
                100.0,
                GlucoseUnit.MG_PER_DL,
            ),
            0.0,
        )
    }

    @Test
    fun `glucose round trip stays within 0 point 1 mg per dl`() {
        listOf(20.0, 70.0, 100.0, 126.0, 200.0, 600.0).forEach { mgPerDl ->
            val mmol = UnitConversion.mgPerDlToMmolPerL(mgPerDl)
            assertClose(
                mgPerDl,
                UnitConversion.mmolPerLToMgPerDl(mmol),
            )
            assertClose(
                mgPerDl,
                UnitConversion.glucoseToMgPerDl(mmol, GlucoseUnit.MMOL_PER_L),
            )
        }
    }

    @Test
    fun `weight conversion and round trip stay within 0 point 1 kg`() {
        assertEquals(2.2046, UnitConversion.kgToLb(1.0), 0.0001)
        assertEquals(1.0, UnitConversion.lbToKg(2.2046), 0.001)

        listOf(1.0, 50.0, 75.5, 100.0, 400.0).forEach { kg ->
            val lb = UnitConversion.kgToLb(kg)
            assertClose(kg, UnitConversion.lbToKg(lb))
            assertClose(kg, UnitConversion.weightToKg(lb, WeightUnit.LB))
            assertEquals(
                kg,
                UnitConversion.weightFromKg(kg, WeightUnit.KG),
                0.0,
            )
            assertEquals(
                lb,
                UnitConversion.weightFromKg(kg, WeightUnit.LB),
                0.0001,
            )
            assertEquals(
                kg,
                UnitConversion.weightToKg(kg, WeightUnit.KG),
                0.0,
            )
        }
    }

    @Test
    fun `temperature conversion and round trip stay within 0 point 1 degree`() {
        assertEquals(32.0, UnitConversion.celsiusToFahrenheit(0.0), 0.0)
        assertEquals(212.0, UnitConversion.celsiusToFahrenheit(100.0), 0.0)
        assertEquals(0.0, UnitConversion.fahrenheitToCelsius(32.0), 0.0)

        listOf(30.0, 36.6, 37.0, 45.0).forEach { celsius ->
            val fahrenheit = UnitConversion.celsiusToFahrenheit(celsius)
            assertClose(celsius, UnitConversion.fahrenheitToCelsius(fahrenheit))
            assertClose(
                celsius,
                UnitConversion.temperatureToCelsius(
                    fahrenheit,
                    TemperatureUnit.FAHRENHEIT,
                ),
            )
            assertEquals(
                celsius,
                UnitConversion.temperatureFromCelsius(
                    celsius,
                    TemperatureUnit.CELSIUS,
                ),
                0.0,
            )
            assertEquals(
                fahrenheit,
                UnitConversion.temperatureFromCelsius(
                    celsius,
                    TemperatureUnit.FAHRENHEIT,
                ),
                0.0001,
            )
            assertEquals(
                celsius,
                UnitConversion.temperatureToCelsius(
                    celsius,
                    TemperatureUnit.CELSIUS,
                ),
                0.0,
            )
        }
    }

    @Test
    fun `reading factories convert user units into canonical units`() {
        val glucose = VitalReading.glucose(
            id = "g",
            recordedAt = at,
            value = 5.5507,
            unit = GlucoseUnit.MMOL_PER_L,
            context = GlucoseContext.FASTING,
        )
        assertClose(100.0, glucose.mgPerDl)
        assertEquals(GlucoseContext.FASTING, glucose.context)
        assertEquals(VitalKind.GLUCOSE, glucose.kind)
        assertEquals(100.0, glucose.primaryValue, 0.1)

        val weight = VitalReading.weight(
            id = "w",
            recordedAt = at,
            value = 165.3467,
            unit = WeightUnit.LB,
        )
        assertClose(75.0, weight.kg)

        val temperature = VitalReading.temperature(
            id = "t",
            recordedAt = at,
            value = 98.6,
            unit = TemperatureUnit.FAHRENHEIT,
        )
        assertClose(37.0, temperature.celsius)
    }
}
