package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteRulesetPayload(
    val version: String,
    val updatedAt: String = "",
    val apps: List<RemoteAppSelectorRule>
)

@JsonClass(generateAdapter = true)
data class RemoteAppSelectorRule(
    val packageName: String,
    val displayName: String = "",
    val feedLabel: String = "Short feed",
    val homeTabLabel: String = "Home",
    val viewIds: List<String> = emptyList(),
    val tabContentDescription: String = ""
)
