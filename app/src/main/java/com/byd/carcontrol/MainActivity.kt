package com.byd.carcontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.byd.carcontrol.discovery.BYDBodyworkInspector
import com.byd.carcontrol.discovery.BYDLightHalInspector
import com.byd.carcontrol.discovery.PermissionDiscovery
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportedFileInfo(
    val fileName: String,
    val fileAbsolutePath: String,
    val sizeBytes: Long,
    val sizeFormatted: String,
    val file: File,
    val contentUri: Uri?
)

/**
 * Atividade Principal do BYD Light & Bodywork HAL Diagnostic.
 * Interface limpa e objetiva focada no diagnóstico de iluminação interna / cortesia / teto.
 * Operação 100% STRICT SAFE READ-ONLY (Sem comandos de escrita no veículo nesta etapa).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var commManager: BYDCommunicationManager
    private lateinit var halInspector: BYDLightHalInspector
    private lateinit var bodyworkInspector: BYDBodyworkInspector
    private lateinit var permissionDiscovery: PermissionDiscovery

    private lateinit var txtVehicleInfo: TextView
    private lateinit var txtActiveTransport: TextView
    private lateinit var txtLiveLogs: TextView

    private lateinit var btnRunHalInspector: Button
    private lateinit var btnCheckPermissions: Button
    private lateinit var btnExportReport: Button
    private lateinit var btnCopyInParts: Button
    private lateinit var btnExportTxt: Button
    private lateinit var btnShareTxt: Button

    private val logHistory = mutableListOf<String>()
    private var lastInspectionReport: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Capturador Global de Exceções Não Tratadas (Garante que o App NUNCA feche sozinho)
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("BYDUncaughtException", "Erro não tratado na thread ${thread.name}", throwable)
            runOnUiThread {
                try {
                    appendLog("💥 CAPTURADO ERRO CRÍTICO DA CENTRAL:\n" +
                            "${throwable.javaClass.simpleName}: ${throwable.message}\n" +
                            throwable.stackTrace.take(4).joinToString("\n"))
                    Toast.makeText(this@MainActivity, "Erro evitado: ${throwable.message}", Toast.LENGTH_LONG).show()
                } catch (_: Throwable) {}
            }
        }

        try {
            setContentView(R.layout.activity_main)

            commManager = BYDCommunicationManager.getInstance(this)
            halInspector = BYDLightHalInspector(this, commManager.repository)
            bodyworkInspector = BYDBodyworkInspector(this, commManager.repository)
            permissionDiscovery = PermissionDiscovery(this, commManager.repository)

            txtVehicleInfo = findViewById(R.id.txtVehicleInfo)
            txtActiveTransport = findViewById(R.id.txtActiveTransport)
            txtLiveLogs = findViewById(R.id.txtLiveLogs)

            btnRunHalInspector = findViewById(R.id.btnRunHalInspector)
            btnCheckPermissions = findViewById(R.id.btnCheckPermissions)
            btnExportReport = findViewById(R.id.btnExportReport)
            btnCopyInParts = findViewById(R.id.btnCopyInParts)
            btnExportTxt = findViewById(R.id.btnExportTxt)
            btnShareTxt = findViewById(R.id.btnShareTxt)

            updateHeaderInfo()

            // 1️⃣ OPÇÃO 1: EXECUTAR DIAGNÓSTICO (BYD INTERIOR LIGHT & BODYWORK DISCOVERY)
            btnRunHalInspector.setOnClickListener {
                appendLog("==================================================")
                appendLog("🔍 INICIANDO DIAGNÓSTICO DA LUZ INTERNA DE CORTESIA...")
                appendLog("Alvo Principal: android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice")
                appendLog("Modo: STRICT SAFE READ-ONLY (Métodos de escrita identificados mas NÃO executados)")
                appendLog("==================================================")
                Toast.makeText(this, "Iniciando varredura por reflexão em Bodywork...", Toast.LENGTH_SHORT).show()

                Thread {
                    try {
                        val report = bodyworkInspector.runDiscovery()
                        lastInspectionReport = report
                        runOnUiThread {
                            appendLog("=== RESULTADO DA INVESTIGAÇÃO DE LUZ INTERNA ===\n$report")
                            Toast.makeText(this@MainActivity, "Diagnóstico de iluminação concluído!", Toast.LENGTH_SHORT).show()
                        }
                    } catch (t: Throwable) {
                        runOnUiThread {
                            appendLog("❌ Falha na inspeção: ${t.javaClass.simpleName} - ${t.message}")
                        }
                    }
                }.start()
            }

            // 2️⃣ OPÇÃO 2: VERIFICAÇÃO DE PERMISSÕES DA CENTRAL
            btnCheckPermissions.setOnClickListener {
                appendLog("==================================================")
                appendLog("🛡️ INICIANDO VERIFICAÇÃO DE PERMISSÕES DA CENTRAL...")
                appendLog("==================================================")
                Toast.makeText(this, "Verificando permissões...", Toast.LENGTH_SHORT).show()

                Thread {
                    try {
                        val permsToTest = listOf(
                            "android.permission.BYDAUTO_LIGHT_GET",
                            "android.permission.BYDAUTO_LIGHT_SET",
                            "android.permission.BYDAUTO_BODYWORK_GET",
                            "android.permission.BYDAUTO_BODYWORK_SET",
                            "com.byd.permission.CAR_LIGHT_CONTROL",
                            "com.byd.permission.CAR_DOOR_CONTROL",
                            "android.car.permission.CONTROL_CAR_INTERIOR_LIGHTS",
                            "cc.omycar.magiccore.permission.API"
                        )
                        val sb = StringBuilder()
                        sb.append("===== ANÁLISE DE PERMISSÕES DILINK =====\n")
                        var grantedCount = 0
                        val totalCount = permsToTest.size

                        permsToTest.forEach { perm ->
                            val isGranted = permissionDiscovery.checkPermissionGranted(perm)
                            if (isGranted) grantedCount++
                            val statusStr = if (isGranted) "GRANTED ✅" else "DENIED ❌"
                            sb.append("• $perm: $statusStr\n")
                        }

                        val resultStr = sb.toString()
                        runOnUiThread {
                            appendLog(resultStr)
                            Toast.makeText(this@MainActivity, "Permissões verificadas: $grantedCount/$totalCount concedidas", Toast.LENGTH_SHORT).show()
                        }
                    } catch (t: Throwable) {
                        runOnUiThread {
                            appendLog("❌ Falha ao verificar permissões: ${t.javaClass.simpleName} - ${t.message}")
                        }
                    }
                }.start()
            }

            // 3️⃣ OPÇÃO 3.1: COPIAR RELATÓRIO COMPLETO INTEGRAL
            btnExportReport.setOnClickListener {
                try {
                    val rawReport = getRawReportText()
                    val fullReport = getFormattedReportWithFooter(rawReport)

                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("BYD_Interior_Light_Report", fullReport)
                    clipboard.setPrimaryClip(clip)

                    appendLog("==================================================")
                    appendLog("📋 RELATÓRIO INTEGRAL COPIADO PARA A ÁREA DE TRANSFERÊNCIA!")
                    appendLog("Caracteres: ${fullReport.length} | Linhas: ${fullReport.lines().size}")
                    appendLog("==================================================")

                    Toast.makeText(this, "Relatório completo copiado!", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    appendLog("❌ Erro ao copiar relatório: ${t.message}")
                }
            }

            // 3️⃣ OPÇÃO 3.2: COPIAR EM PARTES (~30K CARACTERES POR BLOCOS SEM CORTAR LINHAS)
            btnCopyInParts.setOnClickListener {
                try {
                    val rawReport = getRawReportText()
                    val fullReport = getFormattedReportWithFooter(rawReport)
                    val parts = splitReportIntoParts(fullReport, 30000)
                    val totalParts = parts.size

                    val dialogItems = parts.mapIndexed { idx, part ->
                        "Parte ${idx + 1}/$totalParts (${part.length} chars, ${part.lines().size} linhas)"
                    }.toTypedArray()

                    AlertDialog.Builder(this)
                        .setTitle("🧩 Copiar Relatório em Partes ($totalParts partes)")
                        .setItems(dialogItems) { _, which ->
                            val selectedPart = parts[which]
                            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("BYD_Report_Part_${which + 1}_of_$totalParts", selectedPart)
                            clipboard.setPrimaryClip(clip)

                            appendLog("==================================================")
                            appendLog("🧩 PARTE ${which + 1}/$totalParts COPIADA COM SUCESSO!")
                            appendLog("Tamanho: ${selectedPart.length} chars | ${selectedPart.lines().size} linhas")
                            appendLog("==================================================")

                            Toast.makeText(this, "Parte ${which + 1}/$totalParts copiada!", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Fechar", null)
                        .show()
                } catch (t: Throwable) {
                    appendLog("❌ Erro ao dividir relatório em partes: ${t.message}")
                }
            }

            // 3️⃣ OPÇÃO 3.3: EXPORTAR ARQUIVO TXT
            btnExportTxt.setOnClickListener {
                try {
                    val rawReport = getRawReportText()
                    val fullReport = getFormattedReportWithFooter(rawReport)
                    val fileInfo = saveReportTxtToDownloads(fullReport)

                    appendLog("==================================================")
                    appendLog("💾 ARQUIVO TXT EXPORTADO COM SUCESSO!")
                    appendLog("Nome: ${fileInfo.fileName}")
                    appendLog("Tamanho: ${fileInfo.sizeFormatted}")
                    appendLog("Local: ${fileInfo.fileAbsolutePath}")
                    appendLog("==================================================")

                    Toast.makeText(this, "TXT Salvo: ${fileInfo.fileName} (${fileInfo.sizeFormatted})", Toast.LENGTH_LONG).show()
                } catch (t: Throwable) {
                    appendLog("❌ Erro ao exportar TXT: ${t.message}")
                }
            }

            // 3️⃣ OPÇÃO 3.4: COMPARTILHAR ARQUIVO TXT (VIA ACTION_SEND / FILEPROVIDER)
            btnShareTxt.setOnClickListener {
                try {
                    val rawReport = getRawReportText()
                    val fullReport = getFormattedReportWithFooter(rawReport)
                    val fileInfo = saveReportTxtToDownloads(fullReport)

                    shareReportTxtFile(fileInfo)

                    appendLog("==================================================")
                    appendLog("📤 COMPARTILHANDO ARQUIVO TXT...")
                    appendLog("Arquivo: ${fileInfo.fileName}")
                    appendLog("URI Content: Provider de leitura concedido")
                    appendLog("==================================================")
                } catch (t: Throwable) {
                    appendLog("❌ Erro ao compartilhar TXT: ${t.message}")
                }
            }

        } catch (t: Throwable) {
            Log.e("MainActivity", "Erro no onCreate", t)
        }
    }

    private fun getRawReportText(): String {
        return if (!lastInspectionReport.isNullOrEmpty()) {
            lastInspectionReport!!
        } else {
            generateFullReport()
        }
    }

    private fun getFormattedReportWithFooter(rawReport: String): String {
        val delimiter = "==================================================\nEND OF COMPLETE REPORT\n=================================================="
        var text = "$rawReport\n$delimiter\nCharacters: 0\nUTF-8 Bytes: 0\nLines: 0"
        for (i in 0..3) {
            val chars = text.length
            val bytes = text.toByteArray(Charsets.UTF_8).size
            val lines = text.lines().size
            text = "$rawReport\n$delimiter\nCharacters: $chars\nUTF-8 Bytes: $bytes\nLines: $lines"
        }
        return text
    }

    private fun splitReportIntoParts(reportText: String, maxPartSize: Int = 30000): List<String> {
        val lines = reportText.lines()
        val parts = mutableListOf<String>()
        var currentPart = StringBuilder()

        for (line in lines) {
            if (currentPart.isNotEmpty() && (currentPart.length + line.length + 1) > maxPartSize) {
                parts.add(currentPart.toString())
                currentPart = StringBuilder()
            }
            if (currentPart.isNotEmpty()) {
                currentPart.append("\n")
            }
            currentPart.append(line)
        }
        if (currentPart.isNotEmpty()) {
            parts.add(currentPart.toString())
        }
        return if (parts.isEmpty()) listOf(reportText) else parts
    }

    private fun saveReportTxtToDownloads(reportText: String): ExportedFileInfo {
        val timeStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val fileName = "byd_diagnostic_$timeStamp.txt"
        val utf8Bytes = reportText.toByteArray(Charsets.UTF_8)
        var contentUri: Uri? = null

        // 1. MediaStore Save para Android 10 (API 29) em Downloads/BYD-Diagnostics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/BYD-Diagnostics")
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(utf8Bytes)
                    }
                    contentUri = uri
                }
            } catch (t: Throwable) {
                Log.e("MainActivity", "Erro MediaStore ao salvar TXT", t)
            }
        }

        // 2. Criar arquivo físico em Downloads/BYD-Diagnostics para FileProvider / visualização local
        val downloadsFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "BYD-Diagnostics"
        )
        if (!downloadsFolder.exists()) {
            downloadsFolder.mkdirs()
        }

        val targetFile = File(downloadsFolder, fileName)
        targetFile.writeBytes(utf8Bytes)

        val sizeInBytes = targetFile.length()
        val sizeInKb = String.format(Locale.US, "%.2f KB", sizeInBytes / 1024.0)
        val formattedSize = "$sizeInBytes bytes ($sizeInKb)"

        return ExportedFileInfo(
            fileName = fileName,
            fileAbsolutePath = targetFile.absolutePath,
            sizeBytes = sizeInBytes,
            sizeFormatted = formattedSize,
            file = targetFile,
            contentUri = contentUri
        )
    }

    private fun shareReportTxtFile(exportedInfo: ExportedFileInfo) {
        val fileUri: Uri = try {
            FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                exportedInfo.file
            )
        } catch (t: Throwable) {
            exportedInfo.contentUri ?: Uri.fromFile(exportedInfo.file)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, exportedInfo.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Compartilhar Relatório TXT BYD")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(chooser)
    }

    private fun generateFullReport(): String {
        val fw = commManager.getFirmwareInfo()
        val json = JSONObject()

        json.put("appName", "BYD Light HAL Diagnostic")
        json.put("appVersion", "1.0.0")
        json.put("deviceModel", fw["ro.product.model"] ?: "BYD Dolphin Plus")
        json.put("androidVersion", fw["ro.build.version.release"] ?: "10")
        json.put("sdkVersion", fw["ro.build.version.sdk"] ?: "29")
        json.put("dilinkVersion", fw["dilink.version"] ?: "DiLink 3.0/4.0")
        json.put("fingerprint", fw["ro.build.fingerprint"] ?: "N/A")
        json.put("mode", "STRICT SAFE READ-ONLY")

        if (lastInspectionReport != null) {
            json.put("halInspectionSummary", lastInspectionReport)
        }

        json.put("recentLogs", logHistory.takeLast(40).joinToString("\n"))

        return json.toString(2)
    }

    private fun updateHeaderInfo() {
        try {
            val fw = commManager.getFirmwareInfo()
            val model = fw["ro.product.model"] ?: "Dolphin Plus"
            val androidVer = fw["ro.build.version.release"] ?: "10"
            val dilinkVer = fw["dilink.version"] ?: "DiLink 3.0/4.0"
            txtVehicleInfo.text = "$model • Android $androidVer (SDK ${fw["ro.build.version.sdk"] ?: "29"}) • $dilinkVer"
            txtActiveTransport.text = "Modo: STRICT SAFE READ-ONLY (Sem comandos de escrita)"
        } catch (t: Throwable) {
            txtVehicleInfo.text = "BYD Dolphin Plus • DiLink 3.0/4.0 • Android 10 (API 29)"
            txtActiveTransport.text = "Modo: STRICT SAFE READ-ONLY"
        }
    }

    private fun appendLog(text: String) {
        try {
            logHistory.add(text)
            if (logHistory.size > 150) {
                logHistory.removeAt(0)
            }
            txtLiveLogs.text = logHistory.takeLast(35).joinToString("\n\n")
        } catch (_: Throwable) {}
    }
}
