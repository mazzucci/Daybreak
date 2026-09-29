package com.mazzucci.weather.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** When the user travels to and from work, on weekdays. Hours are local to the first page's place. */
data class CommuteSettings(val leaveHour: Int = 8, val returnHour: Int = 17) {
    init {
        require(leaveHour in 0..23 && returnHour in 0..23)
    }
}

/** The day's call: go in, go in with a caveat, or consider working from home. */
data class CommuteAdvice(
    val day: LocalDate,
    val verdict: Verdict,
    val outbound: HourScore,
    val inbound: HourScore,
) {
    enum class Verdict { OFFICE, OFFICE_WITH_CAVEAT, WORK_FROM_HOME }
}

/**
 * Scores the next weekday commute (today's, if the ride in hasn't started yet; otherwise the next weekday's) for
 * [activity], using the same scorer as the "best time" card. Null when the forecast doesn't cover both trips.
 */
fun commuteAdvice(forecast: Forecast, activity: Activity, commute: CommuteSettings): CommuteAdvice? {
    val now = forecast.current.time
    var day = if (now.hour < commute.leaveHour) now.toLocalDate() else now.toLocalDate().plusDays(1)
    while (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) day = day.plusDays(1)
    val out = hourAt(forecast, day.atTime(commute.leaveHour, 0)) ?: return null
    val back = hourAt(forecast, day.atTime(commute.returnHour, 0)) ?: return null
    val outScore = ActivityScorer.score(out, forecast, activity.profile)
    val backScore = ActivityScorer.score(back, forecast, activity.profile)
    val worst = minOf(outScore.score, backScore.score)
    val verdict = when {
        worst >= ActivityScorer.GOOD -> CommuteAdvice.Verdict.OFFICE
        worst >= POOR -> CommuteAdvice.Verdict.OFFICE_WITH_CAVEAT
        else -> CommuteAdvice.Verdict.WORK_FROM_HOME
    }
    return CommuteAdvice(day, verdict, outScore, backScore)
}

private fun hourAt(forecast: Forecast, time: LocalDateTime): HourForecast? = forecast.hours.firstOrNull { it.time == time }

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
    val trips = listOf("in" to advice.outbound, "home" to advice.inbound)
    val detail = if (advice.verdict == CommuteAdvice.Verdict.OFFICE) {
        "Good to $mode both ways: ${tripSummary(advice.outbound, unit)} going in, ${tripSummary(advice.inbound, unit)} coming home."
    } else {
        // Name the worse trip and what's wrong with it.
        val (label, trip) = trips.minBy { it.second.score }
        "${reasonFor(trip).replaceFirstChar { it.uppercase() }} on the way $label at ${formatHour(trip.hour.time, java.util.Locale.US)}."
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
        Limit.RAIN in l -> "rain likely (${h.hour.precipChance}%)"
        Limit.WIND in l -> "strong wind"
        Limit.COLD in l -> "cold"
        Limit.HEAT in l -> "heat"
        Limit.DARK in l -> "darkness"
        else -> "iffy weather"
    }
}
