package com.byd.carcontrol

import android.content.Context

/** Reads the BYD speed feature used by the OEM controls. */
object BydVehicleSpeedReader {
    private const val DEVICE_CLASS = "android.hardware.bydauto.speed.BYDAutoSpeedDevice"
    private val speedFeature = "94400008".toLong(16).toInt()

    fun readKmh(context: Context): Double {
        val clazz = Class.forName(DEVICE_CLASS)
        val instance = clazz.getMethod("getInstance", Context::class.java)
            .invoke(null, BydAutoReadContext(context.applicationContext))
            ?: error("BYDAutoSpeedDevice.getInstance retornou null")
        val getter = clazz.methods.first {
            it.name == "get" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == IntArray::class.java && it.parameterTypes[1] == Class::class.java
        }
        val value = getter.invoke(instance, intArrayOf(speedFeature), java.lang.Double.TYPE)
            ?: error("Leitura de velocidade retornou null")
        return (if (value is Number) value.toDouble() else
            (value.javaClass.getField("doubleValue").get(value) as Number).toDouble())
    }
}
