package com.byd.carcontrol.discovery

import android.content.Context
import android.content.pm.PackageManager
import com.byd.carcontrol.data.PermissionEntity
import com.byd.carcontrol.repository.DiscoveryRepository

class PermissionDiscovery(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    private companion object {
        private val CANDIDATE_PERMISSIONS = listOf(
            "com.byd.permission.CAR_LIGHT_CONTROL",
            "com.byd.permission.CAR_DOOR_CONTROL",
            "com.byd.permission.CAR_WINDOW_CONTROL",
            "com.byd.permission.BYD_AUTO_CONTROL",
            "com.byd.permission.HARDWARE_CONTROL",
            "com.byd.permission.CAR_STATE_READ",
            "com.byd.permission.LIGHT_CONTROL",
            "com.byd.permission.WINDOW_CONTROL",
            "com.byd.permission.DOOR_CONTROL",
            "android.car.permission.CONTROL_CAR_INTERIOR_LIGHTS",
            "android.car.permission.CAR_EXTERIOR_LIGHTS",
            "android.car.permission.CAR_CONTROL",
            "cc.omycar.magiccore.permission.API",
            "cc.omycar.magiccore.permission.MANAGER",
            "cc.omycar.magiccore.permission.WHITE_LIST",
            "android.permission.WRITE_SETTINGS",
            "android.permission.WRITE_SECURE_SETTINGS"
        )
    }

    fun runDiscovery(): List<PermissionEntity> {
        val results = mutableListOf<PermissionEntity>()
        val pm = context.packageManager

        for (permName in CANDIDATE_PERMISSIONS) {
            var exists = false
            var protectionLevel = "UNKNOWN"
            var isGranted = false
            var status = DiscoveryStatus.NOT_AVAILABLE

            try {
                val permInfo = pm.getPermissionInfo(permName, 0)
                exists = true
                protectionLevel = "0x" + Integer.toHexString(permInfo.protectionLevel)
            } catch (e: Exception) {
                DiscoveryLogger.log("PERMISSION", "INFO_NOT_FOUND", permName, "Not registered in System PM", e)
            }

            try {
                val check = context.checkSelfPermission(permName)
                isGranted = (check == PackageManager.PERMISSION_GRANTED)
                status = if (isGranted) DiscoveryStatus.VALIDATED else (if (exists) DiscoveryStatus.DENIED else DiscoveryStatus.NOT_AVAILABLE)
            } catch (e: Exception) {
                DiscoveryLogger.log("PERMISSION", "CHECK_EXCEPTION", permName, "Error: ${e.message}", e)
            }

            val entity = PermissionEntity(
                permissionName = permName,
                exists = exists,
                protectionLevel = protectionLevel,
                isGranted = isGranted,
                status = status,
                timestamp = System.currentTimeMillis()
            )

            repository.savePermission(entity)
            results.add(entity)

            DiscoveryLogger.log(
                category = "PERMISSION",
                operation = "PROBE",
                target = permName,
                result = "Exists=$exists, Granted=$isGranted, Protection=$protectionLevel"
            )
        }

        return results
    }
}
