package app.daybreak.data

import app.daybreak.domain.CurrentConditions
import app.daybreak.domain.DaySummary
import app.daybreak.domain.Forecast
import app.daybreak.domain.HourForecast
import app.daybreak.domain.Place
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Parses an Open-Meteo /v1/forecast response requested with `timezone=auto` and the
 * current/hourly/daily fields from [OpenMeteoApi.forecastUrl]. Times are the place's local time.
 * All returned days and hours are kept; [Forecast] derives today and the next-hours window.
 * The core fields are required; the detail fields (wind, gusts, sun times, UV, is_day) are optional, and a missing
 * array or a null value becomes null rather than failing the whole forecast.
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
        isDay = cur.optBooleanFlag("is_day"),
    )

    val hourly = root.getJSONObject("hourly")
    val hourTimes = hourly.getJSONArray("time")
    val hourTemps = hourly.getJSONArray("temperature_2m")
    val hourPrecip = hourly.getJSONArray("precipitation_probability")
    val hourCodes = hourly.getJSONArray("weather_code")
    val hourWind = hourly.optJSONArray("wind_speed_10m")
    val hourGusts = hourly.optJSONArray("wind_gusts_10m")
    val hourIsDay = hourly.optJSONArray("is_day")
    val hours = (0 until hourTimes.length()).map { i ->
        HourForecast(
            time = LocalDateTime.parse(hourTimes.getString(i)),
            tempC = hourTemps.getDouble(i),
            precipChance = hourPrecip.optIntOrZero(i),
            code = hourCodes.getInt(i),
            windKmh = hourWind.doubleOrNull(i),
            gustKmh = hourGusts.doubleOrNull(i),
            isDay = hourIsDay.doubleOrNull(i)?.let { it != 0.0 },
        )
    }

    val daily = root.getJSONObject("daily")
    val dayDates = daily.getJSONArray("time")
    if (dayDates.length() == 0) throw IOException("Weather service returned no daily forecast")
    val highs = daily.getJSONArray("temperature_2m_max")
    val lows = daily.getJSONArray("temperature_2m_min")
    val dayPrecip = daily.getJSONArray("precipitation_probability_max")
    val dayCodes = daily.getJSONArray("weather_code")
    val sunrises = daily.optJSONArray("sunrise")
    val sunsets = daily.optJSONArray("sunset")
    val windMax = daily.optJSONArray("wind_speed_10m_max")
    val gustMax = daily.optJSONArray("wind_gusts_10m_max")
    val precipSum = daily.optJSONArray("precipitation_sum")
    val uvMax = daily.optJSONArray("uv_index_max")
    val days = (0 until dayDates.length()).map { i ->
        DaySummary(
            date = LocalDate.parse(dayDates.getString(i)),
            highC = highs.getDouble(i),
            lowC = lows.getDouble(i),
            precipChance = dayPrecip.optIntOrZero(i),
            code = dayCodes.getInt(i),
            sunrise = sunrises.timeOrNull(i),
            sunset = sunsets.timeOrNull(i),
            windMaxKmh = windMax.doubleOrNull(i),
            gustMaxKmh = gustMax.doubleOrNull(i),
            precipSumMm = precipSum.doubleOrNull(i),
            uvIndexMax = uvMax.doubleOrNull(i),
        )
    }

    return Forecast(current, days, hours, root.optInt("utc_offset_seconds", 0))
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
            countryCode = r.optStringOrNull("country_code")?.uppercase(),
            latitude = r.getDouble("latitude"),
            longitude = r.getDouble("longitude"),
        )
    }
}

/** Open-Meteo sends null for precipitation probability in some models/hours; treat that as 0%. */
private fun JSONArray.optIntOrZero(i: Int): Int = if (isNull(i)) 0 else getInt(i)

/** Value at [i] of an optional array; null if the array is missing, too short, or holds null there. */
private fun JSONArray?.doubleOrNull(i: Int): Double? =
    if (this == null || i >= length() || isNull(i)) null else optDouble(i).takeIf { !it.isNaN() }

/**
 * Local time at [i] of an optional array; null if missing or unparseable. Polar days come through as real
 * midnight values and are interpreted by [app.daybreak.domain.DaySummary.daylight].
 */
private fun JSONArray?.timeOrNull(i: Int): LocalDateTime? =
    if (this == null || i >= length() || isNull(i)) null
    else runCatching { LocalDateTime.parse(getString(i)) }.getOrNull()

/** Open-Meteo's 0/1 is_day flag (a JSON boolean is accepted too); null if missing or anything else. */
private fun JSONObject.optBooleanFlag(key: String): Boolean? = when (val v = opt(key)) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    else -> null
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key).ifBlank { null } else null
