package com.byd.carcontrol.inspector

import org.json.JSONObject

enum class InspectorMode(val wireName: String) {
    MONITORING("monitoramento"), EXPERIMENT("experimento")
}

data class InspectorObservation(
    val source: String,
    val eventType: String,
    val value: String? = null,
    val service: String? = null,
    val className: String? = null,
    val method: String? = null,
    val property: String? = null,
    val permission: String? = null,
    val metadata: Map<String, Any?> = emptyMap(),
    val isSnapshot: Boolean = false
) {
    val stateKey: String
        get() = listOf(source, service.orEmpty(), className.orEmpty(), method.orEmpty(), property.orEmpty()).joinToString("|")
}

data class InspectorEvent(
    val timestamp: Long,
    val sessionId: String,
    val mode: String,
    val eventType: String,
    val source: String,
    val service: String? = null,
    val className: String? = null,
    val method: String? = null,
    val property: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val rawValue: String? = null,
    val permission: String? = null,
    val actionMarker: String? = null,
    val timeFromMarker: Long? = null,
    val metadata: Map<String, Any?> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("sessionId", sessionId)
        put("mode", mode)
        put("eventType", eventType)
        put("source", source)
        putOpt("service", service)
        putOpt("className", className)
        putOpt("method", method)
        putOpt("property", property)
        putOpt("oldValue", oldValue)
        putOpt("newValue", newValue)
        putOpt("rawValue", rawValue)
        putOpt("permission", permission)
        putOpt("actionMarker", actionMarker)
        putOpt("timeFromMarker", timeFromMarker)
        put("metadata", JSONObject(metadata))
    }

    companion object {
        fun fromJson(json: JSONObject): InspectorEvent = InspectorEvent(
            timestamp = json.optLong("timestamp"),
            sessionId = json.optString("sessionId"),
            mode = json.optString("mode"),
            eventType = json.optString("eventType"),
            source = json.optString("source"),
            service = json.optString("service").takeIf { it.isNotBlank() && it != "null" },
            className = json.optString("className").takeIf { it.isNotBlank() && it != "null" },
            method = json.optString("method").takeIf { it.isNotBlank() && it != "null" },
            property = json.optString("property").takeIf { it.isNotBlank() && it != "null" },
            oldValue = json.optString("oldValue").takeIf { it.isNotBlank() && it != "null" },
            newValue = json.optString("newValue").takeIf { it.isNotBlank() && it != "null" },
            rawValue = json.optString("rawValue").takeIf { it.isNotBlank() && it != "null" },
            permission = json.optString("permission").takeIf { it.isNotBlank() && it != "null" },
            actionMarker = json.optString("actionMarker").takeIf { it.isNotBlank() && it != "null" },
            timeFromMarker = if (json.isNull("timeFromMarker")) null else json.optLong("timeFromMarker"),
            metadata = json.optJSONObject("metadata")?.let { obj ->
                obj.keys().asSequence().associateWith { key -> obj.opt(key) }
            }.orEmpty()
        )
    }
}

data class InspectorSessionMetadata(
    val sessionId: String,
    val mode: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val device: Map<String, Any?>,
    val sources: List<Map<String, Any?>>,
    val storage: Map<String, Any?>,
    val errors: List<Map<String, Any?>> = emptyList(),
    val capture: Map<String, Any?> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", 1)
        put("sessionId", sessionId)
        put("mode", mode)
        put("startedAt", startedAt)
        putOpt("endedAt", endedAt)
        put("device", JSONObject(device))
        put("sources", org.json.JSONArray(sources.map(::JSONObject)))
        put("storage", JSONObject(storage))
        put("errors", org.json.JSONArray(errors.map(::JSONObject)))
        put("capture", JSONObject(capture))
    }
}

data class StoredInspectorSession(
    val sessionId: String,
    val mode: String,
    val startedAt: Long,
    val endedAt: Long?,
    val storageLabel: String,
    val eventsUri: android.net.Uri? = null,
    val metadataUri: android.net.Uri? = null,
    val internalDirectory: java.io.File? = null
)

data class InspectorUiState(
    val active: Boolean = false,
    val ready: Boolean = false,
    val sessionId: String? = null,
    val mode: String? = null,
    val startedAt: Long? = null,
    val eventCount: Long = 0,
    val storageLocation: String = "",
    val lastError: String? = null
)
