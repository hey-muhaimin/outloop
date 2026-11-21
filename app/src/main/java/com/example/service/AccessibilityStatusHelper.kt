package com.example.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object AccessibilityStatusHelper {
    fun isServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val expectedComponent = ComponentName(context, FeedSurgeonService::class.java)

        if (am != null) {
            val enabledServices = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            )
            val matched = enabledServices.any { info ->
                val resolve = info.resolveInfo?.serviceInfo ?: return@any false
                ComponentName(resolve.packageName, resolve.name) == expectedComponent
            }
            if (matched) return true
        }

        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val flatName = expectedComponent.flattenToString()
        val shortName = expectedComponent.flattenToShortString()
        return settingValue.split(':').any { entry ->
            entry.equals(flatName, ignoreCase = true) || entry.equals(shortName, ignoreCase = true)
        }
    }

    /**
     * Observes Accessibility service enablement changes in real time via both
     * AccessibilityManager state callbacks and Settings.Secure content notifications.
     */
    fun observeServiceEnabled(context: Context): Flow<Boolean> = callbackFlow {
        val appContext = context.applicationContext ?: context
        val am = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager

        fun emitCurrent() {
            trySend(isServiceEnabled(appContext))
        }

        emitCurrent()

        val stateListener = AccessibilityManager.AccessibilityStateChangeListener {
            emitCurrent()
        }
        runCatching { am?.addAccessibilityStateChangeListener(stateListener) }

        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                emitCurrent()
            }
        }
        runCatching {
            appContext.contentResolver?.registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                false,
                contentObserver
            )
        }

        awaitClose {
            runCatching { am?.removeAccessibilityStateChangeListener(stateListener) }
            runCatching { appContext.contentResolver?.unregisterContentObserver(contentObserver) }
        }
    }.distinctUntilChanged()

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
