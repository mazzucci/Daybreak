package com.mazzucci.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class Weather(
    val tempC: Double,
    val feelsLikeC: Double,
    val humidity: Int,
    val windKmh: Double,
    val code: Int,
) {
    val description: String get() = describe(code)
}

fun cToF(c: Double): Double = c * 9.0 / 5.0 + 32.0

fun formatTemp(c: Double): Pair<String, String> =
    "${cToF(c).roundToInt()}°F" to "${c.roundToInt()}°C"

/** Fetches current conditions from Open-Meteo (free, no API key). Always requested in Celsius. */
suspend fun fetchWeather(lat: Double, lon: Double): Weather = withContext(Dispatchers.IO) {
    val url = URL(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code"
    )
    val conn = url.openConnection() as HttpURLConnection
    try {
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        if (conn.responseCode != 200) error("Weather service returned HTTP ${conn.responseCode}")
        val current = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONObject("current")
        Weather(
            tempC = current.getDouble("temperature_2m"),
            feelsLikeC = current.getDouble("apparent_temperature"),
            humidity = current.getInt("relative_humidity_2m"),
            windKmh = current.getDouble("wind_speed_10m"),
            code = current.getInt("weather_code"),
        )
    } finally {
        conn.disconnect()
    }
}

/** WMO weather interpretation codes, as used by Open-Meteo. */
private fun describe(code: Int): String = when (code) {
    0 -> "Clear sky"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    else -> "Unknown"
}
