package com.sayem.videoconverter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF64E6B0),
    onPrimary = Color(0xFF082017),
    background = Color(0xFF0B0D12),
    surface = Color(0xFF141820),
    onSurface = Color.White
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
