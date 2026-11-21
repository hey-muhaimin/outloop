package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.FeedSurgeonApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProtectionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val app = context.applicationContext as? FeedSurgeonApp ?: return
        val pendingResult: PendingResult? = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_PAUSE_15_MIN -> {
                        app.interventionRepository.pauseGloballyForMinutes(15)
                    }
                    ACTION_RESUME_PROTECTION -> {
                        app.interventionRepository.resumeGlobalProtection()
                        val prefs = app.settingsDataStore.preferencesFlow.first()
                        if (prefs.isInQuietHours()) {
                            app.interventionRepository.resumeFromQuietHoursEarly()
                        }
                    }
                }

                val updatedPrefs = app.settingsDataStore.preferencesFlow.first()
                val rules = app.database.feedSurgeonDao().getAllRulesSnapshot()
                val installed = InstalledAppDetector.getInstalledPackages(
                    context,
                    rules.map { it.packageName }
                )
                val activeInstalledCount = rules.count { it.enabled && it.packageName in installed }
                val nowMs = System.currentTimeMillis()

                ForegroundProtectionNotifier.updateNotification(
                    context = context,
                    state = ForegroundProtectionNotifier.NotificationProtectionState(
                        activeInstalledCount = activeInstalledCount,
                        isPaused = updatedPrefs.isGloballyPaused(nowMs),
                        isQuietHours = updatedPrefs.isInQuietHours(nowMs),
                        quietEndLabel = com.example.data.local.AppPreferences.formatMinutesOfDay(
                            updatedPrefs.quietEndMinutes
                        )
                    )
                )
            } catch (t: Throwable) {
                android.util.Log.e("ProtectionActionReceiver", "Failed to process notification action", t)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE_15_MIN = "com.example.action.PAUSE_15_MIN"
        const val ACTION_RESUME_PROTECTION = "com.example.action.RESUME_PROTECTION"
    }
}
