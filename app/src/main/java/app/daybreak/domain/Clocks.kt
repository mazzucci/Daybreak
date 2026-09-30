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
        /** A place from search as a clock; null when it has no time zone. */
        fun of(place: Place): Clock? = place.zoneId?.let { Clock(place.id, place.name, place.detail, it) }
    }
}

/**
 * One clock at one moment, as the row shows it: the time there, which day that is for you ("Tomorrow"), how far
 * ahead or behind ("+10 h", "same time as you"), the UTC offset ("UTC+3") and whether it's night there.
 */
data class ClockReading(val time: ZonedDateTime, val day: String, val offset: String, val utc: String, val night: Boolean)

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

/** "+10 h", "−3 h", "+5½ h", "+5¾ h", "−3½ h": whole hours with the common quarter and half hours as fractions. */
fun formatOffset(seconds: Int): String {
    val sign = if (seconds < 0) "−" else "+"
    val minutes = abs(seconds) / 60
    val hours = minutes / 60
    val fraction = when (minutes % 60) {
        0 -> ""
        15 -> "¼"
        30 -> "½"
        45 -> "¾"
        else -> ":%02d".format(minutes % 60)
    }
    return "$sign$hours$fraction h"
}

/** "UTC", "UTC+3", "UTC−7", "UTC+5:30". */
fun formatUtc(seconds: Int): String {
    if (seconds == 0) return "UTC"
    val sign = if (seconds < 0) "−" else "+"
    val minutes = abs(seconds) / 60
    return "UTC$sign${minutes / 60}" + if (minutes % 60 != 0) ":%02d".format(minutes % 60) else ""
}

/** Without a forecast's sunrise and sunset, night is 6 PM to 6 AM. */
fun isNightHour(hour: Int): Boolean = hour < 6 || hour >= 18

/** The city part of a zone id: "America/Los_Angeles" → "Los Angeles", "America/Argentina/Buenos_Aires" → "Buenos Aires". */
fun cityOf(zone: ZoneId): String = zone.id.substringAfterLast('/').replace('_', ' ')
