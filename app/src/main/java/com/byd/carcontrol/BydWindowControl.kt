package com.byd.carcontrol

import android.content.Context
import android.os.SystemClock
import kotlin.math.abs

/** Window targets recovered from the installed BYD Body feature map. */
object BydWindowControl {
    enum class Window(val label: String, val area: Int, val targetFeature: String) {
        DRIVER_FRONT("Motorista — dianteiro esquerdo", 1, "4C119010"),
        PASSENGER_FRONT("Passageiro — dianteiro direito", 2, "4C119020"),
        DRIVER_REAR("Motorista — traseiro esquerdo", 3, "4C119018"),
        PASSENGER_REAR("Passageiro — traseiro direito", 4, "4C119028")
    }

    data class WindowState(val percent: Int?, val state: Int?, val permit: Int?)

    private const val DEVICE = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
    private const val EVENT_VALUE = "android.hardware.bydauto.BYDAutoEventValue"
    private const val SPEED_DEVICE = "android.hardware.bydauto.speed.BYDAutoSpeedDevice"
    private const val GEAR_DEVICE = "android.hardware.bydauto.gearbox.BYDAutoGearboxDevice"
    private const val SPEED_FID = "94400008"

    fun read(context: Context, window: Window): WindowState {
        val (clazz, instance) = bodywork(context)
        val percent = (clazz.getMethod("getWindowOpenPercent", Int::class.javaPrimitiveType)
            .invoke(instance, window.area) as Number).toInt().takeIf { it in 0..100 }
        val state = (clazz.getMethod("getWindowState", Int::class.javaPrimitiveType)
            .invoke(instance, window.area) as Number).toInt()
        val permit = (clazz.getMethod("getWindowPermitState").invoke(instance) as Number).toInt()
        return WindowState(percent, state, permit)
    }

    fun setPosition(context: Context, window: Window, percent: Int): String {
        require(percent in 0..100) { "A posição deve ficar entre 0 e 100%." }
        val (clazz, instance) = bodywork(context)
        checkVehicleSafe(context)
        val permit = (clazz.getMethod("getWindowPermitState").invoke(instance) as Number).toInt()
        require(permit != 1) { "O HAL bloqueia a operação dos vidros (permit=$permit)." }

        val fid = window.targetFeature.toLong(16).toInt()
        val valueClass = Class.forName(EVENT_VALUE)
        val value = valueClass.getDeclaredConstructor().newInstance()
        valueClass.getField("intArrayValue").set(value, intArrayOf(percent))
        val setter = clazz.methods.firstOrNull {
            it.name == "set" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == IntArray::class.java && it.parameterTypes[1].isAssignableFrom(valueClass)
        } ?: error("BYDAutoBodyworkDevice.set(int[], BYDAutoEventValue) indisponível.")
        val accepted = (setter.invoke(instance, intArrayOf(fid), value) as Number).toInt()
        require(accepted == 0) { "HAL recusou posição ($accepted)." }
        SystemClock.sleep(250)
        val observed = (clazz.getMethod("getWindowOpenPercent", Int::class.javaPrimitiveType)
            .invoke(instance, window.area) as Number).toInt().takeIf { it in 0..100 }
        return "${window.label}: alvo $percent% aceito pelo HAL; leitura=${observed?.let { "$it%" } ?: "indisponível"}."
    }

    fun setFullyOpenOrClosed(context: Context, window: Window, open: Boolean): String {
        return setPresetPosition(context, window, if (open) 100 else 0)
    }

    /** OEM exposes only close, half-open and full-open motion commands, not a confirmed arbitrary-percent setter. */
    fun setPresetPosition(context: Context, window: Window, percent: Int): String {
        require(percent in 0..100)
        checkVehicleSafe(context)
        val (clazz, instance) = bodywork(context)
        val permit = (clazz.getMethod("getWindowPermitState").invoke(instance) as Number).toInt()
        require(permit != 1) { "O HAL bloqueia a operação dos vidros (permit=$permit)." }
        val command = when (percent) {
            0 -> 2 // WINDOW_CLOSE
            100 -> 1 // WINDOW_OPEN_FULL
            else -> 4 // WINDOW_OPEN_HALF; map intermediate slider targets to this supported preset
        }
        val result = (clazz.getMethod("setBodyWindowCtrlState", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(instance, window.area, command) as Number).toInt()
        require(result == 0) { "HAL recusou o comando (código=$result)." }
        val preset = when (command) { 1 -> "aberto (100%)"; 2 -> "fechado (0%)"; else -> "abertura parcial (comando OEM de meia abertura)" }
        return "${window.label}: comando $preset aceito pelo HAL. O OEM não expõe percentagem arbitrária confirmada."
    }

    private fun bodywork(context: Context): Pair<Class<*>, Any> {
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val clazz = Class.forName(DEVICE)
        val instance = clazz.getMethod("getInstance", Context::class.java).invoke(null, sdkContext)
            ?: error("BYDAutoBodyworkDevice indisponível.")
        return clazz to instance
    }

    private fun checkVehicleSafe(context: Context) {
        val speed = readSpeed(context) ?: error("Velocidade indisponível; comando recusado.")
        require(speed.isFinite() && abs(speed) <= 0.5) { "Veículo em movimento ($speed km/h); comando recusado." }
        val gear = readGear(context) ?: error("Marcha indisponível; comando recusado.")
        require(gear == 3) { "O comando exige P; marcha reportada=$gear." }
    }

    private fun readSpeed(context: Context): Double? = runCatching {
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val clazz = Class.forName(SPEED_DEVICE)
        val instance = clazz.getMethod("getInstance", Context::class.java).invoke(null, sdkContext)
        val getter = clazz.methods.first { it.name == "get" && it.parameterTypes.size == 2 && it.parameterTypes[0] == IntArray::class.java }
        val value = getter.invoke(instance, intArrayOf(SPEED_FID.toLong(16).toInt()), java.lang.Double.TYPE)
        (value.javaClass.getField("doubleValue").get(value) as Number).toDouble()
    }.getOrNull()

    private fun readGear(context: Context): Int? = runCatching {
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val clazz = Class.forName(GEAR_DEVICE)
        val instance = clazz.getMethod("getInstance", Context::class.java).invoke(null, sdkContext)
        (clazz.getMethod("getCurrentGear").invoke(instance) as Number).toInt()
    }.getOrNull()
}
