package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import com.example.service.LaunchableAppInfo
import com.example.ui.components.OutloopAppMonogramBadge
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopModalSheet
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopSectionHeader
import com.example.ui.components.OutloopSegmentedControl
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopSubScreenScaffold
import com.example.ui.components.OutloopSwitch
import com.example.ui.components.OutloopTonalCard
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme

@Composable
fun ManageAppsScreen(
    rules: List<TargetAppRuleEntity>,
    installedPackages: Set<String>,
    onToggleApp: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    launchableApps: List<LaunchableAppInfo> = emptyList(),
    onSaveCustomRule: (
        packageName: String,
        displayName: String,
        feedLabel: String,
        viewIds: List<String>,
        tabFallbackLabel: String,
        homeTabLabel: String,
        interventionMode: InterventionMode
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteCustomRule: (String) -> Unit = {},
    onShowToast: (String) -> Unit = {},
    hasScannedInstalledApps: Boolean = true
) {
    BackHandler(onBack = onBack)
    val colors = OutloopTheme.colors
    var showAddCustomSheet by rememberSaveable { mutableStateOf(false) }

    val installedRules = if (hasScannedInstalledApps) {
        rules.filter { it.packageName in installedPackages || !it.isBuiltIn }
    } else {
        emptyList()
    }
    val notInstalledRules = if (hasScannedInstalledApps) {
        rules.filter { it.packageName !in installedPackages && it.isBuiltIn }
    } else {
        rules
    }

    OutloopSubScreenScaffold(
        onBack = onBack,
        bottomBar = {
            OutloopPrimaryButton(text = "Done", onClick = onBack)
        }
    ) {
        OutloopStaggeredEntrance(index = 0) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Your apps",
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.ink
                )
                Text(
                    text = "Choose where Outloop steps in, or add a custom app rule.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.secondaryInk
                )
            }
        }

        OutloopStaggeredEntrance(index = 1) {
            Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing)) {
                OutloopSectionHeader(text = "On your phone")
                if (installedRules.isNotEmpty()) {
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        installedRules.forEachIndexed { idx, rule ->
                            if (idx > 0) OutloopHairlineDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = OutloopMetrics.RowMinHeight)
                                    .padding(
                                        horizontal = OutloopMetrics.ListRowPaddingHorizontal,
                                        vertical = OutloopMetrics.ListRowPaddingVertical
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutloopAppMonogramBadge(
                                    appName = rule.displayName,
                                    packageName = rule.packageName
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = buildString {
                                            append("Interrupts ${rule.feedLabel}")
                                            if (!rule.isBuiltIn) append(" \u00B7 Custom")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryInk
                                    )
                                }
                                if (!rule.isBuiltIn) {
                                    IconButton(
                                        onClick = {
                                            onDeleteCustomRule(rule.packageName)
                                            onShowToast("Removed ${rule.displayName}.")
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("delete_custom_rule_${rule.packageName}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = "Remove ${rule.displayName}",
                                            tint = colors.destructive
                                        )
                                    }
                                }
                                OutloopSwitch(
                                    checked = rule.enabled,
                                    onCheckedChange = { enabled -> onToggleApp(rule.packageName, enabled) },
                                    contentDescription = "Protect ${rule.displayName}"
                                )
                            }
                        }
                    }
                } else {
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.SubCardCornerRadius,
                        contentPadding = OutloopMetrics.CompactCardPadding
                    ) {
                        Text(
                            text = if (hasScannedInstalledApps) {
                                "No supported short-form video apps detected on this phone yet. Add a custom app below."
                            } else {
                                "Scanning installed apps\u2026"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryInk
                        )
                    }
                }
            }
        }

        OutloopStaggeredEntrance(index = 2) {
            OutloopSecondaryButton(
                text = "Add custom app",
                onClick = { showAddCustomSheet = true },
                modifier = Modifier.testTag("manage_apps_add_custom")
            )
        }

        if (notInstalledRules.isNotEmpty()) {
            OutloopStaggeredEntrance(index = 3) {
                Column(verticalArrangement = Arrangement.spacedBy(OutloopMetrics.ItemSpacing)) {
                    OutloopSectionHeader(text = "Not installed")
                    OutloopTonalCard(
                        cornerRadius = OutloopMetrics.CardCornerRadius,
                        contentPadding = PaddingValues(0.dp),
                        containerColor = colors.surfaceSubtle,
                        modifier = Modifier.alpha(0.75f)
                    ) {
                        notInstalledRules.forEachIndexed { idx, rule ->
                            if (idx > 0) OutloopHairlineDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = OutloopMetrics.RowMinHeight)
                                    .padding(
                                        horizontal = OutloopMetrics.ListRowPaddingHorizontal,
                                        vertical = OutloopMetrics.ListRowPaddingVertical
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutloopAppMonogramBadge(appName = rule.displayName)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.ink
                                    )
                                    Text(
                                        text = "${rule.feedLabel} \u00B7 not installed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.secondaryInk
                                    )
                                }
                                OutloopSwitch(
                                    checked = false,
                                    enabled = false,
                                    onCheckedChange = {},
                                    contentDescription = "${rule.displayName} not installed"
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddCustomSheet) {
        val existingPackages = remember(rules) { rules.map { it.packageName }.toSet() }
        val candidateApps = remember(launchableApps, existingPackages) {
            launchableApps.filter { it.packageName !in existingPackages }
        }
        AddCustomAppSheet(
            candidateApps = candidateApps,
            onSave = { pkg, display, feed, viewIds, tabFallback, homeTab, mode ->
                onSaveCustomRule(pkg, display, feed, viewIds, tabFallback, homeTab, mode)
                showAddCustomSheet = false
                onShowToast("Added custom protection for $display.")
            },
            onDismiss = { showAddCustomSheet = false }
        )
    }
}

@Composable
private fun AddCustomAppSheet(
    candidateApps: List<LaunchableAppInfo>,
    onSave: (
        packageName: String,
        displayName: String,
        feedLabel: String,
        viewIds: List<String>,
        tabFallbackLabel: String,
        homeTabLabel: String,
        interventionMode: InterventionMode
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = OutloopTheme.colors
    var packageName by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var feedLabel by rememberSaveable { mutableStateOf("Short feed") }
    var tabFallbackLabel by rememberSaveable { mutableStateOf("") }
    var homeTabLabel by rememberSaveable { mutableStateOf("Home") }
    var rawViewIds by rememberSaveable { mutableStateOf("") }
    var selectedMode by rememberSaveable { mutableStateOf(InterventionMode.OVERLAY) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    OutloopModalSheet(
        title = "Add custom app",
        onDismiss = onDismiss
    ) {
        if (candidateApps.isNotEmpty()) {
            Text(
                text = "Pick an installed app",
                style = MaterialTheme.typography.labelMedium,
                color = colors.secondaryInk
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                candidateApps.take(15).forEach { candidate ->
                    val isPicked = packageName == candidate.packageName
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isPicked) colors.primaryTint else colors.surfaceElevated,
                        border = BorderStroke(1.dp, colors.hairlineBorder),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                packageName = candidate.packageName
                                displayName = candidate.displayName
                                validationError = null
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            OutloopAppMonogramBadge(
                                appName = candidate.displayName,
                                packageName = candidate.packageName,
                                size = 22.dp,
                                cornerRadius = 6.dp
                            )
                            Text(
                                text = candidate.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isPicked) colors.onPrimaryTint else colors.ink
                            )
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = packageName,
            onValueChange = {
                packageName = it
                validationError = null
            },
            label = { Text("Package name (e.g. com.app.video)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_app_package_input")
        )

        OutlinedTextField(
            value = displayName,
            onValueChange = {
                displayName = it
                validationError = null
            },
            label = { Text("App name") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_app_name_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = feedLabel,
                onValueChange = {
                    feedLabel = it
                    validationError = null
                },
                label = { Text("Feed label") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("custom_app_feed_input")
            )
            OutlinedTextField(
                value = homeTabLabel,
                onValueChange = { homeTabLabel = it },
                label = { Text("Home tab") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = tabFallbackLabel,
            onValueChange = {
                tabFallbackLabel = it
                validationError = null
            },
            label = { Text("Tab description fallback (e.g. Shorts)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = rawViewIds,
            onValueChange = {
                rawViewIds = it
                validationError = null
            },
            label = { Text("View IDs (comma separated, optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutloopSegmentedControl(
            options = listOf(
                InterventionMode.OVERLAY to "Reminder",
                InterventionMode.BACK to "Back",
                InterventionMode.HOME_TAB to "Home"
            ),
            selected = selectedMode,
            onSelect = { selectedMode = it }
        )

        if (validationError != null) {
            Text(
                text = validationError!!,
                style = MaterialTheme.typography.bodySmall,
                color = colors.destructive
            )
        }

        Spacer(modifier = Modifier.height(2.dp))
        OutloopPrimaryButton(
            text = "Save custom rule",
            onClick = {
                val cleanPkg = packageName.trim()
                val cleanName = displayName.trim()
                val cleanFeed = feedLabel.trim()
                val parsedIds = rawViewIds.split(',', '|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val cleanTab = tabFallbackLabel.trim().ifEmpty { cleanFeed }
                when {
                    cleanPkg.isEmpty() || '.' !in cleanPkg ->
                        validationError = "Enter a valid Android package name (e.g. com.example.app)."
                    cleanName.isEmpty() ->
                        validationError = "Enter a display name for the app."
                    cleanFeed.isEmpty() ->
                        validationError = "Enter the feed name (e.g. Shorts, Reels, Watch)."
                    else ->
                        onSave(
                            cleanPkg,
                            cleanName,
                            cleanFeed,
                            parsedIds,
                            cleanTab,
                            homeTabLabel.trim().ifEmpty { "Home" },
                            selectedMode
                        )
                }
            },
            modifier = Modifier.testTag("save_custom_rule_button")
        )
    }
}
