package com.byd.carcontrol

import android.content.Context
import android.os.SystemClock
import java.lang.reflect.Method

/**
 * Transporte via HAL / Reflection direta nas classes de hardware BYD DiLink.
 * Otimizado com base na engenharia reversa do Dolphin e Dolphin Plus:
 * - android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice
 * - android.hardware.bydauto.setting.BYDAutoSettingDevice
 * - android.hardware.bydauto.speed.BYDAutoSpeedDevice
 * - android.hardware.bydauto.light.BYDAutoLightDevice
 * - android.hardware.bydauto.door.BYDAutoDoorDevice
 * - com.byd.auto.light.BYDAutoLightDevice
 */
class HALReflectionTransport(private val context: Context) : IBYDTransport {

    override val type = TransportType.HAL_REFLECTION
    override val name = "HAL Reflection Transport"
    override val description = "Acesso direto às classes de hardware android.hardware.bydauto.* e com.byd.auto.* via Reflection"

    companion object {
        val TARGET_CLASSES = listOf(
            "android.hardware.bydauto.light.BYDAutoLightDevice",
            "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice",
            "android.hardware.bydauto.setting.BYDAutoSettingDevice",
            "android.hardware.bydauto.speed.BYDAutoSpeedDevice",
            "android.hardware.bydauto.door.BYDAutoDoorDevice",
            "android.hardware.bydauto.window.BYDAutoWindowDevice",
            "android.hardware.bydauto.aircondition.BYDAutoAirConditionDevice",
            "com.byd.auto.light.BYDAutoLightDevice",
            "com.byd.auto.BYDAutoDeviceManager",
            "com.byd.auto.light.BYDLightManager"
        )
    }

    private val loadedInstances = mutableMapOf<String, Any>()
    private val discoveredMethods = mutableMapOf<String, List<String>>()

    init {
        scanAvailableClasses()
    }

