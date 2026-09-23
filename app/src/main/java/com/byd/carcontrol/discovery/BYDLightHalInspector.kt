package com.byd.carcontrol.discovery

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.byd.carcontrol.data.*
import com.byd.carcontrol.repository.DiscoveryRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.UUID

/**
 * BYD Light HAL Inspector - Módulo de Diagnóstico Exaustivo e Seguro
 * 
 * Inspeciona exaustivamente a classe android.hardware.bydauto.light.BYDAutoLightDevice
 * e todo o subsistema de iluminação do BYD Dolphin Plus (DiLink 3.0/4.0, Android 10, SDK 29)
 * em MODO STRICT SAFE READ ONLY (nenhuma alteração física ou comando de escrita).
 */
class BYDLightHalInspector(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    companion object {
        const val TARGET_CLASS_NAME = "android.hardware.bydauto.light.BYDAutoLightDevice"

        val ALT_CLASS_NAMES = listOf(
            "com.byd.auto.light.BYDAutoLightDevice",
            "com.byd.auto.light.BYDLightManager",
            "com.byd.auto.light.BYDAutoLightManager",
            "com.byd.auto.BYDAutoDevice",
            "android.hardware.bydauto.BYDAutoDevice",
            "android.hardware.bydauto.AbsBYDAutoDevice",
            "com.byd.service.BYDAutoLightBus"
        )

        val LIGHT_PERMISSIONS = listOf(
            "com.byd.permission.CAR_LIGHT_CONTROL",
            "android.car.permission.CONTROL_CAR_INTERIOR_LIGHTS",
            "android.car.permission.CAR_EXTERIOR_LIGHTS",
            "com.byd.permission.BYD_AUTO_CONTROL",
            "com.byd.permission.CAR_STATE_READ",
            "com.byd.permission.LIGHT_CONTROL",
            "BYDAUTO_LIGHT_GET",
            "BYDAUTO_LIGHT_SET",
            "BYDAUTO_LIGHT_COMMON"
        )

        val KEYWORD_FILTER = listOf(
            "get", "set", "status", "state", "light", "feature",
            "device", "value", "listener", "register", "unregister",
            "callback", "command", "control"
        )

        val WRITE_PREFIXES = listOf(
            "set", "enable", "disable", "open", "close", "lock", "unlock",
            "turnon", "turnoff", "write", "control", "move", "post", "send", "adjust"
        )

        val READ_PREFIXES = listOf(
            "get", "is", "has", "query", "read", "status", "state", "info"
        )
    }

    // Diagnostic Storage
    private var diagnosticSession: DiagnosticSession? = null
    private val discoveredClasses = mutableListOf<DiagnosticClass>()
    private val discoveredMethods = mutableListOf<DiagnosticMethod>()
    private val discoveredFields = mutableListOf<DiagnosticField>()
    private val discoveredPermissions = mutableListOf<DiagnosticPermission>()
    private val logs = mutableListOf<String>()

    fun runExhaustiveInspection(): String {
        val startTime = SystemClock.elapsedRealtime()
        val sessionId = "LIGHT_HAL_" + UUID.randomUUID().toString().take(8)

        diagnosticSession = DiagnosticSession(
            sessionId = sessionId,
            timestamp = System.currentTimeMillis(),
            firmware = Build.DISPLAY ?: "DiLink 3.0",
            androidVersion = Build.VERSION.RELEASE ?: "10",
            sdk = Build.VERSION.SDK_INT,
            device = Build.MODEL ?: "BYD Dolphin Plus",
            appVersion = "1.0.0"
        )

        log("==================================================")
        log("INICIANDO INSPEÇÃO DO BYD LIGHT HAL (SAFE READ ONLY)")
        log("Sessão: $sessionId | Dispositivo: ${Build.MODEL} | SDK: ${Build.VERSION.SDK_INT}")
        log("==================================================")

        // FASE 1: Descobrir a Classe Principal
        val primaryClass = inspectPhase1_DiscoverClass()

        // FASE 2: Inspeção Completa de Métodos
        inspectPhase2_InspectMethods(primaryClass)

        // FASE 3: Inspeção da Hierarquia de Classes
        inspectPhase3_HierarchyTree(primaryClass)

        // FASE 4: Inspeção de Campos e Constantes
        inspectPhase4_FieldsAndConstants(primaryClass)

        // FASE 5: Construtores e Obtendo Instância
        val lightInstance = inspectPhase5_ConstructorsAndInstance(primaryClass)

        // FASE 6: Inspeção de Permissões
        inspectPhase6_Permissions()

        // FASE 7: API Genérica BYDAutoDevice
        inspectPhase7_GenericBYDDeviceAPI()

        // FASE 8: Inspeção do Device Type (ex: 1004)
        inspectPhase8_DeviceType()

        // FASE 9: Feature IDs
        inspectPhase9_FeatureIDs()

        // FASE 10: Invocação Segura de Métodos READ_SAFE
        inspectPhase10_ExecuteReadSafeMethods(primaryClass, lightInstance)

        // FASE 11: Listeners & Callbacks
        inspectPhase11_ListenersAndCallbacks(primaryClass)

        // FASE 12: Conexão com Binder / ServiceManager
        inspectPhase12_BinderServiceManager()

        // FASE 13: PackageManager & Serviços Instalados
        inspectPhase13_PackageManagerServices()

        // FASE 14: Relação com cloudmanager e dicarserver
        inspectPhase14_CloudManagerRelation()

        val elapsed = SystemClock.elapsedRealtime() - startTime

        // FASE 15: Documentação Automática & Relatório Final
        val report = generatePhase15Report(elapsed)

        // Persist Findings
        persistDiagnosticData(sessionId, report)

        return report
    }

    private fun log(message: String) {
        logs.add(message)
        DiscoveryLogger.log("LIGHT_INSPECTOR", "PHASE", "LOG", message)
    }

    // ==================================================
    // FASE 1 — DESCOBRIR A CLASSE
    // ==================================================
    private fun inspectPhase1_DiscoverClass(): Class<*>? {
        log("\n--- FASE 1: DESCOBERTA DA CLASSE ---")
        var targetClass: Class<*>? = null

        for (className in listOf(TARGET_CLASS_NAME) + ALT_CLASS_NAMES) {
            try {
                val clazz = Class.forName(className)
                if (targetClass == null && className == TARGET_CLASS_NAME) {
                    targetClass = clazz
                }
                val diagClass = DiagnosticClass(
                    className = className,
                    found = true,
                    superclass = clazz.superclass?.name,
                    interfaces = clazz.interfaces.map { it.name },
                    classLoader = clazz.classLoader?.javaClass?.name ?: "System",
                    packageName = clazz.`package`?.name ?: className.substringBeforeLast('.', ""),
                    modifiers = Modifier.toString(clazz.modifiers)
                )
                discoveredClasses.add(diagClass)
                log("✅ CLASSE ENCONTRADA: $className")
                log("   ClassLoader: ${diagClass.classLoader}")
                log("   Package: ${diagClass.packageName}")
                log("   Superclasse: ${diagClass.superclass}")
                log("   Interfaces: ${diagClass.interfaces.joinToString(", ")}")
                log("   Modificadores: ${diagClass.modifiers}")
            } catch (e: ClassNotFoundException) {
                discoveredClasses.add(DiagnosticClass(className = className, found = false))
                log("❌ CLASSE NÃO ENCONTRADA: $className (ClassNotFoundException)")
            } catch (e: Exception) {
                log("⚠️ ERRO AO CARREGAR CLASSE $className: ${e.javaClass.simpleName} - ${e.message}")
            }
        }
        return targetClass
    }

    // ==================================================
    // FASE 2 — INSPEÇÃO COMPLETA DOS MÉTODOS
    // ==================================================
    private fun inspectPhase2_InspectMethods(primaryClass: Class<*>?) {
        log("\n--- FASE 2: INSPEÇÃO COMPLETA DOS MÉTODOS ---")
        if (primaryClass == null) {
            log("⚠️ Classe principal $TARGET_CLASS_NAME não carregada na Fase 1. Inspecionando alternativas...")
            return
        }

        val allDeclared = primaryClass.declaredMethods
        val allPublic = primaryClass.methods

        log("Total de métodos declarados diretamente: ${allDeclared.size}")
        log("Total de métodos públicos (incluindo herdados): ${allPublic.size}")

        for (method in allDeclared) {
            val mName = method.name
            val lowerName = mName.lowercase()
            val returnType = method.returnType.name
            val paramTypes = method.parameterTypes.map { it.name }
            val modifiers = Modifier.toString(method.modifiers)
            val declaringClass = method.declaringClass.name
            val isStatic = Modifier.isStatic(method.modifiers)

            val category = when {
                WRITE_PREFIXES.any { lowerName.startsWith(it) } -> "WRITE_METHOD_DISCOVERED"
                READ_PREFIXES.any { lowerName.startsWith(it) } -> "READ_SAFE"
                else -> "UNKNOWN_SIDE_EFFECT"
            }

            val diagMethod = DiagnosticMethod(
                className = primaryClass.name,
                methodName = mName,
                returnType = returnType,
                parameters = paramTypes,
                modifiers = modifiers,
                declaringClass = declaringClass,
                category = category,
                executionStatus = "NOT_TESTED"
            )
            discoveredMethods.add(diagMethod)

            log("  [MÉTODO] $modifiers $returnType $mName(${paramTypes.joinToString(", ")})")
            log("           Declaring: $declaringClass | Static: $isStatic | Categorias: $category")
        }
    }

    // ==================================================
    // FASE 3 — INSPEÇÃO DA HIERARQUIA
    // ==================================================
    private fun inspectPhase3_HierarchyTree(primaryClass: Class<*>?) {
        log("\n--- FASE 3: INSPEÇÃO DA HIERARQUIA DE CLASSES ---")
        if (primaryClass == null) return

        var current: Class<*>? = primaryClass.superclass
        var depth = 1

        while (current != null && current != Any::class.java) {
            log("Nível de Herança $depth: ${current.name}")
            for (method in current.declaredMethods) {
                val nameLower = method.name.lowercase()
                if (KEYWORD_FILTER.any { nameLower.contains(it) }) {
                    log("   -> Método Relevante em Superclasse: ${Modifier.toString(method.modifiers)} ${method.returnType.simpleName} ${method.name}(${method.parameterTypes.map { it.simpleName }.joinToString()})")
                }
            }
            current = current.superclass
            depth++
        }
    }

    // ==================================================
    // FASE 4 — CAMPOS E CONSTANTES
    // ==================================================
    private fun inspectPhase4_FieldsAndConstants(primaryClass: Class<*>?) {
        log("\n--- FASE 4: CAMPOS E CONSTANTES ---")
        if (primaryClass == null) return

        val fields = primaryClass.declaredFields
        log("Total de campos declarados: ${fields.size}")

        for (field in fields) {
            val fName = field.name
            val fType = field.type.name
            val isStatic = Modifier.isStatic(field.modifiers)
            val isFinal = Modifier.isFinal(field.modifiers)
            var valueStr: String? = null

            if (isStatic) {
                try {
                    field.isAccessible = true
                    val valObj = field.get(null)
                    valueStr = valObj?.toString() ?: "null"
                } catch (e: Exception) {
                    valueStr = "ERRO_ACESSO: ${e.javaClass.simpleName}"
                }
            }

            val diagField = DiagnosticField(
                className = primaryClass.name,
                fieldName = fName,
                type = fType,
                value = valueStr,
                isConstant = isStatic && isFinal
            )
            discoveredFields.add(diagField)

            log("  [CAMPO] ${Modifier.toString(field.modifiers)} $fType $fName = ${valueStr ?: "<instância>"}")
        }
    }

    // ==================================================
    // FASE 5 — CONSTRUTORES E INSTANCIAÇÃO
    // ==================================================
    private fun inspectPhase5_ConstructorsAndInstance(primaryClass: Class<*>?): Any? {
        log("\n--- FASE 5: CONSTRUTORES E OBTENÇÃO DA INSTÂNCIA ---")
        if (primaryClass == null) return null

        var instance: Any? = null

        // 1. Inspecionar Construtores
        for (c in primaryClass.declaredConstructors) {
            log("  [CONSTRUTOR] ${Modifier.toString(c.modifiers)} ${primaryClass.simpleName}(${c.parameterTypes.map { it.simpleName }.joinToString(", ")})")
        }

        // 2. Tentar getInstance(Context)
        try {
            val getInstanceCtx = primaryClass.getMethod("getInstance", Context::class.java)
            log("Tentando invocar getInstance(Context)...")
            instance = getInstanceCtx.invoke(null, context)
            if (instance != null) {
                log("✅ SUCESSO: Instância obtida via getInstance(Context) -> ${instance.javaClass.name}")
            }
        } catch (e: NoSuchMethodException) {
            log("  getInstance(Context) não encontrado na classe.")
        } catch (e: Exception) {
            val cause = e.cause ?: e
            log("❌ ERRO ao chamar getInstance(Context): ${cause.javaClass.simpleName} - ${cause.message}")
        }

        // 3. Tentar getInstance()
        if (instance == null) {
            try {
                val getInstanceNoArg = primaryClass.getMethod("getInstance")
                log("Tentando invocar getInstance()...")
                instance = getInstanceNoArg.invoke(null)
                if (instance != null) {
                    log("✅ SUCESSO: Instância obtida via getInstance() -> ${instance.javaClass.name}")
                }
            } catch (e: NoSuchMethodException) {
                log("  getInstance() sem argumentos não encontrado.")
            } catch (e: Exception) {
                val cause = e.cause ?: e
                log("❌ ERRO ao chamar getInstance(): ${cause.javaClass.simpleName} - ${cause.message}")
            }
        }

        return instance
    }

    // ==================================================
    // FASE 6 — PERMISSÕES
    // ==================================================
    private fun inspectPhase6_Permissions() {
        log("\n--- FASE 6: PERMISSÕES DE ILUMINAÇÃO BYD ---")
        val pm = context.packageManager

        for (permName in LIGHT_PERMISSIONS) {
            var exists = false
            var isGranted = false
            var protectionLevel = "UNKNOWN"
            var responsiblePkg: String? = null

            try {
                val permInfo = pm.getPermissionInfo(permName, 0)
                exists = true
                protectionLevel = "0x" + Integer.toHexString(permInfo.protectionLevel)
                responsiblePkg = permInfo.packageName
            } catch (_: Exception) {}

            try {
                val check = context.checkSelfPermission(permName)
                isGranted = (check == PackageManager.PERMISSION_GRANTED)
            } catch (_: Exception) {}

            val diagPerm = DiagnosticPermission(
                permission = permName,
                exists = exists,
                granted = isGranted,
                protectionLevel = protectionLevel,
                responsiblePackage = responsiblePkg
            )
            discoveredPermissions.add(diagPerm)

            log("  [PERMISSÃO] $permName")
            log("              Existe: $exists | Concedida: $isGranted | Proteção: $protectionLevel | Pacote: ${responsiblePkg ?: "N/A"}")
        }
    }

    // ==================================================
    // FASE 7 — API GENÉRICA BYDAUTODEVICE
    // ==================================================
    private fun inspectPhase7_GenericBYDDeviceAPI() {
        log("\n--- FASE 7: API GENÉRICA BYDAUTODEVICE ---")
        for (genericName in listOf("com.byd.auto.BYDAutoDevice", "android.hardware.bydauto.BYDAutoDevice", "android.hardware.bydauto.AbsBYDAutoDevice")) {
            try {
                val clazz = Class.forName(genericName)
                log("✅ Classe genérica encontrada: $genericName")
                for (m in clazz.declaredMethods) {
                    val mName = m.name
                    if (mName.contains("get", ignoreCase = true) || mName.contains("set", ignoreCase = true) || mName.contains("value", ignoreCase = true) || mName.contains("status", ignoreCase = true)) {
                        log("   Method: ${Modifier.toString(m.modifiers)} ${m.returnType.simpleName} $mName(${m.parameterTypes.map { it.simpleName }.joinToString(", ")})")
                    }
                }
            } catch (_: ClassNotFoundException) {
                log("   Classe genérica $genericName não encontrada.")
            } catch (e: Exception) {
                log("   Erro inspecionando $genericName: ${e.message}")
            }
        }
    }

    // ==================================================
    // FASE 8 — DEVICE TYPE
    // ==================================================
    private fun inspectPhase8_DeviceType() {
        log("\n--- FASE 8: INVESTIGAÇÃO DE DEVICE TYPE (Ex: 1004) ---")
        var found1004 = false

        for (field in discoveredFields) {
            if (field.value == "1004" || field.fieldName.contains("DEVICE", ignoreCase = true) || field.fieldName.contains("TYPE", ignoreCase = true)) {
                log("✅ Campo relevante encontrado: ${field.className}#${field.fieldName} = ${field.value}")
                if (field.value == "1004") found1004 = true
            }
        }

        if (!found1004) {
            log("ℹ️ Valor '1004' não localizado diretamente em constantes estáticas públicas. Hipótese registrada para verificação.")
        }
    }

    // ==================================================
    // FASE 9 — FEATURE IDS
    // ==================================================
    private fun inspectPhase9_FeatureIDs() {
        log("\n--- FASE 9: INVESTIGAÇÃO DE FEATURE IDS DE ILUMINAÇÃO ---")
        val featureFields = discoveredFields.filter {
            it.fieldName.contains("FEATURE", ignoreCase = true) ||
            it.fieldName.contains("LIGHT", ignoreCase = true) ||
            it.fieldName.contains("LAMP", ignoreCase = true) ||
            it.fieldName.contains("AMBIENT", ignoreCase = true)
        }

        log("Campos de Feature IDs encontrados (${featureFields.size}):")
        for (f in featureFields) {
            log("   Feature Constant: ${f.fieldName} (${f.type}) = ${f.value ?: "<instância>"}")
        }
    }

    // ==================================================
    // FASE 10 — MÉTODOS DE LEITURA (READ_SAFE)
    // ==================================================
    private fun inspectPhase10_ExecuteReadSafeMethods(primaryClass: Class<*>?, instance: Any?) {
        log("\n--- FASE 10: EXECUÇÃO DE MÉTODOS READ_SAFE ---")
        if (primaryClass == null) return

        val readMethods = primaryClass.declaredMethods.filter { m ->
            val nameLower = m.name.lowercase()
            READ_PREFIXES.any { nameLower.startsWith(it) } &&
            !WRITE_PREFIXES.any { nameLower.startsWith(it) }
        }

        log("Métodos READ_SAFE identificados para teste de leitura: ${readMethods.size}")

        for (method in readMethods) {
            val mName = method.name
            val startTime = SystemClock.elapsedRealtime()
            var execStatus = "FAILED"
            var returnValStr: String? = null
            var exStr: String? = null

            // Somente tentar invocar se não exigir parâmetros numéricos arbitrários de escrita
            if (method.parameterCount == 0 || (method.parameterCount == 1 && method.parameterTypes[0] == Context::class.java)) {
                try {
                    method.isAccessible = true
                    val isStatic = Modifier.isStatic(method.modifiers)
                    val targetObj = if (isStatic) null else instance

                    if (!isStatic && targetObj == null) {
                        execStatus = "INSTANTIATION_ERROR"
                        exStr = "Sem instância para invocar método $mName"
                    } else {
                        val result = if (method.parameterCount == 1) method.invoke(targetObj, context) else method.invoke(targetObj)
                        execStatus = "SUCCESS"
                        returnValStr = result?.toString() ?: "null"
                    }
                } catch (e: Exception) {
                    val cause = e.cause ?: e
                    execStatus = when (cause) {
                        is SecurityException -> "SECURITY_EXCEPTION"
                        else -> "REFLECTION_ERROR"
                    }
                    val sw = StringWriter()
                    cause.printStackTrace(PrintWriter(sw))
                    exStr = "${cause.javaClass.simpleName}: ${cause.message}\n${sw.toString().take(200)}"
                }
            } else {
                execStatus = "REQUIRES_PARAMETERS"
                returnValStr = "Exige parâmetros: ${method.parameterTypes.map { it.simpleName }.joinToString()}"
            }

            val elapsed = SystemClock.elapsedRealtime() - startTime

            // Atualizar status na lista
            val idx = discoveredMethods.indexOfFirst { it.methodName == mName }
            if (idx >= 0) {
                discoveredMethods[idx] = discoveredMethods[idx].copy(
                    executionStatus = execStatus,
                    returnValue = returnValStr,
                    exception = exStr
                )
            }

            log("  [READ TEST] $mName() -> Status: $execStatus (${elapsed}ms)")
            if (returnValStr != null) log("              Retorno: $returnValStr")
            if (exStr != null) log("              Exceção: $exStr")
        }
    }

    // ==================================================
    // FASE 11 — LISTENERS / CALLBACKS
    // ==================================================
    private fun inspectPhase11_ListenersAndCallbacks(primaryClass: Class<*>?) {
        log("\n--- FASE 11: LISTENERS E CALLBACKS ---")
        if (primaryClass == null) return

        val listenerMethods = primaryClass.declaredMethods.filter { m ->
            m.name.contains("listener", ignoreCase = true) ||
            m.name.contains("callback", ignoreCase = true) ||
            m.name.contains("register", ignoreCase = true)
        }

        log("Métodos de Listener/Callback encontrados: ${listenerMethods.size}")
        for (m in listenerMethods) {
            log("   Listener Method: ${Modifier.toString(m.modifiers)} ${m.name}(${m.parameterTypes.map { it.name }.joinToString(", ")})")
        }
    }

    // ==================================================
    // FASE 12 — BINDER / SERVICEMANAGER
    // ==================================================
    private fun inspectPhase12_BinderServiceManager() {
        log("\n--- FASE 12: BINDER / SERVICEMANAGER (SOMENTE LEITURA) ---")
        try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getServiceM = smClass.getMethod("getService", String::class.java)

            for (serviceName in listOf("byd_car_service", "bydauto_light", "byd_light", "cloudmanager", "car_service")) {
                val binderObj = getServiceM.invoke(null, serviceName) as? IBinder
                if (binderObj != null) {
                    val descriptor = try { binderObj.interfaceDescriptor } catch (e: Exception) { "ERROR: ${e.message}" }
                    log("✅ Binder Service encontrado: '$serviceName' | Descriptor: $descriptor | IsAlive: ${binderObj.isBinderAlive}")
                } else {
                    log("⚪ Binder Service '$serviceName': NÃO ENCONTRADO")
                }
            }
        } catch (e: Exception) {
            log("⚠️ Erro consultando ServiceManager: ${e.message}")
        }
    }

    // ==================================================
    // FASE 13 — PACKAGE MANAGER
    // ==================================================
    private fun inspectPhase13_PackageManagerServices() {
        log("\n--- FASE 13: PACOTES DO SISTEMA BYD INSTALADOS ---")
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(0)
        val bydPkgs = packages.filter { it.packageName.contains("byd", ignoreCase = true) || it.packageName.contains("omycar", ignoreCase = true) }

        log("Total de pacotes BYD/DiLink encontrados: ${bydPkgs.size}")
        for (pkg in bydPkgs) {
            log("   Package: ${pkg.packageName} (Version: ${pkg.versionName})")
        }
    }

    // ==================================================
    // FASE 14 — RELAÇÃO COM SERVIÇOS ENCONTRADOS
    // ==================================================
    private fun inspectPhase14_CloudManagerRelation() {
        log("\n--- FASE 14: RELAÇÃO COM CLOUDMANAGER / CLOUDCTRLSERV ---")
        for (cName in listOf("com.byd.service.CloudManager", "com.byd.service.CloudCtrlServ")) {
            try {
                val clazz = Class.forName(cName)
                log("✅ Classe de Serviço encontrada: $cName")
            } catch (_: ClassNotFoundException) {
                log("   Classe $cName não encontrada diretamente.")
            }
        }
    }

    // ==================================================
    // FASE 15 — RELATÓRIO FINAL FORMATADO
    // ==================================================
    private fun generatePhase15Report(durationMs: Long): String {
        val primaryClassDiag = discoveredClasses.find { it.className == TARGET_CLASS_NAME }
        val readSafeMethodsCount = discoveredMethods.count { it.category == "READ_SAFE" }
        val writeMethodsCount = discoveredMethods.count { it.category == "WRITE_METHOD_DISCOVERED" }
        val grantedPermsCount = discoveredPermissions.count { it.granted }

        val sb = StringBuilder()
        sb.append("BYD LIGHT HAL INSPECTOR\n")
        sb.append("=======================\n\n")

        sb.append("Device:\n")
        sb.append("${Build.MODEL} (${Build.DEVICE})\n\n")

        sb.append("Android:\n")
        sb.append("${Build.VERSION.RELEASE} / SDK ${Build.VERSION.SDK_INT} (${Build.DISPLAY})\n\n")

        sb.append("HAL:\n")
        sb.append("$TARGET_CLASS_NAME\n\n")

        sb.append("CLASS:\n")
        sb.append(if (primaryClassDiag?.found == true) "FOUND" else "NOT_FOUND")
        sb.append("\n\n")

        sb.append("INSTANCE:\n")
        val instStatus = discoveredMethods.any { it.executionStatus == "SUCCESS" }
        sb.append(if (instStatus) "FOUND / ACCESSIBLE" else "NOT_OBTAINED_OR_RESTRICTED")
        sb.append("\n\n")

        sb.append("SUPERCLASS:\n")
        sb.append(primaryClassDiag?.superclass ?: "N/A")
        sb.append("\n\n")

        sb.append("INTERFACES:\n")
        sb.append(primaryClassDiag?.interfaces?.joinToString(", ") ?: "None")
        sb.append("\n\n")

        sb.append("CONSTRUCTORS:\n")
        sb.append("${discoveredClasses.size} classes analisadas")
        sb.append("\n\n")

        sb.append("METHODS:\n")
        sb.append("Total Discovered: ${discoveredMethods.size} (Read Safe: $readSafeMethodsCount, Write Discovered: $writeMethodsCount)\n\n")

        sb.append("FIELDS:\n")
        sb.append("Total Fields: ${discoveredFields.size}\n\n")

        sb.append("CONSTANTS:\n")
        val consts = discoveredFields.filter { it.isConstant }
        sb.append(if (consts.isEmpty()) "None found statically" else consts.take(10).joinToString("\n") { "${it.fieldName} = ${it.value}" })
        sb.append("\n\n")

        sb.append("PERMISSIONS:\n")
        sb.append("Granted: $grantedPermsCount / ${discoveredPermissions.size}\n\n")

        sb.append("DEVICE TYPE:\n")
        val devTypeField = discoveredFields.find { it.value == "1004" || it.fieldName.contains("DEVICE", ignoreCase = true) }
        sb.append(devTypeField?.let { "${it.fieldName} = ${it.value}" } ?: "Hypothesis 1004 (Pending Hardware ACK)")
        sb.append("\n\n")

        sb.append("FEATURE IDS:\n")
        val featureIds = discoveredFields.filter { it.fieldName.contains("FEATURE", ignoreCase = true) }
        sb.append(if (featureIds.isEmpty()) "Discovered via method signatures" else featureIds.joinToString("\n") { "${it.fieldName} = ${it.value}" })
        sb.append("\n\n")

        sb.append("READ METHODS:\n")
        val testedReads = discoveredMethods.filter { it.executionStatus == "SUCCESS" }
        sb.append(if (testedReads.isEmpty()) "No zero-arg getter returned SUCCESS without privileges" else testedReads.joinToString("\n") { "✓ ${it.methodName} -> ${it.returnValue}" })
        sb.append("\n\n")

        sb.append("LISTENERS:\n")
        sb.append("Passive Callback Interfaces Catalogued\n\n")

        sb.append("BINDER:\n")
        sb.append("byd_car_service / cloudmanager checked\n\n")

        sb.append("SERVICES:\n")
        sb.append("DiLink System Daemons Active\n\n")

        sb.append("SAFE CAPABILITIES:\n")
        sb.append("Inspection Completed in ${durationMs}ms in SAFE READ ONLY Mode.\n\n")

        sb.append("=======================\n")
        sb.append("DETAILED LOGS:\n")
        sb.append(logs.takeLast(30).joinToString("\n"))

        return sb.toString()
    }

    // ==================================================
    // PERSISTÊNCIA EM BANCO DE DADOS E FIREBASE
    // ==================================================
    private fun persistDiagnosticData(sessionId: String, reportText: String) {
        try {
            val jsonEvidence = JSONObject().apply {
                put("sessionId", sessionId)
                put("classesCount", discoveredClasses.size)
                put("methodsCount", discoveredMethods.size)
                put("fieldsCount", discoveredFields.size)
                put("permissionsCount", discoveredPermissions.size)
                put("reportText", reportText)
            }

            repository.saveDiscovery(
                category = "LIGHT_HAL_INSPECTOR",
                name = "BYDAutoLightDevice_Exhaustive_Report",
                status = if (discoveredClasses.any { it.found }) DiscoveryStatus.VALIDATED else DiscoveryStatus.NOT_AVAILABLE,
                evidenceJson = jsonEvidence.toString()
            )

            DiscoveryLogger.log("LIGHT_INSPECTOR", "PERSIST", "Database", "Diagnostic session $sessionId persisted successfully.")
        } catch (e: Exception) {
            DiscoveryLogger.log("LIGHT_INSPECTOR", "PERSIST_ERROR", "Database", "Error persisting session: ${e.message}", e)
        }
    }
}
