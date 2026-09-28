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

    data class Result(val accepted: Boolean, val value: Int, val observedState: Int?, val detail: String)

    fun setPower(context: Context, turnOn: Boolean): Result {
        val value = if (turnOn) 2 else 1
        val deviceClass = Class.forName(DEVICE_CLASS)
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext) ?: error("BYDAutoSettingDevice.getInstance retornou null")

        // This ROM exposes set(int[], BYDAutoEventValue), like the OEM SDK.
        val methods = deviceClass.methods.filter { it.name == "set" }
        val setter = methods.firstOrNull {
            it.parameterTypes.size == 2 && it.parameterTypes[0] == IntArray::class.java &&
                it.parameterTypes[1].name == "android.hardware.bydauto.BYDAutoEventValue"
        } ?: error("set(int[], BYDAutoEventValue) não encontrado: ${methods.joinToString { it.toGenericString() }}")
        val eventValue = createEventValue(setter.parameterTypes[1], value)
        val result = setter.invoke(instance, intArrayOf(INTERIOR_LIGHT_COMMAND_FID), eventValue)

        // SDK setters commonly return void; otherwise treat Boolean true or nonzero numeric result as acceptance.
        val accepted = when (result) {
            null -> true
            is Boolean -> result
            is Number -> result.toInt() >= 0
            else -> true
        }
        val observedState = runCatching { readInteriorLightState(deviceClass, instance) }.getOrNull()
        return Result(
            accepted,
            value,
            observedState,
            "FID=0x${INTERIOR_LIGHT_COMMAND_FID.toString(16)}, retorno=${result ?: "void"}, " +
                "leitura 0x42e0002d=${observedState ?: "indisponível"}"
        )
    }

    private fun readInteriorLightState(deviceClass: Class<*>, instance: Any): Int {
        val getter = deviceClass.methods.firstOrNull {
            it.name == "get" && it.parameterTypes.size == 2 &&
                it.parameterTypes[0] == IntArray::class.java && it.parameterTypes[1] == Class::class.java
        } ?: error("get(int[], Class) não encontrado")
        val response = getter.invoke(instance, intArrayOf(0x42E0002D), Int::class.java)
            ?: error("getter retornou null")
        return if (response is Number) response.toInt() else {
            val valueField = response.javaClass.fields.firstOrNull { it.name == "intValue" }
            (valueField?.get(response) as? Number)?.toInt() ?: error("resposta sem intValue")
        }
    }

    private fun createEventValue(eventClass: Class<*>, value: Int): Any {
        val intConstructor = eventClass.constructors.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType))
        }
        if (intConstructor != null) return intConstructor.newInstance(value)

        val noArg = eventClass.constructors.firstOrNull { it.parameterTypes.isEmpty() }
            ?: error("BYDAutoEventValue sem construtor int ou vazio: ${eventClass.constructors.joinToString { it.toGenericString() }}")
        val event = noArg.newInstance()
        val intField = generateSequence(eventClass as Class<*>?) { it.superclass }
            .flatMap { it.declaredFields.asSequence() }
            .firstOrNull { it.name == "intValue" && it.type == Int::class.javaPrimitiveType }
            ?: error("BYDAutoEventValue não expõe campo intValue")
        intField.isAccessible = true
        intField.setInt(event, value)
        return event
    }
}
