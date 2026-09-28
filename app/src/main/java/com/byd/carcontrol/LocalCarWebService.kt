package com.byd.carcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** User-started LAN service. Vehicle operations stay in this privileged Android process. */
class LocalCarWebService : Service() {
    private var server: LocalCarHttpServer? = null
    private var beltScheduler: ScheduledExecutorService? = null
    private var beltAnnouncer: SeatbeltVoiceAnnouncer? = null
    private var beltWasEligible = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, makeNotification())
        if (server == null) {
            try {
                server = LocalCarHttpServer(this).also { it.start() }
                startBeltMonitor()
            } catch (t: Throwable) {
                Log.e(TAG, "Não foi possível iniciar o servidor web local", t)
                prefs(this).edit().putBoolean(KEY_RUNNING, false)
                    .putString(KEY_ERROR, t.message ?: t.javaClass.simpleName).apply()
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun makeNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, "Controles web do carro", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Servidor local para controles BYD na rede Wi-Fi" })
        }
        val activity = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        return if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Controles web BYD ativos")
            .setContentText(prefs(this).getString(KEY_URL, "Página local disponível na rede do hotspot"))
            .setContentIntent(activity).setOngoing(true).build()
        else @Suppress("DEPRECATION") Notification.Builder(this)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("Controles web BYD ativos")
            .setContentText("Página local disponível na rede do hotspot")
            .setContentIntent(activity).setOngoing(true).build()
    }

    private fun startBeltMonitor() {
        beltAnnouncer = SeatbeltVoiceAnnouncer(this)
        beltScheduler = Executors.newSingleThreadScheduledExecutor().also { executor ->
            executor.scheduleWithFixedDelay({
                try {
                    server?.refreshAddress()
                    val settings = getSharedPreferences(BELT_PREFS, MODE_PRIVATE)
                    val enabled = settings.getBoolean(BELT_VOICE_ENABLED, true)
                    val threshold = settings.getInt(BELT_SPEED_KMH, 10).coerceIn(1, 200)
                    val driverUnbuckled = BydSeatbeltReader.readAll(this).seats.any { it.key == "SAFETY_BELT_AREA_MAIN" && it.raw == 2 }
                    val speed = runCatching { BydVehicleSpeedReader.readKmh(this) }.getOrNull()
                    val eligible = enabled && driverUnbuckled && speed != null && speed.isFinite() && speed >= threshold
                    if (eligible && !beltWasEligible) beltAnnouncer?.announce(
                        BydSeatbeltReader.readAll(this).seats.filter { it.key == "SAFETY_BELT_AREA_MAIN" && it.raw == 2 }
                    )
                    if (!enabled) beltAnnouncer?.stop()
                    beltWasEligible = eligible
                } catch (t: Throwable) {
                    beltWasEligible = false
                    Log.w(TAG, "Leitura do cinto no serviço web falhou", t)
                }
            }, 0, 2, TimeUnit.SECONDS)
        }
    }

    override fun onDestroy() {
        beltScheduler?.shutdownNow()
        beltScheduler = null
        beltAnnouncer?.release()
        beltAnnouncer = null
        server?.close()
        server = null
        prefs(this).edit().putBoolean(KEY_RUNNING, false).putString(KEY_URL, "").apply()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.byd.carcontrol.action.STOP_LOCAL_WEB"
        const val PREFS = "local_web_server"
        const val KEY_RUNNING = "running"
        const val KEY_URL = "url"
        const val KEY_ERROR = "error"
        const val BELT_PREFS = "seatbelt_alerts"
        const val BELT_VOICE_ENABLED = "voice_enabled"
        const val BELT_SPEED_KMH = "alert_speed_kmh"
        private const val TAG = "LocalCarWebService"
        private const val CHANNEL_ID = "byd_local_web"
        private const val NOTIFICATION_ID = 53120

        fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}

private class LocalCarHttpServer(private val context: Context) : AutoCloseable {
    private val workers = Executors.newCachedThreadPool()
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var accepting = true

