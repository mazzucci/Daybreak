package com.mazzucci.weather.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun cToF(c: Double): Double = c * 9.0 / 5.0 + 32.0

fun kmhToMph(kmh: Double): Double = kmh * 0.621371

/** Rounded whole degrees in [unit]. */
fun degrees(c: Double, unit: TempUnit): Int = when (unit) {
    TempUnit.C -> c.roundToInt()
    TempUnit.F -> cToF(c).roundToInt()
}

/** "72°F" / "22°C". */
fun formatTemp(c: Double, unit: TempUnit): String = "${degrees(c, unit)}°${unit.name}"

/** "72°" — for places where the unit is already clear from context. */
fun formatDegrees(c: Double, unit: TempUnit): String = "${degrees(c, unit)}°"

/** "74°F (23°C)": both units, primary first, for accessibility labels. */
fun formatBothUnits(c: Double, unit: TempUnit): String = "${formatTemp(c, unit)} (${formatTemp(c, unit.other())})"

fun TempUnit.other(): TempUnit = if (this == TempUnit.F) TempUnit.C else TempUnit.F

/** "9 mph" or "14 km/h", following the primary temperature unit. */
fun formatWind(kmh: Double, unit: TempUnit): String = when (unit) {
    TempUnit.F -> "${kmhToMph(kmh).roundToInt()} mph"
    TempUnit.C -> "${kmh.roundToInt()} km/h"
}

/** "3 PM" style hour label. */
fun formatHour(time: LocalDateTime, locale: Locale = Locale.getDefault()): String =
    time.format(DateTimeFormatter.ofPattern("h a", locale))

/** "7:02 AM" style clock time, for sunrise and sunset. */
fun formatClock(time: LocalDateTime, locale: Locale = Locale.getDefault()): String =
    time.format(DateTimeFormatter.ofPattern("h:mm a", locale))

/** "Today", then short weekday names ("Tue") for the multi-day list. English, like the rest of the UI. */
fun formatDayLabel(date: LocalDate, today: LocalDate, locale: Locale = Locale.US): String =
    if (date == today) "Today" else date.format(DateTimeFormatter.ofPattern("EEE", locale))

/** Full weekday name for accessibility labels ("Tuesday"). */
fun formatDayName(date: LocalDate, today: LocalDate, locale: Locale = Locale.US): String =
    if (date == today) "Today" else date.format(DateTimeFormatter.ofPattern("EEEE", locale))

/** WHO UV index category for a (rounded) UV index. */
fun describeUv(uv: Double): String = when (uv.roundToInt()) {
    in Int.MIN_VALUE..2 -> "Low"
    in 3..5 -> "Moderate"
    in 6..7 -> "High"
    in 8..10 -> "Very high"
    else -> "Extreme"
}

/** "9 AM–1 PM" for an activity window. */
fun formatWindow(w: ActivityWindow, locale: Locale = Locale.getDefault()): String =
    "${formatHour(w.start, locale)}–${formatHour(w.end, locale)}"

/** "Dry · light wind · 17–21°": what the window is like, in the primary unit. */
fun describeWindow(w: ActivityWindow, unit: TempUnit): String {
    val rain = when {
        w.maxPrecipChance < 10 -> "Dry"
        else -> "${w.maxPrecipChance}% rain chance"
    }
    val wind = w.maxWindKmh?.let { if (it < 12) "calm" else if (it < 25) "light wind" else "breezy" }
    val lo = degrees(w.minTempC, unit)
    val hi = degrees(w.maxTempC, unit)
    val temp = if (lo == hi) "$lo°" else "$lo–$hi°"
    return listOfNotNull(rain, wind, temp).joinToString(" · ")
}

/** "Rain and wind": the top one or two reasons there's no good window. */
fun describeBlockers(limits: List<Limit>): String {
    val words = limits.take(2).map {
        when (it) {
            Limit.STORM -> "storms"
            Limit.SNOW -> "snow"
            Limit.RAIN -> "rain"
            Limit.WIND -> "strong wind"
            Limit.COLD -> "the cold"
            Limit.HEAT -> "the heat"
            Limit.DARK -> "darkness"
        }
    }
    return if (words.isEmpty()) "Not great conditions" else words.joinToString(" and ").replaceFirstChar { it.uppercase() }
}

/** WMO weather interpretation codes, as used by Open-Meteo. */
fun describeWeatherCode(code: Int): String = when (code) {
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
