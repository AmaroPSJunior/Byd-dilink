package com.byd.carcontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
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
import com.byd.carcontrol.discovery.BYDAutoServiceInspector
import com.byd.carcontrol.discovery.BYDLightCommandEngine
import com.byd.carcontrol.discovery.ExecutableCommand
import com.byd.carcontrol.discovery.LightCommandAttempt
import com.byd.carcontrol.discovery.LightCommandProgressListener

/**
 * Atividade Principal do BYD Light Command Tester.
 * Executa sequencialmente todos os comandos do protocolo DiLink (HAL, Settings, Intent, Binder)
 * e fornece o módulo "BYD INTERIOR LIGHT LAB" para investigação técnica profunda do autoservice / Device 1023.
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

    // Seção LAB: Status
    private lateinit var txtLabStatusAutoservice: TextView
    private lateinit var txtLabStatusManager: TextView
    private lateinit var txtLabStatusDevice1023: TextView

    // Seção LAB: Botões
    private lateinit var btnInspectApi: Button
    private lateinit var btnReadInterior: Button
    private lateinit var btnArmWriteTest: Button
    private lateinit var btnSetInterior1: Button
    private lateinit var btnSetInterior2: Button

    private lateinit var btnAmbientL1: Button
    private lateinit var btnAmbientL2: Button
    private lateinit var btnAmbientL3: Button
    private lateinit var btnAmbientL4: Button
    private lateinit var btnAmbientL5: Button

    private lateinit var btnPhysicalYes: Button
    private lateinit var btnPhysicalNo: Button

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

            // LAB Views
            txtLabStatusAutoservice = findViewById(R.id.txtLabStatusAutoservice)
            txtLabStatusManager = findViewById(R.id.txtLabStatusManager)
            txtLabStatusDevice1023 = findViewById(R.id.txtLabStatusDevice1023)

            btnInspectApi = findViewById(R.id.btnInspectApi)
            btnReadInterior = findViewById(R.id.btnReadInterior)
            btnArmWriteTest = findViewById(R.id.btnArmWriteTest)
            btnSetInterior1 = findViewById(R.id.btnSetInterior1)
            btnSetInterior2 = findViewById(R.id.btnSetInterior2)

            btnAmbientL1 = findViewById(R.id.btnAmbientL1)
            btnAmbientL2 = findViewById(R.id.btnAmbientL2)
            btnAmbientL3 = findViewById(R.id.btnAmbientL3)
            btnAmbientL4 = findViewById(R.id.btnAmbientL4)
            btnAmbientL5 = findViewById(R.id.btnAmbientL5)

            btnPhysicalYes = findViewById(R.id.btnPhysicalYes)
            btnPhysicalNo = findViewById(R.id.btnPhysicalNo)

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
            setupInteriorLabHandlers()

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

    private fun setupInteriorLabHandlers() {
        val inspector = commManager.autoServiceInspector

        // 1. INSPECT API
        btnInspectApi.setOnClickListener {
            appendLog("🔍 Executando inspeção técnica da API 'autoservice' / BYDAutoManager...")
            Thread {
                try {
                    val report = inspector.inspectApi()
                    runOnUiThread {
                        txtLabStatusAutoservice.text = "AUTOSERVICE: [ ${if (report.autoserviceFound) "FOUND (${report.autoserviceDescriptor})" else "NOT FOUND"} ]"
                        txtLabStatusManager.text = "BYDAUTO MANAGER: [ ${if (report.bydAutoManagerFound) "FOUND (${report.bydAutoManagerClassName})" else "NOT FOUND"} ]"
                        txtLabStatusDevice1023.text = "DEVICE 1023: [ ${if (report.device1023Found) "FOUND / RESPONDED" else "UNKNOWN / UNRESPONSIVE"} ]"

                        appendLog("=== RESULTADO DA INSPEÇÃO API ===")
                        report.logs.forEach { appendLog(it) }
                        Toast.makeText(this@MainActivity, "Inspeção concluída!", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    runOnUiThread {
                        appendLog("❌ Erro na inspeção: ${t.message}")
                    }
                }
            }.start()
        }

        // 2. READ INTERIOR (GET DEVICE 1023)
        btnReadInterior.setOnClickListener {
            appendLog("📖 Executando GET Device 1023 (Interior Light FID: 1330643002 & Atmosphere FID: 1069547536)...")
            Thread {
                try {
                    val resInterior = inspector.readDevice1023(BYDAutoServiceInspector.DEVICE_INTERIOR_LIGHT, BYDAutoServiceInspector.FID_INTERIOR_LIGHT_STATE)
                    val resAtmosphere = inspector.readDevice1023(BYDAutoServiceInspector.DEVICE_INTERIOR_LIGHT, BYDAutoServiceInspector.FID_ATMOSPHERE_BRIGHTNESS)

                    runOnUiThread {
                        appendLog("=== LEITURA DEVICE 1023 ===")
                        appendLog("• INTERIOR LIGHT (0x4F50003A): ${resInterior.returnedValue ?: "SEM RESPOSTA"} | retCode=${resInterior.returnCode} (${resInterior.durationMs}ms)")
                        if (resInterior.exception != null) appendLog("  ⚠️ Exceção: ${resInterior.exception}")

                        appendLog("• ATMOSPHERE BRIGHTNESS (0x3FC00010): ${resAtmosphere.returnedValue ?: "SEM RESPOSTA"} | retCode=${resAtmosphere.returnCode} (${resAtmosphere.durationMs}ms)")
                        if (resAtmosphere.exception != null) appendLog("  ⚠️ Exceção: ${resAtmosphere.exception}")

                        Toast.makeText(this@MainActivity, "Leitura efetuada!", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    runOnUiThread {
                        appendLog("❌ Erro na leitura: ${t.message}")
                    }
                }
            }.start()
        }

        // 3. ARMAR TESTE DE ESCRITA
        btnArmWriteTest.setOnClickListener {
            val nextState = !inspector.isArmedForWrite
            inspector.armWriteTests(nextState)

            // Atualizar UI de trava de segurança
            val writeButtons = listOf(btnSetInterior1, btnSetInterior2, btnAmbientL1, btnAmbientL2, btnAmbientL3, btnAmbientL4, btnAmbientL5)
            writeButtons.forEach { it.isEnabled = nextState }

            if (nextState) {
                btnArmWriteTest.text = "⚠️ TESTE DE ESCRITA ARMADO (ESCRITA LIBERADA)"
                btnArmWriteTest.setBackgroundColor(Color.parseColor("#dc2626"))
                Toast.makeText(this, "⚠️ AENÇÃO: Testes de escrita habilitados!", Toast.LENGTH_SHORT).show()
            } else {
                btnArmWriteTest.text = "🔒 ARMAR TESTE DE ESCRITA (DESARMADO)"
                btnArmWriteTest.setBackgroundColor(Color.parseColor("#334155"))
                Toast.makeText(this, "🔒 Testes de escrita desarmados.", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. SET INTERIOR 1 (LIGAR = 1)
        btnSetInterior1.setOnClickListener {
            executeInteriorWrite(1)
        }

        // 5. SET INTERIOR 2 (DESLIGAR = 2)
        btnSetInterior2.setOnClickListener {
            executeInteriorWrite(2)
        }

        // 6. SET AMBIENT LEVELS 1..5
        val ambientButtons = listOf(
            Pair(btnAmbientL1, 1), Pair(btnAmbientL2, 2), Pair(btnAmbientL3, 3),
            Pair(btnAmbientL4, 4), Pair(btnAmbientL5, 5)
        )
        for ((btn, level) in ambientButtons) {
            btn.setOnClickListener {
                executeAmbientWrite(level)
            }
        }

        // 7. CONFIRMAÇÃO FÍSICA
        btnPhysicalYes.setOnClickListener {
            inspector.setPhysicalConfirmation(true)
            appendLog("🎉 CONFIRMAÇÃO REGISTRADA: A iluminação física MUDOU no Dolphin Plus!")
            Toast.makeText(this, "🎉 Sucesso confirmado no veículo!", Toast.LENGTH_LONG).show()
        }

        btnPhysicalNo.setOnClickListener {
            inspector.setPhysicalConfirmation(false)
            appendLog("❌ CONFIRMAÇÃO REGISTRADA: Nenhuma mudança física observada.")
            Toast.makeText(this, "Registro gravado como sem efeito físico.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeInteriorWrite(targetValue: Int) {
        val inspector = commManager.autoServiceInspector
        appendLog("▶️ Executando SET INTERIOR LIGHT -> Value=$targetValue (Device 1023 / FID 0x4F50003A)...")

        Thread {
            try {
                val res = inspector.writeDevice1023(BYDAutoServiceInspector.DEVICE_INTERIOR_LIGHT, BYDAutoServiceInspector.FID_INTERIOR_LIGHT_STATE, targetValue)
                runOnUiThread {
                    appendLog("=== RESULTADO SET INTERIOR ($targetValue) ===")
                    appendLog("• Read Before: ${res.readBefore ?: "N/A"}")
                    appendLog("• Transact 6 retCode: ${res.returnCode} (${res.durationMs}ms)")
                    appendLog("• Read After: ${res.readAfter ?: "N/A"}")
                    if (res.exception != null) appendLog("⚠️ Exceção: ${res.exception}")

                    Toast.makeText(this@MainActivity, "SET $targetValue enviado! Observe o carro...", Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    appendLog("❌ Erro no SET Interior: ${t.message}")
                }
            }
        }.start()
    }

    private fun executeAmbientWrite(level: Int) {
        val inspector = commManager.autoServiceInspector
        appendLog("▶️ Executando SET AMBIENT BRIGHTNESS -> Level=$level (Device 1023 / FID 0x3FC00010)...")

        Thread {
            try {
                val res = inspector.writeDevice1023(BYDAutoServiceInspector.DEVICE_INTERIOR_LIGHT, BYDAutoServiceInspector.FID_ATMOSPHERE_BRIGHTNESS, level)
                runOnUiThread {
                    appendLog("=== RESULTADO SET AMBIENT (Nível $level) ===")
                    appendLog("• Read Before: ${res.readBefore ?: "N/A"}")
                    appendLog("• Transact 6 retCode: ${res.returnCode} (${res.durationMs}ms)")
                    appendLog("• Read After: ${res.readAfter ?: "N/A"}")
                    if (res.exception != null) appendLog("⚠️ Exceção: ${res.exception}")

                    Toast.makeText(this@MainActivity, "Nível $level enviado! Observe o ambiente...", Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                runOnUiThread {
                    appendLog("❌ Erro no SET Ambient: ${t.message}")
                }
            }
        }.start()
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



