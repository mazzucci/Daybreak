package com.mazzucci.weather.narration

import com.mazzucci.weather.domain.capAboutMe
import com.mazzucci.weather.domain.Tone
import com.mazzucci.weather.domain.degrees
import com.mazzucci.weather.domain.describeWeatherCode
import com.mazzucci.weather.domain.formatHour
import com.mazzucci.weather.domain.formatWind
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Builds the instruction + JSON prompt for the on-device model. Pure, so it's unit-tested. */
object GemmaPrompt {

    fun build(input: NarrationInput, locale: Locale = Locale.getDefault()): String {
        val about = sanitizeAboutMe(input.aboutMe)
        val lines = buildList {
            add("You write the one-line summary at the top of a weather app.")
            add(
                "Using ONLY the data in the JSON below, describe the weather in ${input.placeName} for the next few " +
                    "hours in ${length(input.tone)}."
            )
            add(voice(input.tone))
            add("Temperatures are in °${input.unit.name}. Only use numbers that appear in the JSON. Do not invent numbers, times or places.")
            add("Plain text only: no lists, no markdown.")
            if (about.isNotEmpty()) {
                add(
                    "About the reader: \"$about\". Use this only to choose what to mention (for example rain for " +
                        "someone who cycles). Don't repeat it and don't assume plans they didn't mention."
                )
            }
        }
        return lines.joinToString("\n") + "\n\n" + forecastJson(input, locale)
    }

    private fun length(tone: Tone): String = when (tone) {
        Tone.BRIEF -> "one short sentence"
        Tone.FRIENDLY -> "1 or 2 short sentences"
        Tone.CHEERFUL, Tone.DEADPAN, Tone.PIRATE -> "2 or 3 short sentences"
    }

    /**
     * A 1B model follows concrete words better than adjectives, so each playful voice names the words it may use
     * and shows the shape of its one flourish. Greetings must join the first sentence with a comma: a separate
     * "Ahoy!" would be a fourth sentence and fail validation. The examples carry no numbers, so a model that
     * copies one still validates.
     */
    private fun voice(tone: Tone): String = when (tone) {
        Tone.FRIENDLY -> "Voice: friendly and clear, like a helpful neighbour. No greeting."
        Tone.BRIEF -> "Voice: just the facts, like a headline. No greeting, no advice."
        Tone.CHEERFUL ->
            "Voice: warm and upbeat, like a friend cheering you on. Start with \"Hello there,\" in the first sentence, " +
                "and end with one short encouraging line such as \"Umbrella time!\" or \"Make the most of it!\"."
        Tone.DEADPAN ->
            "Voice: flat and matter-of-fact, no greeting, no exclamation marks. End with one short dry remark " +
                "about the weather, such as \"Thrilling.\" or \"Try to contain your excitement.\""
        Tone.PIRATE ->
            "Voice: a cheerful pirate captain. Start with \"Ahoy,\" in the first sentence, use a few pirate words " +
                "(ye, matey, be, o', aye), and end with a sign-off such as \"Batten down the hatches!\" or " +
                "\"Fair winds, matey!\". Keep every weather fact exact."
    }

    /**
     * The user's note, flattened to one short line without characters that could look like prompt structure
     * (the JSON braces, template tags).
     */
    fun sanitizeAboutMe(text: String): String = text
        .replace(Regex("[{}<>\\[\\]\"`]"), "")
        .replace(Regex("\\s+"), " ")
        .let(::capAboutMe)

    fun forecastJson(input: NarrationInput, locale: Locale = Locale.getDefault()): String {
        val f = input.forecast
        val u = input.unit
        val now = JSONObject()
            .put("temperature", degrees(f.current.tempC, u))
            .put("feels_like", degrees(f.current.feelsLikeC, u))
            .put("condition", describeWeatherCode(f.current.code))
            .put("humidity_percent", f.current.humidity)
            .put("wind", formatWind(f.current.windKmh, u))
        val today = JSONObject()
            .put("high", degrees(f.today.highC, u))
            .put("low", degrees(f.today.lowC, u))
            .put("max_rain_chance_percent", f.today.precipChance)
        // Every third hour keeps the prompt short for a 1B model while still showing the trend.
        val hours = JSONArray(
            f.nextHours.filterIndexed { i, _ -> i % 3 == 0 }.map {
                JSONObject()
                    .put("time", formatHour(it.time, locale))
                    .put("temperature", degrees(it.tempC, u))
                    .put("rain_chance_percent", it.precipChance)
                    .put("condition", describeWeatherCode(it.code))
            }
        )
        return JSONObject()
            .put("place", input.placeName)
            .put("unit", "°${u.name}")
            .put("now", now)
            .put("today", today)
            .put("later", hours)
            .toString()
    }
}
