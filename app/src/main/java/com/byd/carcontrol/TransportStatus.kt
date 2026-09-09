package com.byd.carcontrol

enum class TransportState(val label: String, val badgeColor: String) {
    AVAILABLE("DISPONÍVEL", "#10b981"),
    UNAVAILABLE("INDISPONÍVEL", "#64748b"),
    PERMISSION_DENIED("PERMISSÃO NEGADA", "#f59e0b"),
    SECURITY_EXCEPTION("SEGURANÇA BLOQUEADA", "#ef4444"),
    SERVICE_NOT_FOUND("SERVIÇO NÃO ENCONTRADO", "#94a3b8"),
    METHOD_NOT_FOUND("MÉTODO NÃO ENCONTRADO", "#f97316"),
    EXECUTION_FAILED("FALHA NA EXECUÇÃO", "#dc2626"),
    EXECUTED_NO_CONFIRMATION("ENVIADO SEM CONFIRMAÇÃO", "#eab308"),
    CONFIRMED("CONFIRMADO NO HARDWARE", "#22c55e")
}

enum class TransportType(val displayName: String, val category: String) {
    BYD_AUTO_MANAGER("BYDAutoManager", "Framework Oficial"),
    HAL_REFLECTION("HAL Reflection", "Nativo / Hardware"),
    BINDER_SERVICE("Binder / ServiceManager", "IPC Android"),
    INTENT_BROADCAST("Intent Broadcast Explícito", "Mensageria"),
    SETTINGS_SYSTEM("Settings.System", "Configurações"),
    CONTENT_PROVIDER("Content Provider", "Dados Veiculares"),
    CLUSTER_SPI("Cluster SPI (com.byd.cluster.spi)", "Barramento Painel"),
    CAN_BUS("CAN Bus Direct", "Rede Veicular"),
    UDS_ISO14229("UDS ISO 14229 / OTA", "Diagnóstico ECU"),
    CLOUD_MANAGER("CloudManager / MCU", "Telemetria"),
    NATIVE_JNI("JNI / Native Libraries", "Binários .so"),
    LOCAL_SOCKET("Unix Local Socket", "IPC Baixo Nível"),
    DIRECT_DEVICE("Device Node (/dev/*)", "Drivers Kernel")
}

enum class VehicleCapability(val keyName: String, val description: String) {
    LIGHTS("LIGHTS", "Controle Geral de Iluminação"),
    INTERIOR_LIGHTS("INTERIOR_LIGHTS", "Luzes Internas da Cabine"),
    AMBIENT_LIGHTS("AMBIENT_LIGHTS", "Iluminação Ambiente LED"),
    READING_LIGHTS("READING_LIGHTS", "Luzes de Leitura Plafonier"),
    DOME_LIGHT("DOME_LIGHT", "Luz Central de Teto"),
    DOORS("DOORS", "Estado de Abertura das Portas"),
    LOCKS("LOCKS", "Travas Elétricas"),
    WINDOWS("WINDOWS", "Vidros Elétricos e Teto Solar"),
    HVAC("HVAC", "Climatização e Ar-Condicionado"),
    BATTERY("BATTERY", "Bateria Blade HV e Carregamento"),
    SOC("SOC", "Nível de Carga Bateria (%)"),
    TYRE_PRESSURE("TYRE_PRESSURE", "Pressão TPMS dos Pneus"),
    VEHICLE_SPEED("VEHICLE_SPEED", "Velocidade Instantânea"),
    POWER_STATE("POWER_STATE", "Estado de Ignição / OK")
}

enum class TestMode(val label: String) {
    SAFE_READ_ONLY("1. SAFE READ ONLY (Seguro - Somente Leitura)"),
    DISCOVERY("2. DISCOVERY (Varredura de Serviços)"),
    CONTROL_TEST("3. CONTROL TEST (Teste Controlado com Confirmação)"),
    ADVANCED("4. ADVANCED (Sessões Avançadas)"),
    RAW_EXPERIMENTAL("5. RAW / EXPERIMENTAL (Baixo Nível)")
}

data class TransportProbeResult(
    val transportType: TransportType,
    val state: TransportState,
    val details: String,
    val classNameOrEndpoint: String? = null,
    val methodsDetected: List<String> = emptyList(),
    val permissionsRequired: List<String> = emptyList(),
    val pingTimeMs: Long = 0
)

data class TransportReadResult(
    val success: Boolean,
    val capability: VehicleCapability,
    val rawValue: Any?,
    val formattedValue: String,
    val state: TransportState,
    val exceptionMessage: String? = null
)

data class TransportCommandResult(
    val success: Boolean,
    val capability: VehicleCapability,
    val state: TransportState,
    val message: String,
    val commandPayload: String,
    val readBefore: String?,
    val readAfter: String?,
    val confirmedByHardware: Boolean,
    val latencyMs: Long,
    val exceptionMessage: String? = null
)

data class CommunicationLogEntry(
    val id: String,
    val timestamp: String,
    val transport: TransportType,
    val action: String,
    val target: String,
    val requestPayload: String,
    val responsePayload: String,
    val stateBefore: String?,
    val stateAfter: String?,
    val durationMs: Long,
    val finalState: TransportState,
    val exceptionDetails: String? = null
)
