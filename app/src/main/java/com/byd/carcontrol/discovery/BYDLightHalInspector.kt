package com.byd.carcontrol.discovery

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.byd.carcontrol.data.BinderServiceEntity
import com.byd.carcontrol.data.PermissionEntity
import com.byd.carcontrol.discovery.DiscoveryStatus
import com.byd.carcontrol.repository.DiscoveryRepository
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * BYD Light HAL Deep Inspector - STRICT SAFE READ-ONLY
 * 
 * Inspeciona exaustivamente a hierarquia da classe:
 * android.hardware.bydauto.light.BYDAutoLightDevice
 * e suas superclasses (ex: android.hardware.bydauto.AbsBYDAutoDevice).
 * 
 * Opera 100% em modo STRICT SAFE READ-ONLY:
 * - NENHUM método de escrita é executado.
 * - NENHUM comando CAN, SPI, UART, UDS ou Binder transact de escrita é disparado.
 * - Exibe TODOS os métodos e campos encontrados sem truncamento.
 */
class BYDLightHalInspector(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    companion object {
        const val TARGET_CLASS_NAME = "android.hardware.bydauto.light.BYDAutoLightDevice"

        val READ_KEYWORDS = listOf("get", "read", "query", "status", "feature", "type", "state")
        val WRITE_KEYWORDS = listOf("set", "write", "send", "command", "control", "update")
        val COURTESY_KEYWORDS = listOf("inside", "interior", "reading", "reader", "room", "dome", "ceiling", "roof", "courtesy", "lamp", "light", "door")
        val AMBIENT_KEYWORDS = listOf("ambient", "atmosphere", "color", "rgb", "brightness")
    }

    private val inspectionErrors = mutableListOf<String>()

    fun runExhaustiveInspection(): String {
        val startTime = SystemClock.elapsedRealtime()
        val sb = StringBuilder()

        sb.append("==================================================\n")
        sb.append("BYD LIGHT HAL DEEP INSPECTOR\n")
        sb.append("STRICT SAFE READ-ONLY\n")
        sb.append("==================================================\n\n")

        // 1. DEVICE INFORMATION
        sb.append("DEVICE INFORMATION\n")
        sb.append("------------------\n")
        sb.append("Model: ${Build.MODEL}\n")
        sb.append("Device: ${Build.DEVICE}\n")
        sb.append("Manufacturer: ${Build.MANUFACTURER}\n")
        sb.append("Brand: ${Build.BRAND}\n")
        sb.append("Android Release: ${Build.VERSION.RELEASE}\n")
        sb.append("SDK Int: ${Build.VERSION.SDK_INT}\n")
        sb.append("Build Display: ${Build.DISPLAY}\n")
        sb.append("Fingerprint: ${Build.FINGERPRINT}\n\n")

        // Load Primary Class & Hierarchy
        var primaryClass: Class<*>? = null
        try {
            primaryClass = Class.forName(TARGET_CLASS_NAME)
        } catch (t: Throwable) {
            inspectionErrors.add("Primary class $TARGET_CLASS_NAME not found: ${t.javaClass.simpleName} - ${t.message}")
        }

        val hierarchy = mutableListOf<Class<*>>()
        var current: Class<*>? = primaryClass
        while (current != null && current != Any::class.java) {
            hierarchy.add(current)
            try {
                current = current.superclass
            } catch (t: Throwable) {
                inspectionErrors.add("Error traversing superclass of ${current.name}: ${t.message}")
                break
            }
        }

        // 2. HAL CLASS
        sb.append("HAL CLASS\n")
        sb.append("---------\n")
        if (primaryClass != null) {
            sb.append("Target Class: ${primaryClass.name}\n")
            sb.append("Status: FOUND\n")
            sb.append("Modifiers: ${Modifier.toString(primaryClass.modifiers)}\n")
            sb.append("Package: ${primaryClass.`package`?.name ?: "N/A"}\n")
            sb.append("ClassLoader: ${primaryClass.classLoader?.javaClass?.name ?: "System"}\n\n")
        } else {
            sb.append("Target Class: $TARGET_CLASS_NAME\n")
            sb.append("Status: NOT_FOUND\n\n")
        }

        // 3. INSTANCE
        var instanceObj: Any? = null
        var instanceMethodName = "NONE"
        sb.append("INSTANCE\n")
        sb.append("--------\n")
        if (primaryClass != null) {
            try {
                val getInstanceCtx = primaryClass.getMethod("getInstance", Context::class.java)
                getInstanceCtx.isAccessible = true
                instanceObj = getInstanceCtx.invoke(null, context)
                if (instanceObj != null) instanceMethodName = "getInstance(Context)"
            } catch (_: Throwable) {}

            if (instanceObj == null) {
                try {
                    val getInstanceNoArg = primaryClass.getMethod("getInstance")
                    getInstanceNoArg.isAccessible = true
                    instanceObj = getInstanceNoArg.invoke(null)
                    if (instanceObj != null) instanceMethodName = "getInstance()"
                } catch (_: Throwable) {}
            }

            if (instanceObj != null) {
                sb.append("Status: FOUND / ACCESSIBLE\n")
                sb.append("Method: $instanceMethodName\n")
                sb.append("Runtime Class: ${instanceObj.javaClass.name}\n\n")
            } else {
                sb.append("Status: NOT_OBTAINED\n")
                sb.append("Note: Static methods and class inspection remain fully operational.\n\n")
            }
        } else {
            sb.append("Status: NOT_AVAILABLE\n\n")
        }

        // 4. INHERITANCE
        sb.append("INHERITANCE\n")
        sb.append("-----------\n")
        if (hierarchy.isNotEmpty()) {
            hierarchy.forEachIndexed { index, clazz ->
                sb.append("Level $index: ${clazz.name}\n")
                sb.append("  Modifiers: ${Modifier.toString(clazz.modifiers)}\n")
                val interfaces = clazz.interfaces.map { it.name }
                sb.append("  Interfaces (${interfaces.size}): ${interfaces.joinToString(", ").ifEmpty { "None" }}\n")

                val constructors = try { clazz.declaredConstructors } catch (t: Throwable) { emptyArray<Constructor<*>>() }
                sb.append("  Constructors (${constructors.size}):\n")
                constructors.forEach { c ->
                    val params = c.parameterTypes.map { it.simpleName }.joinToString(", ")
                    sb.append("    ${Modifier.toString(c.modifiers)} ${clazz.simpleName}($params)\n")
                }
                sb.append("\n")
            }
        } else {
            sb.append("No class hierarchy resolved.\n\n")
        }

        // Data collection across hierarchy
        data class DiscoveredMethodInfo(
            val declaringClass: String,
            val modifiers: String,
            val returnType: String,
            val name: String,
            val parameters: String,
            val exceptions: String,
            val methodRef: Method
        )

        data class DiscoveredFieldInfo(
            val declaringClass: String,
            val modifiers: String,
            val type: String,
            val name: String,
            val valueStr: String,
            val decimalStr: String?,
            val hexStr: String?,
            val rawValue: Any?,
            val isStatic: Boolean
        )

        val allMethods = mutableListOf<DiscoveredMethodInfo>()
        val allFields = mutableListOf<DiscoveredFieldInfo>()

        hierarchy.forEach { clazz ->
            // Inspect methods
            val methods = try { clazz.declaredMethods } catch (t: Throwable) {
                inspectionErrors.add("Error inspecting declaredMethods on ${clazz.name}: ${t.message}")
                emptyArray<Method>()
            }
            for (m in methods) {
                try {
                    val mods = Modifier.toString(m.modifiers)
                    val ret = m.returnType.name
                    val params = m.parameterTypes.map { it.name }.joinToString(", ")
                    val excs = m.exceptionTypes.map { it.name }.joinToString(", ").ifEmpty { "NONE" }
                    allMethods.add(
                        DiscoveredMethodInfo(
                            declaringClass = clazz.name,
                            modifiers = mods,
                            returnType = ret,
                            name = m.name,
                            parameters = params,
                            exceptions = excs,
                            methodRef = m
                        )
                    )
                } catch (t: Throwable) {
                    inspectionErrors.add("Error reading method ${m.name} on ${clazz.name}: ${t.message}")
                }
            }

            // Inspect fields
            val fields = try { clazz.declaredFields } catch (t: Throwable) {
                inspectionErrors.add("Error inspecting declaredFields on ${clazz.name}: ${t.message}")
                emptyArray<Field>()
            }
            for (f in fields) {
                try {
                    f.isAccessible = true
                    val mods = Modifier.toString(f.modifiers)
                    val typeStr = f.type.name
                    val isStatic = Modifier.isStatic(f.modifiers)

                    var valueStr = "VALUE_ACCESS_DENIED"
                    var decimalStr: String? = null
                    var hexStr: String? = null
                    var rawVal: Any? = null

                    if (isStatic) {
                        try {
                            rawVal = f.get(null)
                            val formatted = formatNumber(rawVal)
                            valueStr = formatted.first
                            decimalStr = formatted.second
                            hexStr = formatted.third
                        } catch (t: Throwable) {
                            valueStr = "VALUE_ERROR: ${t.javaClass.simpleName} - ${t.message}"
                        }
                    } else if (instanceObj != null && clazz.isInstance(instanceObj)) {
                        try {
                            rawVal = f.get(instanceObj)
                            val formatted = formatNumber(rawVal)
                            valueStr = formatted.first
                            decimalStr = formatted.second
                            hexStr = formatted.third
                        } catch (t: Throwable) {
                            valueStr = "VALUE_ERROR: ${t.javaClass.simpleName} - ${t.message}"
                        }
                    } else {
                        valueStr = "<INSTANCE_REQUIRED>"
                    }

                    allFields.add(
                        DiscoveredFieldInfo(
                            declaringClass = clazz.name,
                            modifiers = mods,
                            type = typeStr,
                            name = f.name,
                            valueStr = valueStr,
                            decimalStr = decimalStr,
                            hexStr = hexStr,
                            rawValue = rawVal,
                            isStatic = isStatic
                        )
                    )
                } catch (t: Throwable) {
                    inspectionErrors.add("Error reading field ${f.name} on ${clazz.name}: ${t.message}")
                }
            }
        }

        // 5. ===== FULL METHOD LIST =====
        sb.append("===== FULL METHOD LIST =====\n")
        sb.append("Total Methods Found Across Hierarchy: ${allMethods.size}\n\n")
        allMethods.forEachIndexed { idx, m ->
            sb.append("METHOD [${idx + 1}/${allMethods.size}]\n")
            sb.append("DECLARING CLASS: ${m.declaringClass}\n")
            sb.append("MODIFIERS: ${m.modifiers}\n")
            sb.append("RETURN TYPE: ${m.returnType}\n")
            sb.append("METHOD NAME: ${m.name}\n")
            sb.append("PARAMETER TYPES: (${m.parameters})\n")
            sb.append("EXCEPTIONS: ${m.exceptions}\n\n")
        }

        // 6. ===== POSSIBLE READ METHODS =====
        val readMethods = allMethods.filter { m ->
            READ_KEYWORDS.any { m.name.lowercase().contains(it) }
        }
        sb.append("===== POSSIBLE READ METHODS =====\n")
        sb.append("Total Read Candidates: ${readMethods.size}\n\n")
        readMethods.forEach { m ->
            sb.append("${m.declaringClass}#${m.modifiers} ${m.returnType} ${m.name}(${m.parameters})\n")
        }
        sb.append("\n")

        // 7. ===== POSSIBLE WRITE METHODS - NOT EXECUTED =====
        val writeMethods = allMethods.filter { m ->
            WRITE_KEYWORDS.any { m.name.lowercase().contains(it) }
        }
        sb.append("===== POSSIBLE WRITE METHODS - NOT EXECUTED =====\n")
        sb.append("Total Write Candidates (BLOCKED - NOT EXECUTED): ${writeMethods.size}\n\n")
        writeMethods.forEach { m ->
            sb.append("[WRITE METHOD (NOT EXECUTED)] ${m.declaringClass}#${m.modifiers} ${m.returnType} ${m.name}(${m.parameters})\n")
        }
        sb.append("\n")

        // 8. ===== POSSIBLE INTERIOR / COURTESY LIGHT METHODS =====
        val courtesyMethods = allMethods.filter { m ->
            COURTESY_KEYWORDS.any { m.name.lowercase().contains(it) }
        }
        sb.append("===== POSSIBLE INTERIOR / COURTESY LIGHT METHODS =====\n")
        sb.append("Total Courtesy Light Candidates: ${courtesyMethods.size}\n\n")
        courtesyMethods.forEach { m ->
            val isWrite = WRITE_KEYWORDS.any { m.name.lowercase().contains(it) }
            val tag = if (isWrite) " [WRITE METHOD (NOT EXECUTED)]" else " [READ CANDIDATE]"
            sb.append("${m.declaringClass}#${m.modifiers} ${m.returnType} ${m.name}(${m.parameters})$tag\n")
        }
        sb.append("\n")

        // 9. ===== FULL FIELD LIST =====
        sb.append("===== FULL FIELD LIST =====\n")
        sb.append("Total Fields Found Across Hierarchy: ${allFields.size}\n\n")
        allFields.forEachIndexed { idx, f ->
            sb.append("FIELD [${idx + 1}/${allFields.size}]\n")
            sb.append("DECLARING CLASS: ${f.declaringClass}\n")
            sb.append("MODIFIERS: ${f.modifiers}\n")
            sb.append("TYPE: ${f.type}\n")
            sb.append("NAME: ${f.name}\n")
            sb.append("VALUE: ${f.valueStr}\n\n")
        }

        // 10. ===== POSSIBLE INTERIOR / COURTESY LIGHT FIELDS =====
        val courtesyFields = allFields.filter { f ->
            COURTESY_KEYWORDS.any { f.name.lowercase().contains(it) }
        }
        sb.append("===== POSSIBLE INTERIOR / COURTESY LIGHT FIELDS =====\n")
        sb.append("Total Courtesy Light Fields Found: ${courtesyFields.size}\n\n")
        courtesyFields.forEach { f ->
            sb.append("NAME: ${f.name}\n")
            sb.append("TYPE: ${f.type}\n")
            sb.append("DECLARING CLASS: ${f.declaringClass}\n")
            if (f.decimalStr != null) sb.append("DECIMAL: ${f.decimalStr}\n")
            if (f.hexStr != null) sb.append("HEX: ${f.hexStr}\n")
            sb.append("VALUE: ${f.valueStr}\n\n")
        }

        // 11. ===== AMBIENT LIGHT - SECONDARY =====
        val ambientFields = allFields.filter { f ->
            AMBIENT_KEYWORDS.any { f.name.lowercase().contains(it) }
        }
        val ambientMethods = allMethods.filter { m ->
            AMBIENT_KEYWORDS.any { m.name.lowercase().contains(it) }
        }
        sb.append("===== AMBIENT LIGHT - SECONDARY =====\n")
        sb.append("Ambient Light Fields (${ambientFields.size}):\n")
        ambientFields.forEach { f ->
            sb.append("  [FIELD] ${f.declaringClass}#${f.name} = ${f.valueStr}\n")
        }
        sb.append("Ambient Light Methods (${ambientMethods.size}):\n")
        ambientMethods.forEach { m ->
            val isWrite = WRITE_KEYWORDS.any { m.name.lowercase().contains(it) }
            val tag = if (isWrite) " [WRITE METHOD (NOT EXECUTED)]" else ""
            sb.append("  [METHOD] ${m.declaringClass}#${m.name}(${m.parameters})$tag\n")
        }
        sb.append("\n")

        // 12. ===== DEVICE TYPE =====
        sb.append("===== DEVICE TYPE =====\n")
        var runtimeDeviceType: Any? = null
        var runtimeTypeVal: Any? = null

        if (instanceObj != null) {
            try {
                val mGetDevType = primaryClass?.getMethod("getDevicetype") ?: primaryClass?.getMethod("getDeviceType")
                if (mGetDevType != null) {
                    mGetDevType.isAccessible = true
                    runtimeDeviceType = mGetDevType.invoke(instanceObj)
                }
            } catch (t: Throwable) {
                inspectionErrors.add("Error invoking getDevicetype(): ${t.message}")
            }

            try {
                val mGetType = primaryClass?.getMethod("getType")
                if (mGetType != null) {
                    mGetType.isAccessible = true
                    runtimeTypeVal = mGetType.invoke(instanceObj)
                }
            } catch (t: Throwable) {
                inspectionErrors.add("Error invoking getType(): ${t.message}")
            }
        }

        val devTypeFormat = formatNumber(runtimeDeviceType)
        val typeFormat = formatNumber(runtimeTypeVal)

        sb.append("getDevicetype:\n")
        sb.append("DECIMAL: ${devTypeFormat.second ?: "N/A"}\n")
        sb.append("HEX: ${devTypeFormat.third ?: "N/A"}\n\n")

        sb.append("getType:\n")
        sb.append("DECIMAL: ${typeFormat.second ?: "N/A"}\n")
        sb.append("HEX: ${typeFormat.third ?: "N/A"}\n\n")

        // 13. ===== FEATURE LIST =====
        sb.append("===== FEATURE LIST =====\n")
        var featureListObj: Any? = null
        if (instanceObj != null) {
            try {
                val mGetFeatureList = primaryClass?.getMethod("getFeatureList")
                if (mGetFeatureList != null) {
                    mGetFeatureList.isAccessible = true
                    featureListObj = mGetFeatureList.invoke(instanceObj)
                }
            } catch (t: Throwable) {
                inspectionErrors.add("Error invoking getFeatureList(): ${t.message}")
            }
        }
        if (featureListObj == null) {
            sb.append("getFeatureList() = null\n\n")
        } else {
            sb.append("getFeatureList() = $featureListObj\n\n")
        }

        // 14. ===== BYDAUTO_LIGHT_GET =====
        sb.append("===== BYDAUTO_LIGHT_GET =====\n")
        appendPermissionDetail(sb, "android.permission.BYDAUTO_LIGHT_GET")

        // 15. ===== BYDAUTO_LIGHT_SET =====
        sb.append("===== BYDAUTO_LIGHT_SET =====\n")
        appendPermissionDetail(sb, "android.permission.BYDAUTO_LIGHT_SET")

        // 16. ===== OTHER BYDAUTO_LIGHT PERMISSIONS =====
        sb.append("===== OTHER BYDAUTO_LIGHT PERMISSIONS =====\n")
        try {
            val pm = context.packageManager
            val knownCandidates = listOf(
                "android.permission.BYDAUTO_LIGHT_COMMON",
                "com.byd.permission.BYD_LIGHT_CONTROL",
                "com.byd.permission.CAR_LIGHT_CONTROL",
                "android.car.permission.CONTROL_CAR_INTERIOR_LIGHTS",
                "android.car.permission.CAR_EXTERIOR_LIGHTS"
            )
            knownCandidates.forEach { perm ->
                try {
                    val info = pm.getPermissionInfo(perm, 0)
                    sb.append("Permission: $perm\n")
                    sb.append("  Owner Package: ${info.packageName}\n")
                    sb.append("  Protection Level: 0x${Integer.toHexString(info.protectionLevel)}\n\n")
                } catch (_: Throwable) {
                    sb.append("Permission: $perm (Not registered in System PM)\n\n")
                }
            }
        } catch (t: Throwable) {
            sb.append("Error querying system permissions: ${t.message}\n\n")
        }

        // 17. ===== BINDER SERVICES =====
        sb.append("===== BINDER SERVICES =====\n")
        inspectBinderService(sb, "autoservice")
        inspectBinderService(sb, "byd_car_service")

        // 18. ===== ERRORS =====
        sb.append("===== ERRORS =====\n")
        if (inspectionErrors.isEmpty()) {
            sb.append("None. All reflection steps completed safely.\n\n")
        } else {
            inspectionErrors.forEach { err ->
                sb.append("• $err\n")
            }
            sb.append("\n")
        }

        // 19. ===== END REPORT =====
        val totalMs = SystemClock.elapsedRealtime() - startTime
        sb.append("==================================================\n")
        sb.append("END REPORT (Inspection completed in ${totalMs}ms)\n")
        sb.append("==================================================\n")

        val reportString = sb.toString()

        // Persist Findings to local database safely
        try {
            repository.saveDiscovery(
                category = "LIGHT_HAL_DEEP_INSPECTOR",
                name = "BYDAutoLightDevice_Exhaustive_Report",
                status = if (primaryClass != null) DiscoveryStatus.VALIDATED else DiscoveryStatus.NOT_AVAILABLE,
                evidenceJson = JSONObject().apply {
                    put("methodsCount", allMethods.size)
                    put("fieldsCount", allFields.size)
                    put("instanceFound", instanceObj != null)
                    put("durationMs", totalMs)
                }.toString()
            )
        } catch (_: Throwable) {}

        return reportString
    }

    private fun appendPermissionDetail(sb: StringBuilder, permName: String) {
        sb.append("Permission: $permName\n")
        var isDeclared = false
        try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            isDeclared = pkgInfo.requestedPermissions?.contains(permName) == true
        } catch (_: Throwable) {}
        sb.append("Declared in our AndroidManifest: ${if (isDeclared) "YES" else "NO"}\n")

        var pmCheckStr = "DENIED"
        try {
            val res = context.packageManager.checkPermission(permName, context.packageName)
            pmCheckStr = if (res == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
        } catch (_: Throwable) {}
        sb.append("PackageManager checkPermission: $pmCheckStr\n")

        var ctxCheckStr = "DENIED"
        try {
            val res = context.checkSelfPermission(permName)
            ctxCheckStr = if (res == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
        } catch (_: Throwable) {}
        sb.append("Context.checkSelfPermission: $ctxCheckStr\n")

        var protectionLevelStr = "UNKNOWN"
        var ownerPkg = "UNKNOWN"
        try {
            val info = context.packageManager.getPermissionInfo(permName, 0)
            protectionLevelStr = "0x" + Integer.toHexString(info.protectionLevel)
            ownerPkg = info.packageName
        } catch (_: Throwable) {}

        sb.append("Protection Level: $protectionLevelStr\n")
        sb.append("Permission Owner: $ownerPkg\n\n")

        repository.savePermission(
            PermissionEntity(
                permissionName = permName,
                exists = protectionLevelStr != "UNKNOWN",
                protectionLevel = protectionLevelStr,
                isGranted = ctxCheckStr == "GRANTED",
                status = if (ctxCheckStr == "GRANTED") DiscoveryStatus.VALIDATED else DiscoveryStatus.DENIED
            )
        )
    }

    private fun inspectBinderService(sb: StringBuilder, serviceName: String) {
        var found = false
        var descriptor = "N/A"
        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceM = smClass.getMethod("getService", String::class.java)
            val binderObj = getServiceM.invoke(null, serviceName) as? IBinder
            if (binderObj != null && binderObj.isBinderAlive) {
                found = true
                descriptor = try { binderObj.interfaceDescriptor ?: "N/A" } catch (_: Throwable) { "N/A" }
            }
        } catch (t: Throwable) {
            inspectionErrors.add("Error querying Binder $serviceName: ${t.message}")
        }

        sb.append("$serviceName = ${if (found) "FOUND" else "NOT FOUND"}")
        if (found) sb.append(" (Interface Descriptor: $descriptor)")
        sb.append("\n\n")

        repository.saveBinder(
            BinderServiceEntity(
                name = serviceName,
                exists = found,
                descriptor = descriptor,
                isAlive = found,
                status = if (found) DiscoveryStatus.VALIDATED else DiscoveryStatus.NOT_AVAILABLE
            )
        )
    }

    private fun formatNumber(valObj: Any?): Triple<String, String?, String?> {
        if (valObj == null) return Triple("null", null, null)
        return when (valObj) {
            is Int -> Triple("$valObj", "$valObj", "0x" + Integer.toHexString(valObj).uppercase())
            is Long -> Triple("$valObj", "$valObj", "0x" + java.lang.Long.toHexString(valObj).uppercase())
            is Short -> Triple("$valObj", "$valObj", "0x" + Integer.toHexString(valObj.toInt()).uppercase())
            is Byte -> Triple("$valObj", "$valObj", "0x" + Integer.toHexString(valObj.toInt() and 0xFF).uppercase())
            else -> Triple(valObj.toString(), null, null)
        }
    }
}
