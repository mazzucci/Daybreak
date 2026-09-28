package com.mazzucci.weather.narration

import com.mazzucci.weather.domain.degrees
import com.mazzucci.weather.domain.describeWeatherCode
import com.mazzucci.weather.domain.formatHour
import com.mazzucci.weather.domain.formatWind
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Builds the instruction + JSON prompt for the on-device model. Pure, so it's unit-tested. */
object GemmaPrompt {

    fun build(input: NarrationInput, locale: Locale = Locale.getDefault()): String =
        """
        You write the one-line summary at the top of a weather app.
        Using ONLY the data in the JSON below, describe the weather in ${input.placeName} for the next few hours in 1 or 2 short, friendly sentences.
        Temperatures are in °${input.unit.name}. Only use numbers that appear in the JSON. Do not invent numbers, times or places.
        Plain text only: no lists, no markdown, no greeting.

        ${forecastJson(input, locale)}
        """.trimIndent()

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
