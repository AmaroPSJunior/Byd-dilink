package com.byd.carcontrol.discovery

import android.content.Context
import android.os.SystemClock
import com.byd.carcontrol.data.TestResultEntity
import com.byd.carcontrol.repository.DiscoveryRepository
import java.lang.reflect.Method
import java.lang.reflect.Modifier

class SafeApiTester(
    private val context: Context,
    private val repository: DiscoveryRepository
) {

    fun testSafeReadMethod(className: String, methodName: String): TestResultEntity {
        val startTime = SystemClock.elapsedRealtime()
        var execution = "FAILED"
        var resultType: String? = null
        var resultVal: String? = null
        var exceptionType: String? = null
        var exceptionMsg: String? = null
        var stackTraceStr: String? = null
        var returnType = "unknown"

        val lowerMethod = methodName.lowercase()
        // Enforce safety check
        val isControl = listOf("set", "enable", "disable", "open", "close", "lock", "unlock", "turnon", "turnoff", "write", "control", "move").any { lowerMethod.startsWith(it) }

        if (isControl) {
            val duration = SystemClock.elapsedRealtime() - startTime
            val result = TestResultEntity(
                id = "$className#$methodName",
                className = className,
                methodName = methodName,
                parameters = "[]",
                returnType = "void",
                execution = "CONTROL_API_SKIPPED",
                resultType = "SAFETY_LOCK",
                result = "CONTROL API — NOT TESTED (Auto-execution prohibited for write methods)",
                durationMs = duration
            )
            repository.saveTestResult(result)
            DiscoveryLogger.log("SAFE_TEST", "SAFETY_SKIP", "$className#$methodName", "Skipped control method for safety")
            return result
        }

        try {
            val clazz = Class.forName(className)
            // Search method with no-arg or Context arg
            var targetMethod: Method? = null
            var passContext = false

            try {
                targetMethod = clazz.getMethod(methodName)
            } catch (_: NoSuchMethodException) {
                try {
                    targetMethod = clazz.getMethod(methodName, Context::class.java)
                    passContext = true
                } catch (_: NoSuchMethodException) {}
            }

            if (targetMethod == null) {
                exceptionType = "NoSuchMethodException"
                exceptionMsg = "Method $methodName not found on $className"
                DiscoveryLogger.log("SAFE_TEST", "METHOD_NOT_FOUND", "$className#$methodName", exceptionMsg)
            } else {
                returnType = targetMethod.returnType.name
                val isStatic = Modifier.isStatic(targetMethod.modifiers)

                // Obtain instance if not static
                var instance: Any? = null
                if (!isStatic) {
                    instance = try {
                        val getInstanceM = clazz.getMethod("getInstance")
                        getInstanceM.invoke(null)
                    } catch (_: Exception) {
                        try {
                            val getInstanceCtx = clazz.getMethod("getInstance", Context::class.java)
                            getInstanceCtx.invoke(null, context)
                        } catch (_: Exception) {
                            try {
                                clazz.getDeclaredConstructor().newInstance()
                            } catch (_: Exception) {
                                try {
                                    clazz.getDeclaredConstructor(Context::class.java).newInstance(context)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        }
                    }
                }

                if (!isStatic && instance == null) {
                    exceptionType = "InstantiationException"
                    exceptionMsg = "Could not obtain instance of $className to invoke $methodName"
                    DiscoveryLogger.log("SAFE_TEST", "INSTANCE_FAILED", "$className#$methodName", exceptionMsg)
                } else {
                    targetMethod.isAccessible = true
                    val res = if (passContext) {
                        targetMethod.invoke(instance, context)
                    } else {
                        targetMethod.invoke(instance)
                    }

                    execution = "SUCCESS"
                    resultType = res?.javaClass?.name ?: returnType
                    resultVal = res?.toString() ?: "null"

                    DiscoveryLogger.log(
                        category = "SAFE_TEST",
                        operation = "INVOKE_SUCCESS",
                        target = "$className#$methodName",
                        result = "Returned: $resultVal (Type: $resultType)"
                    )
                }
            }

        } catch (e: Exception) {
            val cause = e.cause ?: e
            execution = "FAILED"
            exceptionType = cause.javaClass.simpleName
            exceptionMsg = cause.message ?: cause.toString()
            stackTraceStr = cause.stackTrace.take(5).joinToString("\n")

            DiscoveryLogger.log(
                category = "SAFE_TEST",
                operation = "INVOKE_EXCEPTION",
                target = "$className#$methodName",
                result = "$exceptionType: $exceptionMsg",
                exception = cause
            )
        }

        val elapsed = SystemClock.elapsedRealtime() - startTime
        val testResult = TestResultEntity(
            id = "$className#$methodName",
            className = className,
            methodName = methodName,
            parameters = "[]",
            returnType = returnType,
            execution = execution,
            resultType = resultType,
            result = resultVal,
            exceptionType = exceptionType,
            exceptionMessage = exceptionMsg,
            stackTrace = stackTraceStr,
            durationMs = elapsed
        )

        repository.saveTestResult(testResult)
        return testResult
    }
}