    fun start() {
        val token = getOrCreateToken()
        val listener = ServerSocket(PORT, 32, InetAddress.getByName("0.0.0.0"))
        socket = listener
        updateAddress(token)
        Thread({
            while (accepting) {
                try {
                    val client = listener.accept()
                    client.soTimeout = 7000
                    workers.execute { handle(client) }
                } catch (t: Throwable) {
                    if (accepting) Log.w(TAG, "Falha ao aceitar conexão web", t)
                }
            }
        }, "byd-local-web-accept").start()
    }

    fun refreshAddress() {
        val token = LocalCarWebService.prefs(context).getString(KEY_TOKEN, null) ?: return
        updateAddress(token)
    }

    private fun updateAddress(token: String) {
        val ip = findWifiAddress(context)
        val url = if (ip == null) "" else "http://$ip:$PORT/?token=$token"
        LocalCarWebService.prefs(context).edit().putBoolean(LocalCarWebService.KEY_RUNNING, true)
            .putString(LocalCarWebService.KEY_URL, url).putString(LocalCarWebService.KEY_ERROR, "").apply()
    }

    private fun handle(client: Socket) {
        client.use { connection ->
            val input = BufferedInputStream(connection.getInputStream())
            val output = BufferedOutputStream(connection.getOutputStream())
            try {
                val requestLine = readLine(input, 4096) ?: return
                val parts = requestLine.split(' ', limit = 3)
                if (parts.size < 2) return respond(output, 400, jsonError("Requisição inválida."))
                val method = parts[0].uppercase(Locale.US)
                val target = parts[1]
                val uri = java.net.URI(target)
                val headers = linkedMapOf<String, String>()
                var headerBytes = requestLine.length
                while (true) {
                    val line = readLine(input, 8192) ?: break
                    headerBytes += line.length
                    if (headerBytes > 16384) return respond(output, 431, jsonError("Cabeçalhos grandes demais."))
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) headers[line.substring(0, colon).trim().lowercase(Locale.US)] = line.substring(colon + 1).trim()
                }
                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                if (contentLength !in 0..65536) return respond(output, 413, jsonError("Corpo inválido ou acima do limite."))
                val bodyBytes = ByteArray(contentLength)
                var offset = 0
                while (offset < contentLength) {
                    val count = input.read(bodyBytes, offset, contentLength - offset)
                    if (count < 0) break
                    offset += count
                }
                if (offset != contentLength) return respond(output, 400, jsonError("Corpo incompleto."))
                val body = if (contentLength == 0) JSONObject() else JSONObject(String(bodyBytes, Charsets.UTF_8))
                val params = parseQuery(uri.rawQuery)
                val path = uri.path ?: "/"

                if (method == "GET" && (path == "/" || path == "/index.html")) {
                    return respond(output, 200, context.assets.open("web/index.html").use { it.readBytes() }, "text/html; charset=utf-8")
                }
                if (method == "OPTIONS") return respond(output, 405, jsonError("Método não permitido."))
                val supplied = headers["authorization"]?.removePrefix("Bearer ") ?: params["token"].orEmpty()
                if (!tokenMatches(supplied)) return respond(output, 401, jsonError("Pareamento ausente ou inválido."))

                val response = route(method, path, body)
                respond(output, response.first, response.second)
            } catch (t: Throwable) {
                Log.w(TAG, "Falha tratando requisição local", t)
                runCatching { respond(output, 400, jsonError(t.message ?: "Requisição não processada.")) }
            }
        }
    }

    private fun route(method: String, path: String, body: JSONObject): Pair<Int, ByteArray> {
        if (method == "GET" && path == "/api/state") return json(200, readState())
        if (method != "POST") return 405 to jsonError("Método não permitido.")
        return try {
            val result = when {
                path == "/api/climate/power" -> JSONObject().put("message", requestHvacPower(body.getBoolean("on")))
                path == "/api/climate/fan" -> JSONObject().put("message", requestClimateAdjustment("fan", body.getInt("level").also { require(it in 1..7) { "A ventilação deve ficar entre 1 e 7." } }, null))
                path == "/api/climate/temperature" -> JSONObject().put("message", requestClimateAdjustment("temperature", null, body.getDouble("celsius")))
                path == "/api/lights/interior" -> {
                    val light = BydInteriorLightControl.setPower(context, body.getBoolean("on"))
                    JSONObject().put("accepted", light.accepted).put("confirmed", light.observedState == light.requestedState)
                        .put("message", light.detail + if (light.observedState == light.requestedState) "; estado lido confirma o comando." else "; lâmpada física sem confirmação de estado.")
                }
                path == "/api/sunshade" -> {
                    val percent = body.getInt("position").also { require(it in 0..100) { "Posição deve ficar entre 0 e 100%." } }
                    val result = BydSunshadeControl.setPosition(context, percent)
                    JSONObject().put("accepted", result.accepted).put("confirmed", result.confirmed).put("message", result.detail)
                }
                path == "/api/sunshade/stop" -> {
                    val result = BydSunshadeControl.stop(context)
                    JSONObject().put("accepted", result.accepted).put("message", result.detail)
                }
                path.startsWith("/api/windows/") -> {
                    val window = windowFromPath(path.substringAfterLast('/'))
                    val position = body.getInt("position").also { require(it in listOf(0, 50, 100)) { "Use um preset de 0, 50 ou 100%." } }
                    JSONObject().put("message", BydWindowControl.setPresetPosition(context, window, position))
                }
                path == "/api/belt-alerts" -> {
                    val enabled = body.getBoolean("enabled")
                    val speed = body.getInt("speedKmh").also { require(it in 1..200) { "Velocidade deve ficar entre 1 e 200 km/h." } }
                    context.getSharedPreferences(LocalCarWebService.BELT_PREFS, Context.MODE_PRIVATE).edit()
                        .putBoolean(LocalCarWebService.BELT_VOICE_ENABLED, enabled)
                        .putInt(LocalCarWebService.BELT_SPEED_KMH, speed).apply()
                    JSONObject().put("message", "Alerta ${if (enabled) "ativo" else "desativado"}; limite salvo em $speed km/h.")
                }
                else -> return 404 to jsonError("Recurso não encontrado.")
            }
            json(if (result.optBoolean("accepted", true)) 200 else 409, result.put("ok", result.optBoolean("accepted", true)))
        } catch (t: Throwable) {
            409 to jsonError(t.cause?.message ?: t.message ?: "Comando recusado.")
        }
    }

    private fun readState(): JSONObject {
        val root = JSONObject().put("ok", true)
        runCatching {
            val hvac = BydClimateAdjustment.read(context)
            root.put("climate", JSONObject().put("powerState", runCatching { BydClimateAdjustment.readPowerState(context) }.getOrNull())
                .put("fanLevel", hvac.windLevel).put("temperatureCelsius", hvac.temperatureCelsius)
                .put("maxTemperatureCelsius", hvac.maxTemperatureCelsius).put("stepCelsius", hvac.temperatureStepCelsius))
        }.onFailure { root.put("climate", JSONObject().put("error", it.cause?.message ?: it.message)) }
        val speed = runCatching { BydVehicleSpeedReader.readKmh(context) }.getOrNull()?.takeIf { it.isFinite() }
        root.put("speedKmh", speed)
        root.put("sunshade", JSONObject().put("position", BydSunshadeControl.readPosition(context)))
        val windows = JSONObject()
        BydWindowControl.Window.entries.forEach { window ->
            runCatching { BydWindowControl.read(context, window) }.onSuccess { state ->
                windows.put(window.slug(), JSONObject().put("position", state.percent).put("state", state.state).put("permit", state.permit))
            }.onFailure { windows.put(window.slug(), JSONObject().put("error", it.cause?.message ?: it.message)) }
        }
        root.put("windows", windows)
        val beltPrefs = context.getSharedPreferences(LocalCarWebService.BELT_PREFS, Context.MODE_PRIVATE)
        val seat = runCatching { BydSeatbeltReader.readAll(context).seats.firstOrNull() }.getOrNull()
        root.put("belt", JSONObject().put("status", when (seat?.raw) { 1 -> "Afivelado"; 2 -> "Desafivelado"; else -> "Indisponível" }))
        root.put("beltAlert", JSONObject().put("enabled", beltPrefs.getBoolean(LocalCarWebService.BELT_VOICE_ENABLED, true))
            .put("speedKmh", beltPrefs.getInt(LocalCarWebService.BELT_SPEED_KMH, 10)))
        return root
    }

    private fun requestHvacPower(turnOn: Boolean): String {
        val direct = runCatching { BydClimateAdjustment.setPower(context, turnOn) }
        if (direct.isSuccess) return direct.getOrThrow()
        val reason = direct.exceptionOrNull()?.cause?.message ?: direct.exceptionOrNull()?.message ?: "API OEM recusou o comando"
        if (!isClimateAccessibilityEnabled()) throw SecurityException("$reason. O serviço de acessibilidade HVAC do app não está ativo.")

        return runClimateAccessibilityCommand("power", if (turnOn) 1 else 0, null, reason)
    }

    private fun requestClimateAdjustment(kind: String, level: Int?, celsius: Double?): String {
        val direct = runCatching {
            if (kind == "fan") BydClimateAdjustment.setWindLevel(context, requireNotNull(level))
            else BydClimateAdjustment.setTemperatureCelsius(context, requireNotNull(celsius))
        }
        if (direct.isSuccess) return direct.getOrThrow()
        val reason = direct.exceptionOrNull()?.cause?.message ?: direct.exceptionOrNull()?.message ?: "API OEM recusou o comando"
        return runClimateAccessibilityCommand(kind, level, celsius, reason)
    }

    private fun runClimateAccessibilityCommand(kind: String, level: Int?, celsius: Double?, reason: String): String {
        if (!isClimateAccessibilityEnabled()) throw SecurityException(
            "$reason. Ative o serviço de acessibilidade HVAC do app para usar o painel OEM."
        )
        val prefs = context.getSharedPreferences(ClimateAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(ClimateAccessibilityService.KEY_PENDING_POWER, if (kind == "power") level ?: -1 else -1)
            .putInt(ClimateAccessibilityService.KEY_PENDING_FAN, if (kind == "fan") level ?: -1 else -1)
            .putFloat(ClimateAccessibilityService.KEY_PENDING_TEMPERATURE, if (kind == "temperature") celsius!!.toFloat() else Float.NaN)
            .putLong(ClimateAccessibilityService.KEY_REQUEST_TIME, System.currentTimeMillis())
            .putString(ClimateAccessibilityService.KEY_RESULT, "API direta recusada ($reason); aguardando painel HVAC OEM…")
            .apply()
        try {
            context.startActivity(Intent("OPEN_AIR_CONDITIONING")
                .setPackage("com.byd.airconditioning")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
            clearClimateRequests(prefs)
            throw IllegalStateException("API direta recusada ($reason) e não foi possível abrir o painel OEM: ${t.message}")
        }
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            val pending = when (kind) {
                "power" -> prefs.getInt(ClimateAccessibilityService.KEY_PENDING_POWER, -1) != -1
                "fan" -> prefs.getInt(ClimateAccessibilityService.KEY_PENDING_FAN, -1) != -1
                else -> prefs.getFloat(ClimateAccessibilityService.KEY_PENDING_TEMPERATURE, Float.NaN).isFinite()
            }
            if (!pending) return prefs.getString(ClimateAccessibilityService.KEY_RESULT, "Comando HVAC concluído.")
                ?: "Comando HVAC concluído."
        }
        return "Painel HVAC aberto; aguardando confirmação do controle OEM."
    }

    private fun clearClimateRequests(prefs: android.content.SharedPreferences) {
        prefs.edit().putInt(ClimateAccessibilityService.KEY_PENDING_POWER, -1)
            .putInt(ClimateAccessibilityService.KEY_PENDING_FAN, -1)
            .putFloat(ClimateAccessibilityService.KEY_PENDING_TEMPERATURE, Float.NaN).apply()
    }

    private fun isClimateAccessibilityEnabled(): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { info ->
            info.resolveInfo?.serviceInfo?.let { it.packageName == context.packageName && it.name == ClimateAccessibilityService::class.java.name } == true
        }
    }

    private fun windowFromPath(value: String): BydWindowControl.Window = when (value) {
        "driver_front" -> BydWindowControl.Window.DRIVER_FRONT
        "passenger_front" -> BydWindowControl.Window.PASSENGER_FRONT
        "driver_rear" -> BydWindowControl.Window.DRIVER_REAR
        "passenger_rear" -> BydWindowControl.Window.PASSENGER_REAR
        else -> error("Vidro desconhecido.")
    }

    private fun getOrCreateToken(): String {
        val prefs = LocalCarWebService.prefs(context)
        prefs.getString(KEY_TOKEN, null)?.let { return it }
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        val token = bytes.joinToString("") { "%02x".format(Locale.US, it) }
        prefs.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    private fun tokenMatches(value: String): Boolean {
        val expected = LocalCarWebService.prefs(context).getString(KEY_TOKEN, "") ?: ""
        return value.isNotBlank() && MessageDigest.isEqual(value.toByteArray(), expected.toByteArray())
    }

    private fun parseQuery(value: String?): Map<String, String> = value.orEmpty().split('&').mapNotNull { item ->
        val pair = item.split('=', limit = 2)
        if (pair.isEmpty() || pair[0].isEmpty()) null else URLDecoder.decode(pair[0], "UTF-8") to
            URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
    }.toMap()

    private fun json(status: Int, obj: JSONObject) = status to obj.toString().toByteArray(Charsets.UTF_8)
    private fun jsonError(message: String, key: String = "error") = JSONObject().put("ok", false).put(key, message).toString().toByteArray(Charsets.UTF_8)

    private fun respond(out: BufferedOutputStream, status: Int, body: ByteArray, mime: String = "application/json; charset=utf-8") {
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; 409 -> "Conflict"; 413 -> "Payload Too Large"; 431 -> "Request Header Fields Too Large"; else -> "Error" }
        val head = "HTTP/1.1 $status $reason\r\nContent-Type: $mime\r\nContent-Length: ${body.size}\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII)); out.write(body); out.flush()
    }

    private fun readLine(input: BufferedInputStream, max: Int): String? {
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() <= max) {
            val next = input.read()
            if (next < 0) return if (bytes.size() == 0) null else String(bytes.toByteArray(), Charsets.US_ASCII)
            if (next == 10) break
            if (next != 13) bytes.write(next)
        }
        if (bytes.size() > max) error("Linha HTTP acima do limite.")
        return String(bytes.toByteArray(), Charsets.US_ASCII)
    }

    override fun close() {
        accepting = false
        runCatching { socket?.close() }
        socket = null
        workers.shutdownNow()
        LocalCarWebService.prefs(context).edit().putBoolean(LocalCarWebService.KEY_RUNNING, false)
            .putString(LocalCarWebService.KEY_URL, "").apply()
    }

    private fun findWifiAddress(context: Context): String? {
        @Suppress("DEPRECATION")
        val ip = runCatching { (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo.ipAddress }.getOrDefault(0)
        if (ip != 0) return listOf(ip and 255, ip shr 8 and 255, ip shr 16 and 255, ip shr 24 and 255).joinToString(".")
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
        val addresses = interfaces.filter { it.isUp && !it.isLoopback }.sortedBy { if (it.name == "wlan0" || it.name.contains("wifi", true)) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
        return addresses.filterIsInstance<Inet4Address>().firstOrNull { it.isSiteLocalAddress && !it.isLoopbackAddress }?.hostAddress
    }

    private fun BydWindowControl.Window.slug(): String = when (this) {
        BydWindowControl.Window.DRIVER_FRONT -> "driver_front"
        BydWindowControl.Window.PASSENGER_FRONT -> "passenger_front"
        BydWindowControl.Window.DRIVER_REAR -> "driver_rear"
        BydWindowControl.Window.PASSENGER_REAR -> "passenger_rear"
    }

    companion object {
        private const val PORT = 8765
        private const val KEY_TOKEN = "pair_token"
        private const val TAG = "LocalCarHttpServer"
    }
}
