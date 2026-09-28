package com.byd.carcontrol

import android.content.Context

/**
 * Candidate direct controls for the door-linked cabin light exposed by this ROM.
 * The OFF path is the explicit OEM turnOffInsideLight method. The ON path uses
 * the OEM INSIDE_LIGHT_DOOR_OPEN enum and setInsideLightDoorState method.
 */
object BydInteriorLightControl {
    private const val DEVICE_CLASS = "android.hardware.bydauto.setting.BYDAutoSettingDevice"
    private const val INTERIOR_LIGHT_STATE_FID = 0x42E0002D

    data class Result(
        val accepted: Boolean,
        val observedState: Int?,
        val doorState: Int?,
        val detail: String
    )

    fun setPower(context: Context, turnOn: Boolean): Result {
        val deviceClass = Class.forName(DEVICE_CLASS)
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext) ?: error("BYDAutoSettingDevice.getInstance retornou null")
        val (methodName, result) = if (turnOn) {
            val open = deviceClass.getField("INSIDE_LIGHT_DOOR_OPEN").getInt(null)
            "setInsideLightDoorState($open)" to deviceClass
                .getMethod("setInsideLightDoorState", Int::class.javaPrimitiveType)
                .invoke(instance, open)
        } else {
            "turnOffInsideLight()" to deviceClass.getMethod("turnOffInsideLight").invoke(instance)
        }

        // BYD setters use nonnegative command status codes; the readback below is kept separate.
        val accepted = when (result) {
            null -> true
            is Boolean -> result
            is Number -> result.toInt() >= 0
            else -> true
        }
        val observedState = runCatching { readIntFeature(deviceClass, instance, INTERIOR_LIGHT_STATE_FID) }.getOrNull()
        val doorState = runCatching {
            (deviceClass.getMethod("getInsideLightDoorState").invoke(instance) as? Number)?.toInt()
        }.getOrNull()
        return Result(
            accepted,
            observedState,
            doorState,
            "$methodName retorno=${result ?: "void"}; " +
                "leitura bruta 0x42e0002d=${observedState ?: "indisponível"}; " +
                "getInsideLightDoorState=${doorState ?: "indisponível"}"
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
