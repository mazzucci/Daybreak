package com.mazzucci.weather.data

import com.mazzucci.weather.domain.RideHour
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Hourly weather for a past ride, as UTC instants, and the place's UTC offset for showing local times. */
data class RideWeather(val hours: List<RideHour>, val utcOffsetSeconds: Int)

/** Weather for a past ride. */
fun interface RideWeatherApi {
    /**
     * Hourly weather at a place over [from]..[to], inclusive. With [localTime] the dates are the place's own and
     * the offset is reported (one offset for the whole range, so keep ranges short across daylight saving);
     * otherwise everything is UTC, right for long ranges.
     */
    suspend fun weather(latitude: Double, longitude: Double, from: LocalDate, to: LocalDate, localTime: Boolean): RideWeather
}

/**
 * Open-Meteo's Historical Forecast API: archived forecast-model data with no delay (the reanalysis archive lags
 * by days, which would leave this weekend's ride without weather), from 2022 on. Free, no key. Asked in the
 * place's own time zone, so the ride can be shown in the time it happened there.
 */
class OpenMeteoRideWeather(private val http: HttpClient) : RideWeatherApi {
    override suspend fun weather(latitude: Double, longitude: Double, from: LocalDate, to: LocalDate, localTime: Boolean): RideWeather {
        val body = try {
            http.get(url(latitude, longitude, from, to, localTime))
        } catch (e: HttpException) {
            throw IOException("Weather history returned HTTP ${e.code}")
        }
        return try {
            RideWeather(parseRideHours(body), JSONObject(body).optInt("utc_offset_seconds", 0))
        } catch (e: org.json.JSONException) {
            throw IOException("The weather history came back in an unexpected form")
        }
    }

    companion object {
        fun url(latitude: Double, longitude: Double, from: LocalDate, to: LocalDate, localTime: Boolean = true) =
            "https://historical-forecast-api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                "&start_date=$from&end_date=$to" +
                "&hourly=temperature_2m,precipitation,wind_speed_10m,wind_direction_10m,weather_code&timezone=${if (localTime) "auto" else "GMT"}"
    }
}

/** Hourly rows as UTC instants, converted from the response's local times with its `utc_offset_seconds`. */
fun parseRideHours(json: String): List<RideHour> {
    val root = JSONObject(json)
    val offset = ZoneOffset.ofTotalSeconds(root.optInt("utc_offset_seconds", 0))
    val h = root.getJSONObject("hourly")
    val times = h.getJSONArray("time")
    val temp = h.getJSONArray("temperature_2m")
    val rain = h.getJSONArray("precipitation")
    val wind = h.getJSONArray("wind_speed_10m")
    val dir = h.getJSONArray("wind_direction_10m")
    val code = h.getJSONArray("weather_code")
    return (0 until times.length()).mapNotNull { i ->
        // Hours the model has no data for come back as nulls; skip them rather than invent zeros.
        if (temp.isNull(i) || wind.isNull(i) || dir.isNull(i)) return@mapNotNull null
        RideHour(
            time = LocalDateTime.parse(times.getString(i)).toInstant(offset),
            tempC = temp.getDouble(i),
            precipitationMm = if (rain.isNull(i)) 0.0 else rain.getDouble(i),
            windKmh = wind.getDouble(i),
            windFromDeg = dir.getDouble(i),
            code = if (code.isNull(i)) 0 else code.getInt(i),
        )
    }
}
