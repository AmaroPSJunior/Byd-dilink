package com.byd.carcontrol

import android.content.Context
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import java.io.File

/**
 * 7. Transporte Cluster SPI (com.byd.cluster.spi e ClusterDebugService)
 * Documentado no projeto de engenharia reversa do BYD Dolphin:
 * BYDAutoManager -> Binder -> DiCarServer -> auto.default.so -> /dev/spidev_ivi -> MCU
 */
class ClusterSPITransport(private val context: Context) : IBYDTransport {
    override val type = TransportType.CLUSTER_SPI
    override val name = "Cluster SPI Transport"
    override val description = "Barramento SPI do Cluster/Painel de Instrumentos (com.byd.cluster.spi / ClusterDebug)"

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        var spiDetected = false
        val foundDetails = mutableListOf<String>()

        // Verifica existência de classes do Cluster SPI
        for (cName in listOf("com.byd.cluster.spi.ClusterSpiDevice", "com.byd.service.ClusterDebugService", "com.byd.cluster.spi.ClusterDebug")) {
            try {
                Class.forName(cName)
                spiDetected = true
                foundDetails.add("Classe $cName presente")
            } catch (_: ClassNotFoundException) {}
        }

        // Verifica nó de dispositivo SPI do IVI
        val spidevFile = File("/dev/spidev_ivi")
        if (spidevFile.exists()) {
            spiDetected = true
            foundDetails.add("/dev/spidev_ivi presente (Readable=${spidevFile.canRead()}, Writable=${spidevFile.canWrite()})")
        }

        val elapsed = SystemClock.elapsedRealtime() - startTime
        return if (spiDetected) {
            TransportProbeResult(type, TransportState.AVAILABLE, "Cluster SPI detectado: " + foundDetails.joinToString(", "), pingTimeMs = elapsed)
        } else {
            TransportProbeResult(type, TransportState.UNAVAILABLE, "Classes com.byd.cluster.spi e /dev/spidev_ivi não acessíveis.", pingTimeMs = elapsed)
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        return TransportReadResult(false, capability, null, "Cluster SPI em modo diagnóstico: requer sessão aberta", TransportState.UNAVAILABLE)
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        return TransportCommandResult(false, capability, TransportState.EXECUTION_FAILED, "Injeção SPI bloqueada por modo SAFE READ ONLY", "value=$value", null, null, false, 0)
    }
}

/**
 * 8. CAN Transport (Barramento CAN nativo)
 */
class CANTransport(private val context: Context) : IBYDTransport {
    override val type = TransportType.CAN_BUS
    override val name = "CAN Bus Transport"
    override val description = "Interface CAN Bus veicular para telemetria em tempo real"

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        val canFiles = listOf(File("/dev/can0"), File("/dev/can1"), File("/sys/class/net/can0"))
        val existing = canFiles.filter { it.exists() }

        val elapsed = SystemClock.elapsedRealtime() - startTime
        return if (existing.isNotEmpty()) {
            TransportProbeResult(type, TransportState.AVAILABLE, "Dispositivos CAN detectados: " + existing.map { it.absolutePath }.joinToString(), pingTimeMs = elapsed)
        } else {
            TransportProbeResult(type, TransportState.UNAVAILABLE, "Dispositivos SocketCAN não expostos no espaço de usuário Android.", pingTimeMs = elapsed)
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        return TransportReadResult(false, capability, null, "Leitura direta de frames CAN requer driver nativo inicializado", TransportState.UNAVAILABLE)
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        return TransportCommandResult(false, capability, TransportState.EXECUTION_FAILED, "Transmissão CAN arbitrária proibida pelo protocolo de segurança", "value=$value", null, null, false, 0)
    }
}

/**
 * 9. UDS Diagnostics (ISO 14229 / BYDAutoOtaDevice)
 */
class UDSTransport(private val context: Context) : IBYDTransport {
    override val type = TransportType.UDS_ISO14229
    override val name = "UDS ISO 14229 / OTA Transport"
    override val description = "Protocolo de Diagnóstico Unificado UDS e camada OTA BYDAutoOtaDevice"

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        var otaDeviceFound = false
        try {
            Class.forName("android.hardware.bydauto.ota.BYDAutoOtaDevice")
            otaDeviceFound = true
        } catch (_: ClassNotFoundException) {}

