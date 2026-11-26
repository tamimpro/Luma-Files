package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LumaDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF001F26),
    primaryContainer = Color(0xFF003642),
    onPrimaryContainer = Color(0xFFB8F4FF),
    secondary = EmeraldVault,
    onSecondary = Color(0xFF002114),
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = AmberAdvanced,
    onTertiary = Color(0xFF281800),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFDE68A),
    error = CoralError,
    onError = Color.White,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFECACA),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = SlateSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderSlateDark
)

private val LumaLightColorScheme = lightColorScheme(
    primary = DeepCobalt,
    onPrimary = Color.White,
    primaryContainer = DeepCobaltContainer,
    onPrimaryContainer = Color(0xFF001E2F),
    secondary = EmeraldLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = AmberLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    error = CoralErrorLight,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = AlabasterBg,
    onBackground = TextPrimaryLight,
    surface = PureWhiteSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SoftSlateSurface,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderSlateLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LumaDarkColorScheme else LumaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
