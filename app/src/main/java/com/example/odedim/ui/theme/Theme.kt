package com.example.odedim.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ForestColors = darkColorScheme(
    primary = Color(0xFF7BE0A2),
    onPrimary = Color(0xFF07381E),
    primaryContainer = Color(0xFF1A5032),
    onPrimaryContainer = Color(0xFFB8F5CE),
    secondary = Color(0xFFACCCB7),
    onSecondary = Color(0xFF143825),
    secondaryContainer = Color(0xFF213F2E),
    onSecondaryContainer = Color(0xFFD0EED9),
    tertiary = Color(0xFF95D4C1),
    background = Color(0xFF0B1410),
    onBackground = Color(0xFFE4F1E8),
    surface = Color(0xFF0B1410),
    onSurface = Color(0xFFE4F1E8),
    surfaceVariant = Color(0xFF263C2F),
    onSurfaceVariant = Color(0xFFADC2B4),
    surfaceContainerLowest = Color(0xFF070E0A),
    surfaceContainerLow = Color(0xFF121F18),
    surfaceContainer = Color(0xFF18291F),
    surfaceContainerHigh = Color(0xFF213329),
    surfaceContainerHighest = Color(0xFF2A4032),
    outline = Color(0xFF7B9484),
    outlineVariant = Color(0xFF324C3D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun OdedimTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ForestColors,
        typography = Typography,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
