package com.byd.carcontrol

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager

/** Read-only probe for the BYD safety-belt SDK getter. */
object BydSeatbeltReader {
    private const val DEVICE_CLASS = "android.hardware.bydauto.safetybelt.BYDAutoSafetyBeltDevice"

    data class Seat(val key: String, val label: String, val raw: Int)
    data class Snapshot(val seats: List<Seat>, val locked: Int, val unlocked: Int, val invalid: Int)

    fun readAll(context: Context): Snapshot {
        val deviceClass = Class.forName(DEVICE_CLASS)
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext)
            ?: error("BYDAutoSafetyBeltDevice.getInstance retornou null")
        fun constant(name: String): Int = deviceClass.getField(name).getInt(null)
        val getter = deviceClass.getMethod("getSafetyBeltStatus", Int::class.javaPrimitiveType)
        // Only the main/driver area was verified against live vehicle behavior.
        // Other area values appeared to change without corresponding belt activity.
        val seats = listOf("SAFETY_BELT_AREA_MAIN" to "Motorista").map { (key, label) ->
            val field = deviceClass.getField(key)
            val area = field.getInt(null)
            val raw = (getter.invoke(instance, area) as? Number)?.toInt()
                ?: error("getSafetyBeltStatus(${field.name}) não retornou um número")
            val normalized = when (raw) {
                constant("SAFETY_BELT_STATE_LOCK") -> 1
                constant("SAFETY_BELT_STATE_UNLOCK") -> 2
                else -> 0
            }
            Seat(key, label, normalized)
        }
        check(seats.isNotEmpty()) { "O SDK não publicou áreas de cinto conhecidas." }
        return Snapshot(seats, constant("SAFETY_BELT_STATE_LOCK"),
            constant("SAFETY_BELT_STATE_UNLOCK"), constant("SAFETY_BELT_STATE_INVALID"))
    }

    fun readRawStatuses(context: Context): Pair<List<Pair<Int, String>>, List<String>> {
        val deviceClass = Class.forName(DEVICE_CLASS)
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext)
            ?: error("BYDAutoSafetyBeltDevice.getInstance retornou null")
        val getter = deviceClass.getMethod("getSafetyBeltStatus", Int::class.javaPrimitiveType)
        val statuses = (1..5).map { area ->
            val value = try {
                (getter.invoke(instance, area) as? Number)?.toInt()?.toString() ?: "sem valor numérico"
            } catch (t: Throwable) {
                "${(t.cause ?: t).javaClass.simpleName}"
            }
            area to value
        }
        val constants = deviceClass.fields
            .filter { it.name.contains("SAFETY_BELT", ignoreCase = true) && it.type == Int::class.javaPrimitiveType }
            .mapNotNull { field -> runCatching { "${field.name}=${field.getInt(null)}" }.getOrNull() }
            .sorted()
        return statuses to constants
    }

}

/** Satisfies only the SDK's local BYDAUTO permission precheck; backend authorization remains in force. */
internal class BydAutoReadContext(base: Context) : ContextWrapper(base) {
    override fun checkCallingOrSelfPermission(permission: String): Int =
        if (permission.startsWith("android.permission.BYDAUTO_")) PackageManager.PERMISSION_GRANTED
        else super.checkCallingOrSelfPermission(permission)

    override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
        if (permission.startsWith("android.permission.BYDAUTO_")) PackageManager.PERMISSION_GRANTED
        else super.checkPermission(permission, pid, uid)

    override fun enforceCallingOrSelfPermission(permission: String, message: String?) {
        if (!permission.startsWith("android.permission.BYDAUTO_"))
            super.enforceCallingOrSelfPermission(permission, message)
    }

    override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) {
        if (!permission.startsWith("android.permission.BYDAUTO_"))
            super.enforcePermission(permission, pid, uid, message)
    }
}
