package dev.agentbrowser.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Neutral graphite chrome with one teal accent. Layers are told apart by container tone, not shadows.
private val Light = lightColorScheme(
    primary = Color(0xFF0F6B5C), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFEDE5), onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF444846), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8EAE6), onSecondaryContainer = Color(0xFF1A1C1B),
    tertiary = Color(0xFF0F6B5C), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCFEDE5), onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFFAFAF8), onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFFAFAF8), onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFE1E4E0), onSurfaceVariant = Color(0xFF444846),
    surfaceTint = Color(0xFFFAFAF8),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F5F2),
    surfaceContainer = Color(0xFFEEEFEC), surfaceContainerHigh = Color(0xFFE8EAE6),
    surfaceContainerHighest = Color(0xFFE1E4E0),
    outline = Color(0xFF747976), outlineVariant = Color(0xFFC4C8C4),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD6C2), onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF1F4F45), onPrimaryContainer = Color(0xFFBDEFE2),
    secondary = Color(0xFFC0C8C4), onSecondary = Color(0xFF111413),
    secondaryContainer = Color(0xFF262B29), onSecondaryContainer = Color(0xFFE2E3E0),
    tertiary = Color(0xFF7FD6C2), onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF1F4F45), onTertiaryContainer = Color(0xFFBDEFE2),
    background = Color(0xFF111413), onBackground = Color(0xFFE2E3E0),
    surface = Color(0xFF111413), onSurface = Color(0xFFE2E3E0),
    surfaceVariant = Color(0xFF303533), onSurfaceVariant = Color(0xFFC0C8C4),
    surfaceTint = Color(0xFF111413),
    surfaceContainerLowest = Color(0xFF0C0F0E), surfaceContainerLow = Color(0xFF171A19),
    surfaceContainer = Color(0xFF1C201E), surfaceContainerHigh = Color(0xFF262B29),
    surfaceContainerHighest = Color(0xFF303533),
    outline = Color(0xFF8A928E), outlineVariant = Color(0xFF404744),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
