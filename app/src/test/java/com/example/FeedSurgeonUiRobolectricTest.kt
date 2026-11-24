package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.local.AppPreferences
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
import com.example.ui.components.OutloopSideRail
import com.example.ui.screens.AppDetailScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.InsightsScreen
import com.example.ui.screens.ManageAppsScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PrivacyScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WalkthroughReplayScreen
import com.example.ui.theme.FeedSurgeonTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FeedSurgeonUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `onboarding flows cleanly through welcome preview choose apps allow access without skip and disclosure sheet`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        val installed = setOf("com.google.android.youtube")
        var acceptedDisclosure = false

        composeRule.setContent {
            FeedSurgeonTheme {
                OnboardingScreen(
                    rules = rules,
                    installedPackages = installed,
                    isAccessibilityEnabled = false,
                    isServiceConnected = false,
                    isBatteryOptimizationIgnored = false,
                    isNotificationPermissionGranted = true,
                    onRefreshProtectionHealth = {},
                    onToggleApp = { _, _ -> },
                    onAcceptDisclosure = { acceptedDisclosure = true },
                    onOpenPrivacyDetails = {},
                    onCompleteOnboarding = {},
                    disclosureAccepted = false
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Spot feeds.\nSkip the rest.").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_preview_card").assertIsDisplayed()
        composeRule.onNodeWithText("Tap to inspect view tags").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_skip").assertDoesNotExist()

        // Step across all 4 interactive carousel slides before reaching Choose apps
        composeRule.onNodeWithTag("carousel_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Break the loop\nwith Strict mode.").assertIsDisplayed()

        composeRule.onNodeWithTag("carousel_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Leave the feed,\nkeep the app.").assertIsDisplayed()

        composeRule.onNodeWithTag("carousel_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Zero content read.\n100% on-device.").assertIsDisplayed()

        composeRule.onNodeWithTag("onboarding_get_started").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Feeds to block").assertIsDisplayed()
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_skip").assertDoesNotExist()
        composeRule.onNodeWithText("Not installed").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_apps_continue").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Accessibility access").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_skip_protection").assertDoesNotExist()
        composeRule.onNodeWithTag("onboarding_access_back").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_open_settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Accessibility disclosure").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding_agree").performClick()
        composeRule.waitForIdle()
        assertTrue(acceptedDisclosure)
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

        composeRule.onNodeWithTag("status_pill").assertTextContains("Guard paused")
        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("home_resume_now_button"))
        composeRule.onNodeWithText("Guard paused").assertIsDisplayed()
        composeRule.onNodeWithTag("home_resume_now_button").performClick()
        assertTrue(resumed)
    }

    @Test
    fun `home screen opens Pause Guard dialog with Strict mode countdown and duration options`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        val installed = setOf("com.google.android.youtube")

        composeRule.setContent {
            FeedSurgeonTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(strictModeEnabled = true, preferredPauseMinutes = 15),
                        rules = rules,
                        installedPackages = installed,
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = ProtectionStatus.OPERATIONAL,
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

        composeRule.onNodeWithTag("home_pause_guard_fab").assertDoesNotExist()
        composeRule.onNodeWithTag("status_pill").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pause_guard_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Pause Guard?").assertIsDisplayed()
        composeRule.onNodeWithTag("pause_guard_duration_selector").assertIsDisplayed()
        composeRule.onNodeWithTag("pause_guard_keep_button").performClick()
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
        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("home_finish_setup_button"))
        composeRule.onNodeWithTag("home_finish_setup_button").assertIsDisplayed()
    }

    @Test
    fun `app detail screen updates mode and opens pause preview simulation with real metrics`() {
        val ytRule = FeedSurgeonDatabase.defaultBuiltInRules().first {
            it.packageName == "com.google.android.youtube"
        }
        var updatedMode: InterventionMode? = null

        composeRule.setContent {
            FeedSurgeonTheme {
                AppDetailScreen(
                    rule = ytRule,
                    reclaimedTodayText = "12 min",
                    streakDays = 3,
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
        composeRule.onNodeWithText("Loop\nbroken").assertIsDisplayed()
        composeRule.onNodeWithText("12 min").assertIsDisplayed()
        composeRule.onNodeWithText("3 days").assertIsDisplayed()
        composeRule.onNodeWithTag("sim_home_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sim_exit_button").assertIsDisplayed()
        composeRule.onNodeWithTag("sim_snooze_button").assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Exit").assertIsDisplayed()
        composeRule.onNodeWithText("Pause for 15 min").assertIsDisplayed()
    }

    @Test
    fun `insights screen switches between Today 7 days and 30 days ranges`() {
        val dayBars = listOf(
            DayBarStat("M", "Monday", "Nov 16", count = 3, reclaimedMinutes = 9, fraction = 0.5f, isToday = false),
            DayBarStat("T", "Tuesday", "Nov 17", count = 2, reclaimedMinutes = 6, fraction = 0.35f, isToday = false),
            DayBarStat("W", "Wednesday", "Nov 18", count = 5, reclaimedMinutes = 15, fraction = 0.8f, isToday = false),
            DayBarStat("T", "Thursday", "Nov 19", count = 4, reclaimedMinutes = 12, fraction = 0.65f, isToday = false),
            DayBarStat("F", "Friday", "Nov 20", count = 6, reclaimedMinutes = 18, fraction = 1f, isToday = false),
            DayBarStat("S", "Saturday", "Nov 21", count = 3, reclaimedMinutes = 9, fraction = 0.5f, isToday = false),
            DayBarStat("S", "Sunday", "Nov 22", count = 4, reclaimedMinutes = 12, fraction = 0.65f, isToday = true)
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
            AppBreakdownStat("com.instagram.android", "Instagram", "Instagram Reels", 27, 1f)
        )

        composeRule.setContent {
            FeedSurgeonTheme {
                InsightsScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(reclaimedSecondsPerCut = 180),
                        installedPackages = setOf("com.instagram.android"),
                        todayCutsCount = 4,
                        todayReclaimedText = "12 min",
                        todayHourBuckets = todayBuckets,
                        todayByAppBreakdown = appBreakdown,
                        weeklyCutsCount = 27,
                        weeklyDayBars = dayBars,
                        weeklyReclaimedText = "1 h 21 min",
                        byAppBreakdown = appBreakdown,
                        thirtyDayCutsCount = 72,
                        thirtyDayReclaimedText = "3 h 36 min",
                        thirtyDayWeekBuckets = thirtyBuckets,
                        thirtyDayByAppBreakdown = appBreakdown,
                        streakDays = 5,
                        bestStreakDays = 14
                    )
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Your attention had a little more room.").assertIsDisplayed()
        composeRule.onNodeWithText("~1 h 21 min").assertIsDisplayed()
        composeRule.onNodeWithText("3.9").assertIsDisplayed()
        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasText("Feeds interrupted"))
        composeRule.onNodeWithText("Feeds interrupted").assertIsDisplayed()

        composeRule.onNodeWithTag("insights_list")
            .performScrollToNode(hasTestTag("insights_range_selector"))
        composeRule.onNodeWithText("Today").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("~12 min").assertIsDisplayed()
        composeRule.onNodeWithText("5 days").assertIsDisplayed()

        composeRule.onNodeWithText("30 days").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("~3 h 36 min").assertIsDisplayed()
    }

    @Test
    fun `settings screen flattens tools omits theme row opens always open and clear history`() {
        var cleared = false
        var strictMode = true

        composeRule.setContent {
            FeedSurgeonTheme {
                SettingsScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(
                            strictModeEnabled = strictMode
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
                    onSetReclaimedSeconds = {},
                    onSetHapticFeedback = {},
                    onSetShowBanner = {},
                    onClearHistory = { cleared = true },
                    onShowToast = {},
                    onSetStrictModeEnabled = { strictMode = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_theme_row").assertDoesNotExist()
        composeRule.onNodeWithTag("settings_strict_mode_switch").performClick()
        composeRule.waitForIdle()
        assertFalse(strictMode)
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_accessibility_permission"))
        composeRule.onNodeWithText("Active").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_always_open"))
        composeRule.onNodeWithTag("settings_always_open").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Essential tools").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_always_open_done_button").performScrollTo().performClick()
        composeRule.waitForIdle()

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
                        preferences = AppPreferences(rulesetVersion = "v2026.11.21"),
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

        composeRule.onNodeWithText("Everything looks connected").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("v2026.11.21").performScrollTo().assertIsDisplayed()
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
        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("home_resume_quiet_hours_button"))
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
                    isAccessibilityEnabled = false,
                    isServiceConnected = false,
                    isBatteryOptimizationIgnored = false,
                    isNotificationPermissionGranted = true,
                    onRefreshProtectionHealth = {},
                    onToggleApp = { _, _ -> },
                    onAcceptDisclosure = {},
                    onOpenPrivacyDetails = {},
                    onCompleteOnboarding = {}
                )
            }
        }
        composeRule.waitForIdle()

        repeat(3) {
            composeRule.onNodeWithTag("carousel_next_button").performClick()
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithTag("onboarding_get_started").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Feeds to block").assertIsDisplayed()
        composeRule.onNodeWithText("0 selected").assertIsDisplayed()
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

    @Test
    fun `app detail screen displays daily usage and configures per-app limit via presets and custom stepper`() {
        var ytRule by mutableStateOf(
            FeedSurgeonDatabase.defaultBuiltInRules().first {
                it.packageName == "com.google.android.youtube"
            }
        )

        composeRule.setContent {
            FeedSurgeonTheme {
                AppDetailScreen(
                    rule = ytRule,
                    todayUsageSeconds = 18 * 60L,
                    onBack = {},
                    onToggleEnabled = {},
                    onSelectMode = {},
                    onChangeCooldown = {},
                    onChangeDailyLimitMinutes = { mins -> ytRule = ytRule.copy(dailyLimitMinutes = mins) },
                    onAllowOnce = {},
                    onSnoozeMinutes = {},
                    onResumeSnooze = {},
                    onShowToast = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("detail_daily_limit_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("detail_limit_chip_15").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(15, ytRule.dailyLimitMinutes)

        composeRule.onNodeWithTag("detail_limit_custom_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("custom_limit_plus_button").performClick()
        composeRule.onNodeWithTag("custom_limit_save_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(20, ytRule.dailyLimitMinutes)
    }

    @Test
    fun `home screen renders daily limit usage progress on guarded app rows`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules().map { rule ->
            if (rule.packageName == "com.google.android.youtube") {
                rule.copy(dailyLimitMinutes = 30)
            } else {
                rule
            }
        }

        composeRule.setContent {
            FeedSurgeonTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        rules = rules,
                        installedPackages = setOf("com.google.android.youtube"),
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = ProtectionStatus.OPERATIONAL,
                        activeInstalledCount = 1,
                        installedSupportedCount = 1,
                        todayUsageSecondsByPackage = mapOf("com.google.android.youtube" to 12 * 60L)
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

        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("home_app_row_com.google.android.youtube"))
        composeRule.onNodeWithText("Shorts blocked \u00B7 Search open \u00B7 12m/30m").assertIsDisplayed()
    }

    @Test
    fun `privacy screen renders on-device accessibility disclosure and optional rule checks`() {
        composeRule.setContent {
            FeedSurgeonTheme {
                PrivacyScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Everything stays on your phone").assertIsDisplayed()
        composeRule.onNodeWithText("Why accessibility access").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Persistent protection status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Optional rule checks").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `floating pill nav bar rapidly animates and switches across all tabs in order`() {
        var currentTab by mutableStateOf(BottomTab.HOME)
        composeRule.setContent {
            FeedSurgeonTheme {
                FloatingPillNavBar(
                    selectedTab = currentTab,
                    onTabSelected = { currentTab = it },
                    alerts = NavAlertBadges(homeNeedsAttention = false, settingsNeedsAttention = true)
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("floating_segmented_nav").assertIsDisplayed()

        // Switch to Insights
        composeRule.onNodeWithTag("nav_insights").performClick()
        composeRule.waitForIdle()
        assertEquals(BottomTab.INSIGHTS, currentTab)

        // Switch to Settings
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        assertEquals(BottomTab.SETTINGS, currentTab)

        // Switch back to Home
        composeRule.onNodeWithTag("nav_home").performClick()
        composeRule.waitForIdle()
        assertEquals(BottomTab.HOME, currentTab)
    }

    @Test
    fun `side rail renders on tablet with side_rail tag and handles tab selection`() {
        var selected by mutableStateOf(BottomTab.HOME)
        composeRule.setContent {
            FeedSurgeonTheme {
                OutloopSideRail(
                    selectedTab = selected,
                    onTabSelected = { selected = it },
                    alerts = NavAlertBadges(homeNeedsAttention = true)
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("side_rail").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_insights").performClick()
        composeRule.waitForIdle()
        assertEquals(BottomTab.INSIGHTS, selected)

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        assertEquals(BottomTab.SETTINGS, selected)
    }

    @Test
    fun `status pill tap opens OutloopPauseGuardDialog in strict mode with countdown`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        var pausedMinutes: Int? = null

        composeRule.setContent {
            FeedSurgeonTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(strictModeEnabled = true),
                        rules = rules,
                        installedPackages = setOf("com.google.android.youtube"),
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = ProtectionStatus.OPERATIONAL,
                        activeInstalledCount = 1,
                        installedSupportedCount = 1
                    ),
                    onStatusClick = {},
                    onSetupClick = {},
                    onManageAppsClick = {},
                    onToggleGlobalPause = {},
                    onPauseForMinutes = { pausedMinutes = it },
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

        // Tap the status pill to open Pause Guard dialog
        composeRule.onNodeWithTag("status_pill").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pause_guard_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("pause_guard_duration_selector").assertIsDisplayed()
        composeRule.onNodeWithText("Strict mode").assertIsDisplayed()

        // In strict mode, confirm button is initially disabled during countdown
        composeRule.onNodeWithTag("pause_guard_confirm_button").assertIsNotEnabled()

        // Tap Keep guarding to dismiss dialog cleanly
        composeRule.onNodeWithTag("pause_guard_keep_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pause_guard_dialog").assertDoesNotExist()
        assertEquals(null, pausedMinutes)
    }

    @Test
    fun `status pill tap opens OutloopPauseGuardDialog in non-strict mode and confirms pause`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        var pausedMinutes: Int? = null

        composeRule.setContent {
            FeedSurgeonTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(
                            strictModeEnabled = false,
                            preferredPauseMinutes = 15
                        ),
                        rules = rules,
                        installedPackages = setOf("com.google.android.youtube"),
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = ProtectionStatus.OPERATIONAL,
                        activeInstalledCount = 1,
                        installedSupportedCount = 1
                    ),
                    onStatusClick = {},
                    onSetupClick = {},
                    onManageAppsClick = {},
                    onToggleGlobalPause = {},
                    onPauseForMinutes = { pausedMinutes = it },
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

        composeRule.onNodeWithTag("status_pill").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pause_guard_dialog").assertIsDisplayed()
        // Non-strict mode enables confirm button immediately
        composeRule.onNodeWithTag("pause_guard_confirm_button").assertIsEnabled()
        composeRule.onNodeWithTag("pause_guard_confirm_button").performClick()
        composeRule.waitForIdle()

        assertEquals(15, pausedMinutes)
        composeRule.onNodeWithTag("pause_guard_dialog").assertDoesNotExist()
    }

    @Test
    fun `status pill tap when paused or needing setup invokes onStatusClick`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        var statusClickCount = 0

        var currentStatus by mutableStateOf(ProtectionStatus.PAUSED)

        composeRule.setContent {
            FeedSurgeonTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        rules = rules,
                        installedPackages = setOf("com.google.android.youtube"),
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = currentStatus,
                        activeInstalledCount = 1,
                        installedSupportedCount = 1
                    ),
                    onStatusClick = { statusClickCount++ },
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

        // Tapping status pill while paused invokes onStatusClick
        composeRule.onNodeWithTag("status_pill").performClick()
        composeRule.waitForIdle()
        assertEquals(1, statusClickCount)

        // Tapping status pill while setup needed invokes onStatusClick
        currentStatus = ProtectionStatus.PERMISSION_NEEDED
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("status_pill").performClick()
        composeRule.waitForIdle()
        assertEquals(2, statusClickCount)
    }

    @Test
    fun `settings screen configures global feed budget via presets and stepper`() {
        var savedGlobalBudget = 60

        composeRule.setContent {
            FeedSurgeonTheme {
                SettingsScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(globalFeedTimeBudgetMinutes = savedGlobalBudget),
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
                    onSetReclaimedSeconds = {},
                    onSetHapticFeedback = {},
                    onSetShowBanner = {},
                    onClearHistory = {},
                    onShowToast = {},
                    onSetGlobalFeedBudget = { savedGlobalBudget = it }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_global_feed_budget"))
        composeRule.onNodeWithTag("settings_global_feed_budget").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("global_budget_preset_90").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("global_budget_plus_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("global_budget_draft_value").assertTextContains("95 min")

        composeRule.onNodeWithTag("save_global_feed_budget_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(95, savedGlobalBudget)
    }

    @Test
    fun `home screen displays measured feed time and global budget reached badge`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()

        composeRule.setContent {
            FeedSurgeonTheme {
                HomeScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        rules = rules,
                        installedPackages = setOf("com.google.android.youtube", "com.instagram.android"),
                        isAccessibilityEnabled = true,
                        isServiceConnected = true,
                        protectionStatus = ProtectionStatus.OPERATIONAL,
                        activeInstalledCount = 2,
                        installedSupportedCount = 2,
                        todayCutsCount = 3,
                        todayReclaimedText = "9 min",
                        todayUsageSecondsByPackage = mapOf(
                            "com.google.android.youtube" to 40 * 60L,
                            "com.instagram.android" to 25 * 60L
                        ),
                        todayMeasuredFeedSeconds = 65 * 60L,
                        todayMeasuredFeedText = "1h 5m",
                        globalFeedBudgetMinutes = 60,
                        isGlobalFeedBudgetReached = true
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

        composeRule.onNodeWithTag("home_reclaimed_estimate_text").assertIsDisplayed()
        composeRule.onNodeWithTag("home_measured_feed_time").assertIsDisplayed()
        composeRule.onNodeWithText("Daily feed budget reached").assertIsDisplayed()
        composeRule.onNodeWithText("1h 5m / 60m").assertIsDisplayed()

        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasTestTag("home_app_row_com.google.android.youtube"))
        composeRule.onNodeWithText("Budget reached \u00B7 40m used").assertIsDisplayed()
    }

    @Test
    fun `insights screen displays measured feed time alongside est time reclaimed`() {
        composeRule.setContent {
            FeedSurgeonTheme {
                InsightsScreen(
                    uiState = MainUiState(
                        isInitialLoading = false,
                        preferences = AppPreferences(
                            reclaimedSecondsPerCut = 180,
                            globalFeedTimeBudgetMinutes = 60
                        ),
                        installedPackages = setOf("com.instagram.android"),
                        todayCutsCount = 4,
                        todayReclaimedText = "12 min",
                        weeklyCutsCount = 27,
                        weeklyReclaimedText = "1 h 21 min",
                        thirtyDayCutsCount = 72,
                        thirtyDayReclaimedText = "3 h 36 min",
                        todayMeasuredFeedSeconds = 22 * 60L,
                        todayMeasuredFeedText = "22m",
                        globalFeedBudgetMinutes = 60,
                        isGlobalFeedBudgetReached = false
                    )
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("~1 h 21 min").assertIsDisplayed()
        composeRule.onNodeWithTag("insights_measured_feed_time").assertIsDisplayed()
        composeRule.onNodeWithText("Time in feeds today \u00B7 of 60m budget").assertIsDisplayed()
        composeRule.onNodeWithText("22m").assertIsDisplayed()
    }

    @Test
    fun `onboarding carousel supports interactive tag inspection strict countdown exit dock and privacy toggle`() {
        val rules = FeedSurgeonDatabase.defaultBuiltInRules()
        var openedPrivacy = false

        composeRule.setContent {
            FeedSurgeonTheme {
                OnboardingScreen(
                    rules = rules,
                    installedPackages = setOf("com.google.android.youtube"),
                    isAccessibilityEnabled = false,
                    isServiceConnected = false,
                    isBatteryOptimizationIgnored = false,
                    isNotificationPermissionGranted = true,
                    onRefreshProtectionHealth = {},
                    onToggleApp = { _, _ -> },
                    onAcceptDisclosure = {},
                    onOpenPrivacyDetails = { openedPrivacy = true },
                    onCompleteOnboarding = {},
                    disclosureAccepted = false
                )
            }
        }
        composeRule.waitForIdle()

        // Slide 1: Tap-to-inspect view tags
        composeRule.onNodeWithTag("carousel_tag_inspector").assertTextContains("Paused by Guard", substring = true)
        composeRule.onNodeWithTag("carousel_inspect_dm_inbox").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carousel_tag_inspector").assertTextContains("Always open", substring = true)
        composeRule.onNodeWithTag("carousel_inspect_search_bar").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carousel_tag_inspector").assertTextContains("search_edit", substring = true)

        // Slide 2: Strict countdown preview + fast-forward + duration unlock
        composeRule.onNodeWithTag("carousel_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carousel_try_pause_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carousel_preview_break_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("carousel_countdown_fast_forward").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carousel_preview_break_button").assertIsEnabled()
        composeRule.onNodeWithTag("carousel_preview_break_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Temporary break simulated").assertIsDisplayed()

        // Slide 3: Home / Exit 2-row action dock simulator
        composeRule.onNodeWithTag("carousel_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carousel_dock_home").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Returned to Home tab").assertIsDisplayed()
        composeRule.onNodeWithTag("carousel_dock_exit").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Exited to phone Home").assertIsDisplayed()
        composeRule.onNodeWithTag("carousel_dock_reset").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Loop broken").assertIsDisplayed()

        // Slide 4: Privacy boundary switcher & details link
        composeRule.onNodeWithTag("carousel_next_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Selected app package").assertIsDisplayed()
        composeRule.onNodeWithText("Never read").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Messages & chats").assertIsDisplayed()
        composeRule.onNodeWithTag("carousel_open_privacy").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(openedPrivacy)
    }

    @Test
    fun `settings row opens non-destructive walkthrough replay screen and Done returns cleanly`() {
        var openedWalkthrough by mutableStateOf(false)
        var finishedReplay = false

        composeRule.setContent {
            FeedSurgeonTheme {
                if (!openedWalkthrough) {
                    SettingsScreen(
                        uiState = MainUiState(isInitialLoading = false),
                        onOpenManageApps = {},
                        onOpenPrivacy = {},
                        onOpenDiagnostics = {},
                        onOpenWalkthrough = { openedWalkthrough = true },
                        onSelectDefaultMode = {},
                        onSetReclaimedSeconds = {},
                        onSetHapticFeedback = {},
                        onSetShowBanner = {},
                        onClearHistory = {},
                        onShowToast = {}
                    )
                } else {
                    WalkthroughReplayScreen(
                        preferredPauseMinutes = 15,
                        onOpenPrivacyDetails = {},
                        onBack = { finishedReplay = true }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("settings_walkthrough"))
        composeRule.onNodeWithTag("settings_walkthrough").performClick()
        composeRule.waitForIdle()
        assertTrue(openedWalkthrough)

        composeRule.onNodeWithText("How Guard works").assertIsDisplayed()
        composeRule.onNodeWithTag("carousel_dot_3").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("carousel_replay_done").performClick()
        composeRule.waitForIdle()
        assertTrue(finishedReplay)
    }
}


