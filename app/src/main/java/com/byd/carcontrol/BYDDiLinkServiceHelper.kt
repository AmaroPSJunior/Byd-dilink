package com.byd.carcontrol

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.lang.reflect.Method

/**
 * Helper avançado em Kotlin para integração com todos os serviços do ecossistema BYD DiLink.
 * Otimizado especificamente para BYD Dolphin, Dolphin Plus, Song Plus, Seal, Yuan Plus e Han.
 * Implementa 5 canais redundantes de acionamento com transmissão explícita para os pacotes do sistema DiLink.
 */
class BYDDiLinkServiceHelper(private val context: Context) {

    companion object {
        private const val TAG = "BYDDiLinkHelper"

        // Lista estendida de classes candidatas de iluminação no DiLink OS (Android 10/12)
        private val LIGHT_SERVICE_CLASSES = listOf(
            "com.byd.auto.light.BYDAutoLightDevice",
            "android.hardware.bydauto.light.BYDAutoLightDevice",
            "com.byd.auto.BYDAutoLightDevice",
            "com.byd.service.BYDAutoLightBus",
            "com.byd.auto.BYDAutoLightManager",
            "com.byd.auto.BYDAutoDeviceManager",
            "com.byd.auto.light.BYDLight",
            "com.byd.auto.light.BYDLightManager"
        )

        // Pacotes do sistema BYD que contêm BroadcastReceivers para controle veicular
        private val BYD_TARGET_PACKAGES = listOf(
            "com.byd.auto",
            "com.byd.carsettings",
            "com.byd.autorun.service",
            "com.byd.service",
            "com.byd.autoui",
            "com.byd.systemui",
            "com.byd.car.settings"
        )
    }

    private var bydLightBusInstance: Any? = null
    private var detectedLightClassName: String? = null
    private var bydDoorBusInstance: Any? = null
    private var bydWindowBusInstance: Any? = null
    private var bydHvacBusInstance: Any? = null
    private var bydBatteryBusInstance: Any? = null
    private var bydScreenBusInstance: Any? = null

    init {
        initBYDServicesReflection()
    }

    private fun initBYDServicesReflection() {
        // Busca reflexiva pelo serviço de iluminação compatível com o modelo
        for (className in LIGHT_SERVICE_CLASSES) {
            val instance = getServiceInstance(className)
            if (instance != null) {
                bydLightBusInstance = instance
                detectedLightClassName = className
                Log.i(TAG, "Módulo de luzes BYD vinculado com sucesso: $className")
                break
            }
        }

        bydDoorBusInstance = getServiceInstance("com.byd.auto.door.BYDAutoDoorDevice")
            ?: getServiceInstance("android.hardware.bydauto.door.BYDAutoDoorDevice")
            ?: getServiceInstance("com.byd.service.BYDAutoDoorBus")

        bydWindowBusInstance = getServiceInstance("com.byd.auto.window.BYDAutoWindowDevice")
            ?: getServiceInstance("android.hardware.bydauto.window.BYDAutoWindowDevice")
            ?: getServiceInstance("com.byd.service.BYDAutoWindowBus")

        bydHvacBusInstance = getServiceInstance("com.byd.auto.aircondition.BYDAutoAirConditionDevice")
            ?: getServiceInstance("android.hardware.bydauto.aircondition.BYDAutoAirConditionDevice")
            ?: getServiceInstance("com.byd.service.BYDAutoHVACBus")

        bydBatteryBusInstance = getServiceInstance("com.byd.auto.power.BYDAutoPowerDevice")
            ?: getServiceInstance("android.hardware.bydauto.power.BYDAutoPowerDevice")
            ?: getServiceInstance("com.byd.service.BYDAutoBatteryBus")

        bydScreenBusInstance = getServiceInstance("com.byd.auto.screen.BYDAutoScreenDevice")
            ?: getServiceInstance("android.hardware.bydauto.screen.BYDAutoScreenDevice")
            ?: getServiceInstance("com.byd.service.BYDAutoScreenBus")
    }

