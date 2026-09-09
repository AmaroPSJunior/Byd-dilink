package com.byd.carcontrol.discovery

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.byd.carcontrol.data.ContentProviderEntity
import com.byd.carcontrol.repository.DiscoveryRepository
import org.json.JSONObject

class MagicCoreDiscovery(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    private companion object {
        private const val MAGIC_MANAGER_PKG = "cc.omycar.magicmanager"
        private const val MAGIC_SENTRY_PKG = "cc.omycar.magicsentry"
        private const val BYD_BT_PROVIDER_PKG = "com.byd.bluetoothprovider"

        private val CANDIDATE_AUTHORITIES = listOf(
            "cc.omycar.magicmanager.Provider",
            "cc.omycar.magicsentry.Provider",
            "cc.omycar.magiccore.api.MagicCoreProvider",
            "com.byd.bluetoothprovider"
        )
    }

    fun runDiscovery(): List<ContentProviderEntity> {
        val results = mutableListOf<ContentProviderEntity>()
        val pm = context.packageManager

        // 1. Inspect Packages
        for (pkg in listOf(MAGIC_MANAGER_PKG, MAGIC_SENTRY_PKG, BYD_BT_PROVIDER_PKG)) {
            try {
                val pkgInfo = pm.getPackageInfo(pkg, PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS)
                val isInstalled = true
                val providers = pkgInfo.providers ?: emptyArray()

                DiscoveryLogger.log(
                    category = "MAGICCORE",
                    operation = "PKG_INSPECT",
                    target = pkg,
                    result = "INSTALLED (VerName=${pkgInfo.versionName}, ProvidersCount=${providers.size})"
                )

                for (prov in providers) {
                    val entity = ContentProviderEntity(
                        authority = prov.authority ?: prov.name,
                        packageName = prov.packageName,
                        name = prov.name,
                        exported = prov.exported,
                        readPermission = prov.readPermission,
                        writePermission = prov.writePermission,
                        classification = if (pkg == BYD_BT_PROVIDER_PKG) "BYD_BLUETOOTH_PROVIDER" else "MAGICCORE",
                        status = DiscoveryStatus.DISCOVERED,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.saveProvider(entity)
                    results.add(entity)
                }
            } catch (e: Exception) {
                DiscoveryLogger.log(
                    category = "MAGICCORE",
                    operation = "PKG_INSPECT_EXCEPTION",
                    target = pkg,
                    result = "NOT_INSTALLED_OR_ACCESS_DENIED: ${e.message}",
                    exception = e
                )
            }
        }

        // 2. Safe ContentProvider Testing (getType, query, call)
        for (auth in CANDIDATE_AUTHORITIES) {
            val uri = Uri.parse("content://$auth")
            var status = DiscoveryStatus.DISCOVERED
            var typeResult: String? = null
            var queryResult = "NOT_TESTED"
            var exceptionRecorded: Throwable? = null

            // Probe getType
            try {
                typeResult = context.contentResolver.getType(uri)
                if (typeResult != null) {
                    status = DiscoveryStatus.VALIDATED
                }
                DiscoveryLogger.log("MAGICCORE", "GET_TYPE", auth, "Type: $typeResult")
            } catch (e: Exception) {
                exceptionRecorded = e
                DiscoveryLogger.log("MAGICCORE", "GET_TYPE_EXCEPTION", auth, "Error: ${e.message}", e)
            }

            // Probe Safe Query
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                if (cursor != null) {
                    val count = cursor.count
                    val cols = cursor.columnNames.joinToString(",")
                    cursor.close()
                    queryResult = "SUCCESS (Rows=$count, Cols=$cols)"
                    status = DiscoveryStatus.VALIDATED
                    DiscoveryLogger.log("MAGICCORE", "SAFE_QUERY", auth, queryResult)
                } else {
                    queryResult = "NULL_CURSOR"
                }
            } catch (e: Exception) {
                exceptionRecorded = e
                queryResult = "QUERY_EXCEPTION: ${e.message}"
                DiscoveryLogger.log("MAGICCORE", "SAFE_QUERY_EXCEPTION", auth, queryResult, e)
            }

            // Probe Safe Call (Get Version / Ping)
            try {
                val bundle = context.contentResolver.call(uri, "ping", null, null)
                if (bundle != null) {
                    status = DiscoveryStatus.VALIDATED
                    DiscoveryLogger.log("MAGICCORE", "SAFE_CALL", auth, "Call bundle returned: $bundle")
                }
            } catch (e: Exception) {
                DiscoveryLogger.log("MAGICCORE", "SAFE_CALL_EXCEPTION", auth, "Call not supported or denied", e)
            }

            repository.saveDiscovery("MAGICCORE", auth, status, JSONObject().apply {
                put("authority", auth)
                put("getTypeResult", typeResult ?: "null")
                put("queryResult", queryResult)
                put("exception", exceptionRecorded?.message ?: "none")
            }.toString())
        }

        return results
    }
}
