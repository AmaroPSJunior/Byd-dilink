package com.byd.carcontrol

import android.content.ClipData
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import android.provider.Settings
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.byd.carcontrol.discovery.BYDBodyworkInspector
import com.byd.carcontrol.discovery.BYDControlManager
import com.byd.carcontrol.discovery.BYDLightHalInspector
import com.byd.carcontrol.discovery.BYDSettingInspector
import com.byd.carcontrol.discovery.PermissionDiscovery
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

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

    private enum class SunshadeMotion { OPENING, CLOSING }

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
    private lateinit var btnReadDrivingState: Button
    private lateinit var btnReadAccelerometer: Button
    private lateinit var btnOpenHvac: Button
    private lateinit var btnCheckPermissions: Button
    private lateinit var btnExportReport: Button
    private lateinit var btnCopyInParts: Button
    private lateinit var btnExportTxt: Button
    private lateinit var btnShareTxt: Button

    // Verified HVAC power controls and read-only seat-belt probe.
    private lateinit var btnHvacOn: Button
    private lateinit var btnHvacOff: Button
    private lateinit var btnReadSeatbelt: Button
    private lateinit var txtSeatbeltAccessStatus: TextView
    private lateinit var btnInteriorLightOn: Button
    private lateinit var btnInteriorLightOff: Button
    private lateinit var txtInteriorLightProbe: TextView
    private lateinit var txtClimateCommandStatus: TextView
    private lateinit var seekClimateFan: SeekBar
    private lateinit var txtClimateFanValue: TextView
    private lateinit var seekClimateTemperature: SeekBar
    private lateinit var txtClimateTemperatureValue: TextView
    private lateinit var txtClimateAdjustmentStatus: TextView
    private lateinit var btnOpenSunshade: Button
    private lateinit var btnCloseSunshade: Button
    private lateinit var seekSunshadePosition: SeekBar
    private lateinit var txtSunshadePercent: TextView
    private lateinit var txtSunshadeStatus: TextView
    private lateinit var txtWindowCommandStatus: TextView
    private lateinit var windowViews: List<WindowUi>
    private var sunshadeMotion: SunshadeMotion? = null
    private var sunshadePosition = 0
    private var sunshadeCommandId = 0
    private val sunshadeCommandExecutor = Executors.newSingleThreadExecutor()
    private var climateTemperatureMaxCelsius = 33.0
    private var climateTemperatureStepCelsius = 1.0
    private val climateCommandExecutor = Executors.newSingleThreadExecutor()
    private val windowCommandExecutor = Executors.newSingleThreadExecutor()

    private data class WindowUi(
        val window: BydWindowControl.Window,
        val label: TextView,
        val seek: SeekBar,
        val open: Button,
        val close: Button
    )

    private val logHistory = mutableListOf<String>()
    private var lastInspectionReport: String? = null
    private val seatbeltHandler = Handler(Looper.getMainLooper())
    private var seatbeltPolling = false
    private var activityResumed = false

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
        btnReadDrivingState = findViewById(R.id.btnReadDrivingState)
        btnReadAccelerometer = findViewById(R.id.btnReadAccelerometer)
        btnOpenHvac = findViewById(R.id.btnOpenHvac)
        btnCheckPermissions = findViewById(R.id.btnCheckPermissions)
        btnExportReport = findViewById(R.id.btnExportReport)
        btnCopyInParts = findViewById(R.id.btnCopyInParts)
        btnExportTxt = findViewById(R.id.btnExportTxt)
        btnShareTxt = findViewById(R.id.btnShareTxt)

        btnHvacOn = findViewById(R.id.btnHvacOn)
        btnHvacOff = findViewById(R.id.btnHvacOff)
        btnReadSeatbelt = findViewById(R.id.btnReadSeatbelt)
        txtSeatbeltAccessStatus = findViewById(R.id.txtSeatbeltAccessStatus)
        btnInteriorLightOn = findViewById(R.id.btnInteriorLightOn)
        btnInteriorLightOff = findViewById(R.id.btnInteriorLightOff)
        txtInteriorLightProbe = findViewById(R.id.txtInteriorLightProbe)
        txtClimateCommandStatus = findViewById(R.id.txtClimateCommandStatus)
        seekClimateFan = findViewById(R.id.seekClimateFan)
        txtClimateFanValue = findViewById(R.id.txtClimateFanValue)
        seekClimateTemperature = findViewById(R.id.seekClimateTemperature)
        txtClimateTemperatureValue = findViewById(R.id.txtClimateTemperatureValue)
        txtClimateAdjustmentStatus = findViewById(R.id.txtClimateAdjustmentStatus)
        txtWindowCommandStatus = findViewById(R.id.txtWindowCommandStatus)
        windowViews = listOf(
            WindowUi(BydWindowControl.Window.DRIVER_FRONT, findViewById(R.id.txtWindowDriverFront), findViewById(R.id.seekWindowDriverFront), findViewById(R.id.btnWindowDriverFrontOpen), findViewById(R.id.btnWindowDriverFrontClose)),
            WindowUi(BydWindowControl.Window.PASSENGER_FRONT, findViewById(R.id.txtWindowPassengerFront), findViewById(R.id.seekWindowPassengerFront), findViewById(R.id.btnWindowPassengerFrontOpen), findViewById(R.id.btnWindowPassengerFrontClose)),
            WindowUi(BydWindowControl.Window.DRIVER_REAR, findViewById(R.id.txtWindowDriverRear), findViewById(R.id.seekWindowDriverRear), findViewById(R.id.btnWindowDriverRearOpen), findViewById(R.id.btnWindowDriverRearClose)),
            WindowUi(BydWindowControl.Window.PASSENGER_REAR, findViewById(R.id.txtWindowPassengerRear), findViewById(R.id.seekWindowPassengerRear), findViewById(R.id.btnWindowPassengerRearOpen), findViewById(R.id.btnWindowPassengerRearClose))
        )
        btnOpenSunshade = findViewById(R.id.btnOpenSunshade)
        btnCloseSunshade = findViewById(R.id.btnCloseSunshade)
        seekSunshadePosition = findViewById(R.id.seekSunshadePosition)
        txtSunshadePercent = findViewById(R.id.txtSunshadePercent)
        txtSunshadeStatus = findViewById(R.id.txtSunshadeStatus)
    }

    private fun setupTabs() {
        btnTabDiagnostic.setOnClickListener {
            stopSeatbeltPolling()
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
            startSeatbeltPolling()
            refreshSunshadePosition()
            refreshClimateAdjustmentState()
            refreshWindowStates()
        }
    }

    private fun setupDiagnosticListeners() {
        btnOpenHvac.setOnClickListener {
            try {
                startActivity(Intent("OPEN_AIR_CONDITIONING").setPackage("com.byd.airconditioning"))
                appendLog("Abrindo o painel OEM de climatização; nenhum valor HVAC foi enviado pelo app.")
            } catch (t: Throwable) {
                appendLog("Painel HVAC OEM indisponível: ${t.javaClass.simpleName}: ${t.message}")
            }
        }

        btnReadAccelerometer.setOnClickListener {
            appendLog("Lendo acelerômetro inercial Android da central (não é telemetria CAN)...")
            HeadUnitSensorReader.readAccelerometer(this) { result ->
                result.onSuccess { reading ->
                    appendLog(
                        "Acelerômetro ${reading.sensorName}: " +
                            "x=${reading.x} m/s², y=${reading.y} m/s², z=${reading.z} m/s² " +
                            "(amostra monotônica ${reading.timestampNanos} ns). " +
                            "Sensor da central; não representa velocidade nem marcha."
                    )
                }.onFailure { error ->
                    appendLog("Leitura do acelerômetro falhou: ${error.message}")
                }
            }
        }

        // Direct read-only call through BYD AppServer's exported AIDL provider.
        btnReadDrivingState.setOnClickListener {
            appendLog("Lendo getDrivingState() pelo provider IPC BYD (somente leitura)...")
            Thread {
                try {
                    val rawState = BydDrivingStateReader.readRawState(this)
                    runOnUiThread {
                        appendLog(
                            "Leitura real recebida: getDrivingState() = $rawState. " +
                                "Enumeração ainda não mapeada; nenhum significado foi presumido."
                        )
                    }
                } catch (t: Throwable) {
                    Log.e("BydDrivingStateReader", "Falha na leitura AIDL", t)
                    runOnUiThread {
                        appendLog(
                            "Leitura AIDL falhou: ${t.javaClass.simpleName}: ${t.message}. " +
                                "Nenhum comando de escrita foi enviado."
                        )
                    }
                }
            }.start()
        }

        // 1️⃣ OPÇÃO 1: EXECUTAR DIAGNÓSTICO
        btnRunHalInspector.setOnClickListener {
            appendLog("==================================================")
            appendLog("🔍 INICIANDO DIAGNÓSTICO PROFUNDO DILINK HAL...")
            appendLog("Modo: STRICT SAFE READ-ONLY")
            appendLog("==================================================")
            Toast.makeText(this, "Iniciando varredura por reflexão...", Toast.LENGTH_SHORT).show()

            Thread {
                try {
                    val settingReport = settingInspector.runDiscovery()
                    val bodyworkReport = bodyworkInspector.runDiscovery()
                    val report = "$settingReport\n\n$bodyworkReport"
                    lastInspectionReport = report
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
                        val isGranted = try {
                            checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        } catch (_: Throwable) {
                            false
                        }
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
        btnHvacOn.setOnClickListener { requestHvacPower(true) }
        btnHvacOff.setOnClickListener { requestHvacPower(false) }
        btnReadSeatbelt.setOnClickListener {
            readAndRenderSeatbeltState()
        }
        btnInteriorLightOn.setOnClickListener { requestInteriorLightPower(true) }
        btnInteriorLightOff.setOnClickListener { requestInteriorLightPower(false) }
        btnOpenSunshade.setOnClickListener {
            if (sunshadeMotion == SunshadeMotion.OPENING) requestSunshadeStop()
            else requestSunshadePosition(100, SunshadeMotion.OPENING)
        }
        btnCloseSunshade.setOnClickListener {
            if (sunshadeMotion == SunshadeMotion.CLOSING) requestSunshadeStop()
            else requestSunshadePosition(0, SunshadeMotion.CLOSING)
        }
        seekSunshadePosition.max = 100
        seekSunshadePosition.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) txtSunshadePercent.text = "POSIÇÃO DESEJADA: $progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val target = seekBar.progress
                val motion = when {
                    target > sunshadePosition -> SunshadeMotion.OPENING
                    target < sunshadePosition -> SunshadeMotion.CLOSING
                    else -> null
                }
                requestSunshadePosition(target, motion)
            }
        })
        windowViews.forEach { ui ->
            ui.open.setOnClickListener { sendWindowCommand(ui, target = 100, fullTravel = true) }
            ui.close.setOnClickListener { sendWindowCommand(ui, target = 0, fullTravel = true) }
            ui.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) ui.label.text = "${ui.window.label}: alvo $progress%"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) { sendWindowCommand(ui, seekBar.progress, fullTravel = false) }
            })
        }
        updateSunshadeButtons()

        seekClimateFan.max = 6
        seekClimateFan.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                txtClimateFanValue.text = "VENTILAÇÃO: ${progress + 1}/7"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                requestClimateAdjustment("Ajustando ventilação…") {
                    BydClimateAdjustment.setWindLevel(this@MainActivity, seekBar.progress + 1)
                }
            }
        })
        seekClimateTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val celsius = 17.0 + progress * climateTemperatureStepCelsius
                txtClimateTemperatureValue.text = "TEMPERATURA: ${formatTemperature(celsius)} °C"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val celsius = 17.0 + seekBar.progress * climateTemperatureStepCelsius
                requestClimateAdjustment("Ajustando temperatura…") {
                    BydClimateAdjustment.setTemperatureCelsius(this@MainActivity, celsius)
                }
            }
        })
    }

    private fun refreshClimateAdjustmentState() {
        Thread {
            val result = runCatching { BydClimateAdjustment.read(this) }
            runOnUiThread {
                result.onSuccess(::renderClimateAdjustmentState)
                    .onFailure { error ->
                        txtClimateAdjustmentStatus.text = "Leitura HVAC indisponível: ${error.cause?.message ?: error.message}"
                    }
            }
        }.start()
    }

    private fun refreshWindowStates() {
        windowViews.forEach { ui ->
            Thread {
                val result = runCatching { BydWindowControl.read(this, ui.window) }
                runOnUiThread {
                    result.onSuccess { state ->
                        val percent = state.percent
                        ui.label.text = when {
                            percent != null -> "${ui.window.label}: ${percent}% · estado=${state.state} · permissão=${state.permit}"
                            else -> "${ui.window.label}: posição indisponível"
                        }
                        if (percent != null && !ui.seek.isPressed) ui.seek.progress = percent
                    }.onFailure { error ->
                        ui.label.text = "${ui.window.label}: leitura falhou (${error.cause?.message ?: error.message})"
                    }
                }
            }.start()
        }
    }

    private fun sendWindowCommand(ui: WindowUi, target: Int, fullTravel: Boolean) {
        txtWindowCommandStatus.text = "Enviando posição ${target}% para ${ui.window.label}…"
        windowCommandExecutor.execute {
            val result = runCatching {
                if (fullTravel) BydWindowControl.setFullyOpenOrClosed(this, ui.window, target == 100)
                else BydWindowControl.setPosition(this, ui.window, target)
            }
            runOnUiThread {
                result.onSuccess { txtWindowCommandStatus.text = it }
                    .onFailure { error -> txtWindowCommandStatus.text = "Comando recusado/falhou: ${error.cause?.message ?: error.message}" }
                refreshWindowStates()
            }
        }
    }

    private fun requestClimateAdjustment(pendingMessage: String, command: () -> String) {
        txtClimateAdjustmentStatus.text = pendingMessage
        climateCommandExecutor.execute {
            val result = runCatching { command() }
            val snapshot = runCatching { BydClimateAdjustment.read(this) }
            runOnUiThread {
                result.onSuccess { txtClimateAdjustmentStatus.text = it }
                    .onFailure { error -> txtClimateAdjustmentStatus.text = "Comando HVAC falhou: ${error.cause?.message ?: error.message}" }
                snapshot.onSuccess(::renderClimateAdjustmentState)
            }
        }
    }

    private fun renderClimateAdjustmentState(snapshot: BydClimateAdjustment.Snapshot) {
        climateTemperatureMaxCelsius = snapshot.maxTemperatureCelsius.toDouble()
        climateTemperatureStepCelsius = snapshot.temperatureStepCelsius
        seekClimateFan.progress = (snapshot.windLevel - 1).coerceIn(0, 6)
        txtClimateFanValue.text = if (snapshot.windLevel == 0) "VENTILAÇÃO: desligada" else "VENTILAÇÃO: ${snapshot.windLevel}/7"
        seekClimateTemperature.max = ((climateTemperatureMaxCelsius - 17.0) / climateTemperatureStepCelsius).toInt()
        snapshot.temperatureCelsius?.let { celsius ->
            val progress = ((celsius - 17.0) / climateTemperatureStepCelsius).toInt()
                .coerceIn(0, seekClimateTemperature.max)
            seekClimateTemperature.progress = progress
            txtClimateTemperatureValue.text = "TEMPERATURA: ${formatTemperature(celsius)} °C"
        } ?: run { txtClimateTemperatureValue.text = "TEMPERATURA: indisponível" }
        txtClimateAdjustmentStatus.text = snapshot.detail
    }

    private fun formatTemperature(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

    private fun refreshSunshadePosition() {
        Thread {
            val percent = BydSunshadeControl.readPosition(this)
            runOnUiThread {
                if (percent != null && !seekSunshadePosition.isPressed) {
                    renderSunshadePosition(percent)
                }
            }
        }.start()
    }

    private fun requestSunshadeStop() {
        val previousMotion = sunshadeMotion
        sunshadeMotion = null
        updateSunshadeButtons()
        executeSunshadeCommand(stop = true, previousMotion = previousMotion)
    }

    private fun requestSunshadePosition(percent: Int, motion: SunshadeMotion?) {
        val previousMotion = sunshadeMotion
        sunshadeMotion = motion
        updateSunshadeButtons()
        executeSunshadeCommand(stop = false, percent = percent, previousMotion = previousMotion)
    }

    private fun executeSunshadeCommand(
        stop: Boolean,
        percent: Int = sunshadePosition,
        previousMotion: SunshadeMotion? = null
    ) {
        val commandId = ++sunshadeCommandId
        txtSunshadeStatus.text = if (stop) "Enviando parada imediata pela API OEM…"
            else "Solicitando posição $percent% pela API OEM…"
        sunshadeCommandExecutor.execute {
            val result = runCatching {
                if (stop) BydSunshadeControl.stop(this) else BydSunshadeControl.setPosition(this, percent)
            }
            runOnUiThread {
                if (commandId == sunshadeCommandId) {
                    txtSunshadeStatus.text = result.fold(
                        onSuccess = { response ->
                            response.observedPercent?.let(::renderSunshadePosition)
                            if (!response.accepted) {
                                sunshadeMotion = previousMotion
                                updateSunshadeButtons()
                            }
                            response.detail
                        },
                        onFailure = { error ->
                            sunshadeMotion = previousMotion
                            updateSunshadeButtons()
                            "Falha ao mover a persiana: ${error.cause?.javaClass?.simpleName ?: error.javaClass.simpleName}: ${error.cause?.message ?: error.message}"
                        }
                    )
                    appendLog(txtSunshadeStatus.text.toString())
                    if (result.getOrNull()?.accepted == true) {
                        pollSunshadePosition(commandId, if (stop) null else percent)
                    }
                }
            }
        }
    }

    private fun pollSunshadePosition(commandId: Int, target: Int?) {
        Thread {
            val startedAt = SystemClock.elapsedRealtime()
            var consecutiveTargetReads = 0
            repeat(40) {
                Thread.sleep(250L)
                val position = BydSunshadeControl.readPosition(this) ?: return@repeat
                val atTarget = target != null && kotlin.math.abs(position - target) <= 2
                consecutiveTargetReads = if (atTarget) consecutiveTargetReads + 1 else 0
                runOnUiThread {
                    if (commandId == sunshadeCommandId) {
                        renderSunshadePosition(position)
                        if (atTarget && consecutiveTargetReads >= 3 &&
                            SystemClock.elapsedRealtime() - startedAt >= 1_800L
                        ) {
                            sunshadeMotion = null
                            updateSunshadeButtons()
                            txtSunshadeStatus.text = "Posição $position% alcançada."
                        }
                    }
                }
                if (commandId != sunshadeCommandId) return@Thread
                if (atTarget && consecutiveTargetReads >= 3 &&
                    SystemClock.elapsedRealtime() - startedAt >= 1_800L
                ) return@Thread
                if (target == null && it >= 3) {
                    runOnUiThread {
                        if (commandId == sunshadeCommandId) {
                            sunshadeMotion = null
                            updateSunshadeButtons()
                            txtSunshadeStatus.text = "Comando de parada enviado; posição atual ${position}%."
                        }
                    }
                    return@Thread
                }
            }
        }.start()
    }

    private fun renderSunshadePosition(percent: Int) {
        sunshadePosition = percent.coerceIn(0, 100)
        if (!seekSunshadePosition.isPressed) seekSunshadePosition.progress = sunshadePosition
        txtSunshadePercent.text = "POSIÇÃO LIDA: $sunshadePosition%"
    }

    private fun updateSunshadeButtons() {
        btnOpenSunshade.text = if (sunshadeMotion == SunshadeMotion.OPENING) "PARAR ABERTURA" else "ABRIR"
        btnCloseSunshade.text = if (sunshadeMotion == SunshadeMotion.CLOSING) "PARAR FECHAMENTO" else "FECHAR"
    }

    private fun requestInteriorLightPower(turnOn: Boolean) {
        val action = if (turnOn) "ligar" else "desligar"
        txtInteriorLightProbe.text = "Enviando comando para $action a luz interna…"
        btnInteriorLightOn.isEnabled = false
        btnInteriorLightOff.isEnabled = false
        Thread {
            val result = runCatching { BydInteriorLightControl.setPower(this, turnOn) }
            runOnUiThread {
                btnInteriorLightOn.isEnabled = true
                btnInteriorLightOff.isEnabled = true
                txtInteriorLightProbe.text = result.fold(
                    onSuccess = { response ->
                        when {
                            !response.accepted ->
                            "O HAL BYD recusou o comando para $action (${response.detail})."
                            response.observedState == response.requestedState ->
                                "Comando $action aceito; o HAL reporta o estado solicitado (${response.detail})."
                            else ->
                                "Comando $action aceito pelo HAL, mas o estado pedido (${response.requestedState}) não apareceu na leitura (${response.detail}); efeito da lâmpada não confirmado."
                        }
                    },
                    onFailure = { error ->
                        "Falha ao $action a luz interna: ${error.cause?.javaClass?.simpleName ?: error.javaClass.simpleName}: ${error.cause?.message ?: error.message}"
                    }
                )
                appendLog(txtInteriorLightProbe.text.toString())
            }
        }.start()
    }

    private fun openOemPanel(intent: Intent, successMessage: String) {
        try {
            startActivity(intent)
            appendLog(successMessage)
        } catch (t: Throwable) {
            appendLog("Painel OEM indisponível: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun requestHvacPower(turnOn: Boolean) {
        if (!isClimateAccessibilityEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Ative o serviço de acessibilidade")
                .setMessage(
                    "Para controlar o HVAC, o serviço restrito do BYD Controller precisa ser ativado em " +
                        "Acessibilidade. Ele só processa comandos quando o painel oficial com.byd.airconditioning está aberto."
                )
                .setNegativeButton("Agora não", null)
                .setPositiveButton("Abrir configurações") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .show()
            return
        }

        getSharedPreferences(ClimateAccessibilityService.PREFS_NAME, MODE_PRIVATE).edit()
            .putInt(ClimateAccessibilityService.KEY_PENDING_POWER, if (turnOn) 1 else 0)
            .putLong(ClimateAccessibilityService.KEY_REQUEST_TIME, System.currentTimeMillis())
            .putString(ClimateAccessibilityService.KEY_RESULT, "Comando enviado ao painel OEM; aguardando confirmação…")
            .apply()
        updateClimateCommandStatus()
        openOemPanel(
            Intent("OPEN_AIR_CONDITIONING").setPackage("com.byd.airconditioning"),
            "Painel OEM aberto para ${if (turnOn) "ligar" else "desligar"} o ar-condicionado."
        )
    }

    private fun isClimateAccessibilityEnabled(): Boolean {
        val manager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                info.resolveInfo?.serviceInfo?.let { service ->
                    service.packageName == packageName && service.name == ClimateAccessibilityService::class.java.name
                } == true
            }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        if (::txtClimateCommandStatus.isInitialized) updateClimateCommandStatus()
        if (::layoutTabControls.isInitialized && layoutTabControls.visibility == View.VISIBLE) {
            startSeatbeltPolling()
        }
    }

    override fun onPause() {
        activityResumed = false
        stopSeatbeltPolling()
        super.onPause()
    }

    private val seatbeltPoll = object : Runnable {
        override fun run() {
            if (!seatbeltPolling || !activityResumed || layoutTabControls.visibility != View.VISIBLE) return
            readAndRenderSeatbeltState()
            seatbeltHandler.postDelayed(this, 2000L)
        }
    }

    private fun startSeatbeltPolling() {
        if (!activityResumed || !::layoutTabControls.isInitialized || layoutTabControls.visibility != View.VISIBLE) return
        if (seatbeltPolling) return
        seatbeltPolling = true
        seatbeltHandler.post(seatbeltPoll)
    }

    private fun stopSeatbeltPolling() {
        seatbeltPolling = false
        seatbeltHandler.removeCallbacks(seatbeltPoll)
    }

    private fun readAndRenderSeatbeltState() {
        Thread {
            val result = runCatching { BydSeatbeltReader.readDriverState(this) }
            runOnUiThread {
                result.onSuccess { state ->
                    txtSeatbeltAccessStatus.text = when (state.raw) {
                        state.unlocked -> "⚠️ CINTO DO MOTORISTA DESAFIVELADO"
                        state.locked -> "✓ Cinto do motorista afivelado"
                        state.invalid -> "Estado do cinto indisponível (SDK retornou INVALID)."
                        else -> "Estado do cinto desconhecido: ${state.raw}"
                    }
                    txtSeatbeltAccessStatus.setTextColor(
                        if (state.raw == state.unlocked) Color.parseColor("#fca5a5")
                        else Color.parseColor("#fde68a")
                    )
                }.onFailure { error ->
                    val cause = error.cause ?: error
                    txtSeatbeltAccessStatus.text =
                        "Leitura do sensor indisponível: ${cause.javaClass.simpleName}: ${cause.message}"
                    txtSeatbeltAccessStatus.setTextColor(Color.parseColor("#fde68a"))
                }
            }
        }.start()
    }

    private fun updateClimateCommandStatus() {
        val prefs = getSharedPreferences(ClimateAccessibilityService.PREFS_NAME, MODE_PRIVATE)
        txtClimateCommandStatus.text = prefs.getString(
            ClimateAccessibilityService.KEY_RESULT,
            "Nenhum comando de climatização confirmado nesta sessão."
        )
    }

    private fun updateControlsUI() {
        updateClimateCommandStatus()
        txtControlLogs.text = "Ar-condicionado: comandos ON/OFF confirmados pelo painel OEM.\n" +
            "Cinto: leitura de diagnóstico somente; nenhum estado será inferido sem validação.\n" +
            "Luz de teto: a API específica deste veículo ainda não foi confirmada."
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
