package com.mazzucci.weather.domain

import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Weather terms the app can explain when their tile is tapped. */
enum class Term { FEELS_LIKE, HUMIDITY, WIND, UV, SUN, DAYLIGHT, RAIN_CHANCE }

/**
 * A plain-language explanation of a term and of today's value.
 *
 * [value] is today's value, shown large ("68°F", "6", "11h 54m"), with [detail] in small text under it ("20°C",
 * "Today's peak: High"). [now] says what the value means today and [meaning] what the term means in general.
 * [gauge] is an optional picture of where the value sits. [spoken] replaces [value] and [detail] for screen
 * readers when they wouldn't read aloud well.
 */
data class Explanation(
    val title: String,
    val value: String,
    val detail: String?,
    val now: String,
    val meaning: String,
    val gauge: Gauge? = null,
    val spoken: String? = null,
)

/** A picture of today's value for the explanation sheet. */
sealed interface Gauge {
    /** Where the value sits between the [low] and [high] ends of a scale, as a [fraction] from 0 to 1. */
    data class Scale(val fraction: Float, val low: String, val high: String, val kind: Kind) : Gauge {
        /** What the high end of the scale means: more sun, heat or wind, or more water. */
        enum class Kind { INTENSITY, MOISTURE }
    }

    /** Today's sun arc, with where [now] falls between [sunrise] and [sunset]. */
    data class Sun(val sunrise: LocalDateTime, val sunset: LocalDateTime, val now: LocalDateTime) : Gauge {
        /** How far through the day the sun is: 0 at sunrise, 1 at sunset; null while it's below the horizon. */
        val progress: Float?
            get() {
                val day = Duration.between(sunrise, sunset).toMillis().toFloat()
                val elapsed = Duration.between(sunrise, now).toMillis().toFloat()
                return (elapsed / day).takeIf { it in 0f..1f }
            }
    }
}

/** Top of the wind scale: the upper end of gale force (Beaufort 8), km/h. */
private const val GALE_KMH = 75.0

/** Top of the UV scale: "extreme" starts at 11. */
private const val UV_EXTREME = 11f

/**
 * Explains [term] with this forecast's values, in the primary [unit]. Hand-written and built from the data (no
 * model involved), so every number matches the page.
 */
