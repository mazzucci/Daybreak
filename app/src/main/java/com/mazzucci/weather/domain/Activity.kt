package com.mazzucci.weather.domain

import java.time.LocalDateTime
import kotlin.math.roundToInt

/** Outdoor activities the app finds good weather windows for. */
enum class Activity(val label: String, val verb: String, val profile: WeatherProfile) {
    CYCLING(
        "Cycling", "ride",
        WeatherProfile(idealC = 12.0..24.0, okC = 4.0..30.0, maxWindKmh = 25.0, maxGustKmh = 40.0, maxPrecipChance = 20, snowOk = false),
    ),
    RUNNING(
        "Running", "run",
        WeatherProfile(idealC = 6.0..18.0, okC = -2.0..26.0, maxWindKmh = 30.0, maxGustKmh = 50.0, maxPrecipChance = 30, snowOk = true),
    ),
    WALKING(
        "Walking", "walk",
        WeatherProfile(idealC = 12.0..26.0, okC = 0.0..32.0, maxWindKmh = 35.0, maxGustKmh = 60.0, maxPrecipChance = 30, snowOk = true),
    ),
}

/** Comfortable ([idealC]) and still acceptable ([okC]) conditions for an activity. °C, km/h, %. */
data class WeatherProfile(
    val idealC: ClosedFloatingPointRange<Double>,
    val okC: ClosedFloatingPointRange<Double>,
    val maxWindKmh: Double,
    val maxGustKmh: Double,
    val maxPrecipChance: Int,
    val snowOk: Boolean,
)

/** Why an hour lost points. */
enum class Limit { STORM, SNOW, RAIN, WIND, COLD, HEAT, DARK }

/** How good one hour is for the activity, 0–100, and what held it back. */
data class HourScore(val hour: HourForecast, val score: Int, val limits: Set<Limit>)

/** A run of consecutive good hours. [end] is exclusive (the hour after the last one). */
data class ActivityWindow(val hours: List<HourScore>) {
    init {
        require(hours.isNotEmpty())
    }

    val start: LocalDateTime get() = hours.first().hour.time
    val end: LocalDateTime get() = hours.last().hour.time.plusHours(1)
    val averageScore: Int get() = hours.map { it.score }.average().roundToInt()
    val minTempC: Double get() = hours.minOf { it.hour.tempC }
    val maxTempC: Double get() = hours.maxOf { it.hour.tempC }
    val maxPrecipChance: Int get() = hours.maxOf { it.hour.precipChance }
    val maxWindKmh: Double? get() = hours.mapNotNull { it.hour.windKmh }.maxOrNull()
}

/** The activity outlook for a page: every scored hour ahead, the best window (if any) and what's in the way. */
data class ActivityPlan(
    val activity: Activity,
    val hours: List<HourScore>,
    val best: ActivityWindow?,
    /** The most common reasons daylight hours fell short, most frequent first; empty when there's a window. */
    val blockers: List<Limit>,
)

/**
 * Scores hours for an activity and finds the best window. Hours are walked in list order rather than by clock
 * arithmetic, so a daylight-saving day (a missing or repeated local hour) still gives sensible windows.
 */
object ActivityScorer {
    const val GOOD = 70
    const val HORIZON_HOURS = 24

    fun score(hour: HourForecast, forecast: Forecast, profile: WeatherProfile): HourScore {
        val limits = mutableSetOf<Limit>()
        var score = 100.0
        when {
            hour.code in 95..99 -> { limits += Limit.STORM; score = 0.0 }
            hour.code in SNOW && !profile.snowOk -> { limits += Limit.SNOW; score = 0.0 }
            hour.code in SNOW -> { limits += Limit.SNOW; score -= 35 }
            hour.code in RAIN -> { limits += Limit.RAIN; score -= 45 }
        }
        if (hour.precipChance > profile.maxPrecipChance) {
            limits += Limit.RAIN
            score -= (hour.precipChance - profile.maxPrecipChance) * 1.2
        }
        val t = hour.tempC
        val ideal = profile.idealC
        val ok = profile.okC
        when {
            t < ok.start -> { limits += Limit.COLD; score -= 40 + (ok.start - t) * 4 + (ideal.start - ok.start) * 3 }
            t > ok.endInclusive -> { limits += Limit.HEAT; score -= 40 + (t - ok.endInclusive) * 4 + (ok.endInclusive - ideal.endInclusive) * 3 }
            t < ideal.start -> score -= (ideal.start - t) * 3
            t > ideal.endInclusive -> score -= (t - ideal.endInclusive) * 3
        }
        hour.windKmh?.let { w ->
            if (w > profile.maxWindKmh) { limits += Limit.WIND; score -= (w - profile.maxWindKmh) * 2 }
        }
        hour.gustKmh?.let { g ->
            if (g > profile.maxGustKmh) { limits += Limit.WIND; score -= (g - profile.maxGustKmh) * 1.5 }
        }
        if (forecast.isNight(hour)) { limits += Limit.DARK; score -= 50 }
        return HourScore(hour, score.roundToInt().coerceIn(0, 100), limits)
    }

    /** Scores the next [HORIZON_HOURS] hours from the current one and picks the best window. */
    fun plan(forecast: Forecast, activity: Activity): ActivityPlan {
        val thisHour = forecast.nextHours.firstOrNull()?.time
        val ahead = if (thisHour == null) emptyList() else forecast.hours.filter { !it.time.isBefore(thisHour) }.take(HORIZON_HOURS)
        val scored = ahead.map { score(it, forecast, activity.profile) }
        val best = windows(scored).maxWithOrNull(compareBy<ActivityWindow> { value(it) }.thenByDescending { it.start })
        val blockers = if (best != null) emptyList() else scored
            .filter { Limit.DARK !in it.limits }
            .flatMap { it.limits }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
        return ActivityPlan(activity, scored, best, blockers)
    }

    /** Runs of consecutive hours scoring at least [GOOD]. */
    fun windows(scored: List<HourScore>): List<ActivityWindow> {
        val result = mutableListOf<ActivityWindow>()
        var run = mutableListOf<HourScore>()
        for (h in scored) {
            if (h.score >= GOOD) run += h
            else if (run.isNotEmpty()) { result += ActivityWindow(run); run = mutableListOf() }
        }
        if (run.isNotEmpty()) result += ActivityWindow(run)
        return result
    }

    /** Higher is better: quality first, then a bonus for length up to four hours. Ties go to the earlier window. */
    private fun value(w: ActivityWindow): Int = w.averageScore + 5 * minOf(w.hours.size, 4)

    private val SNOW = setOf(71, 73, 75, 77, 85, 86)
    private val RAIN = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)
}
