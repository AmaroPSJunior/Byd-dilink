package com.byd.carcontrol.inspector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/** Collectors must only observe. They must not instantiate OEM device classes or invoke their methods. */
interface InspectorCollector {
    val id: String
    val label: String
    fun collect(context: Context): List<InspectorObservation>
}

class DiLinkApiInventoryCollector : InspectorCollector {
    override val id = "api_reflection"
    override val label = "Reflection de classes e assinaturas (sem invocar métodos)"

    private val candidates = listOf(
        "android.hardware.bydauto.light.BYDAutoLightDevice",
        "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice",
        "android.hardware.bydauto.setting.BYDAutoSettingDevice",
        "android.hardware.bydauto.aircondition.BYDAutoAirConditionDevice",
        "android.hardware.bydauto.door.BYDAutoDoorDevice",
        "android.hardware.bydauto.window.BYDAutoWindowDevice",
        "android.hardware.bydauto.speed.BYDAutoSpeedDevice",
        "android.hardware.bydauto.gearbox.BYDAutoGearboxDevice",
        "android.hardware.bydauto.panorama.BYDAutoPanoramaDevice",
        "android.hardware.bydauto.panorama.IBYDAutoPanoService",
        "android.hardware.bydauto.panorama.IBYDAutoPanoListener",
        "android.hardware.bydauto.IBYDAutoDevice",
        "com.byd.auto.light.BYDAutoLightDevice",
        "com.byd.auto.bodywork.BYDAutoBodyworkDevice",
        "com.byd.service.BYDAutoLightBus"
    )

    override fun collect(context: Context): List<InspectorObservation> = buildList {
        candidates.forEach { name ->
            try {
                val type = Class.forName(name, false, context.classLoader)
                add(InspectorObservation(
                    source = id,
                    eventType = "CLASS_DISCOVERED",
                    className = name,
                    value = "found",
                    metadata = mapOf(
                        "superclass" to type.superclass?.name,
                        "interfaces" to type.interfaces.map { it.name },
                        "classLoader" to type.classLoader?.javaClass?.name,
                        "initialization" to "not_requested"
                    )
                ))
                type.declaredMethods.forEach { method ->
                    val modifiers = Modifier.toString(method.modifiers)
                    val heuristic = when {
                        method.name.startsWith("get", true) || method.name.startsWith("is", true) || method.name.startsWith("has", true) || method.name.startsWith("query", true) -> "name_suggests_read_only_unverified"
                        method.name.startsWith("set", true) || method.name.startsWith("open", true) || method.name.startsWith("close", true) || method.name.startsWith("enable", true) || method.name.startsWith("disable", true) -> "possible_side_effect_not_invoked"
                        else -> "unknown_not_invoked"
                    }
                    val signature = "$modifiers ${method.returnType.name} ${method.name}(${method.parameterTypes.joinToString { it.name }})"
                    add(InspectorObservation(
                        source = id,
                        eventType = "API_METHOD_DISCOVERED",
                        className = name,
                        method = signature,
                        value = heuristic,
                        metadata = mapOf("declaringClass" to method.declaringClass.name, "executionStatus" to "NOT_INVOKED")
                    ))
                }
            } catch (t: Throwable) {
                add(InspectorObservation(
                    source = id,
                    eventType = if (t is ClassNotFoundException || t is NoClassDefFoundError) "CLASS_NOT_VISIBLE" else "CLASS_INSPECTION_ERROR",
                    className = name,
                    value = "${t.javaClass.simpleName}: ${t.message.orEmpty()}",
                    metadata = mapOf("exception" to t.javaClass.name)
                ))
            }
        }
    }
}

class DiLinkPermissionCollector : InspectorCollector {
    override val id = "permissions"
    override val label = "Permissões BYDAUTO declaradas e concedidas ao próprio APK"

    private val permissionNames = listOf(
        "android.permission.BYDAUTO_LIGHT_GET", "android.permission.BYDAUTO_LIGHT_SET",
        "android.permission.BYDAUTO_BODYWORK_GET", "android.permission.BYDAUTO_BODYWORK_SET",
        "android.permission.BYDAUTO_SPEED_GET", "android.permission.BYDAUTO_GEARBOX_GET",
        "android.permission.BYDAUTO_SETTING_GET", "android.permission.BYDAUTO_SETTING_SET",
        "android.permission.BYDAUTO_AC_GET", "android.permission.BYDAUTO_AC_SET",
        "android.permission.BYDAUTO_PANORAMA_GET", "android.permission.BYDAUTO_PANORAMA_SET",
        "android.permission.BYDAUTO_PANORAMA_COMMON", "android.permission.BYDDIAGNOSTIC_SEND_BUFFER",
        "android.permission.READ_LOGS", Manifest.permission.CAMERA
    )

