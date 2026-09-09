package com.byd.carcontrol.firebase

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.byd.carcontrol.data.DiscoveryEntity
import com.byd.carcontrol.discovery.DiscoveryLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Firebase Firestore REST Client for Anonymous Discovery Reporting.
 * Strictly complies with privacy guidelines: no VIN, no location, no audio, no credentials.
 */
class FirebaseDiscoveryRepository(private val context: Context) {

    private companion object {
        private const val TAG = "FirebaseDiscoveryRepo"
        private const val PREFS_NAME = "byd_firebase_prefs"
        private const val KEY_VEHICLE_ID = "anonymous_vehicle_id"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val anonymousVehicleId: String
        get() {
            var id = prefs.getString(KEY_VEHICLE_ID, null)
            if (id.isNullOrEmpty()) {
                id = "byd_dolphin_" + UUID.randomUUID().toString().take(12)
                prefs.edit().putString(KEY_VEHICLE_ID, id).apply()
            }
            return id
        }

    suspend fun syncDiscoveryToFirebase(discovery: DiscoveryEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://firestore.googleapis.com/v1/projects/byd-car-control/databases/(default)/documents/vehicles/$anonymousVehicleId/discoveries/${discovery.id}"
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val jsonBody = JSONObject().apply {
                val fields = JSONObject().apply {
                    put("category", JSONObject().put("stringValue", discovery.category))
                    put("name", JSONObject().put("stringValue", discovery.name))
                    put("status", JSONObject().put("stringValue", discovery.status.name))
                    put("evidenceJson", JSONObject().put("stringValue", discovery.evidenceJson))
                    put("timestamp", JSONObject().put("integerValue", discovery.timestamp))
                    put("vehicleId", JSONObject().put("stringValue", anonymousVehicleId))
                }
                put("fields", fields)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                DiscoveryLogger.log("FIREBASE", "SYNC_SUCCESS", discovery.name, "HTTP $responseCode")
                true
            } else {
                DiscoveryLogger.log("FIREBASE", "SYNC_FAILED", discovery.name, "HTTP $responseCode")
                false
            }
        } catch (e: Exception) {
            DiscoveryLogger.log("FIREBASE", "SYNC_EXCEPTION", discovery.name, "Error: ${e.message}", e)
            false
        }
    }
}
