package com.byd.carcontrol

import android.content.Context

/** Uses the same BYDAutoBodyworkDevice API and percentage convention as OEM CarSettings. */
object BydSunshadeControl {
    private const val DEVICE_CLASS = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
    private const val SPEED_DEVICE_CLASS = "android.hardware.bydauto.speed.BYDAutoSpeedDevice"
    private const val SPEED_FEATURE_HEX = "94400008" // OEM SPEED_AUTO_SPEED
    private const val SUNSHADE_AREA = 6
    private const val OPEN_PERCENT = 100

    data class Result(val accepted: Boolean, val percent: Int?, val speedKmh: Double?, val detail: String)

    fun open(context: Context): Result {
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val speed = readSpeedKmh(sdkContext)
            ?: return Result(false, null, null, "Leitura de velocidade indisponível; comando recusado por segurança.")
        if (!speed.isFinite() || speed > 0.5 || speed < -0.5) {
            return Result(false, null, speed, "Veículo em movimento ($speed km/h); abertura recusada.")
        }

        val deviceClass = Class.forName(DEVICE_CLASS)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext) ?: error("BYDAutoBodyworkDevice.getInstance retornou null")

        // OEM CarSettings disables opening when window operation is disallowed or
        // the shade has not completed initialization. Both getters return state 1
        // when true, as used by its SunRoofPresenter.
        val permit = (deviceClass.getMethod("getWindowPermitState").invoke(instance) as Number).toInt()
        val initialized = (deviceClass.getMethod("getWindoblindInitState").invoke(instance) as Number).toInt()
        if (permit != 1 || initialized != 1) {
            return Result(false, readPercent(deviceClass, instance), speed, "A API OEM bloqueia abertura: permissão=$permit, inicialização=$initialized.")
        }

        val method = deviceClass.getMethod("setSunshadeState", Int::class.javaPrimitiveType)
        val result = method.invoke(instance, OPEN_PERCENT)
        val percent = readPercent(deviceClass, instance)
        // This OEM setter is void; distinguish successful invocation from a verified position.
        return Result(true, percent, speed, "setSunshadeState(100) executado a $speed km/h; retorno=${result ?: "void"}; posição=${percent ?: "indisponível"}%.")
    }

    private fun readSpeedKmh(context: Context): Double? = runCatching {
        val speedClass = Class.forName(SPEED_DEVICE_CLASS)
        val instance = speedClass.getMethod("getInstance", Context::class.java).invoke(null, context)
            ?: error("BYDAutoSpeedDevice.getInstance retornou null")
        val getter = speedClass.methods.first {
            it.name == "get" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == IntArray::class.java && it.parameterTypes[1] == Class::class.java
        }
        val featureId = java.lang.Long.parseLong(SPEED_FEATURE_HEX, 16).toInt()
        val response = getter.invoke(instance, intArrayOf(featureId), java.lang.Double.TYPE)
            ?: error("Leitura de velocidade retornou null")
        when (response) {
            is Number -> response.toDouble()
            else -> (response.javaClass.getField("doubleValue").get(response) as Number).toDouble()
        }
    }.getOrNull()

    private fun readPercent(deviceClass: Class<*>, instance: Any): Int? = runCatching {
        (deviceClass.getMethod("getWindowOpenPercent", Int::class.javaPrimitiveType)
            .invoke(instance, SUNSHADE_AREA) as Number).toInt()
    }.getOrNull()
}
