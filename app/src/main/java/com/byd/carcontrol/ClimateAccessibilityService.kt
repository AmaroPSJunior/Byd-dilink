package com.byd.carcontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.accessibilityservice.GestureDescription
import android.util.Log

/**
 * Executes only an explicit HVAC power request against the OEM climate screen.
 * It ignores every other package and requires the exact OEM power view ID.
 */
class ClimateAccessibilityService : AccessibilityService() {

    companion object {
        const val PREFS_NAME = "climate_accessibility"
        const val KEY_PENDING_POWER = "pending_power"
        const val KEY_REQUEST_TIME = "request_time"
        const val KEY_RESULT = "result"

        private const val TAG = "BydClimateA11y"
        private const val HVAC_PACKAGE = "com.byd.airconditioning"
        private const val HVAC_POWER_VIEW = "com.byd.airconditioning:id/front_ac_power_id"
        private const val REQUEST_TIMEOUT_MS = 30_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var gestureInProgress = false
    private var verificationAttempts = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            packageNames = arrayOf(HVAC_PACKAGE)
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != HVAC_PACKAGE || gestureInProgress) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val desired = prefs.getInt(KEY_PENDING_POWER, -1)
        if (desired != 0 && desired != 1) return

        val requestedAt = prefs.getLong(KEY_REQUEST_TIME, 0L)
        if (System.currentTimeMillis() - requestedAt > REQUEST_TIMEOUT_MS) {
            finishCommand("Comando HVAC expirou antes de localizar o controle OEM.")
            return
        }

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != HVAC_PACKAGE) return
        val powerNode = findPowerNode(root) ?: return

        // On this DiLink build the OEM view is selected when HVAC is OFF.
        val actualOn = !powerNode.isSelected
        if (actualOn == (desired == 1)) {
            finishCommand("Ar-condicionado ${if (actualOn) "ligado" else "desligado"}; estado confirmado pela tela OEM.")
            powerNode.recycle()
            root.recycle()
            return
        }

        if (verificationAttempts == 0) {
            val bounds = Rect().also(powerNode::getBoundsInScreen)
            powerNode.recycle()
            root.recycle()
            dispatchPowerTap(bounds)
        } else {
            powerNode.recycle()
            root.recycle()
        }
    }

    private fun findPowerNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return try {
            root.findAccessibilityNodeInfosByViewId(HVAC_POWER_VIEW)?.firstOrNull()
        } catch (t: Throwable) {
            Log.w(TAG, "OEM power view is not accessible yet", t)
            null
        }
    }

    private fun dispatchPowerTap(bounds: Rect) {
        if (bounds.isEmpty || bounds.width() > 400 || bounds.height() > 200 ||
            bounds.left < 300 || bounds.top < 100 || bounds.bottom > 400
        ) {
            finishCommand("Controle de energia OEM encontrado em posição inesperada; nenhum toque foi enviado.")
            return
        }

        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        val path = Path().apply {
            moveTo(x, y)
            // A zero-length stroke can be accepted by dispatchGesture but inject no tap.
            lineTo(x + 1f, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 90))
            .build()

        gestureInProgress = true
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                gestureInProgress = false
                verificationAttempts = 1
                // The OEM screen animates its HVAC state asynchronously; allow it to settle.
                handler.postDelayed({ verifyResult() }, 1_500)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                gestureInProgress = false
                finishCommand("O painel OEM cancelou o toque; estado HVAC não confirmado.")
            }
        }, handler)

        if (!accepted) {
            gestureInProgress = false
            finishCommand("O sistema recusou o toque no painel OEM; estado HVAC não confirmado.")
        }
    }

    private fun verifyResult() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val desired = prefs.getInt(KEY_PENDING_POWER, -1)
        if (desired != 0 && desired != 1) return

        val root = rootInActiveWindow
        if (root?.packageName?.toString() != HVAC_PACKAGE) {
            root?.recycle()
            finishCommand("Painel OEM deixou de estar ativo; estado HVAC não confirmado.")
            return
        }
        val powerNode = findPowerNode(root)
        if (powerNode == null) {
            root.recycle()
            retryOrFail()
            return
        }

        val actualOn = !powerNode.isSelected
        powerNode.recycle()
        root.recycle()

        if (actualOn == (desired == 1)) {
            finishCommand("Ar-condicionado ${if (actualOn) "ligado" else "desligado"}; estado confirmado pela tela OEM.")
        } else {
            retryOrFail()
        }
    }

    private fun retryOrFail() {
        if (verificationAttempts < 4) {
            verificationAttempts++
            handler.postDelayed({ verifyResult() }, 1_000)
        } else {
            finishCommand("A tela OEM não confirmou a mudança de estado do ar-condicionado.")
        }
    }

    private fun finishCommand(message: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putInt(KEY_PENDING_POWER, -1)
            .putString(KEY_RESULT, message)
            .apply()
        gestureInProgress = false
        verificationAttempts = 0
    }

    override fun onInterrupt() {
        gestureInProgress = false
    }
}