    @Suppress("DEPRECATION")
    override fun collect(context: Context): List<InspectorObservation> {
        val requested = try {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        } catch (t: Throwable) {
            emptySet()
        }
        return permissionNames.map { name ->
            val granted = try {
                context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                false
            }
            val permissionInfo = try {
                context.packageManager.getPermissionInfo(name, 0)
            } catch (_: Throwable) {
                null
            }
            InspectorObservation(
                source = id,
                eventType = "PERMISSION_STATUS",
                value = "${if (name in requested) "requested" else "not_requested"};${if (granted) "granted" else "not_granted"}",
                property = name,
                permission = name,
                metadata = mapOf(
                    "requestedByThisApk" to (name in requested),
                    "grantedToThisUid" to granted,
                    "protectionLevelRaw" to permissionInfo?.protectionLevel,
                    "definitionVisible" to (permissionInfo != null)
                )
            )
        }
    }
}

class DiLinkSnapshotCollector : InspectorCollector {
    override val id = "runtime_snapshot"
    override val label = "Snapshot passivo de Binder conhecido e propriedades allowlist"

    private val serviceNames = listOf(
        "byd_car_service", "autoservice", "dicarserver", "cloudmanager", "acquisitionsrv",
        "bydcameramanager", "bmmcameraserver", "android.hardware.bydauto.panorama.IBYDAutoPanoService",
        "bydauto_light", "bydauto_ac", "bydauto_window", "bydauto_door", "media.camera"
    )
    private val propertyNames = listOf(
        "vehicle.config.cam_sort", "sys.byd.camera_open", "sys.byd.temp_orientation"
    )

    override fun collect(context: Context): List<InspectorObservation> = buildList {
        val getService = try {
            Class.forName("android.os.ServiceManager").getDeclaredMethod("getService", String::class.java).apply { isAccessible = true }
        } catch (t: Throwable) {
            add(InspectorObservation(id, "SOURCE_ERROR", value = "ServiceManager indisponível: ${t.javaClass.simpleName}: ${t.message}", metadata = mapOf("exception" to t.javaClass.name)))
            null
        }
        serviceNames.forEach { name ->
            try {
                val binder = getService?.invoke(null, name) as? IBinder
                if (binder == null) {
                    add(InspectorObservation(id, "SERVICE_SNAPSHOT", service = name, value = "absent", isSnapshot = true))
                } else {
                    val descriptor = try { binder.interfaceDescriptor } catch (t: Throwable) { "DENIED:${t.javaClass.simpleName}" }
                    add(InspectorObservation(
                        id, "SERVICE_SNAPSHOT", service = name,
                        value = "present;alive=${binder.isBinderAlive};descriptor=$descriptor",
                        metadata = mapOf("binderAlive" to binder.isBinderAlive, "descriptor" to descriptor, "operation" to "getService+interfaceDescriptor_only"),
                        isSnapshot = true
                    ))
                }
            } catch (t: Throwable) {
                add(InspectorObservation(id, "SERVICE_ACCESS_ERROR", service = name, value = "${t.javaClass.simpleName}: ${t.message}", metadata = mapOf("exception" to t.javaClass.name)))
            }
        }

        val sysProps = try {
            Class.forName("android.os.SystemProperties").getDeclaredMethod("get", String::class.java, String::class.java).apply { isAccessible = true }
        } catch (t: Throwable) {
            add(InspectorObservation(id, "SOURCE_ERROR", className = "android.os.SystemProperties", value = "${t.javaClass.simpleName}: ${t.message}", metadata = mapOf("exception" to t.javaClass.name)))
            null
        }
        propertyNames.forEach { name ->
            try {
                val value = sysProps?.invoke(null, name, "")?.toString()
                add(InspectorObservation(id, "PROPERTY_SNAPSHOT", property = name, value = value ?: "unavailable", metadata = mapOf("access" to if (sysProps == null) "denied_or_hidden_api" else "allowlist_read"), isSnapshot = true))
            } catch (t: Throwable) {
                add(InspectorObservation(id, "PROPERTY_ACCESS_ERROR", property = name, value = "${t.javaClass.simpleName}: ${t.message}", metadata = mapOf("exception" to t.javaClass.name)))
            }
        }

        val sensors = try {
            val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            manager.getSensorList(Sensor.TYPE_ALL).filter { it.type in OBSERVED_SENSOR_TYPES }
                .map { mapOf("name" to it.name, "vendor" to it.vendor, "type" to it.type, "version" to it.version) }
        } catch (t: Throwable) {
            add(InspectorObservation(id, "SENSOR_ENUMERATION_ERROR", value = "${t.javaClass.simpleName}: ${t.message}"))
            emptyList()
        }
        add(InspectorObservation(id, "ANDROID_SENSOR_INVENTORY", value = "${sensors.size} motion sensor(s)", metadata = mapOf("sensors" to sensors), isSnapshot = true))
    }

