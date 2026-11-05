package app.allinonereminder.vitals

/**
 * Unit conversions used by the entry UI. The canonical storage units are
 * mg/dL for glucose, kg for weight and Celsius for temperature.
 */
object UnitConversion {

    const val MG_DL_PER_MMOL_L: Double = 18.0156
    const val LB_PER_KG: Double = 2.2046226218487757

    fun glucoseToMgPerDl(value: Double, unit: GlucoseUnit): Double =
        when (unit) {
            GlucoseUnit.MG_PER_DL -> value
            GlucoseUnit.MMOL_PER_L -> mmolPerLToMgPerDl(value)
        }

    fun glucoseFromMgPerDl(value: Double, unit: GlucoseUnit): Double =
        when (unit) {
            GlucoseUnit.MG_PER_DL -> value
            GlucoseUnit.MMOL_PER_L -> mgPerDlToMmolPerL(value)
        }

    fun mgPerDlToMmolPerL(mgPerDl: Double): Double = mgPerDl / MG_DL_PER_MMOL_L

    fun mmolPerLToMgPerDl(mmolPerL: Double): Double = mmolPerL * MG_DL_PER_MMOL_L

    fun weightToKg(value: Double, unit: WeightUnit): Double =
        when (unit) {
            WeightUnit.KG -> value
            WeightUnit.LB -> lbToKg(value)
        }

    fun weightFromKg(value: Double, unit: WeightUnit): Double =
        when (unit) {
            WeightUnit.KG -> value
            WeightUnit.LB -> kgToLb(value)
        }

    fun kgToLb(kg: Double): Double = kg * LB_PER_KG

    fun lbToKg(lb: Double): Double = lb / LB_PER_KG

    fun temperatureToCelsius(value: Double, unit: TemperatureUnit): Double =
        when (unit) {
            TemperatureUnit.CELSIUS -> value
            TemperatureUnit.FAHRENHEIT -> fahrenheitToCelsius(value)
        }

    fun temperatureFromCelsius(value: Double, unit: TemperatureUnit): Double =
        when (unit) {
            TemperatureUnit.CELSIUS -> value
            TemperatureUnit.FAHRENHEIT -> celsiusToFahrenheit(value)
        }

    fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

    fun fahrenheitToCelsius(fahrenheit: Double): Double =
        (fahrenheit - 32.0) * 5.0 / 9.0
}
