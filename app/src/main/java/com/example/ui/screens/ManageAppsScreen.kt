package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TargetAppRuleEntity
import com.example.data.model.InterventionMode
import com.example.service.LaunchableAppInfo
import com.example.ui.components.OutloopAppMonogramBadge
import com.example.ui.components.OutloopAppPastelBadge
import com.example.ui.components.OutloopHairlineDivider
import com.example.ui.components.OutloopMintButton
import com.example.ui.components.OutloopModalSheet
import com.example.ui.components.OutloopPrimaryButton
import com.example.ui.components.OutloopSecondaryButton
import com.example.ui.components.OutloopSegmentedControl
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopStatusPill
import com.example.ui.components.OutloopSubScreenScaffold
import com.example.ui.components.OutloopSwitch
import com.example.ui.components.OutloopTonalCard
import com.example.ui.components.PillTone
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
    hasScannedInstalledApps: Boolean = true,
    rulesetVersion: String = "",
    onAppClick: (String) -> Unit = {}
) {
    BackHandler(onBack = onBack)
    val colors = OutloopTheme.colors
    var showAddCustomSheet by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

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
    val normalizedQuery = searchQuery.trim().lowercase()
    val filteredInstalledRules = remember(installedRules, normalizedQuery) {
        if (normalizedQuery.isEmpty()) installedRules
        else installedRules.filter {
            it.displayName.lowercase().contains(normalizedQuery) ||
                it.packageName.lowercase().contains(normalizedQuery) ||
                it.feedLabel.lowercase().contains(normalizedQuery)
        }
    }
    val filteredNotInstalledRules = remember(notInstalledRules, normalizedQuery) {
        if (normalizedQuery.isEmpty()) notInstalledRules
        else notInstalledRules.filter {
            it.displayName.lowercase().contains(normalizedQuery) ||
                it.packageName.lowercase().contains(normalizedQuery) ||
                it.feedLabel.lowercase().contains(normalizedQuery)
        }
    }
    val selectedCount = installedRules.count { it.enabled }
    val supportedBuiltInCount = rules.count { it.isBuiltIn }

    OutloopSubScreenScaffold(
        onBack = onBack,
        trailing = {
            OutloopStatusPill(
                text = "$supportedBuiltInCount Supported Apps",
                tone = PillTone.NEUTRAL
            )
        },
        bottomBar = {
            OutloopMintButton(
                text = "Save & Return",
                onClick = onBack,
                modifier = Modifier.testTag("manage_apps_done_button")
            )
        }
    ) {
        OutloopStaggeredEntrance(index = 0) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (rulesetVersion.isNotBlank()) {
                        Text(
                            text = "RULESET ${rulesetVersion.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.mutedInk
                        )
                    }
                    Text(
                        text = "Guarded apps",
                        style = MaterialTheme.typography.displayLarge,
                        color = colors.ink
                    )
                }

                val searchShape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .shadow(2.dp, searchShape, ambientColor = colors.dockShadow, spotColor = colors.dockShadow)
                        .clip(searchShape)
                        .background(colors.surfaceCard)
                        .border(1.dp, colors.hairlineBorder, searchShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = colors.mutedInk,
                        modifier = Modifier.size(16.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Filter installed apps\u2026",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.mutedInk
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = colors.ink,
                                fontWeight = FontWeight.Medium
                            ),
                            cursorBrush = SolidColor(colors.heroDark),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manage_apps_search_input")
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Clear filter",
                                tint = colors.mutedInk,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        OutloopStaggeredEntrance(index = 1) {
            OutloopTonalCard(
                cornerRadius = OutloopMetrics.CardCornerRadius,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "On your phone",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.secondaryInk
                    )
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        color = colors.ink
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (filteredInstalledRules.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        filteredInstalledRules.forEachIndexed { idx, rule ->
                            if (idx > 0) OutloopHairlineDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 58.dp)
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onAppClick(rule.packageName) }
                                        .padding(vertical = 4.dp)
                                        .testTag("manage_app_row_${rule.packageName}"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutloopAppPastelBadge(
                                        packageName = rule.packageName,
                                        displayName = rule.displayName
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rule.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = colors.ink
                                        )
                                        Text(
                                            text = buildString {
                                                append("${rule.feedLabel} \u00B7 ${rule.homeTabLabel} open")
                                                if (rule.hasDailyLimit()) {
                                                    append(" \u00B7 ${rule.dailyLimitMinutes}m/day")
                                                }
                                                if (!rule.isBuiltIn) append(" \u00B7 Custom")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.mutedInk,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
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
                                    onCheckedChange = { enabled ->
                                        onToggleApp(rule.packageName, enabled)
                                    },
                                    contentDescription = "Guard ${rule.displayName}",
                                    modifier = Modifier.testTag("manage_app_switch_${rule.packageName}")
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = when {
                            !hasScannedInstalledApps -> "Scanning installed apps\u2026"
                            installedRules.isNotEmpty() && normalizedQuery.isNotEmpty() ->
                                "No installed apps match \u201C${searchQuery.trim()}\u201D."
                            else ->
                                "No supported short-form video apps detected on this phone yet. Add a custom app below."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.secondaryInk
                    )
                }
            }
        }

        OutloopStaggeredEntrance(index = 3) {
            OutloopSecondaryButton(
                text = "Add custom app",
                onClick = { showAddCustomSheet = true },
                modifier = Modifier.testTag("manage_apps_add_custom")
            )
        }

        if (filteredNotInstalledRules.isNotEmpty()) {
            OutloopStaggeredEntrance(index = 4) {
                OutloopTonalCard(
                    cornerRadius = OutloopMetrics.CardCornerRadius,
                    contentPadding = PaddingValues(18.dp),
                    containerColor = colors.surfaceSubtle,
                    modifier = Modifier.alpha(0.78f)
                ) {
                    Text(
                        text = "Not installed",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.secondaryInk
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    filteredNotInstalledRules.forEachIndexed { idx, rule ->
                        if (idx > 0) OutloopHairlineDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutloopAppPastelBadge(
                                packageName = rule.packageName,
                                displayName = rule.displayName
                            )
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
                onShowToast("Added custom guard for $display.")
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
                        color = if (isPicked) colors.heroDark else colors.surfaceElevated,
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
                                cornerRadius = 6.dp,
                                preferDarkMonogram = true
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
