package app.daybreak.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Days of forecast fetched per place (today included). Open-Meteo allows up to 16. */
const val FORECAST_DAYS = 8

/** Length of the hourly strip and of the window the summary describes. */
const val NEXT_HOURS = 12

/** Length of the multi-day list on each page (today included). */
const val WEEK_DAYS = 7

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
    /** ISO 3166-1 alpha-2 code ("US"), for public holidays; null if unknown. */
    val countryCode: String? = null,
    /** IANA time zone ("Europe/Bucharest"), for clocks; null if unknown (places saved before it was kept). */
    val zoneId: String? = null,
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
    /** The place's offset from UTC now, to line its hours up with another place's. */
    val utcOffsetSeconds: Int = 0,
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

    /** Today and the days after it, for the multi-day list (at most [count]). */
    fun upcomingDays(count: Int = WEEK_DAYS): List<DaySummary> =
        days.filter { !it.date.isBefore(today.date) }.take(count)

    /** Whether it's dark now at the place, from Open-Meteo's is_day flag or else from sunrise/sunset. */
    val isNightNow: Boolean get() = current.isDay?.not() ?: isNight(current.time)

    /** Whether it's dark at the start of [hour]. */
    fun isNight(hour: HourForecast): Boolean = hour.isDay?.not() ?: isNight(hour.time)

    /**
     * Whether [time] falls outside that day's sunrise–sunset, allowing for a previous day's sunset after midnight
     * (high latitudes). Polar night is always night and midnight sun never is. Days without sun times (a response
     * missing the fields) fall back to a fixed 20:00–06:00 night.
     */
    fun isNight(time: LocalDateTime): Boolean {
        val date = time.toLocalDate()
        val yesterday = days.firstOrNull { it.date == date.minusDays(1) }
        val lateSunset = yesterday?.sunset?.takeIf { yesterday.daylight == Daylight.NORMAL }
        if (lateSunset != null && time.isBefore(lateSunset)) return false
        val day = days.firstOrNull { it.date == date }
        return when (day?.daylight) {
            Daylight.POLAR_NIGHT -> true
            Daylight.MIDNIGHT_SUN -> false
            Daylight.NORMAL -> time.isBefore(day.sunrise) || !time.isBefore(day.sunset)
            Daylight.UNKNOWN, null -> time.hour < 6 || time.hour >= 20
        }
    }
}

data class CurrentConditions(
    val time: LocalDateTime,
    val tempC: Double,
    val feelsLikeC: Double,
    val humidity: Int,
    val windKmh: Double,
    val code: Int,
    /** Open-Meteo's is_day flag; null if the response didn't include it. */
    val isDay: Boolean? = null,
) {
    val description: String get() = describeWeatherCode(code)
}

data class DaySummary(
    val date: LocalDate,
    val highC: Double,
    val lowC: Double,
    /** Highest hourly chance of precipitation on [date], 0–100. */
    val precipChance: Int,
    val code: Int,
    /** Local sunrise and sunset; null when the sun doesn't rise or set that day, or the field is missing. */
    val sunrise: LocalDateTime? = null,
    val sunset: LocalDateTime? = null,
    /** Highest sustained wind and gust of the day, km/h. */
    val windMaxKmh: Double? = null,
    val gustMaxKmh: Double? = null,
    /** Total rain, showers and snow water equivalent, mm. */
    val precipSumMm: Double? = null,
    /** Highest UV index of the day. */
    val uvIndexMax: Double? = null,
) {
    /**
     * How the sun behaves on [date]. Open-Meteo marks polar night with sunrise == sunset (both midnight) and
     * midnight sun with a sunset 24 hours after sunrise, so the raw times mean nothing to show in those cases.
     */
    val daylight: Daylight
        get() = when {
            sunrise == null || sunset == null -> Daylight.UNKNOWN
            !sunset.isAfter(sunrise) -> Daylight.POLAR_NIGHT
            !sunset.isBefore(sunrise.plusHours(24)) -> Daylight.MIDNIGHT_SUN
            else -> Daylight.NORMAL
        }
}

enum class Daylight { NORMAL, POLAR_NIGHT, MIDNIGHT_SUN, UNKNOWN }

data class HourForecast(
    val time: LocalDateTime,
    val tempC: Double,
    val precipChance: Int,
    val code: Int,
    /** Sustained wind and gusts at 10 m, km/h; null if missing. */
    val windKmh: Double? = null,
    val gustKmh: Double? = null,
    /** Open-Meteo's is_day flag for the start of the hour; null if missing. */
    val isDay: Boolean? = null,
)

enum class TempUnit { F, C }

data class AppSettings(
    /** The unit shown large; the other one is shown small next to it. */
    val primaryUnit: TempUnit = TempUnit.F,
    /** Whether the device location gets its own page (first) in the pager. */
    val useCurrentLocation: Boolean = true,
    /** Whether the on-device Gemma model writes the daily meme (needs an installed model). */
    val gemmaEnabled: Boolean = true,
    /** The activity to find good weather windows for; null hides the card. */
    val activity: Activity? = Activity.CYCLING,
    /** Whether pages show upcoming public holidays, long weekends and the next season. */
    val comingUpEnabled: Boolean = true,
    /** The user's own dates (birthdays, presentations, days off), soonest first, counted down to on the first page. */
    val personalDates: List<PersonalDate> = emptyList(),
    /** Whether Home shows tonight's sky: the moon, meteor showers and whether it's clear enough to look up. */
    val skyEnabled: Boolean = true,
    /** Whether each page shows a daily weather meme (made on the phone; Gemma writes it when available). */
    val memesEnabled: Boolean = true,
)
