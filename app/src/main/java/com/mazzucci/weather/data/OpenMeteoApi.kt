package com.mazzucci.weather.data

import com.mazzucci.weather.domain.FORECAST_DAYS
import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.Place
import java.io.IOException
import java.net.URLEncoder

interface WeatherApi {
    suspend fun forecast(latitude: Double, longitude: Double): Forecast
    suspend fun searchPlaces(query: String): List<Place>
}

/** Open-Meteo forecast and geocoding APIs (free, no API key, HTTPS). Always requested in °C and km/h. */
class OpenMeteoApi(private val http: HttpClient) : WeatherApi {

    override suspend fun forecast(latitude: Double, longitude: Double): Forecast =
        parseForecast(get(forecastUrl(latitude, longitude), "Weather service"))

    override suspend fun searchPlaces(query: String): List<Place> =
        parseGeocoding(get(searchUrl(query), "Place search"))

    private suspend fun get(url: String, service: String): String = try {
        http.get(url)
    } catch (e: HttpException) {
        throw IOException("$service returned HTTP ${e.code}")
    }

    companion object {
        fun forecastUrl(latitude: Double, longitude: Double): String =
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day" +
                "&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m,wind_gusts_10m,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code," +
                "sunrise,sunset,wind_speed_10m_max,wind_gusts_10m_max,precipitation_sum,uv_index_max" +
                "&timezone=auto&forecast_days=$FORECAST_DAYS"

        fun searchUrl(query: String): String =
            "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}" +
                "&count=10&language=en&format=json"
    }
}
