package com.byd.carcontrol

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updates only from this project's public GitHub releases. Android's Package Installer
 * enforces package name and signing certificate; user approval remains required.
 */
object AppUpdater {
    private const val RELEASE_URL =
        "https://api.github.com/repos/AmaroPSJunior/Byd-dilink/releases/latest"

    fun check(activity: Activity, report: (String) -> Unit) {
        report("Verificando atualização…")
        Thread {
            try {
                val release = JSONObject(fetchText(RELEASE_URL))
                val assets = release.getJSONArray("assets")
                var downloadUrl: String? = null
                var expectedSha256: String? = null
                for (i in 0 until assets.length()) {
                    val item = assets.getJSONObject(i)
                    if (item.optString("name") == "byd-car-control-release.apk") {
                        downloadUrl = item.getString("browser_download_url")
                    }
                    if (item.optString("name") == "byd-car-control-release.apk.sha256") {
                        expectedSha256 = fetchText(item.getString("browser_download_url"))
                            .trim().split(Regex("\\s+")).firstOrNull()
                    }
                }
                val tag = release.optString("tag_name").removePrefix("v")
                val parts = tag.split(".").map { it.toIntOrNull() }
                require(parts.size == 3 && parts.all { it != null }) { "Tag da versão inválida." }
                val latestCode = parts[0]!! * 1000000L + parts[1]!! * 1000L + parts[2]!!
                val installed = if (Build.VERSION.SDK_INT >= 28)
                    activity.packageManager.getPackageInfo(activity.packageName, 0).longVersionCode
                else @Suppress("DEPRECATION")
                    activity.packageManager.getPackageInfo(activity.packageName, 0).versionCode.toLong()
                if (latestCode <= installed) {
                    activity.runOnUiThread { report("Aplicativo atualizado (v$tag).") }
                    return@Thread
                }
                require(!downloadUrl.isNullOrBlank()) { "APK de release não encontrado." }
                require(!expectedSha256.isNullOrBlank() && expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))) {
                    "Checksum SHA-256 da release ausente ou inválido."
                }
                activity.runOnUiThread {
                    AlertDialog.Builder(activity).setTitle("Atualização v$tag")
                        .setMessage("Uma nova versão está disponível. Baixar e abrir o instalador do Android?")
                        .setNegativeButton("Depois", null)
                        .setPositiveButton("Atualizar") { _, _ ->
                            download(activity, downloadUrl!!, expectedSha256!!, report)
                        }.show()
                    report("Atualização v$tag disponível.")
                }
            } catch (e: Exception) {
                activity.runOnUiThread { report("Não foi possível consultar atualizações: ${e.message}") }
            }
        }.start()
    }

    private fun download(activity: Activity, url: String, sha: String, report: (String) -> Unit) {
        activity.runOnUiThread { report("Baixando atualização…") }
        Thread {
            val file = File(activity.cacheDir, "updates/byd-car-control-release.apk")
            try {
                require(url.startsWith("https://github.com/AmaroPSJunior/Byd-dilink/releases/download/")) {
                    "Origem do download não reconhecida."
                }
                file.parentFile?.mkdirs()
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.instanceFollowRedirects = true
                try {
                    require(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                    conn.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
                } finally { conn.disconnect() }
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        digest.update(buffer, 0, n)
                    }
                }
                val calculated = digest.digest().joinToString("") { "%02x".format(it) }
                require(calculated.equals(sha, ignoreCase = true)) { "Assinatura de integridade SHA-256 divergente." }
                activity.runOnUiThread {
                    val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
                    if (Build.VERSION.SDK_INT >= 26 &&
                        !activity.packageManager.canRequestPackageInstalls()) {
                        report("Autorize a instalação por este aplicativo e toque novamente em Atualizar.")
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${activity.packageName}")))
                        return@runOnUiThread
                    }
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    report("APK validado. Confirme a instalação no Android.")
                }
            } catch (e: Exception) {
                file.delete()
                activity.runOnUiThread { report("Falha na atualização: ${e.message}") }
            }
        }.start()
    }

    private fun fetchText(url: String): String {
        require(url.startsWith("https://api.github.com/repos/AmaroPSJunior/Byd-dilink/") ||
            url.startsWith("https://github.com/AmaroPSJunior/Byd-dilink/releases/download/"))
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "BYDDiLinkUpdater")
        try {
            require(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
    }
}
