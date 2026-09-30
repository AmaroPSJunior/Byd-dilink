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
    private val systemSurfaceKeys = mutableSetOf<String>()
    private val repeatCounts = mutableMapOf<String, Int>()
    private val errorTimes = ConcurrentHashMap<String, Long>()
    private val recentEvents = ArrayDeque<InspectorEvent>()
    private val markers = mutableListOf<ActionMarker>()
    private val errors = mutableListOf<Map<String, Any?>>()
    private var omittedErrorSummaries = 0
    private var activeFiles: ActiveInspectorFiles? = null
    private var activeMode: InspectorMode? = null
    private var startedAt: Long = 0L
    @Volatile private var ready = false
    private var intensity: String = "NORMAL"
    private var pollTask: ScheduledFuture<*>? = null
    private var lightPollTask: ScheduledFuture<*>? = null
    private var sensors: MotionSensorStream? = null
    private var logcat: FilteredLogcatStream? = null
    private var deviceMetadata: Map<String, Any?> = emptyMap()
    private var sourceDescriptions = emptyList<Map<String, Any?>>()
    @Volatile private var eventCount = 0L

    data class ActionMarker(val id: String, val description: String, val timestamp: Long, val elapsedRealtime: Long, val elapsedRealtimeNanos: Long)

    @Synchronized
    fun start(mode: InspectorMode, intensity: String = "NORMAL"): InspectorUiState {
        check(activeFiles == null) { "Já existe uma sessão ativa" }
        val now = System.currentTimeMillis()
        val id = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(now))
        val files = storage.create(id)
        activeFiles = files
        activeMode = mode
        this.intensity = intensity.uppercase(Locale.ROOT).takeIf { it in setOf("LOW", "NORMAL", "DEEP") } ?: "NORMAL"
        startedAt = now
        ready = false
        eventCount = 0
        stateValues.clear()
        systemSurfaceKeys.clear()
        repeatCounts.clear()
        errorTimes.clear()
        recentEvents.clear()
        markers.clear()
        errors.clear()
        omittedErrorSummaries = 0
        deviceMetadata = emptyMap()

        val collectors = listOf(
            DiLinkDeviceMetadataCollector(), DiLinkApiInventoryCollector(),
            DiLinkPermissionCollector(), DiLinkSnapshotCollector()
        )
        sourceDescriptions = collectors.map { mapOf("id" to it.id, "label" to it.label, "status" to "pending") } + listOf(
            mapOf("id" to "byd.interior_light_state", "label" to "Getters BYDAuto conhecidos de iluminação, leitura a cada ${lightPollSeconds()} s", "status" to "pending"),
            mapOf("id" to "vehicle_broadcasts", "label" to "Receiver manifest: 4 ações BYD declaradas", "status" to "armed_when_session_active"),
            mapOf("id" to "android.sensor", "label" to "SensorManager: inventário completo + fluxo allowlist (movimento, luz e ambiente)", "status" to "pending"),
            mapOf("id" to "android.logcat", "label" to "logcat buffer all sem filtro de conteúdo; acesso depende de READ_LOGS", "status" to "best_effort"),
            mapOf("id" to "android.system_surface", "label" to "Settings, getprop, service/dumpsys/lshal, packages/components e inventário /dev /sys /proc", "status" to "pending")
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
            try {
                collectors[3].collect(context).forEach(::recordObservation)
                markSource(collectors[3].id, "initial_snapshot_complete")
            } catch (t: Throwable) { recordSourceError(collectors[3].id, t) }
            pollTask = executor.scheduleWithFixedDelay({ pollSnapshot(collectors[3]) }, snapshotPollSeconds(), snapshotPollSeconds(), TimeUnit.SECONDS)
            val systemCollector = InspectorSystemSurfaceCollector()
            try { collectSystemSurfaceSnapshot(systemCollector, includeClassDiscovery = true, includeExpensiveCommands = true); markSource(systemCollector.id, "readable_surfaces_recorded; inaccessible results are retained") }
            catch (t: Throwable) { recordSourceError(systemCollector.id, t) }
            executor.scheduleWithFixedDelay({ pollSystemSurface(systemCollector) }, broadPollSeconds(), broadPollSeconds(), TimeUnit.SECONDS)
            val lightCollector = InteriorLightStateCollector()
            lightPollTask = executor.scheduleWithFixedDelay({ pollCollector(lightCollector) }, lightPollSeconds(), lightPollSeconds(), TimeUnit.SECONDS)
            try {
                sensors = MotionSensorStream(context, ::onObservation, emitUnchangedSamples = mode == InspectorMode.EXPERIMENT, sampleIntervalMs = sensorPollMillis()).also { it.start() }
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
                metadata = mapOf("intensity" to intensity, "snapshotPollIntervalSeconds" to snapshotPollSeconds(), "systemSurfacePollIntervalSeconds" to broadPollSeconds(), "lightPollIntervalSeconds" to lightPollSeconds(), "actionWindowBeforeMs" to ACTION_BEFORE_MS, "actionWindowAfterMs" to ACTION_AFTER_MS)
            ))
            updateMetadata()
            ready = true
            updateServiceState()
        }
        return currentState()
    }

    fun markAction(description: String, preciseTimestamp: Long = System.currentTimeMillis(), elapsed: Long = SystemClock.elapsedRealtime(), elapsedNanos: Long = SystemClock.elapsedRealtimeNanos()) {
        val clean = description.trim().take(240)
        if (clean.isEmpty()) return
        executor.execute {
            val files = activeFiles ?: return@execute
            if (activeMode != InspectorMode.EXPERIMENT) return@execute
            val marker = ActionMarker("mark-${preciseTimestamp}-${markers.size + 1}", clean, preciseTimestamp, elapsed, elapsedNanos)
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
                metadata = mapOf("markerId" to marker.id, "elapsedRealtimeMs" to elapsed, "elapsedRealtimeNanos" to elapsedNanos, "beforeWindowMs" to ACTION_BEFORE_MS, "afterWindowMs" to ACTION_AFTER_MS)
            )
            writeEvent(event)
            val fromBeforeWindow = recentEvents.filter { it.eventType !in setOf("ACTION_MARKER", "CORRELATION") && marker.timestamp - it.timestamp in 0..ACTION_BEFORE_MS }
            fromBeforeWindow.forEach { related -> writeCorrelation(marker, related, "before_action_window") }
            collectKnownStateSnapshot()
            try { collectSystemSurfaceSnapshot(InspectorSystemSurfaceCollector(), includeClassDiscovery = false, includeExpensiveCommands = true) } catch (t: Throwable) { recordSourceError("android.system_surface.marker_snapshot", t) }
            updateMetadata()
            updateServiceState()
        }
    }

    fun stop(reason: String = "user_stopped", onStopped: (() -> Unit)? = null) {
        executor.execute {
            val files = activeFiles ?: run { onStopped?.invoke(); return@execute }
            collectKnownStateSnapshot()
            try { collectSystemSurfaceSnapshot(InspectorSystemSurfaceCollector(), includeClassDiscovery = false, includeExpensiveCommands = true) } catch (t: Throwable) { recordSourceError("android.system_surface.final_snapshot", t) }
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
            ready = files != null && ready,
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
            markSource(collector.id, "polling_every_${lightPollSeconds()}s")
        } catch (t: Throwable) {
            recordSourceError(collector.id, t)
        }
        updateServiceState()
    }

    private fun collectKnownStateSnapshot() {
        listOf(DiLinkSnapshotCollector(), InteriorLightStateCollector()).forEach { collector ->
            try { collector.collect(context).forEach(::recordObservation) }
            catch (t: Throwable) { recordSourceError("${collector.id}.marker_snapshot", t) }
        }
    }

    private fun pollSystemSurface(collector: InspectorCollector) {
        if (activeFiles == null) return
        try { collectSystemSurfaceSnapshot(collector, includeClassDiscovery = false, includeExpensiveCommands = false) } catch (t: Throwable) { recordSourceError(collector.id, t) }
        updateServiceState()
    }

    private fun collectSystemSurfaceSnapshot(collector: InspectorCollector, includeClassDiscovery: Boolean, includeExpensiveCommands: Boolean) {
        val observations = if (collector is InspectorSystemSurfaceCollector) collector.collect(context, includeClassDiscovery, includeExpensiveCommands) else collector.collect(context)
        val failures = observations.filter { it.eventType.contains("ERROR") || it.eventType.contains("DENIED") || it.eventType.contains("BLOCKED") || it.eventType.contains("LIMIT") || it.metadata["truncated"] == true || it.metadata["outputTruncated"] == true || it.value.orEmpty().contains("permission denied", true) || it.value.orEmpty().contains("operation not permitted", true) }
        observations.forEach(::recordObservation)
        val discoveredClassKeys = if (includeClassDiscovery) emptySet() else systemSurfaceKeys.filter { it.split("|").getOrNull(4).orEmpty().startsWith("android.hardware.bydauto.") || it.split("|").getOrNull(4).orEmpty().startsWith("com.byd.auto.") }.toSet()
        val current = observations.filter { it.isSnapshot }.map { it.stateKey }.toSet() + discoveredClassKeys
        val blockedServices = failures.mapNotNull { it.service }.toSet()
        val blockedPaths = failures.mapNotNull { it.property }.toSet()
        val retained = mutableSetOf<String>()
        (systemSurfaceKeys - current).forEach { key ->
            val fields = key.split("|")
            val service = fields.getOrNull(1).orEmpty()
            val property = fields.getOrNull(4).orEmpty()
            val sourceUnavailable = service in blockedServices ||
                ("package_manager_inventory" in blockedPaths && (property.startsWith("com.") || property.startsWith("android.hardware.") || property.startsWith("service:") || property.startsWith("receiver:") || property.startsWith("provider:"))) ||
                ("BYDAUTO_permission_inventory" in blockedPaths && property.startsWith("android.permission.BYDAUTO_")) ||
                blockedPaths.any { failure ->
                    val settingNamespace = failure.removePrefix("settings.")
                    property.startsWith("$settingNamespace.") || property == failure || property.startsWith(failure)
                }
            val previous = stateValues[key]
            if (sourceUnavailable) retained.add(key)
            else if (previous != null && previous != "<absent>") {
                val event = InspectorEvent(System.currentTimeMillis(), activeFiles?.sessionId.orEmpty(), activeMode?.wireName ?: "unknown", "STATE_CHANGED", fields.getOrNull(0) ?: "android.system_surface", service = fields.getOrNull(1)?.takeIf { it.isNotBlank() }, className = fields.getOrNull(2)?.takeIf { it.isNotBlank() }, method = fields.getOrNull(3)?.takeIf { it.isNotBlank() }, property = fields.getOrNull(4)?.takeIf { it.isNotBlank() }, oldValue = previous, newValue = "<absent>", rawValue = "<absent>", metadata = mapOf("snapshotDiff" to true, "stateKey" to key, "change" to "removed"))
                writeEvent(event)
                markers.filter { event.timestamp - it.timestamp in -ACTION_BEFORE_MS..ACTION_AFTER_MS }.minByOrNull { kotlin.math.abs(event.timestamp - it.timestamp) }?.let { marker -> writeCorrelation(marker, event, "snapshot_removed_entry") }
                stateValues[key] = "<absent>"
            }
        }
        systemSurfaceKeys.clear()
        systemSurfaceKeys.addAll(current)
        systemSurfaceKeys.addAll(retained)
        markSource(collector.id, "snapshot_entries=${current.size}; inaccessible=${failures.size}; uid=${android.os.Process.myUid()}")
    }

    private fun pollSnapshot(collector: InspectorCollector) {
        if (activeFiles == null) return
        try {
            collector.collect(context).forEach(::recordObservation)
            markSource(collector.id, "polling_every_${snapshotPollSeconds()}s")
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
        val observedTimestamp = (observation.metadata["logEpoch"] as? String)?.toDoubleOrNull()?.let { (it * 1000.0).toLong() } ?: now
        val trackedState = observation.isSnapshot || observation.eventType == "SENSOR_SAMPLE"
        val old = if (trackedState) stateValues.put(observation.stateKey, observation.value) else null
        val unchangedState = trackedState && stateValues.containsKey(observation.stateKey) && old == observation.value && old != null
        if (unchangedState && activeMode != InspectorMode.EXPERIMENT && observation.eventType != "SENSOR_SAMPLE") return
        val eventType = when {
            trackedState && old == null -> if (observation.eventType == "SENSOR_SAMPLE") "SENSOR_SAMPLE" else "STATE_OBSERVED"
            trackedState && old != observation.value -> if (observation.eventType == "SENSOR_SAMPLE") "SENSOR_SAMPLE" else "STATE_CHANGED"
            unchangedState && observation.eventType == "SENSOR_SAMPLE" -> "SENSOR_SAMPLE"
            unchangedState -> "STATE_SAMPLE"
            else -> observation.eventType
        }
        if (eventType.contains("ERROR") || eventType.contains("DENIED") || eventType.contains("BLOCKED") || eventType.contains("LIMIT") || eventType == "SOURCE_LIMITED" || eventType == "SERVICE_ACCESS_ERROR" || eventType == "PROPERTY_ACCESS_ERROR") {
            val errorKey = "${observation.source}|${observation.property}|${observation.service}|${observation.value}"
            val last = errorTimes.put(errorKey, now)
            if (last == null || now - last >= ERROR_REPEAT_INTERVAL_MS) addError(observation.source, IllegalStateException(observation.value.orEmpty()), observation.permission)
        }
        val duplicateKey = "${observation.stateKey}|${old.orEmpty()}|${observation.value.orEmpty()}"
        val repeat = (repeatCounts[duplicateKey] ?: 0) + 1
        repeatCounts[duplicateKey] = repeat
        val nearestMarker = markers
            .filter { observedTimestamp - it.timestamp in -ACTION_BEFORE_MS..ACTION_AFTER_MS }
            .minByOrNull { kotlin.math.abs(observedTimestamp - it.timestamp) }
        val metadata = observation.metadata + mapOf("repeatCount" to repeat, "correlationIsTemporalOnly" to (nearestMarker != null))
        val event = InspectorEvent(
            timestamp = observedTimestamp,
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
            timeFromMarker = nearestMarker?.let { observedTimestamp - it.timestamp },
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
                "correlationType" to "heuristic_temporal_and_structural",
                "causalityConfirmed" to false,
                "heuristicScorePercent" to correlationScore(marker, event),
                "heuristicScoreMeaning" to "Inspector heuristic, not statistical probability",
                "evidenceEventType" to event.eventType,
                "evidenceSource" to event.source
            )
        ))
    }

    private fun correlationScore(marker: ActionMarker, event: InspectorEvent): Int {
        val distance = kotlin.math.abs(event.timestamp - marker.timestamp)
        val identity = listOf(event.source, event.service, event.className, event.method, event.property, event.rawValue).joinToString(" ").lowercase(Locale.ROOT)
        val key = listOf(event.source, event.service.orEmpty(), event.className.orEmpty(), event.method.orEmpty(), event.property.orEmpty()).joinToString("|")
        val sameKey = recentEvents.filter { candidate -> candidate.eventType == "STATE_CHANGED" && listOf(candidate.source, candidate.service.orEmpty(), candidate.className.orEmpty(), candidate.method.orEmpty(), candidate.property.orEmpty()).joinToString("|") == key && candidate.timestamp != event.timestamp && candidate.actionMarker != null && candidate.actionMarker != marker.id }
        val inverseTransition = event.oldValue != null && event.newValue != null && sameKey.any { it.oldValue == event.newValue && it.newValue == event.oldValue }

        val signature = event.metadata["normalizedSignature"] as? String
        val priorLogs = if (event.source == "android.logcat" && !signature.isNullOrBlank()) recentEvents.filter { candidate ->
            candidate !== event && candidate.source == "android.logcat" && candidate.service == event.service &&
                candidate.metadata["normalizedSignature"] == signature && candidate.actionMarker != null && candidate.actionMarker != marker.id
        } else emptyList()
        val currentNumbers = (event.metadata["numericTokens"] as? List<*>)?.map { it.toString() }.orEmpty()
        val numericChanged = priorLogs.any { candidate -> (candidate.metadata["numericTokens"] as? List<*>)?.map { it.toString() }?.let { it != currentNumbers } == true }
        val currentAction = marker.description.lowercase(Locale.ROOT)
        val priorActions = priorLogs.mapNotNull { candidate -> markers.firstOrNull { it.id == candidate.actionMarker }?.description?.lowercase(Locale.ROOT) }
        val saysOn: (String) -> Boolean = { Regex("\\b(on|ligar|ligue|ligado|ligada)\\b").containsMatchIn(it) }
        val saysOff: (String) -> Boolean = { Regex("\\b(off|desligar|desligue|desligado|desligada|apagar|apague)\\b").containsMatchIn(it) }
        val inverseAction = saysOn(currentAction) && priorActions.any(saysOff) || saysOff(currentAction) && priorActions.any(saysOn)
        return CorrelationScorer.score(
            distanceMs = distance,
            stateChanged = event.eventType == "STATE_CHANGED",
            bydRelated = identity.contains("byd") || identity.contains("bydauto"),
            halOrBinder = identity.contains("binder") || identity.contains("hal") || identity.contains("autoservice"),
            domainKeyword = Regex("light|lamp|illumination|ambient|interior|room|dome|led|rgb|body", RegexOption.IGNORE_CASE).containsMatchIn(identity),
            repeatedAcrossMarkers = (sameKey.size + priorLogs.mapNotNull { it.actionMarker }.distinct().size).coerceAtMost(3),
            inverseTransition = inverseTransition,
            inverseNumericAction = numericChanged && inverseAction
        )
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
        if (errors.size < 500) errors.add(item) else omittedErrorSummaries++
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
            errors = errors.toList() + if (omittedErrorSummaries > 0) listOf(mapOf("source" to "metadata.error_summary", "omittedSummaryCount" to omittedErrorSummaries, "detail" to "Individual raw error events remain in events.jsonl")) else emptyList(),
            capture = captureMetadata()
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
        errors = emptyList(),
        capture = captureMetadata()
    ).toJson()

    private fun updateServiceState() {
        DiLinkInspectorService.uiState = currentState()
        try { context.sendBroadcast(android.content.Intent(DiLinkInspectorService.ACTION_STATE).setPackage(context.packageName)) } catch (_: Throwable) { }
    }

    companion object {
        private const val ACTION_BEFORE_MS = 5_000L
        private const val ACTION_AFTER_MS = 5_000L
        private const val RECENT_EVENT_RETENTION_MS = 120_000L
        private const val ERROR_REPEAT_INTERVAL_MS = 60_000L
    }

    private fun captureMetadata(): Map<String, Any?> = mapOf(
        "intensity" to intensity,
        "snapshotPollSeconds" to snapshotPollSeconds(),
        "systemSurfacePollSeconds" to broadPollSeconds(),
        "knownLightGetterPollSeconds" to lightPollSeconds(),
        "androidSensorSampleIntervalMs" to sensorPollMillis(),
        "correlationWindowBeforeMs" to ACTION_BEFORE_MS,
        "correlationWindowAfterMs" to ACTION_AFTER_MS,
        "logcatBuffersRequested" to "all",
        "logcatContentFilter" to false
    )

    private fun lightPollSeconds(): Long = when (intensity) { "LOW" -> 10L; "DEEP" -> 1L; else -> 2L }
    private fun snapshotPollSeconds(): Long = when (intensity) { "LOW" -> 30L; "DEEP" -> 5L; else -> 10L }
    private fun sensorPollMillis(): Long = when (intensity) { "LOW" -> 5_000L; "DEEP" -> 500L; else -> 1_000L }
    private fun broadPollSeconds(): Long = when (intensity) { "LOW" -> 120L; "DEEP" -> 30L; else -> 60L }
}
