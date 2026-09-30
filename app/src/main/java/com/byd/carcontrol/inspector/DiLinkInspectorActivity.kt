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
    private var evidenceCategory = "CAPTURA"
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
        addText("Fontes: getters BYDAuto conhecidos (sem setters); reflexão BYDAuto; Binder conhecido e inventário ServiceManager; sensores Android; broadcasts entregues ao receiver; permissões do UID; logcat -b all sem filtro textual; Settings.System/Secure/Global; getprop; service list; dumpsys de byd_car_service, autoservice e ActivityManager; lshal; processos BYD visíveis; pacotes, componentes e permissões BYDAUTO enumeráveis; inventário raso e filtrado de /dev, /sys e /proc; tentativa de dmesg. Inventários amplos usam LOW/NORMAL/DEEP e snapshots em marcadores/finalização.")
        addText("Limites: cada fonte registra erro/acesso negado sob o UID do app. READ_LOGS, dumpsys, dmesg, lshal, package visibility, sysfs/proc/devices e APIs BYD podem exigir privilégios OEM/system. Não há interceptação de tráfego Binder alheio. Correlação não confirma causalidade; procure o score e abra RAW/evidência.")
    }

    private fun renderExperiment() {
        addTitle("Experimento controlado")
        addText("Inicie o experimento e aguarde o snapshot inicial. Então execute manualmente a ação no veículo e marque cada transição imediatamente. A tela indica: Agora execute manualmente a ação no veículo. Cada marcador guarda relógio de parede e relógio monotônico em nanossegundos.")
        addStateCard()
        val activeExperiment = DiLinkInspectorService.uiState.active && DiLinkInspectorService.uiState.mode == InspectorMode.EXPERIMENT.wireName
        addButton("Iniciar experimento", "#7c3aed", enabled = !DiLinkInspectorService.uiState.active) { startSession(InspectorMode.EXPERIMENT) }
        addButton("MARCAR AÇÃO", "#0f766e", enabled = activeExperiment && DiLinkInspectorService.uiState.ready) { promptActionMarker() }
        addButton("Finalizar experimento", "#b91c1c", enabled = activeExperiment) { stopSession() }
        if (activeExperiment && DiLinkInspectorService.uiState.ready) addText("AGORA EXECUTE MANUALMENTE A AÇÃO NO VEÍCULO. Marque LIGAR e DESLIGAR separadamente; finalize para comparar snapshots e correlações.")
        else if (activeExperiment) addText("Preparando captura e snapshot inicial. Aguarde o botão MARCAR AÇÃO ficar ativo.")
        addText("Intensidade LOW / NORMAL / DEEP é selecionada ao iniciar. Janela de correlação: 5 s antes e 5 s depois. O score é heurístico, destaca repetição/reversão e não confirma causalidade.")
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
        val categories = listOf("CAPTURA", "EVENTOS", "DIFF", "CORRELAÇÕES", "SERVIÇOS", "HAL/BINDER", "PERMISSÕES/ERROS", "RAW")
        val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        categories.forEach { category ->
            categoryRow.addView(Button(this).apply {
                text = category; textSize = 9f
                backgroundTintList = android.content.res.ColorStateList.valueOf(if (evidenceCategory == category) Color.rgb(3, 105, 161) else Color.rgb(51, 65, 85))
                setTextColor(Color.WHITE)
                setOnClickListener { evidenceCategory = category; showFiltered() }
            }, LinearLayout.LayoutParams(-2, dp(42)).apply { marginEnd = dp(4) })
        }
        body.addView(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = true; addView(categoryRow) }, marginParams(bottom = 6))
        val output = TextView(this).apply { textSize = 11f; setTextColor(Color.rgb(226, 232, 240)); setTextIsSelectable(true) }
        val resultCards = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun showFiltered() {
            searchText = search.text.toString()
            timeRangeText = times.text.toString()
            val all = storage.readEvents(session)
            val query = searchText.trim().lowercase(Locale.ROOT)
            val range = parseTimeRange(timeRangeText)
            val matching = all.filter { event ->
                val categoryOk = when (evidenceCategory) {
                    "EVENTOS" -> event.eventType == "ACTION_MARKER" || event.eventType == "SESSION_STARTED" || event.eventType == "SESSION_FINISHED"
                    "DIFF" -> event.eventType == "STATE_CHANGED" || event.eventType == "STATE_OBSERVED"
                    "CORRELAÇÕES" -> event.eventType == "CORRELATION"
                    "SERVIÇOS" -> event.service != null || event.eventType.contains("SERVICE") || event.eventType.contains("PACKAGE") || event.eventType.contains("COMPONENT")
                    "HAL/BINDER" -> listOfNotNull(event.source, event.service, event.className, event.method).any { it.contains("hal", true) || it.contains("binder", true) || it.contains("BYDAuto", true) }
                    "PERMISSÕES/ERROS" -> event.permission != null || event.eventType.contains("ERROR") || event.eventType.contains("DENIED") || event.eventType.contains("BLOCKED") || event.eventType.contains("LIMIT") || event.eventType.contains("LIMITED")
                    "RAW" -> event.source == "android.logcat"
                    else -> true
                }
                val searchable = listOfNotNull(event.source, event.service, event.className, event.method, event.property, event.eventType, event.oldValue, event.newValue, event.rawValue, event.actionMarker).joinToString(" ").lowercase(Locale.ROOT)
                val timeOk = range == null || isInsideTimeRange(event.timestamp, range.first, range.second)
                categoryOk && (query.isEmpty() || searchable.contains(query)) && timeOk
            }
            val ranked = if (evidenceCategory == "CORRELAÇÕES") matching.sortedByDescending { (it.metadata["heuristicScorePercent"] as? Number)?.toInt() ?: 0 } else matching
            val visible = if (evidenceCategory == "CORRELAÇÕES") ranked.take(MAX_VISIBLE_EVENTS) else ranked.takeLast(MAX_VISIBLE_EVENTS)
            output.text = "$evidenceCategory · exibindo ${visible.size} de ${matching.size} eventos (${all.size} total). Toque em um item para abrir toda a evidência bruta."
            resultCards.removeAllViews()
            visible.forEach { event ->
                val score = (event.metadata["heuristicScorePercent"] as? Number)?.toInt()
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(9), dp(7), dp(9), dp(7))
                    setBackgroundColor(Color.rgb(15, 23, 42))
                }
                val summary = listOfNotNull(
                    "${formatTimestamp(event.timestamp)} · ${event.eventType} · ${event.source}",
                    event.property?.let { "property=$it" },
                    if (event.oldValue != null || event.newValue != null) "${event.oldValue ?: "∅"} → ${event.newValue ?: "∅"}" else null,
                    score?.let { "correlação heurística $it%" },
                    event.rawValue?.takeIf { it.isNotBlank() }?.let { "raw=${it.take(180)}" }
                ).joinToString("\n")
                card.addView(TextView(this).apply { text = summary; textSize = 11f; setTextColor(Color.WHITE); setTextIsSelectable(true) })
                card.addView(Button(this).apply {
                    text = "Abrir evidência bruta"
                    setOnClickListener {
                        val evidenceText = TextView(this@DiLinkInspectorActivity).apply { text = event.toJson().toString(2); textSize = 12f; setTextColor(Color.WHITE); setTextIsSelectable(true); setPadding(dp(12), dp(8), dp(12), dp(8)) }
                        AlertDialog.Builder(this@DiLinkInspectorActivity)
                            .setTitle("${event.eventType} · ${event.source}")
                            .setView(ScrollView(this@DiLinkInspectorActivity).apply { addView(evidenceText) })
                            .setPositiveButton("Fechar", null).show()
                    }
                })
                resultCards.addView(card, marginParams(top = 4, bottom = 4))
            }
        }
        addButton("Aplicar filtro", "#475569") { showFiltered() }
        addButton("Limpar filtro", "#334155") { search.setText(""); times.setText(""); showFiltered() }
        addButton("Compartilhar events.jsonl", "#0369a1") { shareSessionFile(session, "events.jsonl") }
        addButton("Compartilhar metadata.json", "#0369a1") { shareSessionFile(session, "metadata.json") }
        addButton("Exportar pacote JSON", "#0369a1") { shareSessionBundle(session, humanReadable = false) }
        addButton("Exportar relatório legível", "#0369a1") { shareSessionBundle(session, humanReadable = true) }
        addButton("Voltar à lista", "#334155") { selectedSession = null; renderSessions() }
        body.addView(output, marginParams(top = 10, bottom = 4))
        body.addView(resultCards)
        showFiltered()
    }

    private fun addStateCard() {
        val state = DiLinkInspectorService.uiState
        val label = if (state.active) "ATIVO · ${state.mode}" else "INATIVO"
        addText("Estado: $label${if (state.active && !state.ready) " · preparando snapshot inicial" else if (state.active) " · baseline pronto" else ""}\nSessão: ${state.sessionId ?: "—"}\nEventos gravados: ${state.eventCount}\nDestino: ${state.storageLocation.ifBlank { "Ainda não testado; será verificado ao iniciar uma sessão." }}${state.lastError?.let { "\nAviso de armazenamento: $it" } ?: ""}")
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
        AlertDialog.Builder(this).setTitle("Intensidade da captura")
            .setItems(arrayOf("LOW · snapshot 30 s; inventário 120 s; luz 10 s; sensores 5 s", "NORMAL · snapshot 10 s; inventário 60 s; luz 2 s; sensores 1 s", "DEEP · snapshot 5 s; inventário 30 s; luz 1 s; sensores 0,5 s")) { _, which ->
                val intensity = listOf("LOW", "NORMAL", "DEEP")[which]
                try {
                    DiLinkInspectorService.send(this, if (mode == InspectorMode.EXPERIMENT) DiLinkInspectorService.ACTION_START_EXPERIMENT else DiLinkInspectorService.ACTION_START_MONITORING, intensity = intensity)
                    Toast.makeText(this, "Início solicitado · $intensity.", Toast.LENGTH_SHORT).show()
                } catch (t: Throwable) { Toast.makeText(this, "Não foi possível iniciar: ${t.message}", Toast.LENGTH_LONG).show() }
            }.setNegativeButton("Cancelar", null).show()
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

    private fun shareSessionBundle(session: StoredInspectorSession, humanReadable: Boolean) {
        try {
            val events = storage.readEvents(session)
            val metadata = storage.readMetadata(session) ?: org.json.JSONObject()
            val directory = java.io.File(cacheDir, "inspector-export").apply { mkdirs() }
            val file = java.io.File(directory, if (humanReadable) "${session.sessionId}-report.txt" else "${session.sessionId}-experiment.json")
            if (humanReadable) {
                file.writeText(buildString {
                    appendLine("DiLink Inspector · ${session.sessionId}")
                    appendLine("Metadata: ${metadata.toString(2)}")
                    appendLine("Eventos: ${events.size}")
                    events.forEach { event ->
                        appendLine("${formatTimestamp(event.timestamp)} ${event.eventType} [${event.source}] ${event.property.orEmpty()} ${event.oldValue.orEmpty()} -> ${event.newValue.orEmpty()} raw=${event.rawValue.orEmpty()}")
                        if (event.metadata.isNotEmpty()) appendLine("  evidence=${event.metadata}")
                    }
                }, Charsets.UTF_8)
            } else {
                val bundle = org.json.JSONObject().put("schemaVersion", 1).put("metadata", metadata).put("events", org.json.JSONArray(events.map { it.toJson() }))
                file.writeText(bundle.toString(2), Charsets.UTF_8)
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply { type = if (humanReadable) "text/plain" else "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startActivity(Intent.createChooser(intent, if (humanReadable) "Exportar relatório" else "Exportar experimento JSON"))
        } catch (t: Throwable) { Toast.makeText(this, "Exportação falhou: ${t.message}", Toast.LENGTH_LONG).show() }
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

    companion object { private const val MAX_VISIBLE_EVENTS = 200 }
}
