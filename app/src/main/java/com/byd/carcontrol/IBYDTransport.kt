package com.byd.carcontrol

/**
 * Interface base para todos os 13 transportes de comunicação automotiva DiLink.
 * Garante que cada transporte implemente inspeção, leitura antes da escrita e nunca
 * declare sucesso falso.
 */
interface IBYDTransport {
    val type: TransportType
    val name: String
    val description: String

    /**
     * Varredura passiva de detecção: verifica se as classes, serviços Binder,
     * devices nodes ou receivers existem no firmware do veículo.
     */
    fun probe(): TransportProbeResult

    /**
     * Leitura do estado de uma capacidade veicular sem alterar nenhum hardware.
     */
    fun read(capability: VehicleCapability): TransportReadResult

    /**
     * Execução de comando com medição de latência.
     */
    fun execute(capability: VehicleCapability, value: Any): TransportCommandResult
}
