package com.mazzucci.weather.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale

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

/**
 * The card's three lines, in the shape of the "best time" card: which commute this is about ([eyebrow]), the
 * verdict ([headline]) and why ([detail], dot-separated fragments). [spokenDetail] is the same for screen
 * readers: commas for pauses, "to" for ranges, and both temperature units like the rest of the app.
 */
data class CommuteCopy(val eyebrow: String, val headline: String, val detail: String, val spokenDetail: String)

/** "Tomorrow's commute" / "Office day" / "A dry ride both ways · 61–64°", or the worse trip's problem and when. */
fun describeCommute(advice: CommuteAdvice, activity: Activity, unit: TempUnit, today: LocalDate): CommuteCopy {
    val day = when (advice.day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> advice.day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    }
    val headline = when (advice.verdict) {
        CommuteAdvice.Verdict.OFFICE -> "Office day"
        CommuteAdvice.Verdict.OFFICE_WITH_CAVEAT -> "Office day, with a catch"
        CommuteAdvice.Verdict.WORK_FROM_HOME -> "Maybe work from home"
    }
    val detail = commuteDetail(advice, activity, unit, separator = " · ") { lo, hi ->
        if (lo == hi) formatDegrees(lo, unit) else "${degrees(lo, unit)}–${degrees(hi, unit)}°"
    }
    val spoken = commuteDetail(advice, activity, unit, separator = ", ") { lo, hi ->
        if (lo == hi) formatBothUnits(lo, unit) else "${spokenRange(lo, hi, unit)} (${spokenRange(lo, hi, unit.other())})"
    }
    return CommuteCopy("$day's commute", headline, detail, spoken)
}

private fun spokenRange(lo: Double, hi: Double, unit: TempUnit) = "${degrees(lo, unit)} to ${formatTemp(hi, unit)}"

/**
 * Office day: "A dry ride both ways · 61–64°", or which trip has a rain chance when one does. Otherwise the worse
 * trip's problem, then the measure and the hour: "Rain likely on the way home · 80% at 5 PM".
 */
private fun commuteDetail(
    advice: CommuteAdvice,
    activity: Activity,
    unit: TempUnit,
    separator: String,
    temps: (lo: Double, hi: Double) -> String,
): String {
    val out = advice.outbound?.hour
    val back = advice.inbound.hour
    val trips = listOfNotNull(advice.outbound?.let { "in" to it }, "home" to advice.inbound)
    if (advice.verdict == CommuteAdvice.Verdict.OFFICE) {
        val rain = when {
            // Already on the way in: only the trip home is left to judge.
            out == null -> if (back.precipChance < DRY) "A dry ${activity.verb} home" else "${rainWord(back)} coming home".replaceFirstChar { it.uppercase() }
            out.precipChance < DRY && back.precipChance < DRY -> "A dry ${activity.verb} both ways"
            else -> "${rainWord(out)} going in, ${rainWord(back)} coming home".replaceFirstChar { it.uppercase() }
        }
        val all = listOfNotNull(out, back)
        return "$rain$separator${temps(all.minOf { it.tempC }, all.maxOf { it.tempC })}"
    }
    val worst = trips.minBy { it.second.score }
    // Darkness is only ever a caveat: when it's the only catch, say so and suggest lights.
    val (leg, trip) = if (worst.second.score >= ActivityScorer.GOOD) trips.firstOrNull { Limit.DARK in it.second.limits } ?: worst else worst
    val hour = formatHour(trip.hour.time, Locale.US)
    val l = trip.limits
    val h = trip.hour
    val (problem, measure) = when {
        Limit.STORM in l -> "Storms" to null
        Limit.SNOW in l -> "Snow" to null
        Limit.RAIN in l -> (if (h.precipChance >= LIKELY) "Rain likely" else "Rain possible") to "${h.precipChance}%"
        Limit.WIND in l -> "Strong wind" to windMeasure(h, unit)
        Limit.COLD in l -> "Cold" to temps(h.tempC, h.tempC)
        Limit.HEAT in l -> "Heat" to temps(h.tempC, h.tempC)
        Limit.DARK in l -> "Dark" to "take lights"
        else -> "Iffy weather" to null
    }
    return "$problem on the way $leg$separator${if (measure != null) "$measure at $hour" else hour}"
}

private fun rainWord(h: HourForecast) = if (h.precipChance < DRY) "dry" else "${h.precipChance}% rain chance"

/** Gusts when they're what's notable, otherwise the sustained wind; null when the forecast has neither. */
private fun windMeasure(h: HourForecast, unit: TempUnit): String? {
    val wind = h.windKmh
    val gust = h.gustKmh
    return when {
        gust != null && (wind == null || gust > wind) -> "gusts ${formatWind(gust, unit)}"
        wind != null -> formatWind(wind, unit)
        else -> null
    }
}

/** Under this rain chance a trip counts as dry, matching the "best time" card. */
private const val DRY = 15

/** From this rain chance on, "rain likely" rather than "rain possible". */
private const val LIKELY = 60
