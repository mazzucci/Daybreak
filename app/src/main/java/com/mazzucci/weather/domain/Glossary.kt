package com.mazzucci.weather.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Weather terms the app can explain when their tile is tapped. */
enum class Term { FEELS_LIKE, HUMIDITY, WIND, UV, SUN, DAYLIGHT, RAIN_CHANCE }

/** A plain-language explanation: what the term means in general, and what today's value means. */
data class Explanation(val title: String, val meaning: String, val now: String)

/**
 * Explains [term] with this forecast's values, in the primary [unit]. Hand-written and checked against the data,
 * so it's always right; no model involved.
 */
fun explain(term: Term, forecast: Forecast, unit: TempUnit, locale: Locale = Locale.US): Explanation {
    val cur = forecast.current
    val today = forecast.today
    return when (term) {
        Term.FEELS_LIKE -> {
            val diff = degrees(cur.feelsLikeC, unit) - degrees(cur.tempC, unit)
            val why = when {
                abs(diff) <= 1 -> "About the same as the air temperature: not much wind or humidity to change how it feels."
                diff < 0 && cur.windKmh >= 15 -> "Colder than the air (${formatTemp(cur.tempC, unit)}): the ${formatWind(cur.windKmh, unit)} wind carries heat away from your skin."
                diff < 0 -> "Colder than the air (${formatTemp(cur.tempC, unit)}): a breeze or dry air takes heat away from your skin."
                cur.humidity >= 60 -> "Warmer than the air (${formatTemp(cur.tempC, unit)}): with ${cur.humidity}% humidity, sweat evaporates slowly, so your body cools less."
                else -> "Warmer than the air (${formatTemp(cur.tempC, unit)}): sunshine and little wind make it feel warmer."
            }
            Explanation(
                "Feels like",
                "How the temperature feels on your skin once wind and humidity are taken into account. It's what to dress for.",
                "Now ${formatTemp(cur.feelsLikeC, unit)}. $why",
            )
        }
        Term.HUMIDITY -> {
            val feel = when {
                cur.humidity < 30 -> "Dry: lips and skin may feel it."
                cur.humidity < 60 -> "Comfortable."
                cur.tempC >= 20 -> "Humid: warm and sticky, and sweat doesn't cool you as well."
                else -> "Damp: in cool weather it makes the air feel raw and chilly."
            }
            Explanation(
                "Humidity",
                "How much water vapour the air holds, as a share of the most it could hold at this temperature (relative humidity).",
                "Now ${cur.humidity}%. $feel",
            )
        }
        Term.WIND -> {
            val kmh = cur.windKmh
            val beaufort = when {
                kmh < 2 -> "Calm: smoke rises straight up."
                kmh < 12 -> "Light air to a light breeze: you feel it on your face and leaves rustle."
                kmh < 29 -> "A gentle to moderate breeze: small branches move and loose paper blows around."
                kmh < 50 -> "A fresh to strong breeze: large branches sway and umbrellas are hard to use."
                else -> "Gale force: walking against it is hard. Take care outside."
            }
            val gust = forecast.nextHours.firstOrNull()?.gustKmh?.takeIf { it > kmh + 5 }
            Explanation(
                "Wind",
                "The average wind speed 10 metres above the ground. Gusts are brief bursts that can be much stronger.",
                "Now ${formatWind(kmh, unit)}" + (gust?.let { ", gusting to ${formatWind(it, unit)}" } ?: "") + ". $beaufort",
            )
        }
        Term.UV -> {
            val uv = today.uvIndexMax
            val advice = when (uv?.roundToInt()) {
                null -> "No UV forecast for today."
                in Int.MIN_VALUE..2 -> "Low: no protection needed for most people."
                in 3..5 -> "Moderate: sunscreen and sunglasses if you're out for a while around midday."
                in 6..7 -> "High: sunscreen, a hat and shade around midday; skin can burn in about 20 to 30 minutes."
                in 8..10 -> "Very high: avoid the midday sun; unprotected skin can burn in about 15 minutes."
                else -> "Extreme: stay in the shade around midday; skin can burn in minutes."
            }
            Explanation(
                "UV index",
                "The strength of the sun's burning ultraviolet rays, on a scale from 0 upwards. It peaks around midday and is highest in summer and at altitude. Clouds cut it less than you'd think.",
                (uv?.let { "Today's peak: ${it.roundToInt()} (${describeUv(it)}). " } ?: "") + advice,
            )
        }
        Term.SUN, Term.DAYLIGHT -> {
            val now = when (today.daylight) {
                Daylight.NORMAL -> {
                    val minutes = java.time.Duration.between(today.sunrise, today.sunset).toMinutes()
                    val tomorrow = forecast.days.firstOrNull { it.date == today.date.plusDays(1) }?.takeIf { it.daylight == Daylight.NORMAL }
                    val change = tomorrow?.let { java.time.Duration.between(it.sunrise, it.sunset).toMinutes() - minutes }
                    "Sunrise ${formatClock(today.sunrise!!, locale)}, sunset ${formatClock(today.sunset!!, locale)}: " +
                        "${minutes / 60} hours ${minutes % 60} minutes of daylight." +
                        when {
                            change == null || change == 0L -> ""
                            change > 0 -> " Tomorrow gets $change ${if (change == 1L) "minute" else "minutes"} more."
                            else -> " Tomorrow gets ${-change} ${if (change == -1L) "minute" else "minutes"} less."
                        }
                }
                Daylight.POLAR_NIGHT -> "The sun doesn't rise today: polar night, when the sun stays below the horizon all day."
                Daylight.MIDNIGHT_SUN -> "The sun doesn't set today: midnight sun, when it stays above the horizon all day."
                Daylight.UNKNOWN -> "No sunrise or sunset times for today."
            }
            Explanation(
                "Sunrise and sunset",
                "When the top of the sun crosses the horizon, in the place's local time. It stays light for a while after sunset (twilight).",
                now,
            )
        }
        Term.RAIN_CHANCE -> {
            val p = today.precipChance
            val sum = today.precipSumMm
            Explanation(
                "Chance of rain",
                "The chance that at least a little rain (or snow) falls at this place during the period, not how much of the day it rains or how heavy it is.",
                "Today's highest hourly chance: $p%." +
                    (sum?.takeIf { it >= 0.1 }?.let { " About ${String.format(locale, "%.1f", it)} mm expected in total." } ?: "") +
                    when {
                        p >= 70 -> " Take an umbrella."
                        p >= 30 -> " Worth having an umbrella nearby."
                        else -> " Unlikely to need an umbrella."
                    },
            )
        }
    }
}
