package com.aiassistant.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy = Color(0xFF050A14)
val Panel = Color(0xFF0B1426)
val PanelHigh = Color(0xFF10203A)
val Cyan = Color(0xFF00E5FF)
val Blue = Color(0xFF2979FF)
val Amber = Color(0xFFFFB300)
val Danger = Color(0xFFFF5252)
val Muted = Color(0xFF9FB6CC)

/** Dark-first by design: the JARVIS look is a dark HUD, so there is intentionally no light variant. */
@Composable
fun AssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan, onPrimary = Color.Black,
            secondary = Blue, onSecondary = Color.White,
            background = Navy, onBackground = Color(0xFFE6F7FF),
            surface = Panel, onSurface = Color(0xFFE6F7FF),
            surfaceVariant = PanelHigh, onSurfaceVariant = Muted,
            error = Danger, onError = Color.Black,
        ),
        content = content,
    )
}
