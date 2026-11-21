package com.example.service

import com.example.data.local.TargetAppRuleEntity

/**
 * Tracks foreground package transitions from Accessibility [TYPE_WINDOW_STATE_CHANGED] events
 * so Outloop immediately detects when a user enters or leaves a monitored short-form video app
 * without inspecting window nodes or logging unmonitored apps.
 */
class ForegroundPackageMonitor {

    sealed interface Transition {
        data class EnteredMonitoredApp(
            val previousPackage: String?,
            val packageName: String,
            val rule: TargetAppRuleEntity,
            val activityClassName: String?
        ) : Transition

        data class StayedInMonitoredApp(
            val packageName: String,
            val rule: TargetAppRuleEntity,
            val activityClassName: String?
        ) : Transition

        data class ExitedMonitoredApp(
            val previousPackage: String,
            val newPackage: String
        ) : Transition

        data object IgnoredTransientWindow : Transition
        data object UnmonitoredApp : Transition
    }

    @Volatile
    var currentForegroundPackage: String? = null
        private set

    @Volatile
    var currentForegroundClassName: String? = null
        private set

    @Synchronized
    fun onWindowStateChanged(
        packageName: String?,
        className: String?,
        rulesByPackage: Map<String, TargetAppRuleEntity>,
        hostPackageName: String,
        isOverlayShowing: Boolean
    ): Transition {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isEmpty() || pkg in TRANSIENT_SYSTEM_PACKAGES) {
            return Transition.IgnoredTransientWindow
        }

        if (pkg == hostPackageName && isOverlayShowing) {
            return Transition.IgnoredTransientWindow
        }

        val previous = currentForegroundPackage
        val rule = rulesByPackage[pkg]

        currentForegroundPackage = pkg
        currentForegroundClassName = className

        return if (rule != null) {
            if (previous != pkg) {
                Transition.EnteredMonitoredApp(
                    previousPackage = previous,
                    packageName = pkg,
                    rule = rule,
                    activityClassName = className
                )
            } else {
                Transition.StayedInMonitoredApp(
                    packageName = pkg,
                    rule = rule,
                    activityClassName = className
                )
            }
        } else {
            if (previous != null && previous in rulesByPackage) {
                Transition.ExitedMonitoredApp(
                    previousPackage = previous,
                    newPackage = pkg
                )
            } else {
                Transition.UnmonitoredApp
            }
        }
    }

    @Synchronized
    fun reset() {
        currentForegroundPackage = null
        currentForegroundClassName = null
    }

    companion object {
        private val TRANSIENT_SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "android",
            "com.google.android.inputmethod.latin",
            "com.samsung.android.honeyboard"
        )
    }
}
