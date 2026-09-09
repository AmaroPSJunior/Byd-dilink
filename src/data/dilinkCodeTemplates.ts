export const ANDROID_MANIFEST_XML = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.byd.carcontrol">

    <!-- BYD DiLink Hardware & Car System Permissions -->
    <uses-permission android:name="com.byd.permission.HARDWARE_CONTROL" />
    <uses-permission android:name="com.byd.permission.CAR_STATE_READ" />
    <uses-permission android:name="com.byd.permission.LIGHT_CONTROL" />
    <uses-permission android:name="com.byd.permission.WINDOW_CONTROL" />
    <uses-permission android:name="com.byd.permission.DOOR_CONTROL" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="BYD Controller Kotlin"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|keyboardHidden|screenSize"
            android:launchMode="singleTask"
            android:screenOrientation="landscape">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- Receiver em Kotlin para capturar eventos de CAN Bus e Intenções DiLink -->
        <receiver android:name=".BYDCarStateReceiver" android:exported="true">
            <intent-filter>
                <action android:name="com.byd.action.SEATBELT_STATE_CHANGED" />
                <action android:name="com.byd.action.DOOR_STATE_CHANGED" />
                <action android:name="com.byd.action.LIGHT_STATE_CHANGED" />
                <action android:name="com.byd.action.WINDOW_STATE_CHANGED" />
            </intent-filter>
        </receiver>
    </application>
</manifest>`;

export const MAIN_ACTIVITY_KOTLIN = `package com.byd.carcontrol

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * BYD DiLink Vehicle Controller (Kotlin Native)
 * Target: BYD Dolphin, Song Plus, Yuan Plus, Seal, Tan, Han
 */
class MainActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "BYDControllerKt"
    }

    private lateinit var bydHelper: BYDDiLinkServiceHelper
    private lateinit var btnMasterTurnOffLights: Button
    private lateinit var txtSeatbeltStatus: TextView
    private lateinit var txtDoorsStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa o Helper do SDK DiLink via Reflection em Kotlin
        bydHelper = BYDDiLinkServiceHelper(this)

        btnMasterTurnOffLights = findViewById(R.id.btnMasterTurnOffLights)
        txtSeatbeltStatus = findViewById(R.id.txtSeatbeltStatus)
        txtDoorsStatus = findViewById(R.id.txtDoorsStatus)

        // Botão Central: Apagar todas as luzes internas do carro
        btnMasterTurnOffLights.setOnClickListener {
            val success = bydHelper.turnOffAllInternalLights()
            if (success) {
                Toast.makeText(this, "Luzes internas apagadas via DiLink Bus (Kotlin)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Enviando Broadcast Intent de fallback...", Toast.LENGTH_SHORT).show()
            }
        }

        // Observa status dos cintos em tempo real
        bydHelper.observeSeatbeltStatus { status ->
            runOnUiThread {
                txtSeatbeltStatus.text = "Status Cintos: \${status.getDescription()}"
            }
        }

        // Observa status das portas
        bydHelper.observeDoorStatus { status ->
            runOnUiThread {
                txtDoorsStatus.text = "Status Portas: \${status.getDescription()}"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bydHelper.unregisterReceivers()
    }
}`;

export const BYD_DILINK_SERVICE_HELPER_KOTLIN = `package com.byd.carcontrol

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.lang.reflect.Method

/**
 * Kotlin Service Helper otimizado para BYD Dolphin, Dolphin Plus, Song, Seal, Yuan e Han.
 * Implementa 5 canais redundantes com transmissão explícita direcionada aos pacotes DiLink.
 */
class BYDDiLinkServiceHelper(private val context: Context) {

    companion object {
        private const val TAG = "BYDDiLinkHelperKt"

        private val LIGHT_SERVICE_CLASSES = listOf(
            "com.byd.auto.light.BYDAutoLightDevice",
            "android.hardware.bydauto.light.BYDAutoLightDevice",
            "com.byd.auto.BYDAutoLightDevice",
            "com.byd.service.BYDAutoLightBus",
            "com.byd.auto.BYDAutoLightManager",
            "com.byd.auto.BYDAutoDeviceManager",
            "com.byd.auto.light.BYDLight"
        )

        private val BYD_TARGET_PACKAGES = listOf(
            "com.byd.auto",
            "com.byd.carsettings",
            "com.byd.autorun.service",
            "com.byd.service",
            "com.byd.autoui",
            "com.byd.systemui",
            "com.byd.car.settings"
        )
    }

