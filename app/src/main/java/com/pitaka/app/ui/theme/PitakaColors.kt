package com.pitaka.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Preset accent colors the user can pick for a Pitaka or Goal card. */
val accentColorPalette = listOf(
    "#E06A00", // Pitaka orange
    "#C62800", // Pitaka red
    "#FFC70E", // Pitaka yellow
    "#056C3F", // Pitaka green
    "#0278CF", // Pitaka blue
    "#8E44AD", // purple
    "#D4537E", // pink
    "#546E7A"  // slate
)

fun parseHexColor(hex: String?): Color? = try {
    hex?.let { Color(android.graphics.Color.parseColor(it)) }
} catch (e: Exception) {
    null
}

/**
 * Colors a progress/status bar from red to green based on how "healthy" a value is:
 * - Expense budgets: pass (remaining / limit) — lots left = green, little/none left = red.
 * - Goal progress: pass (current / target) — close to goal = green, far from it = red.
 */
fun healthColor(ratio: Float): Color {
    val clamped = ratio.coerceIn(0f, 1f)
    return lerp(Color(0xFFD64545), Color(0xFF1E8E5A), clamped)
}

/** Warm, high-contrast Batik-inspired presets for Pitaka cards. */
val batikColorPalette = listOf(
    "#C62828", // indigo-red batik
    "#E06A00", // tangerine
    "#FFC107", // turmeric yellow
    "#176B4D", // leaf green
    "#006A9B", // ocean blue
    "#5B2C6F", // deep violet
    "#8D6E63", // earth brown
    "#F5E6C8"  // natural cloth
)