        val elapsed = SystemClock.elapsedRealtime() - startTime
        return if (otaDeviceFound) {
            TransportProbeResult(type, TransportState.AVAILABLE, "BYDAutoOtaDevice detectado com suporte a rotinas UDS", pingTimeMs = elapsed)
        } else {
            TransportProbeResult(type, TransportState.UNAVAILABLE, "BYDAutoOtaDevice não detectado", pingTimeMs = elapsed)
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        return TransportReadResult(false, capability, null, "Sessão UDS segura em espera", TransportState.UNAVAILABLE)
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        return TransportCommandResult(false, capability, TransportState.EXECUTION_FAILED, "UDS Write/Coding desativado por segurança", "value=$value", null, null, false, 0)
    }
}

/**
 * 10. CloudManager / MCU Transport
 */
class CloudManagerTransport(private val context: Context) : IBYDTransport {
    override val type = TransportType.CLOUD_MANAGER
    override val name = "CloudManager / MCU Transport"
    override val description = "Serviço local cloudmanager / cloudctrlserv de comunicação com o MCU"

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        var serviceFound = false
        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = smClass.getMethod("getService", String::class.java)
            val s = getServiceMethod.invoke(null, "cloudmanager") as? IBinder
            if (s != null) serviceFound = true
        } catch (_: Exception) {}

        val elapsed = SystemClock.elapsedRealtime() - startTime
        return if (serviceFound) {
            TransportProbeResult(type, TransportState.AVAILABLE, "Serviço 'cloudmanager' ativo no ServiceManager", pingTimeMs = elapsed)
        } else {
            TransportProbeResult(type, TransportState.UNAVAILABLE, "Serviço 'cloudmanager' inacessível ou restrito por permissão", pingTimeMs = elapsed)
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        return TransportReadResult(false, capability, null, "Leitura via CloudManager requer sessão autorizada", TransportState.UNAVAILABLE)
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        return TransportCommandResult(false, capability, TransportState.EXECUTION_FAILED, "Comandos CloudManager requerem assinatura de sistema", "value=$value", null, null, false, 0)
    }
}

/**
 * 11. Native Libraries / JNI Transport (auto.default.so)
 */
class NativeLibraryTransport(private val context: Context) : IBYDTransport {
    override val type = TransportType.NATIVE_JNI
    override val name = "Native Library / JNI Transport"
    override val description = "Mapeamento das bibliotecas C/C++ nativas do DiLink (auto.default.so, libbyd_*.so)"

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        val candidatePaths = listOf(
            "/vendor/lib64/hw/auto.default.so",
            "/vendor/lib/hw/auto.default.so",
            "/system/lib64/libbyd_auto.so",
            "/system/lib/libbyd_auto.so"
        )
        val detected = candidatePaths.filter { File(it).exists() }
        val elapsed = SystemClock.elapsedRealtime() - startTime

        return if (detected.isNotEmpty()) {
            TransportProbeResult(type, TransportState.AVAILABLE, "Bibliotecas nativas DiLink encontradas: " + detected.joinToString(), pingTimeMs = elapsed)
        } else {
            TransportProbeResult(type, TransportState.UNAVAILABLE, "Bibliotecas nativas restritas pelo SELinux ou ausentes no path padrão", pingTimeMs = elapsed)
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        return TransportReadResult(false, capability, null, "Invocação JNI sob demanda", TransportState.UNAVAILABLE)
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        return TransportCommandResult(false, capability, TransportState.EXECUTION_FAILED, "Execução JNI nativa desabilitada", "value=$value", null, null, false, 0)
    }
}

/**
 * 12. Local Socket Transport
 */
class SocketTransport(private val context: Context) : IBYDTransport {
    override val type = TransportType.LOCAL_SOCKET
    override val name = "Local Socket Transport"
    override val description = "Comunicação via Unix Domain Sockets em /dev/socket/byd_*"

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        val socketDir = File("/dev/socket")
        val bydSockets = if (socketDir.exists() && socketDir.isDirectory) {
            socketDir.listFiles()?.filter { it.name.contains("byd", ignoreCase = true) || it.name.contains("car", ignoreCase = true) }?.map { it.name } ?: emptyList()
        } else emptyList()

