package com.byd.carcontrol

import android.content.Context
import java.util.Locale
import kotlin.math.roundToInt

/** Front HVAC fan and driver temperature controls exposed by the OEM AirConditioningManager. */
object BydClimateAdjustment {
    private const val SERVICE_NAME = "airconditioning"
    private const val AC_DEVICE = "android.hardware.bydauto.ac.BYDAutoAcDevice"
    private const val AC_FEATURES = "android.hardware.bydauto.BYDAutoFeatureIds\$Ac"

    data class Snapshot(
        val windLevel: Int,
        val temperatureCelsius: Double?,
        val maxTemperatureCelsius: Int,
        val temperatureStepCelsius: Double,
        val detail: String
    )

    fun read(context: Context): Snapshot {
        val manager = manager(context)
        val managerClass = manager.javaClass
        val wind = (managerClass.getMethod("getWindLevel").invoke(manager) as Number).toInt().coerceIn(0, 7)
        val unit = (managerClass.getMethod("getAutoAcTemperatureUnit").invoke(manager) as Number).toInt()
        val hasHeating = managerClass.getMethod("hasAcHeatingFeature").invoke(manager) as Boolean
        val maxC = if (hasHeating) 27 else 33
        val displayedTemperature = managerClass.getMethod("getMainTemperatureValue").invoke(manager)?.toString()
        val step = readTemperatureStep(context)
        val celsius = displayedTemperature?.toDoubleOrNull()?.let { value ->
            if (unit == 0) (value - 32.0) * 5.0 / 9.0 else value
        }?.coerceIn(17.0, maxC.toDouble())
        val unitName = if (unit == 0) "°F (convertido para °C na interface)" else "°C"
        return Snapshot(wind, celsius, maxC, step,
            "getWindLevel=$wind; temperatura OEM=${displayedTemperature ?: "indisponível"} $unitName; resolução=${step}°C; aquecimento=$hasHeating")
    }

    fun setWindLevel(context: Context, level: Int): String {
        require(level in 1..7) { "A velocidade do ventilador deve ficar entre 1 e 7." }
        val manager = manager(context)
        manager.javaClass.getMethod("processWindLevelButtonClicked", Int::class.javaPrimitiveType)
            .invoke(manager, level)
        return "Comando OEM processWindLevelButtonClicked($level) enviado."
    }

    fun setTemperatureCelsius(context: Context, celsius: Double): String {
        require(celsius.isFinite()) { "Temperatura inválida." }
        val manager = manager(context)
        val type = manager.javaClass
        val unit = (type.getMethod("getAutoAcTemperatureUnit").invoke(manager) as Number).toInt()
        val hasHeating = type.getMethod("hasAcHeatingFeature").invoke(manager) as Boolean
        val maxC = if (hasHeating) 27.0 else 33.0
        require(celsius in 17.0..maxC) { "Temperatura permitida: 17–${maxC.toInt()} °C." }
        val step = readTemperatureStep(context)
        val roundedC = (celsius / step).roundToInt() * step
        val requested = if (unit == 0) roundedC * 9.0 / 5.0 + 32.0 else roundedC
        val value = String.format(Locale.US, "%.1f", requested).removeSuffix(".0")
        type.getMethod("processMainTemperatureChanged", String::class.java).invoke(manager, value)
        return "Comando OEM processMainTemperatureChanged($value) enviado (${roundedC} °C solicitados)."
    }

    private fun manager(context: Context): Any =
        context.applicationContext.getSystemService(SERVICE_NAME)
            ?: error("AirConditioningManager do sistema indisponível.")

    private fun readTemperatureStep(context: Context): Double = runCatching {
        val featureClass = Class.forName(AC_FEATURES)
        val feature = featureClass.getField("AC_TEMPERATURE_ADJUST_ACCURACY").getInt(null)
        val deviceClass = Class.forName(AC_DEVICE)
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val device = deviceClass.getMethod("getInstance", Context::class.java).invoke(null, sdkContext)
            ?: error("BYDAutoAcDevice.getInstance retornou null")
        val response = deviceClass.getMethod("get", IntArray::class.java, Class::class.java)
            .invoke(device, intArrayOf(feature), Int::class.java)
            ?: error("AC_TEMPERATURE_ADJUST_ACCURACY retornou null")
        val accuracy = if (response is Number) response.toInt()
            else (response.javaClass.getField("intValue").get(response) as Number).toInt()
        if (accuracy == 2) 0.5 else 1.0
    }.getOrDefault(1.0)
}
