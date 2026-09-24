package com.byd.carcontrol.discovery

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import com.byd.carcontrol.data.BinderServiceEntity
import com.byd.carcontrol.data.PermissionEntity
import com.byd.carcontrol.data.TestResultEntity
import com.byd.carcontrol.repository.DiscoveryRepository
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Method
import java.lang.reflect.Modifier

data class AutoServiceTestResult(
    val timestamp: Long = System.currentTimeMillis(),
    val transport: String,
    val deviceType: Int,
    val featureIdDecimal: Long,
    val featureIdHex: String,
    val operation: String, // "GET_INT", "SET_INT", "PROBE"
    val transactionCode: Int,
    val requestPayload: String,
    val responsePayload: String?,
    val returnCode: Int,
    val returnedValue: String?,
    val readBefore: String?,
    val readAfter: String?,
    val exception: String? = null,
    val durationMs: Long,
    var physicalConfirmation: String = "PENDING" // "PHYSICAL_EFFECT_CONFIRMED", "COMMAND_REJECTED_OR_NO_EFFECT", "PENDING"
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("timestamp", timestamp)
        obj.put("transport", transport)
        obj.put("deviceType", deviceType)
        obj.put("featureIdDecimal", featureIdDecimal)
        obj.put("featureIdHex", featureIdHex)
        obj.put("operation", operation)
        obj.put("transactionCode", transactionCode)
        obj.put("request", requestPayload)
        obj.put("response", responsePayload ?: "NONE")
        obj.put("returnCode", returnCode)
        obj.put("returnedValue", returnedValue ?: "N/A")
        obj.put("readBefore", readBefore ?: "N/A")
        obj.put("readAfter", readAfter ?: "N/A")
        obj.put("exception", exception ?: "NONE")
        obj.put("durationMs", durationMs)
        obj.put("physicalConfirmation", physicalConfirmation)
        return obj
    }
}

data class AutoServiceInspectionReport(
    val autoserviceFound: Boolean,
    val autoserviceDescriptor: String?,
    val interfaceDescriptor: String?,
    val bydAutoManagerFound: Boolean,
    val bydAutoManagerClassName: String?,
    val discoveredClasses: List<String>,
    val discoveredPermissions: List<String>,
    val device1023Found: Boolean,
    val logs: List<String>
)

/**
 * Módulo de Investigação Técnica do autoservice e Iluminação Interna (Device 1023).
 * Ultra-defensivo: Protegido por trava de segurança (Arming) e captura de Throwable.
 */
