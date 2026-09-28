package com.mazzucci.weather.narration

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
        val first = "${formatDegrees(f.current.tempC, u)} and $condition now, " +
            "with a high of ${formatDegrees(f.today.highC, u)} and a low of ${formatDegrees(f.today.lowC, u)}."
        return "$first ${rainSentence(input)}"
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
