package com.byd.carcontrol.discovery

import android.content.Context
import com.byd.carcontrol.repository.DiscoveryRepository
import org.json.JSONObject

class FullDiscoveryEngine(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    private val binderDisc = BinderDiscovery(repository)
    private val magicCoreDisc = MagicCoreDiscovery(context, repository)
    private val providerDisc = ProviderDiscovery(context, repository)
    private val permDisc = PermissionDiscovery(context, repository)
    private val reflectionDisc = ReflectionDiscovery(context, repository)
    private val safeTester = SafeApiTester(context, repository)
    private val logcatInsp = LogcatInspector(context)

    fun runFullDiscovery(): String {
        DiscoveryLogger.log("ENGINE", "START", "FullDiscovery", "Initiating 15-step non-destructive API discovery sequence")

        // 1. Device Profile
        val profile = repository.getVehicleProfile()
        repository.saveDiscovery("DEVICE", profile.deviceModel, DiscoveryStatus.VALIDATED, JSONObject().apply {
            put("manufacturer", profile.manufacturer)
            put("brand", profile.brand)
            put("buildDisplay", profile.buildDisplay)
            put("sdkInt", profile.sdkInt)
            put("dilinkVersion", profile.dilinkVersion)
        }.toString())

        // 2. Binders
        val binders = binderDisc.runDiscovery()

        // 3. MagicCore & Providers
        val magicProviders = magicCoreDisc.runDiscovery()
        val allProviders = providerDisc.runDiscovery()

        // 4. Permissions
        val permissions = permDisc.runDiscovery()

        // 5. Classes & Reflection
        val classes = reflectionDisc.runDiscovery()

        // 6. Safe Getter Auto-Tests
        val safeGettersToTest = listOf(
            Pair("com.byd.auto.light.BYDAutoLightDevice", "getReadingLight"),
            Pair("com.byd.auto.light.BYDAutoLightDevice", "getAmbientLightSwitch"),
            Pair("android.hardware.bydauto.light.BYDAutoLightDevice", "getReadingLight"),
            Pair("com.byd.auto.door.BYDAutoDoorDevice", "getDoorState"),
            Pair("android.hardware.bydauto.door.BYDAutoDoorDevice", "getDoorState"),
            Pair("com.byd.auto.window.BYDAutoWindowDevice", "getWindowState"),
            Pair("com.byd.auto.power.BYDAutoPowerDevice", "getBatteryPercent"),
            Pair("com.byd.auto.speed.BYDAutoSpeedDevice", "getCurrentSpeed"),
            Pair("com.byd.auto.setting.BYDAutoSettingDevice", "getDrivingState")
        )

        var testedGettersCount = 0
        for ((cName, mName) in safeGettersToTest) {
            safeTester.testSafeReadMethod(cName, mName)
            testedGettersCount++
        }

        // 7. Logcat Sample
        val logcatSample = logcatInsp.captureFilteredLogcat(50)
        repository.saveDiscovery("LOGCAT", "SAMPLE", DiscoveryStatus.DISCOVERED, JSONObject().apply {
            put("sample", logcatSample.take(1000))
        }.toString())

        val summary = """
            ====================================
            BYD DiLink API DISCOVERY COMPLETE
            ====================================
            Device: ${profile.deviceModel} (${profile.dilinkVersion})
            Android SDK: ${profile.sdkInt}
            
            Binders Found: ${binders.count { it.exists }} / ${binders.size}
            ContentProviders Found: ${allProviders.size}
            MagicCore Providers: ${magicProviders.size}
            BYD Permissions Granted: ${permissions.count { it.isGranted }} / ${permissions.size}
            BYD Classes Discovered: ${classes.count { it.exists }} / ${classes.size}
            Safe Read APIs Tested: $testedGettersCount
            Control APIs Executed: 0 (SAFE MODE ENFORCED)
            ====================================
        """.trimIndent()

        DiscoveryLogger.log("ENGINE", "FINISH", "FullDiscovery", summary)
        return summary
    }
}
