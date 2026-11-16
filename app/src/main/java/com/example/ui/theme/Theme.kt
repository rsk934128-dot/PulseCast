package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = OnElectricCyanContainer,
    secondary = IndigoGlow,
    onSecondary = Color.White,
    secondaryContainer = IndigoContainer,
    onSecondaryContainer = InkLight,
    tertiary = CoralPulse,
    onTertiary = OnCoralPulse,
    tertiaryContainer = CoralPulseContainer,
    onTertiaryContainer = OnCoralPulseContainer,
    background = ObsidianBackground,
    onBackground = InkLight,
    surface = ObsidianSurface,
    onSurface = InkLight,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = MutedSlate,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    outline = SubtleOutlineDark,
    error = CoralPulse,
    onError = Color.White
)

private val StudioLightColorScheme = lightColorScheme(
    primary = CobaltPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = IndigoGlow,
    onSecondary = Color.White,
    tertiary = CoralPrimaryLight,
    onTertiary = Color.White,
    tertiaryContainer = CoralContainerLight,
    onTertiaryContainer = Color(0xFF4C0519),
    background = DaylightBackground,
    onBackground = InkDark,
    surface = DaylightSurface,
    onSurface = InkDark,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = MutedInk,
    outline = SubtleOutlineLight,
    error = CoralPrimaryLight,
    onError = Color.White
)

@Composable
fun PulseCastTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> StudioDarkColorScheme
        else -> StudioLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PulseCastTypography,
        content = content
    )
}
