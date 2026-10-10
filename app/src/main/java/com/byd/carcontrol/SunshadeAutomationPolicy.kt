package com.byd.carcontrol

/** Pure, fail-closed rules; no Android or hardware access. */
object SunshadeAutomationPolicy {
    val operationLock = Any()
    const val PACKAGE = "com.byd.carsettings"
    const val ACTIVITY = "com.byd.vehiclesettings.sunroof.view.SunRoofActivity"
    enum class Action(val label: String, val viewId: String) {
        OPEN("Abrir", "$PACKAGE:id/sun_shade_open"),
        CLOSE("Fechar", "$PACKAGE:id/sun_shade_close")
    }
    fun safe(speed: Double?, gear: Int?, permit: Int?, initialized: Int?): Boolean =
        speed != null && speed.isFinite() && kotlin.math.abs(speed) <= 0.5 &&
            gear == 3 && permit == 0 && initialized == 1
    data class Control(val packageName: String?, val id: String?, val label: String?,
                       val visible: Boolean, val enabled: Boolean, val clickable: Boolean)
    fun matches(c: Control, a: Action): Boolean = c.packageName == PACKAGE &&
        c.id == a.viewId && c.label?.trim() == a.label && c.visible && c.enabled && c.clickable

    class Request(val action: Action, val createdAt: Long) {
        private var consumed = false
        fun expired(now: Long) = now < createdAt || now - createdAt >= 10_000
        fun consume(now: Long, safe: Boolean, exactScreen: Boolean, matches: Int): Boolean {
            if (consumed || expired(now) || !safe || !exactScreen || matches != 1) return false
            consumed = true // consume BEFORE dispatch; never retry a refused or ambiguous click
            return true
        }
    }
}
