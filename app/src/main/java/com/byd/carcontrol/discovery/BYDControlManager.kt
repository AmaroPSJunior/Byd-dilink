package com.byd.carcontrol.discovery

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.byd.carcontrol.repository.DiscoveryRepository
import org.json.JSONObject
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ControlStatus {
    PRONTO,
    BLOQUEADO,
    ERRO
}

enum class ControlAction {
    ABRIR,
    FECHAR,
    PARAR,
    LIGAR,
    DESLIGAR
}

data class ControlInfo(
    val id: String,
    val title: String,
    val description: String,
    val targetClass: String,
    val requiredPermission: String,
    var status: ControlStatus,
    var statusReason: String,
    var detectedMethod: String? = null,
    var detectedArgsDesc: String? = null,
    var lastExecutionTime: String? = null,
    var lastResult: String? = null
)

data class ControlLogEntry(
    val timestamp: String,
    val controlId: String,
    val controlTitle: String,
    val targetClass: String,
    val methodName: String,
    val args: String,
    val permission: String,
    val success: Boolean,
    val result: String,
    val exception: String? = null
)

/**
 * Gerenciador permanente de controles BYD reais descobertos via reflexão.
 * Implementa detecção dinâmica de API, verificação de permissões e execução controlada com confirmação prévia.
 */
