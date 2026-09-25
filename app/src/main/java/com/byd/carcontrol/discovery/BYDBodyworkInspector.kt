package com.byd.carcontrol.discovery

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.byd.carcontrol.data.PermissionEntity
import com.byd.carcontrol.repository.DiscoveryRepository
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * BYD Bodywork & Interior Light Deep Inspector - STRICT SAFE READ-ONLY
 *
 * Inspeciona exaustivamente a classe:
 * android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice
 * e suas superclasses (ex: android.hardware.bydauto.AbsBYDAutoDevice).
 *
 * Também varre classes secundárias do ecossistema android.hardware.bydauto.*
 * para localizar a API real que controla a luz de teto / cortesia / domo do veículo.
 *
 * REGRA ABSOLUTA: 100% READ-ONLY. NENHUM MÉTODO DE ESCRITA (SET/WRITE) É EXECUTADO.
 */
class BYDBodyworkInspector(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    companion object {
        const val TARGET_BODYWORK_CLASS = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
        const val TARGET_LIGHT_CLASS = "android.hardware.bydauto.light.BYDAutoLightDevice"

        val INTERIOR_SEARCH_KEYWORDS = listOf(
            "room", "dome", "courtesy", "reading", "reader", "ceiling", "roof",
            "interior", "inside", "cabin", "lamp", "light",
            "door", "door_open", "door_close", "welcome", "leave", "entry", "exit"
        )

        val OTHER_CLASS_CANDIDATES = listOf(
            "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice",
            "android.hardware.bydauto.setting.BYDAutoSettingDevice",
            "android.hardware.bydauto.door.BYDAutoDoorDevice",
            "android.hardware.bydauto.cabin.BYDAutoCabinDevice",
            "android.hardware.bydauto.interior.BYDAutoInteriorDevice",
            "android.hardware.bydauto.lamp.BYDAutoLampDevice",
            "android.hardware.bydauto.ac.BYDAutoAcDevice",
            "android.hardware.bydauto.instrument.BYDAutoInstrumentDevice",
            "android.hardware.bydauto.panoramic.BYDAutoPanoramicDevice"
        )
    }

    private val inspectionErrors = mutableListOf<String>()

    fun runDiscovery(): String {
        val startTime = SystemClock.elapsedRealtime()
        val sb = StringBuilder()

        sb.append("===== BYD INTERIOR LIGHT DISCOVERY =====\n\n")

        // 1. LIGHT HAL SUMMARY
        sb.append("LIGHT HAL SUMMARY\n")
        sb.append("-----------------\n")
        try {
            val lightClass = Class.forName(TARGET_LIGHT_CLASS)
            var lightDevType = "UNKNOWN"
            try {
                val getInstanceM = lightClass.getMethod("getInstance", Context::class.java)
                val lightInst = getInstanceM.invoke(null, context)
                if (lightInst != null) {
                    val getTypeM = lightClass.getMethod("getType")
                    val valType = getTypeM.invoke(lightInst)
                    if (valType is Int) {
                        lightDevType = "$valType / 0x${Integer.toHexString(valType).uppercase()}"
                    }
                }
            } catch (_: Throwable) {}

            sb.append("BYDAutoLightDevice: FOUND\n")
            sb.append("DeviceType: $lightDevType\n")
            sb.append("Interior/Dome explicit candidates in Light HAL: NONE (Focus shifted to Bodywork HAL)\n\n")
        } catch (_: Throwable) {
            sb.append("BYDAutoLightDevice: NOT_FOUND\n\n")
        }

        // 2. BODYWORK CLASS
        sb.append("BODYWORK CLASS\n")
        sb.append("--------------\n")
        var bodyworkClass: Class<*>? = null
        try {
            bodyworkClass = Class.forName(TARGET_BODYWORK_CLASS)
            sb.append("Target Class: $TARGET_BODYWORK_CLASS\n")
            sb.append("Status: FOUND\n")
            sb.append("Modifiers: ${Modifier.toString(bodyworkClass.modifiers)}\n")
            sb.append("Package: ${bodyworkClass.`package`?.name ?: "N/A"}\n")
            sb.append("ClassLoader: ${bodyworkClass.classLoader?.javaClass?.name ?: "System"}\n")
        } catch (t: Throwable) {
            sb.append("Target Class: $TARGET_BODYWORK_CLASS\n")
            sb.append("Status: NOT_FOUND\n")
            inspectionErrors.add("Bodywork class $TARGET_BODYWORK_CLASS not found: ${t.message}")
        }

        val hierarchy = mutableListOf<Class<*>>()
        var current: Class<*>? = bodyworkClass
        while (current != null && current != Any::class.java) {
            hierarchy.add(current)
            try {
                current = current.superclass
            } catch (t: Throwable) {
                inspectionErrors.add("Error traversing superclass of ${current.name}: ${t.message}")
                break
            }
        }

        if (hierarchy.isNotEmpty()) {
            sb.append("Inheritance Hierarchy:\n")
            hierarchy.forEachIndexed { idx, clazz ->
                sb.append("  [$idx] ${clazz.name} (${Modifier.toString(clazz.modifiers)})\n")
                val interfaces = clazz.interfaces.map { it.name }
                if (interfaces.isNotEmpty()) {
                    sb.append("      Interfaces: ${interfaces.joinToString(", ")}\n")
                }
            }
        }
        sb.append("\n")

        // 3. BODYWORK INSTANCE
        sb.append("BODYWORK INSTANCE\n")
        sb.append("-----------------\n")
        var bodyworkInstance: Any? = null
        var instanceMethodUsed = "NONE"

        if (bodyworkClass != null) {
            // Try getInstance overloads
            val candidateMethods = listOf(
                Pair("getInstance(Context)", arrayOf<Class<*>>(Context::class.java)),
                Pair("getInstance()", emptyArray<Class<*>>())
            )

            for (cand in candidateMethods) {
                try {
                    val m = bodyworkClass.getDeclaredMethod("getInstance", *cand.second)
                    m.isAccessible = true
                    val args = if (cand.second.isNotEmpty()) arrayOf<Any>(context) else emptyArray()
                    bodyworkInstance = m.invoke(null, *args)
                    if (bodyworkInstance != null) {
                        instanceMethodUsed = cand.first
                        break
                    }
                } catch (_: Throwable) {}
            }

            if (bodyworkInstance != null) {
                sb.append("Status: FOUND / ACCESSIBLE\n")
                sb.append("Obtained Via: $instanceMethodUsed\n")
                sb.append("Runtime Class: ${bodyworkInstance.javaClass.name}\n\n")
            } else {
                sb.append("Status: NOT_OBTAINED\n")
                sb.append("Note: Static inspection and class reflection remain fully operational.\n\n")
            }
        } else {
            sb.append("Status: NOT_AVAILABLE\n\n")
        }

        // 4. BODYWORK DEVICE TYPE
        sb.append("BODYWORK DEVICE TYPE\n")
        sb.append("--------------------\n")
        var bodyDevTypeVal: Any? = null
        var bodyTypeVal: Any? = null

        if (bodyworkInstance != null && bodyworkClass != null) {
            try {
                val mGetDevType = bodyworkClass.getMethod("getDevicetype") ?: bodyworkClass.getMethod("getDeviceType")
                mGetDevType.isAccessible = true
                bodyDevTypeVal = mGetDevType.invoke(bodyworkInstance)
            } catch (t: Throwable) {
                inspectionErrors.add("Error calling getDevicetype(): ${t.message}")
            }

            try {
                val mGetType = bodyworkClass.getMethod("getType")
                mGetType.isAccessible = true
                bodyTypeVal = mGetType.invoke(bodyworkInstance)
            } catch (t: Throwable) {
                inspectionErrors.add("Error calling getType(): ${t.message}")
            }
        }

        val devTypeFormatted = formatNumber(bodyDevTypeVal)
        val typeFormatted = formatNumber(bodyTypeVal)

        sb.append("getDevicetype:\n")
        sb.append("  Decimal: ${devTypeFormatted.second ?: "N/A"}\n")
        sb.append("  Hex: ${devTypeFormatted.third ?: "N/A"}\n")
        sb.append("getType:\n")
        sb.append("  Decimal: ${typeFormatted.second ?: "N/A"}\n")
        sb.append("  Hex: ${typeFormatted.third ?: "N/A"}\n\n")

        // 5. BODYWORK FEATURE LIST
        sb.append("BODYWORK FEATURE LIST\n")
        sb.append("---------------------\n")
        var featureListResult: Any? = null
        if (bodyworkInstance != null && bodyworkClass != null) {
            try {
                val mGetFeatureList = bodyworkClass.getMethod("getFeatureList")
                mGetFeatureList.isAccessible = true
                featureListResult = mGetFeatureList.invoke(bodyworkInstance)
            } catch (t: Throwable) {
                inspectionErrors.add("Error calling getFeatureList(): ${t.message}")
            }
        }

        if (featureListResult == null) {
            sb.append("getFeatureList() = null\n\n")
        } else if (featureListResult is IntArray) {
            sb.append("getFeatureList() returned IntArray (${featureListResult.size} elements):\n")
            featureListResult.forEachIndexed { idx, v ->
                sb.append("  [$idx] Decimal: $v | Hex: 0x${Integer.toHexString(v).uppercase()}\n")
            }
            sb.append("\n")
        } else {
            sb.append("getFeatureList() = $featureListResult\n\n")
        }

        // 6. BODYWORK FEATURE STRINGS & HASFEATURE
        sb.append("BODYWORK FEATURE STRINGS\n")
        sb.append("------------------------\n")
        val featureStringConstants = mutableMapOf<String, String>()

        hierarchy.forEach { clazz ->
            try {
                val fields = clazz.declaredFields
                for (f in fields) {
                    if (Modifier.isStatic(f.modifiers) && f.type == String::class.java) {
                        if (f.name.contains("FEATURE", ignoreCase = true)) {
                            f.isAccessible = true
                            val strVal = f.get(null) as? String
                            if (strVal != null) {
                                featureStringConstants[f.name] = strVal
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                inspectionErrors.add("Error scanning feature strings on ${clazz.name}: ${t.message}")
            }
        }

        if (featureStringConstants.isEmpty()) {
            sb.append("No static String FEATURE_* constants discovered in fields.\n\n")
        } else {
            sb.append("Discovered ${featureStringConstants.size} FEATURE constants:\n")
            featureStringConstants.forEach { (fieldName, strValue) ->
                sb.append("  FIELD: $fieldName => VALUE: \"$strValue\"\n")
            }
            sb.append("\n")

            // Safe hasFeature testing
            if (bodyworkInstance != null && bodyworkClass != null) {
                var hasFeatureMethod: Method? = null
                try {
                    hasFeatureMethod = bodyworkClass.getMethod("hasFeature", String::class.java)
                    hasFeatureMethod.isAccessible = true
                } catch (_: Throwable) {}

                if (hasFeatureMethod != null) {
                    sb.append("hasFeature() Testing (Safe Read-Only using discovered constants):\n")
                    featureStringConstants.forEach { (fieldName, strValue) ->
                        try {
                            val res = hasFeatureMethod.invoke(bodyworkInstance, strValue)
                            sb.append("  hasFeature(\"$strValue\") [$fieldName] => $res\n")
                        } catch (t: Throwable) {
                            sb.append("  hasFeature(\"$strValue\") [$fieldName] => ERROR: ${t.message}\n")
                        }
                    }
                    sb.append("\n")
                }
            }
        }

        // 7. BODYWORK PERMISSIONS
        sb.append("BODYWORK PERMISSIONS\n")
        sb.append("--------------------\n")
        var getPermStr: String? = null
        var setPermStr: String? = null

        if (bodyworkInstance != null && bodyworkClass != null) {
            try {
                val m = bodyworkClass.getMethod("getGetPermission")
                m.isAccessible = true
                getPermStr = m.invoke(bodyworkInstance) as? String
            } catch (_: Throwable) {}

            try {
                val m = bodyworkClass.getMethod("getSetPermission")
                m.isAccessible = true
                setPermStr = m.invoke(bodyworkInstance) as? String
            } catch (_: Throwable) {}
        }

        val permListToInspect = mutableListOf<String>()
        if (!getPermStr.isNullOrEmpty()) permListToInspect.add(getPermStr!!)
        if (!setPermStr.isNullOrEmpty()) permListToInspect.add(setPermStr!!)

        // Add standard bodywork permission fallbacks if not returned
        if (permListToInspect.isEmpty()) {
            permListToInspect.add("android.permission.BYDAUTO_BODYWORK_GET")
            permListToInspect.add("android.permission.BYDAUTO_BODYWORK_SET")
        }

        permListToInspect.distinct().forEach { permName ->
            appendPermissionDetail(sb, permName)
        }

        // Data collection for Methods and Fields across Bodywork hierarchy
        data class MethodInfo(
            val declaringClass: String,
            val modifiers: String,
            val returnType: String,
            val name: String,
            val parameters: String
        )

        data class FieldInfo(
            val declaringClass: String,
            val modifiers: String,
            val type: String,
            val name: String,
            val valueStr: String,
            val decimalStr: String?,
            val hexStr: String?
        )

        val bodyworkMethods = mutableListOf<MethodInfo>()
        val bodyworkFields = mutableListOf<FieldInfo>()

        hierarchy.forEach { clazz ->
            // Collect methods
            val methods = try { clazz.declaredMethods } catch (t: Throwable) {
                inspectionErrors.add("Error getting declaredMethods on ${clazz.name}: ${t.message}")
                emptyArray<Method>()
            }
            for (m in methods) {
                try {
                    val mods = Modifier.toString(m.modifiers)
                    val ret = m.returnType.name
                    val params = m.parameterTypes.map { it.name }.joinToString(", ")
                    bodyworkMethods.add(
                        MethodInfo(
                            declaringClass = clazz.name,
                            modifiers = mods,
                            returnType = ret,
                            name = m.name,
                            parameters = params
                        )
                    )
                } catch (t: Throwable) {
                    inspectionErrors.add("Error reading method ${m.name}: ${t.message}")
                }
            }

            // Collect fields
            val fields = try { clazz.declaredFields } catch (t: Throwable) {
                inspectionErrors.add("Error getting declaredFields on ${clazz.name}: ${t.message}")
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

                    if (isStatic) {
                        try {
                            val raw = f.get(null)
                            val formatted = formatNumber(raw)
                            valueStr = formatted.first
                            decimalStr = formatted.second
                            hexStr = formatted.third
                        } catch (t: Throwable) {
                            valueStr = "VALUE_ERROR: ${t.message}"
                        }
                    } else if (bodyworkInstance != null && clazz.isInstance(bodyworkInstance)) {
                        try {
                            val raw = f.get(bodyworkInstance)
                            val formatted = formatNumber(raw)
                            valueStr = formatted.first
                            decimalStr = formatted.second
                            hexStr = formatted.third
                        } catch (t: Throwable) {
                            valueStr = "VALUE_ERROR: ${t.message}"
                        }
                    } else {
                        valueStr = "<INSTANCE_REQUIRED>"
                    }

                    bodyworkFields.add(
                        FieldInfo(
                            declaringClass = clazz.name,
                            modifiers = mods,
                            type = typeStr,
                            name = f.name,
                            valueStr = valueStr,
                            decimalStr = decimalStr,
                            hexStr = hexStr
                        )
                    )
                } catch (t: Throwable) {
                    inspectionErrors.add("Error reading field ${f.name}: ${t.message}")
                }
            }
        }

        // 8. ===== BODYWORK FULL METHOD LIST =====
        sb.append("===== BODYWORK FULL METHOD LIST =====\n")
        sb.append("Total Bodywork Methods Found: ${bodyworkMethods.size}\n\n")
        bodyworkMethods.forEachIndexed { idx, m ->
            sb.append("METHOD [${idx + 1}/${bodyworkMethods.size}]\n")
            sb.append("DECLARING CLASS: ${m.declaringClass}\n")
            sb.append("MODIFIERS: ${m.modifiers}\n")
            sb.append("RETURN TYPE: ${m.returnType}\n")
            sb.append("METHOD NAME: ${m.name}\n")
            sb.append("PARAMETER TYPES: (${m.parameters})\n\n")
        }

        // 9. ===== BODYWORK FULL FIELD LIST =====
        sb.append("===== BODYWORK FULL FIELD LIST =====\n")
        sb.append("Total Bodywork Fields Found: ${bodyworkFields.size}\n\n")
        bodyworkFields.forEachIndexed { idx, f ->
            sb.append("FIELD [${idx + 1}/${bodyworkFields.size}]\n")
            sb.append("DECLARING CLASS: ${f.declaringClass}\n")
            sb.append("MODIFIERS: ${f.modifiers}\n")
            sb.append("TYPE: ${f.type}\n")
            sb.append("NAME: ${f.name}\n")
            sb.append("VALUE: ${f.valueStr}\n")
            if (f.decimalStr != null) sb.append("DECIMAL: ${f.decimalStr}\n")
            if (f.hexStr != null) sb.append("HEX: ${f.hexStr}\n")
            sb.append("\n")
        }

        // 10. ===== INTERIOR / DOME / COURTESY CANDIDATES =====
        val candidateMethods = bodyworkMethods.filter { m ->
            INTERIOR_SEARCH_KEYWORDS.any { kw -> m.name.lowercase().contains(kw) }
        }
        val candidateFields = bodyworkFields.filter { f ->
            INTERIOR_SEARCH_KEYWORDS.any { kw -> f.name.lowercase().contains(kw) }
        }

        sb.append("===== INTERIOR / DOME / COURTESY CANDIDATES =====\n")
        sb.append("Candidate Methods Found (${candidateMethods.size}):\n")
        candidateMethods.forEach { m ->
            val isWrite = m.name.lowercase().startsWith("set") || m.name.lowercase().startsWith("write")
            val tag = if (isWrite) " [WRITE METHOD (BLOCKED - NOT EXECUTED)]" else " [READ CANDIDATE]"
            sb.append("  • ${m.declaringClass}#${m.modifiers} ${m.returnType} ${m.name}(${m.parameters})$tag\n")
        }
        sb.append("\nCandidate Fields Found (${candidateFields.size}):\n")
        candidateFields.forEach { f ->
            sb.append("  • ${f.declaringClass}#${f.name} (${f.type}) = ${f.valueStr}\n")
            if (f.decimalStr != null && f.hexStr != null) {
                sb.append("    DECIMAL: ${f.decimalStr} | HEX: ${f.hexStr}\n")
            }
        }
        sb.append("\n")

        // 11. ===== OTHER RELEVANT BYDAUTO CLASSES =====
        sb.append("===== OTHER RELEVANT BYDAUTO CLASSES =====\n")
        OTHER_CLASS_CANDIDATES.distinct().forEach { className ->
            try {
                val clazz = Class.forName(className)
                val isInst = try {
                    val m = clazz.getMethod("getInstance", Context::class.java)
                    m.invoke(null, context) != null
                } catch (_: Throwable) { false }

                sb.append("CLASS: $className\n")
                sb.append("  Status: FOUND\n")
                sb.append("  Instance Accessible: ${if (isInst) "YES" else "NO"}\n")
                sb.append("  Declared Methods: ${clazz.declaredMethods.size}\n")
                sb.append("  Declared Fields: ${clazz.declaredFields.size}\n\n")
            } catch (_: Throwable) {
                sb.append("CLASS: $className\n")
                sb.append("  Status: NOT_FOUND\n\n")
            }
        }

        // 12. ===== ERRORS =====
        sb.append("===== ERRORS =====\n")
        if (inspectionErrors.isEmpty()) {
            sb.append("None. All reflection steps completed safely.\n\n")
        } else {
            inspectionErrors.forEach { err ->
                sb.append("• $err\n")
            }
            sb.append("\n")
        }

        // 13. ===== END =====
        val totalMs = SystemClock.elapsedRealtime() - startTime
        sb.append("===== END ===== (Inspection completed in ${totalMs}ms)\n")

        val reportStr = sb.toString()

        // Persist findings safely
        try {
            repository.saveDiscovery(
                category = "BYD_BODYWORK_INTERIOR_LIGHT",
                name = "Bodywork_Interior_Light_Report",
                status = if (bodyworkClass != null) com.byd.carcontrol.discovery.DiscoveryStatus.VALIDATED else com.byd.carcontrol.discovery.DiscoveryStatus.NOT_AVAILABLE,
                evidenceJson = JSONObject().apply {
                    put("methodsCount", bodyworkMethods.size)
                    put("fieldsCount", bodyworkFields.size)
                    put("instanceFound", bodyworkInstance != null)
                    put("durationMs", totalMs)
                }.toString()
            )
        } catch (_: Throwable) {}

        return reportStr
    }

    private fun appendPermissionDetail(sb: StringBuilder, permName: String) {
        sb.append("Permission: $permName\n")
        var isDeclared = false
        try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            isDeclared = pkgInfo.requestedPermissions?.contains(permName) == true
        } catch (_: Throwable) {}
        sb.append("  Declared in Manifest: ${if (isDeclared) "YES" else "NO"}\n")

        var pmCheckStr = "DENIED"
        try {
            val res = context.packageManager.checkPermission(permName, context.packageName)
            pmCheckStr = if (res == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
        } catch (_: Throwable) {}
        sb.append("  PackageManager checkPermission: $pmCheckStr\n")

        var ctxCheckStr = "DENIED"
        try {
            val res = context.checkSelfPermission(permName)
            ctxCheckStr = if (res == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
        } catch (_: Throwable) {}
        sb.append("  Context.checkSelfPermission: $ctxCheckStr\n")

        var protectionLevelStr = "UNKNOWN"
        var ownerPkg = "UNKNOWN"
        try {
            val info = context.packageManager.getPermissionInfo(permName, 0)
            protectionLevelStr = "0x" + Integer.toHexString(info.protectionLevel)
            ownerPkg = info.packageName
        } catch (_: Throwable) {}

        sb.append("  Protection Level: $protectionLevelStr\n")
        sb.append("  Permission Owner: $ownerPkg\n\n")

        repository.savePermission(
            PermissionEntity(
                permissionName = permName,
                exists = protectionLevelStr != "UNKNOWN",
                protectionLevel = protectionLevelStr,
                isGranted = ctxCheckStr == "GRANTED",
                status = if (ctxCheckStr == "GRANTED") com.byd.carcontrol.discovery.DiscoveryStatus.VALIDATED else com.byd.carcontrol.discovery.DiscoveryStatus.DENIED
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
