package com.byd.carcontrol.discovery

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.provider.Settings
import com.byd.carcontrol.repository.DiscoveryRepository
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier

data class LightCommandAttempt(
    val index: Int,
    val total: Int,
    val transportName: String,
    val commandName: String,
    val payloadStr: String,
    val success: Boolean,
    val statusLabel: String, // "ACK", "VERIFIED_READ_AFTER", "EXCEPTED", "SETTINGS_UPDATED", "BROADCAST_SENT"
    val readBefore: String?,
    val readAfter: String?,
    val latencyMs: Long,
    val errorDetails: String? = null
)

interface LightCommandProgressListener {
    fun onCommandStarted(index: Int, total: Int, commandName: String, transport: String)
    fun onCommandCompleted(attempt: LightCommandAttempt)
    fun onBatchFinished(successCount: Int, totalCount: Int, summaryReport: String)
}

/**
 * Motor de Execução Sequencial de Comandos de Iluminação para BYD Dolphin Plus (DiLink 3.0/4.0).
 * Executa todos os protocolos conhecidos (HAL, Settings, Intent, Binder, ContentProvider)
 * com delay configurável para observação visual real dentro da cabine do veículo.
 */
class BYDLightCommandEngine(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRunning = false

    fun stopBatch() {
        isRunning = false
    }

    /**
     * Executa a varredura completa de acender (ON = 1) ou apagar (OFF = 0)
     */
    fun runAllLightCommands(
        turnOn: Boolean,
        delayBetweenMs: Long = 400L,
        listener: LightCommandProgressListener
    ) {
        if (isRunning) return
        isRunning = true

        val targetValInt = if (turnOn) 1 else 0
        val targetValBool = turnOn
        val modeLabel = if (turnOn) "LIGAR / ACENDER (1)" else "DESLIGAR / APAGAR (0)"

        val commandQueue = mutableListOf<() -> LightCommandAttempt>()

        // ----------------------------------------------------
        // PROTOCOLO 1: HAL REFLECTION (android.hardware.bydauto.light.BYDAutoLightDevice & com.byd.auto.*)
        // ----------------------------------------------------
        val halClassNames = listOf(
            "android.hardware.bydauto.light.BYDAutoLightDevice",
            "com.byd.auto.light.BYDAutoLightDevice",
            "com.byd.auto.light.BYDLightManager",
            "com.byd.auto.BYDAutoDeviceManager"
        )

        val halMethodsToTest = listOf(
            "setReadingLight",
            "setReadingLightState",
            "setReadingLightSwitch",
            "setAmbientLightSwitch",
            "setAmbientLightColor",
            "setAmbientLightBrightness",
            "setDomeLightState",
            "setLightState",
            "setValue",
            "postValue",
            "setCommonCommand"
        )

        for (cName in halClassNames) {
            for (mName in halMethodsToTest) {
                // Zone 0, 1, 2, 3
                for (zone in listOf(0, 1, 2, 3)) {
                    commandQueue.add {
                        executeHalMethodCommand(cName, mName, zone, targetValInt)
                    }
                }
            }
        }

        // ----------------------------------------------------
        // PROTOCOLO 2: SETTINGS.SYSTEM DATABASE KEYS
        // ----------------------------------------------------
        val settingsKeys = listOf(
            "auto_dome_light",
            "byd_ambient_light_switch",
            "byd_reading_light_state",
            "byd_light_master_off",
            "car_light_dome_state",
            "car_reading_light_driver",
            "car_reading_light_passenger",
            "car_reading_light_rear",
            "byd_interior_light_switch"
        )

        for (key in settingsKeys) {
            commandQueue.add {
                executeSettingsCommand(key, if (key == "byd_light_master_off") (if (turnOn) 0 else 1) else targetValInt)
            }
        }

        // ----------------------------------------------------
        // PROTOCOLO 3: INTENT BROADCASTS
        // ----------------------------------------------------
        val intentActions = listOf(
            Pair("com.byd.intent.action.LIGHT_CONTROL", mapOf("light_type" to 1, "light_state" to targetValInt)),
            Pair("com.byd.action.CAR_LIGHT_CONTROL", mapOf("cmd" to targetValInt)),
            Pair("android.intent.action.BYD_LIGHT_SWITCH", mapOf("state" to targetValInt)),
            Pair("com.byd.auto.action.READING_LIGHT", mapOf("value" to targetValInt)),
            Pair("com.byd.car.action.INTERIOR_LIGHT", mapOf("enable" to targetValBool))
        )

        for ((action, extras) in intentActions) {
            commandQueue.add {
                executeBroadcastCommand(action, extras)
            }
        }

        // ----------------------------------------------------
        // PROTOCOLO 4: BINDER DIRECT TRANSACT (byd_car_service, bydauto_light)
        // ----------------------------------------------------
        val binderServices = listOf("byd_car_service", "bydauto_light", "dicarserver", "cloudmanager")
        for (srv in binderServices) {
            for (code in listOf(1, 2, 3, 1004)) {
                commandQueue.add {
                    executeBinderCommand(srv, code, targetValInt)
                }
            }
        }

        val totalCount = commandQueue.size
        DiscoveryLogger.log("LIGHT_ENGINE", "START_BATCH", modeLabel, "Iniciando fila de $totalCount comandos de iluminação")

        // Executar sequencialmente no worker thread
        Thread {
            var successCount = 0
            val attemptsList = mutableListOf<LightCommandAttempt>()

            for (i in 0 until totalCount) {
                if (!isRunning) break

                val idx = i + 1
                val cmdBlock = commandQueue[i]

                mainHandler.post {
                    listener.onCommandStarted(idx, totalCount, "Comando $idx", "Calculando...")
                }

                val attempt = cmdBlock.invoke().copy(index = idx, total = totalCount)
                attemptsList.add(attempt)

                if (attempt.success) successCount++

                // Salvar descoberta no SQLite
                repository.saveDiscovery(
                    category = "LIGHT_TEST_CMD",
                    name = "${attempt.transportName}#${attempt.commandName}",
                    status = if (attempt.success) DiscoveryStatus.VALIDATED else DiscoveryStatus.FAILED,
                    evidenceJson = JSONObject().apply {
                        put("mode", modeLabel)
                        put("payload", attempt.payloadStr)
                        put("status", attempt.statusLabel)
                        put("latencyMs", attempt.latencyMs)
                        put("error", attempt.errorDetails ?: "none")
                    }.toString()
                )

                mainHandler.post {
                    listener.onCommandCompleted(attempt)
                }

                // Pausa configurável entre comandos para permitir inspeção física da lâmpada
                if (delayBetweenMs > 0) {
                    try { Thread.sleep(delayBetweenMs) } catch (_: InterruptedException) {}
                }
            }

            isRunning = false

            val summary = """
                ==================================================
                VARREDURA DE COMANDOS DE ILUMINAÇÃO CONCLUÍDA
                ==================================================
                Modo Executado: $modeLabel
                Total de Comandos Testados: ${attemptsList.size} / $totalCount
                Sucessos de Envio / ACK: $successCount
                ==================================================
            """.trimIndent()

            mainHandler.post {
                listener.onBatchFinished(successCount, totalCount, summary)
            }
        }.start()
    }

    // --- MÉTODOS PRIVADOS DE EXECUÇÃO DE PROTOCOLO ---

    private fun executeHalMethodCommand(cName: String, mName: String, zone: Int, valInt: Int): LightCommandAttempt {
        val start = SystemClock.elapsedRealtime()
        var success = false
        var statusLabel = "METHOD_NOT_FOUND"
        var errorDetails: String? = null

        try {
            val clazz = Class.forName(cName)
            val instance = try {
                clazz.getMethod("getInstance", Context::class.java).invoke(null, context)
            } catch (_: Exception) {
                try { clazz.getMethod("getInstance").invoke(null) } catch (_: Exception) { null }
            }

            if (instance != null) {
                // Tentar encontrar método com 2 parâmetros int (zone, val)
                var method: Method? = null
                try {
                    method = clazz.getMethod(mName, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                } catch (_: NoSuchMethodException) {
                    try {
                        method = clazz.getMethod(mName, Int::class.javaPrimitiveType)
                    } catch (_: NoSuchMethodException) {}
                }

                if (method != null) {
                    method.isAccessible = true
                    if (method.parameterCount == 2) {
                        method.invoke(instance, zone, valInt)
                    } else if (method.parameterCount == 1) {
                        method.invoke(instance, valInt)
                    }
                    success = true
                    statusLabel = "ACK (Method Invoked)"
                } else {
                    statusLabel = "METHOD_NOT_FOUND"
                }
            } else {
                statusLabel = "INSTANCE_NULL"
            }
        } catch (e: Exception) {
            val cause = e.cause ?: e
            success = false
            statusLabel = "EXCEPTED (${cause.javaClass.simpleName})"
            errorDetails = "${cause.javaClass.simpleName}: ${cause.message}"
        }

        val latency = SystemClock.elapsedRealtime() - start
        return LightCommandAttempt(
            index = 0, total = 0,
            transportName = "HAL Reflection",
            commandName = "$cName#$mName",
            payloadStr = "zone=$zone, val=$valInt",
            success = success,
            statusLabel = statusLabel,
            readBefore = null, readAfter = null,
            latencyMs = latency,
            errorDetails = errorDetails
        )
    }

    private fun executeSettingsCommand(key: String, valInt: Int): LightCommandAttempt {
        val start = SystemClock.elapsedRealtime()
        var success = false
        var statusLabel = "SETTINGS_UPDATED"
        var errorDetails: String? = null

        try {
            val readBefore = Settings.System.getInt(context.contentResolver, key, -1)
            val updated = Settings.System.putInt(context.contentResolver, key, valInt)
            val readAfter = Settings.System.getInt(context.contentResolver, key, -1)

            success = updated && (readAfter == valInt)
            statusLabel = if (success) "VERIFIED_READ_AFTER ($readBefore -> $readAfter)" else "WRITE_FAILED"
        } catch (e: Exception) {
            success = false
            statusLabel = "PERMISSION_DENIED (${e.javaClass.simpleName})"
            errorDetails = e.message
        }

        val latency = SystemClock.elapsedRealtime() - start
        return LightCommandAttempt(
            index = 0, total = 0,
            transportName = "Settings.System",
            commandName = "Settings.System.putInt('$key')",
            payloadStr = "$key=$valInt",
            success = success,
            statusLabel = statusLabel,
            readBefore = null, readAfter = null,
            latencyMs = latency,
            errorDetails = errorDetails
        )
    }

    private fun executeBroadcastCommand(action: String, extras: Map<String, Any>): LightCommandAttempt {
        val start = SystemClock.elapsedRealtime()
        var success = false
        var statusLabel = "BROADCAST_SENT"
        var errorDetails: String? = null

        try {
            val intent = Intent(action)
            for ((k, v) in extras) {
                when (v) {
                    is Int -> intent.putExtra(k, v)
                    is Boolean -> intent.putExtra(k, v)
                    is String -> intent.putExtra(k, v)
                }
            }
            context.sendBroadcast(intent)
            success = true
            statusLabel = "BROADCAST_DISPATCHED"
        } catch (e: Exception) {
            success = false
            statusLabel = "BROADCAST_FAILED"
            errorDetails = e.message
        }

        val latency = SystemClock.elapsedRealtime() - start
        return LightCommandAttempt(
            index = 0, total = 0,
            transportName = "Intent Broadcast",
            commandName = action,
            payloadStr = extras.toString(),
            success = success,
            statusLabel = statusLabel,
            readBefore = null, readAfter = null,
            latencyMs = latency,
            errorDetails = errorDetails
        )
    }

    private fun executeBinderCommand(serviceName: String, code: Int, valInt: Int): LightCommandAttempt {
        val start = SystemClock.elapsedRealtime()
        var success = false
        var statusLabel = "BINDER_TRANSACT"
        var errorDetails: String? = null

        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceM = smClass.getMethod("getService", String::class.java)
            val binderObj = getServiceM.invoke(null, serviceName) as? IBinder

            if (binderObj != null && binderObj.isBinderAlive) {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                try {
                    data.writeInterfaceToken(binderObj.interfaceDescriptor ?: "android.os.IInterface")
                    data.writeInt(0) // Zone
                    data.writeInt(valInt) // Value
                    val res = binderObj.transact(code, data, reply, 0)
                    success = res
                    statusLabel = if (res) "TRANSACT_ACK (Code $code)" else "TRANSACT_FALSE"
                } finally {
                    data.recycle()
                    reply.recycle()
                }
            } else {
                statusLabel = "SERVICE_NOT_FOUND"
            }
        } catch (e: Exception) {
            success = false
            statusLabel = "BINDER_ERROR (${e.javaClass.simpleName})"
            errorDetails = e.message
        }

        val latency = SystemClock.elapsedRealtime() - start
        return LightCommandAttempt(
            index = 0, total = 0,
            transportName = "Binder IPC",
            commandName = "ServiceManager.getService('$serviceName').transact($code)",
            payloadStr = "code=$code, val=$valInt",
            success = success,
            statusLabel = statusLabel,
            readBefore = null, readAfter = null,
            latencyMs = latency,
            errorDetails = errorDetails
        )
    }
}
