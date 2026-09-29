package com.mazzucci.weather.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * When the user travels to and from work, on weekdays. Hours are local to the commute page's place. A return
 * hour at or before the leave hour is a night shift: the trip home is the next morning. The times are kept while
 * the check is switched off.
 */
data class CommuteSettings(val leaveHour: Int = 8, val returnHour: Int = 17, val enabled: Boolean = false) {
    init {
        require(leaveHour in 0..23 && returnHour in 0..23)
    }
}

/**
 * The call for the next workday's trips: go in, go in with a caveat, or consider working from home.
 * [outbound] is null once the trip in has already started (only the way home is left to judge).
 */
data class CommuteAdvice(
    val day: LocalDate,
    val verdict: Verdict,
    val outbound: HourScore?,
    val inbound: HourScore,
) {
    enum class Verdict { OFFICE, OFFICE_WITH_CAVEAT, WORK_FROM_HOME }
}

/**
 * Scores the next commute for [activity] with the same scorer as the "best time" card:
 * - today, if it's a workday and the trip home is still ahead (after leaving, only the trip home counts);
 * - otherwise the next workday, skipping weekends and [holidays].
 * Darkness never tips it to "work from home" (lights fix that); it's a caveat at most. Null when the forecast
 * doesn't cover the trips.
 */
fun commuteAdvice(
    forecast: Forecast,
    activity: Activity,
    commute: CommuteSettings,
    holidays: Set<LocalDate> = emptySet(),
): CommuteAdvice? {
    val now = forecast.current.time
    val thisHour = now.truncatedTo(ChronoUnit.HOURS)
    fun workday(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY && d !in holidays
    fun leaveAt(d: LocalDate) = d.atTime(commute.leaveHour, 0)
    fun backAt(d: LocalDate) = if (commute.returnHour <= commute.leaveHour) d.plusDays(1).atTime(commute.returnHour, 0) else d.atTime(commute.returnHour, 0)

    // A night shift that started yesterday may still have its trip home ahead.
    val candidates = generateSequence(now.toLocalDate().minusDays(1)) { it.plusDays(1) }.take(12)
    val day = candidates.firstOrNull { workday(it) && !thisHour.isAfter(backAt(it)) && (it >= now.toLocalDate() || commute.returnHour <= commute.leaveHour) }
        ?: return null
    val outHour = leaveAt(day).takeIf { !thisHour.isAfter(it) }?.let { hourAt(forecast, it) ?: return null }
    val backHour = hourAt(forecast, backAt(day)) ?: return null
    val out = outHour?.let { score(it, forecast, activity, isNow = it.time == thisHour) }
    val back = score(backHour, forecast, activity, isNow = backHour.time == thisHour)
    val trips = listOfNotNull(out, back)
    val worst = trips.minOf { it.score }
    val verdict = when {
        worst < POOR -> CommuteAdvice.Verdict.WORK_FROM_HOME
        worst < ActivityScorer.GOOD || trips.any { Limit.DARK in it.limits } -> CommuteAdvice.Verdict.OFFICE_WITH_CAVEAT
        else -> CommuteAdvice.Verdict.OFFICE
    }
    return CommuteAdvice(day, verdict, out, back)
}

private fun score(hour: HourForecast, forecast: Forecast, activity: Activity, isNow: Boolean): HourScore =
    ActivityScorer.score(hour, forecast, activity.profile, isNow, ignoreDark = true)

/** The forecast hour at [time], or the next one within the hour (a daylight-saving gap skips 2 AM). */
private fun hourAt(forecast: Forecast, time: LocalDateTime): HourForecast? =
    forecast.hours.firstOrNull { !it.time.isBefore(time) && it.time.isBefore(time.plusHours(2)) }

/** Below this, a trip isn't worth it: suggest working from home. */
private const val POOR = 40

/** "Office day" / "Office day, with a catch" / "Maybe work from home", plus a line saying why. */
fun describeCommute(advice: CommuteAdvice, activity: Activity, unit: TempUnit, today: LocalDate): Pair<String, String> {
    val whenLabel = when (advice.day) {
        today -> ""
        today.plusDays(1) -> "Tomorrow: "
        else -> "${advice.day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.US)}: "
    }
    val headline = whenLabel + when (advice.verdict) {
        CommuteAdvice.Verdict.OFFICE -> "office day"
        CommuteAdvice.Verdict.OFFICE_WITH_CAVEAT -> "office day, with a catch"
        CommuteAdvice.Verdict.WORK_FROM_HOME -> "maybe work from home"
    }.let { if (whenLabel.isEmpty()) it.replaceFirstChar { c -> c.uppercase() } else it }
    val mode = activity.verb
    val out = advice.outbound
    val trips = listOfNotNull(out?.let { "in" to it }, "home" to advice.inbound)
    val detail = when {
        advice.verdict == CommuteAdvice.Verdict.OFFICE && out == null ->
            "Good to $mode home: ${tripSummary(advice.inbound, unit)}."
        advice.verdict == CommuteAdvice.Verdict.OFFICE ->
            "Good to $mode both ways: ${tripSummary(out!!, unit)} going in, ${tripSummary(advice.inbound, unit)} coming home."
        else -> {
            // Name the worse trip and what's wrong with it; darkness alone only ever makes it a caveat.
            val (label, trip) = trips.minBy { it.second.score }
            val darkOnly = trip.score >= ActivityScorer.GOOD
            val (darkLabel, darkTrip) = trips.firstOrNull { Limit.DARK in it.second.limits } ?: (label to trip)
            if (darkOnly) "Dark on the way $darkLabel at ${formatHour(darkTrip.hour.time, java.util.Locale.US)}: take lights."
            else "${reasonFor(trip).replaceFirstChar { it.uppercase() }} on the way $label at ${formatHour(trip.hour.time, java.util.Locale.US)}."
        }
    }
    return headline to detail
}

private fun tripSummary(h: HourScore, unit: TempUnit): String =
    "${if (h.hour.precipChance < 15) "dry" else "${h.hour.precipChance}% rain"}, ${formatDegrees(h.hour.tempC, unit)}"

private fun reasonFor(h: HourScore): String {
    val l = h.limits
    return when {
        Limit.STORM in l -> "storms"
        Limit.SNOW in l -> "snow"
        Limit.RAIN in l -> if (h.hour.precipChance >= 50) "rain likely (${h.hour.precipChance}%)" else "a chance of rain (${h.hour.precipChance}%)"
        Limit.WIND in l -> "strong wind"
        Limit.COLD in l -> "cold"
        Limit.HEAT in l -> "heat"
        Limit.DARK in l -> "darkness"
        else -> "iffy weather"
    }
}
