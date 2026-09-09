package com.byd.carcontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Atividade principal do BYD Communication Lab.
 * Conecta a interface Android ao BYDCommunicationManager para testes de hardware no Dolphin Plus.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var commManager: BYDCommunicationManager
    private lateinit var txtVehicleInfo: TextView
    private lateinit var txtActiveTransport: TextView
    private lateinit var txtTestMode: TextView
    private lateinit var txtDiscoveryMatrixView: TextView
    private lateinit var txtLiveLogs: TextView

    private lateinit var btnRunDiscovery: Button
    private lateinit var btnTestBenchLight: Button
    private lateinit var btnExportJson: Button
    private lateinit var btnSettingsPermission: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        commManager = BYDCommunicationManager.getInstance(this)

        txtVehicleInfo = findViewById(R.id.txtVehicleInfo)
        txtActiveTransport = findViewById(R.id.txtActiveTransport)
        txtTestMode = findViewById(R.id.txtTestMode)
        txtDiscoveryMatrixView = findViewById(R.id.txtDiscoveryMatrixView)
        txtLiveLogs = findViewById(R.id.txtLiveLogs)

        btnRunDiscovery = findViewById(R.id.btnRunDiscovery)
        btnTestBenchLight = findViewById(R.id.btnTestBenchLight)
        btnExportJson = findViewById(R.id.btnExportJson)
        btnSettingsPermission = findViewById(R.id.btnSettingsPermission)

        updateFirmwareHeader()
        refreshMatrixDisplay()
        refreshLogsDisplay()

        // Botão 1: Executar Varredura Completa da Matriz de Comunicação
        btnRunDiscovery.setOnClickListener {
            Toast.makeText(this, "Iniciando varredura em 12 transportes...", Toast.LENGTH_SHORT).show()
            commManager.runFullDiscovery()
            refreshMatrixDisplay()
            refreshLogsDisplay()
            Toast.makeText(this, "Varredura concluída!", Toast.LENGTH_SHORT).show()
        }

        // Botão 2: Test Bench de Luz com Confirmação Obrigatória
        btnTestBenchLight.setOnClickListener {
            val report = commManager.runLightTestBench()
            txtLiveLogs.text = report
            refreshMatrixDisplay()
            Toast.makeText(this, "Test Bench executado!", Toast.LENGTH_SHORT).show()
        }

        // Botão 3: Exportar Diagnóstico Completo em JSON
        btnExportJson.setOnClickListener {
            val jsonReport = commManager.exportDiagnosticJson()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("BYD_Diagnostic_Report", jsonReport)
            clipboard.setPrimaryClip(clip)

            txtLiveLogs.text = "[RELATÓRIO EXPORTADO PARA CLIPBOARD]\n$jsonReport"
            Toast.makeText(this, "📋 Relatório JSON copiado!", Toast.LENGTH_LONG).show()
        }

        // Botão 4: Conceder Permissão WRITE_SETTINGS
        btnSettingsPermission.setOnClickListener {
            if (Settings.System.canWrite(this)) {
                Toast.makeText(this, "✅ WRITE_SETTINGS já concedida!", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    Toast.makeText(this, "Conceda permissão para 'Modificar configurações'", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Erro ao abrir configurações: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateFirmwareHeader() {
        val fw = commManager.getFirmwareInfo()
        val model = fw["ro.product.model"] ?: "Dolphin Plus"
        val androidVer = fw["ro.build.version.release"] ?: "12"
        val dilinkVer = fw["dilink.version"] ?: "DiLink 4.0/5.0"
        txtVehicleInfo.text = "Veículo: $model | Android $androidVer | $dilinkVer"
        txtActiveTransport.text = "Transporte Ativo: ${commManager.preferredTransport.name}"
        txtTestMode.text = "Modo de Segurança: ${commManager.testMode.label}"
    }

    private fun refreshMatrixDisplay() {
        val matrix = commManager.discoveryMatrix
        val sb = StringBuilder()
        for ((type, result) in matrix) {
            val stateIcon = when (result.state) {
                TransportState.CONFIRMED -> "🟢 CONFIRMADO"
                TransportState.AVAILABLE -> "🔵 DISPONÍVEL"
                TransportState.PERMISSION_DENIED -> "🟠 PERM NEGADA"
                TransportState.SECURITY_EXCEPTION -> "🔴 BLOQUEADO"
                TransportState.UNAVAILABLE -> "⚪ INDISPONÍVEL"
                TransportState.SERVICE_NOT_FOUND -> "⚪ NÃO ENCONTRADO"
                TransportState.EXECUTED_NO_CONFIRMATION -> "🟡 ENVIADO S/ ACK"
                else -> "🔴 ${result.state.label}"
            }
            sb.append(String.format("%-20s : %s\n", type.displayName, stateIcon))
            sb.append("   └ Detalhes: ${result.details}\n")
        }
        txtDiscoveryMatrixView.text = sb.toString()
    }

    private fun refreshLogsDisplay() {
        val logs = commManager.getLogs().take(15)
        if (logs.isEmpty()) {
            txtLiveLogs.text = "Nenhum evento registrado ainda. Execute a varredura ou um teste."
            return
        }
        val sb = StringBuilder()
        for (log in logs) {
            sb.append("[${log.timestamp}] [${log.transport.displayName}] ${log.action}\n")
            sb.append("   Status: ${log.finalState.label} (${log.durationMs}ms)\n")
            if (log.stateBefore != null) sb.append("   Antes: ${log.stateBefore} -> Depois: ${log.stateAfter}\n")
            if (log.exceptionDetails != null) sb.append("   ⚠️ Exceção: ${log.exceptionDetails.take(80)}...\n")
            sb.append("\n")
        }
        txtLiveLogs.text = sb.toString()
    }
}
