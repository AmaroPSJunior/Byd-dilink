package com.byd.carcontrol.repository

import android.content.Context
import android.os.Build
import com.byd.carcontrol.data.ApiClassEntity
import com.byd.carcontrol.data.BinderServiceEntity
import com.byd.carcontrol.data.ContentProviderEntity
import com.byd.carcontrol.data.DiscoveryDbHelper
import com.byd.carcontrol.data.DiscoveryEntity
import com.byd.carcontrol.data.PermissionEntity
import com.byd.carcontrol.data.TestResultEntity
import com.byd.carcontrol.data.VehicleProfileEntity
import com.byd.carcontrol.discovery.DiscoveryLogger
import com.byd.carcontrol.discovery.DiscoveryStatus
import com.byd.carcontrol.firebase.FirebaseDiscoveryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class DiscoveryRepository(private val context: Context) {

    private val dbHelper = DiscoveryDbHelper(context)
    private val firebaseRepo = FirebaseDiscoveryRepository(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun getVehicleProfile(): VehicleProfileEntity {
        return VehicleProfileEntity(
            id = firebaseRepo.anonymousVehicleId,
            deviceModel = Build.MODEL ?: "Unknown",
            manufacturer = Build.MANUFACTURER ?: "BYD",
            brand = Build.BRAND ?: "BYD",
            product = Build.PRODUCT ?: "Unknown",
            buildDisplay = Build.DISPLAY ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            incremental = Build.VERSION.INCREMENTAL ?: "Unknown",
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Build.VERSION.SECURITY_PATCH else "N/A",
            dilinkVersion = System.getProperty("ro.build.display.id") ?: "DiLink 3.0",
            timestamp = System.currentTimeMillis()
        )
    }

    fun saveDiscovery(category: String, name: String, status: DiscoveryStatus, evidenceJson: String) {
        val entity = DiscoveryEntity(
            id = UUID.randomUUID().toString().take(16),
            category = category,
            name = name,
            status = status,
            evidenceJson = evidenceJson,
            timestamp = System.currentTimeMillis()
        )
        dbHelper.saveDiscovery(entity)
        DiscoveryLogger.log("REPOSITORY", "SAVE_DISCOVERY", name, status.name)

        // Sync async to Firebase
        scope.launch {
            firebaseRepo.syncDiscoveryToFirebase(entity)
        }
    }

    fun saveBinder(entity: BinderServiceEntity) {
        dbHelper.saveBinder(entity)
        saveDiscovery("BINDER", entity.name, entity.status, JSONObject().apply {
            put("descriptor", entity.descriptor ?: "UNKNOWN")
            put("isAlive", entity.isAlive)
        }.toString())
    }

    fun saveProvider(entity: ContentProviderEntity) {
        dbHelper.saveProvider(entity)
        saveDiscovery("PROVIDER", entity.authority, entity.status, JSONObject().apply {
            put("packageName", entity.packageName)
            put("exported", entity.exported)
            put("classification", entity.classification)
            put("readPermission", entity.readPermission ?: "NONE")
            put("writePermission", entity.writePermission ?: "NONE")
        }.toString())
    }

    fun savePermission(entity: PermissionEntity) {
        dbHelper.savePermission(entity)
        saveDiscovery("PERMISSION", entity.permissionName, entity.status, JSONObject().apply {
            put("exists", entity.exists)
            put("protectionLevel", entity.protectionLevel)
            put("isGranted", entity.isGranted)
        }.toString())
    }

    fun saveClass(entity: ApiClassEntity) {
        dbHelper.saveClass(entity)
        saveDiscovery("CLASS", entity.className, entity.status, JSONObject().apply {
            put("packageName", entity.packageName)
            put("superclass", entity.superclass ?: "Object")
            put("interfaces", entity.interfaces.joinToString(","))
        }.toString())
    }

    fun saveTestResult(entity: TestResultEntity) {
        dbHelper.saveTestResult(entity)
        saveDiscovery("TEST", "${entity.className}#${entity.methodName}", if (entity.execution == "SUCCESS") DiscoveryStatus.VALIDATED else DiscoveryStatus.FAILED, JSONObject().apply {
            put("execution", entity.execution)
            put("parameters", entity.parameters)
            put("returnType", entity.returnType)
            put("result", entity.result ?: "null")
            put("exceptionType", entity.exceptionType ?: "")
            put("exceptionMessage", entity.exceptionMessage ?: "")
            put("durationMs", entity.durationMs)
        }.toString())
    }

    fun getLocalDiscoveries(): List<DiscoveryEntity> {
        return dbHelper.getAllDiscoveries()
    }

    fun exportFullReportJson(): String {
        val root = JSONObject()
        val profile = getVehicleProfile()

        root.put("reportVersion", "1.0")
        root.put("exportTimestamp", System.currentTimeMillis())

        val vehicleObj = JSONObject().apply {
            put("vehicleId", profile.id)
            put("model", profile.deviceModel)
            put("manufacturer", profile.manufacturer)
            put("brand", profile.brand)
            put("sdkInt", profile.sdkInt)
            put("buildDisplay", profile.buildDisplay)
            put("dilinkVersion", profile.dilinkVersion)
        }
        root.put("deviceProfile", vehicleObj)

        val discoveriesArr = JSONArray()
        for (d in dbHelper.getAllDiscoveries()) {
            discoveriesArr.put(JSONObject().apply {
                put("id", d.id)
                put("category", d.category)
                put("name", d.name)
                put("status", d.status.name)
                put("evidenceJson", d.evidenceJson)
                put("timestamp", d.timestamp)
            })
        }
        root.put("discoveries", discoveriesArr)

        val logsArr = JSONArray()
        for (log in DiscoveryLogger.getLogs()) {
            logsArr.put(log.toFormattedString())
        }
        root.put("logs", logsArr)

        return root.toString(2)
    }

    fun importFullReportJson(jsonStr: String): Int {
        var importedCount = 0
        try {
            val root = JSONObject(jsonStr)
            if (root.has("discoveries")) {
                val arr = root.getJSONArray("discoveries")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val cat = item.optString("category", "UNKNOWN")
                    val name = item.optString("name", "UNKNOWN")
                    val stName = item.optString("status", "UNKNOWN")
                    val status = try { DiscoveryStatus.valueOf(stName) } catch (_: Exception) { DiscoveryStatus.UNKNOWN }
                    val ev = item.optString("evidenceJson", "{}")

                    saveDiscovery(cat, name, status, ev)
                    importedCount++
                }
            }
        } catch (e: Exception) {
            DiscoveryLogger.log("REPOSITORY", "IMPORT_ERROR", "JSON", "Error: ${e.message}", e)
        }
        return importedCount
    }
}
