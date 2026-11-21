package com.example.data.model

enum class InterventionMode(
    val shortLabel: String,
    val subtitleLabel: String,
    val explanation: String
) {
    OVERLAY(
        shortLabel = "Gentle reminder",
        subtitleLabel = "Gentle reminder",
        explanation = "A short pause asks if you want to continue."
    ),
    BACK(
        shortLabel = "Take me back",
        subtitleLabel = "Take me back",
        explanation = "Returns to the previous screen."
    ),
    HOME_TAB(
        shortLabel = "Go to the home tab",
        subtitleLabel = "Go to the home tab",
        explanation = "Opens the app\u2019s main tab instead."
    )
}
