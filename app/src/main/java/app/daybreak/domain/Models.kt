package app.daybreak.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** Days of forecast fetched per place (today included). Open-Meteo allows up to 16. */
const val FORECAST_DAYS = 10

/** Length of the hourly strip and of the window the summary describes. */
const val NEXT_HOURS = 12

/** Length of the multi-day list on each page (today included). */
const val LIST_DAYS = 10

/** Days in the list from this one on (0 = today) are further out than a week, so they're drawn as less certain. */
const val LESS_CERTAIN_FROM = 7

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
    fun upcomingDays(count: Int = LIST_DAYS): List<DaySummary> =
        days.filter { !it.date.isBefore(today.date) }.take(count)

    /**
     * The hours of [date], stamped midnight to 11 PM (fewer if the data starts or ends that day). Their rain covers 11 PM
     * the evening before to 11 PM, the same hours as Open-Meteo's daily sums (see [Precip]).
     */
    fun hoursOf(date: LocalDate): List<HourForecast> = hours.filter { it.time.toLocalDate() == date }

    private val byTime: Map<LocalDateTime, HourForecast> by lazy { hours.associateBy { it.time } }

    /** The hour stamped [time], if the forecast has it. */
    fun hourAt(time: LocalDateTime): HourForecast? = byTime[time]

    /**
     * The hour whose precipitation, snowfall and chance fall during [hour]: Open-Meteo stamps them at the end of
     * the hour they cover, so it's the one stamped an hour later. Null past the end of the data.
     */
    fun rainDuring(hour: HourForecast): HourForecast? = hourAt(hour.time.plusHours(1))

    /** The day with this date, if the forecast has it. */
    fun day(date: LocalDate): DaySummary? = days.firstOrNull { it.date == date }

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
    /** Direction the wind blows from now, degrees clockwise from north; null if missing. */
    val windDirectionDeg: Double? = null,
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
    /** Hours with any precipitation (Open-Meteo's precipitation_hours). */
    val precipHours: Double? = null,
    /** Total snowfall, cm (not water equivalent). */
    val snowSumCm: Double? = null,
    /** Direction the wind mostly blows from, degrees clockwise from north. */
    val windDirectionDeg: Double? = null,
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
    /** Chance of at least 0.1 mm in the hour ending at [time], 0–100. */
    val precipChance: Int,
    val code: Int,
    /** Sustained wind and gusts at 10 m, km/h; null if missing. */
    val windKmh: Double? = null,
    val gustKmh: Double? = null,
    /** Open-Meteo's is_day flag for the start of the hour; null if missing. */
    val isDay: Boolean? = null,
    /** Apparent ("feels like") temperature, °C; null if missing. */
    val feelsLikeC: Double? = null,
    /**
     * Precipitation (rain, showers and snow as water) over the hour ending at [time], mm; null if missing. Like
     * [precipChance] and [snowCm], it describes the hour before the stamp (see [Precip]).
     */
    val precipMm: Double? = null,
    /** Snowfall over the hour ending at [time], cm; null if missing. */
    val snowCm: Double? = null,
    /** Direction the wind blows from, degrees clockwise from north; null if missing. */
    val windDirectionDeg: Double? = null,
)

enum class TempUnit { F, C }

data class AppSettings(
    /** The unit shown large; the other one is shown small next to it. */
    val primaryUnit: TempUnit = TempUnit.F,
    /** Whether the device location gets its own page (first) in the pager. */
    val useCurrentLocation: Boolean = true,
    /** Whether the on-device Gemma model writes the daily meme (needs an installed model). */
    val gemmaEnabled: Boolean = true,
    /** Whether pages show upcoming public holidays, long weekends and the next season. */
    val comingUpEnabled: Boolean = true,
    /** The user's own dates (birthdays, presentations, days off), soonest first, counted down to on the first page. */
    val personalDates: List<PersonalDate> = emptyList(),
    /** Whether Home shows tonight's sky: the moon, meteor showers and whether it's clear enough to look up. */
    val skyEnabled: Boolean = true,
    /** Whether each page shows a daily weather meme (made on the phone; Gemma writes it when available). */
    val memesEnabled: Boolean = true,
    /** Whether Home shows today's habits to tap (once there's a habit). */
    val habitsOnHome: Boolean = true,
    /** Whether Home shows a cheerful moment from today's date in history, from Wikipedia. */
    val onThisDayEnabled: Boolean = true,
)
