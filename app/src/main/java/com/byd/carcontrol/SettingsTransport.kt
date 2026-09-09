package com.byd.carcontrol

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Transporte via Settings.System / Settings.Global do DiLink OS.
 * Implementa o ciclo obrigatório: READ BEFORE -> WRITE -> READ AFTER -> COMPARE.
 * Se a escrita não for refletida na leitura subsequente, NUNCA retorna sucesso.
 */
class SettingsTransport(private val context: Context) : IBYDTransport {

    override val type = TransportType.SETTINGS_SYSTEM
    override val name = "Settings.System Transport"
    override val description = "Provedor de configurações persistentes do sistema Android DiLink (auto_dome_light, ambient_light)"

    companion object {
        val LIGHT_KEYS = listOf(
            "auto_dome_light",
            "byd_auto_dome_light",
            "byd_dome_light",
            "byd_reading_light",
            "byd_reading_light_fl",
            "byd_reading_light_fr",
            "byd_reading_light_rear",
            "byd_ambient_light_switch",
            "byd_atmosphere_light_switch"
        )
    }

    private fun hasPermission(): Boolean {
        return Settings.System.canWrite(context)
    }

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        val permissionGranted = hasPermission()

        val foundKeys = mutableListOf<String>()
        val cr = context.contentResolver
        for (key in LIGHT_KEYS) {
            try {
                val v = Settings.System.getInt(cr, key, -999)
                if (v != -999) {
                    foundKeys.add("$key=$v")
                }
            } catch (_: Exception) {}
        }

        val elapsed = SystemClock.elapsedRealtime() - startTime

        return if (!permissionGranted) {
            TransportProbeResult(
                transportType = type,
                state = TransportState.PERMISSION_DENIED,
                details = "Permissão WRITE_SETTINGS não concedida pelo usuário na multimídia.",
                permissionsRequired = listOf("android.permission.WRITE_SETTINGS"),
                pingTimeMs = elapsed
            )
        } else if (foundKeys.isNotEmpty()) {
            TransportProbeResult(
                transportType = type,
                state = TransportState.AVAILABLE,
                details = "Chaves DiLink detectadas (${foundKeys.size}): " + foundKeys.joinToString(", "),
                classNameOrEndpoint = "android.provider.Settings.System",
                methodsDetected = foundKeys,
                permissionsRequired = listOf("android.permission.WRITE_SETTINGS"),
                pingTimeMs = elapsed
            )
        } else {
            TransportProbeResult(
                transportType = type,
                state = TransportState.AVAILABLE,
                details = "Permissão concedida; chaves serão criadas/acessadas sob demanda.",
                pingTimeMs = elapsed
            )
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        return try {
            val cr = context.contentResolver
            val domeVal = Settings.System.getInt(cr, "auto_dome_light", -1)
            val ambientVal = Settings.System.getInt(cr, "byd_ambient_light_switch", -1)

            val summary = "auto_dome_light=$domeVal, ambient_switch=$ambientVal"
            TransportReadResult(
                success = true,
                capability = capability,
                rawValue = mapOf("auto_dome_light" to domeVal, "byd_ambient_light_switch" to ambientVal),
                formattedValue = summary,
                state = TransportState.AVAILABLE
            )
        } catch (e: Exception) {
            TransportReadResult(false, capability, null, "Erro ao ler Settings: ${e.message}", TransportState.EXECUTION_FAILED, e.stackTraceToString())
        }
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        val startTime = SystemClock.elapsedRealtime()
        val readBefore = read(capability).formattedValue

        if (!hasPermission()) {
            return TransportCommandResult(
                success = false,
                capability = capability,
                state = TransportState.PERMISSION_DENIED,
                message = "Operação rejeitada: Permissão WRITE_SETTINGS ausente",
                commandPayload = "value=$value",
                readBefore = readBefore,
                readAfter = null,
                confirmedByHardware = false,
                latencyMs = SystemClock.elapsedRealtime() - startTime
            )
        }

        val targetInt = if (value is Boolean) (if (value) 1 else 0) else (value as? Int ?: 0)
        val cr = context.contentResolver
        var keysWritten = 0

        for (key in LIGHT_KEYS) {
            try {
                Settings.System.putInt(cr, key, targetInt)
                keysWritten++
            } catch (_: Exception) {}
        }

        val latency = SystemClock.elapsedRealtime() - startTime
        val readAfterObj = read(capability)
        val readAfter = readAfterObj.formattedValue

        // Verificação estrita se o valor gravado bate com o lido
        val verified = readAfter.contains("auto_dome_light=$targetInt")

        val state = when {
            verified -> TransportState.CONFIRMED
            keysWritten > 0 -> TransportState.EXECUTED_NO_CONFIRMATION
            else -> TransportState.EXECUTION_FAILED
        }

        return TransportCommandResult(
            success = verified,
            capability = capability,
            state = state,
            message = if (verified) "Configuração do sistema gravada e CONFIRMADA ($keysWritten chaves)" else "Escrita efetuada mas valor não alterado",
            commandPayload = "targetValue=$targetInt, keysCount=$keysWritten",
            readBefore = readBefore,
            readAfter = readAfter,
            confirmedByHardware = verified,
            latencyMs = latency
        )
    }
}
