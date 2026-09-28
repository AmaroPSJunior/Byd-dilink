package com.byd.carcontrol

import android.content.Context

/** Read-only probe for the BYD instrument SDK's seat-belt getter. */
object BydSeatbeltReader {
    private const val DEVICE_CLASS = "android.hardware.bydauto.instrument.BYDAutoInstrumentDevice"

    fun readDriverRawStatus(context: Context): Int {
        val deviceClass = Class.forName(DEVICE_CLASS)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, context.applicationContext)
            ?: error("BYDAutoInstrumentDevice.getInstance retornou null")
        val getter = deviceClass.getMethod("getSafetyBeltStatus", Int::class.javaPrimitiveType)
        return (getter.invoke(instance, 0) as? Number)?.toInt()
            ?: error("getSafetyBeltStatus(0) não retornou um número")
    }
}
