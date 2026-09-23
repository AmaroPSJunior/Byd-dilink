package com.byd.carcontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.byd.carcontrol.discovery.BYDLightCommandEngine
import com.byd.carcontrol.discovery.LightCommandAttempt
import com.byd.carcontrol.discovery.LightCommandProgressListener

/**
 * Atividade Principal do BYD Light Command Tester.
 * Executa sequencialmente todos os comandos conhecidos do protocolo DiLink (HAL, Settings, Intent, Binder)
 * com feedback visual em tempo real na tela da central multimídia do Dolphin Plus.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var commManager: BYDCommunicationManager
    private lateinit var lightEngine: BYDLightCommandEngine

    private lateinit var txtVehicleInfo: TextView
    private lateinit var txtActiveTransport: TextView
    private lateinit var txtLiveLogs: TextView
    private lateinit var txtProgressStep: TextView
    private lateinit var panelProgress: LinearLayout
    private lateinit var progressBarLight: ProgressBar

    private lateinit var btnTurnOnAllLights: Button
    private lateinit var btnTurnOffAllLights: Button
    private lateinit var btnStopLightLoop: Button
    private lateinit var btnRunDiscovery: Button
    private lateinit var btnExportJson: Button

    private val logHistory = mutableListOf<String>()

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
            lightEngine = BYDLightCommandEngine(this, commManager.repository)

            txtVehicleInfo = findViewById(R.id.txtVehicleInfo)
            txtActiveTransport = findViewById(R.id.txtActiveTransport)
            txtLiveLogs = findViewById(R.id.txtLiveLogs)
            txtProgressStep = findViewById(R.id.txtProgressStep)
            panelProgress = findViewById(R.id.panelProgress)
            progressBarLight = findViewById(R.id.progressBarLight)

            btnTurnOnAllLights = findViewById(R.id.btnTurnOnAllLights)
            btnTurnOffAllLights = findViewById(R.id.btnTurnOffAllLights)
            btnStopLightLoop = findViewById(R.id.btnStopLightLoop)
            btnRunDiscovery = findViewById(R.id.btnRunDiscovery)
            btnExportJson = findViewById(R.id.btnExportJson)

            updateHeaderInfo()

            // 💡 BOTÃO 1: TESTAR ACENDER TODAS AS LUZES
            btnTurnOnAllLights.setOnClickListener {
                try {
                    startLightBatchTest(turnOn = true)
                } catch (t: Throwable) {
                    appendLog("❌ Erro ao iniciar acendimento: ${t.message}")
                }
            }

            // 🌙 BOTÃO 2: TESTAR APAGAR TODAS AS LUZES
            btnTurnOffAllLights.setOnClickListener {
                try {
                    startLightBatchTest(turnOn = false)
                } catch (t: Throwable) {
                    appendLog("❌ Erro ao iniciar apagar: ${t.message}")
                }
            }

            // ⏹️ BOTÃO 3: PARAR VARREDURA EM ANDAMENTO
            btnStopLightLoop.setOnClickListener {
                try {
                    lightEngine.stopBatch()
                    btnStopLightLoop.visibility = View.GONE
                    Toast.makeText(this, "Varredura interrompida", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    appendLog("❌ Erro ao parar varredura: ${t.message}")
                }
            }

            // 🔍 BOTÃO 4: VARREDURA HAL E DISCOVERY MATRIX
            btnRunDiscovery.setOnClickListener {
                Toast.makeText(this, "Iniciando varredura profunda em background...", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        val summary = commManager.fullEngine.runFullDiscovery()
                        runOnUiThread {
                            appendLog("=== VARREDURA CONCLUÍDA ===\n$summary")
                        }
                    } catch (t: Throwable) {
                        runOnUiThread {
                            appendLog("❌ Falha na varredura: ${t.message}")
                        }
                    }
                }.start()
            }

            // 📋 BOTÃO 5: EXPORTAR RELATÓRIO DIAGNÓSTICO
            btnExportJson.setOnClickListener {
                try {
                    val jsonReport = commManager.exportDiagnosticJson()
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("BYD_Diagnostic_Report", jsonReport)
                    clipboard.setPrimaryClip(clip)

                    appendLog("📋 Relatório copiado para a área de transferência!\n$jsonReport")
                    Toast.makeText(this, "Relatório copiado!", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    appendLog("❌ Erro ao exportar JSON: ${t.message}")
                }
            }
        } catch (t: Throwable) {
            Log.e("MainActivity", "Erro no onCreate", t)
        }
    }

    private fun startLightBatchTest(turnOn: Boolean) {
        val modeStr = if (turnOn) "ACENDER (LIGAR = 1)" else "APAGAR (DESLIGAR = 0)"
        logHistory.clear()
        appendLog("==================================================")
        appendLog("INICIANDO VARREDURA DE COMANDOS PARA: $modeStr")
        appendLog("Observe a iluminação física do veículo durante os testes...")
        appendLog("==================================================")

        panelProgress.visibility = View.VISIBLE
        btnStopLightLoop.visibility = View.VISIBLE

        lightEngine.runAllLightCommands(
            turnOn = turnOn,
            delayBetweenMs = 400L,
            listener = object : LightCommandProgressListener {
                override fun onCommandStarted(index: Int, total: Int, commandName: String, transport: String) {
                    try {
                        val pct = (index * 100) / total
                        progressBarLight.progress = pct
                        txtProgressStep.text = "[$index/$total] Executando comando..."
                    } catch (_: Throwable) {}
                }

                override fun onCommandCompleted(attempt: LightCommandAttempt) {
                    try {
                        val statusIcon = if (attempt.success) "✅" else "❌"
                        val logLine = "[$statusIcon ${attempt.index}/${attempt.total}] [${attempt.transportName}] ${attempt.commandName}\n" +
                                "   Payload: ${attempt.payloadStr} | Status: ${attempt.statusLabel} (${attempt.latencyMs}ms)" +
                                if (attempt.errorDetails != null) "\n   ⚠️ Erro: ${attempt.errorDetails}" else ""

                        appendLog(logLine)
                    } catch (_: Throwable) {}
                }

                override fun onBatchFinished(successCount: Int, totalCount: Int, summaryReport: String) {
                    try {
                        panelProgress.visibility = View.GONE
                        btnStopLightLoop.visibility = View.GONE
                        appendLog(summaryReport)
                        Toast.makeText(this@MainActivity, "Varredura concluída ($successCount/$totalCount sucessos)", Toast.LENGTH_LONG).show()
                    } catch (_: Throwable) {}
                }
            }
        )
    }

    private fun updateHeaderInfo() {
        try {
            val fw = commManager.getFirmwareInfo()
            val model = fw["ro.product.model"] ?: "Dolphin Plus"
            val androidVer = fw["ro.build.version.release"] ?: "10"
            val dilinkVer = fw["dilink.version"] ?: "DiLink 3.0/4.0"
            txtVehicleInfo.text = "Veículo: $model | Android $androidVer | $dilinkVer"
            txtActiveTransport.text = "Transporte Ativo: ${commManager.preferredTransport.name}"
        } catch (t: Throwable) {
            txtVehicleInfo.text = "Veículo: BYD Dolphin Plus (DiLink)"
            txtActiveTransport.text = "Transporte Ativo: HAL / Binder / Settings"
        }
    }

    private fun appendLog(text: String) {
        try {
            logHistory.add(text)
            if (logHistory.size > 150) {
                logHistory.removeAt(0)
            }
            txtLiveLogs.text = logHistory.takeLast(30).joinToString("\n\n")
        } catch (_: Throwable) {}
    }
}

