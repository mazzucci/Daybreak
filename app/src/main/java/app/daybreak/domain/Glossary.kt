package app.daybreak.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Weather terms the app can explain when their tile is tapped. */
enum class Term {
    FEELS_LIKE, HUMIDITY, WIND, UV, SUN, DAYLIGHT, RAIN_CHANCE,
    /** A whole day's rain or snow, from the day page (needs the day's date). */
    RAIN_DAY,
    /** How the "This week" outlook scores days. */
    WEEK,
}

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

/** Top of the day-rain scale: 20 mm is a wet day. */
private const val WET_DAY_SCALE_MM = 20.0

/** Gusts from about here make an umbrella more trouble than it's worth, km/h. */
private const val UMBRELLA_GUST_KMH = 40.0

/**
 * Explains [term] with this forecast's values, in the primary [unit]. Hand-written and built from the data (no
 * model involved), so every number matches the page; numbers are written the US way, as everywhere in the app's
 * English copy. [date] picks the day for [Term.RAIN_DAY] (today if null).
 */
fun explain(
    term: Term,
    forecast: Forecast,
    unit: TempUnit,
    date: LocalDate? = null,
    /** The moment "This week" is for ([outlookMoment]), so the explanation matches the card. */
    now: LocalDateTime = forecast.current.time,
): Explanation {
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
            val rain = Precip.dayRain(forecast, today.date)
            val p = rain.chance
            Explanation(
                title = if (rain.showsSnow) "Chance of snow" else "Chance of rain",
                value = "$p%",
                detail = "Highest hourly chance today",
                now = todaysAmount(rain, cur.time, unit) + umbrella(p, today.gustMaxKmh),
                meaning = "The chance that at least a little rain (or snow) falls at this place in a given hour. We show today's highest hour, so the chance of some rain at some point today can be higher. It says nothing about how long it rains or how heavy it is.",
                gauge = Gauge.Scale(p / 100f, "0%", "100%", Gauge.Scale.Kind.MOISTURE),
            )
        }
        Term.RAIN_DAY -> {
            val day = date?.let { forecast.day(it) } ?: today
            val rain = Precip.dayRain(forecast, day.date)
            val snow = rain.showsSnow
            val name = formatDayName(day.date, today.date, Locale.US).let { if (it == "Today") "today" else it }
            val hours = rain.wetHours
            val what = if (snow) "snow" else "rain"
            val timing = rain.timing?.takeIf { rain.amountShown }?.let(Precip::timingSentence)
            val spell = when {
                hours > 0 && timing != null -> "About ${plural(hours.toLong(), "hour")} of $what. $timing"
                hours > 0 -> "About ${plural(hours.toLong(), "hour")} of $what."
                timing != null -> timing
                else -> "No $what expected."
            }
            // Snow is measured as depth, so its gauge is too: 20 cm (8 inches) is deep, as 20 mm of rain is a wet day.
            val top = when {
                snow && unit == TempUnit.C -> 20.0
                snow -> 8 * 2.54
                unit == TempUnit.C -> WET_DAY_SCALE_MM
                else -> 0.8 * 25.4
            }
            val amount = if (snow) rain.snowCm else rain.totalMm
            Explanation(
                title = if (snow) "Snow" else "Rain",
                value = if (snow) Precip.formatSnow(rain.snowCm, unit, rain.rough) else Precip.formatRain(rain.totalMm, unit, rain.rough),
                detail = if (snow) "Expected snowfall for $name" else "Expected total for $name",
                now = "$spell ${umbrella(rain.chance, day.gustMaxKmh)}",
                meaning = if (snow) {
                    "The fresh snow the forecast expects over the day, as depth before it settles. 1 cm dusts the ground; 5 cm covers it; 20 cm is deep. Hours count any hour with at least 0.1 mm of rain or melted snow."
                } else {
                    "The total rain (and melted snow) the forecast expects to fall over the day. 1 mm is a few drops on the pavement; 5 mm wets everything; 20 mm is a wet day. Hours count any hour with at least 0.1 mm."
                },
                gauge = Gauge.Scale(
                    (amount / top).toFloat().coerceIn(0f, 1f),
                    when {
                        unit == TempUnit.F -> "0 in"
                        snow -> "0 cm"
                        else -> "0 mm"
                    },
                    if (snow) Precip.formatSnow(top, unit) else Precip.formatRain(top, unit),
                    Gauge.Scale.Kind.MOISTURE,
                ),
                spoken = listOfNotNull(
                    if (snow) Precip.spokenSnow(rain.snowCm, unit, rain.rough) else Precip.spokenRain(rain.totalMm, unit, rain.rough),
                    if (snow) "expected snowfall for $name" else "expected in total for $name",
                ).joinToString(", "),
            )
        }
        Term.WEEK -> explainWeek(weekOutlook(forecast, unit, now = now), forecast, unit)
    }
}

