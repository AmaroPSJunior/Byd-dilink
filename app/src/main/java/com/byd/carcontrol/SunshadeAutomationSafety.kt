package com.byd.carcontrol

import android.content.Context

/** Normal app context only. No permission wrappers, setters or shell privileges. */
object SunshadeAutomationSafety {
    fun allowed(context: Context): Boolean = runCatching {
        fun device(name: String): Pair<Class<*>, Any> {
            val cls = Class.forName("android.hardware.bydauto.$name")
            return cls to (cls.getMethod("getInstance", Context::class.java)
                .invoke(null, context.applicationContext) ?: error("Unavailable"))
        }
        val (speedClass, speedDevice) = device("speed.BYDAutoSpeedDevice")
        val getter = speedClass.getMethod("get", IntArray::class.java, Class::class.java)
        val raw = getter.invoke(speedDevice, intArrayOf(0x94400008L.toInt()), java.lang.Double.TYPE)
            ?: return false
        val speed = if (raw is Number) raw.toDouble()
            else (raw.javaClass.getField("doubleValue").get(raw) as Number).toDouble()
        val (gearClass, gearDevice) = device("gearbox.BYDAutoGearboxDevice")
        val gear = (gearClass.getMethod("getCurrentGear").invoke(gearDevice) as Number).toInt()
        val (bodyClass, bodyDevice) = device("bodywork.BYDAutoBodyworkDevice")
        val permit = (bodyClass.getMethod("getWindowPermitState").invoke(bodyDevice) as Number).toInt()
        val initialized = (bodyClass.getMethod("getWindoblindInitState").invoke(bodyDevice) as Number).toInt()
        SunshadeAutomationPolicy.safe(speed, gear, permit, initialized)
    }.getOrDefault(false)
}