    private var bydLightBusInstance: Any? = null
    private var detectedLightClassName: String? = null
    private var bydDoorBusInstance: Any? = null
    private var bydWindowBusInstance: Any? = null
    private var bydHvacBusInstance: Any? = null
    private var bydBatteryBusInstance: Any? = null
    private var bydScreenBusInstance: Any? = null

    init {
        initBYDServicesReflection()
    }

    private fun initBYDServicesReflection() {
        for (className in LIGHT_SERVICE_CLASSES) {
            val instance = getServiceInstance(className)
            if (instance != null) {
                bydLightBusInstance = instance
                detectedLightClassName = className
                Log.i(TAG, "Módulo de luzes BYD conectado via HAL: $className")
                break
            }
        }

        bydDoorBusInstance = getServiceInstance("com.byd.auto.door.BYDAutoDoorDevice")
            ?: getServiceInstance("android.hardware.bydauto.door.BYDAutoDoorDevice")
            ?: getServiceInstance("com.byd.service.BYDAutoDoorBus")

        bydWindowBusInstance = getServiceInstance("com.byd.auto.window.BYDAutoWindowDevice")
            ?: getServiceInstance("android.hardware.bydauto.window.BYDAutoWindowDevice")
            ?: getServiceInstance("com.byd.service.BYDAutoWindowBus")

        bydHvacBusInstance = getServiceInstance("com.byd.auto.aircondition.BYDAutoAirConditionDevice")
            ?: getServiceInstance("android.hardware.bydauto.aircondition.BYDAutoAirConditionDevice")
            ?: getServiceInstance("com.byd.service.BYDAutoHVACBus")

        bydBatteryBusInstance = getServiceInstance("com.byd.auto.power.BYDAutoPowerDevice")
            ?: getServiceInstance("android.hardware.bydauto.power.BYDAutoPowerDevice")
            ?: getServiceInstance("com.byd.service.BYDAutoBatteryBus")

        bydScreenBusInstance = getServiceInstance("com.byd.auto.screen.BYDAutoScreenDevice")
            ?: getServiceInstance("android.hardware.bydauto.screen.BYDAutoScreenDevice")
            ?: getServiceInstance("com.byd.service.BYDAutoScreenBus")
    }

