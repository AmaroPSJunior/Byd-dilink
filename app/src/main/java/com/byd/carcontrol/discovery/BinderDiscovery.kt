package com.byd.carcontrol.discovery

import android.os.IBinder
import android.os.SystemClock
import com.byd.carcontrol.data.BinderServiceEntity
import com.byd.carcontrol.repository.DiscoveryRepository
import org.json.JSONObject

class BinderDiscovery(private val repository: DiscoveryRepository) {

    private companion object {
        private val KNOWN_BINDER_SERVICES = listOf(
            "byd_car_service",
            "bydauto_light",
            "byd_light",
            "bydauto_door",
            "bydauto_window",
            "bydauto_ac",
            "bydauto_car",
            "bydauto_power",
            "bydauto_screen",
            "dicarserver",
            "cloudmanager",
            "cloudctrlserv",
            "car_service"
        )
    }

    fun runDiscovery(): List<BinderServiceEntity> {
        val results = mutableListOf<BinderServiceEntity>()
        val startTime = SystemClock.elapsedRealtime()

        val smClass = try {
            Class.forName("android.os.ServiceManager")
        } catch (e: Exception) {
            DiscoveryLogger.log("BINDER", "CLASS_NOT_FOUND", "android.os.ServiceManager", "Failed to load ServiceManager", e)
            null
        }

        val getServiceMethod = smClass?.let {
            try {
                it.getMethod("getService", String::class.java)
            } catch (e: Exception) {
                DiscoveryLogger.log("BINDER", "METHOD_NOT_FOUND", "ServiceManager.getService", "Error", e)
                null
            }
        }

        for (serviceName in KNOWN_BINDER_SERVICES) {
            var exists = false
            var descriptor: String? = null
            var isAlive = false
            var status = DiscoveryStatus.NOT_AVAILABLE
            var exceptionRecorded: Throwable? = null

            try {
                if (getServiceMethod != null) {
                    val binderObj = getServiceMethod.invoke(null, serviceName) as? IBinder
                    if (binderObj != null) {
                        exists = true
                        isAlive = binderObj.isBinderAlive
                        descriptor = try {
                            binderObj.interfaceDescriptor
                        } catch (e: Exception) {
                            exceptionRecorded = e
                            "DESCRIPTOR_ERROR: ${e.message}"
                        }

                        status = if (descriptor != null && !descriptor.startsWith("DESCRIPTOR_ERROR")) {
                            DiscoveryStatus.VALIDATED
                        } else {
                            DiscoveryStatus.DISCOVERED
                        }

                        DiscoveryLogger.log(
                            category = "BINDER",
                            operation = "PROBE",
                            target = serviceName,
                            result = "FOUND (Alive=$isAlive, Descriptor=$descriptor)",
                            exception = exceptionRecorded
                        )
                    } else {
                        DiscoveryLogger.log("BINDER", "PROBE", serviceName, "NOT_FOUND")
                    }
                }
            } catch (e: Exception) {
                status = DiscoveryStatus.FAILED
                exceptionRecorded = e
                DiscoveryLogger.log("BINDER", "PROBE_EXCEPTION", serviceName, "Error: ${e.message}", e)
            }

            val entity = BinderServiceEntity(
                name = serviceName,
                exists = exists,
                descriptor = descriptor,
                isAlive = isAlive,
                status = status,
                timestamp = System.currentTimeMillis()
            )
            repository.saveBinder(entity)
            results.add(entity)
        }

        // Attempt generic listServices if available
        try {
            val listServicesMethod = smClass?.getMethod("listServices")
            val servicesArray = listServicesMethod?.invoke(null) as? Array<*>
            if (servicesArray != null) {
                val serviceListStr = servicesArray.filterNotNull().map { it.toString() }
                DiscoveryLogger.log("BINDER", "GENERIC_LIST", "ServiceManager.listServices", "Found ${serviceListStr.size} services total")
                repository.saveDiscovery("BINDER", "ALL_SERVICES_LIST", DiscoveryStatus.DISCOVERED, JSONObject().apply {
                    put("totalCount", serviceListStr.size)
                    put("services", serviceListStr.take(100).joinToString(","))
                }.toString())
            }
        } catch (e: Exception) {
            DiscoveryLogger.log("BINDER", "GENERIC_LIST_DENIED", "listServices", "Access denied or restricted", e)
        }

        return results
    }
}
