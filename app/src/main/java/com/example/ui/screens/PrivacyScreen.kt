package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.components.OutloopIconBadge
import com.example.ui.components.OutloopInsetWell
import com.example.ui.components.OutloopStaggeredEntrance
import com.example.ui.components.OutloopSubScreenScaffold
import com.example.ui.components.OutloopTonalCard
import com.example.ui.theme.OutloopMetrics
import com.example.ui.theme.OutloopTheme

@Composable
fun PrivacyScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val colors = OutloopTheme.colors

    OutloopSubScreenScaffold(onBack = onBack) {
        OutloopStaggeredEntrance(index = 0) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Privacy",
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.ink
                )
                Text(
                    text = "What Outloop can see, and what it keeps.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.secondaryInk
                )
            }
        }

        OutloopStaggeredEntrance(index = 1) {
            OutloopTonalCard(
                cornerRadius = OutloopMetrics.CardCornerRadius,
                contentPadding = PaddingValues(18.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutloopIconBadge(
                        icon = Icons.Outlined.Lock,
                        containerColor = colors.heroDark,
                        tint = colors.onPrimaryTint
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Everything stays on your phone",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "No accounts, no analytics servers, and no personal content leaves this device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.secondaryInk
                        )
                    }
                }
            }
        }

        OutloopStaggeredEntrance(index = 2) {
            PrivacyDetailCard(
                icon = Icons.Outlined.AccessibilityNew,
                title = "Why accessibility access",
                body = "Android only lets an app recognise what\u2019s on screen through its Accessibility Service. Outloop uses it to tell when a Shorts, Reels or For You feed is open."
            )
        }
        OutloopStaggeredEntrance(index = 3) {
            PrivacyDetailCard(
                icon = Icons.Outlined.Visibility,
                title = "What it inspects",
                body = "Technical screen labels in the apps you choose. It doesn\u2019t read your messages, passwords or videos."
            )
        }
        OutloopStaggeredEntrance(index = 4) {
            PrivacyDetailCard(
                icon = Icons.Outlined.PhoneAndroid,
                title = "What stays on your phone",
                body = "Feed detection and your daily counts stay strictly on this device."
            )
        }
        OutloopStaggeredEntrance(index = 5) {
            PrivacyDetailCard(
                icon = Icons.Outlined.GridView,
                title = "What is stored",
                body = "Your app choices, preferences and local interruption counts. Troubleshooting logs remain off unless you turn them on in Diagnostics."
            )
        }
        OutloopStaggeredEntrance(index = 6) {
            PrivacyDetailCard(
                icon = Icons.Outlined.Tune,
                title = "How to turn it off",
                body = "Android Settings \u203A Accessibility \u203A Outloop, at any time."
            )
        }

        OutloopStaggeredEntrance(index = 7) {
            OutloopInsetWell(
                containerColor = colors.surfaceSubtle,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "You\u2019re in control. Pause Guard from Today or export your local history whenever you want.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink
                )
            }
        }
    }
}

@Composable
private fun PrivacyDetailCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    val colors = OutloopTheme.colors
    OutloopTonalCard(
        cornerRadius = OutloopMetrics.CardCornerRadius,
        contentPadding = PaddingValues(18.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutloopIconBadge(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.secondaryInk
                )
            }
        }
    }
}