/**
 * "How the outlook works": the score of the day the today line is about, from which hours and what held it back,
 * then the rules. No weekend days needed: the explanation never names the best day.
 */
private fun explainWeek(outlook: WeekOutlook, forecast: Forecast, unit: TempUnit): Explanation {
    val read = outlook.focus
    val today = forecast.today.date
    val who = read?.let { if (it.date == today) "Today" else "Tomorrow" }
    val now = if (read == null) {
        "There's no daylight left in the forecast to judge."
    } else {
        val hours = read.best
        val from = hours.first().hour.time
        val to = hours.last().hour.time.plusHours(1)
        val ahead = " still ahead".takeIf { read.date == today } ?: ""
        val held = when {
            read.polarNight -> "It's polar night, so it can't score higher than mixed."
            read.shortDay -> "There's hardly any daylight, so it can't score higher than mixed."
            read.mostlyWet && read.tier == OutlookTier.MEH -> "Rain falls in at least a third of its hours, so it can't score higher than mixed."
            else -> when (read.topic.takeIf { read.tier != OutlookTier.GREAT }) {
                OutlookTopic.WET -> if (read.stormy && read.hours.none { Limit.RAIN in it.limits || Limit.SNOW in it.limits }) "The risk of thunderstorms is what holds it back most."
                else "Rain is what holds it back most."
                OutlookTopic.WIND -> "The wind is what holds it back most."
                OutlookTopic.HEAT -> "The heat is what holds it back most."
                OutlookTopic.COLD -> "The cold is what holds it back most."
                OutlookTopic.DARK, null -> "Nothing much holds it back."
            }
        }
        listOfNotNull(
            "Today's daylight is over, so the outlook looks at tomorrow.".takeIf { read.date != today },
            when {
                read.fromDaily -> "$who scores ${read.score} out of 100, from the day's figures."
                // Without daylight the hours are waking hours, and their times would only confuse.
                read.dim -> "$who scores ${read.score} out of 100, from its best ${plural(hours.size.toLong(), "waking hour")}$ahead."
                else -> "$who scores ${read.score} out of 100, from its best ${plural(hours.size.toLong(), "hour")} of daylight$ahead (${formatSpan(from, to)})."
            },
            held,
        ).joinToString(" ")
    }
    val profile = WeatherProfile.OUTDOOR
    val band = "${degrees(profile.idealC.start, unit)}–${formatTemp(profile.idealC.endInclusive, unit)}"
    return Explanation(
        title = "How the outlook works",
        value = read?.tier?.word ?: "–",
        detail = read?.let { "$who: ${it.score} out of 100" },
        now = now,
        meaning = "Each of the next 7 days gets a score out of 100 for being outside (a walk, a ride, time in the park), " +
            "from its best ${Outlook.RUN_HOURS} hours of daylight in a row, so one nice hour doesn't make a good day. Each hour " +
            "loses points for rain, snow and storms, for wind over ${formatWind(profile.maxWindKmh, unit)} or gusts over " +
            "${formatWind(profile.maxGustKmh, unit)}, and for temperatures outside $band. ${Outlook.GREAT_MIN} and up is great, " +
            "${Outlook.GOOD_MIN} good and ${Outlook.MEH_MIN} mixed. Below that it's a day for staying in. A day with rain in " +
            "a third of its hours or more is mixed at best, however dry its best hours, and so is a day without daylight. " +
            "Rainy days and spells follow the same rain rules as the rest of the app, and days further out than a week are " +
            "left out as less certain.",
        spoken = read?.let { "${it.tier.word}. $who: ${it.score} out of 100" },
    )
}

/**
 * Today's amount for the rain explanation, with whether it's still to come: "Still to come today: about 4 mm over 3
 * hours, mostly this evening. ", or "Today in all: about 4 mm over 3 hours, mostly before sunrise. " once none of
 * it is left. Nothing when the day isn't worth an amount.
 */
private fun todaysAmount(rain: DayRain, now: LocalDateTime, unit: TempUnit): String {
    if (!rain.amountShown) return ""
    val rest = rain.stillToCome(now)
    return if (rest.mm >= Precip.HOUR_AMOUNT_MIN_MM) "Still to come today: ${Precip.spanPhrase(rest, rain, unit)}. "
    else "Today in all: ${Precip.spanPhrase(rain.whole, rain, unit)}. "
}

/** What to take, by the chance (with the same words as everywhere else) and how gusty it gets. */
private fun umbrella(chance: Int, gustKmh: Double?): String {
    val likelihood = Precip.likelihood(chance)
    val wet = likelihood == Likelihood.LIKELY || likelihood == Likelihood.POSSIBLE
    return when {
        wet && (gustKmh ?: 0.0) >= UMBRELLA_GUST_KMH -> "Take a proper coat rather than an umbrella: it'll be gusty too."
        likelihood == Likelihood.LIKELY -> "Take an umbrella."
        wet -> "Worth having an umbrella nearby."
        else -> "Unlikely to need an umbrella."
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