    companion object {
        val OBSERVED_SENSOR_TYPES = setOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ROTATION_VECTOR)
    }
}

class DiLinkDeviceMetadataCollector : InspectorCollector {
    override val id = "device_metadata"
    override val label = "Versão de build e dispositivo"
    override fun collect(context: Context): List<InspectorObservation> {
        @Suppress("DEPRECATION")
        val appVersion = try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (_: Throwable) { null }
        return listOf(InspectorObservation(
            source = id,
            eventType = "DEVICE_CONTEXT",
            value = "${Build.MANUFACTURER}/${Build.MODEL}; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            metadata = mapOf(
                "manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL, "device" to Build.DEVICE,
                "product" to Build.PRODUCT, "release" to Build.VERSION.RELEASE, "sdk" to Build.VERSION.SDK_INT,
                "fingerprint" to Build.FINGERPRINT, "appPackage" to context.packageName, "appVersion" to appVersion,
                "uid" to android.os.Process.myUid()
            )
        ))
    }
}

class MotionSensorStream(private val context: Context, private val emit: (InspectorObservation) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lastEmittedAt = ConcurrentHashMap<Int, Long>()
    private val previous = ConcurrentHashMap<Int, String>()
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        DiLinkSnapshotCollector.OBSERVED_SENSOR_TYPES.forEach { type ->
            manager.getDefaultSensor(type)?.let { sensor ->
                val registered = try { manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) } catch (_: Throwable) { false }
                if (!registered) emit(InspectorObservation("android.sensor", "SENSOR_LISTENER_UNAVAILABLE", value = sensor.name, metadata = mapOf("sensorType" to sensor.type)))
            }
        }
    }

    fun stop() {
        running = false
        try { manager.unregisterListener(this) } catch (_: Throwable) { }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!running) return
        val now = System.currentTimeMillis()
        val previousTime = lastEmittedAt[event.sensor.type] ?: 0L
        if (now - previousTime < 1000L) return
        val value = event.values.joinToString(",") { "%.4f".format(java.util.Locale.US, it) }
        val old = previous.put(event.sensor.type, value)
        lastEmittedAt[event.sensor.type] = now
        if (old == null || old != value) emit(InspectorObservation(
            source = "android.sensor",
            eventType = "SENSOR_SAMPLE",
            value = value,
            className = event.sensor.javaClass.name,
            property = "${event.sensor.name}[type=${event.sensor.type}]",
            metadata = mapOf("accuracy" to event.accuracy, "sensorTimestampNanos" to event.timestamp, "wallTimestamp" to now)
        ))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor != null) emit(InspectorObservation("android.sensor", "SENSOR_ACCURACY_CHANGED", value = accuracy.toString(), property = sensor.name, metadata = mapOf("sensorType" to sensor.type)))
    }
}

/** Best-effort filtered logcat stream; Android normally restricts an ordinary app to its own logs. */
class FilteredLogcatStream(private val emit: (InspectorObservation) -> Unit) {
    @Volatile private var process: Process? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        Thread({
            try {
                val child = ProcessBuilder(
                    "logcat", "-v", "epoch", "-T", "1",
                    "BYD:V", "DiLink:V", "DiCar:V", "Vehicle:V", "MagicCore:V", "MagicManager:V", "*:S"
                ).redirectErrorStream(true).start()
                process = child
                BufferedReader(InputStreamReader(child.inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        if (!running) return@forEach
                        if (line.contains("not permitted", true) || line.contains("permission denied", true) || line.contains("Operation not permitted", true)) {
                            emit(InspectorObservation("android.logcat", "SOURCE_LIMITED", value = line.take(500), permission = Manifest.permission.READ_LOGS))
                        } else if (line.isNotBlank()) {
                            emit(InspectorObservation("android.logcat", "LOGCAT_LINE", value = line.take(1200), property = "filtered_line"))
                        }
                    }
                }
                if (running) emit(InspectorObservation("android.logcat", "SOURCE_ENDED", value = "logcat process finished with ${child.exitValue()}"))
            } catch (t: Throwable) {
                if (running) emit(InspectorObservation("android.logcat", "SOURCE_LIMITED", value = "${t.javaClass.simpleName}: ${t.message}", permission = Manifest.permission.READ_LOGS, metadata = mapOf("exception" to t.javaClass.name)))
            } finally {
                process = null
            }
        }, "DiLinkInspector-logcat").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        try { process?.destroy() } catch (_: Throwable) { }
        process = null
    }
}

object InspectorEventBridge {
    @Volatile private var sink: ((InspectorObservation) -> Unit)? = null
    fun attach(listener: (InspectorObservation) -> Unit) { sink = listener }
    fun detach() { sink = null }
    fun publish(observation: InspectorObservation) { try { sink?.invoke(observation) } catch (t: Throwable) { Log.w("DiLinkInspector", "Broadcast event was not recorded", t) } }
}

fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
