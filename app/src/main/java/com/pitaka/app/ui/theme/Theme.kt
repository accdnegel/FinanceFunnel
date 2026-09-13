package com.pitaka.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Sampled directly from the Pitaka wallet logo: warm sunburst orange/yellow, red flower
// accent, leaf green, and wave blue.
val PitakaOrange = Color(0xFFE06A00)
val PitakaRed = Color(0xFFC62800)
val PitakaYellow = Color(0xFFFFC70E)
val PitakaGreen = Color(0xFF056C3F)
val PitakaBlue = Color(0xFF0278CF)

private val LightColors = lightColorScheme(
    primary = PitakaOrange,
    secondary = PitakaBlue,
    tertiary = PitakaGreen,
    error = PitakaRed
)

private val DarkColors = darkColorScheme(
    primary = PitakaOrange,
    secondary = PitakaBlue,
    tertiary = PitakaGreen,
    error = PitakaRed
)

@Composable
fun PitakaTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
