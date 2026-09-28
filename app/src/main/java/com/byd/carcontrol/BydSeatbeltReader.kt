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
        val names = listOf(
            listOf("SAFETY_BELT_AREA_MAIN") to ("SAFETY_BELT_AREA_MAIN" to "Motorista"),
            listOf("SAFETY_BELT_AREA_DEPUTY", "SAFETY_BELT_AREA_FRONT_ROW_SEAT_RIGHT") to ("SAFETY_BELT_AREA_DEPUTY" to "Passageiro dianteiro"),
            listOf("SAFETY_BELT_AREA_SECOND_ROW_SEAT_LEFT", "SAFETY_BELT_AREA_REAR_LEFT") to ("SAFETY_BELT_AREA_SECOND_ROW_SEAT_LEFT" to "Traseiro esquerdo"),
            listOf("SAFETY_BELT_AREA_SECOND_ROW_SEAT_MID", "SAFETY_BELT_AREA_SECOND_ROW_SEAT_MIDDLE", "SAFETY_BELT_AREA_SECOND_ROW_SEAT_CENTER") to ("SAFETY_BELT_AREA_SECOND_ROW_SEAT_MID" to "Traseiro central"),
            listOf("SAFETY_BELT_AREA_SECOND_ROW_SEAT_RIGHT", "SAFETY_BELT_AREA_REAR_RIGHT") to ("SAFETY_BELT_AREA_SECOND_ROW_SEAT_RIGHT" to "Traseiro direito")
        )
        val seats = names.mapNotNull { (candidates, seat) ->
            val (key, label) = seat
            val field = candidates.firstNotNullOfOrNull { name -> runCatching { deviceClass.getField(name) }.getOrNull() }
                ?: return@mapNotNull null
            val area = runCatching { field.getInt(null) }.getOrNull() ?: return@mapNotNull null
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
