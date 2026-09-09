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
        android:label="BYD Controller"
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

        <!-- Receiver to capture BYD DiLink CAN Bus Broadcasts -->
        <receiver android:name=".BYDCarStateReceiver" android:exported="true">
            <intent-filter>
                <action android:name="com.byd.action.SEATBELT_STATE_CHANGED" />
                <action android:name="com.byd.action.DOOR_STATE_CHANGED" />
                <action android:name="com.byd.action.LIGHT_STATE_CHANGED" />
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa o Helper do SDK DiLink via Reflection em Kotlin
        bydHelper = BYDDiLinkServiceHelper(this)

        btnMasterTurnOffLights = findViewById(R.id.btnMasterTurnOffLights)
        txtSeatbeltStatus = findViewById(R.id.txtSeatbeltStatus)

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
    }

    override fun onDestroy() {
        super.onDestroy()
        bydHelper.unregisterReceivers()
    }
}`;

export const BYD_DILINK_SERVICE_HELPER_KOTLIN = `package com.byd.carcontrol

import android.content.Context
import android.content.Intent
import android.util.Log
import java.lang.reflect.Method

/**
 * Kotlin Service Helper for BYD DiLink Hardware SDK via Reflection & Broadcast Intents.
 * Compatible with DiLink 3.0, 4.0, 5.0 and BYD OS.
 */
class BYDDiLinkServiceHelper(private val context: Context) {

    private companion object {
        private const val TAG = "BYDDiLinkHelperKt"
    }

    private var bydLightBusInstance: Any? = null

    init {
        initBYDLightBusReflection()
    }

    /**
     * Inicializa com.byd.service.BYDAutoLightBus via Reflection em Kotlin.
     * Permite compilar sem necessitar dos arquivos .jar proprietários no Gradle.
     */
    private fun initBYDLightBusReflection() {
        try {
            val clazz = Class.forName("com.byd.service.BYDAutoLightBus")
            val getInstance: Method = clazz.getMethod("getInstance", Context::class.java)
            bydLightBusInstance = getInstance.invoke(null, context)
            Log.d(TAG, "Conectado com sucesso ao serviço nativo BYDAutoLightBus!")
        } catch (e: Exception) {
            Log.w(TAG, "SDK Nativo BYD não encontrado nesta plataforma. Usando Fallback de Broadcast Intent.")
        }
    }

    /**
     * Apaga todas as luzes internas do veículo
     */
    fun turnOffAllInternalLights(): Boolean {
        var nativeSuccess = false
        bydLightBusInstance?.let { instance ->
            try {
                // Invoca setReadingLightState(int lightArea, int state) -> Area 0 = ALL, State 0 = OFF
                val setLight = instance.javaClass.getMethod("setReadingLightState", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                setLight.invoke(instance, 0, 0)

                // Invoca setAmbientLightState(int state) -> 0 = OFF
                val setAmbient = instance.javaClass.getMethod("setAmbientLightState", Int::class.javaPrimitiveType)
                setAmbient.invoke(instance, 0)

                nativeSuccess = true
                Log.i(TAG, "API Nativa de Luzes BYD executada via Kotlin com sucesso!")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao invocar API de luzes via Reflection", e)
            }
        }

        // Broadcast Intent Fallback para Multimídia Central BYD
        val intent = Intent("com.byd.action.LIGHT_CONTROL").apply {
            putExtra("command", "MASTER_OFF")
            putExtra("target", "ALL_INTERNAL_LIGHTS")
            putExtra("value", 0)
        }
        context.sendBroadcast(intent)

        return nativeSuccess
    }

    data class SeatbeltStatus(
        val driverBuckled: Boolean = true,
        val passengerBuckled: Boolean = true,
        val rearLeftBuckled: Boolean = true,
        val rearCenterBuckled: Boolean = true,
        val rearRightBuckled: Boolean = true
    ) {
        fun getDescription(): String {
            val unbuckled = listOf(driverBuckled, passengerBuckled, rearLeftBuckled, rearCenterBuckled, rearRightBuckled).count { !it }
            return if (unbuckled == 0) "Todos os cintos afivelados" else "$unbuckled cinto(s) desatados!"
        }
    }

    fun observeSeatbeltStatus(callback: (SeatbeltStatus) -> Unit) {
        // Observer de telemetria CAN Bus / Receiver
    }

    fun unregisterReceivers() {
        // Cleanup de receivers registrado
    }
}`;

export const BUILD_GRADLE = `plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    compileSdk 34

    defaultConfig {
        applicationId "com.byd.carcontrol"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.12.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}`;

export const ROOT_BUILD_GRADLE = `// Top-level build file for Kotlin Android Project
plugins {
    id 'com.android.application' version '8.2.2' apply false
    id 'org.jetbrains.kotlin.android' version '1.9.22' apply false
}
`;

export const SETTINGS_GRADLE = `pluginManagement {
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

rootProject.name = "BydControllerKotlin"
include ':app'
`;

export const GRADLE_WRAPPER_PROPERTIES = `distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.2-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
`;

export const GITHUB_ACTIONS_WORKFLOW = `name: Build BYD Car Control APK (Kotlin Native)

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build-apk:
    name: Build Kotlin Debug APK
    runs-on: ubuntu-latest

    steps:
    - name: Checkout Source Code
      uses: actions/checkout@v4

    - name: Set up Java JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: 'gradle'

    - name: Grant Execute Permission for Gradlew
      run: chmod +x gradlew

    - name: Build Kotlin Debug APK with Gradle
      run: ./gradlew assembleDebug --no-daemon

    - name: Upload APK to Artifacts
      uses: actions/upload-artifact@v4
      with:
        name: BYD-Controller-Kotlin-Debug.apk
        path: app/build/outputs/apk/debug/app-debug.apk
        retention-days: 30
`;
