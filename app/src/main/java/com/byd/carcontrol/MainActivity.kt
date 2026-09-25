package com.byd.carcontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.byd.carcontrol.discovery.BYDBodyworkInspector
import com.byd.carcontrol.discovery.BYDControlManager
import com.byd.carcontrol.discovery.BYDLightHalInspector
import com.byd.carcontrol.discovery.BYDSettingInspector
import com.byd.carcontrol.discovery.ControlAction
import com.byd.carcontrol.discovery.ControlStatus
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
 * Atividade Principal do BYD Controller & Diagnostic.
 * Suporta navegação entre 2 abas:
 * - DIAGNÓSTICO: Varredura de hardware e relatórios completos.
 * - CONTROLES: Painel de acionamento permanente para APIs reais descobertas com confirmação prévia de segurança.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var commManager: BYDCommunicationManager
    private lateinit var halInspector: BYDLightHalInspector
    private lateinit var bodyworkInspector: BYDBodyworkInspector
    private lateinit var settingInspector: BYDSettingInspector
    private lateinit var permissionDiscovery: PermissionDiscovery
    private lateinit var controlManager: BYDControlManager

    private lateinit var txtVehicleInfo: TextView
    private lateinit var txtActiveTransport: TextView
    private lateinit var txtLiveLogs: TextView
    private lateinit var txtControlLogs: TextView

    // Tab buttons & containers
    private lateinit var btnTabDiagnostic: Button
    private lateinit var btnTabControls: Button
    private lateinit var layoutTabDiagnostic: ScrollView
    private lateinit var layoutTabControls: ScrollView

    // Diagnostic tab buttons
    private lateinit var btnRunHalInspector: Button
    private lateinit var btnCheckPermissions: Button
    private lateinit var btnExportReport: Button
    private lateinit var btnCopyInParts: Button
    private lateinit var btnExportTxt: Button
    private lateinit var btnShareTxt: Button

    // Controls tab views
    private lateinit var txtStatusSunshade: TextView
    private lateinit var txtDetailsSunshade: TextView
    private lateinit var btnSunshadeOpen: Button
    private lateinit var btnSunshadeClose: Button
    private lateinit var btnSunshadeStop: Button

    private lateinit var txtStatusMoonroof: TextView
    private lateinit var txtDetailsMoonroof: TextView
    private lateinit var btnMoonroofOpen: Button
    private lateinit var btnMoonroofClose: Button
    private lateinit var btnMoonroofStop: Button

    private lateinit var txtStatusDriverWindow: TextView
    private lateinit var txtDetailsDriverWindow: TextView
    private lateinit var btnDriverWindowOpen: Button
    private lateinit var btnDriverWindowClose: Button
    private lateinit var btnDriverWindowStop: Button

    private lateinit var txtStatusAC: TextView
    private lateinit var txtDetailsAC: TextView
    private lateinit var btnAcOn: Button
    private lateinit var btnAcOff: Button

    private lateinit var txtStatusInteriorLight: TextView
    private lateinit var txtDetailsInteriorLight: TextView
    private lateinit var btnInteriorLightOn: Button
    private lateinit var btnInteriorLightOff: Button

    private val logHistory = mutableListOf<String>()
    private var lastInspectionReport: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Capturador Global de Exceções
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
            settingInspector = BYDSettingInspector(this, commManager.repository)
            permissionDiscovery = PermissionDiscovery(this, commManager.repository)
            controlManager = BYDControlManager(this, commManager.repository)

            initViews()
            setupTabs()
            setupDiagnosticListeners()
            setupControlsListeners()

            updateHeaderInfo()
            updateControlsUI()

        } catch (t: Throwable) {
            Log.e("MainActivity", "Erro no onCreate", t)
        }
    }

    private fun initViews() {
        txtVehicleInfo = findViewById(R.id.txtVehicleInfo)
        txtActiveTransport = findViewById(R.id.txtActiveTransport)
        txtLiveLogs = findViewById(R.id.txtLiveLogs)
        txtControlLogs = findViewById(R.id.txtControlLogs)

        btnTabDiagnostic = findViewById(R.id.btnTabDiagnostic)
        btnTabControls = findViewById(R.id.btnTabControls)
        layoutTabDiagnostic = findViewById(R.id.layoutTabDiagnostic)
        layoutTabControls = findViewById(R.id.layoutTabControls)

        btnRunHalInspector = findViewById(R.id.btnRunHalInspector)
        btnCheckPermissions = findViewById(R.id.btnCheckPermissions)
        btnExportReport = findViewById(R.id.btnExportReport)
        btnCopyInParts = findViewById(R.id.btnCopyInParts)
        btnExportTxt = findViewById(R.id.btnExportTxt)
        btnShareTxt = findViewById(R.id.btnShareTxt)

        // Sunshade
        txtStatusSunshade = findViewById(R.id.txtStatusSunshade)
        txtDetailsSunshade = findViewById(R.id.txtDetailsSunshade)
        btnSunshadeOpen = findViewById(R.id.btnSunshadeOpen)
        btnSunshadeClose = findViewById(R.id.btnSunshadeClose)
        btnSunshadeStop = findViewById(R.id.btnSunshadeStop)

        // Moonroof
        txtStatusMoonroof = findViewById(R.id.txtStatusMoonroof)
        txtDetailsMoonroof = findViewById(R.id.txtDetailsMoonroof)
        btnMoonroofOpen = findViewById(R.id.btnMoonroofOpen)
        btnMoonroofClose = findViewById(R.id.btnMoonroofClose)
        btnMoonroofStop = findViewById(R.id.btnMoonroofStop)

        // Driver window
        txtStatusDriverWindow = findViewById(R.id.txtStatusDriverWindow)
        txtDetailsDriverWindow = findViewById(R.id.txtDetailsDriverWindow)
        btnDriverWindowOpen = findViewById(R.id.btnDriverWindowOpen)
        btnDriverWindowClose = findViewById(R.id.btnDriverWindowClose)
        btnDriverWindowStop = findViewById(R.id.btnDriverWindowStop)

        // AC
        txtStatusAC = findViewById(R.id.txtStatusAC)
        txtDetailsAC = findViewById(R.id.txtDetailsAC)
        btnAcOn = findViewById(R.id.btnAcOn)
        btnAcOff = findViewById(R.id.btnAcOff)

        // Interior light
        txtStatusInteriorLight = findViewById(R.id.txtStatusInteriorLight)
        txtDetailsInteriorLight = findViewById(R.id.txtDetailsInteriorLight)
        btnInteriorLightOn = findViewById(R.id.btnInteriorLightOn)
        btnInteriorLightOff = findViewById(R.id.btnInteriorLightOff)
    }

    private fun setupTabs() {
        btnTabDiagnostic.setOnClickListener {
            layoutTabDiagnostic.visibility = View.VISIBLE
            layoutTabControls.visibility = View.GONE
            btnTabDiagnostic.setBackgroundColor(Color.parseColor("#0284c7"))
            btnTabDiagnostic.setTextColor(Color.WHITE)
            btnTabControls.setBackgroundColor(Color.parseColor("#334155"))
            btnTabControls.setTextColor(Color.parseColor("#94a3b8"))
        }

        btnTabControls.setOnClickListener {
            layoutTabDiagnostic.visibility = View.GONE
            layoutTabControls.visibility = View.VISIBLE
            btnTabControls.setBackgroundColor(Color.parseColor("#0284c7"))
            btnTabControls.setTextColor(Color.WHITE)
            btnTabDiagnostic.setBackgroundColor(Color.parseColor("#334155"))
            btnTabDiagnostic.setTextColor(Color.parseColor("#94a3b8"))
            updateControlsUI()
        }
    }

    private fun setupDiagnosticListeners() {
        // 1️⃣ OPÇÃO 1: EXECUTAR DIAGNÓSTICO
        btnRunHalInspector.setOnClickListener {
            appendLog("==================================================")
            appendLog("🔍 INICIANDO DIAGNÓSTICO PROFUNDO DILINK HAL...")
            appendLog("Modo: STRICT SAFE READ-ONLY")
            appendLog("==================================================")
            Toast.makeText(this, "Iniciando varredura por reflexão...", Toast.LENGTH_SHORT).show()

            Thread {
                try {
                    val report = settingInspector.runDiscovery()
                    lastInspectionReport = report
                    controlManager.probeAllControls()
                    runOnUiThread {
                        appendLog("=== RESULTADO DO DIAGNÓSTICO ===\n$report")
                        updateControlsUI()
                        Toast.makeText(this@MainActivity, "Diagnóstico concluído!", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    runOnUiThread {
                        appendLog("❌ Falha na inspeção: ${t.javaClass.simpleName} - ${t.message}")
                    }
                }
            }.start()
        }

        // 2️⃣ OPÇÃO 2: PERMISSÕES
        btnCheckPermissions.setOnClickListener {
            appendLog("==================================================")
            appendLog("🛡️ VERIFICANDO PERMISSÕES DA CENTRAL...")
            appendLog("==================================================")
            Toast.makeText(this, "Verificando permissões...", Toast.LENGTH_SHORT).show()

            Thread {
                try {
                    val permsToTest = listOf(
                        "android.permission.BYDAUTO_LIGHT_GET",
                        "android.permission.BYDAUTO_LIGHT_SET",
                        "android.permission.BYDAUTO_BODYWORK_GET",
                        "android.permission.BYDAUTO_BODYWORK_SET",
                        "android.permission.BYDAUTO_SETTING_GET",
                        "android.permission.BYDAUTO_SETTING_SET",
                        "android.permission.BYDAUTO_AC_GET",
                        "android.permission.BYDAUTO_AC_SET",
                        "com.byd.permission.CAR_LIGHT_CONTROL",
                        "com.byd.permission.CAR_DOOR_CONTROL",
                        "android.car.permission.CONTROL_CAR_INTERIOR_LIGHTS",
                        "cc.omycar.magiccore.permission.API"
                    )
                    val sb = StringBuilder()
                    sb.append("===== ANÁLISE DE PERMISSÕES DILINK =====\n")
                    var grantedCount = 0

                    permsToTest.forEach { perm ->
                        val isGranted = permissionDiscovery.checkPermissionGranted(perm)
                        if (isGranted) grantedCount++
                        val statusStr = if (isGranted) "GRANTED ✅" else "DENIED ❌"
                        sb.append("• $perm: $statusStr\n")
                    }

                    val resultStr = sb.toString()
                    runOnUiThread {
                        appendLog(resultStr)
                        Toast.makeText(this@MainActivity, "Permissões: $grantedCount/${permsToTest.size} concedidas", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    runOnUiThread {
                        appendLog("❌ Falha ao verificar permissões: ${t.message}")
                    }
                }
            }.start()
        }

        // 3️⃣ OPÇÕES DE EXPORTAÇÃO
        btnExportReport.setOnClickListener {
            try {
                val fullReport = getFormattedReportWithFooter(getRawReportText())
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("BYD_Report", fullReport))
                appendLog("📋 RELATÓRIO INTEGRAL COPIADO!")
                Toast.makeText(this, "Relatório completo copiado!", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                appendLog("❌ Erro ao copiar relatório: ${t.message}")
            }
        }

        btnCopyInParts.setOnClickListener {
            try {
                val fullReport = getFormattedReportWithFooter(getRawReportText())
                val parts = splitReportIntoParts(fullReport, 30000)
                val totalParts = parts.size

                val items = parts.mapIndexed { idx, part ->
                    "Parte ${idx + 1}/$totalParts (${part.length} chars, ${part.lines().size} linhas)"
                }.toTypedArray()

                AlertDialog.Builder(this)
                    .setTitle("🧩 Copiar Relatório em Partes ($totalParts partes)")
                    .setItems(items) { _, which ->
                        val selectedPart = parts[which]
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("BYD_Part_${which + 1}", selectedPart))
                        appendLog("🧩 PARTE ${which + 1}/$totalParts COPIADA!")
                        Toast.makeText(this, "Parte ${which + 1}/$totalParts copiada!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Fechar", null)
                    .show()
            } catch (t: Throwable) {
                appendLog("❌ Erro ao dividir relatório: ${t.message}")
            }
        }

        btnExportTxt.setOnClickListener {
            try {
                val fullReport = getFormattedReportWithFooter(getRawReportText())
                val fileInfo = saveReportTxtToDownloads(fullReport)
                appendLog("💾 TXT SALVO: ${fileInfo.fileName} em ${fileInfo.fileAbsolutePath}")
                Toast.makeText(this, "TXT Salvo em Downloads: ${fileInfo.fileName}", Toast.LENGTH_LONG).show()
            } catch (t: Throwable) {
                appendLog("❌ Erro ao exportar TXT: ${t.message}")
            }
        }

        btnShareTxt.setOnClickListener {
            try {
                val fullReport = getFormattedReportWithFooter(getRawReportText())
                val fileInfo = saveReportTxtToDownloads(fullReport)
                shareReportTxtFile(fileInfo)
                appendLog("📤 COMPARTILHANDO ARQUIVO TXT: ${fileInfo.fileName}")
            } catch (t: Throwable) {
                appendLog("❌ Erro ao compartilhar TXT: ${t.message}")
            }
        }
    }

    private fun setupControlsListeners() {
        // 1. Sunshade
        btnSunshadeOpen.setOnClickListener { confirmAndExecute("SUNSHADE", ControlAction.ABRIR) }
        btnSunshadeClose.setOnClickListener { confirmAndExecute("SUNSHADE", ControlAction.FECHAR) }
        btnSunshadeStop.setOnClickListener { confirmAndExecute("SUNSHADE", ControlAction.PARAR) }

        // 2. Moonroof
        btnMoonroofOpen.setOnClickListener { confirmAndExecute("MOONROOF", ControlAction.ABRIR) }
        btnMoonroofClose.setOnClickListener { confirmAndExecute("MOONROOF", ControlAction.FECHAR) }
        btnMoonroofStop.setOnClickListener { confirmAndExecute("MOONROOF", ControlAction.PARAR) }

        // 3. Driver Window
        btnDriverWindowOpen.setOnClickListener { confirmAndExecute("DRIVER_WINDOW", ControlAction.ABRIR) }
        btnDriverWindowClose.setOnClickListener { confirmAndExecute("DRIVER_WINDOW", ControlAction.FECHAR) }
        btnDriverWindowStop.setOnClickListener { confirmAndExecute("DRIVER_WINDOW", ControlAction.PARAR) }

        // 4. AC
        btnAcOn.setOnClickListener { confirmAndExecute("AC", ControlAction.LIGAR) }
        btnAcOff.setOnClickListener { confirmAndExecute("AC", ControlAction.DESLIGAR) }

        // 5. Interior Light (Blocked)
        btnInteriorLightOn.setOnClickListener {
            Toast.makeText(this, "Controle de luz interna mantido bloqueado por segurança.", Toast.LENGTH_LONG).show()
        }
        btnInteriorLightOff.setOnClickListener {
            Toast.makeText(this, "Controle de luz interna mantido bloqueado por segurança.", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmAndExecute(controlId: String, action: ControlAction) {
        val ctrl = controlManager.controlsMap[controlId] ?: return

        if (ctrl.status == ControlStatus.BLOQUEADO) {
            AlertDialog.Builder(this)
                .setTitle("Controle Bloqueado")
                .setMessage("Este controle está BLOQUEADO pelo sistema.\n\nMotivo: ${ctrl.statusReason}")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("⚠️ Confirmação de Teste Físico")
            .setMessage("Veículo parado e em condição segura para executar este teste?\n\nControle: ${ctrl.title}\nAção: ${action.name}\nMétodo: ${ctrl.detectedMethod}")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Executar") { _, _ ->
                val result = controlManager.executeControlAction(controlId, action)
                updateControlsUI()
                Toast.makeText(this, result, Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun updateControlsUI() {
        controlManager.controlsMap["SUNSHADE"]?.let {
            applyControlToUI(it, txtStatusSunshade, txtDetailsSunshade, listOf(btnSunshadeOpen, btnSunshadeClose, btnSunshadeStop))
        }

        controlManager.controlsMap["MOONROOF"]?.let {
            applyControlToUI(it, txtStatusMoonroof, txtDetailsMoonroof, listOf(btnMoonroofOpen, btnMoonroofClose, btnMoonroofStop))
        }

        controlManager.controlsMap["DRIVER_WINDOW"]?.let {
            applyControlToUI(it, txtStatusDriverWindow, txtDetailsDriverWindow, listOf(btnDriverWindowOpen, btnDriverWindowClose, btnDriverWindowStop))
        }

        controlManager.controlsMap["AC"]?.let {
            applyControlToUI(it, txtStatusAC, txtDetailsAC, listOf(btnAcOn, btnAcOff))
        }

        controlManager.controlsMap["INTERIOR_LIGHT"]?.let {
            applyControlToUI(it, txtStatusInteriorLight, txtDetailsInteriorLight, listOf(btnInteriorLightOn, btnInteriorLightOff))
        }

        txtControlLogs.text = controlManager.getControlLogsReport()
    }

    private fun applyControlToUI(
        ctrl: com.byd.carcontrol.discovery.ControlInfo,
        txtStatus: TextView,
        txtDetails: TextView,
        buttons: List<Button>
    ) {
        txtStatus.text = ctrl.status.name
        when (ctrl.status) {
            ControlStatus.PRONTO -> {
                txtStatus.setBackgroundColor(Color.parseColor("#10b981"))
                buttons.forEach { it.isEnabled = true }
            }
            ControlStatus.BLOQUEADO -> {
                txtStatus.setBackgroundColor(Color.parseColor("#dc2626"))
                buttons.forEach { it.isEnabled = false }
            }
            ControlStatus.ERRO -> {
                txtStatus.setBackgroundColor(Color.parseColor("#f59e0b"))
                buttons.forEach { it.isEnabled = true }
            }
        }

        val detailsSb = StringBuilder()
        detailsSb.append("Classe Target: ${ctrl.targetClass}\n")
        detailsSb.append("Permissão: ${ctrl.requiredPermission}\n")
        if (ctrl.detectedMethod != null) {
            detailsSb.append("API Descoberta: ${ctrl.detectedMethod}\n")
        }
        if (ctrl.detectedArgsDesc != null) {
            detailsSb.append("Parâmetros: ${ctrl.detectedArgsDesc}\n")
        }
        detailsSb.append("Status: ${ctrl.statusReason}\n")
        if (ctrl.lastExecutionTime != null) {
            detailsSb.append("Última Execução (${ctrl.lastExecutionTime}): ${ctrl.lastResult}")
        }
        txtDetails.text = detailsSb.toString()
    }

    private fun getRawReportText(): String {
        val baseReport = if (!lastInspectionReport.isNullOrEmpty()) {
            lastInspectionReport!!
        } else {
            generateFullReport()
        }

        val logsReport = controlManager.getControlLogsReport()
        return "$baseReport\n\n$logsReport"
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

        json.put("appName", "BYD DiLink Car Control Lab")
        json.put("appVersion", "1.0.0")
        json.put("deviceModel", fw["ro.product.model"] ?: "BYD Dolphin Plus")
        json.put("androidVersion", fw["ro.build.version.release"] ?: "10")
        json.put("sdkVersion", fw["ro.build.version.sdk"] ?: "29")
        json.put("dilinkVersion", fw["dilink.version"] ?: "DiLink 3.0/4.0")
        json.put("mode", "DIAGNOSTIC & HARDWARE CONTROLS")

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
            txtActiveTransport.text = "Aba Ativa: DIAGNÓSTICO E CONTROLES DE HARDWARE"
        } catch (t: Throwable) {
            txtVehicleInfo.text = "BYD Dolphin Plus • DiLink 3.0/4.0 • Android 10 (API 29)"
            txtActiveTransport.text = "Aba Ativa: DIAGNÓSTICO E CONTROLES DE HARDWARE"
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
