package com.mazzucci.weather.data

import com.mazzucci.weather.domain.CurrentConditions
import com.mazzucci.weather.domain.DaySummary
import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.HourForecast
import com.mazzucci.weather.domain.Place
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

const val NEXT_HOURS = 12

/**
 * Parses an Open-Meteo /v1/forecast response requested with `timezone=auto` and the
 * current/hourly/daily fields from [OpenMeteoApi.forecastUrl]. Times are the place's local time.
 */
fun parseForecast(json: String): Forecast {
    val root = JSONObject(json)
    val cur = root.getJSONObject("current")
    val now = LocalDateTime.parse(cur.getString("time"))
    val current = CurrentConditions(
        time = now,
        tempC = cur.getDouble("temperature_2m"),
        feelsLikeC = cur.getDouble("apparent_temperature"),
        humidity = cur.getInt("relative_humidity_2m"),
        windKmh = cur.getDouble("wind_speed_10m"),
        code = cur.getInt("weather_code"),
    )

    val hourly = root.getJSONObject("hourly")
    val hourTimes = hourly.getJSONArray("time")
    val hourTemps = hourly.getJSONArray("temperature_2m")
    val hourPrecip = hourly.getJSONArray("precipitation_probability")
    val hourCodes = hourly.getJSONArray("weather_code")
    val thisHour = now.truncatedTo(ChronoUnit.HOURS)
    val nextHours = (0 until hourTimes.length())
        .map { i ->
            HourForecast(
                time = LocalDateTime.parse(hourTimes.getString(i)),
                tempC = hourTemps.getDouble(i),
                precipChance = hourPrecip.optIntOrZero(i),
                code = hourCodes.getInt(i),
            )
        }
        .filter { !it.time.isBefore(thisHour) }
        .take(NEXT_HOURS)

    val daily = root.getJSONObject("daily")
    val days = daily.getJSONArray("time")
    val todayIndex = (0 until days.length())
        .firstOrNull { LocalDate.parse(days.getString(it)) == now.toLocalDate() }
        ?: 0
    val today = DaySummary(
        highC = daily.getJSONArray("temperature_2m_max").getDouble(todayIndex),
        lowC = daily.getJSONArray("temperature_2m_min").getDouble(todayIndex),
        precipChance = daily.getJSONArray("precipitation_probability_max").optIntOrZero(todayIndex),
        code = daily.getJSONArray("weather_code").getInt(todayIndex),
    )

    return Forecast(current, today, nextHours)
}

/** Parses an Open-Meteo geocoding /v1/search response. No matches → empty list (the API omits "results"). */
fun parseGeocoding(json: String): List<Place> {
    val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).map { i ->
        val r = results.getJSONObject(i)
        Place(
            id = r.getLong("id").toString(),
            name = r.getString("name"),
            region = r.optStringOrNull("admin1"),
            country = r.optStringOrNull("country"),
            latitude = r.getDouble("latitude"),
            longitude = r.getDouble("longitude"),
        )
    }
}

/** Open-Meteo sends null for precipitation probability in some models/hours; treat that as 0%. */
private fun JSONArray.optIntOrZero(i: Int): Int = if (isNull(i)) 0 else getInt(i)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key).ifBlank { null } else null
