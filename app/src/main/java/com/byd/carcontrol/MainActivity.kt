package com.byd.carcontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var bydHelper: BYDDiLinkServiceHelper
    private lateinit var btnMasterTurnOffLights: Button
    private lateinit var btnTurnOnLights: Button
    private lateinit var btnTotalBlackout: Button
    private lateinit var btnRunDiagnostics: Button
    private lateinit var btnGrantPermission: Button
    private lateinit var txtSeatbeltStatus: TextView
    private lateinit var txtDoorsStatus: TextView
    private lateinit var txtExecutionDetails: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bydHelper = BYDDiLinkServiceHelper(this)

        btnMasterTurnOffLights = findViewById(R.id.btnMasterTurnOffLights)
        btnTurnOnLights = findViewById(R.id.btnTurnOnLights)
        btnTotalBlackout = findViewById(R.id.btnTotalBlackout)
        btnRunDiagnostics = findViewById(R.id.btnRunDiagnostics)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        txtSeatbeltStatus = findViewById(R.id.txtSeatbeltStatus)
        txtDoorsStatus = findViewById(R.id.txtDoorsStatus)
        txtExecutionDetails = findViewById(R.id.txtExecutionDetails)

        // Botão Principal: FORÇAR APAGAR TODAS AS LUZES
        btnMasterTurnOffLights.setOnClickListener {
            val result = bydHelper.turnOffAllInteriorLights()
            val textLog = "[COMANDO APAGAR LUZES DISPARADO]\n${result.message}\nStatus: SUCESSO\nCanais acionados: ${result.channelsTriggered.joinToString(", ")}"
            txtExecutionDetails.text = textLog
            Toast.makeText(this, "⚡ Corte de iluminação enviado!", Toast.LENGTH_SHORT).show()
        }

        // Botão Secundário: Ligar Luzes
        btnTurnOnLights.setOnClickListener {
            val result = bydHelper.turnOnAllInteriorLights()
            txtExecutionDetails.text = "[LUZES LIGADAS]\n${result.message}\nCanais: ${result.channelsTriggered.joinToString(", ")}"
            Toast.makeText(this, "Luzes ligadas", Toast.LENGTH_SHORT).show()
        }

        // Botão Blackout Total (Luzes + Tela Multimídia DiLink)
        btnTotalBlackout.setOnClickListener {
            val result = bydHelper.activateTotalBlackout()
            txtExecutionDetails.text = "[BLACKOUT TOTAL]\n${result.message}\nLuzes e tela desligadas."
            Toast.makeText(this, "🌙 Blackout total ativado!", Toast.LENGTH_SHORT).show()
        }

        // Botão Executar Diagnóstico de Conexão DiLink
        btnRunDiagnostics.setOnClickListener {
            val diagReport = bydHelper.runFullDiLinkDiagnostics()
            txtExecutionDetails.text = diagReport

            // Copia para área de transferência para fácil envio pelo usuário se necessário
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("BYD_DiLink_Diag", diagReport)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, "📋 Diagnóstico gerado e copiado!", Toast.LENGTH_LONG).show()
        }

        // Botão Conceder Permissão System Settings
        btnGrantPermission.setOnClickListener {
            if (bydHelper.hasWriteSettingsPermission()) {
                Toast.makeText(this, "✅ Permissão WRITE_SETTINGS já concedida!", Toast.LENGTH_SHORT).show()
            } else {
                bydHelper.requestWriteSettingsPermission()
                Toast.makeText(this, "Conceda a permissão para 'Modificar configurações do sistema'", Toast.LENGTH_LONG).show()
            }
        }

        bydHelper.observeSeatbeltStatus { status ->
            runOnUiThread {
                txtSeatbeltStatus.text = "Status Cintos: ${status.getDescription()}"
            }
        }

        bydHelper.observeDoorStatus { status ->
            runOnUiThread {
                txtDoorsStatus.text = "Status Portas: ${status.getDescription()}"
            }
        }

        // Executa um diagnóstico rápido ao iniciar
        if (!bydHelper.hasWriteSettingsPermission()) {
            txtExecutionDetails.text = "⚠️ ATENÇÃO: Para habilitar o controle pelo canal Settings.System no Dolphin Plus, clique em 'Permissão System' acima."
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bydHelper.unregisterReceivers()
    }
}
