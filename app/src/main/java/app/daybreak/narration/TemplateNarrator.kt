package app.daybreak.narration

import app.daybreak.domain.describeWeatherCode
import app.daybreak.domain.formatDegrees
import app.daybreak.domain.formatHour
import java.util.Locale

/** The summary line: a deterministic, always-available description of the forecast. */
class TemplateNarrator(private val locale: Locale = Locale.getDefault()) {

    fun describe(input: NarrationInput): String {
        val f = input.forecast
        val u = input.unit
        val condition = describeWeatherCode(f.current.code).lowercase(locale)
        val now = formatDegrees(f.current.tempC, u)
        val high = formatDegrees(f.today.highC, u)
        val low = formatDegrees(f.today.lowC, u)
        return "$now and $condition now, with a high of $high and a low of $low. ${rainSentence(input)}"
    }

    private fun precipitatingNow(input: NarrationInput): Boolean = input.forecast.current.code in WET_CODES

    private fun rainSentence(input: NarrationInput): String {
        val wetHour = input.forecast.nextHours.firstOrNull { it.precipChance >= RAIN_LIKELY }
        return when {
            wetHour != null ->
                "Rain is likely around ${formatHour(wetHour.time, locale)} (${wetHour.precipChance}% chance)."
            // Already falling (the first sentence says so) but no hour ahead is likely: it's on its way out.
            precipitatingNow(input) -> "It should ease off soon."
            input.forecast.today.precipChance >= RAIN_POSSIBLE ->
                "There's a ${input.forecast.today.precipChance}% chance of rain today."
            else -> "No rain expected."
        }
    }

    private companion object {
        const val RAIN_LIKELY = 50
        const val RAIN_POSSIBLE = 20
        val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
        /** Drizzle, rain, showers, snow and storms. */
        val WET_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99) + SNOW_CODES
    }
}
