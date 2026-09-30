package app.daybreak.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** A saved clock: a place and its time zone ([zoneId] is IANA, "Europe/Bucharest"). */
data class Clock(val id: String, val name: String, val detail: String? = null, val zoneId: String) {
    /** Null if the zone id isn't one this phone knows (never for zones from the geocoder). */
    val zone: ZoneId? get() = runCatching { ZoneId.of(zoneId) }.getOrNull()

    companion object {
        /** A place from search as a clock; null when it has no time zone, or one this phone doesn't know. */
        fun of(place: Place): Clock? = place.zoneId
            ?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
            ?.let { Clock(place.id, place.name, place.detail, it) }
    }
}

/**
 * One clock at one moment, as the row shows it: the time there, which day that is for you ("Tomorrow"), how far
 * ahead or behind ("+10 h", "same time as you"), the UTC offset ("UTC+3") and whether it's night there.
 */
data class ClockReading(
    val time: ZonedDateTime,
    val day: String,
    val offset: String,
    val utc: String,
    val night: Boolean,
    /** The offset for screen readers: "10 hours ahead". */
    val spoken: String = offset,
)

fun readClock(now: Instant, here: ZoneId, there: ZoneId): ClockReading {
    val local = now.atZone(there)
    val mine = now.atZone(here)
    val diff = local.offset.totalSeconds - mine.offset.totalSeconds
    return ClockReading(
        time = local,
        day = relativeDay(mine.toLocalDate(), local.toLocalDate()),
        offset = if (diff == 0) "same time as you" else formatOffset(diff),
        utc = formatUtc(local.offset.totalSeconds),
        night = isNightHour(local.hour),
        spoken = spokenOffset(diff),
    )
}

/** [time] on [day] where [from] is, as the moment it is in [to]. A time skipped by daylight saving moves forward. */
fun convertTime(time: LocalTime, day: LocalDate, from: ZoneId, to: ZoneId): ZonedDateTime =
    ZonedDateTime.of(day, time, from).withZoneSameInstant(to)

/** "Today", "Tomorrow", "Yesterday", from [mine]'s point of view. */
fun relativeDay(mine: LocalDate, theirs: LocalDate): String = when (ChronoUnit.DAYS.between(mine, theirs)) {
    0L -> "Today"
    1L -> "Tomorrow"
    -1L -> "Yesterday"
    else -> theirs.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)
}

/**
 * "+10 h", "−3 h", "+5½ h", "+5¾ h": whole hours with the common quarter and half hours as fractions; under an
 * hour, minutes ("+15 min", "−30 min").
 */
fun formatOffset(seconds: Int): String {
    val sign = if (seconds < 0) "−" else "+"
    val minutes = abs(seconds) / 60
    val hours = minutes / 60
    if (hours == 0) return "$sign$minutes min"
    val fraction = when (minutes % 60) {
        0 -> ""
        15 -> "¼"
        30 -> "½"
        45 -> "¾"
        else -> String.format(java.util.Locale.US, ":%02d", minutes % 60)
    }
    return "$sign$hours$fraction h"
}

/** "UTC", "UTC+3", "UTC−7", "UTC+5:30". */
fun formatUtc(seconds: Int): String {
    if (seconds == 0) return "UTC"
    val sign = if (seconds < 0) "−" else "+"
    val minutes = abs(seconds) / 60
    return "UTC$sign${minutes / 60}" + if (minutes % 60 != 0) String.format(java.util.Locale.US, ":%02d", minutes % 60) else ""
}

/** "10 hours ahead", "1 hour behind", "5 and a half hours ahead", "15 minutes behind", for screen readers. */
fun spokenOffset(seconds: Int): String {
    if (seconds == 0) return "same time as you"
    val way = if (seconds > 0) "ahead" else "behind"
    val minutes = abs(seconds) / 60
    val hours = minutes / 60
    val rest = minutes % 60
    if (hours == 0) return "$minutes minutes $way"
    val fraction = when (rest) {
        0 -> ""
        15 -> " and a quarter"
        30 -> " and a half"
        45 -> " and three quarters"
        else -> " and $rest minutes"
    }
    return "$hours$fraction ${if (hours == 1 && rest == 0) "hour" else "hours"} $way"
}

/** How [theirs] relates to [from] for a converted time: null the same day, "next day", "day before", or the weekday. */
fun dayNote(from: LocalDate, theirs: LocalDate): String? = when (ChronoUnit.DAYS.between(from, theirs)) {
    0L -> null
    1L -> "next day"
    -1L -> "day before"
    else -> theirs.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)
}

/** Without a forecast's sunrise and sunset, night is 6 PM to 6 AM. */
fun isNightHour(hour: Int): Boolean = hour < 6 || hour >= 18

/** The city part of a zone id: "America/Los_Angeles" → "Los Angeles", "America/Argentina/Buenos_Aires" → "Buenos Aires". */
fun cityOf(zone: ZoneId): String = zone.id.substringAfterLast('/').replace('_', ' ')
