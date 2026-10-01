package app.daybreak.domain

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
fun formatHour(time: LocalDateTime, locale: Locale = Locale.getDefault(), use24Hour: Boolean = ClockFormat.use24Hour): String =
    time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h a", locale))

/** "7:02 AM" style clock time, for sunrise and sunset. */
fun formatClock(time: LocalDateTime, locale: Locale = Locale.getDefault(), use24Hour: Boolean = ClockFormat.use24Hour): String =
    time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale))

/**
 * Whether times are written on the 24-hour clock ("18:00") or the 12-hour one ("6 PM"). Follows the phone's own
 * setting; the activity updates it whenever it comes to the foreground. False (12-hour) in tests.
 */
object ClockFormat {
    @Volatile var use24Hour: Boolean = false
}

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

/**
 * "9 AM–1 PM" for an activity window; "Now–6 PM" when it starts this hour ([startsNow]), and "From 8 AM" when it
 * runs to the end of the data, since the good weather may well carry on.
 */
fun formatWindow(w: ActivityWindow, startsNow: Boolean = false, locale: Locale = Locale.getDefault()): String {
    val start = if (startsNow) "Now" else formatHour(w.start, locale)
    return when {
        w.openEnded && startsNow -> "From now on"
        w.openEnded -> "From $start"
        else -> "$start–${formatHour(w.end, locale)}"
    }
}

/** "Dry · light wind · 17–21°": what the window is like, in the primary unit. */
fun describeWindow(w: ActivityWindow, unit: TempUnit): String =
    listOfNotNull(windowRain(w), windowWind(w), windowTemps(w, unit)).joinToString(" · ")

/** The same for screen readers: commas for pauses, "to" for ranges, and both units like the rest of the app. */
fun describeWindowSpoken(w: ActivityWindow, unit: TempUnit): String {
    val other = unit.other()
    val temps = "${windowTemps(w, unit).replace("–", " to ")}${unit.name} (${windowTemps(w, other).replace("–", " to ")}${other.name})"
    return listOfNotNull(windowRain(w), windowWind(w), temps).joinToString(", ")
}

private fun windowRain(w: ActivityWindow) = if (w.maxPrecipChance < 15) "Dry" else "${w.maxPrecipChance}% rain chance"

private fun windowWind(w: ActivityWindow) = w.maxWindKmh?.let { if (it < 12) "calm" else if (it < 25) "light wind" else "breezy" }

private fun windowTemps(w: ActivityWindow, unit: TempUnit): String {
    val lo = degrees(w.minTempC, unit)
    val hi = degrees(w.maxTempC, unit)
    return if (lo == hi) "$lo°" else "$lo–$hi°"
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

/** Feels-like is worth a line of its own once it's this many degrees (in the unit shown) from the actual temperature. */
const val FEELS_LIKE_GAP = 3

/** The feels-like temperature when it differs enough from [tempC] to mention, else null. */
fun feelsLikeWorthShowing(tempC: Double, feelsLikeC: Double?, unit: TempUnit): Double? =
    feelsLikeC?.takeIf { kotlin.math.abs(degrees(it, unit) - degrees(tempC, unit)) >= FEELS_LIKE_GAP }

private val COMPASS_SHORT = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
private val COMPASS_LONG = listOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")

/** "SW" for a direction in degrees clockwise from north (8 points: finer splits read worse than they help). */
fun compassPoint(degrees: Double): String = COMPASS_SHORT[compassIndex(degrees)]

/** "from the SW": where the wind comes from, as forecasts say it. */
fun formatWindFrom(degrees: Double): String = "from the ${compassPoint(degrees)}"

/** "from the southwest", for screen readers. */
fun formatWindFromSpoken(degrees: Double): String = "from the ${COMPASS_LONG[compassIndex(degrees)]}"

private fun compassIndex(degrees: Double): Int = (((degrees % 360 + 360) % 360 + 22.5) / 45).toInt() % 8

/** Past this, a page's forecast counts as stale: the "updated" line turns amber and suggests a refresh. */
val STALE_AFTER: java.time.Duration = java.time.Duration.ofMinutes(90)

/** "Updated just now", "Updated 8 min ago", "Updated 2 hours ago", "Updated 3 days ago". */
fun formatUpdated(fetchedAt: java.time.Instant, now: java.time.Instant): String {
    val minutes = java.time.Duration.between(fetchedAt, now).toMinutes().coerceAtLeast(0)
    val ago = when {
        minutes < 1 -> return "Updated just now"
        minutes < 60 -> "$minutes min"
        minutes < 48 * 60 -> (minutes / 60).let { if (it == 1L) "1 hour" else "$it hours" }
        else -> "${minutes / (24 * 60)} days"
    }
    return "Updated $ago ago"
}

fun isStale(fetchedAt: java.time.Instant, now: java.time.Instant): Boolean =
    java.time.Duration.between(fetchedAt, now) > STALE_AFTER

private val NUMBER_AND_UNIT = Regex("""(\d) (mm|cm|in|inch|inches|km/h|mph|h|hour|hours|AM|PM)\b""")

/** Joins each number to its unit with a no-break space, so "12 mm" or "7 PM" never splits across lines. */
fun keepUnitsTogether(text: String): String = NUMBER_AND_UNIT.replace(text) { "${it.groupValues[1]} ${it.groupValues[2]}" }
