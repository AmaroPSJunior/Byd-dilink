package com.byd.carcontrol

import android.content.Context

/** Read-only check of OEM interior/door-light and reading-light status features. */
object BydInteriorLightReader {
    private data class Feature(val device: String, val name: String, val id: Int)

    private val features = listOf(
        Feature("setting", "inside_light_door_state", 0x42E0002B),
        Feature("setting", "inside_light_door_state_second", 0x3FF00026),
        Feature("setting", "inside_light_online", 0x3FF00009),
        Feature("setting", "has_interior_atmosphere_lamp", 0x3FF00000),
        Feature("setting", "interior_lamp_duration", 0x39400015),
        Feature("light", "front_left_reading_light", 0x3FE0000A),
        Feature("light", "front_right_reading_light", 0x3FE0000C),
        Feature("light", "middle_left_reading_light", 0x3FE0000E),
        Feature("light", "middle_right_reading_light", 0x3FE00010),
        Feature("light", "rear_left_reading_light", 0x3FE00016),
        Feature("light", "rear_right_reading_light", 0x3FE00018)
    )

    fun read(context: Context): List<String> {
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val instances = mutableMapOf<String, Pair<Class<*>, Any>>()
        return features.map { feature ->
            runCatching {
                val (clazz, instance) = instances.getOrPut(feature.device) {
                    val packageName = if (feature.device == "setting") "setting" else "light"
                    val className = "android.hardware.bydauto.$packageName.BYDAuto${if (packageName == "setting") "Setting" else "Light"}Device"
                    val deviceClass = Class.forName(className)
                    val device = deviceClass.getMethod("getInstance", Context::class.java)
                        .invoke(null, sdkContext) ?: error("getInstance retornou null")
                    deviceClass to device
                }
                val getter = clazz.methods.firstOrNull {
                    it.name == "get" && it.parameterTypes.size == 2 &&
                        it.parameterTypes[0] == IntArray::class.java && it.parameterTypes[1] == Class::class.java
                } ?: error("método get(int[], Class) não encontrado")
                val result = getter.invoke(instance, intArrayOf(feature.id), Int::class.java)
                    ?: error("getter retornou null")
                val raw = if (result is Number) result.toInt() else {
                    val valueField = result.javaClass.fields.firstOrNull { it.name == "intValue" }
                    (valueField?.get(result) as? Number)?.toInt()
                        ?: error("resposta ${result.javaClass.name} sem intValue")
                }
                "${feature.name}=0x${feature.id.toString(16)}:$raw"
            }.getOrElse { error ->
                "${feature.name}=0x${feature.id.toString(16)}:ERRO(${error.cause?.javaClass?.simpleName ?: error.javaClass.simpleName}:${error.cause?.message ?: error.message})"
            }
        }
    }
}