fun explain(term: Term, forecast: Forecast, unit: TempUnit, locale: Locale = Locale.US): Explanation {
    val cur = forecast.current
    val today = forecast.today
    return when (term) {
        Term.FEELS_LIKE -> {
            val diff = degrees(cur.feelsLikeC, unit) - degrees(cur.tempC, unit)
            val air = formatTemp(cur.tempC, unit)
            val why = when {
                abs(diff) <= 1 -> "About the same as the air temperature: not much wind or humidity to change how it feels."
                diff < 0 && cur.windKmh >= 15 -> "Colder than the air ($air): the ${formatWind(cur.windKmh, unit)} wind carries heat away from your skin."
                diff < 0 -> "Colder than the air ($air): without sunshine on you (in the shade, under cloud or after dark), even a light breeze feels cool."
                cur.tempC >= 20 && dewPointC(cur.tempC, cur.humidity) >= MUGGY_DEW_POINT_C ->
                    "Warmer than the air ($air): the air is muggy, so sweat evaporates slowly and your body cools less."
                else -> "Warmer than the air ($air): sunshine, still air or humidity make it feel warmer."
            }
            Explanation(
                title = "Feels like",
                value = formatTemp(cur.feelsLikeC, unit),
                detail = formatTemp(cur.feelsLikeC, unit.other()),
                now = why,
                meaning = "How the temperature feels on your skin once wind, humidity and sunshine are taken into account. It's what to dress for.",
                spoken = formatBothUnits(cur.feelsLikeC, unit),
            )
        }
        Term.HUMIDITY -> {
            val (word, feel) = when {
                cur.humidity < 30 -> "Dry" to "Lips and skin may feel it."
                cur.humidity < 60 -> "Comfortable" to "Neither dry nor sticky."
                cur.tempC >= 20 && dewPointC(cur.tempC, cur.humidity) >= MUGGY_DEW_POINT_C ->
                    "Humid" to "Warm and sticky: sweat evaporates slowly, so it feels hotter than the number."
                cur.tempC >= 20 -> "Comfortable" to "Plenty of moisture for this warmth, but not muggy."
                else -> "Damp" to "In cool weather it makes the air feel raw and chilly."
            }
            Explanation(
                title = "Humidity",
                value = "${cur.humidity}%",
                detail = word,
                now = feel,
                meaning = "How much water vapour the air holds, as a share of the most it could hold at this temperature (relative humidity).",
                gauge = Gauge.Scale(cur.humidity / 100f, "Dry", "Humid", Gauge.Scale.Kind.MOISTURE),
            )
        }
        Term.WIND -> {
            val kmh = cur.windKmh
            val beaufort = when {
                kmh < 2 -> "Calm: smoke rises straight up."
                kmh < 12 -> "Light air to a light breeze: you feel it on your face and leaves rustle."
                kmh < 29 -> "A gentle to moderate breeze: small branches move and loose paper blows around."
                kmh < 50 -> "A fresh to strong breeze: large branches sway and umbrellas are hard to use."
                kmh < 62 -> "Near gale: whole trees move and walking into it takes effort."
                else -> "Gale force: twigs break off trees and walking against it is hard. Take care outside."
            }
            // Same rule as the Wind tile, so the two never disagree about gusts.
            val gust = forecast.nextHours.firstOrNull()?.gustKmh?.takeIf { it > kmh }
            Explanation(
                title = "Wind",
                value = formatWind(kmh, unit),
                detail = gust?.let { "Gusts to ${formatWind(it, unit)}" },
                now = beaufort,
                meaning = "The average wind speed 10 metres above the ground. Gusts are brief bursts that can be much stronger.",
                gauge = Gauge.Scale((kmh / GALE_KMH).toFloat().coerceIn(0f, 1f), "Calm", "Gale", Gauge.Scale.Kind.INTENSITY),
            )
        }
        Term.UV -> {
            val uv = today.uvIndexMax
            val advice = when (uv?.roundToInt()) {
                null -> "No UV forecast for today."
                in Int.MIN_VALUE..2 -> "No protection needed for most people."
                in 3..5 -> "Sunscreen and sunglasses if you're out for a while around midday."
                in 6..7 -> "Sunscreen, a hat and shade around midday: fair skin can burn in about 20 to 30 minutes."
                in 8..10 -> "Avoid the midday sun: fair skin can burn in about 15 to 20 minutes."
                else -> "Stay in the shade around midday: skin can burn in minutes."
            }
            Explanation(
                title = "UV index",
                value = uv?.roundToInt()?.toString() ?: "–",
                detail = uv?.let { "Today's peak: ${describeUv(it)}" },
                now = advice,
                meaning = "The strength of the sun's burning ultraviolet rays, on a scale from 0 upwards. It peaks around midday and is highest in summer and at altitude. Clouds cut it less than you'd think.",
                gauge = uv?.let { Gauge.Scale((it / UV_EXTREME).toFloat().coerceIn(0f, 1f), "Low", "Extreme", Gauge.Scale.Kind.INTENSITY) },
            )
        }
        Term.SUN, Term.DAYLIGHT -> {
            val meaning = "When the top of the sun crosses the horizon, in the place's local time. It stays light for a while after sunset (twilight)."
            when (today.daylight) {
                Daylight.NORMAL -> {
                    val sunrise = today.sunrise!!
                    val sunset = today.sunset!!
                    val minutes = Duration.between(sunrise, sunset).toMinutes()
                    val tomorrow = forecast.days.firstOrNull { it.date == today.date.plusDays(1) }?.takeIf { it.daylight == Daylight.NORMAL }
                    val change = tomorrow?.let { Duration.between(it.sunrise, it.sunset).toMinutes() - minutes }
                    val next = when {
                        cur.time.isBefore(sunrise) -> countdown("Sunrise", cur.time, sunrise)
                        cur.time.isBefore(sunset) -> countdown("Sunset", cur.time, sunset)
                        tomorrow != null -> countdown("Sunrise", cur.time, tomorrow.sunrise!!)
                        else -> "The sun has set for today."
                    }
                    val trend = when {
                        change == null || change == 0L -> ""
                        change > 0 -> " Tomorrow gets ${plural(change, "minute")} more."
                        else -> " Tomorrow gets ${plural(-change, "minute")} less."
                    }
                    Explanation(
                        title = "Sunrise and sunset",
                        value = formatHoursMinutesShort(minutes),
                        detail = "Daylight today",
                        now = next + trend,
                        meaning = meaning,
                        gauge = Gauge.Sun(sunrise, sunset, cur.time),
                        spoken = "${formatHoursMinutes(minutes)} of daylight today",
                    )
                }
                Daylight.POLAR_NIGHT -> Explanation(
                    title = "Daylight",
                    value = "None",
                    detail = "Polar night",
                    now = "The sun stays below the horizon all day, so there's no sunrise or sunset. Twilight can still bring a little light around midday.",
                    meaning = meaning,
                )
                Daylight.MIDNIGHT_SUN -> Explanation(
                    title = "Daylight",
                    value = "24 hours",
                    detail = "Midnight sun",
                    now = "The sun stays above the horizon all day, so there's no sunset. It's still lowest around midnight.",
                    meaning = meaning,
                )
                Daylight.UNKNOWN -> Explanation(
                    title = "Sunrise and sunset",
                    value = "–",
                    detail = null,
                    now = "No sunrise or sunset times for today.",
                    meaning = meaning,
                )
            }
        }
        Term.RAIN_CHANCE -> {
            val p = today.precipChance
            val amount = today.precipSumMm?.takeIf { it >= 0.1 }?.let { "About ${formatPrecip(it, unit, locale)} expected in total. " } ?: ""
            val umbrella = when {
                p >= 70 -> "Take an umbrella."
                p >= 30 -> "Worth having an umbrella nearby."
                else -> "Unlikely to need an umbrella."
            }
            Explanation(
                title = "Chance of rain",
                value = "$p%",
                detail = "Highest hourly chance today",
                now = amount + umbrella,
                meaning = "The chance that at least a little rain (or snow) falls at this place in a given hour. We show today's highest hour, so the chance of some rain at some point today can be higher. It says nothing about how long it rains or how heavy it is.",
                gauge = Gauge.Scale(p / 100f, "0%", "100%", Gauge.Scale.Kind.MOISTURE),
            )
        }
    }
}

