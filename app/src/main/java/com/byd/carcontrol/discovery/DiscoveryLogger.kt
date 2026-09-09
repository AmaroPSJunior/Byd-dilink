package com.byd.carcontrol.discovery

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

data class LogEntry(
    val timestamp: String,
    val category: String,
    val operation: String,
    val target: String,
    val result: String,
    val exception: String? = null,
    val durationMs: Long = 0
) {
    fun toFormattedString(): String {
        val base = "[$timestamp] [$category] $operation -> $target: $result (${durationMs}ms)"
        return if (exception.isNullOrEmpty()) base else "$base\n   EXCEPTION: $exception"
    }
}

object DiscoveryLogger {
    private const val TAG = "BYDDiscoveryLogger"
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val logs = CopyOnWriteArrayList<LogEntry>()

    fun log(
        category: String,
        operation: String,
        target: String,
        result: String,
        exception: Throwable? = null,
        durationMs: Long = 0
    ) {
        val timeStr = dateFormat.format(Date())
        val exStr = exception?.let { e ->
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            "${e.javaClass.simpleName}: ${e.message}\n${sw.toString().take(500)}"
        }

        val entry = LogEntry(
            timestamp = timeStr,
            category = category,
            operation = operation,
            target = target,
            result = result,
            exception = exStr,
            durationMs = durationMs
        )

        logs.add(entry)
        if (logs.size > 2000) {
            logs.removeAt(0)
        }

        val logMsg = entry.toFormattedString()
        if (exception != null) {
            Log.e(TAG, logMsg)
        } else {
            Log.i(TAG, logMsg)
        }
    }

    fun getLogs(): List<LogEntry> = logs.toList()

    fun clear() {
        logs.clear()
    }
}
