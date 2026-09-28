package com.byd.carcontrol

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager

/** Read-only probe for the BYD safety-belt SDK getter. */
object BydSeatbeltReader {
    private const val DEVICE_CLASS = "android.hardware.bydauto.safetybelt.BYDAutoSafetyBeltDevice"

    data class DriverState(val raw: Int, val locked: Int, val unlocked: Int, val invalid: Int)

    fun readDriverState(context: Context): DriverState {
        val deviceClass = Class.forName(DEVICE_CLASS)
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext)
            ?: error("BYDAutoSafetyBeltDevice.getInstance retornou null")
        fun constant(name: String): Int = deviceClass.getField(name).getInt(null)
        val mainArea = constant("SAFETY_BELT_AREA_MAIN")
        val getter = deviceClass.getMethod("getSafetyBeltStatus", Int::class.javaPrimitiveType)
        val raw = (getter.invoke(instance, mainArea) as? Number)?.toInt()
            ?: error("getSafetyBeltStatus não retornou um número")
        return DriverState(
            raw = raw,
            locked = constant("SAFETY_BELT_STATE_LOCK"),
            unlocked = constant("SAFETY_BELT_STATE_UNLOCK"),
            invalid = constant("SAFETY_BELT_STATE_INVALID")
        )
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

    /** Satisfies the SDK's local BYDAUTO permission precheck for this getter only. */
    private class BydAutoReadContext(base: Context) : ContextWrapper(base) {
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
}
