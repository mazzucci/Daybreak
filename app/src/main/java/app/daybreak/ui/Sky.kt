package app.daybreak.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** The handful of looks the UI distinguishes, collapsed from WMO weather codes. */
enum class Sky { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM, UNKNOWN }

fun skyOf(code: Int): Sky = when (code) {
    0, 1 -> Sky.CLEAR
    2 -> Sky.PARTLY_CLOUDY
    3 -> Sky.CLOUDY
    45, 48 -> Sky.FOG
    51, 53, 55, 56, 57 -> Sky.DRIZZLE
    61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
    71, 73, 75, 77, 85, 86 -> Sky.SNOW
    95, 96, 99 -> Sky.STORM
    else -> Sky.UNKNOWN
}

/**
 * Top and bottom colors of the hero gradient for a condition. Every color here keeps white text at >= 4.5:1
 * (WCAG AA), so the hero can use plain white text without a scrim. Dark mode pulls them towards the dark
 * surface so the hero doesn't glow against the rest of the screen.
 */
fun heroGradient(sky: Sky, night: Boolean, darkTheme: Boolean): List<Color> {
    val (top, bottom) = if (night) {
        when (sky) {
            Sky.CLEAR -> 0xFF0B1B3A to 0xFF233A6A
            Sky.PARTLY_CLOUDY -> 0xFF15223C to 0xFF2A3B5C
            Sky.CLOUDY, Sky.FOG -> 0xFF1E2A3A to 0xFF2E3C50
            Sky.DRIZZLE, Sky.RAIN -> 0xFF18232F to 0xFF283848
            Sky.SNOW -> 0xFF28344A to 0xFF3E4C64
            Sky.STORM -> 0xFF12192A to 0xFF262F45
            Sky.UNKNOWN -> 0xFF1E2A3A to 0xFF2E3C50
        }
    } else {
        when (sky) {
            Sky.CLEAR -> 0xFF0D47A1 to 0xFF1976D2
            Sky.PARTLY_CLOUDY -> 0xFF245C96 to 0xFF3878AE
            Sky.CLOUDY, Sky.FOG -> 0xFF4A5F70 to 0xFF5E7587
            Sky.DRIZZLE, Sky.RAIN -> 0xFF2F4A63 to 0xFF4B6A86
            Sky.SNOW -> 0xFF4C6688 to 0xFF587696
            Sky.STORM -> 0xFF22304A to 0xFF354566
            Sky.UNKNOWN -> 0xFF3A5570 to 0xFF557390
        }
    }
    val colors = listOf(Color(top), Color(bottom))
    return if (darkTheme && !night) colors.map { lerp(it, Color(0xFF0B101B), 0.35f) } else colors
}

/** The backdrop for pages that have no forecast yet (loading, errors, permission prompt). */
fun neutralGradient(darkTheme: Boolean): List<Color> = heroGradient(Sky.UNKNOWN, night = false, darkTheme = darkTheme)
