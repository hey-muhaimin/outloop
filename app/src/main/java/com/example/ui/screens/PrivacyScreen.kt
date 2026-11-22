package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.components.OutloopBackButton
import com.example.ui.components.OutloopBentoCard
import com.example.ui.components.OutloopIconBadge
import com.example.ui.theme.OutloopTheme

@Composable
fun PrivacyScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val colors = OutloopTheme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 20.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutloopBackButton(onClick = onBack)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
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

                PrivacyDetailCard(
                    icon = Icons.Outlined.AccessibilityNew,
                    title = "Why accessibility access",
                    body = "Android only lets an app recognise what\u2019s on screen through its Accessibility Service. Outloop uses it to tell when a Shorts, Reels or Watch feed is open."
                )
                PrivacyDetailCard(
                    icon = Icons.Outlined.Visibility,
                    title = "What it inspects",
                    body = "Technical screen labels in the apps you choose. It doesn\u2019t read your messages, passwords or videos."
                )
                PrivacyDetailCard(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "What stays on your phone",
                    body = "Feed detection and your daily counts stay strictly on this device."
                )
                PrivacyDetailCard(
                    icon = Icons.Outlined.GridView,
                    title = "What is stored",
                    body = "Your app choices, preferences and local interruption counts. Troubleshooting logs remain off unless you turn them on in Diagnostics."
                )
                PrivacyDetailCard(
                    icon = Icons.Outlined.Tune,
                    title = "How to turn it off",
                    body = "Android Settings \u203A Accessibility \u203A Outloop, at any time."
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
    OutloopBentoCard(cornerRadius = 22.dp, contentPadding = PaddingValues(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutloopIconBadge(icon = icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
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
