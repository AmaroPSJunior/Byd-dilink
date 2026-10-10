package com.byd.carcontrol

/** Only opens a user-facing settings screen; never enables a service. */
object AccessibilitySettingsRoute {
    const val STANDARD = "android.settings.ACCESSIBILITY_SETTINGS"
    const val OEM = "android.intent.action.BYD_ACCESSIBILITY"
    const val OEM_PACKAGE = "com.byd.carsettings"
    const val OEM_ACTIVITY = "com.byd.systemsettings.accessibility.AccessibilityMainActivity"
    data class Target(val packageName: String, val name: String, val enabled: Boolean,
                      val exported: Boolean, val permitted: Boolean)
    class Unavailable : Exception()
    fun open(resolve: (String) -> Target?, launch: (String, Target) -> Unit): Boolean {
        for (action in listOf(STANDARD, OEM)) {
            try {
                val target = resolve(action) ?: continue
                if (!target.enabled || !target.exported || !target.permitted) continue
                if (action == OEM && (target.packageName != OEM_PACKAGE || target.name != OEM_ACTIVITY)) continue
                launch(action, target)
                return true
            } catch (_: Unavailable) {
                // Activity may disappear after resolution. Try only the validated next route.
            } catch (_: SecurityException) {
                // No permissions are granted or bypassed.
            }
        }
        return false
    }
}
