package com.byd.carcontrol

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock

/**
 * Transporte via Broadcast Intents direcionados explicitamente para os pacotes do sistema DiLink.
 * Regra Crítica: Disparar um sendBroadcast NUNCA é marcado como CONFIRMED, pois broadcasts assíncronos
 * não garantem recebimento pelo ECU ou pela camada DiLink sem um listener de resposta.
 */
class IntentBroadcastTransport(private val context: Context) : IBYDTransport {

    override val type = TransportType.INTENT_BROADCAST
    override val name = "Intent Broadcast Transport"
    override val description = "Transmissão direcionada explícita de Intents para receptores dos pacotes com.byd.*"

    companion object {
        val BYD_PACKAGES = listOf(
            "com.byd.auto",
            "com.byd.carsettings",
            "com.byd.autorun.service",
            "com.byd.service",
            "com.byd.autoui",
            "com.byd.systemui"
        )

        val ACTION_CATALOG = listOf(
            "com.byd.action.CONTROL_LIGHTS",
            "com.byd.action.LIGHT_CONTROL",
            "byd.intent.action.LIGHT_CONTROL",
            "com.byd.action.DOMELIGHT_OFF",
            "com.byd.action.AMBIENT_LIGHT_SWITCH",
            "com.byd.action.SCREEN_OFF",
            "com.byd.action.NIGHT_MODE"
        )
    }

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        val pm = context.packageManager
        val discoveredReceivers = mutableListOf<String>()

        for (action in ACTION_CATALOG) {
            val intent = Intent(action)
            val list = pm.queryBroadcastReceivers(intent, PackageManager.MATCH_ALL)
            for (resolveInfo in list) {
                discoveredReceivers.add("$action -> ${resolveInfo.activityInfo.packageName}/${resolveInfo.activityInfo.name}")
            }
        }

        val elapsed = SystemClock.elapsedRealtime() - startTime

        return if (discoveredReceivers.isNotEmpty()) {
            TransportProbeResult(
                transportType = type,
                state = TransportState.AVAILABLE,
                details = "Encontrados ${discoveredReceivers.size} BroadcastReceivers do sistema registrados.",
                classNameOrEndpoint = "android.content.Context.sendBroadcast",
                methodsDetected = discoveredReceivers,
                permissionsRequired = listOf("com.byd.permission.BYD_AUTO_CONTROL"),
                pingTimeMs = elapsed
            )
        } else {
            TransportProbeResult(
                transportType = type,
                state = TransportState.AVAILABLE,
                details = "Nenhum receiver estático público encontrado via queryBroadcastReceivers. Intents serão transmitidos com fallback explícito a cada pacote.",
                pingTimeMs = elapsed
            )
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        return TransportReadResult(
            success = false,
            capability = capability,
            rawValue = null,
            formattedValue = "Intents são de via única (Write-Only). Leitura não suportada neste canal.",
            state = TransportState.UNAVAILABLE
        )
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        val startTime = SystemClock.elapsedRealtime()
        val intVal = if (value is Boolean) (if (value) 1 else 0) else (value as? Int ?: 0)

        val baseIntent = Intent("com.byd.action.CONTROL_LIGHTS").apply {
            putExtra("light_type", "ALL_INTERIOR")
            putExtra("state", intVal)
            putExtra("command", if (intVal == 0) "MASTER_OFF" else "MASTER_ON")
        }

        var dispatched = 0
        try {
            context.sendBroadcast(baseIntent)
            dispatched++
        } catch (_: Exception) {}

        for (pkg in BYD_PACKAGES) {
            try {
                val targeted = Intent(baseIntent).apply { setPackage(pkg) }
                context.sendBroadcast(targeted)
                dispatched++
            } catch (_: Exception) {}
        }

        val latency = SystemClock.elapsedRealtime() - startTime

        // REGRA DE OURO: Intents sem telemetria de retorno SÓ PODEM SER classificados como EXECUTED_NO_CONFIRMATION!
        return TransportCommandResult(
            success = dispatched > 0,
            capability = capability,
            state = TransportState.EXECUTED_NO_CONFIRMATION,
            message = "Disparados $dispatched broadcasts direcionados (sem ACK de hardware). Status: NÃO CONFIRMADO.",
            commandPayload = "action=CONTROL_LIGHTS, state=$intVal, targets=${BYD_PACKAGES.size}",
            readBefore = null,
            readAfter = null,
            confirmedByHardware = false,
            latencyMs = latency
        )
    }
}
