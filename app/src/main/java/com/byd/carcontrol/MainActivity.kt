package com.byd.carcontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Color
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
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
    private lateinit var btnTabInspector: Button
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
    private lateinit var switchSeatbeltAudio: CompoundButton
    private lateinit var editSeatbeltSpeed: EditText
    private var seatbeltVoiceEnabled = true
    private var seatbeltAlertSpeedKmh = 10
    private lateinit var seatbeltDiagram: SeatbeltStatusView
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
    private lateinit var btnWebServer: Button
    private lateinit var txtWebServerAddress: TextView
    private lateinit var imgWebServerQr: ImageView
    private var lastWebQrUrl: String? = null
    private val webServerUiPoll = object : Runnable {
        override fun run() {
            if (::btnWebServer.isInitialized) refreshWebServerUi()
            if (activityResumed) seatbeltHandler.postDelayed(this, 1500L)
        }
    }
    private lateinit var windowViews: List<WindowUi>
    private var sunshadeMotion: SunshadeMotion? = null
    private var sunshadePosition = 0
    private var sunshadeCommandId = 0
    private val sunshadeCommandExecutor = Executors.newSingleThreadExecutor()
    private var climateTemperatureMaxCelsius = 33.0
    private var climateTemperatureStepCelsius = 1.0
    private val windowCommandExecutor = Executors.newSingleThreadExecutor()

    private data class WindowUi(
        val window: BydWindowControl.Window,
        val label: TextView,
        val seek: SeekBar,
        val open: Button,
        val close: Button,
        val apply: Button
    )

    private val logHistory = mutableListOf<String>()
    private var lastInspectionReport: String? = null
    private val seatbeltHandler = Handler(Looper.getMainLooper())
    private var seatbeltPolling = false
    private var activityResumed = false
    private var seatbeltVoiceAnnouncer: SeatbeltVoiceAnnouncer? = null
    private var previouslyUnbuckled = emptySet<String>()
    @Volatile private var seatbeltReadInFlight = false
    private var hvacCommandStatus = "Climatização aguardando comando."

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
            seatbeltVoiceAnnouncer = SeatbeltVoiceAnnouncer(this)

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
        btnTabInspector = findViewById(R.id.btnTabInspector)
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
        switchSeatbeltAudio = findViewById(R.id.switchSeatbeltAudio)
        editSeatbeltSpeed = findViewById(R.id.editSeatbeltSpeed)
        val beltPrefs = getSharedPreferences("seatbelt_alerts", MODE_PRIVATE)
        seatbeltVoiceEnabled = beltPrefs.getBoolean("voice_enabled", true)
        seatbeltAlertSpeedKmh = beltPrefs.getInt("alert_speed_kmh", 10).coerceIn(1, 200)
        switchSeatbeltAudio.isChecked = seatbeltVoiceEnabled
        editSeatbeltSpeed.setText(seatbeltAlertSpeedKmh.toString())
        seatbeltDiagram = findViewById(R.id.seatbeltDiagram)
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
        btnWebServer = findViewById(R.id.btnWebServer)
        txtWebServerAddress = findViewById(R.id.txtWebServerAddress)
        imgWebServerQr = findViewById(R.id.imgWebServerQr)
        windowViews = listOf(
            WindowUi(BydWindowControl.Window.DRIVER_FRONT, findViewById(R.id.txtWindowDriverFront), findViewById(R.id.seekWindowDriverFront), findViewById(R.id.btnWindowDriverFrontOpen), findViewById(R.id.btnWindowDriverFrontClose), findViewById(R.id.btnWindowDriverFrontApply)),
            WindowUi(BydWindowControl.Window.PASSENGER_FRONT, findViewById(R.id.txtWindowPassengerFront), findViewById(R.id.seekWindowPassengerFront), findViewById(R.id.btnWindowPassengerFrontOpen), findViewById(R.id.btnWindowPassengerFrontClose), findViewById(R.id.btnWindowPassengerFrontApply)),
            WindowUi(BydWindowControl.Window.DRIVER_REAR, findViewById(R.id.txtWindowDriverRear), findViewById(R.id.seekWindowDriverRear), findViewById(R.id.btnWindowDriverRearOpen), findViewById(R.id.btnWindowDriverRearClose), findViewById(R.id.btnWindowDriverRearApply)),
            WindowUi(BydWindowControl.Window.PASSENGER_REAR, findViewById(R.id.txtWindowPassengerRear), findViewById(R.id.seekWindowPassengerRear), findViewById(R.id.btnWindowPassengerRearOpen), findViewById(R.id.btnWindowPassengerRearClose), findViewById(R.id.btnWindowPassengerRearApply))
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

        btnTabInspector.setOnClickListener {
            startActivity(Intent(this, com.byd.carcontrol.inspector.DiLinkInspectorActivity::class.java))
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
        btnWebServer.setOnClickListener {
            val prefs = LocalCarWebService.prefs(this)
            if (prefs.getBoolean(LocalCarWebService.KEY_RUNNING, false)) {
                startService(Intent(this, LocalCarWebService::class.java).setAction(LocalCarWebService.ACTION_STOP))
            } else {
                val intent = Intent(this, LocalCarWebService::class.java)
                if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(this, intent)
                else startService(intent)
            }
            seatbeltHandler.postDelayed({ refreshWebServerUi() }, 500L)
        }
        btnHvacOn.setOnClickListener { requestHvacPower(true) }
        btnHvacOff.setOnClickListener { requestHvacPower(false) }
        btnReadSeatbelt.setOnClickListener {
            readAndRenderSeatbeltState()
        }
        switchSeatbeltAudio.setOnCheckedChangeListener { _, enabled ->
            seatbeltVoiceEnabled = enabled
            getSharedPreferences("seatbelt_alerts", MODE_PRIVATE).edit().putBoolean("voice_enabled", enabled).apply()
            if (!enabled) seatbeltVoiceAnnouncer?.stop()
            else {
                previouslyUnbuckled = emptySet()
                readAndRenderSeatbeltState()
            }
        }
        editSeatbeltSpeed.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveSeatbeltAlertSpeed()
        }
        editSeatbeltSpeed.setOnEditorActionListener { _, _, _ -> saveSeatbeltAlertSpeed(); false }
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
            ui.apply.setOnClickListener { sendWindowCommand(ui, ui.seek.progress, fullTravel = false) }
            ui.seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) ui.label.text = "${ui.window.label}: alvo $progress% (selecione APLICAR)"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    val target = when {
                        seekBar.progress < 25 -> 0
                        seekBar.progress < 75 -> 50
                        else -> 100
                    }
                    seekBar.progress = target
                    ui.label.text = "${ui.window.label}: alvo $target% — toque APLICAR"
                }
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
        runCatching { BydClimateAdjustment.read(this) }
            .onSuccess(::renderClimateAdjustmentState)
            .onFailure { error ->
                txtClimateAdjustmentStatus.text = "Leitura HVAC indisponível: ${error.cause?.message ?: error.message}"
            }
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
                else BydWindowControl.setPresetPosition(this, ui.window, target)
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
        val result = runCatching { command() }
        val snapshot = runCatching { BydClimateAdjustment.read(this) }
        result.onSuccess { txtClimateAdjustmentStatus.text = it }
            .onFailure { error -> txtClimateAdjustmentStatus.text = "Comando HVAC falhou: ${error.cause?.message ?: error.message}" }
        snapshot.onSuccess(::renderClimateAdjustmentState)
            .onFailure { error -> txtClimateAdjustmentStatus.text = "${txtClimateAdjustmentStatus.text} Leitura: ${error.cause?.message ?: error.message}" }
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
        val direct = runCatching { BydClimateAdjustment.setPower(this, turnOn) }
        if (direct.isSuccess) {
            hvacCommandStatus = direct.getOrThrow()
            updateClimateCommandStatus()
            seatbeltHandler.postDelayed({
                val state = runCatching { BydClimateAdjustment.readPowerState(this) }
                hvacCommandStatus += state.fold(
                    { "; estado OEM após comando=$it." },
                    { "; não foi possível confirmar o estado: ${it.cause?.message ?: it.message}" }
                )
                updateClimateCommandStatus()
            }, 500)
            return
        }

        val reason = direct.exceptionOrNull()?.let { it.cause?.message ?: it.message } ?: "sem detalhe"
        val panelIntent = Intent("OPEN_AIR_CONDITIONING").setPackage("com.byd.airconditioning")
        if (isClimateAccessibilityEnabled()) {
            getSharedPreferences(ClimateAccessibilityService.PREFS_NAME, MODE_PRIVATE).edit()
                .putInt(ClimateAccessibilityService.KEY_PENDING_POWER, if (turnOn) 1 else 0)
                .putLong(ClimateAccessibilityService.KEY_REQUEST_TIME, System.currentTimeMillis())
                .putString(ClimateAccessibilityService.KEY_RESULT, "API direta recusada ($reason); aguardando confirmação da tela OEM…")
                .apply()
            hvacCommandStatus = "API direta sem permissão; usando o controle OEM já autorizado, sem abrir configurações."
            openOemPanel(panelIntent, "Painel OEM aberto para concluir o comando HVAC com o serviço já ativo.")
            seatbeltHandler.postDelayed({
                val result = getSharedPreferences(ClimateAccessibilityService.PREFS_NAME, MODE_PRIVATE)
                    .getString(ClimateAccessibilityService.KEY_RESULT, null)
                if (!result.isNullOrBlank()) hvacCommandStatus = result
                updateClimateCommandStatus()
            }, 2_000)
        } else {
            hvacCommandStatus = "API direta recusada ($reason). Abrindo painel oficial para controle manual; nenhuma tela de configurações será aberta."
            openOemPanel(panelIntent, "Painel oficial HVAC aberto; a permissão do sistema impede o controle direto pelo app.")
        }
        updateClimateCommandStatus()
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
        seatbeltHandler.removeCallbacks(webServerUiPoll)
        seatbeltHandler.post(webServerUiPoll)
        if (::txtClimateCommandStatus.isInitialized) updateClimateCommandStatus()
        if (::layoutTabControls.isInitialized && layoutTabControls.visibility == View.VISIBLE) {
            startSeatbeltPolling()
        }
    }

    override fun onPause() {
        activityResumed = false
        seatbeltHandler.removeCallbacks(webServerUiPoll)
        stopSeatbeltPolling()
        super.onPause()
    }

    override fun onDestroy() {
        seatbeltVoiceAnnouncer?.release()
        seatbeltVoiceAnnouncer = null
        super.onDestroy()
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
        if (seatbeltReadInFlight) return
        seatbeltReadInFlight = true
        Thread {
            val result = runCatching {
                val belt = BydSeatbeltReader.readAll(this)
                val speed = runCatching { BydVehicleSpeedReader.readKmh(this) }.getOrNull()
                belt to speed
            }
            runOnUiThread {
                seatbeltReadInFlight = false
                result.onSuccess { (state, speed) ->
                    val byName = state.seats.associate { it.key to it.raw }
                    seatbeltDiagram.setSeatStates(byName)
                    val unbuckled = state.seats.filter { it.raw == 2 }
                    val unavailable = state.seats.filter { it.raw == 0 }
                    val speedEligible = speed != null && speed.isFinite() && speed >= seatbeltAlertSpeedKmh
                    val eligibleWarnings = if (speedEligible) unbuckled else emptyList()
                    txtSeatbeltAccessStatus.text = buildString {
                        if (unbuckled.isEmpty()) append("✓ Cinto do motorista não está reportado como desafivelado.")
                        else append("⚠ Desafivelados: ${unbuckled.joinToString { it.label }}.")
                        if (unavailable.isNotEmpty()) append(" Estado indisponível: ${unavailable.joinToString { it.label }}.")
                        append(" Velocidade: ${speed?.let { "%.0f".format(Locale.getDefault(), it) + " km/h" } ?: "indisponível"}; alerta a partir de $seatbeltAlertSpeedKmh km/h.")
                        if (seatbeltVoiceEnabled) append(if (speedEligible) " Aviso sonoro habilitado." else " Abaixo do limite, sem aviso sonoro.")
                        else append(" Aviso sonoro desativado.")
                    }
                    txtSeatbeltAccessStatus.setTextColor(if (unbuckled.isEmpty()) Color.parseColor("#86efac") else Color.parseColor("#fca5a5"))
                    val newWarnings = eligibleWarnings.filter { it.key !in previouslyUnbuckled }
                    val webServerOwnsSeatbeltAudio = LocalCarWebService.prefs(this).getBoolean(LocalCarWebService.KEY_RUNNING, false)
                    if (seatbeltVoiceEnabled && !webServerOwnsSeatbeltAudio) seatbeltVoiceAnnouncer?.announce(newWarnings)
                    previouslyUnbuckled = if (seatbeltVoiceEnabled) eligibleWarnings.map { it.key }.toSet() else emptySet()
                }.onFailure { error ->
                    val cause = error.cause ?: error
                    txtSeatbeltAccessStatus.text =
                        "Leitura do sensor indisponível: ${cause.javaClass.simpleName}: ${cause.message}"
                    txtSeatbeltAccessStatus.setTextColor(Color.parseColor("#fde68a"))
                }
            }
        }.start()
    }

    private fun saveSeatbeltAlertSpeed() {
        val value = editSeatbeltSpeed.text.toString().toIntOrNull()
        if (value == null || value !in 1..200) {
            editSeatbeltSpeed.error = "Informe uma velocidade de 1 a 200 km/h"
            editSeatbeltSpeed.setText(seatbeltAlertSpeedKmh.toString())
            return
        }
        seatbeltAlertSpeedKmh = value
        getSharedPreferences("seatbelt_alerts", MODE_PRIVATE).edit().putInt("alert_speed_kmh", value).apply()
        previouslyUnbuckled = emptySet()
        txtSeatbeltAccessStatus.text = "Limite do alerta salvo: $value km/h. Os assentos sem sensor de ocupação validado não geram aviso."
    }

    private fun updateClimateCommandStatus() {
        txtClimateCommandStatus.text = hvacCommandStatus
    }

    private fun refreshWebServerUi() {
        val prefs = LocalCarWebService.prefs(this)
        val running = prefs.getBoolean(LocalCarWebService.KEY_RUNNING, false)
        val url = prefs.getString(LocalCarWebService.KEY_URL, "").orEmpty()
        btnWebServer.text = if (running) "PARAR SERVIDOR WEB" else "INICIAR SERVIDOR WEB"
        btnWebServer.backgroundTintList = android.content.res.ColorStateList.valueOf(
            Color.parseColor(if (running) "#b91c1c" else "#0284c7")
        )
        if (!running) {
            txtWebServerAddress.text = prefs.getString(LocalCarWebService.KEY_ERROR, null)
                ?.takeIf { it.isNotBlank() }?.let { "Servidor parado: $it" } ?: "Servidor parado."
            imgWebServerQr.visibility = View.GONE
            lastWebQrUrl = null
            return
        }
        if (url.isBlank()) {
            txtWebServerAddress.text = "Servidor ativo, mas não encontrei o endereço Wi-Fi da central. Verifique se ela está conectada ao hotspot."
            imgWebServerQr.visibility = View.GONE
            return
        }
        txtWebServerAddress.text = "Ativo na rede do hotspot. Escaneie o QR ou abra:\n$url"
        imgWebServerQr.visibility = View.VISIBLE
        if (lastWebQrUrl != url) {
            runCatching {
                val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 420, 420)
                val pixels = IntArray(matrix.width * matrix.height)
                for (y in 0 until matrix.height) for (x in 0 until matrix.width)
                    pixels[y * matrix.width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                imgWebServerQr.setImageBitmap(Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888))
                lastWebQrUrl = url
            }.onFailure {
                imgWebServerQr.visibility = View.GONE
                txtWebServerAddress.append("\nFalha ao gerar QR: ${it.message}")
            }
        }
    }

    private fun updateControlsUI() {
        updateClimateCommandStatus()
        txtControlLogs.text = "Ar-condicionado: comando direto pelo AirConditioningManager OEM.\n" +
            "Cintos: leitura dos assentos expostos pelo SDK; estados desconhecidos permanecem indisponíveis.\n" +
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
