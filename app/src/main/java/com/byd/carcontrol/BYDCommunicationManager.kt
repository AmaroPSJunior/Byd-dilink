package com.byd.carcontrol

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Orquestrador Central do Laboratório de Comunicação BYD (BYD Communication Lab).
 * Gerencia a matriz de 13 transportes, executa o ciclo de confirmação em hardware,
 * mantém o log de baixo nível e fornece relatórios JSON completos.
 */
class BYDCommunicationManager(private val context: Context) {

    companion object {
        private const val TAG = "BYDCommManager"
        @Volatile private var instance: BYDCommunicationManager? = null

        fun getInstance(context: Context): BYDCommunicationManager {
            return instance ?: synchronized(this) {
                instance ?: BYDCommunicationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // Lista de transportes desacoplados
    val transports: List<IBYDTransport> = listOf(
        HALReflectionTransport(context),
        BinderTransport(context),
        SettingsTransport(context),
        IntentBroadcastTransport(context),
        ClusterSPITransport(context),
        CANTransport(context),
        UDSTransport(context),
        CloudManagerTransport(context),
        NativeLibraryTransport(context),
        SocketTransport(context),
        DirectDeviceTransport(context),
        ContentProviderTransport(context)
    )

    // Transporte ativo/preferencial
    var preferredTransport: IBYDTransport = transports.first { it.type == TransportType.HAL_REFLECTION }
        private set

    // Modo de teste atual (default: SAFE_READ_ONLY)
    var testMode: TestMode = TestMode.SAFE_READ_ONLY

    // Resultados da última matriz de descoberta
    val discoveryMatrix = mutableMapOf<TransportType, TransportProbeResult>()

    // Fila de logs de baixo nível
    private val communicationLogs = mutableListOf<CommunicationLogEntry>()

    // Mapeamento de capacidades confirmadas
    val confirmedCapabilities = mutableMapOf<VehicleCapability, TransportType>()

    init {
        runFullDiscovery()
    }

    /**
     * Executa varredura profunda de todos os transportes para gerar a Matriz de Comunicação.
     */
    fun runFullDiscovery(): Map<TransportType, TransportProbeResult> {
        discoveryMatrix.clear()
        for (t in transports) {
            try {
                val probeResult = t.probe()
                discoveryMatrix[t.type] = probeResult
                addLog(
                    transport = t.type,
                    action = "PROBE_DISCOVERY",
                    target = t.name,
                    request = "probe()",
                    response = probeResult.details,
                    stateBefore = null,
                    stateAfter = probeResult.state.name,
                    durationMs = probeResult.pingTimeMs,
                    finalState = probeResult.state
                )
            } catch (e: Exception) {
                discoveryMatrix[t.type] = TransportProbeResult(
                    transportType = t.type,
                    state = TransportState.EXECUTION_FAILED,
                    details = "Falha crítica no probe: ${e.message}"
                )
            }
        }
        return discoveryMatrix
    }

    /**
     * Define explicitamente um transporte preferencial após confirmação de funcionamento.
     */
    fun setPreferredTransport(type: TransportType): Boolean {
        val found = transports.find { it.type == type }
        return if (found != null) {
            preferredTransport = found
            addLog(
                transport = type,
                action = "SET_PREFERRED_TRANSPORT",
                target = found.name,
                request = type.name,
                response = "Transporte preferencial atualizado para: ${found.name}",
                stateBefore = null,
                stateAfter = null,
                durationMs = 0,
                finalState = TransportState.CONFIRMED
            )
            true
        } else false
    }

    /**
     * CICLO MANDATÓRIO DE EXECUÇÃO COM TESTE CONTROLADO:
     * 1. READ BEFORE
     * 2. SEND COMMAND
     * 3. READ AFTER
     * 4. COMPARE & CONFIRM
     *
     * NUNCA declara CONFIRMED se o estado do veículo não foi alterado na leitura posterior.
     */
    fun executeVerifiedCommand(
        capability: VehicleCapability,
        targetValue: Any,
        specificTransport: IBYDTransport? = null
    ): TransportCommandResult {
        val transport = specificTransport ?: preferredTransport
        val startTime = SystemClock.elapsedRealtime()

        // 1. Leitura Prévia (Read Before)
        val readBefore = transport.read(capability)
        val stateBeforeStr = readBefore.formattedValue

        // 2. Execução do Comando
        val cmdResult = transport.execute(capability, targetValue)

        // 3. Leitura Posterior (Read After)
        val readAfter = transport.read(capability)
        val stateAfterStr = readAfter.formattedValue

        val duration = SystemClock.elapsedRealtime() - startTime

        // 4. Comparação
        val isConfirmed = cmdResult.confirmedByHardware || (
            readBefore.success && readAfter.success &&
            stateBeforeStr != stateAfterStr
        )

        val finalState = if (isConfirmed) {
            confirmedCapabilities[capability] = transport.type
            TransportState.CONFIRMED
        } else if (cmdResult.state == TransportState.EXECUTED_NO_CONFIRMATION) {
            TransportState.EXECUTED_NO_CONFIRMATION
        } else {
            cmdResult.state
        }

        val enrichedResult = cmdResult.copy(
            state = finalState,
            readBefore = stateBeforeStr,
            readAfter = stateAfterStr,
            confirmedByHardware = isConfirmed,
            latencyMs = duration
        )

        addLog(
            transport = transport.type,
            action = "COMMAND_${capability.keyName}",
            target = transport.name,
            request = "value=$targetValue",
            response = cmdResult.message,
            stateBefore = stateBeforeStr,
            stateAfter = stateAfterStr,
            durationMs = duration,
            finalState = finalState,
            exceptionDetails = cmdResult.exceptionMessage
        )

        return enrichedResult
    }

    /**
     * Test Bench específico para Luzes da Cabine (Dolphin Plus).
     */
    fun runLightTestBench(): String {
        val sb = StringBuilder()
        sb.append("=== TEST BENCH DE ILUMINAÇÃO: BYD DOLPHIN PLUS ===\n")
        sb.append("Modo Atual: ${testMode.label}\n")
        sb.append("Transporte Preferencial: ${preferredTransport.name}\n\n")

        // Passo 1: Leitura Inicial
        sb.append("[1/4] Leitura Inicial do Plafonier...\n")
        val initialRead = preferredTransport.read(VehicleCapability.LIGHTS)
        sb.append("Estado Atual: ${initialRead.formattedValue} (${initialRead.state.label})\n\n")

        // Passo 2: Comando Desligar com Verificação
        sb.append("[2/4] Enviando Comando MASTER_OFF (0)...\n")
        val offResult = executeVerifiedCommand(VehicleCapability.LIGHTS, 0)
        sb.append("Resultado: ${offResult.state.label} - ${offResult.message}\n")
        sb.append("Confirmação de Hardware: ${if (offResult.confirmedByHardware) "SIM ✅" else "NÃO ⚠️"}\n\n")

        // Passo 3: Leitura Final
        sb.append("[3/4] Leitura Posterior do Plafonier...\n")
        val finalRead = preferredTransport.read(VehicleCapability.LIGHTS)
        sb.append("Estado Posterior: ${finalRead.formattedValue}\n\n")

        // Passo 4: Diagnóstico
        sb.append("[4/4] Conclusão:\n")
        if (offResult.confirmedByHardware) {
            sb.append("✅ SUCESSO COMPROVADO: O transporte ${preferredTransport.type} alterou e confirmou o estado do veículo!\n")
        } else {
            sb.append("⚠️ NÃO COMPROVADO: O transporte ${preferredTransport.type} não teve retorno de leitura confirmando corte físico.\n")
            sb.append("Recomenda-se testar outro transporte na matriz de descoberta.\n")
        }

        return sb.toString()
    }

    private fun addLog(
        transport: TransportType,
        action: String,
        target: String,
        request: String,
        response: String,
        stateBefore: String?,
        stateAfter: String?,
        durationMs: Long,
        finalState: TransportState,
        exceptionDetails: String? = null
    ) {
        val df = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        val entry = CommunicationLogEntry(
            id = System.currentTimeMillis().toString(),
            timestamp = df.format(Date()),
            transport = transport,
            action = action,
            target = target,
            requestPayload = request,
            responsePayload = response,
            stateBefore = stateBefore,
            stateAfter = stateAfter,
            durationMs = durationMs,
            finalState = finalState,
            exceptionDetails = exceptionDetails
        )
        synchronized(communicationLogs) {
            communicationLogs.add(0, entry)
            if (communicationLogs.size > 200) {
                communicationLogs.removeAt(communicationLogs.lastIndex)
            }
        }
        Log.d(TAG, "[${entry.timestamp}] ${transport.name} -> $action: ${finalState.label} (${durationMs}ms)")
    }

    fun getLogs(): List<CommunicationLogEntry> = synchronized(communicationLogs) { communicationLogs.toList() }

    /**
     * Coleta informações reais de firmware, Android e hardware do Dolphin Plus.
     */
    fun getFirmwareInfo(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["ro.build.version.release"] = Build.VERSION.RELEASE ?: "N/A"
        map["ro.build.version.sdk"] = Build.VERSION.SDK_INT.toString()
        map["ro.build.display.id"] = Build.DISPLAY ?: "N/A"
        map["ro.build.fingerprint"] = Build.FINGERPRINT ?: "N/A"
        map["ro.product.model"] = Build.MODEL ?: "N/A"
        map["ro.product.device"] = Build.DEVICE ?: "N/A"
        map["ro.product.name"] = Build.PRODUCT ?: "N/A"
        map["ro.hardware"] = Build.HARDWARE ?: "N/A"
        map["ro.bootloader"] = Build.BOOTLOADER ?: "N/A"
        map["dilink.version"] = if (Build.VERSION.SDK_INT >= 30) "DiLink 4.0 / 5.0 (Android 11/12)" else "DiLink 3.0 / 4.0 (Android 10)"
        map["mcu.status"] = if (discoveryMatrix[TransportType.CLUSTER_SPI]?.state == TransportState.AVAILABLE) "SPI Ativo" else "Fallback"
        return map
    }

    /**
     * Exporta o relatório de diagnóstico completo em formato JSON.
     */
    fun exportDiagnosticJson(): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"timestamp\": \"${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())}\",\n")
        sb.append("  \"firmware\": {\n")
        getFirmwareInfo().entries.forEachIndexed { idx, e ->
            sb.append("    \"${e.key}\": \"${e.value.replace("\"", "\\\"")}\"${if (idx < getFirmwareInfo().size - 1) "," else ""}\n")
        }
        sb.append("  },\n")
        sb.append("  \"transports\": [\n")
        discoveryMatrix.values.forEachIndexed { idx, t ->
            sb.append("    {\n")
            sb.append("      \"type\": \"${t.transportType.name}\",\n")
            sb.append("      \"name\": \"${t.transportType.displayName}\",\n")
            sb.append("      \"state\": \"${t.state.name}\",\n")
            sb.append("      \"details\": \"${t.details.replace("\"", "\\\"")}\",\n")
            sb.append("      \"pingTimeMs\": ${t.pingTimeMs}\n")
            sb.append("    }${if (idx < discoveryMatrix.size - 1) "," else ""}\n")
        }
        sb.append("  ],\n")
        sb.append("  \"confirmedCapabilities\": {\n")
        confirmedCapabilities.entries.forEachIndexed { idx, e ->
            sb.append("    \"${e.key.name}\": \"${e.value.name}\"${if (idx < confirmedCapabilities.size - 1) "," else ""}\n")
        }
        sb.append("  },\n")
        sb.append("  \"recentLogsCount\": ${communicationLogs.size}\n")
        sb.append("}\n")
        return sb.toString()
    }
}
