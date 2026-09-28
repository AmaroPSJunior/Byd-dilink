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
        const val KEY_PENDING_FAN = "pending_fan"
        const val KEY_PENDING_TEMPERATURE = "pending_temperature_c"
        const val KEY_REQUEST_TIME = "request_time"
        const val KEY_RESULT = "result"

        private const val TAG = "BydClimateA11y"
        private const val HVAC_PACKAGE = "com.byd.airconditioning"
        private const val HVAC_POWER_VIEW = "com.byd.airconditioning:id/front_ac_power_id"
        private const val REQUEST_TIMEOUT_MS = 30_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var gestureInProgress = false
    private var uiCommandRunning = false
    private var verificationAttempts = 0
    private var temperatureTaps = 0

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
        val desiredFan = prefs.getInt(KEY_PENDING_FAN, -1)
        val desiredTemperature = prefs.getFloat(KEY_PENDING_TEMPERATURE, Float.NaN)
        if (desired !in 0..1 && desiredFan !in 1..7 && !desiredTemperature.isFinite()) return

        val requestedAt = prefs.getLong(KEY_REQUEST_TIME, 0L)
        if (System.currentTimeMillis() - requestedAt > REQUEST_TIMEOUT_MS) {
            finishCommand("Comando HVAC expirou antes de localizar o controle OEM.")
            return
        }

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != HVAC_PACKAGE) return
        if (desiredFan in 1..7) {
            if (uiCommandRunning) { root.recycle(); return }
            uiCommandRunning = true
            root.recycle()
            adjustFan(desiredFan)
            return
        }
        if (desiredTemperature.isFinite()) {
            if (uiCommandRunning) { root.recycle(); return }
            uiCommandRunning = true
            root.recycle()
            adjustTemperature(desiredTemperature.toDouble())
            return
        }
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
        dispatchTap(x, y) {
            verificationAttempts = 1
            handler.postDelayed({ verifyResult() }, 1_500)
        }
    }

    private fun adjustFan(target: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getInt(KEY_PENDING_FAN, -1) != target) return
        val current = runCatching { BydClimateAdjustment.read(this).windLevel }.getOrNull()
        if (current == target) {
            finishCommand("Ventilação $target/7 confirmada pela leitura HVAC OEM.")
            return
        }
        if (verificationAttempts >= 3) {
            finishCommand("A tela OEM não confirmou a ventilação $target/7 (leitura=${current ?: "indisponível"}).")
            return
        }
        val root = rootInActiveWindow
        if (root?.packageName?.toString() != HVAC_PACKAGE) {
            root?.recycle()
            finishCommand("Painel HVAC OEM não está ativo; ventilação não alterada.")
            return
        }
        fun nodeBounds(id: String): Rect? = try {
            root.findAccessibilityNodeInfosByViewId("$HVAC_PACKAGE:id/$id")?.firstOrNull()?.let { node ->
                Rect().also(node::getBoundsInScreen).also { node.recycle() }
            }
        } catch (_: Throwable) { null }
        val minimum = nodeBounds("wind_min_id")
        val maximum = nodeBounds("wind_max_id")
        val track = nodeBounds("wind_level_id")
        root.recycle()
        if (minimum == null || maximum == null || track == null || maximum.left <= minimum.right) {
            finishCommand("Não foi possível localizar a barra de ventilação OEM.")
            return
        }
        val step = (maximum.left - minimum.right).toFloat() / 7f
        val x = minimum.right + (target - 0.5f) * step
        val y = track.exactCenterY()
        verificationAttempts++
        dispatchTap(x, y) { handler.postDelayed({ adjustFan(target) }, 600) }
    }

    private fun adjustTemperature(targetCelsius: Double) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getFloat(KEY_PENDING_TEMPERATURE, Float.NaN).isFinite()) return
        val snapshot = runCatching { BydClimateAdjustment.read(this) }.getOrNull()
        val current = snapshot?.temperatureCelsius
        if (snapshot == null || current == null) {
            finishCommand("A leitura de temperatura HVAC está indisponível; ajuste não enviado.")
            return
        }
        val step = snapshot.temperatureStepCelsius.coerceAtLeast(0.5)
        if (kotlin.math.abs(current - targetCelsius) < step / 2.0) {
            finishCommand("Temperatura ${current} °C confirmada pela leitura HVAC OEM.")
            return
        }
        if (temperatureTaps >= 32) {
            finishCommand("O painel OEM não atingiu ${targetCelsius} °C (leitura=${current} °C).")
            return
        }
        val root = rootInActiveWindow
        if (root?.packageName?.toString() != HVAC_PACKAGE) {
            root?.recycle()
            finishCommand("Painel HVAC OEM não está ativo; temperatura não alterada.")
            return
        }
        val id = if (targetCelsius > current) "main_arrow_plus_img" else "main_arrow_minus_img"
        val bounds = try {
            root.findAccessibilityNodeInfosByViewId("$HVAC_PACKAGE:id/$id")?.firstOrNull()?.let { node ->
                Rect().also(node::getBoundsInScreen).also { node.recycle() }
            }
        } catch (_: Throwable) { null }
        root.recycle()
        if (bounds == null) {
            finishCommand("Não foi possível localizar os controles de temperatura do motorista.")
            return
        }
        temperatureTaps++
        dispatchTap(bounds.exactCenterX(), bounds.exactCenterY()) {
            handler.postDelayed({ adjustTemperature(targetCelsius) }, 500)
        }
    }

    private fun dispatchTap(x: Float, y: Float, onCompleted: () -> Unit) {
        if (x < 200 || x > 1900 || y < 100 || y > 980) {
            finishCommand("Controle HVAC OEM fora da área esperada; nenhum toque foi enviado.")
            return
        }
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + 1f, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 90))
            .build()
        gestureInProgress = true
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                gestureInProgress = false
                onCompleted()
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
            .putInt(KEY_PENDING_FAN, -1)
            .putFloat(KEY_PENDING_TEMPERATURE, Float.NaN)
            .putString(KEY_RESULT, message)
            .apply()
        gestureInProgress = false
        uiCommandRunning = false
        verificationAttempts = 0
        temperatureTaps = 0
    }

    override fun onInterrupt() {
        gestureInProgress = false
    }
}
