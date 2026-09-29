package com.mazzucci.weather.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Kinds of workout the log names; everything else is "Workout". */
enum class ExerciseKind(val label: String) { RIDE("Ride"), RUN("Run"), WALK("Walk"), HIKE("Hike"), OTHER("Workout") }

/** One recorded workout, as another app (e.g. Garmin Connect) wrote it to Health Connect. */
data class Exercise(
    val id: String,
    val kind: ExerciseKind,
    val title: String?,
    val start: Instant,
    val end: Instant,
    /** Package that recorded it, e.g. "com.garmin.android.apps.connectmobile". */
    val source: String?,
    /** A stationary bike, treadmill, pool, gym session…: the weather outside doesn't apply. */
    val indoor: Boolean = false,
    /** The UTC offset where it was recorded (from the watch app), for its local time and to spot travel. */
    val zoneOffsetSeconds: Int? = null,
) {
    val duration: Duration get() = Duration.between(start, end)
}

/** A workout with the weather during it, at the place the log is for. Nulls when the hours are missing. */
data class ExerciseWeather(
    val exercise: Exercise,
    /** Recorded in another time zone than the log's place: its weather would be somewhere else's. */
    val elsewhere: Boolean = false,
    val minTempC: Double?,
    val maxTempC: Double?,
    val rainMm: Double,
    val maxWindKmh: Double?,
    /** The most severe WMO code during the workout, for the icon. */
    val code: Int?,
)

/**
 * Pairs each workout with the hours it overlapped (rain through its last hour, as in the ride replay). A workout
 * recorded more than an hour's offset away from [placeOffsetSeconds] (a trip) gets no weather rather than the
 * wrong place's; an hour's leeway allows for daylight saving.
 */
fun withWeather(exercises: List<Exercise>, hours: List<RideHour>, placeOffsetSeconds: Int? = null): List<ExerciseWeather> = exercises.map { e ->
    if (e.indoor) return@map ExerciseWeather(e, false, null, null, 0.0, null, null)
    val off = e.zoneOffsetSeconds
    if (off != null && placeOffsetSeconds != null && kotlin.math.abs(off - placeOffsetSeconds) > 3600) {
        return@map ExerciseWeather(e, true, null, null, 0.0, null, null)
    }
    val near = hours.filter { !it.time.isBefore(e.start.minusSeconds(1800)) && !it.time.isAfter(e.end.plusSeconds(1800)) }
    val rain = hours.filter { it.time.isAfter(e.start) && it.time.isBefore(e.end.plusSeconds(3600)) }
    ExerciseWeather(
        exercise = e,
        elsewhere = false,
        minTempC = near.minOfOrNull { it.tempC },
        maxTempC = near.maxOfOrNull { it.tempC },
        rainMm = rain.sumOf { it.precipitationMm },
        maxWindKmh = near.maxOfOrNull { it.windKmh },
        code = near.maxOfOrNull { it.code },
    )
}

/** A workout counts as wet from this much rain, mm. */
const val WET_MM = 0.2

/**
 * The log's headline: "9 workouts in 30 days", "· all indoors" when none were outside, and "· windiest 35 km/h"
 * when it blew hard. The rain count and the coldest workout are the tiles under it, so they aren't repeated here.
 */
fun describeLog(items: List<ExerciseWeather>, days: Int, unit: TempUnit): String {
    if (items.isEmpty()) return "No workouts in the last $days days"
    val parts = mutableListOf("${items.size} ${if (items.size == 1) "workout" else "workouts"} in $days days")
    if (items.all { it.exercise.indoor }) parts += "all indoors"
    items.mapNotNull { it.maxWindKmh }.maxOrNull()?.takeIf { it >= 30 }?.let { parts += "windiest ${formatWind(it, unit)}" }
    return parts.joinToString(" · ")
}

/** One calendar week (Monday to Sunday) of the log, newest workout first. */
data class LogWeek(val label: String, val items: List<ExerciseWeather>) {
    val duration: Duration get() = items.fold(Duration.ZERO) { sum, it -> sum + it.exercise.duration }
}

/** The log cut into weeks, newest first: "This week", "Last week", then "Sep 7–13" or "Aug 31 – Sep 6". */
fun groupByWeek(items: List<ExerciseWeather>, today: LocalDate, zone: ZoneId): List<LogWeek> {
    val thisMonday = today.with(DayOfWeek.MONDAY)
    return items
        .groupBy { it.exercise.start.atZone(zone).toLocalDate().with(DayOfWeek.MONDAY) }
        .toSortedMap(reverseOrder())
        .map { (monday, week) -> LogWeek(weekLabel(monday, thisMonday), week.sortedByDescending { it.exercise.start }) }
}

private fun weekLabel(monday: LocalDate, thisMonday: LocalDate): String {
    val sunday = monday.plusDays(6)
    val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)
    return when {
        monday == thisMonday -> "This week"
        monday == thisMonday.minusWeeks(1) -> "Last week"
        monday.month == sunday.month -> "${monday.format(monthDay)}–${sunday.dayOfMonth}"
        else -> "${monday.format(monthDay)} – ${sunday.format(monthDay)}"
    }
}

/** "1h 05m" or "42 min". */
fun formatDuration(d: Duration): String {
    val minutes = d.toMinutes().coerceAtLeast(0)
    return if (minutes < 60) "$minutes min" else "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"
}

/** [formatDuration] spelled out for TalkBack: "1 hour 5 minutes", "42 minutes". */
fun spokenDuration(d: Duration): String {
    val minutes = d.toMinutes().coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { "$it hour${if (it == 1L) "" else "s"}" },
        "$m minute${if (m == 1L) "" else "s"}".takeIf { m > 0 || h == 0L },
    ).joinToString(" ")
}

