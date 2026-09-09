package com.byd.carcontrol.discovery

import android.content.Context
import android.content.pm.PackageManager
import com.byd.carcontrol.data.ContentProviderEntity
import com.byd.carcontrol.repository.DiscoveryRepository

class ProviderDiscovery(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    fun runDiscovery(): List<ContentProviderEntity> {
        val results = mutableListOf<ContentProviderEntity>()
        val pm = context.packageManager

        try {
            val providers = pm.queryContentProviders(null, 0, 0)
            DiscoveryLogger.log("PROVIDER", "QUERY_ALL", "System", "Found ${providers.size} content providers total")

            for (prov in providers) {
                val auth = prov.authority ?: continue
                val pkg = prov.packageName ?: "unknown"

                val classification = when {
                    auth.contains("magic", ignoreCase = true) -> "MAGICCORE"
                    auth.contains("byd", ignoreCase = true) || pkg.contains("byd", ignoreCase = true) -> "BYD"
                    pkg.startsWith("android") || pkg.startsWith("com.android") -> "SYSTEM"
                    else -> "THIRD_PARTY"
                }

                val entity = ContentProviderEntity(
                    authority = auth,
                    packageName = pkg,
                    name = prov.name,
                    exported = prov.exported,
                    readPermission = prov.readPermission,
                    writePermission = prov.writePermission,
                    classification = classification,
                    status = DiscoveryStatus.DISCOVERED,
                    timestamp = System.currentTimeMillis()
                )

                repository.saveProvider(entity)
                results.add(entity)
            }
        } catch (e: Exception) {
            DiscoveryLogger.log("PROVIDER", "QUERY_ALL_EXCEPTION", "System", "Error enumerating providers: ${e.message}", e)
        }

        return results
    }
}
