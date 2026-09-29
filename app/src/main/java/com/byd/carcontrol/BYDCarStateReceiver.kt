package com.byd.carcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Kotlin BroadcastReceiver para capturar eventos em tempo real do CAN Bus BYD
 */
class BYDCarStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.action?.let { action ->
            Log.d("BYDCarReceiver", "Evento de veículo recebido: $action")
            val safeExtras = linkedMapOf<String, Any?>()
            intent.extras?.keySet()?.forEach { key ->
                val value = runCatching { intent.extras?.get(key) }.getOrNull()
                if (value == null || value is String || value is Boolean || value is Int || value is Long || value is Float || value is Double) {
                    safeExtras[key] = value
                } else {
                    safeExtras[key] = "<${value.javaClass.name}>"
                }
            }
            com.byd.carcontrol.inspector.InspectorEventBridge.publish(
                com.byd.carcontrol.inspector.InspectorObservation(
                    source = "byd.broadcast.receiver",
                    eventType = "VEHICLE_BROADCAST",
                    value = action,
                    service = action,
                    className = javaClass.name,
                    method = "onReceive(Intent)",
                    property = "intent.action",
                    metadata = safeExtras
                )
            )
            when (action) {
                "com.byd.action.SEATBELT_STATE_CHANGED" -> {
                    val driverState = intent.getBooleanExtra("driver_buckled", true)
                    Log.i("BYDCarReceiver", "Status Cinto Motorista: $driverState")
                }
                "com.byd.action.DOOR_STATE_CHANGED" -> {
                    val doorId = intent.getIntExtra("door_id", 0)
                    val isOpen = intent.getBooleanExtra("is_open", false)
                    Log.i("BYDCarReceiver", "Porta $doorId alterada: isOpen=$isOpen")
                }
                else -> {
                    Log.d("BYDCarReceiver", "Outra ação recebida: $action")
                }
            }
        }
    }
}
