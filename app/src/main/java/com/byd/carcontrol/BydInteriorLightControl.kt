package com.byd.carcontrol

import android.content.Context

/** Controls the cabin light through BYDAutoSettingDevice's explicit state setter. */
object BydInteriorLightControl {
    private const val DEVICE_CLASS = "android.hardware.bydauto.setting.BYDAutoSettingDevice"
    private const val INTERIOR_LIGHT_STATE_FID = 0x42E0002D

    data class Result(
        val accepted: Boolean,
        val requestedState: Int,
        val observedState: Int?,
        val doorState: Int?,
        val lightReadings: List<String>,
        val detail: String
    )

    fun setPower(context: Context, turnOn: Boolean): Result {
        val deviceClass = Class.forName(DEVICE_CLASS)
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext) ?: error("BYDAutoSettingDevice.getInstance retornou null")
        // The OEM method is oddly named for both states, but the SDK validates
        // INSIGHT_LIGHT_OFF=1 and INSIGHT_LIGHT_ON=2 before writing
        // SET_INSIDE_LIGHT_STATE_SET. Do not use setInsideLightDoorState here:
        // that setter changes the door-linked state, not the cabin lamp power.
        val stateField = if (turnOn) "INSIGHT_LIGHT_ON" else "INSIGHT_LIGHT_OFF"
        val state = deviceClass.getField(stateField).getInt(null)
        val methodName = "turnOffInsideLight($stateField=$state)"
        val result = deviceClass
            .getMethod("turnOffInsideLight", Int::class.javaPrimitiveType)
            .invoke(instance, state)

        // BYDAutoManager.BYDAUTO_COMMAND_RESULT_SUCCESS is exactly 0.
        val accepted = result is Number && result.toInt() == 0
        val observedState = runCatching { readIntFeature(deviceClass, instance, INTERIOR_LIGHT_STATE_FID) }.getOrNull()
        val doorState = runCatching {
            (deviceClass.getMethod("getInsideLightDoorState").invoke(instance) as? Number)?.toInt()
        }.getOrNull()
        val lightReadings = BydInteriorLightReader.read(context)
        return Result(
            accepted,
            state,
            observedState,
            doorState,
            lightReadings,
            "$methodName retorno=${result ?: "void"}; " +
                "leitura bruta 0x42e0002d=${observedState ?: "indisponível"}; " +
                "getInsideLightDoorState=${doorState ?: "indisponível"}; " +
                "leituras após comando=${lightReadings.joinToString()}"
        )
    }

    private fun readIntFeature(deviceClass: Class<*>, instance: Any, featureId: Int): Int {
        val getter = deviceClass.methods.firstOrNull {
            it.name == "get" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == IntArray::class.java && it.parameterTypes[1] == Class::class.java
        } ?: error("get(int[], Class) não encontrado")
        val response = getter.invoke(instance, intArrayOf(featureId), Int::class.java)
            ?: error("getter retornou null")
        return if (response is Number) response.toInt() else {
            val valueField = response.javaClass.fields.firstOrNull { it.name == "intValue" }
            (valueField?.get(response) as? Number)?.toInt() ?: error("resposta sem intValue")
        }
    }

}
