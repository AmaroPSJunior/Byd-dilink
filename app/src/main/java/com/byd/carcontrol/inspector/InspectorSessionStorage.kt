package com.byd.carcontrol.inspector

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File

data class ActiveInspectorFiles(
    val sessionId: String,
    var storageMode: String,
    var displayLocation: String,
    var metadataUri: Uri? = null,
    var eventsUri: Uri? = null,
    var internalDirectory: File? = null,
    var lastStorageError: String? = null,
    var metadataJson: JSONObject = JSONObject()
)

class InspectorSessionStorage(private val context: Context) {
    companion object {
        const val PUBLIC_ROOT = "/sdcard/Download/BydDilink/Inspector/sessions"
        private const val RELATIVE_ROOT = "${Environment.DIRECTORY_DOWNLOADS}/BydDilink/Inspector/sessions"
    }

    fun create(sessionId: String): ActiveInspectorFiles {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var metadata: Uri? = null
            var events: Uri? = null
            try {
                metadata = createDownloadRow(sessionId, "metadata.json", "application/json")
                events = createDownloadRow(sessionId, "events.jsonl", "application/x-ndjson")
                // Probe actual create/write/append access. Do not report external storage as usable until both files open.
                writeUri(metadata, "{}\n", "wt")
                writeUri(events, "", "wa")
                return ActiveInspectorFiles(
                    sessionId = sessionId,
                    storageMode = "mediastore_downloads",
                    displayLocation = "$PUBLIC_ROOT/$sessionId/",
                    metadataUri = metadata,
                    eventsUri = events
                )
            } catch (t: Throwable) {
                listOfNotNull(metadata, events).forEach { uri -> try { context.contentResolver.delete(uri, null, null) } catch (_: Throwable) { } }
                return createInternal(sessionId, "MediaStore Download indisponível: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        return createInternal(sessionId, "MediaStore Downloads requer Android 10 / API 29; usando armazenamento privado exportável.")
    }

    fun appendEvent(files: ActiveInspectorFiles, jsonLine: String) {
        val uri = files.eventsUri
        if (files.storageMode == "mediastore_downloads" && uri != null) {
            try {
                writeUri(uri, "$jsonLine\n", "wa")
                return
            } catch (t: Throwable) {
                switchToInternal(files, "Falha ao acrescentar JSONL no MediaStore: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        val destination = File(requireNotNull(files.internalDirectory), "events.jsonl")
        destination.appendText("$jsonLine\n", Charsets.UTF_8)
    }

    fun writeMetadata(files: ActiveInspectorFiles, json: JSONObject) {
        files.metadataJson = json
        val uri = files.metadataUri
        if (files.storageMode == "mediastore_downloads" && uri != null) {
            try {
                writeUri(uri, json.toString(2) + "\n", "wt")
                return
            } catch (t: Throwable) {
                switchToInternal(files, "Falha ao atualizar metadata.json no MediaStore: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
        File(requireNotNull(files.internalDirectory), "metadata.json").writeText(json.toString(2) + "\n", Charsets.UTF_8)
    }

    fun listSessions(): List<StoredInspectorSession> {
        val sessions = LinkedHashMap<String, StoredInspectorSession>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val rows = mutableMapOf<String, MutableMap<String, Uri>>()
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.RELATIVE_PATH)
                val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?"
                context.contentResolver.query(collection, projection, selection, arrayOf("$RELATIVE_ROOT/%"), null)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.RELATIVE_PATH)
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(pathColumn).orEmpty()
                        val sessionId = path.removePrefix("$RELATIVE_ROOT/").trimEnd('/').substringBefore('/')
                        val name = cursor.getString(nameColumn).orEmpty()
                        val uri = Uri.withAppendedPath(collection, cursor.getLong(idColumn).toString())
                        if (sessionId.isNotBlank()) rows.getOrPut(sessionId) { mutableMapOf() }[name] = uri
                    }
                }
                rows.forEach { (id, files) ->
                    val metadataUri = files["metadata.json"]
                    val metadata = metadataUri?.let { readText(it) }?.let { runCatching { JSONObject(it) }.getOrNull() }
                    if (metadata != null) {
                        sessions[id] = StoredInspectorSession(
                            sessionId = id,
                            mode = metadata.optString("mode", "unknown"),
                            startedAt = metadata.optLong("startedAt"),
                            endedAt = if (metadata.isNull("endedAt")) null else metadata.optLong("endedAt"),
                            storageLabel = "$PUBLIC_ROOT/$id/",
                            eventsUri = files["events.jsonl"],
                            metadataUri = metadataUri
                        )
                    }
                }
            } catch (_: Throwable) { }
        }

        internalSessionsDirectory().listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val metadata = runCatching { JSONObject(File(dir, "metadata.json").readText()) }.getOrNull() ?: return@forEach
            val local = StoredInspectorSession(
                sessionId = dir.name,
                mode = metadata.optString("mode", "unknown"),
                startedAt = metadata.optLong("startedAt"),
                endedAt = if (metadata.isNull("endedAt")) null else metadata.optLong("endedAt"),
                storageLabel = dir.absolutePath,
                internalDirectory = dir
            )
            // Prefer the external copy if one exists; otherwise expose the fallback directory.
            if (!sessions.containsKey(dir.name)) sessions[dir.name] = local
        }
        return sessions.values.sortedByDescending { it.startedAt }
    }

    fun readEvents(session: StoredInspectorSession): List<InspectorEvent> {
        val text = try {
            session.eventsUri?.let(::readText) ?: File(requireNotNull(session.internalDirectory), "events.jsonl").readText()
        } catch (_: Throwable) { return emptyList() }
        return text.lineSequence().mapNotNull { line ->
            runCatching { InspectorEvent.fromJson(JSONObject(line)) }.getOrNull()
        }.toList()
    }

    fun readMetadata(session: StoredInspectorSession): JSONObject? = try {
        session.metadataUri?.let(::readText)?.let(::JSONObject)
            ?: session.internalDirectory?.let { File(it, "metadata.json").readText() }?.let(::JSONObject)
    } catch (_: Throwable) { null }

    fun shareUri(session: StoredInspectorSession, fileName: String): Uri? {
        return when (fileName) {
            "events.jsonl" -> session.eventsUri ?: session.internalDirectory?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(it, fileName)) }
            "metadata.json" -> session.metadataUri ?: session.internalDirectory?.let { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(it, fileName)) }
            else -> null
        }
    }

    private fun createDownloadRow(sessionId: String, displayName: String, mimeType: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "$RELATIVE_ROOT/$sessionId/")
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        return requireNotNull(context.contentResolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)) {
            "MediaStore insert retornou null para $displayName"
        }
    }

    private fun createInternal(sessionId: String, reason: String): ActiveInspectorFiles {
        val dir = File(internalSessionsDirectory(), sessionId)
        check(dir.mkdirs() || dir.isDirectory) { "Não foi possível criar $dir" }
        File(dir, "events.jsonl").writeText("", Charsets.UTF_8)
        return ActiveInspectorFiles(
            sessionId = sessionId,
            storageMode = "app_private_fallback",
            displayLocation = dir.absolutePath,
            internalDirectory = dir,
            lastStorageError = reason
        )
    }

    private fun switchToInternal(files: ActiveInspectorFiles, reason: String) {
        val dir = File(internalSessionsDirectory(), files.sessionId)
        check(dir.mkdirs() || dir.isDirectory) { "Falha ao criar fallback ${dir.absolutePath}" }
        files.eventsUri?.let { uri ->
            try { File(dir, "events.jsonl").writeText(readText(uri), Charsets.UTF_8) } catch (_: Throwable) { }
        }
        files.metadataUri?.let { uri ->
            try { File(dir, "metadata.json").writeText(readText(uri), Charsets.UTF_8) } catch (_: Throwable) { }
        }
        listOfNotNull(files.eventsUri, files.metadataUri).forEach { uri -> try { context.contentResolver.delete(uri, null, null) } catch (_: Throwable) { } }
        files.storageMode = "app_private_fallback"
        files.displayLocation = dir.absolutePath
        files.internalDirectory = dir
        files.eventsUri = null
        files.metadataUri = null
        files.lastStorageError = reason
    }

    private fun writeUri(uri: Uri, text: String, mode: String) {
        val stream = requireNotNull(context.contentResolver.openOutputStream(uri, mode)) { "MediaStore não abriu $uri ($mode)" }
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)); it.flush() }
    }

    private fun readText(uri: Uri): String = requireNotNull(context.contentResolver.openInputStream(uri)).bufferedReader(Charsets.UTF_8).use { it.readText() }
    private fun internalSessionsDirectory(): File = File(context.filesDir, "Inspector/sessions")
}
