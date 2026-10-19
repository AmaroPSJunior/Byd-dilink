package com.byd.carcontrol

/** Pure power-command contract. Acceptance alone never proves the requested state. */
object HvacPowerPolicy {
    const val MANUAL_MESSAGE = "Controle automático HVAC indisponível nesta instalação. Com o veículo parado, use o botão de energia no painel original da central; o comando não foi confirmado."

    data class Result(
        val accepted: Boolean,
        val confirmed: Boolean,
        val method: String,
        val message: String,
        val manualInteractionRequired: Boolean = false
    ) {
        val httpStatus: Int get() = if (confirmed) 200 else if (accepted) 202 else 409
    }

    fun execute(
        accessibilityReady: Boolean,
        directPermissionGranted: Boolean,
        direct: () -> String,
        accessible: () -> Result
    ): Result {
        if (accessibilityReady) return accessible()
        if (!directPermissionGranted) return manual()
        return try {
            Result(true, false, "direct_oem", direct() + " Estado solicitado ainda não confirmado.")
        } catch (_: Exception) {
            // No retry or alternate write after a possibly accepted OEM call.
            manual()
        }
    }

    fun manual() = Result(false, false, "manual", MANUAL_MESSAGE, true)
}
