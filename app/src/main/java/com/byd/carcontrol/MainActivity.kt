package com.byd.carcontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.byd.carcontrol.discovery.BYDBodyworkInspector
import com.byd.carcontrol.discovery.BYDLightHalInspector
import com.byd.carcontrol.discovery.PermissionDiscovery
import org.json.JSONObject

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
                appendLog("🛡️ VERIFICANDO PERMISSÕES DO SISTEMA DILINK...")
                appendLog("==================================================")
                Toast.makeText(this, "Verificando permissões...", Toast.LENGTH_SHORT).show()

                Thread {
                    try {
                        val permissions = permissionDiscovery.runDiscovery()
                        val grantedCount = permissions.count { it.isGranted }
                        val totalCount = permissions.size

                        val sb = StringBuilder()
                        sb.append("=== PERMISSÕES DA CENTRAL ===\n")
                        sb.append("Total Inspecionado: $totalCount | Concedidas: $grantedCount\n\n")

                        permissions.forEach { p ->
                            val icon = if (p.isGranted) "✅ [CONCEDIDA]" else if (p.exists) "⚠️ [NEGADA]" else "❌ [NÃO REGISTRADA]"
                            sb.append("$icon ${p.permissionName}\n")
                            sb.append("   Proteção: ${p.protectionLevel}\n")
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

            // 3️⃣ OPÇÃO 3: EXPORTAR RELATÓRIO COMPLETO
            btnExportReport.setOnClickListener {
                try {
                    val fullReport = if (!lastInspectionReport.isNullOrEmpty()) {
                        lastInspectionReport!!
                    } else {
                        generateFullReport()
                    }

                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("BYD_Interior_Light_Report", fullReport)
                    clipboard.setPrimaryClip(clip)

                    appendLog("==================================================")
                    appendLog("📋 RELATÓRIO COMPLETO COPIADO PARA A ÁREA DE TRANSFERÊNCIA!")
                    appendLog("==================================================")

                    Toast.makeText(this, "Relatório completo copiado para a área de transferência!", Toast.LENGTH_LONG).show()
                } catch (t: Throwable) {
                    appendLog("❌ Erro ao exportar relatório: ${t.message}")
                }
            }

        } catch (t: Throwable) {
            Log.e("MainActivity", "Erro no onCreate", t)
        }
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
