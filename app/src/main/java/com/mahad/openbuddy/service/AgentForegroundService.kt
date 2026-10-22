package com.mahad.openbuddy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mahad.openbuddy.MainActivity
import com.mahad.openbuddy.R

/**
 * Phase 3/5 — keeps long-running agent work (tool loops, Veo polling,
 * hands-free voice sessions) alive while the user switches apps.
 * Android cannot guarantee a process runs forever (OEM battery killers exist),
 * but a foreground service is the strongest guarantee the platform offers.
 */
class AgentForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Go foreground at the earliest possible moment: the platform gives
        // startForegroundService() callers only ~5 seconds to see a matching
        // startForeground() before it kills the app.
        ensureChannel()
        goForeground("Agent is working…")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val statusText = intent?.getStringExtra(EXTRA_STATUS) ?: "Agent is working…"
        ensureChannel()
        goForeground(statusText)
        return START_NOT_STICKY
    }

    /**
     * Calls startForeground with the dataSync type, falling back to the
     * untyped call if the platform refuses. Never swallow a failure silently:
     * a service that never goes foreground gets the whole app killed with
     * ForegroundServiceDidNotStartInTimeException.
     */
    private fun goForeground(statusText: String) {
        val notification = buildNotification(statusText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                com.mahad.openbuddy.data.CrashReporter.record(this, e2)
            }
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Agent activity", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while Open Buddy's agent is working in the background."
                }
            )
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Open Buddy agent")
            .setContentText(statusText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "agent_activity"
        private const val NOTIFICATION_ID = 4201
        private const val EXTRA_STATUS = "extra_status"

        fun start(context: Context, status: String = "Agent is working…") {
            try {
                val intent = Intent(context, AgentForegroundService::class.java).apply {
                    putExtra(EXTRA_STATUS, status)
                }
                context.startForegroundService(intent)
            } catch (_: Exception) { /* see onStartCommand note */ }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, AgentForegroundService::class.java))
            } catch (_: Exception) { /* ignore */ }
        }
    }
}
