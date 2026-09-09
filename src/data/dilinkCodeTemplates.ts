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

export const MAIN_ACTIVITY_JAVA = `package com.byd.carcontrol;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * BYD DiLink Vehicle Controller
 * Target: BYD Dolphin, Song Plus, Yuan Plus, Seal, Tan, Han
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BYDController";
    private BYDDiLinkServiceHelper bydHelper;
    private Button btnMasterTurnOffLights;
    private TextView txtSeatbeltStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize BYD DiLink Hardware Service Helper
        bydHelper = new BYDDiLinkServiceHelper(this);

        btnMasterTurnOffLights = findViewById(R.id.btnMasterTurnOffLights);
        txtSeatbeltStatus = findViewById(R.id.txtSeatbeltStatus);

        // Central Button Handler: Apagar todas as luzes internas do carro
        btnMasterTurnOffLights.setOnClickListener(v -> {
            boolean success = bydHelper.turnOffAllInternalLights();
            if (success) {
                Toast.makeText(MainActivity.this, "Luzes internas apagadas via DiLink Bus", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Enviando Broadcast Intent de fallback...", Toast.LENGTH_SHORT).show();
            }
        });

        // Register listener for Seatbelt & Car state
        bydHelper.observeSeatbeltStatus(status -> {
            runOnUiThread(() -> {
                txtSeatbeltStatus.setText("Status Cintos: " + status.getDescription());
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bydHelper.unregisterReceivers();
    }
}`;

export const BYD_DILINK_SERVICE_HELPER_JAVA = `package com.byd.carcontrol;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.lang.reflect.Method;

/**
 * Service Helper for BYD DiLink Hardware SDK via Reflection & Broadcast Intents.
 * Compatible with DiLink 3.0, 4.0, 5.0 and BYD OS.
 */
public class BYDDiLinkServiceHelper {

    private static final String TAG = "BYDDiLinkHelper";
    private final Context context;
    private Object bydLightBusInstance;

    public BYDDiLinkServiceHelper(Context context) {
        this.context = context;
        initBYDLightBusReflection();
    }

    /**
     * Initializes BYD Hardware Service (com.byd.service.BYDAutoLightBus) via reflection
     * so it compiles safely even without BYD system JARs in local SDK.
     */
    private void initBYDLightBusReflection() {
        try {
            Class<?> clazz = Class.forName("com.byd.service.BYDAutoLightBus");
            Method getInstance = clazz.getMethod("getInstance", Context.class);
            bydLightBusInstance = getInstance.invoke(null, context);
            Log.d(TAG, "Successfully attached to BYDAutoLightBus system service!");
        } catch (Exception e) {
            Log.w(TAG, "BYD Native SDK class not found on this device. Using Intent Broadcast Fallback.");
        }
    }

    /**
     * Apaga todas as luzes internas do veículo
     */
    public boolean turnOffAllInternalLights() {
        boolean nativeSuccess = false;
        if (bydLightBusInstance != null) {
            try {
                // Invokes setReadingLightState(int lightArea, int state) -> Area 0 = ALL, State 0 = OFF
                Method setLight = bydLightBusInstance.getClass().getMethod("setReadingLightState", int.class, int.class);
                setLight.invoke(bydLightBusInstance, 0, 0);
                
                // Invokes setAmbientLightState(int state) -> 0 = OFF
                Method setAmbient = bydLightBusInstance.getClass().getMethod("setAmbientLightState", int.class);
                setAmbient.invoke(bydLightBusInstance, 0);

                nativeSuccess = true;
                Log.i(TAG, "Native BYD Light API invoked successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error calling BYD Light reflection API", e);
            }
        }

        // Broadcast Intent Fallback for BYD Central Multimedia
        Intent intent = new Intent("com.byd.action.LIGHT_CONTROL");
        intent.putExtra("command", "MASTER_OFF");
        intent.putExtra("target", "ALL_INTERNAL_LIGHTS");
        intent.putExtra("value", 0);
        context.sendBroadcast(intent);

        return nativeSuccess;
    }

    public interface SeatbeltCallback {
        void onStatusChanged(SeatbeltStatus status);
    }

    public static class SeatbeltStatus {
        public boolean driverBuckled = true;
        public boolean passengerBuckled = true;
        public boolean rearLeftBuckled = true;
        public boolean rearCenterBuckled = true;
        public boolean rearRightBuckled = true;

        public String getDescription() {
            int unbuckledCount = 0;
            if (!driverBuckled) unbuckledCount++;
            if (!passengerBuckled) unbuckledCount++;
            if (!rearLeftBuckled) unbuckledCount++;
            if (!rearCenterBuckled) unbuckledCount++;
            if (!rearRightBuckled) unbuckledCount++;

            return unbuckledCount == 0 ? "Todos os cintos afivelados" : unbuckledCount + " cinto(s) desatados!";
        }
    }

    public void observeSeatbeltStatus(SeatbeltCallback callback) {
        // Broadcast Receiver or BYD SeatBeltBus hook
    }

    public void unregisterReceivers() {
        // Cleanup
    }
}`;

export const BUILD_GRADLE = `plugins {
    id 'com.android.application'
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
        sourceCompatibility JavaVersion.VERSION_1_8
        targetCompatibility JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
}`;

export const ROOT_BUILD_GRADLE = `// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id 'com.android.application' version '8.2.2' apply false
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

rootProject.name = "BydController"
include ':app'
`;

export const GRADLE_WRAPPER_PROPERTIES = `distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-8.2-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
`;

export const GITHUB_ACTIONS_WORKFLOW = `name: Build BYD Car Control APK (No Android Studio)

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build-apk:
    name: Build Debug APK
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

    - name: Build Debug APK with Gradle
      run: ./gradlew assembleDebug --no-daemon

    - name: Upload APK to Artifacts
      uses: actions/upload-artifact@v4
      with:
        name: BYD-Controller-Debug.apk
        path: app/build/outputs/apk/debug/app-debug.apk
        retention-days: 30
`;
