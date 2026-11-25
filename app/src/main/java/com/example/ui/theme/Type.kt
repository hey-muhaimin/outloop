package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans, FontWeight.ExtraBold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val ManropeFontFamily = FontFamily(
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold)
)

val OutloopTypography = Typography(
    // .page-title (26px Bold, -0.025em tracking, 1.18 line height)
    displayLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.65).sp
    ),
    // Hero KPI & sub-screen headline
    headlineLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.65).sp
    ),
    // Card KPI / Sheet / Overlay titles
    headlineMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.4).sp
    ),
    // Pattern title / card headline
    titleLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.25).sp
    ),
    // Primary button (.btn-mint / .btn-dark) & row title (14px SemiBold/Bold)
    titleMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.5.sp,
        lineHeight = 20.sp
    ),
    // Secondary item title
    titleSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    // Body paragraph
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    // Card paragraph / row value
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    // Footnote / secondary metadata (.app-meta / .si-sub)
    bodySmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    ),
    // Pill / segment / chip label
    labelLarge = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    // .eyebrow & .section-label (11px Bold, uppercase-ready tracking)
    labelMedium = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.8.sp
    ),
    // .nav-item label (11px SemiBold)
    labelSmall = TextStyle(
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)
