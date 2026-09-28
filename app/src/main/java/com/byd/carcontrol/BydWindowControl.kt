package com.byd.carcontrol

import android.content.Context
import android.os.SystemClock
import kotlin.math.abs

/** Window targets recovered from the installed BYD Body feature map. */
object BydWindowControl {
    enum class Window(val label: String, val area: Int, val percentFeature: String, val initFeature: String, val targetFeature: String) {
        DRIVER_FRONT("Motorista — dianteiro esquerdo", 1, "38800018", "48E00010", "4C119010"),
        PASSENGER_FRONT("Passageiro — dianteiro direito", 2, "4B900010", "4B900008", "4C119020"),
        DRIVER_REAR("Motorista — traseiro esquerdo", 3, "38800020", "48E00012", "4C119018"),
        PASSENGER_REAR("Passageiro — traseiro direito", 4, "38800030", "48E00016", "4C119028")
    }

    data class WindowState(val percent: Int?, val initialized: Int?)

    private const val DEVICE = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
    private const val EVENT_VALUE = "android.hardware.bydauto.BYDAutoEventValue"
    private const val SPEED_DEVICE = "android.hardware.bydauto.speed.BYDAutoSpeedDevice"
    private const val GEAR_DEVICE = "android.hardware.bydauto.gearbox.BYDAutoGearboxDevice"
    private const val SPEED_FID = "94400008"

    fun read(context: Context, window: Window): WindowState {
        val (clazz, instance) = bodywork(context)
        val percent = readInt(clazz, instance, window.percentFeature)?.takeIf { it in 0..100 }
        val initialized = readInt(clazz, instance, window.initFeature)
        return WindowState(percent, initialized)
    }

    fun setPosition(context: Context, window: Window, percent: Int): String {
        require(percent in 0..100) { "A posição deve ficar entre 0 e 100%." }
        val speed = readSpeed(context) ?: error("Velocidade indisponível; comando recusado.")
        require(speed.isFinite() && abs(speed) <= 0.5) { "Veículo em movimento ($speed km/h); comando recusado." }
        val gear = readGear(context) ?: error("Marcha indisponível; comando recusado.")
        require(gear == 3) { "O comando exige P; marcha reportada=$gear." }
        val (clazz, instance) = bodywork(context)
        val init = readInt(clazz, instance, window.initFeature)
        require(init == 1) { "${window.label}: inicialização não confirmada (estado=$init)." }

        val fid = window.targetFeature.toLong(16).toInt()
        val valueClass = Class.forName(EVENT_VALUE)
        val value = valueClass.getDeclaredConstructor().newInstance()
        valueClass.getField("intArrayValue").set(value, intArrayOf(percent))
        val setter = clazz.methods.firstOrNull {
            it.name == "set" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == IntArray::class.java && it.parameterTypes[1].isAssignableFrom(valueClass)
        } ?: error("BYDAutoBodyworkDevice.set(int[], BYDAutoEventValue) indisponível.")
        val accepted = setter.invoke(instance, intArrayOf(fid), value)
        SystemClock.sleep(250)
        val observed = readInt(clazz, instance, window.percentFeature)?.takeIf { it in 0..100 }
        return "${window.label}: alvo $percent% enviado; retorno=${accepted ?: "void"}; leitura=${observed?.let { "$it%" } ?: "indisponível"}."
    }

    private fun bodywork(context: Context): Pair<Class<*>, Any> {
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val clazz = Class.forName(DEVICE)
        val instance = clazz.getMethod("getInstance", Context::class.java).invoke(null, sdkContext)
            ?: error("BYDAutoBodyworkDevice indisponível.")
        return clazz to instance
    }

    private fun readInt(clazz: Class<*>, instance: Any, featureHex: String): Int? = runCatching {
        val getter = clazz.methods.first {
            it.name == "get" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == IntArray::class.java && it.parameterTypes[1] == Class::class.java
        }
        val value = getter.invoke(instance, intArrayOf(featureHex.toLong(16).toInt()), Integer.TYPE) ?: return null
        runCatching { (value.javaClass.getField("intValue").get(value) as Number).toInt() }
            .getOrElse { (value as Number).toInt() }
    }.getOrNull()

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
