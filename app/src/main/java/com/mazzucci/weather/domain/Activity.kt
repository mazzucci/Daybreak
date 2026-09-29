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

/**
 * A run of consecutive good hours. [end] is exclusive (the hour after the last one). [openEnded] means the run
 * reaches the last scored hour, so it may well go on: the data ran out, not the good weather.
 */
data class ActivityWindow(val hours: List<HourScore>, val openEnded: Boolean = false) {
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

/** Scores hours for an activity and finds the best window. */
object ActivityScorer {
    const val GOOD = 70
    const val HORIZON_HOURS = 24

    /** [isNow] uses the current conditions' daylight, so "Now" agrees with the rest of the page. */
    fun score(hour: HourForecast, forecast: Forecast, profile: WeatherProfile, isNow: Boolean = false): HourScore {
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
            score -= LIMIT_STEP + (hour.precipChance - profile.maxPrecipChance) * 1.2
        }
        val t = hour.tempC
        val ideal = profile.idealC
        val ok = profile.okC
        when {
            t < ok.start -> { limits += Limit.COLD; score -= 40 + (ok.start - t) * 4 + (ideal.start - ok.start) * 3 }
            t > ok.endInclusive -> { limits += Limit.HEAT; score -= 40 + (t - ok.endInclusive) * 4 + (ok.endInclusive - ideal.endInclusive) * 3 }
            t < ideal.start -> {
                val penalty = (ideal.start - t) * 3
                score -= penalty
                if (penalty > NOTABLE) limits += Limit.COLD
            }
            t > ideal.endInclusive -> {
                val penalty = (t - ideal.endInclusive) * 3
                score -= penalty
                if (penalty > NOTABLE) limits += Limit.HEAT
            }
        }
        val windOver = hour.windKmh?.let { it - profile.maxWindKmh }?.takeIf { it > 0 }
        val gustOver = hour.gustKmh?.let { it - profile.maxGustKmh }?.takeIf { it > 0 }
        if (windOver != null || gustOver != null) {
            limits += Limit.WIND
            score -= LIMIT_STEP + (windOver ?: 0.0) * 2 + (gustOver ?: 0.0) * 1.5
        }
        // Dark hours are never more than "poor", however nice the weather: grey bars through the night.
        if (isDark(hour, forecast, isNow)) { limits += Limit.DARK; score = minOf(score - 50, DARK_MAX) }
        return HourScore(hour, score.roundToInt().coerceIn(0, 100), limits)
    }

    /**
     * Daylight at the middle of the hour, so an hour the sun rises early in (7:00 with sunrise 7:02) counts as
     * light and one it sets early in (18:00 with sunset 18:05) as dark. The current hour follows the current
     * conditions instead.
     */
    private fun isDark(hour: HourForecast, forecast: Forecast, isNow: Boolean): Boolean =
        if (isNow) forecast.isNightNow else forecast.isNight(hour.time.plusMinutes(30))

    /** Scores the next [HORIZON_HOURS] hours from the current one and picks the best window. */
    fun plan(forecast: Forecast, activity: Activity): ActivityPlan {
        val thisHour = forecast.nextHours.firstOrNull()?.time
        val ahead = if (thisHour == null) emptyList() else forecast.hours.filter { !it.time.isBefore(thisHour) }.take(HORIZON_HOURS)
        val scored = ahead.mapIndexed { i, h -> score(h, forecast, activity.profile, isNow = i == 0) }
        val candidates = windows(scored)
        // A lone good hour only wins when there's no longer window at all.
        val pool = candidates.filter { it.hours.size >= 2 }.ifEmpty { candidates }
        val best = pool.maxWithOrNull(compareBy<ActivityWindow> { value(it) }.thenByDescending { it.start })
        val blockers = if (best != null) emptyList() else blockersOf(scored)
        return ActivityPlan(activity, scored, best, blockers)
    }

    /**
     * The most common reasons, most frequent first. Darkness only counts when there's no daylight at all in the
     * scored hours (polar night, or late evening with the data running out before sunrise); then it comes first,
     * followed by whatever else those dark hours have (rain, cold…).
     */
    private fun blockersOf(scored: List<HourScore>): List<Limit> {
        val light = scored.filter { Limit.DARK !in it.limits }
        fun byCount(hours: List<HourScore>) =
            hours.flatMap { it.limits - Limit.DARK }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        if (light.isEmpty()) return if (scored.isEmpty()) emptyList() else listOf(Limit.DARK) + byCount(scored)
        return byCount(light)
    }

    /** Runs of consecutive hours (in list order) scoring at least [GOOD]. */
    fun windows(scored: List<HourScore>): List<ActivityWindow> {
        val result = mutableListOf<ActivityWindow>()
        var run = mutableListOf<HourScore>()
        for (h in scored) {
            if (h.score >= GOOD) run += h
            else if (run.isNotEmpty()) { result += ActivityWindow(run); run = mutableListOf() }
        }
        if (run.isNotEmpty()) result += ActivityWindow(run, openEnded = true)
        return result
    }

    /** Higher is better: quality, plus a bonus for length up to four hours. Ties go to the earlier window. */
    internal fun value(w: ActivityWindow): Int = w.averageScore + 8 * minOf(w.hours.size, 4)

    /** Extra cost for crossing a rain or wind limit at all, so an hour over the limit rarely still counts as good. */
    private const val LIMIT_STEP = 15.0

    /** The best a dark hour can score: below the "fair" tier. */
    private const val DARK_MAX = 30.0

    /** An in-band temperature penalty big enough to be worth naming as a reason. */
    private const val NOTABLE = 15.0

    private val SNOW = setOf(71, 73, 75, 77, 85, 86)
    private val RAIN = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)
}
