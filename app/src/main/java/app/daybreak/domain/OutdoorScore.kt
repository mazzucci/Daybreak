package app.daybreak.domain

import kotlin.math.roundToInt

/** Comfortable ([idealC]) and still acceptable ([okC]) conditions for being outside. °C, km/h, %. */
data class WeatherProfile(
    val idealC: ClosedFloatingPointRange<Double>,
    val okC: ClosedFloatingPointRange<Double>,
    val maxWindKmh: Double,
    val maxGustKmh: Double,
    val maxPrecipChance: Int,
    val snowOk: Boolean,
) {
    companion object {
        /**
         * Time outside in general (a walk, the park, errands on foot): forgiving of cool weather and a little snow,
         * not of rain, storms or a gale. The "This week" outlook scores every day with it.
         */
        val OUTDOOR = WeatherProfile(
            idealC = 12.0..26.0, okC = 0.0..32.0, maxWindKmh = 35.0, maxGustKmh = 60.0, maxPrecipChance = 30, snowOk = true,
        )
    }
}

/** Why an hour lost points. */
enum class Limit { STORM, SNOW, RAIN, WIND, COLD, HEAT, DARK }

/** How good one hour is for being outside, 0–100, and what held it back. */
data class HourScore(val hour: HourForecast, val score: Int, val limits: Set<Limit>)

/** Scores single hours against a [WeatherProfile]. */
object OutdoorScorer {
    /** [isNow] uses the current conditions' daylight, so "Now" agrees with the rest of the page. */
    fun score(hour: HourForecast, forecast: Forecast, profile: WeatherProfile, isNow: Boolean = false): HourScore =
        score(hour, profile, dark = isDark(hour, forecast, isNow))

    /**
     * Scores [hour] (with the rain that falls during it) from 100 down: storms rule it out (and snow, unless the
     * profile allows it); rain, a chance over the profile's limit, temperatures outside the ideal band, and wind or
     * gusts over the limits each cost points. A [dark] hour is never more than "poor", however nice the weather.
     *
     * The weather code's rain and snow only count when the hour's own chance and amount aren't dry by
     * [Precip.classify], so the score never calls an hour wet that every other surface calls dry; a storm code with
     * dry numbers still costs [DRY_STORM] points (a storm nearby), and is still named.
     */
    fun score(hour: HourForecast, profile: WeatherProfile, dark: Boolean): HourScore {
        val limits = mutableSetOf<Limit>()
        var score = 100.0
        val dry = Precip.classify(hour.precipChance, hour.precipMm).dry
        when {
            hour.code in 95..99 && dry -> { limits += Limit.STORM; score -= DRY_STORM }
            hour.code in 95..99 -> { limits += Limit.STORM; score = 0.0 }
            dry -> Unit
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
        if (dark) {
            limits += Limit.DARK
            score = minOf(score - 50, DARK_MAX)
        }
        return HourScore(hour, score.roundToInt().coerceIn(0, 100), limits)
    }

    /**
     * Daylight at the middle of the hour, so an hour the sun rises early in (7:00 with sunrise 7:02) counts as
     * light and one it sets early in (18:00 with sunset 18:05) as dark. The current hour follows the current
     * conditions instead.
     */
    fun isDark(hour: HourForecast, forecast: Forecast, isNow: Boolean = false): Boolean =
        if (isNow) forecast.isNightNow else forecast.isNight(hour.time.plusMinutes(30))

    /** Extra cost for crossing a rain or wind limit at all, so an hour over the limit rarely still counts as good. */
    private const val LIMIT_STEP = 15.0

    /** What a storm code costs when the hour's chance and amount are dry. */
    private const val DRY_STORM = 30.0

    /** The best a dark hour can score. */
    private const val DARK_MAX = 30.0

    /** An in-band temperature penalty big enough to be worth naming as a reason. */
    private const val NOTABLE = 15.0

    private val SNOW = setOf(71, 73, 75, 77, 85, 86)
    private val RAIN = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)
}