class BYDControlManager(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    companion object {
        private const val TAG = "BYDControlManager"

        const val BODYWORK_CLASS = "android.hardware.bydauto.bodywork.BYDAutoBodyworkDevice"
        const val AC_CLASS_1 = "android.hardware.bydauto.ac.BYDAutoAcDevice"
        const val AC_CLASS_2 = "android.hardware.bydauto.aircondition.BYDAutoAirConditionDevice"
    }

    private val controlLogs = mutableListOf<ControlLogEntry>()
    val controlsMap = mutableMapOf<String, ControlInfo>()

    init {
        initializeControlsMap()
        probeAllControls()
    }

    private fun initializeControlsMap() {
        controlsMap["SUNSHADE"] = ControlInfo(
            id = "SUNSHADE",
            title = "1. PERSIANA DO TETO",
            description = "Controle de abertura/fechamento da cortina/persiana do teto panorâmico.",
            targetClass = BODYWORK_CLASS,
            requiredPermission = "android.permission.BYDAUTO_BODYWORK_SET",
            status = ControlStatus.BLOQUEADO,
            statusReason = "Probing em andamento..."
        )

        controlsMap["MOONROOF"] = ControlInfo(
            id = "MOONROOF",
            title = "2. TETO SOLAR",
            description = "Controle de abertura/fechamento e parada do teto solar panorâmico.",
            targetClass = BODYWORK_CLASS,
            requiredPermission = "android.permission.BYDAUTO_BODYWORK_SET",
            status = ControlStatus.BLOQUEADO,
            statusReason = "Probing em andamento..."
        )

        controlsMap["DRIVER_WINDOW"] = ControlInfo(
            id = "DRIVER_WINDOW",
            title = "3. VIDRO MOTORISTA",
            description = "Controle de acionamento do vidro elétrico da porta do motorista.",
            targetClass = BODYWORK_CLASS,
            requiredPermission = "android.permission.BYDAUTO_BODYWORK_SET",
            status = ControlStatus.BLOQUEADO,
            statusReason = "Probing em andamento..."
        )

        controlsMap["AC"] = ControlInfo(
            id = "AC",
            title = "4. AR-CONDICIONADO",
            description = "Controle de ligar/desligar o sistema de climatização veicular.",
            targetClass = AC_CLASS_1,
            requiredPermission = "android.permission.BYDAUTO_AC_SET",
            status = ControlStatus.BLOQUEADO,
            statusReason = "Probing em andamento..."
        )

        controlsMap["INTERIOR_LIGHT"] = ControlInfo(
            id = "INTERIOR_LIGHT",
            title = "5. LUZ INTERNA",
            description = "Controle da luz de teto/cortesia. Mantido bloqueado por segurança até confirmação da API real.",
            targetClass = "android.hardware.bydauto.light.BYDAutoLightDevice",
            requiredPermission = "android.permission.BYDAUTO_LIGHT_SET",
            status = ControlStatus.BLOQUEADO,
            statusReason = "Ainda não identificamos com segurança a API da luz de teto/cortesia. Não usar LIGHT_FOOT nem faróis como substituição."
        )
    }

    fun probeAllControls() {
        val bodyworkInstance = getDeviceInstance(BODYWORK_CLASS)
        val bodyworkClass = try { Class.forName(BODYWORK_CLASS) } catch (_: Throwable) { null }

        // 1. PROBE SUNSHADE (PERSIANA DO TETO)
        probeSunshade(bodyworkClass, bodyworkInstance)

        // 2. PROBE MOONROOF (TETO SOLAR)
        probeMoonroof(bodyworkClass, bodyworkInstance)

        // 3. PROBE DRIVER WINDOW (VIDRO MOTORISTA)
        probeDriverWindow(bodyworkClass, bodyworkInstance)

        // 4. PROBE AC (AR-CONDICIONADO)
        probeAC()

        // 5. INTERIOR LIGHT remains BLOQUEADO as instructed
    }

    private fun probeSunshade(clazz: Class<*>?, instance: Any?) {
        val ctrl = controlsMap["SUNSHADE"] ?: return
        if (clazz == null || instance == null) {
            ctrl.status = ControlStatus.BLOQUEADO
            ctrl.statusReason = "Instância de $BODYWORK_CLASS não foi obtida no runtime."
            return
        }

        val methodCandidates = listOf(
            "setSunshadeState", "setSunshadePos", "setSunroofSunshadeState", "setSunshadeCtrl"
        )
        var foundMethod: Method? = null
        for (mName in methodCandidates) {
            val m = clazz.methods.firstOrNull { it.name == mName && it.parameterTypes.size == 1 }
            if (m != null) {
                foundMethod = m
                break
            }
        }

        val constantsFound = findConstantFields(clazz, listOf("SUNSHADE", "SHADE"))
        if (foundMethod != null && constantsFound.isNotEmpty()) {
            ctrl.status = ControlStatus.PRONTO
            ctrl.detectedMethod = "${foundMethod.name}(${foundMethod.parameterTypes[0].simpleName})"
            ctrl.detectedArgsDesc = "Valores de constante descobertos: " + constantsFound.entries.joinToString { "${it.key}=${it.value}" }
            ctrl.statusReason = "Chamada e parâmetros identificados com sucesso."
        } else if (foundMethod != null) {
            ctrl.status = ControlStatus.PRONTO
            ctrl.detectedMethod = "${foundMethod.name}(${foundMethod.parameterTypes[0].simpleName})"
            ctrl.detectedArgsDesc = "Assinatura encontrada. Mapeando valores numéricos padrão (1=ABRIR, 2=FECHAR, 3=PARAR)."
            ctrl.statusReason = "Método descoberto em Bodywork."
        } else {
            ctrl.status = ControlStatus.BLOQUEADO
            ctrl.statusReason = "Método de controle de persiana não localizado na classe Bodywork."
        }
    }

    private fun probeMoonroof(clazz: Class<*>?, instance: Any?) {
        val ctrl = controlsMap["MOONROOF"] ?: return
        if (clazz == null || instance == null) {
            ctrl.status = ControlStatus.BLOQUEADO
            ctrl.statusReason = "Instância de $BODYWORK_CLASS não foi obtida no runtime."
            return
        }

        val methodCandidates = listOf(
            "setMoonRoofState", "setMoonroofState", "setSunRoofState", "setSunroofState", "setMoonroofCtrl"
        )
        var foundMethod: Method? = null
        for (mName in methodCandidates) {
            val m = clazz.methods.firstOrNull { it.name == mName && it.parameterTypes.size == 1 }
            if (m != null) {
                foundMethod = m
                break
            }
        }

        val constantsFound = findConstantFields(clazz, listOf("MOONROOF", "SUNROOF"))
        if (foundMethod != null) {
            ctrl.status = ControlStatus.PRONTO
            ctrl.detectedMethod = "${foundMethod.name}(${foundMethod.parameterTypes[0].simpleName})"
            ctrl.detectedArgsDesc = if (constantsFound.isNotEmpty()) {
                "Valores de constante descobertos: " + constantsFound.entries.joinToString { "${it.key}=${it.value}" }
            } else {
                "Assinatura encontrada. Mapeamento padrão (1=ABRIR, 2=FECHAR, 3=PARAR)."
            }
            ctrl.statusReason = "Chamada e parâmetros identificados com sucesso."
        } else {
            ctrl.status = ControlStatus.BLOQUEADO
            ctrl.statusReason = "Método de controle do teto solar não localizado em Bodywork."
        }
    }

    private fun probeDriverWindow(clazz: Class<*>?, instance: Any?) {
        val ctrl = controlsMap["DRIVER_WINDOW"] ?: return
        if (clazz == null || instance == null) {
            ctrl.status = ControlStatus.BLOQUEADO
            ctrl.statusReason = "Instância de $BODYWORK_CLASS não foi obtida no runtime."
            return
        }

        val methodCandidates = listOf(
            "setBodyWindowCtrlState", "setWindowCtrlState", "setWindowPos", "setDriverWindowPos"
        )
        var foundMethod: Method? = null
        for (mName in methodCandidates) {
            val m = clazz.methods.firstOrNull { it.name == mName }
            if (m != null) {
                foundMethod = m
                break
            }
        }

        val windowConstants = findConstantFields(clazz, listOf("WINDOW", "BODY_WINDOW"))
        if (foundMethod != null && windowConstants.isNotEmpty()) {
            ctrl.status = ControlStatus.PRONTO
            ctrl.detectedMethod = "${foundMethod.name}(${foundMethod.parameterTypes.map { it.simpleName }.joinToString()})"
            ctrl.detectedArgsDesc = "Campos e constantes de janela confirmados: " + windowConstants.entries.take(5).joinToString { "${it.key}=${it.value}" }
            ctrl.statusReason = "Chamada e parâmetros confirmados por evidência de reflexão."
        } else {
            ctrl.status = ControlStatus.BLOQUEADO
            ctrl.statusReason = "Parâmetros ainda não confirmados por evidência de reflexão (evitando envio de FIDs inseguros)."
        }
    }

    private fun probeAC() {
        val ctrl = controlsMap["AC"] ?: return

        var acClass: Class<*>? = null
        var acTargetName = AC_CLASS_1
        try {
            acClass = Class.forName(AC_CLASS_1)
        } catch (_: Throwable) {
            try {
                acClass = Class.forName(AC_CLASS_2)
                acTargetName = AC_CLASS_2
            } catch (_: Throwable) {}
        }

        if (acClass == null) {
            ctrl.status = ControlStatus.BLOQUEADO
            ctrl.statusReason = "Classe de Ar-Condicionado ($AC_CLASS_1 / $AC_CLASS_2) não encontrada no framework."
            return
        }

        val acInstance = getDeviceInstance(acTargetName)
        if (acInstance == null) {
            ctrl.status = ControlStatus.BLOQUEADO
            ctrl.statusReason = "Classe $acTargetName encontrada, mas a instância não foi obtida no runtime."
            return
        }

        val methodCandidates = listOf(
            "setAcPowerState", "setAcState", "setAcPower", "setAirConditionPower", "setPowerState"
        )
        var foundMethod: Method? = null
        for (mName in methodCandidates) {
            val m = acClass.methods.firstOrNull { it.name == mName && it.parameterTypes.size == 1 }
            if (m != null) {
                foundMethod = m
                break
            }
        }

        val acConstants = findConstantFields(acClass, listOf("AC", "POWER", "AIRCONDITION"))
        if (foundMethod != null && acConstants.isNotEmpty()) {
            ctrl.status = ControlStatus.PRONTO
            ctrl.targetClass = acTargetName
            ctrl.detectedMethod = "${foundMethod.name}(${foundMethod.parameterTypes[0].simpleName})"
            ctrl.detectedArgsDesc = "Constantes de AC descobertas: " + acConstants.entries.take(4).joinToString { "${it.key}=${it.value}" }
            ctrl.statusReason = "Chamada e valores de AC confirmados na classe $acTargetName."
        } else if (foundMethod != null) {
            ctrl.status = ControlStatus.PRONTO
            ctrl.targetClass = acTargetName
            ctrl.detectedMethod = "${foundMethod.name}(${foundMethod.parameterTypes[0].simpleName})"
            ctrl.detectedArgsDesc = "Método encontrado. Mapeando 1=LIGAR, 0=DESLIGAR."
            ctrl.statusReason = "Método de controle de AC descoberto em $acTargetName."
        } else {
            ctrl.status = ControlStatus.BLOQUEADO
            ctrl.statusReason = "Aguardando identificação de método e valores concretos na classe de AC."
        }
    }

    fun executeControlAction(controlId: String, action: ControlAction): String {
        val ctrl = controlsMap[controlId] ?: return "Controle $controlId não encontrado."
        val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        if (ctrl.status == ControlStatus.BLOQUEADO) {
            val msg = "Ação bloqueada: ${ctrl.statusReason}"
            recordLog(
                timeStamp = timeStamp,
                controlId = controlId,
                controlTitle = ctrl.title,
                targetClass = ctrl.targetClass,
                methodName = ctrl.detectedMethod ?: "N/A",
                args = action.name,
                permission = ctrl.requiredPermission,
                success = false,
                result = msg
            )
            return msg
        }

        val deviceInstance = getDeviceInstance(ctrl.targetClass)
        if (deviceInstance == null) {
            ctrl.status = ControlStatus.ERRO
            val msg = "Erro: Não foi possível obter a instância da classe ${ctrl.targetClass} no runtime."
            ctrl.lastResult = msg
            recordLog(
                timeStamp = timeStamp,
                controlId = controlId,
                controlTitle = ctrl.title,
                targetClass = ctrl.targetClass,
                methodName = ctrl.detectedMethod ?: "N/A",
                args = action.name,
                permission = ctrl.requiredPermission,
                success = false,
                result = msg
            )
            return msg
        }

        return try {
            val clazz = deviceInstance.javaClass
            val targetMethodName = ctrl.detectedMethod?.substringBefore("(")

            if (targetMethodName == null) {
                ctrl.status = ControlStatus.ERRO
                val msg = "Erro: Método de destino não configurado para $controlId."
                ctrl.lastResult = msg
                recordLog(timeStamp, controlId, ctrl.title, ctrl.targetClass, "NONE", action.name, ctrl.requiredPermission, false, msg)
                return msg
            }

            val targetMethod = clazz.methods.firstOrNull { it.name == targetMethodName }
            if (targetMethod == null) {
                ctrl.status = ControlStatus.ERRO
                val msg = "Erro: Método $targetMethodName não localizado em ${clazz.name}."
                ctrl.lastResult = msg
                recordLog(timeStamp, controlId, ctrl.title, ctrl.targetClass, targetMethodName, action.name, ctrl.requiredPermission, false, msg)
                return msg
            }

            val argValue = mapActionToParamValue(clazz, controlId, action)
            val methodParams = targetMethod.parameterTypes

            val invokeResult = if (methodParams.size == 1) {
                targetMethod.isAccessible = true
                targetMethod.invoke(deviceInstance, argValue)
            } else if (methodParams.size == 2) {
                targetMethod.isAccessible = true
                val windowIndex = getWindowIndexParam(clazz)
                targetMethod.invoke(deviceInstance, windowIndex, argValue)
            } else {
                targetMethod.isAccessible = true
                targetMethod.invoke(deviceInstance)
            }

            val resStr = "Sucesso. Retorno da API BYD: ${invokeResult ?: "void/0"}"
            ctrl.lastExecutionTime = timeStamp
            ctrl.lastResult = resStr

            recordLog(
                timeStamp = timeStamp,
                controlId = controlId,
                controlTitle = ctrl.title,
                targetClass = ctrl.targetClass,
                methodName = targetMethod.name,
                args = "Ação=${action.name}, ValorEnviado=$argValue",
                permission = ctrl.requiredPermission,
                success = true,
                result = resStr
            )

            resStr
        } catch (t: Throwable) {
            ctrl.status = ControlStatus.ERRO
            val errMsg = "Exceção ao chamar API BYD: ${t.javaClass.simpleName} - ${t.message}"
            ctrl.lastExecutionTime = timeStamp
            ctrl.lastResult = errMsg

            recordLog(
                timeStamp = timeStamp,
                controlId = controlId,
                controlTitle = ctrl.title,
                targetClass = ctrl.targetClass,
                methodName = ctrl.detectedMethod ?: "N/A",
                args = action.name,
                permission = ctrl.requiredPermission,
                success = false,
                result = errMsg,
                exception = t.stackTrace.take(3).joinToString("; ")
            )

            errMsg
        }
    }

    private fun mapActionToParamValue(clazz: Class<*>, controlId: String, action: ControlAction): Int {
        val constants = findConstantFields(clazz, listOf(controlId, "STATE", "CTRL", "OPEN", "CLOSE", "STOP", "POWER", "ON", "OFF"))

        return when (action) {
            ControlAction.ABRIR -> {
                constants["SUNSHADE_OPEN"] ?: constants["SUNSHADE_STATE_OPEN"] ?:
                constants["MOONROOF_OPEN"] ?: constants["MOONROOF_STATE_OPEN"] ?:
                constants["WINDOW_OPEN"] ?: constants["OPEN"] ?: 1
            }
            ControlAction.FECHAR -> {
                constants["SUNSHADE_CLOSE"] ?: constants["SUNSHADE_STATE_CLOSE"] ?:
                constants["MOONROOF_CLOSE"] ?: constants["MOONROOF_STATE_CLOSE"] ?:
                constants["WINDOW_CLOSE"] ?: constants["CLOSE"] ?: 2
            }
            ControlAction.PARAR -> {
                constants["SUNSHADE_STOP"] ?: constants["SUNSHADE_STATE_STOP"] ?:
                constants["MOONROOF_STOP"] ?: constants["MOONROOF_STATE_STOP"] ?:
                constants["WINDOW_STOP"] ?: constants["STOP"] ?: 3
            }
            ControlAction.LIGAR -> {
                constants["AC_POWER_ON"] ?: constants["POWER_ON"] ?: constants["ON"] ?: 1
            }
            ControlAction.DESLIGAR -> {
                constants["AC_POWER_OFF"] ?: constants["POWER_OFF"] ?: constants["OFF"] ?: 0
            }
        }
    }

    private fun getWindowIndexParam(clazz: Class<*>): Int {
        val fields = findConstantFields(clazz, listOf("DRIVER", "LEFT_FRONT", "FL", "FRONT_LEFT", "WINDOW"))
        return fields["WINDOW_DRIVER"] ?: fields["WINDOW_FL"] ?: fields["WINDOW_LEFT_FRONT"] ?: fields["WINDOW_INDEX_FL"] ?: 1
    }

    private fun getDeviceInstance(className: String): Any? {
        return try {
            val clazz = Class.forName(className)
            // 1. Try public getMethod("getInstance", Context)
            try {
                val m = clazz.getMethod("getInstance", Context::class.java)
                m.isAccessible = true
                val inst = m.invoke(null, context)
                if (inst != null) return inst
            } catch (_: Throwable) {}

            // 2. Try public getMethod("getInstance")
            try {
                val m = clazz.getMethod("getInstance")
                m.isAccessible = true
                val inst = m.invoke(null)
                if (inst != null) return inst
            } catch (_: Throwable) {}

            // 3. Try declared getDeclaredMethod("getInstance", Context)
            try {
                val m = clazz.getDeclaredMethod("getInstance", Context::class.java)
                m.isAccessible = true
                val inst = m.invoke(null, context)
                if (inst != null) return inst
            } catch (_: Throwable) {}

            // 4. Try declared getDeclaredMethod("getInstance")
            try {
                val m = clazz.getDeclaredMethod("getInstance")
                m.isAccessible = true
                val inst = m.invoke(null)
                if (inst != null) return inst
            } catch (_: Throwable) {}

            // 5. Try constructors
            for (c in clazz.declaredConstructors) {
                try {
                    c.isAccessible = true
                    if (c.parameterTypes.size == 1 && c.parameterTypes[0].isAssignableFrom(Context::class.java)) {
                        val inst = c.newInstance(context)
                        if (inst != null) return inst
                    } else if (c.parameterTypes.isEmpty()) {
                        val inst = c.newInstance()
                        if (inst != null) return inst
                    }
                } catch (_: Throwable) {}
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun findConstantFields(clazz: Class<*>, keywords: List<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        try {
            for (f in clazz.declaredFields) {
                if (Modifier.isStatic(f.modifiers) && (f.type == Int::class.javaPrimitiveType || f.type == Integer::class.java)) {
                    val nameLower = f.name.lowercase()
                    if (keywords.any { nameLower.contains(it.lowercase()) }) {
                        f.isAccessible = true
                        try {
                            val value = f.get(null) as? Int
                            if (value != null) {
                                result[f.name] = value
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }
        } catch (_: Throwable) {}
        return result
    }

    private fun recordLog(
        timeStamp: String,
        controlId: String,
        controlTitle: String,
        targetClass: String,
        methodName: String,
        args: String,
        permission: String,
        success: Boolean,
        result: String,
        exception: String? = null
    ) {
        val entry = ControlLogEntry(
            timestamp = timeStamp,
            controlId = controlId,
            controlTitle = controlTitle,
            targetClass = targetClass,
            methodName = methodName,
            args = args,
            permission = permission,
            success = success,
            result = result,
            exception = exception
        )
        controlLogs.add(entry)

        try {
            repository.saveDiscovery(
                category = "BYD_HARDWARE_CONTROL_EXECUTION",
                name = "${controlId}_$timeStamp",
                status = if (success) com.byd.carcontrol.discovery.DiscoveryStatus.VALIDATED else com.byd.carcontrol.discovery.DiscoveryStatus.EXECUTION_FAILED,
                evidenceJson = JSONObject().apply {
                    put("controlTitle", controlTitle)
                    put("targetClass", targetClass)
                    put("methodName", methodName)
                    put("args", args)
                    put("permission", permission)
                    put("result", result)
                    if (exception != null) put("exception", exception)
                }.toString()
            )
        } catch (_: Throwable) {}
    }

    fun getControlLogsReport(): String {
        val sb = StringBuilder()
        sb.append("===== BYD HARDWARE CONTROLS EXECUTION LOGS =====\n")
        sb.append("Total Executions Recorded: ${controlLogs.size}\n\n")

        if (controlLogs.isEmpty()) {
            sb.append("Nenhum comando físico foi disparado nesta sessão.\n\n")
        } else {
            controlLogs.forEachIndexed { idx, log ->
                sb.append("LOG [${idx + 1}/${controlLogs.size}]\n")
                sb.append("Data/Hora: ${log.timestamp}\n")
                sb.append("Controle: ${log.controlTitle} (${log.controlId})\n")
                sb.append("Classe: ${log.targetClass}\n")
                sb.append("Método: ${log.methodName}\n")
                sb.append("Argumentos: ${log.args}\n")
                sb.append("Permissão Requerida: ${log.permission}\n")
                sb.append("Status Execução: ${if (log.success) "SUCESSO" else "FALHA/ERRO"}\n")
                sb.append("Resultado: ${log.result}\n")
                if (log.exception != null) sb.append("Exceção: ${log.exception}\n")
                sb.append("\n")
            }
        }
        return sb.toString()
    }
}
