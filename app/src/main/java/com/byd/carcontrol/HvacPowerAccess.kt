package com.byd.carcontrol

import android.content.Context
import android.content.pm.PackageManager

object HvacPowerAccess {
    // Real PackageManager grants only. Never use BydAutoReadContext for writes.
    fun directPermissionGranted(context: Context): Boolean =
        listOf("android.permission.BYDAUTO_AC_SET", "android.permission.BYDACQUISITION_SEND_BUFFER")
            .all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
}