        val elapsed = SystemClock.elapsedRealtime() - startTime
        return if (bydSockets.isNotEmpty()) {
            TransportProbeResult(type, TransportState.AVAILABLE, "Sockets locais detectados: " + bydSockets.joinToString(), pingTimeMs = elapsed)
        } else {
            TransportProbeResult(type, TransportState.UNAVAILABLE, "Nenhum socket automotivo em /dev/socket acessível ao UID do app", pingTimeMs = elapsed)
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        return TransportReadResult(false, capability, null, "Sockets requerem daemon listener", TransportState.UNAVAILABLE)
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        return TransportCommandResult(false, capability, TransportState.EXECUTION_FAILED, "Socket IPC não conectado", "value=$value", null, null, false, 0)
    }
}

/**
 * 13. Direct Device Transport (/dev/*)
 */
class DirectDeviceTransport(private val context: Context) : IBYDTransport {
    override val type = TransportType.DIRECT_DEVICE
    override val name = "Direct Device Transport"
    override val description = "Nós de dispositivo físico do kernel (/dev/spidev*, /dev/can*, /dev/mcu*)"

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        val candidateDevices = listOf("/dev/spidev_ivi", "/dev/spidev0.0", "/dev/can0", "/dev/mcu_uart", "/dev/ttyHS0")
        val found = candidateDevices.filter { File(it).exists() }

        val elapsed = SystemClock.elapsedRealtime() - startTime
        return if (found.isNotEmpty()) {
            TransportProbeResult(type, TransportState.AVAILABLE, "Device nodes encontrados: " + found.joinToString(), pingTimeMs = elapsed)
        } else {
            TransportProbeResult(type, TransportState.UNAVAILABLE, "Device nodes exigem privilégio de sistema ou root (SELinux enforcement)", pingTimeMs = elapsed)
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        return TransportReadResult(false, capability, null, "Acesso direto bloqueado pelo SELinux", TransportState.SECURITY_EXCEPTION)
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        return TransportCommandResult(false, capability, TransportState.SECURITY_EXCEPTION, "REQUIRES PRIVILEGED ACCESS", "value=$value", null, null, false, 0)
    }
}

/**
 * 14. Content Provider Transport
 */
class ContentProviderTransport(private val context: Context) : IBYDTransport {
    override val type = TransportType.CONTENT_PROVIDER
    override val name = "Content Provider Transport"
    override val description = "Consultas e leitura de dados veiculares via URIs content://com.byd.*"

    override fun probe(): TransportProbeResult {
        val startTime = SystemClock.elapsedRealtime()
        val candidateUris = listOf(
            "content://com.byd.carsettings.provider/settings",
            "content://com.byd.auto/status",
            "content://com.byd.service.provider/car_state"
        )
        val accessibleUris = mutableListOf<String>()

        for (uriStr in candidateUris) {
            try {
                val cursor = context.contentResolver.query(Uri.parse(uriStr), null, null, null, null)
                if (cursor != null) {
                    accessibleUris.add(uriStr)
                    cursor.close()
                }
            } catch (_: Exception) {}
        }

        val elapsed = SystemClock.elapsedRealtime() - startTime
        return if (accessibleUris.isNotEmpty()) {
            TransportProbeResult(type, TransportState.AVAILABLE, "Providers acessíveis: " + accessibleUris.joinToString(), pingTimeMs = elapsed)
        } else {
            TransportProbeResult(type, TransportState.UNAVAILABLE, "Providers veiculares exigem permissões de assinatura BYD", pingTimeMs = elapsed)
        }
    }

    override fun read(capability: VehicleCapability): TransportReadResult {
        return TransportReadResult(false, capability, null, "Nenhum provider aberto para leitura", TransportState.UNAVAILABLE)
    }

    override fun execute(capability: VehicleCapability, value: Any): TransportCommandResult {
        return TransportCommandResult(false, capability, TransportState.EXECUTION_FAILED, "ContentProvider veicular é somente leitura", "value=$value", null, null, false, 0)
    }
}
