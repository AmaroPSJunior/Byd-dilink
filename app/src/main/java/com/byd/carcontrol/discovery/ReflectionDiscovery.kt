package com.byd.carcontrol.discovery

import android.content.Context
import com.byd.carcontrol.data.ApiClassEntity
import com.byd.carcontrol.data.ApiMethodEntity
import com.byd.carcontrol.repository.DiscoveryRepository
import java.lang.reflect.Modifier

class ReflectionDiscovery(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    private companion object {
        private val CANDIDATE_CLASSES = listOf(
            "com.byd.auto.light.BYDAutoLightDevice",
            "android.hardware.bydauto.light.BYDAutoLightDevice",
            "com.byd.auto.BYDAutoLightDevice",
            "com.byd.service.BYDAutoLightBus",
            "com.byd.auto.light.BYDAutoLightManager",
            "com.byd.auto.BYDAutoDeviceManager",
            "com.byd.auto.light.BYDLight",
            "com.byd.auto.light.BYDLightManager",
            "com.byd.auto.door.BYDAutoDoorDevice",
            "android.hardware.bydauto.door.BYDAutoDoorDevice",
            "com.byd.auto.window.BYDAutoWindowDevice",
            "android.hardware.bydauto.window.BYDAutoWindowDevice",
            "com.byd.auto.aircondition.BYDAutoAirConditionDevice",
            "android.hardware.bydauto.aircondition.BYDAutoAirConditionDevice",
            "com.byd.auto.power.BYDAutoPowerDevice",
            "android.hardware.bydauto.power.BYDAutoPowerDevice",
            "com.byd.auto.screen.BYDAutoScreenDevice",
            "android.hardware.bydauto.screen.BYDAutoScreenDevice",
            "com.byd.auto.speed.BYDAutoSpeedDevice",
            "android.hardware.bydauto.speed.BYDAutoSpeedDevice",
            "com.byd.auto.setting.BYDAutoSettingDevice",
            "android.hardware.bydauto.setting.BYDAutoSettingDevice",
            "android.hardware.bydauto.ota.BYDAutoOtaDevice",
            "com.byd.cluster.spi.ClusterSpiDevice",
            "com.byd.service.ClusterDebugService"
        )

        private val CONTROL_PREFIXES = listOf(
            "set", "enable", "disable", "open", "close", "lock", "unlock",
            "turnon", "turnoff", "setvalue", "write", "control", "move", "adjust", "post"
        )
    }

    fun runDiscovery(): List<ApiClassEntity> {
        val classResults = mutableListOf<ApiClassEntity>()

        for (className in CANDIDATE_CLASSES) {
            try {
                val clazz = Class.forName(className)
                val pkgName = clazz.`package`?.name ?: className.substringBeforeLast('.', "")
                val superclassName = clazz.superclass?.name ?: "java.lang.Object"
                val interfaceNames = clazz.interfaces.map { it.name }

                val classEntity = ApiClassEntity(
                    className = className,
                    packageName = pkgName,
                    exists = true,
                    superclass = superclassName,
                    interfaces = interfaceNames,
                    status = DiscoveryStatus.DISCOVERED,
                    timestamp = System.currentTimeMillis()
                )
                repository.saveClass(classEntity)
                classResults.add(classEntity)

                DiscoveryLogger.log(
                    category = "REFLECTION",
                    operation = "CLASS_FOUND",
                    target = className,
                    result = "Superclass: $superclassName, Interfaces: ${interfaceNames.size}"
                )

                // Inspect Methods
                val methods = clazz.declaredMethods
                for (m in methods) {
                    val mName = m.name
                    val lowerName = mName.lowercase()
                    val isStatic = Modifier.isStatic(m.modifiers)
                    val vis = when {
                        Modifier.isPublic(m.modifiers) -> "public"
                        Modifier.isProtected(m.modifiers) -> "protected"
                        Modifier.isPrivate(m.modifiers) -> "private"
                        else -> "package-private"
                    }

                    val safety = if (CONTROL_PREFIXES.any { lowerName.startsWith(it) }) {
                        ApiSafety.CONTROL
                    } else if (lowerName.startsWith("get") || lowerName.startsWith("is") || lowerName.startsWith("has") || lowerName.startsWith("query") || lowerName.startsWith("read")) {
                        ApiSafety.READ_ONLY
                    } else {
                        ApiSafety.UNKNOWN
                    }

                    val methodEntity = ApiMethodEntity(
                        id = "$className#$mName",
                        classId = className,
                        className = className,
                        methodName = mName,
                        visibility = vis,
                        isStatic = isStatic,
                        returnType = m.returnType.name,
                        parameterTypes = m.parameterTypes.map { it.name },
                        safety = safety,
                        status = DiscoveryStatus.DISCOVERED,
                        timestamp = System.currentTimeMillis()
                    )

                    // Note: methods entity saved via class evidence
                    DiscoveryLogger.log(
                        category = "REFLECTION",
                        operation = "METHOD_INSPECT",
                        target = "$className#$mName",
                        result = "Vis: $vis, Static: $isStatic, Return: ${m.returnType.simpleName}, Safety: $safety"
                    )
                }

            } catch (e: ClassNotFoundException) {
                val classEntity = ApiClassEntity(
                    className = className,
                    packageName = className.substringBeforeLast('.', ""),
                    exists = false,
                    status = DiscoveryStatus.NOT_AVAILABLE,
                    timestamp = System.currentTimeMillis()
                )
                repository.saveClass(classEntity)
                DiscoveryLogger.log("REFLECTION", "CLASS_NOT_FOUND", className, "ClassNotFoundException", e)
            } catch (e: Exception) {
                DiscoveryLogger.log("REFLECTION", "CLASS_INSPECT_EXCEPTION", className, "Error: ${e.message}", e)
            }
        }

        return classResults
    }
}
