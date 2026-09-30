package com.byd.carcontrol.inspector

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiLinkInspectorActivity : AppCompatActivity() {
    private val storage by lazy { InspectorSessionStorage(this) }
    private lateinit var page: LinearLayout
    private lateinit var body: LinearLayout
    private var selectedSession: StoredInspectorSession? = null
    private var searchText = ""
    private var timeRangeText = ""
    private var lastUiState = DiLinkInspectorService.uiState
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            val latest = DiLinkInspectorService.uiState
            if (::body.isInitialized && selectedSession == null && currentPage != Page.SESSIONS && latest != lastUiState) {
                lastUiState = latest
                renderPage(currentPage)
            }
            refreshHandler.postDelayed(this, 1200L)
        }
    }
    private var currentPage = Page.MONITORING

    private enum class Page { MONITORING, EXPERIMENT, SESSIONS }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DiLink Inspector"
        setContentView(buildRoot())
        renderPage(Page.MONITORING)
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.post(refreshRunnable)
    }

    override fun onPause() {
        refreshHandler.removeCallbacks(refreshRunnable)
        super.onPause()
    }

    private fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 13, 30))
            setPadding(dp(14), dp(12), dp(14), dp(10))
        }
        root.addView(TextView(this).apply {
            text = "DiLink Inspector"
            textSize = 23f
            setTextColor(Color.rgb(0, 229, 255))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        })
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(navButton("MONITORAMENTO", Page.MONITORING), weightedParams())
        nav.addView(navButton("EXPERIMENTO", Page.EXPERIMENT), weightedParams())
        nav.addView(navButton("SESSÕES", Page.SESSIONS), weightedParams())
        root.addView(nav)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(24))
        }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return root
    }

    private fun navButton(text: String, destination: Page): Button = Button(this).apply {
        this.text = text
        textSize = 10f
        setOnClickListener { selectedSession = null; renderPage(destination) }
    }

    private fun renderPage(destination: Page) {
        currentPage = destination
        selectedSession = null
        body.removeAllViews()
        when (destination) {
            Page.MONITORING -> renderMonitoring()
            Page.EXPERIMENT -> renderExperiment()
            Page.SESSIONS -> renderSessions()
        }
    }

    private fun renderMonitoring() {
        addTitle("Monitoramento passivo")
        addText("Coleta em foreground service enquanto você usa normalmente a central. O Inspector não envia comandos ao carro nem invoca métodos BYDAuto descobertos.")
        addStateCard()
        val active = DiLinkInspectorService.uiState.active
        addButton(if (active && DiLinkInspectorService.uiState.mode == InspectorMode.MONITORING.wireName) "Parar monitoramento" else "Iniciar monitoramento", if (active) "#b91c1c" else "#0284c7") {
            if (active) stopSession() else startSession(InspectorMode.MONITORING)
        }
        addText("Fontes instrumentadas nesta versão:\n• BYDAuto light/setting: leitura read-only dos FIDs conhecidos de luz interna e indicadores relacionados, a cada 2 s; registra valor ou erro por FID.\n• Reflection: classes, métodos e nomes/tipos de campos BYDAuto relevantes; nada é instanciado ou invocado.\n• ServiceManager: snapshots de uma allowlist; consulta de existência, alive e descriptor.\n• SystemProperties: leitura de três propriedades conhecidas, sujeita às restrições de hidden API/SELinux.\n• Broadcasts: quatro ações BYD já declaradas no receiver do app.\n• SensorManager: acelerômetro, giroscópio, gravidade, aceleração linear e vetor de rotação disponíveis.\n• logcat: stream filtrado por termos de iluminação/veículo; Android pode negar logs de outros UIDs.\n• Permissões: estado solicitado/concedido ao UID deste APK.")
        addText("Limites: o Android não permite ao app observar transações Binder de outros processos, chamadas de métodos dentro de outros APKs, broadcasts arbitrários não destinados a ele, todas as propriedades do sistema nem callbacks OEM sem contrato/permissão. Snapshot de tempo próximo a uma marcação é correlação temporal, não prova causal.")
    }

    private fun renderExperiment() {
        addTitle("Experimento controlado")
        addText("Inicie a sessão antes de fazer uma ação manual no veículo. Use MARCAR AÇÃO no instante da mudança e descreva o que fez. Cada marcação usa timestamp de relógio de parede e relógio monotônico.")
        addStateCard()
        val activeExperiment = DiLinkInspectorService.uiState.active && DiLinkInspectorService.uiState.mode == InspectorMode.EXPERIMENT.wireName
        addButton("Iniciar experimento", "#7c3aed", enabled = !DiLinkInspectorService.uiState.active) { startSession(InspectorMode.EXPERIMENT) }
        addButton("MARCAR AÇÃO", "#0f766e", enabled = activeExperiment) { promptActionMarker() }
        addButton("Finalizar experimento", "#b91c1c", enabled = activeExperiment) { stopSession() }
        addText("Janela de correlação: 20 segundos antes e 30 segundos depois da marcação. O arquivo guarda eventos e registros CORRELATION separados; a associação é explicitamente temporal e não afirma causalidade. Mudanças repetidas recebem repeatCount.")
    }

    private fun renderSessions() {
        addTitle(if (selectedSession == null) "Sessões salvas" else "Sessão ${selectedSession?.sessionId}")
        val selected = selectedSession
        if (selected == null) {
            val sessions = storage.listSessions()
            if (sessions.isEmpty()) addText("Nenhuma sessão encontrada. Inicie Monitoramento ou Experimento para criar uma.")
            sessions.forEach { session ->
                val block = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    setBackgroundColor(Color.rgb(15, 23, 42))
                }
                block.addView(TextView(this).apply {
                    text = "${session.mode.uppercase(Locale.ROOT)} · ${session.sessionId}\n${formatTimestamp(session.startedAt)}${session.endedAt?.let { " — ${formatTimestamp(it)}" } ?: " — em andamento/interrompida"}\n${session.storageLabel}"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                })
                block.addView(Button(this).apply { text = "Ver eventos e correlações"; setOnClickListener { selectedSession = session; renderSessionDetail(session) } })
                body.addView(block, marginParams(bottom = 8))
            }
        } else {
            renderSessionDetail(selected)
        }
    }

    private fun renderSessionDetail(session: StoredInspectorSession) {
        body.removeAllViews()
        addTitle("Eventos · ${session.sessionId}")
        addText("Armazenamento: ${session.storageLabel}\nFormato: events.jsonl (um objeto JSON por linha) e metadata.json.")
        val search = EditText(this).apply {
            hint = "Filtrar classe, serviço, propriedade, método ou origem"
            setText(searchText)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        body.addView(search, marginParams(bottom = 6))
        val times = EditText(this).apply {
            hint = "Horário opcional HH:mm-HH:mm"
            setText(timeRangeText)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
        }
        body.addView(times, marginParams(bottom = 6))
        val output = TextView(this).apply { textSize = 11f; setTextColor(Color.rgb(226, 232, 240)); setTextIsSelectable(true) }
        fun showFiltered() {
            searchText = search.text.toString()
            timeRangeText = times.text.toString()
            val all = storage.readEvents(session)
            val query = searchText.trim().lowercase(Locale.ROOT)
            val range = parseTimeRange(timeRangeText)
            val matching = all.filter { event ->
                val searchable = listOfNotNull(event.source, event.service, event.className, event.method, event.property, event.eventType, event.oldValue, event.newValue, event.rawValue, event.actionMarker).joinToString(" ").lowercase(Locale.ROOT)
                val timeOk = range == null || isInsideTimeRange(event.timestamp, range.first, range.second)
                (query.isEmpty() || searchable.contains(query)) && timeOk
            }
            val visible = matching.takeLast(MAX_VISIBLE_EVENTS)
            output.text = buildString {
                append("Exibindo ${visible.size} de ${matching.size} eventos encontrados (${all.size} total).\n\n")
                visible.forEach { event ->
                    append(formatTimestamp(event.timestamp)).append("  ").append(event.eventType).append("  [").append(event.source).append("]\n")
                    event.service?.let { append("  service: ").append(it).append('\n') }
                    event.className?.let { append("  class: ").append(it).append('\n') }
                    event.method?.let { append("  method/callback: ").append(it).append('\n') }
                    event.property?.let { append("  property: ").append(it).append('\n') }
                    if (event.oldValue != null || event.newValue != null) append("  ").append(event.oldValue ?: "∅").append(" → ").append(event.newValue ?: "∅").append('\n')
                    event.rawValue?.let { append("  raw: ").append(it.take(360)).append('\n') }
                    event.permission?.let { append("  permission: ").append(it).append('\n') }
                    event.actionMarker?.let { append("  marker: ").append(it).append(" (Δt=").append(event.timeFromMarker).append(" ms)\n") }
                    if (event.eventType == "CORRELATION") append("  correlação temporal; causalidade não confirmada\n")
                    if (event.metadata.isNotEmpty()) append("  metadata: ").append(event.metadata.toString().take(420)).append('\n')
                    append('\n')
                }
            }
        }
        addButton("Aplicar filtro", "#475569") { showFiltered() }
        addButton("Limpar filtro", "#334155") { search.setText(""); times.setText(""); showFiltered() }
        addButton("Compartilhar events.jsonl", "#0369a1") { shareSessionFile(session, "events.jsonl") }
        addButton("Compartilhar metadata.json", "#0369a1") { shareSessionFile(session, "metadata.json") }
        addButton("Voltar à lista", "#334155") { selectedSession = null; renderSessions() }
        body.addView(output, marginParams(top = 10))
        showFiltered()
    }

    private fun addStateCard() {
        val state = DiLinkInspectorService.uiState
        val label = if (state.active) "ATIVO · ${state.mode}" else "INATIVO"
        addText("Estado: $label\nSessão: ${state.sessionId ?: "—"}\nEventos gravados: ${state.eventCount}\nDestino: ${state.storageLocation.ifBlank { "Ainda não testado; será verificado ao iniciar uma sessão." }}${state.lastError?.let { "\nAviso de armazenamento: $it" } ?: ""}")
    }

    private fun promptActionMarker() {
        val input = EditText(this).apply { hint = "Ex.: Alterei temperatura para 22 °C"; setSingleLine(false) }
        AlertDialog.Builder(this)
            .setTitle("Marcar ação")
            .setMessage("Descreva a ação manual que você está realizando no veículo.")
            .setView(input)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Marcar") { _, _ ->
                val description = input.text.toString().trim()
                if (description.isEmpty()) Toast.makeText(this, "Informe uma descrição para a marcação.", Toast.LENGTH_SHORT).show()
                else DiLinkInspectorService.send(this, DiLinkInspectorService.ACTION_MARK, description)
            }
            .show()
    }

    private fun startSession(mode: InspectorMode) {
        if (DiLinkInspectorService.uiState.active) {
            Toast.makeText(this, "Finalize a sessão ativa antes de iniciar outra.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            DiLinkInspectorService.send(this, if (mode == InspectorMode.EXPERIMENT) DiLinkInspectorService.ACTION_START_EXPERIMENT else DiLinkInspectorService.ACTION_START_MONITORING)
            Toast.makeText(this, "Solicitado início de ${mode.wireName}.", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "Não foi possível iniciar: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopSession() {
        DiLinkInspectorService.send(this, DiLinkInspectorService.ACTION_STOP)
        Toast.makeText(this, "Finalizando sessão e atualizando metadata…", Toast.LENGTH_SHORT).show()
    }

    private fun shareSessionFile(session: StoredInspectorSession, fileName: String) {
        val uri = storage.shareUri(session, fileName)
        if (uri == null) {
            Toast.makeText(this, "Arquivo indisponível para compartilhamento.", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (fileName.endsWith("jsonl")) "application/x-ndjson" else "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Exportar $fileName"))
    }

    private fun addTitle(text: String) {
        body.addView(TextView(this).apply { this.text = text; textSize = 18f; setTextColor(Color.rgb(56, 189, 248)); setTypeface(null, android.graphics.Typeface.BOLD) }, marginParams(bottom = 8))
    }

    private fun addText(text: String) {
        body.addView(TextView(this).apply { this.text = text; textSize = 12f; setTextColor(Color.rgb(203, 213, 225)); setPadding(dp(10), dp(9), dp(10), dp(9)); setBackgroundColor(Color.rgb(15, 23, 42)) }, marginParams(bottom = 9))
    }

    private fun addButton(text: String, color: String, enabled: Boolean = true, action: () -> Unit) {
        body.addView(Button(this).apply {
            this.text = text
            isEnabled = enabled
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(color))
            setTextColor(Color.WHITE)
            setOnClickListener { action() }
        }, marginParams(bottom = 6))
    }

    private fun weightedParams() = LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(2) }
    private fun marginParams(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

    private fun parseTimeRange(value: String): Pair<Int, Int>? {
        val pieces = value.trim().split('-')
        if (pieces.size != 2) return null
        fun parse(part: String): Int? {
            val fields = part.trim().split(':')
            if (fields.size != 2) return null
            val hour = fields[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            val minute = fields[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            return hour * 60 + minute
        }
        val from = parse(pieces[0]) ?: return null
        val to = parse(pieces[1]) ?: return null
        return from to to
    }

    private fun isInsideTimeRange(timestamp: Long, from: Int, to: Int): Boolean {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val minutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        return if (from <= to) minutes in from..to else minutes >= from || minutes <= to
    }

    companion object { private const val MAX_VISIBLE_EVENTS = 500 }
}
