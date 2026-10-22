package com.byd.carcontrol.inspector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.byd.carcontrol.MainActivity

class DiLinkInspectorService : Service() {
    private var controller: InspectorSessionController? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING, ACTION_START_EXPERIMENT -> {
                if (controller != null) return START_NOT_STICKY
                val mode = if (intent.action == ACTION_START_EXPERIMENT) InspectorMode.EXPERIMENT else InspectorMode.MONITORING
                val notification = buildNotification(mode)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                controller = InspectorSessionController(this)
                try {
                    val intensity = intent.getStringExtra(EXTRA_INTENSITY) ?: "NORMAL"
                    uiState = controller?.start(mode, intensity) ?: InspectorUiState()
                } catch (t: Throwable) {
                    uiState = InspectorUiState(lastError = "${t.javaClass.simpleName}: ${t.message}")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                }
            }
            ACTION_MARK -> {
                val text = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
                controller?.markAction(text)
            }
            ACTION_STOP -> {
                val active = controller
                if (active == null) {
                    uiState = InspectorUiState()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                } else {
                    active.stop("user_stopped") {
                        uiState = InspectorUiState()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                    controller = null
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        controller?.stop("service_destroyed")
        controller = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "DiLink Inspector", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Indica que a coleta passiva do DiLink Inspector está ativa."
            })
        }
    }

    private fun buildNotification(mode: InspectorMode): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("DiLink Inspector ativo")
            .setContentText(if (mode == InspectorMode.EXPERIMENT) "Experimento em coleta; marque as ações na tela do Inspector." else "Monitoramento passivo em execução.")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START_MONITORING = "com.byd.carcontrol.inspector.START_MONITORING"
        const val ACTION_START_EXPERIMENT = "com.byd.carcontrol.inspector.START_EXPERIMENT"
        const val ACTION_MARK = "com.byd.carcontrol.inspector.MARK_ACTION"
        const val ACTION_STOP = "com.byd.carcontrol.inspector.STOP"
        const val ACTION_STATE = "com.byd.carcontrol.inspector.STATE"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_INTENSITY = "intensity"
        private const val CHANNEL_ID = "dilink_inspector"
        private const val NOTIFICATION_ID = 7901

        @Volatile var uiState: InspectorUiState = InspectorUiState()
            internal set

        fun send(context: android.content.Context, action: String, description: String? = null, intensity: String? = null) {
            val intent = Intent(context, DiLinkInspectorService::class.java).setAction(action)
            if (description != null) intent.putExtra(EXTRA_DESCRIPTION, description)
            if (intensity != null) intent.putExtra(EXTRA_INTENSITY, intensity)
            if (action == ACTION_START_MONITORING || action == ACTION_START_EXPERIMENT) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
