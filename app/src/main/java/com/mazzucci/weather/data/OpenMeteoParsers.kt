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

/**
 * Parses an Open-Meteo /v1/forecast response requested with `timezone=auto` and the
 * current/hourly/daily fields from [OpenMeteoApi.forecastUrl]. Times are the place's local time.
 * All returned days and hours are kept; [Forecast] derives today and the next-hours window.
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
    val hours = (0 until hourTimes.length()).map { i ->
        HourForecast(
            time = LocalDateTime.parse(hourTimes.getString(i)),
            tempC = hourTemps.getDouble(i),
            precipChance = hourPrecip.optIntOrZero(i),
            code = hourCodes.getInt(i),
        )
    }

    val daily = root.getJSONObject("daily")
    val dayDates = daily.getJSONArray("time")
    val highs = daily.getJSONArray("temperature_2m_max")
    val lows = daily.getJSONArray("temperature_2m_min")
    val dayPrecip = daily.getJSONArray("precipitation_probability_max")
    val dayCodes = daily.getJSONArray("weather_code")
    val days = (0 until dayDates.length()).map { i ->
        DaySummary(
            date = LocalDate.parse(dayDates.getString(i)),
            highC = highs.getDouble(i),
            lowC = lows.getDouble(i),
            precipChance = dayPrecip.optIntOrZero(i),
            code = dayCodes.getInt(i),
        )
    }

    return Forecast(current, days, hours)
}

/** Parses an Open-Meteo geocoding /v1/search response. No matches → empty list (the API omits "results"). */
fun parseGeocoding(json: String): List<Place> {
    val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).map { i ->
        val r = results.getJSONObject(i)
        Place(
            id = Place.geocodingId(r.getLong("id")),
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
