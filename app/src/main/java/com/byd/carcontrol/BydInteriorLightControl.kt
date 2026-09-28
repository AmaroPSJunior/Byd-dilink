package com.byd.carcontrol

import android.content.Context

/**
 * Commands for the BYD cabin/dome light. The FID and values are documented in
 * the BYD setting catalog: 2 = on, 1 = off. The HAL response is acceptance,
 * not proof that the physical lamp changed.
 */
object BydInteriorLightControl {
    private const val DEVICE_CLASS = "android.hardware.bydauto.setting.BYDAutoSettingDevice"
    private const val INTERIOR_LIGHT_COMMAND_FID = 1330643002 // 0x4F50003A

    data class Result(val accepted: Boolean, val value: Int, val detail: String)

    fun setPower(context: Context, turnOn: Boolean): Result {
        val value = if (turnOn) 2 else 1
        val deviceClass = Class.forName(DEVICE_CLASS)
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext) ?: error("BYDAutoSettingDevice.getInstance retornou null")

        // ROMs expose the generic setter with slightly different signatures.
        val methods = deviceClass.methods.filter { it.name == "set" }
        val typedSetter = methods.firstOrNull {
            it.parameterTypes.size == 3 && it.parameterTypes[0] == IntArray::class.java &&
                it.parameterTypes[1] == Class::class.java
        }
        val result = when {
            typedSetter != null -> typedSetter.invoke(instance, intArrayOf(INTERIOR_LIGHT_COMMAND_FID), Int::class.java, value)
            methods.any { it.parameterTypes.size == 2 && it.parameterTypes[0] == IntArray::class.java } -> {
                val setter = methods.first { it.parameterTypes.size == 2 && it.parameterTypes[0] == IntArray::class.java }
                setter.invoke(instance, intArrayOf(INTERIOR_LIGHT_COMMAND_FID), value)
            }
            else -> error("Nenhum método set compatível encontrado em $DEVICE_CLASS: ${methods.joinToString { it.toGenericString() }}")
        }

        // SDK setters commonly return void; otherwise treat Boolean true or nonzero numeric result as acceptance.
        val accepted = when (result) {
            null -> true
            is Boolean -> result
            is Number -> result.toInt() != 0
            else -> true
        }
        return Result(accepted, value, "FID=0x${INTERIOR_LIGHT_COMMAND_FID.toString(16)}, retorno=${result ?: "void"}")
    }
}