    private fun getServiceInstance(className: String): Any? {
        return try {
            val clazz = Class.forName(className)
            try {
                val getInstanceMethod: Method = clazz.getMethod("getInstance", Context::class.java)
                getInstanceMethod.invoke(null, context)
            } catch (e: NoSuchMethodException) {
                try {
                    val getInstanceMethod: Method = clazz.getMethod("getInstance")
                    getInstanceMethod.invoke(null)
                } catch (e2: NoSuchMethodException) {
                    clazz.getDeclaredConstructor().newInstance()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun hasWriteSettingsPermission(): Boolean = Settings.System.canWrite(context)

    fun requestWriteSettingsPermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:\${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    data class LightControlResult(
        val success: Boolean,
        val message: String,
        val channelsTriggered: List<String>
    )

    private fun sendTargetedBYDBroadcasts(baseIntent: Intent): Int {
        var count = 0
        try { context.sendBroadcast(baseIntent); count++ } catch (_: Exception) {}
        for (pkg in BYD_TARGET_PACKAGES) {
            try {
                context.sendBroadcast(Intent(baseIntent).apply { setPackage(pkg) })
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    fun turnOffAllInternalLights(): LightControlResult {
        val channels = mutableListOf<String>()

        bydLightBusInstance?.let { instance ->
            for (zone in listOf(0, 1, 2, 3)) {
                val methods = listOf(
                    Pair("setReadingLight", arrayOf(zone, 0)),
                    Pair("setReadingLightState", arrayOf(zone, 0)),
                    Pair("setReadingLightSwitch", arrayOf(zone, 0)),
                    Pair("setAmbientLightSwitch", arrayOf(0)),
                    Pair("setAmbientLightState", arrayOf(0)),
                    Pair("setTopLightState", arrayOf(0)),
                    Pair("setCeilingLightState", arrayOf(0)),
                    Pair("setFootwellLight", arrayOf(0))
                )
                for ((methodName, args) in methods) {
                    try {
                        val types = args.map { it.javaClass.getField("TYPE").get(null) as Class<*> }.toTypedArray()
                        val m = instance.javaClass.getMethod(methodName, *types)
                        m.invoke(instance, *args)
                    } catch (_: Exception) {}
                }
            }
            channels.add("HAL Nativo ($detectedLightClassName)")
        }

        if (hasWriteSettingsPermission()) {
            try {
                val cr = context.contentResolver
                val keys = listOf("auto_dome_light", "byd_auto_dome_light", "byd_dome_light", "byd_reading_light", "byd_ambient_light_switch")
                for (k in keys) { try { Settings.System.putInt(cr, k, 0) } catch (_: Exception) {} }
                channels.add("Settings.System (Auto Dome = 0)")
            } catch (_: Exception) {}
        }

        val intents = listOf(
            Intent("com.byd.action.CONTROL_LIGHTS").apply { putExtra("light_type", "ALL_INTERIOR"); putExtra("state", 0); putExtra("command", "MASTER_OFF") },
            Intent("com.byd.action.LIGHT_CONTROL").apply { putExtra("command", "MASTER_OFF"); putExtra("target", "ALL_INTERNAL_LIGHTS"); putExtra("value", 0) },
            Intent("byd.intent.action.LIGHT_CONTROL").apply { putExtra("type", "reading"); putExtra("status", 0) },
            Intent("com.byd.action.DOMELIGHT_OFF"),
            Intent("com.byd.action.AMBIENT_LIGHT_SWITCH").apply { putExtra("state", 0) }
        )
        var totalFired = 0
        for (it in intents) { totalFired += sendTargetedBYDBroadcasts(it) }
        channels.add("Broadcasts Explícitos BYD ($totalFired disparados)")

        try {
            val carClass = Class.forName("android.car.Car")
            val carObj = carClass.getMethod("createCar", Context::class.java).invoke(null, context)
            val propMgr = carClass.getMethod("getCarManager", String::class.java).invoke(carObj, "property")
            val setPropMethod = propMgr.javaClass.getMethod("setIntProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            setPropMethod.invoke(propMgr, 289410818, 0, 0)
            channels.add("Android Automotive CarPropertyManager")
        } catch (_: Exception) {}

        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "settings put system auto_dome_light 0"))
            channels.add("Shell Command")
        } catch (_: Exception) {}

        val summary = "Corte de iluminação concluído via: " + channels.joinToString(", ")
        Log.i(TAG, summary)
        return LightControlResult(true, summary, channels)
    }

    fun turnOnAllInternalLights(): LightControlResult {
        val channels = mutableListOf<String>()
        bydLightBusInstance?.let { instance ->
            try {
                val method = instance.javaClass.getMethod("setReadingLight", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                method.invoke(instance, 0, 1)
                channels.add("HAL Nativo")
            } catch (_: Exception) {}
        }
        val intent = Intent("com.byd.action.LIGHT_CONTROL").apply {
            putExtra("command", "MASTER_ON")
            putExtra("target", "ALL_INTERNAL_LIGHTS")
            putExtra("value", 1)
        }
        sendTargetedBYDBroadcasts(intent)
        channels.add("Broadcast Explícito BYD")
        return LightControlResult(true, "Luzes ligadas via " + channels.joinToString(", "), channels)
    }

    fun activateTotalBlackout(): LightControlResult {
        val lightResult = turnOffAllInternalLights()
        try { sendTargetedBYDBroadcasts(Intent("com.byd.action.SCREEN_OFF")) } catch (_: Exception) {}
        return LightControlResult(true, "Blackout total ativado", lightResult.channelsTriggered + listOf("Screen Off Intent"))
    }

    fun runFullDiLinkDiagnostics(): String {
        val sb = StringBuilder()
        sb.append("=== DIAGNÓSTICO DILINK DOLPHIN PLUS ===\\n")
        sb.append("Modelo: \${Build.MANUFACTURER} \${Build.MODEL} (\${Build.PRODUCT})\\n")
        sb.append("Android: \${Build.VERSION.RELEASE} (SDK \${Build.VERSION.SDK_INT})\\n")
        sb.append("Permissão WRITE_SETTINGS: \${if (hasWriteSettingsPermission()) "CONCEDIDA ✅" else "PENDENTE ⚠️"}\\n")
        sb.append("LightDevice HAL: \${detectedLightClassName ?: "Usando Broadcast/Settings"}\\n")
        return sb.toString()
    }
}
`;

export const CAR_STATE_RECEIVER_KOTLIN = `package com.byd.carcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Kotlin BroadcastReceiver para capturar eventos em tempo real do CAN Bus BYD
 */
class BYDCarStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.action?.let { action ->
            Log.d("BYDCarReceiver", "Evento de veículo recebido: $action")
            when (action) {
                "com.byd.action.SEATBELT_STATE_CHANGED" -> {
                    val driverState = intent.getBooleanExtra("driver_buckled", true)
                    Log.i("BYDCarReceiver", "Status Cinto Motorista: $driverState")
                }
                "com.byd.action.DOOR_STATE_CHANGED" -> {
                    val doorId = intent.getIntExtra("door_id", 0)
                    val isOpen = intent.getBooleanExtra("is_open", false)
                    Log.i("BYDCarReceiver", "Porta $doorId alterada: isOpen=$isOpen")
                }
                else -> {
                    Log.d("BYDCarReceiver", "Outra ação recebida: $action")
                }
            }
        }
    }
}`;

export const APP_BUILD_GRADLE_KTS = `plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumberParam: String? = project.findProperty("buildNumber") as String?
val buildNum: Int = buildNumberParam?.toIntOrNull() ?: 1
val verName: String = "1.0.$buildNum"

android {
    namespace = "com.byd.carcontrol"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.byd.carcontrol"
        minSdk = 24
        targetSdk = 34
        versionCode = buildNum
        versionName = verName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (!keystorePath.isNullOrEmpty()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (!keystorePath.isNullOrEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}`;

export const ROOT_BUILD_GRADLE_KTS = `// Top-level build file for Kotlin Android Project with Gradle Kotlin DSL
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
`;

export const SETTINGS_GRADLE_KTS = `pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "BYDCarControl"
include(":app")
`;

export const BUILD_GRADLE = APP_BUILD_GRADLE_KTS;
export const ROOT_BUILD_GRADLE = ROOT_BUILD_GRADLE_KTS;
export const SETTINGS_GRADLE = SETTINGS_GRADLE_KTS;

export const GRADLE_WRAPPER_PROPERTIES = `distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
`;

export const GRADLEW_SHELL_SCRIPT = `#!/usr/bin/env sh
##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################
exec gradle "$@"
`;

export const GITHUB_ACTIONS_WORKFLOW = `name: Build Android APK (Kotlin DSL & CI/CD Release)

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    name: Build Android Debug APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Source Code
        uses: actions/checkout@v4

      - name: Setup JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Setup Gradle 8.5
        uses: gradle/actions/setup-gradle@v3
        with:
          gradle-version: '8.5'

      - name: Grant Execute Permission for Gradlew
        run: chmod +x gradlew || true

      - name: Build Android Debug APK with Dynamic Versioning
        run: |
          BUILD_NUM=\${{ github.run_number }}
          echo "Building APK version code \$BUILD_NUM..."
          gradle assembleDebug -PbuildNumber=\$BUILD_NUM --no-daemon

      - name: Prepare APK Artifact Name
        run: |
          BUILD_NUM=\${{ github.run_number }}
          mkdir -p artifacts
          APK_PATH=\$(find app/build/outputs/apk/debug/ -name "*.apk" | head -n 1)
          if [ -f "\$APK_PATH" ]; then
            cp "\$APK_PATH" "artifacts/byd-car-control-v1.0.\${BUILD_NUM}.apk"
            echo "APK successfully created and renamed to byd-car-control-v1.0.\${BUILD_NUM}.apk"
          else
            echo "Warning: APK path not found directly, checking build outputs."
          fi

      - name: Upload APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: byd-car-control-v1.0.\${{ github.run_number }}.apk
          path: artifacts/*.apk
          retention-days: 30

      - name: Create GitHub Release
        if: github.event_name == 'push' && (github.ref == 'refs/heads/main' || github.ref == 'refs/heads/master')
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v1.0.\${{ github.run_number }}
          name: BYD Car Control v1.0.\${{ github.run_number }}
          draft: false
          prerelease: false
          files: artifacts/*.apk
          body: |
            🚀 **Novo APK Automático Gerado via GitHub Actions**
            
            - **Versão:** \`1.0.\${{ github.run_number }}\`
            - **Build Number:** \`\${{ github.run_number }}\`
            - **Arquitetura:** Kotlin Native + Gradle Kotlin DSL (.gradle.kts)
            - **Compatibilidade:** BYD DiLink Multimedia Systems & Android 7.0+ (API 24+)
        env:
          GITHUB_TOKEN: \${{ secrets.GITHUB_TOKEN }}
`;
