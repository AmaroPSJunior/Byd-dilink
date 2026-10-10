package com.byd.carcontrol

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager

object AccessibilitySettingsLauncher {
    fun open(activity: Activity): Boolean = AccessibilitySettingsRoute.open(resolve = { action ->
        val intent = Intent(action).apply {
            if (action == AccessibilitySettingsRoute.OEM) setPackage(AccessibilitySettingsRoute.OEM_PACKAGE)
        }
        val info = activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        info?.let {
            AccessibilitySettingsRoute.Target(it.packageName, it.name,
                it.enabled && it.applicationInfo.enabled, it.exported,
                it.permission.isNullOrEmpty() || activity.checkSelfPermission(it.permission) == PackageManager.PERMISSION_GRANTED)
        }
    }, launch = { action, target ->
        try {
            activity.startActivity(Intent(action).setClassName(target.packageName, target.name))
        } catch (_: ActivityNotFoundException) {
            throw AccessibilitySettingsRoute.Unavailable()
        }
    })
}
