package com.mazzucci.weather.domain

import java.time.LocalDateTime

/** A place the app can show weather for: either a saved search result or the device's current location. */
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
    }
}

/** Everything the UI and the narrators need about one place's weather. All temperatures in °C, wind in km/h. */
data class Forecast(
    val current: CurrentConditions,
    val today: DaySummary,
    /** The current hour and the 11 after it, in the place's local time. */
    val nextHours: List<HourForecast>,
)

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
