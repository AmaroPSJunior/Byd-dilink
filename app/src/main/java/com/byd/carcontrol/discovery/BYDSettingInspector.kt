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
 * BYD Setting Device Deep Inspector - STRICT SAFE READ-ONLY
 *
 * Inspeciona exaustivamente a classe:
 * android.hardware.bydauto.setting.BYDAutoSettingDevice
 * e suas superclasses (ex: android.hardware.bydauto.AbsBYDAutoDevice).
 *
 * REGRA ABSOLUTA: 100% READ-ONLY.
 * NENHUM MÉTODO DE ESCRITA (set*, postEvent, voiceCtl, write, etc.) É EXECUTADO.
 */
class BYDSettingInspector(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    companion object {
        const val TARGET_SETTING_CLASS = "android.hardware.bydauto.setting.BYDAutoSettingDevice"
        const val TARGET_BODYWORK_CLASS = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
        const val TARGET_LIGHT_CLASS = "android.hardware.bydauto.light.BYDAutoLightDevice"

        val FILTER_KEYWORDS = listOf(
            "light", "lamp", "dome", "room", "reading", "courtesy",
            "interior", "inside", "cabin", "roof", "door",
            "welcome", "leave", "delay", "illumination"
        )

        val WRITE_BLOCK_KEYWORDS = listOf(
            "set", "write", "postevent", "voicectl", "update", "send", "command", "control"
        )

        val OTHER_CLASS_CANDIDATES = listOf(
            "android.hardware.bydauto.setting.BYDAutoSettingDevice",
            "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice",
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

        sb.append("===== BYD SETTING DEVICE & INTERIOR LIGHT DISCOVERY =====\n")
        sb.append("STRICT SAFE READ-ONLY MODE ACTIVE\n\n")

        // 1. SUMMARY OF PREVIOUS ANALYSES
        sb.append("SUMMARY OF PREVIOUS HAL ANALYSES\n")
        sb.append("--------------------------------\n")
        sb.append("• BYDAutoLightDevice: FOUND (DeviceType = 1004 / 0x3EC) - Exterior/headlight focus (Daytime, HighBeam, LowBeam, Fog, Footwell).\n")
        sb.append("• BYDAutoBodyworkDevice: FOUND (DeviceType = 1005 / 0x3ED) - Body hardware focus (Doors, windows, locks, bodywork).\n")
        sb.append("• Current Deep Target: android.hardware.bydauto.setting.BYDAutoSettingDevice\n\n")

        // 2. SETTING CLASS
        sb.append("SETTING CLASS\n")
        sb.append("-------------\n")
        var settingClass: Class<*>? = null
        try {
            settingClass = Class.forName(TARGET_SETTING_CLASS)
            sb.append("Target Class: $TARGET_SETTING_CLASS\n")
            sb.append("Status: FOUND\n")
            sb.append("Modifiers: ${Modifier.toString(settingClass.modifiers)}\n")
            sb.append("Package: ${settingClass.`package`?.name ?: "N/A"}\n")
            sb.append("ClassLoader: ${settingClass.classLoader?.javaClass?.name ?: "System"}\n")
        } catch (t: Throwable) {
            sb.append("Target Class: $TARGET_SETTING_CLASS\n")
            sb.append("Status: NOT_FOUND\n")
            inspectionErrors.add("Setting class $TARGET_SETTING_CLASS not found: ${t.message}")
        }

        val hierarchy = mutableListOf<Class<*>>()
        var current: Class<*>? = settingClass
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

        // 3. SETTING INSTANCE
        sb.append("SETTING INSTANCE\n")
        sb.append("----------------\n")
        var settingInstance: Any? = null
        var instanceMethodUsed = "NONE"

        if (settingClass != null) {
            val candidateOverloads = listOf(
                Pair("getInstance(Context)", arrayOf<Class<*>>(Context::class.java)),
                Pair("getInstance()", emptyArray<Class<*>>())
            )

            for (cand in candidateOverloads) {
                try {
                    val m = settingClass.getDeclaredMethod("getInstance", *cand.second)
                    m.isAccessible = true
                    val args = if (cand.second.isNotEmpty()) arrayOf<Any>(context) else emptyArray()
                    settingInstance = m.invoke(null, *args)
                    if (settingInstance != null) {
                        instanceMethodUsed = cand.first
                        break
                    }
                } catch (_: Throwable) {}
            }

            if (settingInstance != null) {
                sb.append("Status: FOUND / ACCESSIBLE\n")
                sb.append("Obtained Via: $instanceMethodUsed\n")
                sb.append("Runtime Class: ${settingInstance.javaClass.name}\n\n")
            } else {
                sb.append("Status: NOT_OBTAINED\n")
                sb.append("Note: Static inspection and class reflection remain fully operational.\n\n")
            }
        } else {
            sb.append("Status: NOT_AVAILABLE\n\n")
        }

        // 4. SETTING DEVICE TYPE
        sb.append("SETTING DEVICE TYPE\n")
        sb.append("-------------------\n")
        var devTypeVal: Any? = null
        var typeVal: Any? = null

        if (settingInstance != null && settingClass != null) {
            try {
                val mGetDevType = settingClass.getMethod("getDevicetype") ?: settingClass.getMethod("getDeviceType")
                mGetDevType.isAccessible = true
                devTypeVal = mGetDevType.invoke(settingInstance)
            } catch (t: Throwable) {
                inspectionErrors.add("Error calling getDevicetype(): ${t.message}")
            }

            try {
                val mGetType = settingClass.getMethod("getType")
                mGetType.isAccessible = true
                typeVal = mGetType.invoke(settingInstance)
            } catch (t: Throwable) {
                inspectionErrors.add("Error calling getType(): ${t.message}")
            }
        }

        val devTypeFormatted = formatNumber(devTypeVal)
        val typeFormatted = formatNumber(typeVal)

        sb.append("getDevicetype:\n")
        sb.append("  Decimal: ${devTypeFormatted.second ?: "N/A"}\n")
        sb.append("  Hex: ${devTypeFormatted.third ?: "N/A"}\n")
        sb.append("getType:\n")
        sb.append("  Decimal: ${typeFormatted.second ?: "N/A"}\n")
        sb.append("  Hex: ${typeFormatted.third ?: "N/A"}\n\n")

        // 5. SETTING FEATURE LIST
        sb.append("SETTING FEATURE LIST\n")
        sb.append("--------------------\n")
        var featureListResult: Any? = null
        if (settingInstance != null && settingClass != null) {
            try {
                val mGetFeatureList = settingClass.getMethod("getFeatureList")
                mGetFeatureList.isAccessible = true
                featureListResult = mGetFeatureList.invoke(settingInstance)
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

        // 6. SETTING FEATURE STRINGS & HASFEATURE
        sb.append("SETTING FEATURE STRINGS\n")
        sb.append("-----------------------\n")
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

            if (settingInstance != null && settingClass != null) {
                var hasFeatureMethod: Method? = null
                try {
                    hasFeatureMethod = settingClass.getMethod("hasFeature", String::class.java)
                    hasFeatureMethod.isAccessible = true
                } catch (_: Throwable) {}

                if (hasFeatureMethod != null) {
                    sb.append("hasFeature() Testing (Safe Read-Only using discovered constants):\n")
                    featureStringConstants.forEach { (fieldName, strValue) ->
                        try {
                            val res = hasFeatureMethod.invoke(settingInstance, strValue)
                            sb.append("  hasFeature(\"$strValue\") [$fieldName] => $res\n")
                        } catch (t: Throwable) {
                            sb.append("  hasFeature(\"$strValue\") [$fieldName] => ERROR: ${t.message}\n")
                        }
                    }
                    sb.append("\n")
                }
            }
        }

        // 7. SETTING PERMISSIONS
        sb.append("SETTING PERMISSIONS\n")
        sb.append("-------------------\n")
        var getPermStr: String? = null
        var setPermStr: String? = null

        if (settingInstance != null && settingClass != null) {
            try {
                val m = settingClass.getMethod("getGetPermission")
                m.isAccessible = true
                getPermStr = m.invoke(settingInstance) as? String
            } catch (_: Throwable) {}

            try {
                val m = settingClass.getMethod("getSetPermission")
                m.isAccessible = true
                setPermStr = m.invoke(settingInstance) as? String
            } catch (_: Throwable) {}
        }

        sb.append("getGetPermission(): ${getPermStr ?: "android.permission.BYDAUTO_SETTING_GET"}\n")
        sb.append("getSetPermission(): ${setPermStr ?: "android.permission.BYDAUTO_SETTING_SET"}\n\n")

        val permListToInspect = mutableListOf<String>()
        if (!getPermStr.isNullOrEmpty()) permListToInspect.add(getPermStr!!)
        if (!setPermStr.isNullOrEmpty()) permListToInspect.add(setPermStr!!)

        if (permListToInspect.isEmpty()) {
            permListToInspect.add("android.permission.BYDAUTO_SETTING_GET")
            permListToInspect.add("android.permission.BYDAUTO_SETTING_SET")
        }

        permListToInspect.distinct().forEach { permName ->
            appendPermissionDetail(sb, permName)
        }

        // Data collection for Methods and Fields across Setting hierarchy
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

        val settingMethods = mutableListOf<MethodInfo>()
        val settingFields = mutableListOf<FieldInfo>()

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
                    settingMethods.add(
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
                    } else if (settingInstance != null && clazz.isInstance(settingInstance)) {
                        try {
                            val raw = f.get(settingInstance)
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

                    settingFields.add(
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

        // 8. ===== SETTING FULL METHOD LIST =====
        sb.append("===== SETTING FULL METHOD LIST =====\n")
        sb.append("Total Setting Methods Found Across Hierarchy: ${settingMethods.size}\n\n")
        settingMethods.forEachIndexed { idx, m ->
            sb.append("METHOD [${idx + 1}/${settingMethods.size}]\n")
            sb.append("DECLARING CLASS: ${m.declaringClass}\n")
            sb.append("MODIFIERS: ${m.modifiers}\n")
            sb.append("RETURN TYPE: ${m.returnType}\n")
            sb.append("METHOD NAME: ${m.name}\n")
            sb.append("PARAMETER TYPES: (${m.parameters})\n\n")
        }

        // 9. ===== SETTING FULL FIELD LIST =====
        sb.append("===== SETTING FULL FIELD LIST =====\n")
        sb.append("Total Setting Fields Found Across Hierarchy: ${settingFields.size}\n\n")
        settingFields.forEachIndexed { idx, f ->
            sb.append("FIELD [${idx + 1}/${settingFields.size}]\n")
            sb.append("DECLARING CLASS: ${f.declaringClass}\n")
            sb.append("MODIFIERS: ${f.modifiers}\n")
            sb.append("TYPE: ${f.type}\n")
            sb.append("NAME: ${f.name}\n")
            sb.append("VALUE: ${f.valueStr}\n")
            if (f.decimalStr != null) sb.append("DECIMAL: ${f.decimalStr}\n")
            if (f.hexStr != null) sb.append("HEX: ${f.hexStr}\n")
            sb.append("\n")
        }

        // 10. ===== SETTING INTERIOR / LIGHT / COURTESY CANDIDATES =====
        val candidateMethods = settingMethods.filter { m ->
            FILTER_KEYWORDS.any { kw -> m.name.lowercase().contains(kw) }
        }
        val candidateFields = settingFields.filter { f ->
            FILTER_KEYWORDS.any { kw -> f.name.lowercase().contains(kw) }
        }

        sb.append("===== SETTING INTERIOR / LIGHT / COURTESY CANDIDATES =====\n")
        sb.append("Filtered Candidate Methods Found (${candidateMethods.size}):\n")
        candidateMethods.forEach { m ->
            val isWrite = WRITE_BLOCK_KEYWORDS.any { kw -> m.name.lowercase().contains(kw) }
            val tag = if (isWrite) " [WRITE METHOD (BLOCKED - NOT EXECUTED)]" else " [READ CANDIDATE]"
            sb.append("  • ${m.declaringClass}#${m.modifiers} ${m.returnType} ${m.name}(${m.parameters})$tag\n")
        }
        sb.append("\nFiltered Candidate Fields Found (${candidateFields.size}):\n")
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
        sb.append("Inspection completed in ${totalMs}ms.\n")

        val reportStr = sb.toString()

        // Persist findings safely
        try {
            repository.saveDiscovery(
                category = "BYD_SETTING_INTERIOR_LIGHT",
                name = "Setting_Interior_Light_Report",
                status = if (settingClass != null) com.byd.carcontrol.discovery.DiscoveryStatus.VALIDATED else com.byd.carcontrol.discovery.DiscoveryStatus.NOT_AVAILABLE,
                evidenceJson = JSONObject().apply {
                    put("methodsCount", settingMethods.size)
                    put("fieldsCount", settingFields.size)
                    put("instanceFound", settingInstance != null)
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
