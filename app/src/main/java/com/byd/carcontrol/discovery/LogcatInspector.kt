package com.byd.carcontrol.discovery

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

class LogcatInspector(private val context: Context) {

    fun captureFilteredLogcat(maxLines: Int = 100): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -v time BYD:V DiLink:V DiCar:V Vehicle:V MagicCore:V MagicManager:V *:S")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = mutableListOf<String>()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                line?.let { lines.add(it) }
                if (lines.size >= maxLines) break
            }

            reader.close()
            process.destroy()

            if (lines.isEmpty()) {
                val fallbackProc = Runtime.getRuntime().exec("logcat -d -t 100")
                val fbReader = BufferedReader(InputStreamReader(fallbackProc.inputStream))
                var fbLine: String?
                while (fbReader.readLine().also { fbLine = it } != null) {
                    fbLine?.let { lines.add(it) }
                }
                fbReader.close()
                fallbackProc.destroy()
            }

            if (lines.isEmpty()) "NO_LOGCAT_OUTPUT_AVAILABLE" else lines.joinToString("\n")
        } catch (e: Exception) {
            DiscoveryLogger.log("LOGCAT", "ACCESS_DENIED", "logcat", "Error: ${e.message}", e)
            "LOGCAT_ACCESS_DENIED: ${e.message}"
        }
    }
}
