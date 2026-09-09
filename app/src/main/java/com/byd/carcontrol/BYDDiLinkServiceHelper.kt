package com.byd.carcontrol

import android.content.Context
import android.content.Intent
import android.util.Log
import java.lang.reflect.Method

/**
 * Helper em Kotlin para integração com serviços do ecossistema BYD DiLink via Reflection.
 */
class BYDDiLinkServiceHelper(private val context: Context) {

    companion object {
        private const val TAG = "BYDDiLinkHelper"
    }

    private var bydLightBusInstance: Any? = null
    private var bydDoorBusInstance: Any? = null

    init {
        initBYDServicesReflection()
    }

    private fun initBYDServicesReflection() {
        try {
            val lightClazz = Class.forName("com.byd.service.BYDAutoLightBus")
            val getLightInstance: Method = lightClazz.getMethod("getInstance", Context::class.java)
            bydLightBusInstance = getLightInstance.invoke(null, context)
            Log.d(TAG, "Conectado ao serviço BYDAutoLightBus!")
        } catch (e: Exception) {
            Log.w(TAG, "SDK Nativo de Luzes não encontrado. Usando Broadcast Intent Fallback.")
        }

        try {
            val doorClazz = Class.forName("com.byd.service.BYDAutoDoorBus")
            val getDoorInstance: Method = doorClazz.getMethod("getInstance", Context::class.java)
            bydDoorBusInstance = getDoorInstance.invoke(null, context)
            Log.d(TAG, "Conectado ao serviço BYDAutoDoorBus!")
        } catch (e: Exception) {
            Log.w(TAG, "SDK Nativo de Portas não encontrado. Usando Broadcast Intent Fallback.")
        }
    }

    fun turnOffAllInteriorLights(): Boolean {
        var nativeSuccess = false
        bydLightBusInstance?.let { instance ->
            try {
                val setLight = instance.javaClass.getMethod("setReadingLightState", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                setLight.invoke(instance, 0, 0)

                val setAmbient = instance.javaClass.getMethod("setAmbientLightState", Int::class.javaPrimitiveType)
                setAmbient.invoke(instance, 0)

                nativeSuccess = true
                Log.i(TAG, "API Nativa de Luzes BYD executada via Kotlin!")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao invocar API de luzes", e)
            }
        }

        val intent = Intent("com.byd.action.CONTROL_LIGHTS").apply {
            putExtra("light_type", "ALL_INTERIOR")
            putExtra("state", 0)
        }
        context.sendBroadcast(intent)
        return nativeSuccess || true
    }

    data class SeatbeltStatus(
        val driverBuckled: Boolean = true,
        val passengerBuckled: Boolean = true,
        val rearLeftBuckled: Boolean = true,
        val rearMiddleBuckled: Boolean = true,
        val rearRightBuckled: Boolean = true
    ) {
        fun getDescription(): String {
            val unbuckledCount = listOf(driverBuckled, passengerBuckled, rearLeftBuckled, rearMiddleBuckled, rearRightBuckled).count { !it }
            return if (unbuckledCount == 0) "Todos os cintos afivelados" else "$unbuckledCount cinto(s) desafivelado(s)!"
        }
    }

    data class DoorStatus(
        val driverOpen: Boolean = false,
        val passengerOpen: Boolean = false,
        val rearLeftOpen: Boolean = false,
        val rearRightOpen: Boolean = false,
        val trunkOpen: Boolean = false
    ) {
        fun getDescription(): String {
            val openCount = listOf(driverOpen, passengerOpen, rearLeftOpen, rearRightOpen, trunkOpen).count { it }
            return if (openCount == 0) "Todas as portas fechadas" else "$openCount porta(s) aberta(s)!"
        }
    }

    fun observeSeatbeltStatus(callback: (SeatbeltStatus) -> Unit) {
        callback(SeatbeltStatus())
    }

    fun observeDoorStatus(callback: (DoorStatus) -> Unit) {
        callback(DoorStatus())
    }

    fun unregisterReceivers() {
        // Cleanup
    }
}