class BYDAutoServiceInspector(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    companion object {
        private const val TAG = "BYDAutoServiceInspector"
        const val DEVICE_INTERIOR_LIGHT = 1023
        const val DEVICE_READING_EXTERIOR_LIGHT = 1004

        const val FID_INTERIOR_LIGHT_STATE = 1330643002L // 0x4F50003A
        const val FID_ATMOSPHERE_BRIGHTNESS = 1069547536L // 0x3FC00010

        const val FID_INTERIOR_LIGHT_STATE_HEX = "0x4F50003A"
        const val FID_ATMOSPHERE_BRIGHTNESS_HEX = "0x3FC00010"

        const val TRANSACTION_GET_INT = 5
        const val TRANSACTION_SET_INT = 6
        const val TRANSACTION_GET_FLOAT = 7
        const val TRANSACTION_GET_BUFFER = 8

        const val INTERFACE_DESCRIPTOR = "android.gui.BYDAutoServer"
    }

    var isArmedForWrite: Boolean = false
        private set

    private val inspectionLogs = mutableListOf<String>()
    private var lastTestResult: AutoServiceTestResult? = null

    fun armWriteTests(enable: Boolean) {
        isArmedForWrite = enable
        val stateStr = if (enable) "⚠️ ARMED (Escrita Liberada)" else "🔒 DISARMED (Somente Leitura)"
        log("ARM_STATE", stateStr)
    }

    fun getLastTestResult(): AutoServiceTestResult? = lastTestResult

    private fun log(tag: String, message: String) {
        val entry = "[$tag] $message"
        Log.i(TAG, entry)
        inspectionLogs.add(entry)
    }

    // =========================================================================
    // FASE 2, 3, 4: INSPECT API (AUTOSERVICE, BYDAUTO MANAGER, PERMISSÕES)
    // =========================================================================
    fun inspectApi(): AutoServiceInspectionReport {
        inspectionLogs.clear()
        log("INSPECT_API", "Iniciando inspeção técnica da API do BYD Dolphin Plus...")

        var autoserviceFound = false
        var autoserviceDescriptor: String? = null
        var interfaceDescriptor: String? = null

        // FASE 2: INSPEÇÃO DE AUTOSERVICE BINDER
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            val binderObj = getServiceMethod.invoke(null, "autoservice") as? IBinder

            if (binderObj != null) {
                autoserviceFound = true
                val isAlive = binderObj.isBinderAlive
                autoserviceDescriptor = try { binderObj.interfaceDescriptor } catch (t: Throwable) { "Unknown" }
                interfaceDescriptor = autoserviceDescriptor

                log("AUTOSERVICE", "✅ Serviço 'autoservice' encontrado! IBinder Ativo: $isAlive | Descritor: $autoserviceDescriptor")

                repository.saveBinder(
                    BinderServiceEntity(
                        name = "autoservice",
                        descriptor = autoserviceDescriptor,
                        isAlive = isAlive,
                        status = DiscoveryStatus.AVAILABLE
                    )
                )
            } else {
                log("AUTOSERVICE", "❌ Serviço 'autoservice' retornou NULL no ServiceManager.")
            }
        } catch (t: Throwable) {
            log("AUTOSERVICE_ERR", "Falha ao consultar 'autoservice': ${t.javaClass.simpleName} - ${t.message}")
        }

        // FASE 3: INSPEÇÃO DE CLASSES E MÉTODOS BYDAUTO
        val targetClasses = listOf(
            "com.byd.auto.BYDAutoManager",
            "android.hardware.bydauto.BYDAutoManager",
            "com.byd.auto.BYDAutoDevice",
            "com.byd.auto.AbsBYDAutoDevice",
            "com.byd.auto.IBYDAutoDevice",
            "com.byd.auto.BYDAutoConstants",
            "com.byd.auto.BYDAutoServer",
            "com.byd.auto.BYDAutoService",
            "com.byd.permission.BydPermissionContext"
        )

        val discoveredClassesList = mutableListOf<String>()
        var bydAutoManagerFound = false
        var bydAutoManagerClassName: String? = null

        for (cName in targetClasses) {
            try {
                val clazz = Class.forName(cName)
                discoveredClassesList.add(cName)
                log("CLASS_DISCOVERY", "✅ Classe encontrada: $cName")

                if (cName.contains("BYDAutoManager")) {
                    bydAutoManagerFound = true
                    bydAutoManagerClassName = cName
                }

                // Inspecionar métodos conhecidos
                val targetMethods = listOf("getInt", "setInt", "getFloat", "setFloat", "getBuffer", "get", "set", "getStatus", "setStatus")
                for (m in clazz.declaredMethods) {
                    if (targetMethods.contains(m.name) || m.name.lowercase().contains("light")) {
                        val params = m.parameterTypes.joinToString(",") { it.simpleName }
                        log("METHOD_DISCOVERY", "   -> ${clazz.simpleName}#${m.name}($params): ${m.returnType.simpleName}")
                    }
                }
            } catch (_: ClassNotFoundException) {
                // Classe não encontrada nesta ROM
            } catch (t: Throwable) {
                log("CLASS_ERR", "Erro ao inspecionar $cName: ${t.message}")
            }
        }

        // FASE 4: INSPEÇÃO DE PERMISSÕES BYDAUTO
        val targetPermissions = listOf(
            "android.permission.BYDAUTO_LIGHT_GET",
            "android.permission.BYDAUTO_LIGHT_SET",
            "android.permission.BYDAUTO_LIGHT_COMMON",
            "android.permission.BYDAUTO_CAR_CONTROL",
            "com.byd.permission.BYD_LIGHT_CONTROL",
            "android.permission.BYD_AUTO_CONTROL"
        )

        val discoveredPermissionsList = mutableListOf<String>()
        val pm = context.packageManager

        for (pName in targetPermissions) {
            try {
                val pInfo = pm.getPermissionInfo(pName, 0)
                val isGranted = pm.checkPermission(pName, context.packageName) == PackageManager.PERMISSION_GRANTED
                discoveredPermissionsList.add("$pName (Granted=$isGranted, Level=${pInfo.protectionLevel})")
                log("PERMISSION_DISCOVERY", "✅ Permissão BYD encontrada: $pName | Concedida: $isGranted")

                repository.savePermission(
                    PermissionEntity(
                        permissionName = pName,
                        exists = true,
                        protectionLevel = pInfo.protectionLevel.toString(),
                        isGranted = isGranted,
                        status = if (isGranted) DiscoveryStatus.AVAILABLE else DiscoveryStatus.PERMISSION_DENIED
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Permissão não declarada no manifesto/sistema
            } catch (t: Throwable) {
                log("PERMISSION_ERR", "Erro ao checar $pName: ${t.message}")
            }
        }

        // FASE 5: TESTE PRELIMINAR DE LEITURA DEVICE 1023
        val device1023Test = readDevice1023(DEVICE_INTERIOR_LIGHT, FID_INTERIOR_LIGHT_STATE)
        val device1023Found = device1023Test.returnCode == 0 || device1023Test.returnedValue != null

        return AutoServiceInspectionReport(
            autoserviceFound = autoserviceFound,
            autoserviceDescriptor = autoserviceDescriptor,
            interfaceDescriptor = interfaceDescriptor ?: INTERFACE_DESCRIPTOR,
            bydAutoManagerFound = bydAutoManagerFound,
            bydAutoManagerClassName = bydAutoManagerClassName,
            discoveredClasses = discoveredClassesList,
            discoveredPermissions = discoveredPermissionsList,
            device1023Found = device1023Found,
            logs = inspectionLogs
        )
    }

    // =========================================================================
    // FASE 6 & 7: SOMENTE LEITURA (GET_INT - DEVICE 1023)
    // =========================================================================
    fun readDevice1023(device: Int, featureId: Long): AutoServiceTestResult {
        val startTime = SystemClock.elapsedRealtime()
        val featureHex = "0x" + java.lang.Long.toHexString(featureId).uppercase()
        val reqPayload = "device=$device, featureId=$featureId ($featureHex)"

        log("GET_INT_REQ", "Executando GET (somente leitura) -> Device=$device, FeatureId=$featureId ($featureHex)")

        var binderObj: IBinder? = null
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            binderObj = getServiceMethod.invoke(null, "autoservice") as? IBinder
        } catch (t: Throwable) {
            log("GET_INT_ERR", "Falha ao obter Binder autoservice: ${t.message}")
        }

        if (binderObj == null) {
            val res = AutoServiceTestResult(
                transport = "autoservice_Binder",
                deviceType = device,
                featureIdDecimal = featureId,
                featureIdHex = featureHex,
                operation = "GET_INT",
                transactionCode = TRANSACTION_GET_INT,
                requestPayload = reqPayload,
                responsePayload = null,
                returnCode = -1,
                returnedValue = null,
                readBefore = null,
                readAfter = null,
                exception = "ServiceManager 'autoservice' binder not found or null",
                durationMs = SystemClock.elapsedRealtime() - startTime
            )
            lastTestResult = res
            return res
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        return try {
            data.writeInterfaceToken(INTERFACE_DESCRIPTOR)
            data.writeInt(device)
            data.writeInt(featureId.toInt())

            val statusOk = binderObj.transact(TRANSACTION_GET_INT, data, reply, 0)
            val duration = SystemClock.elapsedRealtime() - startTime

            if (statusOk) {
                reply.readException()
                val retCode = reply.readInt()
                val valueInt = reply.readInt()
                val respStr = "statusOk=true, retCode=$retCode, value=$valueInt"

                log("GET_INT_RESP", "✅ Sucesso no Binder transact 5! Retorno=$valueInt (retCode=$retCode, ${duration}ms)")

                val res = AutoServiceTestResult(
                    transport = "autoservice_Binder",
                    deviceType = device,
                    featureIdDecimal = featureId,
                    featureIdHex = featureHex,
                    operation = "GET_INT",
                    transactionCode = TRANSACTION_GET_INT,
                    requestPayload = reqPayload,
                    responsePayload = respStr,
                    returnCode = retCode,
                    returnedValue = valueInt.toString(),
                    readBefore = valueInt.toString(),
                    readAfter = valueInt.toString(),
                    exception = null,
                    durationMs = duration
                )

                repository.saveTestResult(
                    TestResultEntity(
                        testName = "AUTOSERVICE_GET_${device}_$featureHex",
                        transportType = "autoservice_Binder",
                        status = DiscoveryStatus.AVAILABLE,
                        latencyMs = duration,
                        payloadSent = reqPayload,
                        responseReceived = respStr,
                        verified = true
                    )
                )

                lastTestResult = res
                res
            } else {
                log("GET_INT_FAIL", "❌ Binder transact retornou FALSE (${duration}ms)")
                val res = AutoServiceTestResult(
                    transport = "autoservice_Binder",
                    deviceType = device,
                    featureIdDecimal = featureId,
                    featureIdHex = featureHex,
                    operation = "GET_INT",
                    transactionCode = TRANSACTION_GET_INT,
                    requestPayload = reqPayload,
                    responsePayload = "transact returned false",
                    returnCode = -2,
                    returnedValue = null,
                    readBefore = null,
                    readAfter = null,
                    exception = "Binder transact(5) returned false",
                    durationMs = duration
                )
                lastTestResult = res
                res
            }
        } catch (t: Throwable) {
            val duration = SystemClock.elapsedRealtime() - startTime
            log("GET_INT_EXC", "⚠️ Exceção no Binder transact: ${t.javaClass.simpleName} - ${t.message}")

            val res = AutoServiceTestResult(
                transport = "autoservice_Binder",
                deviceType = device,
                featureIdDecimal = featureId,
                featureIdHex = featureHex,
                operation = "GET_INT",
                transactionCode = TRANSACTION_GET_INT,
                requestPayload = reqPayload,
                responsePayload = null,
                returnCode = -3,
                returnedValue = null,
                readBefore = null,
                readAfter = null,
                exception = "${t.javaClass.simpleName}: ${t.message}",
                durationMs = duration
            )
            lastTestResult = res
            res
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    // =========================================================================
    // FASE 8, 9, 10: ESCRITA CONTROLADA E PROTEGIDA (SET_INT - DEVICE 1023)
    // =========================================================================
    fun writeDevice1023(device: Int, featureId: Long, value: Int): AutoServiceTestResult {
        if (!isArmedForWrite) {
            val errStr = "❌ OPERAÇÃO DE ESCRITA BLOQUEADA! O teste de escrita não foi armado pelo usuário (FASE 8)."
            log("SET_INT_BLOCKED", errStr)
            val featureHex = "0x" + java.lang.Long.toHexString(featureId).uppercase()
            val res = AutoServiceTestResult(
                transport = "autoservice_Binder",
                deviceType = device,
                featureIdDecimal = featureId,
                featureIdHex = featureHex,
                operation = "SET_INT",
                transactionCode = TRANSACTION_SET_INT,
                requestPayload = "device=$device, fid=$featureId, val=$value",
                responsePayload = "WRITE_TEST_DISARMED",
                returnCode = -99,
                returnedValue = null,
                readBefore = null,
                readAfter = null,
                exception = "TEST_NOT_ARMED",
                durationMs = 0
            )
            lastTestResult = res
            return res
        }

        val startTime = SystemClock.elapsedRealtime()
        val featureHex = "0x" + java.lang.Long.toHexString(featureId).uppercase()
        val reqPayload = "device=$device, featureId=$featureId ($featureHex), value=$value"

        log("SET_INT_START", "=== INICIANDO TESTE CONTROLADO DE ESCRITA ===")
        log("SET_INT_START", "Alvo: Device=$device | FeatureId=$featureHex | Novo Valor=$value")

        // 1. READ BEFORE
        val readBeforeRes = readDevice1023(device, featureId)
        val readBeforeVal = readBeforeRes.returnedValue ?: "UNKNOWN"
        log("SET_INT_READ_BEFORE", "Valor Lido Antes (READ BEFORE): $readBeforeVal")

        // 2. SET INT VIA BINDER TRANSACT 6
        var binderObj: IBinder? = null
        try {
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)
            binderObj = getServiceMethod.invoke(null, "autoservice") as? IBinder
        } catch (t: Throwable) {
            log("SET_INT_ERR", "Falha ao obter Binder autoservice: ${t.message}")
        }

        if (binderObj == null) {
            val res = AutoServiceTestResult(
                transport = "autoservice_Binder",
                deviceType = device,
                featureIdDecimal = featureId,
                featureIdHex = featureHex,
                operation = "SET_INT",
                transactionCode = TRANSACTION_SET_INT,
                requestPayload = reqPayload,
                responsePayload = null,
                returnCode = -1,
                returnedValue = null,
                readBefore = readBeforeVal,
                readAfter = null,
                exception = "ServiceManager 'autoservice' binder null",
                durationMs = SystemClock.elapsedRealtime() - startTime
            )
            lastTestResult = res
            return res
        }

        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            data.writeInterfaceToken(INTERFACE_DESCRIPTOR)
            data.writeInt(device)
            data.writeInt(featureId.toInt())
            data.writeInt(value)

            val statusOk = binderObj.transact(TRANSACTION_SET_INT, data, reply, 0)

            if (statusOk) {
                reply.readException()
                val retCode = reply.readInt()
                log("SET_INT_TRANSACT", "✅ Binder transact 6 executado com retCode=$retCode")

                // 3. AGUARDAR MCU PROCESSAR (500ms)
                try { Thread.sleep(500L) } catch (_: InterruptedException) {}

                // 4. READ AFTER
                val readAfterRes = readDevice1023(device, featureId)
                val readAfterVal = readAfterRes.returnedValue ?: "UNKNOWN"
                val duration = SystemClock.elapsedRealtime() - startTime

                log("SET_INT_READ_AFTER", "Valor Lido Após (READ AFTER): $readAfterVal (${duration}ms)")

                val res = AutoServiceTestResult(
                    transport = "autoservice_Binder",
                    deviceType = device,
                    featureIdDecimal = featureId,
                    featureIdHex = featureHex,
                    operation = "SET_INT",
                    transactionCode = TRANSACTION_SET_INT,
                    requestPayload = reqPayload,
                    responsePayload = "statusOk=true, retCode=$retCode",
                    returnCode = retCode,
                    returnedValue = value.toString(),
                    readBefore = readBeforeVal,
                    readAfter = readAfterVal,
                    exception = null,
                    durationMs = duration
                )

                repository.saveTestResult(
                    TestResultEntity(
                        testName = "AUTOSERVICE_SET_${device}_${featureHex}_V$value",
                        transportType = "autoservice_Binder",
                        status = DiscoveryStatus.AVAILABLE,
                        latencyMs = duration,
                        payloadSent = reqPayload,
                        responseReceived = "retCode=$retCode | ReadBefore=$readBeforeVal | ReadAfter=$readAfterVal",
                        verified = true
                    )
                )

                lastTestResult = res
                return res
            } else {
                val duration = SystemClock.elapsedRealtime() - startTime
                log("SET_INT_FAIL", "❌ Binder transact 6 retornou FALSE (${duration}ms)")

                val res = AutoServiceTestResult(
                    transport = "autoservice_Binder",
                    deviceType = device,
                    featureIdDecimal = featureId,
                    featureIdHex = featureHex,
                    operation = "SET_INT",
                    transactionCode = TRANSACTION_SET_INT,
                    requestPayload = reqPayload,
                    responsePayload = "transact returned false",
                    returnCode = -2,
                    returnedValue = null,
                    readBefore = readBeforeVal,
                    readAfter = null,
                    exception = "Binder transact(6) returned false",
                    durationMs = duration
                )
                lastTestResult = res
                return res
            }
        } catch (t: Throwable) {
            val duration = SystemClock.elapsedRealtime() - startTime
            log("SET_INT_EXC", "⚠️ Exceção ao executar SET_INT: ${t.javaClass.simpleName} - ${t.message}")

            val res = AutoServiceTestResult(
                transport = "autoservice_Binder",
                deviceType = device,
                featureIdDecimal = featureId,
                featureIdHex = featureHex,
                operation = "SET_INT",
                transactionCode = TRANSACTION_SET_INT,
                requestPayload = reqPayload,
                responsePayload = null,
                returnCode = -3,
                returnedValue = null,
                readBefore = readBeforeVal,
                readAfter = null,
                exception = "${t.javaClass.simpleName}: ${t.message}",
                durationMs = duration
            )
            lastTestResult = res
            return res
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    // =========================================================================
    // FASE 11: CONFIRMAÇÃO FÍSICA DO USUÁRIO
    // =========================================================================
    fun setPhysicalConfirmation(confirmed: Boolean) {
        val last = lastTestResult
        if (last != null) {
            last.physicalConfirmation = if (confirmed) "PHYSICAL_EFFECT_CONFIRMED" else "COMMAND_REJECTED_OR_NO_EFFECT"
            val statusStr = if (confirmed) "🎉 CONFIRMADO! A luz mudou fisicamente no veículo." else "❌ NÃO MUDOU. A luz permaneceu inalterada."
            log("PHYSICAL_CONFIRMATION", statusStr)

            repository.saveDiscovery(
                category = "PHYSICAL_TEST",
                name = "DEVICE_${last.deviceType}_FID_${last.featureIdHex}_VAL_${last.returnedValue}",
                status = if (confirmed) DiscoveryStatus.AVAILABLE else DiscoveryStatus.EXECUTION_FAILED,
                evidenceJson = last.toJson().toString()
            )
        } else {
            log("PHYSICAL_CONFIRMATION", "Nenhum teste de escrita executado recentemente para confirmar.")
        }
    }
}