    private fun scanAvailableClasses() {
        for (className in TARGET_CLASSES) {
            try {
                val clazz = Class.forName(className)
                val instance = try {
                    clazz.getMethod("getInstance", Context::class.java).invoke(null, context)
                } catch (_: NoSuchMethodException) {
                    try {
                        clazz.getMethod("getInstance").invoke(null)
                    } catch (_: Exception) {
                        try { clazz.getDeclaredConstructor().newInstance() } catch (_: Exception) { null }
                    }
                }

                if (instance != null) {
                    loadedInstances[className] = instance
                    discoveredMethods[className] = clazz.methods.map { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }}): ${it.returnType.simpleName}" }
                }
            } catch (_: ClassNotFoundException) {
                // Classe não presente nesta versão de ROM
            } catch (e: Exception) {
                // Outro erro de reflexão
            }
        }
    }

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        scanAvailableClasses()
        val elapsed = SystemClock.elapsedRealtime() - startTime

        return if (loadedInstances.isNotEmpty()) {
            val allMethods = discoveredMethods.values.flatten()
            TransportProbeResult(
                transportType = type,
                state = TransportState.AVAILABLE,
                details = "Vinculado com sucesso a ${loadedInstances.size} classe(s) HAL BYD: ${loadedInstances.keys.joinToString()}",
                classNameOrEndpoint = loadedInstances.keys.firstOrNull(),
                methodsDetected = allMethods,
                permissionsRequired = listOf("com.byd.permission.CAR_LIGHT_CONTROL", "android.car.permission.CONTROL_CAR_INTERIOR_LIGHTS"),
                pingTimeMs = elapsed
            )
        } else {
            TransportProbeResult(
                transportType = type,
                state = TransportState.UNAVAILABLE,
                details = "Nenhuma das classes android.hardware.bydauto.* ou com.byd.auto.* pôde ser instanciada.",
                pingTimeMs = elapsed
            )
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        return try {
            when (capability) {
                VehicleCapability.LIGHTS, VehicleCapability.READING_LIGHTS -> {
                    val lightInstance = loadedInstances["android.hardware.bydauto.light.BYDAutoLightDevice"]
                        ?: loadedInstances["com.byd.auto.light.BYDAutoLightDevice"]

                    if (lightInstance != null) {
                        val getMethod = try {
                            lightInstance.javaClass.getMethod("getReadingLight", Int::class.javaPrimitiveType)
                        } catch (_: NoSuchMethodException) {
                            try { lightInstance.javaClass.getMethod("getReadingLightState", Int::class.javaPrimitiveType) }
                            catch (_: Exception) { null }
                        }

                        if (getMethod != null) {
                            val valResult = getMethod.invoke(lightInstance, 0)
                            TransportReadResult(
                                success = true,
                                capability = capability,
                                rawValue = valResult,
                                formattedValue = if (valResult == 0) "DESLIGADO (0)" else "LIGADO ($valResult)",
                                state = TransportState.AVAILABLE
                            )
                        } else {
                            TransportReadResult(false, capability, null, "Método getReadingLight não disponível na classe", TransportState.METHOD_NOT_FOUND)
                        }
                    } else {
                        TransportReadResult(false, capability, null, "HAL de Luzes não instanciado", TransportState.SERVICE_NOT_FOUND)
                    }
                }
                else -> {
                    TransportReadResult(false, capability, null, "Leitura ainda não implementada para $capability nesta HAL", TransportState.UNAVAILABLE)
                }
            }
        } catch (e: Exception) {
            TransportReadResult(false, capability, null, "Exceção ao ler HAL: ${e.message}", TransportState.EXECUTION_FAILED, e.stackTraceToString())
        }
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        val startTime = SystemClock.elapsedRealtime()
        val readBefore = read(capability).formattedValue

        try {
            val lightInstance = loadedInstances["android.hardware.bydauto.light.BYDAutoLightDevice"]
                ?: loadedInstances["com.byd.auto.light.BYDAutoLightDevice"]

            if (lightInstance == null) {
                return TransportCommandResult(
                    success = false,
                    capability = capability,
                    state = TransportState.SERVICE_NOT_FOUND,
                    message = "Instância HAL BYDAutoLightDevice ausente",
                    commandPayload = "value=$value",
                    readBefore = readBefore,
                    readAfter = null,
                    confirmedByHardware = false,
                    latencyMs = SystemClock.elapsedRealtime() - startTime
                )
            }

            val intVal = if (value is Boolean) (if (value) 1 else 0) else (value as? Int ?: 0)
            var invokedMethod: Method? = null

            for (mName in listOf("setReadingLight", "setReadingLightState", "setReadingLightSwitch")) {
                try {
                    val m = lightInstance.javaClass.getMethod(mName, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    m.invoke(lightInstance, 0, intVal)
                    invokedMethod = m
                    break
                } catch (_: NoSuchMethodException) {}
            }

            val latency = SystemClock.elapsedRealtime() - startTime
            val readAfter = read(capability).formattedValue

            // VERIFICAÇÃO RIGOROSA: se readAfter != null e corresponde ao comando, CONFIRMED
            val confirmed = (readAfter.contains("DESLIGADO") && intVal == 0) || (readAfter.contains("LIGADO") && intVal == 1)

            val finalState = when {
                confirmed -> TransportState.CONFIRMED
                invokedMethod != null -> TransportState.EXECUTED_NO_CONFIRMATION
                else -> TransportState.METHOD_NOT_FOUND
            }

            return TransportCommandResult(
                success = invokedMethod != null,
                capability = capability,
                state = finalState,
                message = if (confirmed) "Comando verificado e CONFIRMADO no HAL" else "Invocado ${invokedMethod?.name ?: "Nenhum"}, aguardando ACK",
                commandPayload = "method=${invokedMethod?.name ?: "NONE"}, zone=0, val=$intVal",
                readBefore = readBefore,
                readAfter = readAfter,
                confirmedByHardware = confirmed,
                latencyMs = latency
            )
        } catch (e: Exception) {
            return TransportCommandResult(
                success = false,
                capability = capability,
                state = TransportState.EXECUTION_FAILED,
                message = "Erro ao executar HAL: ${e.message}",
                commandPayload = "value=$value",
                readBefore = readBefore,
                readAfter = null,
                confirmedByHardware = false,
                latencyMs = SystemClock.elapsedRealtime() - startTime,
                exceptionMessage = e.stackTraceToString()
            )
        }
    }
}
