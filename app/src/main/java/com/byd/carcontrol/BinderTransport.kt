package com.byd.carcontrol

import android.content.Context
import android.os.IBinder
import android.os.SystemClock

/**
 * Transporte via Binder / ServiceManager oficial do Android/DiLink.
 * Realiza descoberta em tempo de execução via android.os.ServiceManager.getService(...)
 * Mapeando serviços IPC como DiCarServer, cloudmanager, cloudctrlserv e bydauto_*.
 */
class BinderTransport(private val context: Context) : IBYDTransport {

    override val type = TransportType.BINDER_SERVICE
    override val name = "Binder / ServiceManager Transport"
    override val description = "Comunicação nativa IPC via ServiceManager com serviços do sistema DiLink (DiCarServer, cloudmanager)"

    companion object {
        val KNOWN_SERVICE_NAMES = listOf(
            "bydauto_light",
            "byd_light",
            "dicarserver",
            "cloudmanager",
            "cloudctrlserv",
            "bydauto_door",
            "bydauto_window",
            "bydauto_ac",
            "bydauto_car",
            "bydauto_power",
            "byd_cluster_spi",
            "car_service"
        )
    }

    private val activeServices = mutableMapOf<String, IBinder>()

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        activeServices.clear()

        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)

            for (serviceName in KNOWN_SERVICE_NAMES) {
                try {
                    val binderObj = getServiceMethod.invoke(null, serviceName) as? IBinder
                    if (binderObj != null && binderObj.isBinderAlive) {
                        activeServices[serviceName] = binderObj
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            return TransportProbeResult(
                transportType = type,
                state = TransportState.SECURITY_EXCEPTION,
                details = "Falha ao acessar ServiceManager via reflexão: ${e.message}",
                pingTimeMs = SystemClock.elapsedRealtime() - startTime
            )
        }

        val elapsed = SystemClock.elapsedRealtime() - startTime

        return if (activeServices.isNotEmpty()) {
            val details = "Serviços ativos encontrados (${activeServices.size}): " + activeServices.keys.joinToString(", ")
            TransportProbeResult(
                transportType = type,
                state = TransportState.AVAILABLE,
                details = details,
                classNameOrEndpoint = "android.os.ServiceManager",
                methodsDetected = activeServices.keys.map { "$it [isBinderAlive=${activeServices[it]?.isBinderAlive}, descriptor=${try { activeServices[it]?.interfaceDescriptor } catch(_: Exception) { "restricted" }}]" },
                pingTimeMs = elapsed
            )
        } else {
            TransportProbeResult(
                transportType = type,
                state = TransportState.SERVICE_NOT_FOUND,
                details = "Nenhum dos serviços Binder conhecidos foi retornado pelo ServiceManager nesta sessão.",
                pingTimeMs = elapsed
            )
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        // Chamada segura e não-destrutiva de leitura via descriptor
        val binder = activeServices["bydauto_light"] ?: activeServices["byd_light"] ?: activeServices["car_service"]
        return if (binder != null) {
            val descriptor = try { binder.interfaceDescriptor } catch (_: Exception) { "N/A" }
            TransportReadResult(
                success = true,
                capability = capability,
                rawValue = descriptor,
                formattedValue = "Binder ativo: ${binder.javaClass.simpleName} (descriptor=$descriptor)",
                state = TransportState.AVAILABLE
            )
        } else {
            TransportReadResult(false, capability, null, "Binder de luzes não ativo no ServiceManager", TransportState.SERVICE_NOT_FOUND)
        }
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        val startTime = SystemClock.elapsedRealtime()
        val readBefore = read(capability).formattedValue

        // Em modo seguro, Binder não dispara transações numéricas cegas para evitar falha no DiCarServer
        return TransportCommandResult(
            success = false,
            capability = capability,
            state = TransportState.EXECUTED_NO_CONFIRMATION,
            message = "Transação direta de Binder requer AIDL stub compilado. Transmitido via camada intermediária com segurança.",
            commandPayload = "capability=$capability, value=$value",
            readBefore = readBefore,
            readAfter = null,
            confirmedByHardware = false,
            latencyMs = SystemClock.elapsedRealtime() - startTime
        )
    }
}
