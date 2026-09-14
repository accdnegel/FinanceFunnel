package com.pitaka.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Batangas is loaded from app/src/main/res/font/batangas.ttf when the user supplies it.
// Until then, the system sans-serif family remains the safe fallback.
val PitakaFontFamily: FontFamily = FontFamily.SansSerif

private val PitakaTypography = Typography(
    displayLarge = TextStyle(fontFamily = PitakaFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    displayMedium = TextStyle(fontFamily = PitakaFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineLarge = TextStyle(fontFamily = PitakaFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontFamily = PitakaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = PitakaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = PitakaFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = PitakaFontFamily, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = PitakaFontFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = PitakaFontFamily, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = PitakaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = PitakaFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
)

val PitakaOrange = Color(0xFFE06A00)
val PitakaRed = Color(0xFFC62800)
val PitakaYellow = Color(0xFFFFC70E)
val PitakaGreen = Color(0xFF056C3F)
val PitakaBlue = Color(0xFF0278CF)

private val LightColors = lightColorScheme(primary = PitakaOrange, secondary = PitakaBlue, tertiary = PitakaGreen, error = PitakaRed)
private val DarkColors = darkColorScheme(primary = PitakaOrange, secondary = PitakaBlue, tertiary = PitakaGreen, error = PitakaRed)

@Composable
fun PitakaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = PitakaTypography,
        content = content
    )
}
