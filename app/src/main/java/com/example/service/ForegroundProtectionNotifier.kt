package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R

object ForegroundProtectionNotifier {

    const val CHANNEL_ID = "outloop_protection_channel"
    const val NOTIFICATION_ID = 4107

    data class NotificationProtectionState(
        val activeInstalledCount: Int,
        val isPaused: Boolean = false,
        val isQuietHours: Boolean = false,
        val quietEndLabel: String = ""
    )

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "On-Device Protection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Outloop active in the background to step in when short-form feeds open."
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(context: Context, activeInstalledCount: Int): Notification =
        buildNotification(
            context = context,
            state = NotificationProtectionState(activeInstalledCount = activeInstalledCount)
        )

    fun buildNotification(
        context: Context,
        state: NotificationProtectionState
    ): Notification {
        ensureChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, contentText) = when {
            state.isPaused -> {
                "Outloop paused" to "Taking a little space \u00B7 tap Resume when ready"
            }
            state.isQuietHours -> {
                val endSuffix = if (state.quietEndLabel.isNotBlank()) " until ${state.quietEndLabel}" else ""
                "Quiet hours active" to "Outloop is stepped back$endSuffix"
            }
            state.activeInstalledCount > 0 -> {
                val noun = if (state.activeInstalledCount == 1) "app" else "apps"
                "Outloop active" to "Guarding ${state.activeInstalledCount} selected $noun on your device"
            }
            else -> {
                "Outloop ready" to "Tap to choose apps to guard"
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (state.isPaused || state.isQuietHours) {
            val resumeIntent = Intent(context, ProtectionActionReceiver::class.java).apply {
                action = ProtectionActionReceiver.ACTION_RESUME_PROTECTION
            }
            val resumePendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Resume", resumePendingIntent)
        } else if (state.activeInstalledCount > 0) {
            val pauseIntent = Intent(context, ProtectionActionReceiver::class.java).apply {
                action = ProtectionActionReceiver.ACTION_PAUSE_15_MIN
            }
            val pausePendingIntent = PendingIntent.getBroadcast(
                context,
                102,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Pause 15 min", pausePendingIntent)
        }

        return builder.build()
    }

    fun startForegroundProtection(
        service: Service,
        state: NotificationProtectionState
    ) {
        val notification = buildNotification(service, state)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(service, NOTIFICATION_ID, notification, serviceType)
        }
    }

    fun startForegroundProtection(service: Service, activeInstalledCount: Int) {
        startForegroundProtection(
            service = service,
            state = NotificationProtectionState(activeInstalledCount = activeInstalledCount)
        )
    }

    fun updateNotification(context: Context, state: NotificationProtectionState) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val notification = buildNotification(context, state)
        runCatching {
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    fun updateNotification(context: Context, activeInstalledCount: Int) {
        updateNotification(
            context = context,
            state = NotificationProtectionState(activeInstalledCount = activeInstalledCount)
        )
    }

    fun stopForegroundProtection(service: Service) {
        runCatching {
            ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }
}
