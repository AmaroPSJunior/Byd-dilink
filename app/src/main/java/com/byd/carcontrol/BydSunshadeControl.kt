package com.byd.carcontrol

import android.content.Context
import android.os.SystemClock
import kotlin.math.abs

/** Uses BYDAutoBodyworkDevice as called by the OEM sunshade screen. */
object BydSunshadeControl {
    private const val DEVICE_CLASS = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
    private const val SPEED_DEVICE_CLASS = "android.hardware.bydauto.speed.BYDAutoSpeedDevice"
    private const val GEARBOX_DEVICE_CLASS = "android.hardware.bydauto.gearbox.BYDAutoGearboxDevice"
    private const val SPEED_FEATURE_HEX = "94400008" // OEM SPEED_AUTO_SPEED
    private const val SUNSHADE_AREA = 6
    private const val PARK_GEAR = 3 // Confirmed from CarSettings P-only checks.
    private const val OPEN_OPERATION_BLOCKED = 1
    private const val POSITION_WAIT_MS = 12_000L
    private const val POSITION_POLL_MS = 500L
    private const val POSITION_TOLERANCE = 2

    data class Result(
        val accepted: Boolean,
        val requestedPercent: Int,
        val observedPercent: Int?,
        val confirmed: Boolean,
        val speedKmh: Double?,
        val gear: Int?,
        val detail: String
    )

    fun readPosition(context: Context): Int? = runCatching {
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val deviceClass = Class.forName(DEVICE_CLASS)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext) ?: error("BYDAutoBodyworkDevice.getInstance retornou null")
        readPercent(deviceClass, instance)
    }.getOrNull()

    fun setPosition(context: Context, percent: Int): Result {
        require(percent in 0..100) { "A posição deve ficar entre 0 e 100%." }
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val speed = readSpeedKmh(sdkContext)
            ?: return blocked(percent, null, null, "Leitura de velocidade indisponível; comando recusado.")
        if (!speed.isFinite() || abs(speed) > 0.5) {
            return blocked(percent, speed, null, "Veículo em movimento ($speed km/h); comando recusado.")
        }
        val gear = readGear(sdkContext)
            ?: return blocked(percent, speed, null, "Marcha indisponível; o comando exige P.")
        if (gear != PARK_GEAR) return blocked(percent, speed, gear, "O comando exige P; marcha reportada=$gear.")

        val deviceClass = Class.forName(DEVICE_CLASS)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext) ?: error("BYDAutoBodyworkDevice.getInstance retornou null")

        // OEM CarSettings disables its sunshade controls when this getter is 1.
        val operationBlocked = (deviceClass.getMethod("getWindowPermitState").invoke(instance) as Number).toInt()
        val initialized = (deviceClass.getMethod("getWindoblindInitState").invoke(instance) as Number).toInt()
        if (operationBlocked == OPEN_OPERATION_BLOCKED || initialized != 1) {
            return blocked(
                percent, speed, gear,
                "API OEM bloqueia a operação: bloqueio=$operationBlocked, inicialização=$initialized.",
                readPercent(deviceClass, instance)
            )
        }

        val method = deviceClass.getMethod("setSunshadeState", Int::class.javaPrimitiveType)
        val rawResult = method.invoke(instance, percent)
        val apiAccepted = rawResult is Number && rawResult.toInt() == 0
        if (!apiAccepted) {
            return Result(false, percent, readPercent(deviceClass, instance), false, speed, gear,
                "setSunshadeState($percent) retornou ${rawResult ?: "void"}; comando não confirmado como aceito.")
        }

        // The BYD getter can lag behind the accepted command; poll it while the
        // OEM motor moves, rather than showing a false failure from one instant read.
        val started = SystemClock.elapsedRealtime()
        var observed = readPercent(deviceClass, instance)
        while (!matchesTarget(observed, percent) && SystemClock.elapsedRealtime() - started < POSITION_WAIT_MS) {
            SystemClock.sleep(POSITION_POLL_MS)
            observed = readPercent(deviceClass, instance)
        }
        val confirmed = matchesTarget(observed, percent)
        val detail = if (confirmed) {
            "Posição ${observed}% confirmada em P (velocidade ${speed} km/h)."
        } else {
            "API aceitou setSunshadeState($percent), mas a leitura ficou em ${observed ?: "indisponível"}% após ${POSITION_WAIT_MS / 1000}s."
        }
        return Result(true, percent, observed, confirmed, speed, gear, detail)
    }

    private fun blocked(percent: Int, speed: Double?, gear: Int?, reason: String, current: Int? = null) =
        Result(false, percent, current, false, speed, gear, reason)

    private fun matchesTarget(observed: Int?, target: Int): Boolean =
        observed != null && abs(observed - target) <= POSITION_TOLERANCE

    private fun readGear(context: Context): Int? = runCatching {
        val gearClass = Class.forName(GEARBOX_DEVICE_CLASS)
        val instance = gearClass.getMethod("getInstance", Context::class.java).invoke(null, context)
            ?: error("BYDAutoGearboxDevice.getInstance retornou null")
        (gearClass.getMethod("getCurrentGear").invoke(instance) as Number).toInt()
    }.getOrNull()

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
            .invoke(instance, SUNSHADE_AREA) as Number).toInt().coerceIn(0, 100)
    }.getOrNull()
}
