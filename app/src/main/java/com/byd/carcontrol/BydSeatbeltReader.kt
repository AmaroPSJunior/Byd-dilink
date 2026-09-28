package com.byd.carcontrol

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager

/** Read-only probe for the BYD instrument SDK's seat-belt getter. */
object BydSeatbeltReader {
    private const val DEVICE_CLASS = "android.hardware.bydauto.instrument.BYDAutoInstrumentDevice"

    fun readRawStatuses(context: Context): List<Pair<Int, String>> {
        val deviceClass = Class.forName(DEVICE_CLASS)
        val sdkContext = BydAutoReadContext(context.applicationContext)
        val instance = deviceClass.getMethod("getInstance", Context::class.java)
            .invoke(null, sdkContext)
            ?: error("BYDAutoInstrumentDevice.getInstance retornou null")
        val getter = deviceClass.getMethod("getSafetyBeltStatus", Int::class.javaPrimitiveType)
        return (0..8).map { index ->
            val value = try {
                (getter.invoke(instance, index) as? Number)?.toInt()?.toString() ?: "sem valor numérico"
            } catch (t: Throwable) {
                "${(t.cause ?: t).javaClass.simpleName}"
            }
            index to value
        }
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
