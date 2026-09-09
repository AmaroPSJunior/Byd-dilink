package com.byd.carcontrol

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
        txtSeatbeltStatus = findViewById(R.id.txtSeatbeltStatus)
        txtDoorsStatus = findViewById(R.id.txtDoorsStatus)
        txtExecutionDetails = findViewById(R.id.txtExecutionDetails)

        // Botão Principal: FORÇAR APAGAR TODAS AS LUZES
        btnMasterTurnOffLights.setOnClickListener {
            val result = bydHelper.turnOffAllInteriorLights()
            txtExecutionDetails.text = "[COMANDO DISPARADO]\n${result.message}\nStatus: SUCESSO\nCanais acionados: ${result.channelsTriggered.joinToString(", ")}"
            Toast.makeText(this, "⚡ Luzes apagadas! Canais: ${result.channelsTriggered.size}", Toast.LENGTH_SHORT).show()
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
    }

    override fun onDestroy() {
        super.onDestroy()
        bydHelper.unregisterReceivers()
    }
}