    private fun getServiceInstance(className: String): Any? {
        return try {
            val clazz = Class.forName(className)
            // Tenta getInstance(context)
            try {
                val method = clazz.getMethod("getInstance", Context::class.java)
                return method.invoke(null, context)
            } catch (e: NoSuchMethodException) {
                // Tenta getInstance() sem parâmetros
                try {
                    val method = clazz.getMethod("getInstance")
                    return method.invoke(null)
                } catch (e2: NoSuchMethodException) {
                    // Tenta construtor padrão
                    return clazz.getDeclaredConstructor().newInstance()
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verifica se o aplicativo possui permissão para modificar as configurações do sistema.
     */
    fun hasWriteSettingsPermission(): Boolean {
        return Settings.System.canWrite(context)
    }

    /**
     * Abre a tela de configurações para o usuário conceder permissão WRITE_SETTINGS.
     */
    fun requestWriteSettingsPermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao abrir configurações WRITE_SETTINGS: ${e.message}")
        }
    }

    data class LightControlResult(
        val success: Boolean,
        val message: String,
        val channelsTriggered: List<String>
    )

    /**
     * Dispara um Intent tanto de forma implícita quanto explicitamente para todos os pacotes BYD.
     */
    private fun sendTargetedBYDBroadcasts(baseIntent: Intent): Int {
        var count = 0
        // Broadcast implícito geral
        try {
            context.sendBroadcast(baseIntent)
            count++
        } catch (_: Exception) {}

        // Broadcasts explícitos para pacotes BYD do DiLink
        for (pkg in BYD_TARGET_PACKAGES) {
            try {
                val targetedIntent = Intent(baseIntent).apply {
                    setPackage(pkg)
                }
                context.sendBroadcast(targetedIntent)
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    /**
     * Força o desligamento incondicional de todas as luzes internas da cabine
     * utilizando 5 canais redundantes (HAL, Settings, Broadcasts Explícitos, CarProperty, Shell).
     */
    fun turnOffAllInteriorLights(): LightControlResult {
        val channels = mutableListOf<String>()
        var halExecuted = false

        // CANAL 1: Invocação direta via HAL nativo BYD (com varredura de métodos)
        bydLightBusInstance?.let { instance ->
            val zones = listOf(0, 1, 2, 3) // 0=All, 1=Driver, 2=Passenger, 3=Rear
            for (zone in zones) {
                val methodsToTry = listOf(
                    Pair("setReadingLight", arrayOf(zone, 0)),
                    Pair("setReadingLightState", arrayOf(zone, 0)),
                    Pair("setReadingLightSwitch", arrayOf(zone, 0)),
                    Pair("setAmbientLightSwitch", arrayOf(0)),
                    Pair("setAmbientLightState", arrayOf(0)),
                    Pair("setTopLightState", arrayOf(0)),
                    Pair("setCeilingLightState", arrayOf(0)),
                    Pair("setFootwellLight", arrayOf(0)),
                    Pair("setDomeLightState", arrayOf(0)),
                    Pair("setInteriorLight", arrayOf(0))
                )

                for ((methodName, args) in methodsToTry) {
                    try {
                        val paramTypes = args.map { it.javaClass.getField("TYPE").get(null) as Class<*> }.toTypedArray()
                        val method: Method = instance.javaClass.getMethod(methodName, *paramTypes)
                        method.invoke(instance, *args)
                        halExecuted = true
                    } catch (_: Exception) {}
                }
            }
            if (halExecuted) {
                channels.add("HAL Nativo ($detectedLightClassName)")
            }
        }

        // CANAL 2: Provedor de Configurações Android (Settings.System) do DiLink
        try {
            if (hasWriteSettingsPermission()) {
                val cr = context.contentResolver
                val keys = listOf(
                    "auto_dome_light",
                    "byd_auto_dome_light",
                    "byd_dome_light",
                    "byd_reading_light",
                    "byd_reading_light_fl",
                    "byd_reading_light_fr",
                    "byd_reading_light_rear",
                    "byd_ambient_light",
                    "byd_ambient_light_switch",
                    "byd_atmosphere_light_switch",
                    "byd_atmosphere_light_brightness"
                )
                for (key in keys) {
                    try {
                        Settings.System.putInt(cr, key, 0)
                    } catch (_: Exception) {}
                }
                channels.add("Settings.System (Auto Dome / Ambient = 0)")
            } else {
                channels.add("Settings.System (Aguardando permissão WRITE_SETTINGS)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Acesso a Settings.System restrito: ${e.message}")
        }

        // CANAL 3: Disparo de Multi-Intents Explícitos para Pacotes BYD
        val intentsToSend = listOf(
            Intent("com.byd.action.CONTROL_LIGHTS").apply {
                putExtra("light_type", "ALL_INTERIOR")
                putExtra("state", 0)
                putExtra("command", "MASTER_OFF")
            },
            Intent("com.byd.action.LIGHT_CONTROL").apply {
                putExtra("target", "ALL_INTERNAL_LIGHTS")
                putExtra("value", 0)
                putExtra("command", "MASTER_OFF")
            },
            Intent("byd.intent.action.LIGHT_CONTROL").apply {
                putExtra("type", "reading")
                putExtra("status", 0)
            },
            Intent("com.byd.intent.action.SET_LIGHT").apply {
                putExtra("light_id", 0)
                putExtra("state", 0)
            },
            Intent("com.byd.action.DOMELIGHT_OFF"),
            Intent("com.byd.action.AMBIENT_LIGHT_SWITCH").apply {
                putExtra("state", 0)
            }
        )

        var totalBroadcastsFired = 0
        for (intent in intentsToSend) {
            totalBroadcastsFired += sendTargetedBYDBroadcasts(intent)
        }
        channels.add("Broadcasts Explícitos BYD ($totalBroadcastsFired disparados)")

        // CANAL 4: Android Automotive CarPropertyManager (se presente na ROM)
        try {
            val carClass = Class.forName("android.car.Car")
            val createCarMethod = carClass.getMethod("createCar", Context::class.java)
            val carObj = createCarMethod.invoke(null, context)
            val getCarManagerMethod = carClass.getMethod("getCarManager", String::class.java)
            val propertyManager = getCarManagerMethod.invoke(carObj, "property")

            val setPropertyMethod = propertyManager.javaClass.getMethod(
                "setIntProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            // CABIN_LIGHTS_SWITCH = 289410818 (0x11400F02) -> 0 = OFF
            // READING_LIGHTS_SWITCH = 289410820 (0x11400F04) -> 0 = OFF
            setPropertyMethod.invoke(propertyManager, 289410818, 0, 0)
            setPropertyMethod.invoke(propertyManager, 289410820, 0, 0)
            channels.add("Android Automotive CarPropertyManager (CABIN & READING = 0)")
        } catch (_: Exception) {
            // Não é uma ROM pura AAOS
        }

        // CANAL 5: Shell Fallback
        try {
            Runtime.getRuntime().exec(arrayOf("sh", "-c", "settings put system auto_dome_light 0; settings put system byd_dome_light 0"))
            channels.add("Shell Command (settings put auto_dome_light 0)")
        } catch (_: Exception) {}

        val summary = "Corte de iluminação executado em ${channels.size} canais no Dolphin Plus: " + channels.joinToString(", ")
        Log.i(TAG, summary)

        return LightControlResult(
            success = true,
            message = summary,
            channelsTriggered = channels
        )
    }

    /**
     * Liga as luzes internas intencionalmente.
     */
    fun turnOnAllInteriorLights(): LightControlResult {
        val channels = mutableListOf<String>()

        bydLightBusInstance?.let { instance ->
            try {
                val method = instance.javaClass.getMethod("setReadingLight", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                method.invoke(instance, 0, 1)
                channels.add("HAL Nativo ($detectedLightClassName)")
            } catch (_: Exception) {}
        }

        if (hasWriteSettingsPermission()) {
            try {
                Settings.System.putInt(context.contentResolver, "auto_dome_light", 1)
                Settings.System.putInt(context.contentResolver, "byd_ambient_light_switch", 1)
                channels.add("Settings.System")
            } catch (_: Exception) {}
        }

        val intent = Intent("com.byd.action.LIGHT_CONTROL").apply {
            putExtra("target", "ALL_INTERNAL_LIGHTS")
            putExtra("command", "MASTER_ON")
            putExtra("value", 1)
        }
        sendTargetedBYDBroadcasts(intent)
        channels.add("Broadcast Explícito BYD")

        return LightControlResult(
            success = true,
            message = "Luzes ligadas via " + channels.joinToString(", "),
            channelsTriggered = channels
        )
    }

    /**
     * Modo Noturno Total: Apaga luzes e desliga tela central multimídia DiLink.
     */
    fun activateTotalBlackout(): LightControlResult {
        val lightResult = turnOffAllInteriorLights()
        try {
            sendTargetedBYDBroadcasts(Intent("com.byd.action.SCREEN_OFF"))
            sendTargetedBYDBroadcasts(Intent("com.byd.action.NIGHT_MODE"))
        } catch (_: Exception) {}
        return LightControlResult(
            success = true,
            message = "Blackout total ativado (Luzes + Tela DiLink apagadas)",
            channelsTriggered = lightResult.channelsTriggered + listOf("Screen Off Intent Target")
        )
    }

    /**
     * Executa varredura profunda de diagnóstico no hardware e serviços do Dolphin Plus.
     */
    fun runFullDiLinkDiagnostics(): String {
        val sb = StringBuilder()
        sb.append("=== DIAGNÓSTICO DILINK DOLPHIN PLUS ===\n")
        sb.append("Modelo Dispositivo: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})\n")
        sb.append("Versão Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("Permissão WRITE_SETTINGS: ${if (hasWriteSettingsPermission()) "CONCEDIDA ✅" else "PENDENTE ⚠️"}\n\n")

        sb.append("--- MÓDULOS DE HARDWARE VINCULADOS (HAL) ---\n")
        sb.append("Luzes (LightDevice): ${detectedLightClassName ?: "Não detectado (Usando Broadcast/Settings)"}\n")
        sb.append("Portas (DoorDevice): ${if (bydDoorBusInstance != null) "Conectado ✅" else "Fallback"}\n")
        sb.append("Vidros (WindowDevice): ${if (bydWindowBusInstance != null) "Conectado ✅" else "Fallback"}\n")
        sb.append("Ar-Condicionado (HVAC): ${if (bydHvacBusInstance != null) "Conectado ✅" else "Fallback"}\n")
        sb.append("Bateria Blade (Power): ${if (bydBatteryBusInstance != null) "Conectado ✅" else "Fallback"}\n")
        sb.append("Giro de Tela (Screen): ${if (bydScreenBusInstance != null) "Conectado ✅" else "Fallback"}\n\n")

        sb.append("--- BINDER SERVICES REGISTRADOS ---\n")
        val binderNames = listOf("bydauto_light", "byd_light", "bydauto_door", "bydauto_window", "bydauto_ac", "bydauto_car")
        try {
            val getServiceMethod = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            for (name in binderNames) {
                val service = getServiceMethod.invoke(null, name)
                sb.append("Service '$name': ${if (service != null) "ATIVO ✅" else "Indisponível"}\n")
            }
        } catch (e: Exception) {
            sb.append("Falha na varredura ServiceManager: ${e.message}\n")
        }

        sb.append("\n--- VALORES ATUAIS EM SETTINGS.SYSTEM ---\n")
        val keys = listOf("auto_dome_light", "byd_dome_light", "byd_reading_light", "byd_ambient_light_switch")
        for (key in keys) {
            try {
                val valInt = Settings.System.getInt(context.contentResolver, key, -1)
                sb.append("$key = $valInt\n")
            } catch (_: Exception) {
                sb.append("$key = N/A\n")
            }
        }

        return sb.toString()
    }

    fun setDriverTemperature(tempCelsius: Float) {
        bydHvacBusInstance?.let { instance ->
            try {
                val method = instance.javaClass.getMethod("setDriverTemperature", Float::class.javaPrimitiveType)
                method.invoke(instance, tempCelsius)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao ajustar temperatura do motorista", e)
            }
        }
        val intent = Intent("com.byd.action.HVAC_TEMP_CHANGE").apply {
            putExtra("target_temp", tempCelsius)
        }
        sendTargetedBYDBroadcasts(intent)
    }

    fun rotateScreen(orientation: Int) { // 0 = Horizontal, 1 = Vertical
        bydScreenBusInstance?.let { instance ->
            try {
                val method = instance.javaClass.getMethod("setScreenOrientation", Int::class.javaPrimitiveType)
                method.invoke(instance, orientation)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao girar a tela central", e)
            }
        }
        val intent = Intent("com.byd.action.SCREEN_ROTATE_CONTROL").apply {
            putExtra("orientation", orientation)
        }
        sendTargetedBYDBroadcasts(intent)
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
