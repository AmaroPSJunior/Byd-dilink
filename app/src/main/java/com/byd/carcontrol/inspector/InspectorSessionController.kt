package com.byd.carcontrol.inspector

import android.content.Context
import android.os.Build
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class InspectorSessionController(private val context: Context) {
    private val storage = InspectorSessionStorage(context)
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "DiLinkInspector-collector") }
    private val stateValues = mutableMapOf<String, String?>()
    private val repeatCounts = mutableMapOf<String, Int>()
    private val errorTimes = ConcurrentHashMap<String, Long>()
    private val recentEvents = ArrayDeque<InspectorEvent>()
    private val markers = mutableListOf<ActionMarker>()
    private val errors = mutableListOf<Map<String, Any?>>()
    private var activeFiles: ActiveInspectorFiles? = null
    private var activeMode: InspectorMode? = null
    private var startedAt: Long = 0L
    private var pollTask: ScheduledFuture<*>? = null
    private var lightPollTask: ScheduledFuture<*>? = null
    private var sensors: MotionSensorStream? = null
    private var logcat: FilteredLogcatStream? = null
    private var deviceMetadata: Map<String, Any?> = emptyMap()
    private var sourceDescriptions = emptyList<Map<String, Any?>>()
    @Volatile private var eventCount = 0L

    data class ActionMarker(val id: String, val description: String, val timestamp: Long, val elapsedRealtime: Long)

    @Synchronized
    fun start(mode: InspectorMode): InspectorUiState {
        check(activeFiles == null) { "Já existe uma sessão ativa" }
        val now = System.currentTimeMillis()
        val id = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(now))
        val files = storage.create(id)
        activeFiles = files
        activeMode = mode
        startedAt = now
        eventCount = 0
        stateValues.clear()
        repeatCounts.clear()
        errorTimes.clear()
        recentEvents.clear()
        markers.clear()
        errors.clear()
        deviceMetadata = emptyMap()

        val collectors = listOf(
            DiLinkDeviceMetadataCollector(), DiLinkApiInventoryCollector(),
            DiLinkPermissionCollector(), DiLinkSnapshotCollector()
        )
        sourceDescriptions = collectors.map { mapOf("id" to it.id, "label" to it.label, "status" to "pending") } + listOf(
            mapOf("id" to "byd.interior_light_state", "label" to "Getters BYDAuto conhecidos de iluminação, leitura a cada 2 s", "status" to "pending"),
            mapOf("id" to "vehicle_broadcasts", "label" to "Receiver manifest: 4 ações BYD declaradas", "status" to "armed_when_session_active"),
            mapOf("id" to "android.sensor", "label" to "SensorManager: motion sensors allowlisted", "status" to "pending"),
            mapOf("id" to "android.logcat", "label" to "logcat filtrado; acesso depende de READ_LOGS", "status" to "best_effort")
        )

        val metadata = baseMetadata(files, endedAt = null)
        storage.writeMetadata(files, metadata)
        InspectorEventBridge.attach { observation -> onObservation(observation) }

        updateServiceState()
        executor.execute {
            collectors.take(3).forEach { collector ->
                try {
                    val observations = collector.collect(context)
                    if (collector is DiLinkDeviceMetadataCollector) {
                        deviceMetadata = observations.firstOrNull()?.metadata.orEmpty()
                    }
                    observations.forEach(::recordObservation)
                    markSource(collector.id, if (observations.isEmpty()) "no_results" else "observed")
                } catch (t: Throwable) {
                    recordSourceError(collector.id, t)
                }
            }
            pollTask = executor.scheduleWithFixedDelay({ pollSnapshot(collectors[3]) }, 0, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)
            val lightCollector = InteriorLightStateCollector()
            lightPollTask = executor.scheduleWithFixedDelay({ pollCollector(lightCollector) }, 0, LIGHT_POLL_INTERVAL_SECONDS, TimeUnit.SECONDS)
            try {
                sensors = MotionSensorStream(context, ::onObservation).also { it.start() }
                markSource("android.sensor", "listener_registered_where_available")
            } catch (t: Throwable) {
                recordSourceError("android.sensor", t)
            }
            try {
                logcat = FilteredLogcatStream(::onObservation).also { it.start() }
            } catch (t: Throwable) {
                recordSourceError("android.logcat", t)
            }
            recordObservation(InspectorObservation(
                source = "inspector", eventType = "SESSION_STARTED", value = mode.wireName,
                metadata = mapOf("pollIntervalSeconds" to POLL_INTERVAL_SECONDS, "actionWindowBeforeMs" to ACTION_BEFORE_MS, "actionWindowAfterMs" to ACTION_AFTER_MS)
            ))
            updateMetadata()
            updateServiceState()
        }
        return currentState()
    }

    fun markAction(description: String, preciseTimestamp: Long = System.currentTimeMillis(), elapsed: Long = SystemClock.elapsedRealtime()) {
        val clean = description.trim().take(240)
        if (clean.isEmpty()) return
        executor.execute {
            val files = activeFiles ?: return@execute
            if (activeMode != InspectorMode.EXPERIMENT) return@execute
            val marker = ActionMarker("mark-${preciseTimestamp}-${markers.size + 1}", clean, preciseTimestamp, elapsed)
            markers.add(marker)
            val event = InspectorEvent(
                timestamp = preciseTimestamp,
                sessionId = files.sessionId,
                mode = activeMode!!.wireName,
                eventType = "ACTION_MARKER",
                source = "user_action",
                method = "MARCAR AÇÃO",
                rawValue = clean,
                actionMarker = marker.id,
                timeFromMarker = 0,
                metadata = mapOf("markerId" to marker.id, "elapsedRealtimeMs" to elapsed, "beforeWindowMs" to ACTION_BEFORE_MS, "afterWindowMs" to ACTION_AFTER_MS)
            )
            writeEvent(event)
            val fromBeforeWindow = recentEvents.filter { it.eventType !in setOf("ACTION_MARKER", "CORRELATION") && marker.timestamp - it.timestamp in 0..ACTION_BEFORE_MS }
            fromBeforeWindow.forEach { related -> writeCorrelation(marker, related, "before_action_window") }
            updateMetadata()
            updateServiceState()
        }
    }

    fun stop(reason: String = "user_stopped", onStopped: (() -> Unit)? = null) {
        executor.execute {
            val files = activeFiles ?: run { onStopped?.invoke(); return@execute }
            pollTask?.cancel(true)
            pollTask = null
            lightPollTask?.cancel(true)
            lightPollTask = null
            sensors?.stop()
            sensors = null
            logcat?.stop()
            logcat = null
            InspectorEventBridge.detach()
            recordObservation(InspectorObservation("inspector", "SESSION_FINISHED", value = reason))
            updateMetadata(endedAt = System.currentTimeMillis())
            activeFiles = null
            activeMode = null
            updateServiceState()
            executor.shutdown()
            onStopped?.invoke()
        }
    }

    @Synchronized
    fun currentState(): InspectorUiState {
        val files = activeFiles
        return InspectorUiState(
            active = files != null,
            sessionId = files?.sessionId,
            mode = activeMode?.wireName,
            startedAt = startedAt.takeIf { files != null },
            eventCount = eventCount,
            storageLocation = files?.displayLocation.orEmpty(),
            lastError = files?.lastStorageError
        )
    }

    private fun pollCollector(collector: InspectorCollector) {
        if (activeFiles == null) return
        try {
            collector.collect(context).forEach(::recordObservation)
            markSource(collector.id, "polling_every_${LIGHT_POLL_INTERVAL_SECONDS}s")
        } catch (t: Throwable) {
            recordSourceError(collector.id, t)
        }
        updateServiceState()
    }

    private fun pollSnapshot(collector: InspectorCollector) {
        if (activeFiles == null) return
        try {
            collector.collect(context).forEach(::recordObservation)
            markSource(collector.id, "polling_every_${POLL_INTERVAL_SECONDS}s")
        } catch (t: Throwable) {
            recordSourceError(collector.id, t)
        }
        activeFiles?.lastStorageError?.let { error ->
            addError("storage", IllegalStateException(error))
            updateMetadata()
        }
        updateServiceState()
    }

    private fun onObservation(observation: InspectorObservation) {
        if (activeFiles == null) return
        try { executor.execute { if (activeFiles != null) recordObservation(observation) } } catch (_: Throwable) { }
    }

    private fun recordObservation(observation: InspectorObservation) {
        val files = activeFiles ?: return
        val now = System.currentTimeMillis()
        val trackedState = observation.isSnapshot || observation.eventType == "SENSOR_SAMPLE"
        val old = if (trackedState) stateValues.put(observation.stateKey, observation.value) else null
        if (trackedState && stateValues.containsKey(observation.stateKey) && old == observation.value && old != null) return
        val eventType = when {
            trackedState && old == null -> if (observation.eventType == "SENSOR_SAMPLE") "SENSOR_SAMPLE" else "STATE_OBSERVED"
            trackedState && old != observation.value -> if (observation.eventType == "SENSOR_SAMPLE") "SENSOR_SAMPLE" else "STATE_CHANGED"
            else -> observation.eventType
        }
        if (eventType.endsWith("ERROR") || eventType == "SOURCE_LIMITED" || eventType == "SERVICE_ACCESS_ERROR" || eventType == "PROPERTY_ACCESS_ERROR") {
            val errorKey = "${observation.source}|${observation.property}|${observation.service}|${observation.value}"
            val last = errorTimes.put(errorKey, now)
            if (last != null && now - last < ERROR_REPEAT_INTERVAL_MS) return
            addError(observation.source, IllegalStateException(observation.value.orEmpty()), observation.permission)
        }
        val duplicateKey = "${observation.stateKey}|${old.orEmpty()}|${observation.value.orEmpty()}"
        val repeat = (repeatCounts[duplicateKey] ?: 0) + 1
        repeatCounts[duplicateKey] = repeat
        val nearestMarker = markers
            .filter { now - it.timestamp in -ACTION_BEFORE_MS..ACTION_AFTER_MS }
            .minByOrNull { kotlin.math.abs(now - it.timestamp) }
        val metadata = observation.metadata + mapOf("repeatCount" to repeat, "correlationIsTemporalOnly" to (nearestMarker != null))
        val event = InspectorEvent(
            timestamp = now,
            sessionId = files.sessionId,
            mode = activeMode?.wireName ?: "unknown",
            eventType = eventType,
            source = observation.source,
            service = observation.service,
            className = observation.className,
            method = observation.method,
            property = observation.property,
            oldValue = if (trackedState && old != null) old else null,
            newValue = if (trackedState) observation.value else null,
            rawValue = observation.value,
            permission = observation.permission,
            actionMarker = nearestMarker?.id,
            timeFromMarker = nearestMarker?.let { now - it.timestamp },
            metadata = metadata
        )
        writeEvent(event)
        if (nearestMarker != null && eventType !in setOf("ACTION_MARKER", "CORRELATION")) {
            writeCorrelation(nearestMarker, event, "within_action_window")
        }
        updateServiceState()
    }

    private fun writeCorrelation(marker: ActionMarker, event: InspectorEvent, relation: String) {
        val files = activeFiles ?: return
        writeEvent(InspectorEvent(
            timestamp = System.currentTimeMillis(),
            sessionId = files.sessionId,
            mode = activeMode?.wireName ?: "unknown",
            eventType = "CORRELATION",
            source = "temporal_correlation",
            service = event.service,
            className = event.className,
            method = event.method,
            property = event.property,
            oldValue = event.oldValue,
            newValue = event.newValue,
            rawValue = event.rawValue,
            actionMarker = marker.id,
            timeFromMarker = event.timestamp - marker.timestamp,
            metadata = mapOf(
                "markerDescription" to marker.description,
                "relatedEventTimestamp" to event.timestamp,
                "relation" to relation,
                "correlationType" to "time_proximity_only",
                "causalityConfirmed" to false
            )
        ))
    }

    private fun writeEvent(event: InspectorEvent) {
        val files = activeFiles ?: return
        try {
            storage.appendEvent(files, event.toJson().toString())
            eventCount++
            recentEvents.addLast(event)
            while (recentEvents.isNotEmpty() && event.timestamp - recentEvents.first().timestamp > RECENT_EVENT_RETENTION_MS) recentEvents.removeFirst()
        } catch (t: Throwable) {
            addError("storage", t)
            updateMetadata()
        }
    }

    private fun recordSourceError(source: String, t: Throwable) {
        addError(source, t)
        recordObservation(InspectorObservation(source, "SOURCE_ERROR", value = "${t.javaClass.simpleName}: ${t.message}", metadata = mapOf("exception" to t.javaClass.name)))
    }

    private fun addError(source: String, t: Throwable, permission: String? = null) {
        val item = mapOf("timestamp" to System.currentTimeMillis(), "source" to source, "exception" to t.javaClass.name, "message" to t.message, "permission" to permission)
        if (errors.size < 500) errors.add(item)
    }

    private fun markSource(id: String, status: String) {
        val changed = sourceDescriptions.any { it["id"] == id && it["status"] != status }
        if (!changed) return
        sourceDescriptions = sourceDescriptions.map { if (it["id"] == id) it + mapOf("status" to status) else it }
        updateMetadata()
    }

    private fun updateMetadata(endedAt: Long? = null) {
        val files = activeFiles ?: return
        val metadata = InspectorSessionMetadata(
            sessionId = files.sessionId,
            mode = activeMode?.wireName ?: files.metadataJson.optString("mode", "unknown"),
            startedAt = startedAt,
            endedAt = endedAt,
            device = deviceMetadata.ifEmpty { mapOf("manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL, "device" to Build.DEVICE, "release" to Build.VERSION.RELEASE, "sdk" to Build.VERSION.SDK_INT, "fingerprint" to Build.FINGERPRINT, "appPackage" to context.packageName, "uid" to android.os.Process.myUid()) },
            sources = sourceDescriptions,
            storage = mapOf("mode" to files.storageMode, "location" to files.displayLocation, "publicPath" to if (files.storageMode == "mediastore_downloads") files.displayLocation else null, "verifiedWritableAtStart" to true, "format" to "JSONL events + JSON metadata", "lastError" to files.lastStorageError),
            errors = errors.toList()
        )
        try { storage.writeMetadata(files, metadata.toJson()) } catch (t: Throwable) { addError("storage.metadata", t) }
    }

    private fun baseMetadata(files: ActiveInspectorFiles, endedAt: Long?): org.json.JSONObject = InspectorSessionMetadata(
        sessionId = files.sessionId,
        mode = activeMode?.wireName ?: "unknown",
        startedAt = startedAt,
        endedAt = endedAt,
        device = mapOf("manufacturer" to Build.MANUFACTURER, "model" to Build.MODEL, "device" to Build.DEVICE, "release" to Build.VERSION.RELEASE, "sdk" to Build.VERSION.SDK_INT, "fingerprint" to Build.FINGERPRINT, "appPackage" to context.packageName, "uid" to android.os.Process.myUid()),
        sources = sourceDescriptions,
        storage = mapOf("mode" to files.storageMode, "location" to files.displayLocation, "publicPath" to if (files.storageMode == "mediastore_downloads") files.displayLocation else null, "verifiedWritableAtStart" to true),
        errors = emptyList()
    ).toJson()

    private fun updateServiceState() {
        DiLinkInspectorService.uiState = currentState()
        try { context.sendBroadcast(android.content.Intent(DiLinkInspectorService.ACTION_STATE).setPackage(context.packageName)) } catch (_: Throwable) { }
    }

    companion object {
        private const val POLL_INTERVAL_SECONDS = 5L
        private const val LIGHT_POLL_INTERVAL_SECONDS = 2L
        private const val ACTION_BEFORE_MS = 20_000L
        private const val ACTION_AFTER_MS = 30_000L
        private const val RECENT_EVENT_RETENTION_MS = 120_000L
        private const val ERROR_REPEAT_INTERVAL_MS = 60_000L
    }
}
