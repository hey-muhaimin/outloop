package com.example.data.model

data class InspectedNode(
    val depth: Int,
    val className: String,
    val viewId: String?,
    val contentDescription: String?,
    val isSelected: Boolean,
    val isMatched: Boolean = false
)

data class LiveTreeSnapshot(
    val packageName: String,
    val appDisplayName: String,
    val capturedAtMs: Long,
    val isDiagnosticSample: Boolean,
    val nodes: List<InspectedNode>
)

data class InterventionToastState(
    val eventId: Long,
    val packageName: String,
    val feedLabel: String,
    val message: String,
    val timestampMs: Long
)
