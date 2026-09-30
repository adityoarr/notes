package com.adityoarr.securevaultnotes.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// ============================================================================
// SECURE VAULT THEME
// - 100% Dark mode, tanpa varian Light (keamanan & konsistensi visual).
// - Warna permukaan = hitam pekat OLED (#000000).
// - Tipografi default Material3, akan disetel lebih lanjut saat Batch UI.
// ============================================================================

private val SecureVaultDarkColorScheme = darkColorScheme(
    primary = AccentWhite,
    onPrimary = BlackPure,
    secondary = TextSecondary,
    onSecondary = BlackPure,
    tertiary = AccentAmber,

    background = BlackPure,           // Latar belakang aplikasi
    onBackground = TextPrimary,

    surface = BlackPure,              // Latar Card, Dialog, BottomSheet
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,     // Card note
    onSurfaceVariant = TextSecondary,

    outline = BorderSubtle,
    outlineVariant = BorderSubtle,

    error = AccentRed,
    onError = BlackPure,
)

@Composable
fun SecureVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SecureVaultDarkColorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}