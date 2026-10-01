package app.daybreak.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * "This week": plain advice about being outside, from rules and the forecast alone (no model). Each of the next
 * [Outlook.DAYS] days (today and the six after it; days 8 to 10 are too uncertain) gets a score from 0 to 100 for
 * time outside, a [OutlookTier], and the page gets one line about today ("Great day to be outside, best 1–5 PM") and
 * up to two about the week ("Rainy spell from tomorrow until Monday", "Saturday is the best day this week").
 *
 * **A day's score.** Its daylight hours within waking hours (6 AM to 10 PM) are scored one by one with
 * [OutdoorScorer] and [WeatherProfile.OUTDOOR], and the day takes the average of its best run of [Outlook.RUN_HOURS]
 * hours, so one nice hour doesn't make a good day. Each hour is judged on what Open-Meteo says about it (see
 * [withRainDuring] for which stamp that is). Today counts only the daylight still ahead; with fewer than
 * [Outlook.MIN_LIGHT_HOURS] hours left it has no score, and the today line looks at tomorrow. Under polar night, and
 * on a day with under [Outlook.MIN_LIGHT_HOURS] hours of daylight, the waking hours stand in for daylight, capped below
 * "good"; under the midnight sun the waking hours are the daylight. A day with rain in at least a third of its hours
 * is capped below "good" too, however dry its best run. A day the hourly data doesn't reach is scored from its daily
 * figures as one stand-in hour.
 *
 * **Rain words** come from [Precip] and never contradict it: a day [Precip] calls dry is never rainy here, a day is
 * wet ([DayKind.WET], a spell's day) only when [Precip.dayRain] says so, the strip speaks the day page's own verdict
 * ("rain likely", "showers possible"), and "dry until 2 PM" needs every hour before 2 PM to be dry by
 * [Precip.classify]. The lines don't contradict each other either: today counts towards a spell only when what's
 * still to come today is wet, the day the today line calls good or great is never part of one, "Dry again" follows
 * only a wet today line, and the best day is never a wet day or a day inside the spell.
 *
 * Copy is English with US numbers and day names, in one voice and without semicolons; temperatures and wind follow
 * the user's unit, as elsewhere.
 */
object Outlook {
    /** Today and the six days after it. */
    const val DAYS = 7

    /**
     * Tier floors, tuned on the fixtures. Great needs dry, calm hours from about 6 °C to 32 °C (a 40% chance or a
     * 3 °C morning is only good); good allows one small dent, such as 0 °C or a breezy hour; mixed is about one rule
     * broken (a drizzly hour, a gale's gusts); below that it's raining, stormy, freezing or too hot.
     */
    const val GREAT_MIN = 80
    const val GOOD_MIN = 60
    const val MEH_MIN = 35

    /** A day is as good as its best run of this many daylight hours. */
    const val RUN_HOURS = 3

    /**
     * Today needs at least this many daylight hours ahead to get a score of its own; a day with fewer light waking
     * hours than this in all is judged like polar night.
     */
    const val MIN_LIGHT_HOURS = 2

    /** Hours next to the best run within this many points of it widen "best 1–5 PM". */
    const val STRETCH_DROP = 10

    /** The best day is named only when it's this far over the week's median, and at least good. */
    const val BEST_MARGIN = 10

    /** A weekend day this close to the best day is named instead: that's when most people can go. */
    const val WEEKEND_SLACK = 5

    /** Highs from here make a heat spell (two days or more in a row). */
    const val HEAT_SPELL_C = 30.0

    /** From here (the profile's limit) a day's peak gets "Hot day: 34° by 3 PM". */
    const val HOT_C = 32.0

    /** A high this much below today's makes a cold snap, when it's under [COLD_SNAP_HIGH_C]. */
    const val COLD_DROP_C = 8.0
    const val COLD_SNAP_HIGH_C = 16.0

    /** Gusts (or, without them, wind) from here (gale force) get a line of their own. */
    const val GALE_GUST_KMH = 70.0

    /** The share of the hours with rain that makes it "rain most of the day". */
    const val MOST_OF_DAY = 0.6

    /** A day with rain in at least this share of its scored hours is mixed at best. */
    const val WET_CAP_SHARE = 1.0 / 3

    /**
     * A morning low under this is frost: it rounds to −1° or lower in °C (and 31° or lower in °F), so the line never
     * reads "down to 0°".
     */
    const val FROST_C = -0.5

    /** A morning's low is the lowest of the stamps from midnight to this hour. */
    const val MORNING_UNTIL = 9

    /** "First frost" needs at least this many frost-free mornings before it in the data; otherwise it's "Frost". */
    const val FIRST_FROST_AFTER = 5

    /** Waking hours: hours starting from 6 AM up to 9 PM. */
    const val WAKING_FROM = 6
    const val WAKING_UNTIL = 22
}

/** How good a day is for being outside, by its score. */
enum class OutlookTier(
    /** For the explanation: "Great", "Stay in". */
    val word: String,
    /** For screen readers, after the day's name: "Saturday, great for being outside". */
    val spoken: String,
) {
    GREAT("Great", "great for being outside"),
    GOOD("Good", "good for being outside"),
    MEH("Mixed", "a mixed day"),
    STAY_IN("Stay in", "one for staying in"),
    ;

    val atLeastGood: Boolean get() = this == GREAT || this == GOOD

    companion object {
        fun of(score: Int): OutlookTier = when {
            score >= Outlook.GREAT_MIN -> GREAT
            score >= Outlook.GOOD_MIN -> GOOD
            score >= Outlook.MEH_MIN -> MEH
            else -> STAY_IN
        }
    }
}

/** What holds a day back most: rain (or snow, or storms), wind, heat or cold; darkness under polar night. */
enum class OutlookTopic { WET, WIND, HEAT, COLD, DARK }

/** Which rule a line comes from, highest priority first for the week lines. */
enum class LineKind { DAY, WET_SPELL, DRY_TURN, BEST_DAY, BIG_WIND, HEAT_SPELL, COLD_SNAP, FROST, DRY_WEEK }

/**
 * One line of the outlook: [text] as shown, [spoken] for screen readers (both temperature units, "to" for ranges).
 * [date] is the day it's mainly about (a spell's first day, with its last as [end]) and [topic] what it's about, so a
 * week line can skip what the today line says.
 */
data class OutlookLine(
    val text: String,
    val spoken: String,
    val kind: LineKind,
    val date: LocalDate? = null,
    val topic: OutlookTopic? = null,
    val end: LocalDate? = null,
)

/**
 * A day in the strip. [score] and [tier] are null for today once there isn't enough daylight left to judge. [rain]
 * is the day's [Precip] kind (a wet day gets a glyph) and [snow] whether its amount is snow; [highC] its high.
 */
data class OutlookDay(
    val date: LocalDate,
    val score: Int?,
    val tier: OutlookTier?,
    val isBest: Boolean,
    val rain: DayKind,
    val snow: Boolean,
    val highC: Double,
    /** "Saturday, best day, great for being outside, dry"; "Today, daylight's over, rain likely". */
    val spoken: String,
)

/**
 * The outlook for a page: the [today] line (always there), 0–2 [week] lines, the [days] of the strip, today first,
 * and the lines in one [spoken] summary. [focus] is the day the today line talks about (today, or tomorrow once
 * today's daylight is over), for the explanation.
 */
data class WeekOutlook(
    val today: OutlookLine,
    val week: List<OutlookLine>,
    val days: List<OutlookDay>,
    val spoken: String,
    val focus: DayRead?,
) {
    /** The day marked best in the strip (at least good), if any. */
    val bestDate: LocalDate? get() = days.firstOrNull { it.isBest }?.date
}

/**
 * A day's scored hours: daylight (still ahead, for today) within waking hours, in order, or one stand-in from the
 * daily figures ([fromDaily]). [best] is the best run of [Outlook.RUN_HOURS] hours (fewer when there are fewer) and
 * [stretch] the run around it worth recommending; [score] is [best]'s average, capped below "good" under polar night,
 * on a [shortDay], or when at least a third of the hours are rainy ([mostlyWet]).
 */
data class DayRead(
    val date: LocalDate,
    val hours: List<HourScore>,
    val best: List<HourScore>,
    val stretch: List<HourScore>,
    val score: Int,
    val fromDaily: Boolean,
    val polarNight: Boolean,
    val mostlyWet: Boolean = false,
    /** Under [Outlook.MIN_LIGHT_HOURS] hours of daylight in all, so judged like polar night. */
    val shortDay: Boolean = false,
) {
    val tier: OutlookTier get() = OutlookTier.of(score)

    /** Without (enough) daylight: the waking hours stand in for it. */
    val dim: Boolean get() = polarNight || shortDay

    /** The warmest scored hour, for "34° by 3 PM" and "no warmer than 2°". */
    val warmest: HourScore get() = hours.maxBy { it.hour.tempC }

    val maxGustKmh: Double? get() = hours.mapNotNull { it.hour.gustKmh }.maxOrNull()
    val maxWindKmh: Double? get() = hours.mapNotNull { it.hour.windKmh }.maxOrNull()

    /** The stronger of the gusts and the wind over the scored hours, for the gale rules. */
    val peakWindKmh: Double get() = maxOf(maxGustKmh ?: 0.0, maxWindKmh ?: 0.0)

    /** Whether a scored hour has a thunderstorm code. */
    val stormy: Boolean get() = hours.any { Limit.STORM in it.limits }

    /**
     * What holds the day back most, by how many hours it hits; ties go to rain, then wind, heat and cold. Rain, when
     * it's [mostlyWet].
     */
    val topic: OutlookTopic?
        get() {
            if (mostlyWet) return OutlookTopic.WET
            val counts = mapOf(
                OutlookTopic.WET to hours.count { h -> h.limits.any { it == Limit.STORM || it == Limit.RAIN || it == Limit.SNOW } },
                OutlookTopic.WIND to hours.count { Limit.WIND in it.limits },
                OutlookTopic.HEAT to hours.count { Limit.HEAT in it.limits },
                OutlookTopic.COLD to hours.count { Limit.COLD in it.limits },
            ).filterValues { it > 0 }
            return counts.entries.maxWithOrNull(compareBy({ it.value }, { -it.key.ordinal }))?.key
        }
}

/**
 * The moment the outlook is for, at the forecast's place: the later of the forecast's own time and [clock] (in the
 * forecast's UTC offset, so its stamps line up), to the hour. It moves on at most once an hour, so a screen can work
 * the outlook out again only then, and a forecast fetched at 2 PM and still shown at 8 PM is judged at 8 PM.
 */
fun outlookMoment(forecast: Forecast, clock: Instant): LocalDateTime {
    val local = clock.atOffset(ZoneOffset.ofTotalSeconds(forecast.utcOffsetSeconds)).toLocalDateTime().truncatedTo(ChronoUnit.HOURS)
    return maxOf(forecast.current.time, local)
}

/**
 * Works out the outlook from [forecast] as of [now] (see [outlookMoment]), in [unit]. [weekend] (from
 * [weekendDays]) decides which days count as the weekend when two are about as good. [rains] is [Precip.dayRain] for
 * each of [Forecast.upcomingDays], when the page has already worked it out.
 */
fun weekOutlook(
    forecast: Forecast,
    unit: TempUnit,
    weekend: Set<DayOfWeek> = weekendDays(null),
    now: LocalDateTime = forecast.current.time,
    rains: List<DayRain>? = null,
    profile: WeatherProfile = WeatherProfile.OUTDOOR,
): WeekOutlook {
    val today = forecast.today.date
    val days = forecast.upcomingDays(Outlook.DAYS)
    val reads = days.map { readDay(forecast, it, now, profile) }
    val allRains = forecast.upcomingDays().let { upcoming ->
        rains?.takeIf { r -> r.map { it.date } == upcoming.map { it.date } } ?: upcoming.map { Precip.dayRain(forecast, it.date) }
    }

    val todayRead = reads.firstOrNull()
    val focusIndex = if (todayRead != null) 0 else 1
    val focus = reads.getOrNull(focusIndex)
    val todayLine = if (focus != null) dayLine(focus, allRains[focusIndex], tomorrow = focusIndex == 1, unit)
    else line(LineKind.DAY, unit, today) { "Daylight's over for today" }

    val ctx = Context(forecast, now, today, days, reads, allRains, weekend, focusIndex, todayLine)
    val spell = ctx.spell()
    val best = ctx.bestDay(spell)
    val candidates = listOfNotNull(
        spell?.let { ctx.wetSpellLine(it, unit) },
        ctx.dryTurn(unit, spell),
        best?.let { ctx.bestDayLine(it, unit) },
        ctx.bigWind(unit, said = todayLine.date.takeIf { todayLine.topic == OutlookTopic.WIND }),
        ctx.heatSpell(unit),
        ctx.coldSnap(unit) ?: ctx.frost(unit),
    )
    val week = candidates.take(2).ifEmpty { listOfNotNull(ctx.dryWeek(unit)) }

    val bestDate = best?.takeIf { it.tier.atLeastGood }?.date
    val outlookDays = days.mapIndexed { i, day ->
        val read = reads[i]
        val rain = allRains[i]
        val name = outlookDayName(day.date, today).replaceFirstChar { it.uppercase() }
        val isBest = day.date == bestDate
        OutlookDay(
            date = day.date,
            score = read?.score,
            tier = read?.tier,
            isBest = isBest,
            rain = rain.kind,
            snow = rain.showsSnow,
            highC = day.highC,
            spoken = listOfNotNull(
                name,
                "best day".takeIf { isBest },
                read?.tier?.spoken ?: if (daylightOver(forecast, day, now)) "daylight's over" else "not enough daylight left",
                rainWords(rain, unit),
            ).joinToString(", "),
        )
    }
    val spoken = (listOf(todayLine) + week).joinToString(". ") { it.spoken } + "."
    return WeekOutlook(todayLine, week, outlookDays, spoken, focus)
}

/** Whether [now] is past [day]'s sunset (when it has one) or the end of its waking hours. */
private fun daylightOver(forecast: Forecast, day: DaySummary, now: LocalDateTime): Boolean {
    if (now >= day.date.atTime(Outlook.WAKING_UNTIL, 0)) return true
    return when (day.daylight) {
        Daylight.NORMAL -> now >= day.sunset!!
        Daylight.UNKNOWN -> forecast.isNight(now) && now.hour >= 12 // the fixed 8 PM fallback sunset
        Daylight.POLAR_NIGHT, Daylight.MIDNIGHT_SUN -> false
    }
}

/** "dry", or the day page's verdict without its amount: "rain likely", "showers possible", "a small chance of snow". */
internal fun rainWords(rain: DayRain, unit: TempUnit): String =
    if (rain.dry) "dry" else Precip.verdict(rain, unit).substringBefore(" · ").replaceFirstChar { it.lowercase() }

/**
 * "today", "tomorrow", a weekday within six days ("Saturday"), then "next Monday". Lower case; capitalise at the
 * start of a sentence.
 */
fun outlookDayName(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    val weekday = date.format(DateTimeFormatter.ofPattern("EEEE", Locale.US))
    return when {
        days == 0L -> "today"
        days == 1L -> "tomorrow"
        days in 2..6 -> weekday
        else -> "next $weekday"
    }
}

// --- Scoring a day -----------------------------------------------------------------------------

internal fun readDay(forecast: Forecast, day: DaySummary, now: LocalDateTime, profile: WeatherProfile): DayRead? {
    val isToday = day.date == forecast.today.date
    val polarNight = day.daylight == Daylight.POLAR_NIGHT
    val all = forecast.hoursOf(day.date)
    if (all.isEmpty()) return if (isToday) null else fromDaily(day, profile)
    val waking = all.filter { it.time.hour in Outlook.WAKING_FROM until Outlook.WAKING_UNTIL }
    val light = waking.filter { !OutdoorScorer.isDark(it, forecast) }
    // A whole day of waking hours with hardly any daylight (sunrise 11:40, sunset 12:20) is judged like polar night.
    val shortDay = !polarNight && waking.size == Outlook.WAKING_UNTIL - Outlook.WAKING_FROM && light.size < Outlook.MIN_LIGHT_HOURS
    val dim = polarNight || shortDay
    // Today's light is over once the sun has set or the waking hours have ended, whatever stands in for it.
    if (isToday && dim && daylightOver(forecast, day, now)) return null
    // An hour still counts as ahead while its middle is.
    val candidates = if (dim) waking else light
    val ahead = if (isToday) candidates.filter { it.time.plusMinutes(30) > now } else candidates
    if (ahead.size < Outlook.MIN_LIGHT_HOURS) return if (isToday) null else fromDaily(day, profile)
    val scored = ahead.map { OutdoorScorer.score(withRainDuring(forecast, it), profile, dark = false) }
    val run = minOf(Outlook.RUN_HOURS, scored.size)
    // The earliest of the best runs, so a tie goes to the sooner hours.
    val bestStart = (0..scored.size - run).maxWith(compareBy<Int> { i -> scored.subList(i, i + run).sumOf { it.score } }.thenByDescending { it })
    val best = scored.subList(bestStart, bestStart + run)
    val average = best.map { it.score }.average()
    var from = bestStart
    var to = bestStart + run // exclusive
    val floor = average - Outlook.STRETCH_DROP
    while (from > 0 && scored[from - 1].score >= floor) from--
    while (to < scored.size && scored[to].score >= floor) to++
    // A poor hour at the edge of the best run isn't part of what we recommend.
    while (to - from > 1 && scored[from].score < floor) from++
    while (to - from > 1 && scored[to - 1].score < floor) to--
    // Rain in a third of the hours makes a mixed day at best, however dry the rest, as does a day without the sun.
    val mostlyWet = scored.count { isRainyHour(it.hour) } >= Outlook.WET_CAP_SHARE * scored.size
    val score = average.roundToInt().let { if (dim || mostlyWet) minOf(it, Outlook.GOOD_MIN - 1) else it }
    return DayRead(day.date, scored, best, scored.subList(from, to), score, fromDaily = false, polarNight = polarNight, mostlyWet = mostlyWet, shortDay = shortDay)
}

/** An hour (with the rain that falls during it) that's rainy: from a 40% chance ("possible"), or wet by [Precip.classify]. */
private fun isRainyHour(hour: HourForecast): Boolean =
    hour.precipChance >= Precip.POSSIBLE || Precip.classify(hour.precipChance, hour.precipMm).kind == DayKind.WET

/**
 * The hour starting at [hour] as the outlook judges it, by Open-Meteo's own conventions
 * (https://open-meteo.com/en/docs, the hourly table's "valid time"):
 * - precipitation, snowfall and precipitation_probability are the **preceding hour's** sum and probability, and
 *   wind_gusts_10m is the **preceding hour's maximum**: for the hour from H to H + 1 they come from the stamp H + 1
 *   ([Forecast.rainDuring]), as the hourly strip's rain lines do;
 * - weather_code, temperature_2m and wind_speed_10m are **instant**, valid at the stamp itself: they stay the hour's
 *   own, which is the icon the hourly strip draws in the cell for H. So the strip and the outlook agree on which hour
 *   a storm is in.
 * The last hour of the data has no later stamp and keeps its own values.
 */
internal fun withRainDuring(forecast: Forecast, hour: HourForecast): HourForecast =
    forecast.rainDuring(hour)?.let { next ->
        hour.copy(precipChance = next.precipChance, precipMm = next.precipMm, snowCm = next.snowCm, gustKmh = next.gustKmh ?: hour.gustKmh)
    } ?: hour

/** One stand-in hour at 1 PM from the day's figures: its high, highest chance, code, strongest wind and gusts. */
private fun fromDaily(day: DaySummary, profile: WeatherProfile): DayRead {
    val stand = HourForecast(
        time = day.date.atTime(13, 0),
        tempC = day.highC,
        precipChance = day.precipChance,
        code = day.code,
        windKmh = day.windMaxKmh,
        gustKmh = day.gustMaxKmh,
        precipMm = day.precipSumMm,
        snowCm = day.snowSumCm,
    )
    val scored = listOf(OutdoorScorer.score(stand, profile, dark = false))
    val score = scored.first().score.let { if (day.daylight == Daylight.POLAR_NIGHT) minOf(it, Outlook.GOOD_MIN - 1) else it }
    return DayRead(day.date, scored, scored, scored, score, fromDaily = true, polarNight = day.daylight == Daylight.POLAR_NIGHT)
}

// --- The today line ----------------------------------------------------------------------------

/** How numbers are written: on screen ("34°", "1–5 PM") or for screen readers ("93°F (34°C)", "1 PM to 5 PM"). */
private class Words(val unit: TempUnit, val spoken: Boolean) {
    fun deg(c: Double) = if (spoken) formatBothUnits(c, unit) else formatDegrees(c, unit)
    fun wind(kmh: Double) = formatWind(kmh, unit)
    fun hour(t: LocalDateTime) = formatHour(t, Locale.US)
    fun span(start: LocalDateTime, end: LocalDateTime) = if (spoken) "${hour(start)} to ${hour(end)}" else formatSpan(start, end)
}

/** "1–5 PM", "11 AM–2 PM", or "13:00–17:00" on the 24-hour clock. */
internal fun formatSpan(start: LocalDateTime, end: LocalDateTime): String {
    val sameHalf = (start.hour < 12) == (end.hour < 12) && end.hour != 0
    if (!ClockFormat.use24Hour && sameHalf) return "${start.format(DateTimeFormatter.ofPattern("h", Locale.US))}–${formatHour(end, Locale.US)}"
    return "${formatHour(start, Locale.US)}–${formatHour(end, Locale.US)}"
}

private fun line(
    kind: LineKind,
    unit: TempUnit,
    date: LocalDate?,
    topic: OutlookTopic? = null,
    end: LocalDate? = null,
    build: Words.() -> String,
) = OutlookLine(Words(unit, spoken = false).build(), Words(unit, spoken = true).build(), kind, date, topic, end)

/** ", best 1–5 PM", ", best before 3 PM", ", best from 2 PM"; nothing when the good stretch is the whole day. */
private fun Words.qualifier(read: DayRead): String {
    if (read.fromDaily) return ""
    val first = read.stretch.first() == read.hours.first()
    val last = read.stretch.last() == read.hours.last()
    val start = read.stretch.first().hour.time
    val end = read.stretch.last().hour.time.plusHours(1)
    return when {
        first && last -> ""
        first -> ", best before ${hour(end)}"
        last -> ", best from ${hour(start)}"
        else -> ", best ${span(start, end)}"
    }
}

/**
 * When the rain falls in the day's scored hours, by [Precip]'s rules: "rain most of the day", "dry until 2 PM, then
 * showers", "showers until 11 AM, then drier", "showers from 2 PM", "showers on and off"; null on a day [Precip]
 * calls dry, or when none of the rain falls in them. An hour counts as rainy from a 40% chance ("possible").
 */
private fun Words.rainPattern(read: DayRead, rain: DayRain): String? {
    if (rain.dry || read.fromDaily) return null
    val word = rain.word.word.lowercase(Locale.US)
    val hours = read.hours.map { it.hour }
    val wet = hours.map(::isRainyHour)
    val wetCount = wet.count { it }
    if (wetCount == 0) return null
    val first = wet.indexOf(true)
    val last = wet.lastIndexOf(true)
    val dry = hours.map { Precip.classify(it.precipChance, it.precipMm).dry }
    // Mostly wet from the first rainy hour to the last: more than half of them.
    val solid = wet.subList(first, last + 1).count { it } * 2 > last - first + 1
    return when {
        wetCount >= Outlook.MOST_OF_DAY * hours.size -> "$word most of the day"
        first >= 2 && wet.drop(first).count { it } * 2 >= hours.size - first ->
            if (dry.take(first).all { it }) "dry until ${hour(hours[first].time)}, then $word" else "$word from ${hour(hours[first].time)}"
        first == 0 && last <= hours.size - 3 && solid -> "$word until ${hour(hours[last].time.plusHours(1))}, then drier"
        else -> "$word on and off"
    }
}

/**
 * Why a day held back by rain is: its [rainPattern], "risk of thunderstorms" for storms without rain worth the name,
 * or the day page's verdict ("rain possible"). Null only on a dry day without storms.
 */
private fun Words.wetReason(read: DayRead, rain: DayRain): String? =
    rainPattern(read, rain)
        ?: "risk of thunderstorms".takeIf { read.stormy }
        ?: rainWords(rain, unit).takeIf { !rain.dry }

/**
 * The end of a good or great day's line: its [qualifier], and where the rain falls when [Precip] doesn't call the day
 * dry and some of its scored hours are rainy: ", best from 3 PM after showers", ", best before 2 PM, then rain",
 * ", best 11 AM–2 PM, showers on and off"; ", risk of thunderstorms" for storms without rain.
 */
private fun Words.goodTail(read: DayRead, rain: DayRain): String {
    val q = qualifier(read)
    if (read.fromDaily) return q
    val rainy = read.hours.indices.filter { isRainyHour(read.hours[it].hour) }
    if (rain.dry || rainy.isEmpty()) return q + if (read.stormy) ", risk of thunderstorms" else ""
    val word = rain.word.word.lowercase(Locale.US)
    val from = read.hours.indexOf(read.stretch.first())
    val to = read.hours.indexOf(read.stretch.last())
    return when {
        rainy.all { it < from } -> "$q after $word"
        rainy.all { it > to } -> "$q, then $word"
        else -> "$q, $word on and off"
    }
}

/** "gusts to 85 km/h", or "wind to 50 km/h" when the gusts are missing or no stronger than the wind. */
private fun Words.windPeak(read: DayRead): String {
    val gust = read.maxGustKmh
    val wind = read.maxWindKmh ?: 0.0
    return if (gust != null && gust >= wind) "gusts to ${wind(gust)}" else "wind to ${wind(wind)}"
}

private fun dayLine(read: DayRead, rain: DayRain, tomorrow: Boolean, unit: TempUnit): OutlookLine {
    val tier = read.tier
    val topic = read.topic
    val notGood = !tier.atLeastGood
    val stayIn = tier == OutlookTier.STAY_IN
    val warmest = read.warmest
    fun says(topic: OutlookTopic?, build: Words.() -> String) = line(LineKind.DAY, unit, read.date, topic, build = build)
    fun pick(today: String, tmrw: String) = if (tomorrow) tmrw else today
    return when {
        notGood && topic == OutlookTopic.WET && Words(unit, false).wetReason(read, rain) != null -> says(OutlookTopic.WET) {
            val why = wetReason(read, rain)!!
            if (stayIn) pick("Better stay in: $why", "Better stay in tomorrow: $why")
            else pick("Mixed day: $why", "Tomorrow looks mixed: $why")
        }
        warmest.hour.tempC >= Outlook.HOT_C && !read.fromDaily -> says(OutlookTopic.HEAT) {
            val peak = "${deg(warmest.hour.tempC)} by ${hour(warmest.hour.time)}"
            if (stayIn) pick("Too hot to enjoy outside: $peak", "Tomorrow looks too hot to enjoy: $peak")
            else pick("Hot day: $peak", "Hot day tomorrow: $peak") + qualifier(read)
        }
        (notGood && topic == OutlookTopic.WIND) || read.peakWindKmh >= Outlook.GALE_GUST_KMH -> says(OutlookTopic.WIND) {
            val w = windPeak(read)
            if (notGood && (stayIn || read.peakWindKmh >= Outlook.GALE_GUST_KMH)) pick("Too windy to enjoy outside: $w", "Tomorrow looks too windy to enjoy: $w")
            else pick("Blustery day: $w", "Tomorrow looks blustery: $w") + qualifier(read)
        }
        notGood && topic == OutlookTopic.COLD -> says(OutlookTopic.COLD) {
            val t = "no warmer than ${deg(warmest.hour.tempC)}"
            if (stayIn) pick("Too cold to enjoy outside: $t", "Tomorrow looks too cold to enjoy: $t")
            else pick("Cold day: $t", "Cold day tomorrow: $t")
        }
        read.dim -> says(OutlookTopic.DARK) {
            val but = if (rain.dry) ", but dry" else ""
            val q = qualifier(read).removePrefix(", ").let { if (it.isEmpty()) "" else ": $it" }
            val light = if (read.polarNight) "No daylight" else "Little daylight"
            pick("$light today$but$q", "$light tomorrow$but$q")
        }
        tier == OutlookTier.GREAT -> says(null) { pick("Great day to be outside", "Tomorrow looks great outside") + goodTail(read, rain) }
        tier == OutlookTier.GOOD && topic == OutlookTopic.COLD -> says(OutlookTopic.COLD) {
            pick("Cold but good to be outside", "Tomorrow looks cold but good outside") + goodTail(read, rain)
        }
        tier == OutlookTier.GOOD -> says(null) { pick("Good day to be outside", "Tomorrow looks good outside") + goodTail(read, rain) }
        tier == OutlookTier.MEH -> says(topic) { pick("Mixed day", "Tomorrow looks mixed") + qualifier(read) }
        else -> says(topic) { pick("Better stay in", "Better stay in tomorrow") }
    }
}

// --- The week lines ----------------------------------------------------------------------------

/** A run of wet days: indexes into the forecast's days from today, [end] inclusive. */
private data class Spell(val start: Int, val end: Int) {
    operator fun contains(i: Int) = i in start..end
}

/**
 * The days of the outlook with their reads (null: today, not enough daylight left), every forecast day's rain from
 * today, which day the today line is about ([focusIndex]) and the line itself.
 */
private class Context(
    val forecast: Forecast,
    val now: LocalDateTime,
    val today: LocalDate,
    val days: List<DaySummary>,
    val reads: List<DayRead?>,
    /** Every forecast day from today, past the outlook too: a spell can run on into them. */
    val rains: List<DayRain>,
    val weekend: Set<DayOfWeek>,
    val focusIndex: Int,
    val todayLine: OutlookLine,
) {
    fun name(i: Int) = outlookDayName(rains[i].date, today)
    fun capitalName(i: Int) = name(i).replaceFirstChar { it.uppercase() }

    /** Whether what's still to come today is wet by [Precip.classify], judged on the stamps after [now]. */
    private val restOfTodayWet: Boolean by lazy {
        val r = rains.firstOrNull() ?: return@lazy false
        if (r.kind != DayKind.WET) return@lazy false
        val chance = r.counted.filter { it.time > now }.maxOfOrNull { it.precipChance } ?: 0
        Precip.classify(chance, r.stillToCome(now).mm).kind == DayKind.WET
    }

    /** The day the today line calls good or great: never part of a spell, so the two never disagree. */
    private val focusGood: Boolean get() = reads.getOrNull(focusIndex)?.tier?.atLeastGood == true

    /** A day that counts towards a spell: wet by [Precip], today only for what's still to come, and not [focusGood]. */
    private fun wet(i: Int): Boolean =
        rains[i].kind == DayKind.WET && (i != 0 || restOfTodayWet) && !(i == focusIndex && focusGood)

    /** The first run of two or more wet days that starts within the week, as far as the data shows it. */
    fun spell(): Spell? {
        val start = (0 until minOf(Outlook.DAYS, rains.size - 1)).firstOrNull { wet(it) && wet(it + 1) } ?: return null
        var end = start
        while (end + 1 < rains.size && wet(end + 1)) end++
        return Spell(start, end)
    }

    /**
     * "Wet until Wednesday, drier from Thursday" (from today), "Showers today and tomorrow, then drier", "Rainy spell
     * from tomorrow until Monday", "Showers on and off Thursday to Saturday", "Rainy spell Thursday to next
     * Saturday", "Snowy from Friday, into next week" (only when it runs to the end of the data). It's "on and off"
     * when its days are showers or average under six wet hours.
     */
    fun wetSpellLine(spell: Spell, unit: TempUnit): OutlookLine {
        val (start, end) = spell
        val days = rains.subList(start, end + 1)
        val snow = days.count { it.showsSnow } * 2 > days.size
        val showers = days.any { it.word == PrecipKind.SHOWERS }
        val onAndOff = showers || (days.all { it.complete } && days.map { it.wetHours }.average() < 6)
        val open = end == rains.size - 1 // the data ends before the spell does
        val after = end + 1
        val adjective = if (snow) "Snowy" else "Rainy"
        val noun = when {
            snow -> "Snow"
            showers -> "Showers"
            else -> "Rain"
        }
        val text = when {
            start == 0 && open -> if (snow) "Snowy into next week" else "Wet into next week"
            start == 0 && end == 1 -> "$noun today and tomorrow, then drier"
            start == 0 -> (if (onAndOff) "$noun on and off until ${name(end)}" else "${if (snow) "Snowy" else "Wet"} until ${name(end)}") +
                ", drier from ${name(after)}"
            open && onAndOff -> "$noun on and off from ${name(start)}, into next week"
            open -> "$adjective from ${name(start)}, into next week"
            start == 1 && onAndOff -> "$noun on and off from tomorrow until ${name(end)}"
            start == 1 -> "$adjective spell from tomorrow until ${name(end)}"
            onAndOff -> "$noun on and off ${name(start)} to ${name(end)}"
            else -> "$adjective spell ${name(start)} to ${name(end)}"
        }
        return line(LineKind.WET_SPELL, unit, rains[start].date, OutlookTopic.WET, end = rains[end].date) { text }
    }

    /**
     * Today's line is wet, and today is a wet day on its own (not a spell): the first dry day after it. "Dry again
     * from Thursday".
     */
    fun dryTurn(unit: TempUnit, spell: Spell?): OutlookLine? {
        if (spell?.start == 0 || !restOfTodayWet) return null
        if (todayLine.date != today || todayLine.topic != OutlookTopic.WET) return null
        val i = (1 until minOf(Outlook.DAYS, rains.size)).firstOrNull { rains[it].dry } ?: return null
        return line(LineKind.DRY_TURN, unit, rains[i].date, OutlookTopic.WET) { "Dry again from ${name(i)}" }
    }

    /**
     * The best of the scored days when it stands out: at least [Outlook.BEST_MARGIN] over the median. Never a wet day
     * or a day in the [spell]. A weekend day within [Outlook.WEEKEND_SLACK] of it (good, and standing out itself) is
     * picked instead; ties go to the earlier day. Null with fewer than three scored days, or when none stands out.
     */
    fun bestDay(spell: Spell?): DayRead? {
        val scored = reads.filterNotNull()
        if (scored.size < 3) return null
        val sorted = scored.map { it.score }.sorted()
        val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2].toDouble() else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        val eligible = reads.withIndex().filter { (i, r) -> r != null && rains[i].kind != DayKind.WET && (spell == null || i !in spell) }.map { it.value!! }
        val top = eligible.maxByOrNull { it.score } ?: return null // the earliest of equals
        if (top.score < median + Outlook.BEST_MARGIN) return null
        if (!top.tier.atLeastGood) return top
        val weekendPick = eligible
            .filter { it.date.dayOfWeek in weekend && it.tier.atLeastGood && it.score >= top.score - Outlook.WEEKEND_SLACK && it.score >= median + Outlook.BEST_MARGIN }
            .maxByOrNull { it.score }
        return weekendPick ?: top
    }

    /**
     * "Saturday is the best day this week", "Today's the best day this week", or with no good day at all, "Thursday
     * is the best of a poor week". Null when the best that's left isn't good but a good day is (a wet one).
     */
    fun bestDayLine(best: DayRead, unit: TempUnit): OutlookLine? {
        val i = rains.indexOfFirst { it.date == best.date }
        val text = when {
            !best.tier.atLeastGood -> if (reads.any { it?.tier?.atLeastGood == true }) return null else "${capitalName(i)} is the best of a poor week"
            i == 0 -> "Today's the best day this week"
            else -> "${capitalName(i)} is the best day this week"
        }
        return line(LineKind.BEST_DAY, unit, best.date) { text }
    }

    /**
     * The first day whose scored hours have gusts (or wind) of [Outlook.GALE_GUST_KMH] or more, other than [said]
     * (the day the today line already calls too windy): "Very windy Thursday: gusts to 80 km/h". Today counts only
     * while it has a score.
     */
    fun bigWind(unit: TempUnit, said: LocalDate?): OutlookLine? {
        val i = reads.indices.firstOrNull { i -> reads[i]?.let { it.peakWindKmh >= Outlook.GALE_GUST_KMH && it.date != said } == true } ?: return null
        val read = reads[i]!!
        return line(LineKind.BIG_WIND, unit, days[i].date, OutlookTopic.WIND) { "Very windy ${name(i)}: ${windPeak(read)}" }
    }

    /** Two or more days in a row with highs of [Outlook.HEAT_SPELL_C]: "Hot spell Thursday to Saturday, up to 34°". */
    fun heatSpell(unit: TempUnit): OutlookLine? {
        val hot = days.map { it.highC >= Outlook.HEAT_SPELL_C }
        val start = (0 until days.size - 1).firstOrNull { hot[it] && hot[it + 1] } ?: return null
        var end = start
        while (end + 1 < days.size && hot[end + 1]) end++
        val peak = days.subList(start, end + 1).maxOf { it.highC }
        val toEnd = end == days.size - 1 && days.size == Outlook.DAYS
        return line(LineKind.HEAT_SPELL, unit, days[start].date, OutlookTopic.HEAT, end = days[end].date) {
            val upTo = "up to ${deg(peak)}"
            when {
                start == 0 && toEnd -> "Hot all week, $upTo"
                start == 0 -> "Hot until ${name(end)}, $upTo"
                toEnd -> "Hot from ${name(start)} on, $upTo"
                start == 1 -> "Hot spell from tomorrow until ${name(end)}, $upTo"
                else -> "Hot spell ${name(start)} to ${name(end)}, $upTo"
            }
        }
    }

    /**
     * The first day whose high is [Outlook.COLD_DROP_C] or more below today's, and cool ([Outlook.COLD_SNAP_HIGH_C]):
     * "Much colder Friday: 9°, down from 18° today". The end of a heat spell isn't a cold snap.
     */
    fun coldSnap(unit: TempUnit): OutlookLine? {
        val high = days.firstOrNull()?.highC ?: return null
        val i = (1 until days.size).firstOrNull { days[it].highC <= high - Outlook.COLD_DROP_C && days[it].highC < Outlook.COLD_SNAP_HIGH_C } ?: return null
        return line(LineKind.COLD_SNAP, unit, days[i].date, OutlookTopic.COLD) {
            "Much colder ${name(i)}: ${deg(days[i].highC)}, down from ${deg(high)} today"
        }
    }

    /** A morning's low: the lowest of its stamps from midnight to 9 AM, or the day's low without hourly data. */
    private fun morningLow(i: Int): Double =
        forecast.hoursOf(days[i].date).filter { it.time.hour <= Outlook.MORNING_UNTIL }.minOfOrNull { it.tempC } ?: days[i].lowC

    /**
     * The first frosty morning (under [Outlook.FROST_C]) when this morning wasn't: "Frost by Wednesday morning: down
     * to −2°", or "First frost by Saturday morning" after [Outlook.FIRST_FROST_AFTER] frost-free mornings.
     */
    fun frost(unit: TempUnit): OutlookLine? {
        if (days.isEmpty()) return null
        val lows = days.indices.map(::morningLow)
        if (lows[0] < Outlook.FROST_C) return null
        val i = (1 until days.size).firstOrNull { lows[it] < Outlook.FROST_C } ?: return null
        val first = (0 until i).count { lows[it] >= 0.0 } >= Outlook.FIRST_FROST_AFTER
        return line(LineKind.FROST, unit, days[i].date, OutlookTopic.COLD) {
            "${if (first) "First frost" else "Frost"} by ${name(i)} morning: down to ${deg(lows[i])}"
        }
    }

    /**
     * Every day of the week dry by [Precip], when nothing else is worth saying: "Great all week" when every scored
     * day is great, "Good all week" when every one is at least good, otherwise "Dry all week".
     */
    fun dryWeek(unit: TempUnit): OutlookLine? {
        if (days.size < Outlook.DAYS || rains.take(Outlook.DAYS).any { !it.dry }) return null
        val tiers = reads.mapNotNull { it?.tier }
        val text = when {
            tiers.all { it == OutlookTier.GREAT } -> "Great all week"
            tiers.all { it.atLeastGood } -> "Good all week"
            else -> "Dry all week"
        }
        return line(LineKind.DRY_WEEK, unit, null) { text }
    }
}
