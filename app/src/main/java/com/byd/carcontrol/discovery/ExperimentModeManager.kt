package com.byd.carcontrol.discovery

import android.content.Context
import com.byd.carcontrol.repository.DiscoveryRepository
import org.json.JSONObject

data class DrivingStateCorrelation(
    val rawValue: Int,
    val userGearLabel: String, // P, R, N, D, Unknown
    val timestamp: Long = System.currentTimeMillis()
)

class ExperimentModeManager(
    private val context: Context,
    private val repository: DiscoveryRepository
) {
    private val correlations = mutableListOf<DrivingStateCorrelation>()

    fun recordDrivingStateCorrelation(rawValue: Int, gearLabel: String) {
        val entry = DrivingStateCorrelation(rawValue, gearLabel)
        correlations.add(entry)

        DiscoveryLogger.log(
            category = "EXPERIMENT",
            operation = "DRIVING_STATE_MAP",
            target = "BydDrivingState",
            result = "Raw $rawValue -> Gear '$gearLabel'"
        )

        repository.saveDiscovery(
            category = "EXPERIMENT",
            name = "DrivingStateCorrelation_Raw_$rawValue",
            status = DiscoveryStatus.VALIDATED,
            evidenceJson = JSONObject().apply {
                put("rawValue", rawValue)
                put("gearLabel", gearLabel)
                put("timestamp", entry.timestamp)
            }.toString()
        )
    }

    fun getCorrelations(): List<DrivingStateCorrelation> = correlations.toList()
}
