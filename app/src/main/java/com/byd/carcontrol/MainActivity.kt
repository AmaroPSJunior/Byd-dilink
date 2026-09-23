package com.byd.carcontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.byd.carcontrol.discovery.BYDLightCommandEngine
import com.byd.carcontrol.discovery.ExecutableCommand
import com.byd.carcontrol.discovery.LightCommandAttempt
import com.byd.carcontrol.discovery.LightCommandProgressListener

/**
 * Atividade Principal do BYD Light Command Tester.
 * Executa sequencialmente todos os comandos conhecidos do protocolo DiLink (HAL, Settings, Intent, Binder)
 * com feedback visual em tempo real e permite a execução individual e dinâmica de qualquer comando selecionado.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var commManager: BYDCommunicationManager
    private lateinit var lightEngine: BYDLightCommandEngine

    private lateinit var txtVehicleInfo: TextView
    private lateinit var txtActiveTransport: TextView
    private lateinit var txtLiveLogs: TextView
    private lateinit var txtProgressStep: TextView
    private lateinit var txtWorkingCount: TextView
    private lateinit var panelProgress: LinearLayout
    private lateinit var progressBarLight: ProgressBar

    private lateinit var spinnerCommands: Spinner
    private lateinit var btnExecuteSelectedOn: Button
    private lateinit var btnExecuteSelectedOff: Button

    private lateinit var btnTurnOnAllLights: Button
    private lateinit var btnTurnOffAllLights: Button
    private lateinit var btnStopLightLoop: Button
    private lateinit var btnRunDiscovery: Button
    private lateinit var btnExportJson: Button

    private val logHistory = mutableListOf<String>()
    private val candidateCommands = mutableListOf<ExecutableCommand>()
    private val workingCommands = mutableListOf<ExecutableCommand>()
    private lateinit var spinnerAdapter: ArrayAdapter<ExecutableCommand>

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
            txtWorkingCount = findViewById(R.id.txtWorkingCount)
            panelProgress = findViewById(R.id.panelProgress)
            progressBarLight = findViewById(R.id.progressBarLight)

            spinnerCommands = findViewById(R.id.spinnerCommands)
            btnExecuteSelectedOn = findViewById(R.id.btnExecuteSelectedOn)
            btnExecuteSelectedOff = findViewById(R.id.btnExecuteSelectedOff)

            btnTurnOnAllLights = findViewById(R.id.btnTurnOnAllLights)
            btnTurnOffAllLights = findViewById(R.id.btnTurnOffAllLights)
            btnStopLightLoop = findViewById(R.id.btnStopLightLoop)
            btnRunDiscovery = findViewById(R.id.btnRunDiscovery)
            btnExportJson = findViewById(R.id.btnExportJson)

            updateHeaderInfo()
            setupCommandSpinner()

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

            // 🎯 BOTÃO 3A: EXECUTAR COMANDO SELECIONADO (ACENDER = 1)
            btnExecuteSelectedOn.setOnClickListener {
                executeSelectedCommand(turnOn = true)
            }

            // 🎯 BOTÃO 3B: EXECUTAR COMANDO SELECIONADO (APAGAR = 0)
            btnExecuteSelectedOff.setOnClickListener {
                executeSelectedCommand(turnOn = false)
            }

            // ⏹️ BOTÃO 4: PARAR VARREDURA EM ANDAMENTO
            btnStopLightLoop.setOnClickListener {
                try {
                    lightEngine.stopBatch()
                    btnStopLightLoop.visibility = View.GONE
                    Toast.makeText(this, "Varredura interrompida", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) {
                    appendLog("❌ Erro ao parar varredura: ${t.message}")
                }
            }

            // 🔍 BOTÃO 5: VARREDURA HAL E DISCOVERY MATRIX
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

            // 📋 BOTÃO 6: EXPORTAR RELATÓRIO DIAGNÓSTICO
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

    private fun setupCommandSpinner() {
        try {
            candidateCommands.clear()
            candidateCommands.addAll(lightEngine.getAllCandidateCommands())

            spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, candidateCommands)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCommands.adapter = spinnerAdapter
        } catch (t: Throwable) {
            Log.e("MainActivity", "Erro ao configurar Spinner de comandos", t)
        }
    }

    private fun executeSelectedCommand(turnOn: Boolean) {
        val selectedCmd = spinnerCommands.selectedItem as? ExecutableCommand
        if (selectedCmd == null) {
            Toast.makeText(this, "Nenhum comando selecionado", Toast.LENGTH_SHORT).show()
            return
        }

        val actionStr = if (turnOn) "LIGAR (1)" else "DESLIGAR (0)"
        appendLog("▶️ Executando comando individual: [${selectedCmd.title}] -> $actionStr")

        Thread {
            try {
                val attempt = lightEngine.executeSingleExecutableCommand(selectedCmd, turnOn)
                runOnUiThread {
                    val icon = if (attempt.success) "✅" else "❌"
                    val logLine = "[$icon SELECIONADO] ${attempt.transportName}: ${attempt.commandName}\n" +
                            "   Status: ${attempt.statusLabel} (${attempt.latencyMs}ms)" +
                            if (attempt.errorDetails != null) "\n   ⚠️ Erro: ${attempt.errorDetails}" else ""

                    appendLog(logLine)
                    Toast.makeText(this@MainActivity, "$icon ${attempt.statusLabel} (${attempt.latencyMs}ms)", Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    appendLog("❌ Erro ao executar comando selecionado: ${t.message}")
                }
            }
        }.start()
    }

    private fun startLightBatchTest(turnOn: Boolean) {
        val modeStr = if (turnOn) "ACENDER (LIGAR = 1)" else "APAGAR (DESLIGAR = 0)"
        logHistory.clear()
        workingCommands.clear()
        txtWorkingCount.text = "Varredura iniciada... Detectando comandos positivos..."

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

                        if (attempt.success) {
                            // Adicionar aos comandos positivos detectados
                            val matchingCmd = candidateCommands.getOrNull(attempt.index - 1)
                            if (matchingCmd != null && !workingCommands.contains(matchingCmd)) {
                                workingCommands.add(matchingCmd)
                                updateSpinnerWithWorkingCommands()
                            }
                        }
                    } catch (_: Throwable) {}
                }

                override fun onBatchFinished(successCount: Int, totalCount: Int, summaryReport: String) {
                    try {
                        panelProgress.visibility = View.GONE
                        btnStopLightLoop.visibility = View.GONE
                        appendLog(summaryReport)

                        if (workingCommands.isNotEmpty()) {
                            txtWorkingCount.text = "🎉 ${workingCommands.size} comando(s) POSITIVOS detectados! Selecione um no dropdown abaixo para testar:"
                        } else {
                            txtWorkingCount.text = "Nenhum ACK direto recebido, mas você pode escolher qualquer comando abaixo para testar individualmente:"
                        }

                        Toast.makeText(this@MainActivity, "Varredura concluída ($successCount/$totalCount sucessos)", Toast.LENGTH_LONG).show()
                    } catch (_: Throwable) {}
                }
            }
        )
    }

    private fun updateSpinnerWithWorkingCommands() {
        try {
            runOnUiThread {
                txtWorkingCount.text = "✅ ${workingCommands.size} comando(s) positivos detectados! Escolha abaixo:"
                // Colocar os comandos funcionais no topo da lista
                val combinedList = mutableListOf<ExecutableCommand>()
                combinedList.addAll(workingCommands.map { it.copy(title = "✅ POSITIVO: ${it.title}") })
                combinedList.addAll(candidateCommands.filter { !workingCommands.contains(it) })

                spinnerAdapter.clear()
                spinnerAdapter.addAll(combinedList)
                spinnerAdapter.notifyDataSetChanged()
            }
        } catch (_: Throwable) {}
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


