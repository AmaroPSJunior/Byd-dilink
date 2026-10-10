package com.byd.carcontrol

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** One explicit request, one exact OEM node click. No gestures or coordinates. */
class SunshadeAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile private var connected: SunshadeAccessibilityService? = null
        var result: String = "Requer acessibilidade habilitada e leitura OEM autorizada de velocidade/P."
            private set
        fun available() = connected != null
        fun busy() = connected?.pending != null
        fun cancelOnReturn() {
            val service = connected ?: return
            if (service.pending != null) {
                service.pending = null
                service.screenConfirmed = false
                service.handler.removeCallbacks(service.timeout)
                result = "Retorno ao aplicativo: solicitação pendente cancelada."
            }
        }
        fun request(activity: Activity, action: SunshadeAutomationPolicy.Action) {
            val service = connected ?: return
            service.begin(activity, action)
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var pending: SunshadeAutomationPolicy.Request? = null
    private var screenConfirmed = false
    private val timeout = Runnable { finish("Tempo esgotado: nenhum clique enviado.") }
    override fun onServiceConnected() { connected = this }
    private fun begin(activity: Activity, action: SunshadeAutomationPolicy.Action) = synchronized(SunshadeAutomationPolicy.operationLock) {
        if (pending != null) { result = "Já existe uma solicitação em andamento."; return }
        if (!SunshadeAutomationSafety.allowed(this)) {
            result = "Bloqueado: confirme leituras válidas de velocidade, P e estado OEM. Nenhum clique enviado."
            return
        }
        pending = SunshadeAutomationPolicy.Request(action, SystemClock.elapsedRealtime())
        screenConfirmed = false
        result = "Localizando controle OEM…"
        handler.postDelayed(timeout, 10_000)
        try {
            activity.startActivity(Intent().setClassName(SunshadeAutomationPolicy.PACKAGE, SunshadeAutomationPolicy.ACTIVITY))
        } catch (_: Exception) { finish("Tela OEM indisponível. Nenhum clique enviado.") }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val request = pending ?: return
        if (request.expired(SystemClock.elapsedRealtime())) { finish("Solicitação expirada."); return }
        if (event?.packageName?.toString() != SunshadeAutomationPolicy.PACKAGE) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            screenConfirmed = event.className?.toString() == SunshadeAutomationPolicy.ACTIVITY
        }
        if (!screenConfirmed) return
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != SunshadeAutomationPolicy.PACKAGE) return
            val nodes = root.findAccessibilityNodeInfosByViewId(request.action.viewId)
            try {
                val matching = nodes.filter { node ->
                    SunshadeAutomationPolicy.matches(SunshadeAutomationPolicy.Control(
                        node.packageName?.toString(), node.viewIdResourceName, node.text?.toString(),
                        node.isVisibleToUser, node.isEnabled, node.isClickable), request.action)
                }
                if (matching.size != 1) return // timeout, never fallback to a coordinate or parent
                if (!SunshadeAutomationSafety.allowed(this)) {
                    finish("Condições de segurança não confirmadas. Nenhum clique enviado."); return
                }
                if (!request.consume(SystemClock.elapsedRealtime(), true, screenConfirmed, matching.size)) return
                val accepted = matching.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)
                finish(if (accepted) "Clique enviado à tela OEM. Movimento e posição física não confirmados."
                    else "A tela OEM recusou o clique. Não houve nova tentativa.")
            } finally { nodes.forEach { it.recycle() } }
        } catch (_: Exception) { finish("Falha de acessibilidade; resultado indeterminado. Não repetir automaticamente.") }
        finally { root.recycle() }
    }
    private fun finish(message: String) {
        if (pending == null) return
        pending = null; screenConfirmed = false; handler.removeCallbacks(timeout); result = message
        // Reuse the existing activity/task; do not clear its navigation state.
        try { startActivity(Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        catch (_: Exception) { result = "$message Volte manualmente ao aplicativo." }
    }
    override fun onInterrupt() { finish("Acessibilidade interrompida; solicitação cancelada.") }
    override fun onDestroy() {
        finish("Serviço desligado; solicitação cancelada.")
        connected = null; handler.removeCallbacksAndMessages(null); super.onDestroy()
    }
}
