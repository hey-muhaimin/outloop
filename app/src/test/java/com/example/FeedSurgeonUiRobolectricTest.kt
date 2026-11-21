package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.data.local.AppPreferences
import com.example.data.local.AppearanceMode
import com.example.data.local.FeedSurgeonDatabase
import com.example.data.model.InterventionMode
import com.example.data.model.ProtectionStatus
import com.example.ui.AppBreakdownStat
import com.example.ui.DayBarStat
import com.example.ui.InsightBucketStat
import com.example.ui.MainUiState
import com.example.ui.components.BottomTab
import com.example.ui.components.FloatingPillNavBar
import com.example.ui.components.NavAlertBadges
import com.example.ui.screens.AppDetailScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.ManageAppsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.FeedSurgeonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FeedSurgeonUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `onboarding flows cleanly through welcome privacy choose apps style and allow access`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        val installed = setOf("com.google.android.youtube")
        var selectedMode = InterventionMode.OVERLAY

        composeRule.setContent {
            FeedSurgeonTheme {
                OnboardingScreen(
                    rules = rules,
                    installedPackages = installed,
                    defaultMode = selectedMode,
                    isAccessibilityEnabled = false,
                    isServiceConnected = false,
                    isBatteryOptimizationIgnored = false,
                    isNotificationPermissionGranted = true,
                    onRefreshProtectionHealth = {},
                    onToggleApp = { _, _ -> },
                    onSelectDefaultMode = { selectedMode = it },
                    onAcceptDisclosure = {},
                    onDeclineDisclosure = {},
                    onOpenPrivacyDetails = {},
                    onCompleteOnboarding = {},
                    onGoToSettings = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Make room for what matters.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_get_started").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Here\u2019s exactly what Outloop can see.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_agree").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("On your phone").assertIsDisplayed()
        composeRule.onNodeWithText("Not installed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_apps_continue").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Recommended").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_mode_back").performClick()
        composeRule.waitForIdle()
        assertEquals(InterventionMode.BACK, selectedMode)

        composeRule.onNodeWithTag("onboarding_style_continue").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Waiting for permission").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_open_settings").assertIsDisplayed()
    }

    @Test
    fun `home screen renders paused state countdown and resume action`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        val installed = setOf("com.google.android.youtube", "com.instagram.android")
        val pausedUntil = System.currentTimeMillis() + 5 * 60_000L
        var resumed = false

        composeRule.setContent {
            FeedSurgeonTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        rules = rules,
                        installedPackages = installed,
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = ProtectionStatus.PAUSED,
                        activeInstalledCount = 2,
                        installedSupportedCount = 2,
                        isGloballyPaused = true,
                        globalPauseUntilMs = pausedUntil
                    ),
                    onStatusClick = {},
                    onSetupClick = {},
                    onManageAppsClick = {},
                    onToggleGlobalPause = {},
                    onPauseForMinutes = {},
                    onGlobalBreakExpired = { resumed = true },
                    onAppSnoozeExpired = {},
                    onRefreshBlockersClick = {},
                    onAppClick = {},
                    onToggleApp = { _, _ -> },
                    onShowEstimateToast = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("status_pill").assertTextContains("Taking a little space")
        composeRule.onAllNodesWithText("Taking a little space").assertCountEquals(2)
        composeRule.onAllNodesWithText("Taking a little space").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("home_resume_now_button").performClick()
        assertTrue(resumed)
    }

    @Test
    fun `home screen displays setup banner when permission needed`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        composeRule.setContent {
            FeedSurgeonTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        rules = rules,
                        installedPackages = setOf("com.google.android.youtube"),
                        isAccessibilityEnabled = false,
                        isServiceConnected = false,
                        protectionStatus = ProtectionStatus.PERMISSION_NEEDED,
                        activeInstalledCount = 1,
                        installedSupportedCount = 1
                    ),
                    onStatusClick = {},
                    onSetupClick = {},
                    onManageAppsClick = {},
                    onToggleGlobalPause = {},
                    onPauseForMinutes = {},
                    onGlobalBreakExpired = {},
                    onAppSnoozeExpired = {},
                    onRefreshBlockersClick = {},
                    onAppClick = {},
                    onToggleApp = { _, _ -> },
                    onShowEstimateToast = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("status_pill").assertTextContains("Setup needed")
        composeRule.onNodeWithTag("home_finish_setup_button").assertIsDisplayed()
    }

    @Test
    fun `app detail screen updates mode and opens pause preview simulation`() {
        val ytRule = FeedSurgeonDatabase.defaultBuiltInRules().first {
            it.packageName == "com.google.android.youtube"
        }
        var updatedMode: InterventionMode? = null

        composeRule.setContent {
            FeedSurgeonTheme {
                AppDetailScreen(
                    rule = ytRule,
                    onBack = {},
                    onToggleEnabled = {},
                    onSelectMode = { updatedMode = it },
                    onChangeCooldown = {},
                    onAllowOnce = {},
                    onSnoozeMinutes = {},
                    onResumeSnooze = {},
                    onShowToast = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("detail_mode_back").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(InterventionMode.BACK, updatedMode)

        composeRule.onNodeWithTag("detail_preview_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("simulation_pause_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Take a moment.").assertIsDisplayed()
    }

    @Test
    fun `insights screen switches between Today 7 days and 30 days ranges`() {
        val dayBars = listOf(
            DayBarStat("S", "Saturday", "Nov 14", count = 3, reclaimedMinutes = 9, fraction = 0.5f, isToday = false),
            DayBarStat("S", "Sunday", "Nov 15", count = 2, reclaimedMinutes = 6, fraction = 0.35f, isToday = false),
            DayBarStat("M", "Monday", "Nov 16", count = 5, reclaimedMinutes = 15, fraction = 0.8f, isToday = false),
            DayBarStat("T", "Tuesday", "Nov 17", count = 4, reclaimedMinutes = 12, fraction = 0.65f, isToday = false),
            DayBarStat("W", "Wednesday", "Nov 18", count = 6, reclaimedMinutes = 18, fraction = 1f, isToday = false),
            DayBarStat("T", "Thursday", "Nov 19", count = 3, reclaimedMinutes = 9, fraction = 0.5f, isToday = false),
            DayBarStat("F", "Friday", "Nov 20", count = 4, reclaimedMinutes = 12, fraction = 0.65f, isToday = true)
        )
        val todayBuckets = listOf(
            InsightBucketStat("Morning", "Morning (5am\u201312pm)", 2, 1f),
            InsightBucketStat("Afternoon", "Afternoon (12pm\u20135pm)", 1, 0.5f),
            InsightBucketStat("Evening", "Evening (5pm\u201310pm)", 1, 0.5f),
            InsightBucketStat("Night", "Night (10pm\u20135am)", 0, 0.08f)
        )
        val thirtyBuckets = listOf(
            InsightBucketStat("Wk 1", "3 weeks ago", 12, 0.5f),
            InsightBucketStat("Wk 2", "2 weeks ago", 18, 0.7f),
            InsightBucketStat("Wk 3", "Last week", 15, 0.6f),
            InsightBucketStat("Wk 4", "This week", 27, 1f)
        )
        val appBreakdown = listOf(
            AppBreakdownStat("com.google.android.youtube", "YouTube", "YouTube Shorts", 27, 1f)
        )

        composeRule.setContent {
            FeedSurgeonTheme {
                InsightsScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(reclaimedSecondsPerCut = 180),
                        installedPackages = setOf("com.google.android.youtube"),
                        todayCutsCount = 4,
                        todayReclaimedText = "~12 min",
                        todayHourBuckets = todayBuckets,
                        todayBusiestTimeLabel = "Morning",
                        todayByAppBreakdown = appBreakdown,
                        weeklyCutsCount = 27,
                        weeklyDayBars = dayBars,
                        weeklyReclaimedText = "~1 h 21 min",
                        byAppBreakdown = appBreakdown,
                        thirtyDayCutsCount = 72,
                        thirtyDayReclaimedText = "~3 h 36 min",
                        thirtyDayWeekBuckets = thirtyBuckets,
                        thirtyDayByAppBreakdown = appBreakdown
                    )
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Your attention had a little more room this week.").assertIsDisplayed()
        composeRule.onNodeWithText("Today").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Small moments of awareness add up.").assertIsDisplayed()
        composeRule.onNodeWithText("30 days").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("You paused before entering 72 feeds.").assertIsDisplayed()
    }

    @Test
    fun `settings screen shows appearance controls and clear history sheet`() {
        var appearance = AppearanceMode.LIGHT
        var cleared = false

        composeRule.setContent {
            FeedSurgeonTheme(appearanceMode = appearance) {
                SettingsScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(appearanceMode = appearance),
                        rules = FeedSurgeonDatabase.defaultBuiltInRules(),
                        installedPackages = setOf("com.google.android.youtube"),
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = ProtectionStatus.OPERATIONAL,
                        activeInstalledCount = 1,
                        installedSupportedCount = 1
                    ),
                    onOpenManageApps = {},
                    onOpenPrivacy = {},
                    onOpenDiagnostics = {},
                    onSelectDefaultMode = {},
                    onSelectAppearanceMode = { appearance = it },
                    onSetReclaimedSeconds = {},
                    onSetHapticFeedback = {},
                    onSetShowBanner = {},
                    onClearHistory = { cleared = true },
                    onShowToast = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_appearance_selector"))
        composeRule.onNodeWithText("Dark").performClick()
        assertEquals(AppearanceMode.DARK, appearance)

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_clear_history_button"))
        composeRule.onNodeWithTag("settings_clear_history_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_clear_history_button").performClick()
        assertTrue(cleared)
    }

    @Test
    fun `diagnostics screen renders live status and ruleset version`() {
        composeRule.setContent {
            FeedSurgeonTheme {
                DiagnosticsScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(rulesetVersion = "v2026.11.18"),
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = ProtectionStatus.OPERATIONAL,
                        activeInstalledCount = 3,
                        lastDetectionSummary = "YouTube Shorts \u00B7 Just now",
                        lastInterventionOutcomeSummary = "Completed, you went back"
                    ),
                    onRefreshHealth = {},
                    onCheckRulesNow = {},
                    onSetAutoUpdateRules = {},
                    onSetEventCaptureEnabled = {},
                    onShareEventLog = {},
                    onClearEventLog = {},
                    onShowToast = {},
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("v2026.11.18").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Everything looks connected").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `floating pill nav bar renders Home Insights Settings and switches tabs`() {
        var currentTab = BottomTab.HOME
        composeRule.setContent {
            FeedSurgeonTheme {
                FloatingPillNavBar(
                    selectedTab = currentTab,
                    onTabSelected = { currentTab = it },
                    alerts = NavAlertBadges(homeNeedsAttention = true)
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_home").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_insights").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_settings").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_insights").performClick()
        assertEquals(BottomTab.INSIGHTS, currentTab)
    }

    @Test
    @Config(qualifiers = "w840dp-h600dp")
    fun `navigation host uses side rail on wide tablet screens and navigates tabs`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        composeRule.setContent {
            FeedSurgeonTheme {
                OutloopNavigationHost(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(onboardingCompleted = true),
                        rules = rules,
                        installedPackages = setOf("com.google.android.youtube"),
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = ProtectionStatus.OPERATIONAL,
                        activeInstalledCount = 1,
                        installedSupportedCount = 1
                    )
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_insights").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Insights").assertCountEquals(2)
        composeRule.onNodeWithTag("insights_range_selector").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Settings").assertCountEquals(2)
        composeRule.onNodeWithTag("settings_list").assertIsDisplayed()
    }

    @Test
    fun `home screen shows quiet hours card and daily target progress`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        var resumedEarly = false

        composeRule.setContent {
            FeedSurgeonTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(
                            quietHoursEnabled = true,
                            dailyBudgetCuts = 10
                        ),
                        rules = rules,
                        installedPackages = setOf("com.google.android.youtube"),
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = ProtectionStatus.QUIET_HOURS,
                        isInQuietHours = true,
                        quietHoursEndLabel = "7:00 AM",
                        activeInstalledCount = 1,
                        installedSupportedCount = 1,
                        todayCutsCount = 4
                    ),
                    onStatusClick = {},
                    onSetupClick = {},
                    onManageAppsClick = {},
                    onToggleGlobalPause = {},
                    onPauseForMinutes = {},
                    onGlobalBreakExpired = {},
                    onAppSnoozeExpired = {},
                    onRefreshBlockersClick = {},
                    onAppClick = {},
                    onToggleApp = { _, _ -> },
                    onShowEstimateToast = {},
                    onResumeQuietHoursEarly = { resumedEarly = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("4 of 10 daily target").assertIsDisplayed()
        composeRule.onNodeWithTag("status_pill").assertTextContains("Quiet hours")
        composeRule.onNodeWithText("Quiet hours active").assertIsDisplayed()
        composeRule.onNodeWithTag("home_resume_quiet_hours_button").performClick()
        assertTrue(resumedEarly)
    }

    @Test
    fun `settings screen configures quiet hours and daily pause target`() {
        var quietEnabled = false
        var savedBudget = 0

        composeRule.setContent {
            FeedSurgeonTheme {
                SettingsScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(
                            quietHoursEnabled = quietEnabled,
                            dailyBudgetCuts = savedBudget
                        ),
                        rules = FeedSurgeonDatabase.defaultBuiltInRules(),
                        installedPackages = setOf("com.google.android.youtube"),
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = ProtectionStatus.OPERATIONAL,
                        activeInstalledCount = 1,
                        installedSupportedCount = 1
                    ),
                    onOpenManageApps = {},
                    onOpenPrivacy = {},
                    onOpenDiagnostics = {},
                    onSelectDefaultMode = {},
                    onSelectAppearanceMode = {},
                    onSetReclaimedSeconds = {},
                    onSetHapticFeedback = {},
                    onSetShowBanner = {},
                    onClearHistory = {},
                    onShowToast = {},
                    onSetQuietHours = { enabled, _, _ -> quietEnabled = enabled },
                    onSetDailyBudget = { savedBudget = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_quiet_hours"))
        composeRule.onNodeWithTag("settings_quiet_hours").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("quiet_hours_switch").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("save_quiet_hours_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(quietEnabled)

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_daily_budget"))
        composeRule.onNodeWithTag("settings_daily_budget").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("budget_option_10").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(10, savedBudget)
    }

    @Test
    fun `manage apps screen adds and deletes custom app rules`() {
        val initialRules = FeedSurgeonDatabase.defaultBuiltInRules()
        var savedPkg = ""
        var deletedPkg = ""

        composeRule.setContent {
            FeedSurgeonTheme {
                ManageAppsScreen(
                    rules = initialRules + FeedSurgeonDatabase.defaultBuiltInRules().first().copy(
                        packageName = "com.custom.demo",
                        displayName = "DemoVideo",
                        feedLabel = "Shorts",
                        isBuiltIn = false
                    ),
                    installedPackages = setOf("com.google.android.youtube", "com.custom.demo"),
                    onToggleApp = { _, _ -> },
                    onBack = {},
                    onSaveCustomRule = { pkg, _, _, _, _, _, _ -> savedPkg = pkg },
                    onDeleteCustomRule = { pkg -> deletedPkg = pkg }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("delete_custom_rule_com.custom.demo").performClick()
        assertEquals("com.custom.demo", deletedPkg)

        composeRule.onNodeWithTag("manage_apps_add_custom").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("custom_app_package_input").performScrollTo().performTextInput("com.new.feedapp")
        composeRule.onNodeWithTag("custom_app_name_input").performScrollTo().performTextInput("NewFeed")
        composeRule.onNodeWithTag("save_custom_rule_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("com.new.feedapp", savedPkg)
    }

    @Test
    fun `empty installed packages shows not installed separately without faking installed apps`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        composeRule.setContent {
            FeedSurgeonTheme {
                OnboardingScreen(
                    rules = rules,
                    installedPackages = emptySet(),
                    hasScannedInstalledApps = true,
                    defaultMode = InterventionMode.OVERLAY,
                    isAccessibilityEnabled = false,
                    isServiceConnected = false,
                    isBatteryOptimizationIgnored = false,
                    isNotificationPermissionGranted = true,
                    onRefreshProtectionHealth = {},
                    onToggleApp = { _, _ -> },
                    onSelectDefaultMode = {},
                    onAcceptDisclosure = {},
                    onDeclineDisclosure = {},
                    onOpenPrivacyDetails = {},
                    onCompleteOnboarding = {},
                    onGoToSettings = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("onboarding_get_started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("onboarding_agree").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("On your phone").assertIsDisplayed()
        composeRule.onNodeWithText("0 apps selected").assertIsDisplayed()
        composeRule.onNodeWithText("Not installed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Shorts \u00B7 not installed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `manage apps renders empty notice when no built-in apps installed`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        composeRule.setContent {
            FeedSurgeonTheme {
                ManageAppsScreen(
                    rules = rules,
                    installedPackages = emptySet(),
                    hasScannedInstalledApps = true,
                    onToggleApp = { _, _ -> },
                    onBack = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("On your phone").assertIsDisplayed()
        composeRule.onNodeWithText("No supported short-form video apps detected on this phone yet. Add a custom app below.").assertIsDisplayed()
        composeRule.onNodeWithText("Not installed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Shorts \u00B7 not installed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `settings screen gates accessibility permission behind prominent disclosure until accepted`() {
        var accepted = false
        composeRule.setContent {
            FeedSurgeonTheme {
                SettingsScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(
                            disclosureAccepted = false,
                            onboardingCompleted = true
                        ),
                        rules = FeedSurgeonDatabase.defaultBuiltInRules(),
                        installedPackages = setOf("com.google.android.youtube"),
                        isAccessibilityEnabled = false,
                        isServiceConnected = false,
                        protectionStatus = ProtectionStatus.PERMISSION_NEEDED,
                        activeInstalledCount = 1,
                        installedSupportedCount = 1
                    ),
                    onOpenManageApps = {},
                    onOpenPrivacy = {},
                    onOpenDiagnostics = {},
                    onSelectDefaultMode = {},
                    onSelectAppearanceMode = {},
                    onSetReclaimedSeconds = {},
                    onSetHapticFeedback = {},
                    onSetShowBanner = {},
                    onClearHistory = {},
                    onShowToast = {},
                    onAcceptDisclosure = { accepted = true }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_accessibility_permission"))
        composeRule.onNodeWithTag("settings_accessibility_permission").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Accessibility disclosure").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_disclosure_decline").performClick()
        composeRule.waitForIdle()
        assertEquals(false, accepted)

        composeRule.onNodeWithTag("settings_accessibility_permission").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_disclosure_agree").performClick()
        composeRule.waitForIdle()
        assertTrue(accepted)
    }
}
