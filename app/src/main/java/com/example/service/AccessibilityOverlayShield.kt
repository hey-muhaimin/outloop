package com.example.service

import android.content.Context

/**
 * Facade managing the [TransparentPauseDimOverlay] window attachment and lifecycle
 * for [FeedSurgeonService].
 */
class AccessibilityOverlayShield(context: Context) {

    private val dimOverlay = TransparentPauseDimOverlay(context)

    fun isShowing(): Boolean = dimOverlay.isShowing()

    fun show(
        appName: String,
        feedLabel: String,
        useDarkTheme: Boolean = false,
        hapticsEnabled: Boolean = true,
        strictModeEnabled: Boolean = true,
        todayLoopsBroken: Int = 1,
        timeKeptText: String = "0 min",
        streakDays: Int = 0,
        preferredPauseMinutes: Int = 15,
        primaryUsefulActionLabel: String = "Open DMs",
        secondaryUsefulActionLabel: String = "Go to Search",
        usefulPartsSummary: String = "DMs and Search are still open.",
        triggerMode: OverlayTriggerMode = OverlayTriggerMode.FEED_GUARD,
        dailyUsedSeconds: Long = 0L,
        dailyLimitMinutes: Int = 0,
        dimAlpha: Float = OverlayPresentationConfig.DEFAULT_DIM_ALPHA,
        onExitFeed: () -> Unit,
        onAllowOnce: () -> Unit,
        onSnoozeMinutes: (Int) -> Unit = {},
        onAttachmentFailed: () -> Unit = {}
    ) {
        dimOverlay.show(
            config = OverlayPresentationConfig(
                appName = appName,
                feedLabel = feedLabel,
                triggerMode = triggerMode,
                dimAlpha = dimAlpha,
                useDarkTheme = useDarkTheme,
                hapticsEnabled = hapticsEnabled,
                strictModeEnabled = strictModeEnabled,
                todayLoopsBroken = todayLoopsBroken,
                timeKeptText = timeKeptText,
                streakDays = streakDays,
                dailyUsedSeconds = dailyUsedSeconds,
                dailyLimitMinutes = dailyLimitMinutes,
                preferredPauseMinutes = preferredPauseMinutes,
                primaryUsefulActionLabel = primaryUsefulActionLabel,
                secondaryUsefulActionLabel = secondaryUsefulActionLabel,
                usefulPartsSummary = usefulPartsSummary
            ),
            onExitFeed = onExitFeed,
            onAllowOnce = onAllowOnce,
            onSnoozeMinutes = onSnoozeMinutes,
            onAttachmentFailed = onAttachmentFailed
        )
    }

    fun dismiss() {
        dimOverlay.dismiss()
    }
}
