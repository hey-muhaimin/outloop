package com.example.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager

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

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
