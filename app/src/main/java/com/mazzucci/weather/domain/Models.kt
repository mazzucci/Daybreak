package com.mazzucci.weather.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Days of forecast fetched per place (today included). Open-Meteo allows up to 16. */
const val FORECAST_DAYS = 8

/** Length of the hourly strip and of the window the summary describes. */
const val NEXT_HOURS = 12

/**
 * A place the app can show weather for: either a saved search result or the device's current location.
 * [id] doubles as the page key: saved places are "geo:<geocoding id>", the device location is [CURRENT_LOCATION_ID].
 */
data class Place(
    val id: String,
    val name: String,
    val region: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
) {
    /** "Region, Country" line shown under the name; null if there's nothing to add. */
    val detail: String? get() = listOfNotNull(region, country).joinToString(", ").ifBlank { null }

    companion object {
        const val CURRENT_LOCATION_ID = "current"
        const val GEOCODING_PREFIX = "geo:"

        fun geocodingId(id: Long): String = "$GEOCODING_PREFIX$id"
    }
}

/** Everything the UI and the narrators need about one place's weather. All temperatures in °C, wind in km/h. */
data class Forecast(
    val current: CurrentConditions,
    /** One entry per day, starting with the place's today (up to [FORECAST_DAYS]). Never empty. */
    val days: List<DaySummary>,
    /** Hourly forecast for the same range as [days], in the place's local time. */
    val hours: List<HourForecast>,
) {
    init {
        require(days.isNotEmpty()) { "A forecast needs at least one day" }
    }

    /** The day containing [CurrentConditions.time]. */
    val today: DaySummary = days.firstOrNull { it.date == current.time.toLocalDate() } ?: days.first()

    /** The current hour and the [NEXT_HOURS] - 1 after it. */
    val nextHours: List<HourForecast> = current.time.truncatedTo(ChronoUnit.HOURS).let { thisHour ->
        hours.filter { !it.time.isBefore(thisHour) }.take(NEXT_HOURS)
    }
}

data class CurrentConditions(
    val time: LocalDateTime,
    val tempC: Double,
    val feelsLikeC: Double,
    val humidity: Int,
    val windKmh: Double,
    val code: Int,
) {
    val description: String get() = describeWeatherCode(code)
}

data class DaySummary(
    val date: LocalDate,
    val highC: Double,
    val lowC: Double,
    /** Highest hourly chance of precipitation today, 0–100. */
    val precipChance: Int,
    val code: Int,
)

data class HourForecast(
    val time: LocalDateTime,
    val tempC: Double,
    val precipChance: Int,
    val code: Int,
)

enum class TempUnit { F, C }

data class AppSettings(
    /** The unit shown large; the other one is shown small next to it. */
    val primaryUnit: TempUnit = TempUnit.F,
    /** Whether the device location gets its own page (first) in the pager. */
    val useCurrentLocation: Boolean = true,
    /** Whether to try the on-device Gemma model for the summary line (needs an imported model). */
    val gemmaEnabled: Boolean = true,
)