/** "Sunset in 4 hours 26 minutes." or "Sunset now." */
private fun countdown(event: String, from: LocalDateTime, to: LocalDateTime): String {
    val minutes = Duration.between(from, to).toMinutes()
    return if (minutes < 1) "$event now." else "$event in ${formatHoursMinutes(minutes)}."
}

/** "4 hours 26 minutes", "1 hour", "45 minutes". */
private fun formatHoursMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { plural(it, "hour") },
        m.takeIf { it > 0 || h == 0L }?.let { plural(it, "minute") },
    ).joinToString(" ")
}

/** "11h 54m", "12h", "45m": short enough to show large. */
private fun formatHoursMinutesShort(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return listOfNotNull(h.takeIf { it > 0 }?.let { "${it}h" }, m.takeIf { it > 0 || h == 0L }?.let { "${it}m" }).joinToString(" ")
}

/** Dew point by the Magnus formula; mugginess follows it far better than relative humidity. */
private fun dewPointC(tempC: Double, humidity: Int): Double {
    val a = 17.62
    val b = 243.12
    val alpha = kotlin.math.ln(humidity.coerceAtLeast(1) / 100.0) + a * tempC / (b + tempC)
    return b * alpha / (a - alpha)
}

/** From about this dew point, warm air feels sticky. */
private const val MUGGY_DEW_POINT_C = 16.0

private fun plural(n: Long, word: String) = "$n $word${if (n == 1L) "" else "s"}"

/** Rain total in the unit system that goes with [unit]: "6.5 mm" for °C, "0.26 inches" for °F. */
private fun formatPrecip(mm: Double, unit: TempUnit, locale: Locale): String = when (unit) {
    TempUnit.C -> "${String.format(locale, "%.1f", mm)} mm"
    TempUnit.F -> "${String.format(locale, "%.2f", mm / 25.4)} inches"
}
