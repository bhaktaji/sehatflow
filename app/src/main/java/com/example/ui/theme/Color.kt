package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


// Primary & Secondary: Medical Teal & Deep Cyan
val Teal80 = Color(0xFF80CBC4)
val TealGrey80 = Color(0xFFB2DFDB)
val Cyan80 = Color(0xFF80DEEA)

val Teal40 = Color(0xFF00695C)
val TealGrey40 = Color(0xFF004D40)
val Cyan40 = Color(0xFF00838F)

// Clinical Accents & Alerts
val MintGreen = Color(0xFF2E7D32)
val MintGreenContainer = Color(0xFFE8F5E9)
val AmberWarning = Color(0xFFEF6C00)
val AmberWarningContainer = Color(0xFFFFF3E0)
val RedError = Color(0xFFC62828)
val RedErrorContainer = Color(0xFFFFEBEE)

// Surface & Card background
val SurfaceLight = Color(0xFFF7FAFA)
val SurfaceDark = Color(0xFF121A1A)
val CardBackgroundLight = Color(0xFFFFFFFF)
val CardBackgroundDark = Color(0xFF1E2828)

/**
 * Data class representing accessible, theme-aware banner colors
 * that guarantee high contrast in both Dark and Light themes.
 */
data class ThemedBannerColors(
    val container: Color,
    val content: Color,
    val icon: Color,
    val border: Color
)

@Composable
fun getWarningBannerColors(): ThemedBannerColors {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    return if (isDark) {
        ThemedBannerColors(
            container = Color(0xFF332005),
            content = Color(0xFFFEF3C7),
            icon = Color(0xFFFBBF24),
            border = Color(0xFF784A0E)
        )
    } else {
        ThemedBannerColors(
            container = Color(0xFFFFF3E0),
            content = Color(0xFF7C2D12),
            icon = Color(0xFFD97706),
            border = Color(0xFFFFCC80)
        )
    }
}

@Composable
fun getSuccessBannerColors(): ThemedBannerColors {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    return if (isDark) {
        ThemedBannerColors(
            container = Color(0xFF0D3319),
            content = Color(0xFFDCFCE7),
            icon = Color(0xFF4ADE80),
            border = Color(0xFF166534)
        )
    } else {
        ThemedBannerColors(
            container = Color(0xFFE8F5E9),
            content = Color(0xFF14532D),
            icon = Color(0xFF16A34A),
            border = Color(0xFFA5D6A7)
        )
    }
}

@Composable
fun getErrorBannerColors(): ThemedBannerColors {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    return if (isDark) {
        ThemedBannerColors(
            container = Color(0xFF3B1214),
            content = Color(0xFFFEE2E2),
            icon = Color(0xFFF87171),
            border = Color(0xFF991B1B)
        )
    } else {
        ThemedBannerColors(
            container = Color(0xFFFFEBEE),
            content = Color(0xFF7F1D1D),
            icon = Color(0xFFDC2626),
            border = Color(0xFFFFCDD2)
        )
    }
}

