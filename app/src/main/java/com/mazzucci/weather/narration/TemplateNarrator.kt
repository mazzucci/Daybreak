package com.mazzucci.weather.narration

import com.mazzucci.weather.domain.Tone
import com.mazzucci.weather.domain.describeWeatherCode
import com.mazzucci.weather.domain.formatDegrees
import com.mazzucci.weather.domain.formatHour
import java.util.Locale

/** Deterministic, always-available description. Used immediately and whenever the LLM can't be trusted. */
class TemplateNarrator(private val locale: Locale = Locale.getDefault()) : WeatherNarrator {

    override suspend fun narrate(input: NarrationInput): String = describe(input)

    fun describe(input: NarrationInput): String {
        val f = input.forecast
        val u = input.unit
        val condition = describeWeatherCode(f.current.code).lowercase(locale)
        val now = formatDegrees(f.current.tempC, u)
        val high = formatDegrees(f.today.highC, u)
        val low = formatDegrees(f.today.lowC, u)
        if (input.tone == Tone.BRIEF) return "$now and $condition, high $high, low $low. ${briefRain(input)}"
        val first = "$now and $condition now, with a high of $high and a low of $low."
        val wet = rainLikely(input)
        // The voices only add words around the facts, never numbers, so the result still passes validation.
        return when (input.tone) {
            Tone.FRIENDLY, Tone.BRIEF -> "$first ${rainSentence(input)}"
            Tone.CHEERFUL -> "Hello there, it's ${first.replaceFirstChar { it.lowercase(locale) }} ${rainSentence(input)} " +
                if (wet) "Umbrella time!" else "Enjoy it!"
            Tone.DEADPAN -> "$first ${rainSentence(input)} " + if (wet) "Thrilling." else "Try to contain your excitement."
            Tone.PIRATE -> "Ahoy, it's ${first.replaceFirstChar { it.lowercase(locale) }} ${rainSentence(input)} " +
                if (wet) "Batten down the hatches!" else "Fair winds, matey!"
        }
    }

    private fun rainLikely(input: NarrationInput): Boolean =
        input.forecast.nextHours.any { it.precipChance >= RAIN_LIKELY } || input.forecast.today.precipChance >= RAIN_POSSIBLE

    private fun briefRain(input: NarrationInput): String {
        val wetHour = input.forecast.nextHours.firstOrNull { it.precipChance >= RAIN_LIKELY }
        return if (wetHour != null) "Rain around ${formatHour(wetHour.time, locale)}." else if (rainLikely(input)) "Rain possible." else "Dry."
    }

    private fun rainSentence(input: NarrationInput): String {
        val wetHour = input.forecast.nextHours.firstOrNull { it.precipChance >= RAIN_LIKELY }
        return when {
            wetHour != null ->
                "Rain is likely around ${formatHour(wetHour.time, locale)} (${wetHour.precipChance}% chance)."
            input.forecast.today.precipChance >= RAIN_POSSIBLE ->
                "There's a ${input.forecast.today.precipChance}% chance of rain today."
            else -> "No rain expected."
        }
    }

    private companion object {
        const val RAIN_LIKELY = 50
        const val RAIN_POSSIBLE = 20
    }
}
