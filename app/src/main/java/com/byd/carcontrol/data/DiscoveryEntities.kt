package com.byd.carcontrol.data

import com.byd.carcontrol.discovery.ApiSafety
import com.byd.carcontrol.discovery.DiscoveryStatus

data class VehicleProfileEntity(
    val id: String,
    val deviceModel: String,
    val manufacturer: String,
    val brand: String,
    val product: String,
    val buildDisplay: String,
    val sdkInt: Int,
    val incremental: String,
    val securityPatch: String,
    val dilinkVersion: String,
    val timestamp: Long
)

data class BinderServiceEntity(
    val id: String = "",
    val name: String,
    val exists: Boolean,
    val descriptor: String? = null,
    val isAlive: Boolean = false,
    val status: DiscoveryStatus = DiscoveryStatus.DISCOVERED,
    val timestamp: Long = System.currentTimeMillis()
)

data class ContentProviderEntity(
    val id: String = "",
    val authority: String,
    val packageName: String,
    val name: String,
    val exported: Boolean,
    val readPermission: String? = null,
    val writePermission: String? = null,
    val classification: String, // SYSTEM, BYD, MAGICCORE, THIRD_PARTY, UNKNOWN
    val status: DiscoveryStatus = DiscoveryStatus.DISCOVERED,
    val timestamp: Long = System.currentTimeMillis()
)

data class PermissionEntity(
    val id: String = "",
    val permissionName: String,
    val exists: Boolean,
    val protectionLevel: String,
    val isGranted: Boolean,
    val status: DiscoveryStatus = DiscoveryStatus.DISCOVERED,
    val timestamp: Long = System.currentTimeMillis()
)

data class ApiClassEntity(
    val id: String = "",
    val className: String,
    val packageName: String,
    val exists: Boolean,
    val superclass: String? = null,
    val interfaces: List<String> = emptyList(),
    val status: DiscoveryStatus = DiscoveryStatus.DISCOVERED,
    val timestamp: Long = System.currentTimeMillis()
)

data class ApiMethodEntity(
    val id: String = "",
    val classId: String,
    val className: String,
    val methodName: String,
    val visibility: String,
    val isStatic: Boolean,
    val returnType: String,
    val parameterTypes: List<String>,
    val safety: ApiSafety,
    val status: DiscoveryStatus = DiscoveryStatus.DISCOVERED,
    val timestamp: Long = System.currentTimeMillis()
)

data class TestResultEntity(
    val id: String = "",
    val className: String,
    val methodName: String,
    val parameters: String = "[]",
    val returnType: String,
    val execution: String, // SUCCESS, FAILED, DENIED
    val resultType: String? = null,
    val result: String? = null,
    val exceptionType: String? = null,
    val exceptionMessage: String? = null,
    val stackTrace: String? = null,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class DiscoveryEntity(
    val id: String,
    val category: String,
    val name: String,
    val status: DiscoveryStatus,
    val evidenceJson: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class DiagnosticSession(
    val sessionId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val firmware: String,
    val androidVersion: String,
    val sdk: Int,
    val device: String,
    val appVersion: String
)

data class DiagnosticClass(
    val className: String,
    val found: Boolean,
    val superclass: String? = null,
    val interfaces: List<String> = emptyList(),
    val classLoader: String? = null,
    val packageName: String? = null,
    val modifiers: String? = null
)

data class DiagnosticMethod(
    val className: String,
    val methodName: String,
    val returnType: String,
    val parameters: List<String>,
    val modifiers: String,
    val declaringClass: String,
    val category: String, // READ_SAFE, WRITE_METHOD_DISCOVERED, UNKNOWN_SIDE_EFFECT
    val executionStatus: String, // NOT_TESTED, SUCCESS, FAILED, PERMISSION_DENIED, etc.
    val returnValue: String? = null,
    val exception: String? = null
)

data class DiagnosticField(
    val className: String,
    val fieldName: String,
    val type: String,
    val value: String? = null,
    val isConstant: Boolean = false
)

data class DiagnosticPermission(
    val permission: String,
    val exists: Boolean,
    val granted: Boolean,
    val protectionLevel: String,
    val responsiblePackage: String? = null,
    val uid: Int? = null
)
