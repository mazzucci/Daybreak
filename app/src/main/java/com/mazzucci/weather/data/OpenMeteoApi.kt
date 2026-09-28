package com.mazzucci.weather.data

import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.Place
import java.net.URLEncoder

interface WeatherApi {
    suspend fun forecast(latitude: Double, longitude: Double): Forecast
    suspend fun searchPlaces(query: String): List<Place>
}

/** Open-Meteo forecast and geocoding APIs (free, no API key, HTTPS). Always requested in °C and km/h. */
class OpenMeteoApi(private val http: HttpClient) : WeatherApi {

    override suspend fun forecast(latitude: Double, longitude: Double): Forecast =
        parseForecast(http.get(forecastUrl(latitude, longitude)))

    override suspend fun searchPlaces(query: String): List<Place> =
        parseGeocoding(http.get(searchUrl(query)))

    companion object {
        fun forecastUrl(latitude: Double, longitude: Double): String =
            "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code" +
                "&hourly=temperature_2m,precipitation_probability,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
                "&timezone=auto&forecast_days=2"

        fun searchUrl(query: String): String =
            "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}" +
                "&count=10&language=en&format=json"
    }
}
