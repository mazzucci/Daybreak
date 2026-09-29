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
        val facts = "$now and $condition now, with a high of $high and a low of $low. ${rainSentence(input)}"
        return styled(input.tone, facts, wetness(input), snowy = input.forecast.current.code in SNOW_CODES)
    }

    /**
     * A sample line in [tone] for previewing it in Settings. Deliberately has no numbers, so it can't be
     * mistaken for a real forecast, and it uses the same greeting and sign-off as the real summary.
     */
    fun preview(tone: Tone): String = when (tone) {
        Tone.BRIEF -> "Partly cloudy, cooler tonight. Rain this evening."
        else -> styled(tone, "Partly cloudy now, with rain likely this evening.", Wetness.WET)
    }

    /**
     * Wraps the facts in the voice's greeting and sign-off. The voices only add words around the facts, never
     * numbers, so the result still passes validation whenever [facts] does. Greetings join with a comma so the
     * line stays within the three sentences the validator allows a playful voice.
     */
    private fun styled(tone: Tone, facts: String, wetness: Wetness, snowy: Boolean = false): String {
        val lower = facts.replaceFirstChar { it.lowercase(locale) }
        return when (tone) {
            Tone.FRIENDLY, Tone.BRIEF -> facts
            Tone.CHEERFUL -> "Hello there, it's $lower " + when (wetness) {
                Wetness.WET -> if (snowy) "Bundle up!" else "Umbrella time!"
                Wetness.MAYBE -> "Maybe pack an umbrella!"
                Wetness.DRY -> "Make the most of it!"
            }
            Tone.DEADPAN -> "$facts " + when (wetness) {
                Wetness.WET -> "Thrilling."
                Wetness.MAYBE -> "The suspense is unbearable."
                Wetness.DRY -> "Try to contain your excitement."
            }
            Tone.PIRATE -> "Ahoy, 'tis ${pirateSpeak(lower)} " + when (wetness) {
                Wetness.WET -> "Batten down the hatches!"
                Wetness.MAYBE -> "Keep an eye on the horizon!"
                Wetness.DRY -> "Fair winds, matey!"
            }
        }
    }

    /** Strong sign-offs only when rain is likely soon or falling now; a modest daily chance gets a hedged one. */
    private enum class Wetness { WET, MAYBE, DRY }

    private fun wetness(input: NarrationInput): Wetness = when {
        precipitatingNow(input) || input.forecast.nextHours.any { it.precipChance >= RAIN_LIKELY } -> Wetness.WET
        input.forecast.today.precipChance >= RAIN_POSSIBLE -> Wetness.MAYBE
        else -> Wetness.DRY
    }

    private fun precipitatingNow(input: NarrationInput): Boolean = input.forecast.current.code in WET_CODES

    /** A little pirate in the middle of the line too, not just at its ends. Touches words only, never numbers. */
    private fun pirateSpeak(facts: String): String = facts
        .replace("Rain is likely", "Rain be comin'")
        .replace("There's a", "There be a")
        .replace("of rain today", "o' rain today")
        .replace("No rain expected", "No rain on the horizon")
        .replace("rain likely this evening", "rain comin' this evening")

    private fun briefRain(input: NarrationInput): String {
        val wetHour = input.forecast.nextHours.firstOrNull { it.precipChance >= RAIN_LIKELY }
        return when {
            wetHour != null -> "Rain around ${formatHour(wetHour.time, locale)}."
            precipitatingNow(input) -> "Easing soon."
            input.forecast.today.precipChance >= RAIN_POSSIBLE -> "Rain possible."
            else -> "No rain."
        }
    }

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
