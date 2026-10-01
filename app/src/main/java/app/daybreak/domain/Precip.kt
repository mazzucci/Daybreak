package app.daybreak.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The one rule set for rain and snow, so the hourly strip, the 10-day list, the day page, the hero pill, Home,
 * the widget and the summary never disagree about when a chance or an amount is worth showing, or what to call it.
 * Thresholds and wording follow docs/research/accuweather.md §9.2, with one classifier ([classify]) behind all of them.
 *
 * Amounts are kept in mm (water) and cm (snow depth) as Open-Meteo sends them, and shown in one unit system that
 * follows the temperature setting: mm and cm with °C, inches with °F. Rain numbers are read for size, not converted,
 * so unlike temperatures they're never shown in both. Numbers are always written the US way (the copy is English).
 *
 * **Which hour a value belongs to.** Open-Meteo stamps an hour's precipitation, snowfall and
 * precipitation_probability with the hour's end: the value at 15:00 is what falls from 14:00 to 15:00. So:
 * - a stretch of time from A to B holds the values stamped after A, up to and including B: (A, B];
 * - "heaviest around 2 PM" names the hour the rain falls in (the stamp minus an hour);
 * - the hourly strip's cell for the hour starting at H shows the values stamped H + 1 ([Forecast.rainDuring]);
 * - the day page's chart draws the value stamped T in the slot from T − 1 to T.
 *
 * **Which day.** A date's stamps run from 00:00 to 23:00, and Open-Meteo's daily figures (precipitation_sum,
 * precipitation_probability_max, precipitation_hours) are built from exactly those, so a day's rain runs from 11 PM the
 * evening before to 11 PM. We group the hours the same way ([Forecast.hoursOf]) so the day page's rows, its verdict,
 * the 10-day list and the daily sums all agree, and no hour is ever counted on two days. The rows are still named by
 * the calendar ("Before sunrise 12 AM–7 AM", "Evening 7 PM–12 AM"): the hour either side of midnight is an hour early.
 *
 * **Daylight saving.** Open-Meteo gives a response one fixed UTC offset (the one in force when it's made), so when the
 * clocks change inside the ten days the stamps don't: from then on rows, chart and cells are an hour off the local
 * clock. Rare, and harmless at this precision, so it's left alone.
 */
object Precip {
    /** An hourly chance below this is noise: the cell leaves its line blank. */
    const val HOUR_CHANCE_MIN = 10

    /** From here a chance is worth planning around, and is drawn in the rain colour. */
    const val CHANCE_HIGHLIGHT = 40

    /** A daily chance below this isn't printed; without a real amount it makes a dry day (or part of one). */
    const val DAY_CHANCE_MIN = 20

    /** Open-Meteo's probability is defined on 0.1 mm an hour; below it is a trace. */
    const val HOUR_AMOUNT_MIN_MM = 0.1

    /** The smallest day total worth a number. */
    const val DAY_TOTAL_MIN_MM = 0.5

    /** From here a modelled amount is real enough to show whatever the chance ("a small chance · up to 2 mm"). */
    const val AMOUNT_ALONE_MM = 1.0

    /** The smallest snowfall that makes it a snow day (or part of one), or a snow hour. */
    const val SNOW_DAY_MIN_CM = 0.5
    const val SNOW_HOUR_MIN_CM = 0.1

    /** A wet day (the outlook's rainy day) needs both a real chance and a real amount. */
    const val WET_DAY_CHANCE = 50
    const val WET_DAY_MM = 1.0

    /** Chance words: likely, possible, a small chance, unlikely. */
    const val LIKELY = 70
    const val POSSIBLE = 40

    /** Below this a day's rain is "a few drops", so the verdict doesn't print a number. */
    const val FEW_DROPS_MM = 1.0

    /** Open-Meteo: "for the water equivalent in millimeter, divide by 7", i.e. 7 cm of snow is about 10 mm of water. */
    private const val SNOW_CM_PER_WATER_MM = 0.7

    private const val MM_PER_INCH = 25.4
    private const val CM_PER_INCH = 2.54

    /** One hour holding at least this share of the day's total gets "heaviest around …". */
    private const val PEAK_SHARE = 0.35

    /** A part of the day holding this share of the total gets "mostly in the …". */
    private const val MOSTLY_SHARE = 0.6

    /** A part of the day with at least this share counts as wet when the rain is spread out. */
    private const val SOME_SHARE = 0.15

    fun showHourChance(chance: Int): Boolean = chance >= HOUR_CHANCE_MIN

    fun highlightChance(chance: Int): Boolean = chance >= CHANCE_HIGHLIGHT

    fun showDayChance(chance: Int): Boolean = chance >= DAY_CHANCE_MIN

    fun likelihood(chance: Int): Likelihood = when {
        chance >= LIKELY -> Likelihood.LIKELY
        chance >= POSSIBLE -> Likelihood.POSSIBLE
        chance >= DAY_CHANCE_MIN -> Likelihood.SMALL
        else -> Likelihood.UNLIKELY
    }

    /**
     * Judges a day, a part of one or an hour from its highest hourly [chance] and its amount ([mm] of water, null when
     * unknown), for every surface: dry, possible ([DayKind.MIXED]) or likely ([DayKind.WET]), and whether the amount
     * is worth a number.
     *
     * - An amount is shown from 0.1 mm, and only with at least a 20% chance or at least 1 mm: a real modelled amount
     *   at a low chance still counts ("A small chance of rain · up to 2 mm"), a trace at a low chance doesn't.
     * - Dry: under 20% with no amount worth showing. Nothing about rain is shown then: no amounts, bars or rows.
     * - Wet: at least 50% and 1 mm, worth calling a rainy day (the "This week" outlook's wet day).
     * - Possible: anything in between, including a real chance with no modelled amount.
     */
    fun classify(chance: Int, mm: Double?): RainCall {
        val amountShown = mm != null && mm >= HOUR_AMOUNT_MIN_MM && (chance >= DAY_CHANCE_MIN || mm >= AMOUNT_ALONE_MM)
        val kind = when {
            chance < DAY_CHANCE_MIN && !amountShown -> DayKind.DRY
            chance >= WET_DAY_CHANCE && (mm ?: 0.0) >= WET_DAY_MM -> DayKind.WET
            else -> DayKind.MIXED
        }
        return RainCall(kind, amountShown)
    }

    /** Snow when there's enough of it and it makes up most of the water that falls. */
    fun isSnow(snowCm: Double?, precipMm: Double?, minCm: Double): Boolean {
        if (snowCm == null || snowCm < minCm) return false
        val waterMm = snowCm / SNOW_CM_PER_WATER_MM
        return waterMm >= (precipMm ?: 0.0) / 2
    }

    // --- Amounts as text --------------------------------------------------------------------------

    /**
     * Rain (water) for cells and rows: "0.6 mm", "4 mm", "18 mm" with °C (one decimal under 10 mm, a whole ".0"
     * dropped); "0.02 in", "0.15 in", "0.7 in", "1.2 in" with °F. [rough] rounds mm to a whole number, for amounts at
     * a low chance ("up to 2 mm").
     */
    fun formatRain(mm: Double, unit: TempUnit, rough: Boolean = false): String = when (unit) {
        TempUnit.C -> "${metric(mm, rough)} mm"
        TempUnit.F -> "${inches(mm / MM_PER_INCH)} in"
    }

    /** Snow depth for cells and rows: "0.4 cm", "3 cm", or "0.16 in", "1.2 in". */
    fun formatSnow(cm: Double, unit: TempUnit, rough: Boolean = false): String = when (unit) {
        TempUnit.C -> "${metric(cm, rough)} cm"
        TempUnit.F -> "${inches(cm / CM_PER_INCH)} in"
    }

    /** Rain in a sentence: "4 mm", or "0.26 inches" (one inch is "1 inch"). */
    fun proseRain(mm: Double, unit: TempUnit, rough: Boolean = false): String = when (unit) {
        TempUnit.C -> "${metric(mm, rough)} mm"
        TempUnit.F -> proseInches(mm / MM_PER_INCH)
    }

    /** Snow in a sentence: "3 cm", or "1.2 inches". */
    fun proseSnow(cm: Double, unit: TempUnit, rough: Boolean = false): String = when (unit) {
        TempUnit.C -> "${metric(cm, rough)} cm"
        TempUnit.F -> proseInches(cm / CM_PER_INCH)
    }

    /** For screen readers: "0.6 millimetres", "0.02 inches". */
    fun spokenRain(mm: Double, unit: TempUnit, rough: Boolean = false): String = when (unit) {
        TempUnit.C -> "${metric(mm, rough)} millimetres"
        TempUnit.F -> proseRain(mm, unit)
    }

    /** "3 centimetres of snow", "1.2 inches of snow". */
    fun spokenSnow(cm: Double, unit: TempUnit, rough: Boolean = false): String = when (unit) {
        TempUnit.C -> "${metric(cm, rough)} centimetres of snow"
        TempUnit.F -> "${proseSnow(cm, unit)} of snow"
    }

    private fun metric(v: Double, rough: Boolean): String = when {
        rough && v > 0.0 -> "${v.roundToInt().coerceAtLeast(1)}"
        v >= 9.95 -> "${v.roundToInt()}"
        else -> String.format(Locale.US, "%.1f", v).removeSuffix(".0")
    }

    /**
     * "0.02", "0.15", "0.26", "0.7" below an inch (two decimals, a trailing zero dropped), "1.2" and "3" from one.
     * Never "0.00": the smallest amount we'd show (0.1 mm) still reads as 0.01.
     */
    private fun inches(v: Double): String = when {
        v <= 0.0 -> "0"
        v < 0.995 -> String.format(Locale.US, "%.2f", v.coerceAtLeast(0.01)).trimEnd('0').removeSuffix(".")
        else -> String.format(Locale.US, "%.1f", v).removeSuffix(".0")
    }

    private fun proseInches(v: Double): String = inches(v).let { if (it == "1") "1 inch" else "$it inches" }

    // --- Hours ------------------------------------------------------------------------------------

    fun isSnowHour(hour: HourForecast): Boolean = isSnow(hour.snowCm, hour.precipMm, SNOW_HOUR_MIN_CM)

    /**
     * The amount for an hour's cell ("0.6 mm", "0.4 cm"), from [rain], the hour stamped at the end of the cell's
     * hour; null when [classify] says it isn't worth a number, or it's missing.
     */
    fun hourAmount(rain: HourForecast, unit: TempUnit): String? = when {
        !classify(rain.precipChance, rain.precipMm).amountShown -> null
        isSnowHour(rain) -> formatSnow(rain.snowCm!!, unit)
        else -> formatRain(rain.precipMm!!, unit)
    }

    /** The same, spoken: "about 0.6 millimetres". */
    fun hourAmountSpoken(rain: HourForecast, unit: TempUnit): String? = when {
        !classify(rain.precipChance, rain.precipMm).amountShown -> null
        isSnowHour(rain) -> "about ${spokenSnow(rain.snowCm!!, unit)}"
        else -> "about ${spokenRain(rain.precipMm!!, unit)}"
    }

    // --- Days -------------------------------------------------------------------------------------

    fun isSnowDay(day: DaySummary): Boolean = isSnow(day.snowSumCm, day.precipSumMm, SNOW_DAY_MIN_CM)

    /**
     * The day's amount for the end of its 10-day row and the hero pill ("4 mm", "3 cm" of snow; "2 mm" rounded when
     * the chance is low), or null when there's too little to mention. The same figure the day page's verdict gives.
     */
    fun dayAmount(rain: DayRain, unit: TempUnit): String? = when {
        !rain.amountShown -> null
        rain.showsSnow -> formatSnow(rain.snowCm, unit, rain.rough)
        rain.rough || rain.totalMm >= DAY_TOTAL_MIN_MM -> formatRain(rain.totalMm, unit, rain.rough)
        else -> null
    }

    /** The same, spoken: "about 4 millimetres", "about 3 centimetres of snow", "up to 2 millimetres". */
    fun dayAmountSpoken(rain: DayRain, unit: TempUnit): String? {
        if (dayAmount(rain, unit) == null) return null
        val lead = if (rain.rough) "up to" else "about"
        return if (rain.showsSnow) "$lead ${spokenSnow(rain.snowCm, unit, rain.rough)}"
        else "$lead ${spokenRain(rain.totalMm, unit, rain.rough)}"
    }

    /**
     * The day page's headline, from the same figures as its rows: "Rain likely · about 12 mm over 6 hours",
     * "Showers possible · a few drops over 2 hours", "Snow likely · about 3 cm over 5 hours", "A small chance of rain ·
     * up to 2 mm over 6 hours", "Rain unlikely". The chance word comes from the day's highest hourly chance, the same
     * number the 10-day row shows, so the two never disagree.
     */
    fun verdict(rain: DayRain, unit: TempUnit): String {
        if (rain.dry) return "Rain unlikely"
        val word = rain.word.word
        val head = when (likelihood(rain.chance)) {
            Likelihood.LIKELY -> "$word likely"
            Likelihood.POSSIBLE -> "$word possible"
            Likelihood.SMALL, Likelihood.UNLIKELY -> "A small chance of ${word.lowercase(Locale.US)}"
        }
        return listOfNotNull(head, amountPhrase(rain, unit)).joinToString(" · ")
    }

    /** "about 12 mm over 6 hours", "a few drops", "up to 2 mm", "about 4 mm of rain and 3 cm of snow"; null for none. */
    private fun amountPhrase(rain: DayRain, unit: TempUnit): String? {
        if (!rain.amountShown) return null
        val lead = if (rain.rough) "up to" else "about"
        val hours = rain.wetHours.takeIf { it > 0 }?.let { " over ${plural(it, "hour")}" } ?: ""
        val amount = when {
            rain.mix == Mix.RAIN_AND_SNOW -> {
                val snow = "${proseSnow(rain.snowCm, unit, rain.rough)} of snow"
                if (rain.rainMm < FEW_DROPS_MM) "a few drops of rain and $lead $snow"
                else "$lead ${proseRain(rain.rainMm, unit, rain.rough)} of rain and $snow"
            }
            rain.showsSnow -> "$lead ${proseSnow(rain.snowCm, unit, rain.rough)}"
            !rain.rough && rain.totalMm < FEW_DROPS_MM -> "a few drops"
            else -> "$lead ${proseRain(rain.totalMm, unit, rain.rough)}"
        }
        return amount + hours
    }

    /**
     * "Carries on after midnight: about 12 mm by 7 AM Friday." for the day page, from the next day's first part
     * ([DayRain.carryOn]); "about 3 cm of snow" when it's snow.
     */
    fun carryOnLine(next: RainPeriod, unit: TempUnit): String {
        val call = next.call
        val lead = if (next.rough) "up to" else "about"
        val amount = when {
            !call.amountShown -> "a ${next.chance}% chance"
            next.snow -> "$lead ${proseSnow(next.snowCm, unit, next.rough)} of snow"
            !next.rough && next.totalMm < FEW_DROPS_MM -> "a few drops"
            else -> "$lead ${proseRain(next.totalMm, unit, next.rough)}"
        }
        val by = "${formatHour(next.labelEnd, Locale.US)} ${next.labelEnd.format(DateTimeFormatter.ofPattern("EEEE", Locale.US))}"
        return "Carries on after midnight: $amount by $by."
    }

    /**
     * Everything about a day's rain, for every surface that shows it: the day's chance, its counted amount and hours,
     * how it's judged, its parts (before sunrise, daytime, evening: see the header for the hours each holds), when
     * it falls, and how much of the next day's first part carries on after midnight.
     *
     * The figures are the sum of the parts that aren't dry, judged each on its own hours, so the rows on the day page
     * add up to the verdict exactly: a trace in an otherwise dry evening is left out of both. Without hourly amounts
     * for the whole day (the data starts or ends that day), the day's own daily figures stand in.
     */
    fun dayRain(forecast: Forecast, date: LocalDate): DayRain {
        val day = forecast.day(date)
        val hours = forecast.hoursOf(date)
        val parts = dayParts(day, date)
        val periods = if (hours.isNotEmpty() && hours.all { it.precipMm != null }) parts.periods(hours) else emptyList()
        val counted = periods.filter { !it.dry }
        val countedHours = hours.filter { h -> counted.any { h.time in it } }
        val complete = hours.size == HOURS_PER_DAY && periods.isNotEmpty() && periods.all { it.complete }
        val carryOn = forecast.day(date.plusDays(1))?.let { next ->
            val nextHours = forecast.hoursOf(next.date)
            if (nextHours.isEmpty() || nextHours.any { it.precipMm == null }) null
            else dayParts(next, next.date).periods(nextHours).firstOrNull()?.takeIf { it.complete && !it.dry }
        }
        val timing = timing(countedHours, parts)
        if (!complete) return fromDaily(day, date, parts, hours, periods, countedHours, timing, carryOn)
        val snowParts = counted.filter { it.snow }
        val rainParts = counted.filter { !it.snow && it.totalMm >= HOUR_AMOUNT_MIN_MM }
        val chance = hours.maxOf { it.precipChance }
        val totalMm = counted.sumOf { it.totalMm }
        val call = classify(chance, totalMm)
        return DayRain(
            date = date,
            code = day?.code ?: 0,
            chance = chance,
            totalMm = totalMm,
            rainMm = rainParts.sumOf { it.totalMm },
            snowCm = snowParts.sumOf { it.snowCm },
            wetHours = counted.sumOf { it.wetHours },
            call = call,
            mix = when {
                snowParts.isEmpty() -> Mix.RAIN
                rainParts.isEmpty() -> Mix.SNOW
                else -> Mix.RAIN_AND_SNOW
            },
            complete = true,
            parts = parts,
            hours = hours,
            periods = periods,
            counted = countedHours,
            timing = timing,
            carryOn = carryOn,
        )
    }

    private fun fromDaily(
        day: DaySummary?,
        date: LocalDate,
        parts: DayParts,
        hours: List<HourForecast>,
        periods: List<RainPeriod>,
        counted: List<HourForecast>,
        timing: Timing?,
        carryOn: RainPeriod?,
    ): DayRain {
        val chance = day?.precipChance ?: 0
        val call = classify(chance, day?.precipSumMm)
        val dry = call.kind == DayKind.DRY
        val snow = day != null && isSnowDay(day)
        val totalMm = if (dry) 0.0 else day?.precipSumMm ?: 0.0
        return DayRain(
            date = date,
            code = day?.code ?: 0,
            chance = chance,
            totalMm = totalMm,
            rainMm = if (snow) 0.0 else totalMm,
            snowCm = if (snow && !dry) day?.snowSumCm ?: 0.0 else 0.0,
            wetHours = if (dry) 0 else day?.precipHours?.roundToInt() ?: 0,
            call = call,
            mix = if (snow) Mix.SNOW else Mix.RAIN,
            complete = false,
            parts = parts,
            hours = hours,
            periods = periods,
            counted = counted,
            timing = timing,
            carryOn = carryOn,
        )
    }

    /**
     * The day's parts: sunrise and sunset rounded to the hour, or 7 AM and 7 PM when the sun doesn't rise or set (or
     * the times are missing). Kept inside the day so every hour lands in exactly one part, and no part is empty at
     * its start.
     */
    private fun dayParts(day: DaySummary?, date: LocalDate): DayParts {
        val daylight = day?.daylight ?: Daylight.UNKNOWN
        val normal = daylight == Daylight.NORMAL
        val rise = (if (normal) nearestHour(day!!.sunrise!!) else date.atTime(7, 0)).coerceIn(date.atTime(1, 0), date.atTime(23, 0))
        val set = (if (normal) nearestHour(day!!.sunset!!) else date.atTime(19, 0)).coerceIn(rise, date.atTime(23, 0))
        return DayParts(date, rise, set, daylight)
    }

    private fun nearestHour(t: LocalDateTime): LocalDateTime =
        t.truncatedTo(ChronoUnit.HOURS).let { if (t.minute >= 30) it.plusHours(1) else it }

    /**
     * When in the day the rain in [hours] falls (stamps, so each is the hour before it): mostly in one part of the
     * day, in two, on and off all day, or before sunrise and then clearing; with the heaviest hour when one stands
     * out. The parts are the day page's rows, with the daytime split at noon. Null when the hours hold no rain.
     */
    fun timing(hours: List<HourForecast>, parts: DayParts): Timing? {
        val amounts = hours.map { it.time to (it.precipMm ?: 0.0) }
        val total = amounts.sumOf { it.second }
        if (total < HOUR_AMOUNT_MIN_MM) return null
        val shares = PartOfDay.entries.associateWith { part ->
            amounts.filter { parts.partOf(it.first) == part }.sumOf { it.second } / total
        }
        val wetHours = amounts.count { it.second >= HOUR_AMOUNT_MIN_MM }
        val (peakStamp, peakMm) = amounts.maxBy { it.second }
        // The stamp ends the hour the rain falls in: name that hour.
        val peak = peakStamp.minusHours(1).takeIf { wetHours >= 2 && total >= FEW_DROPS_MM && peakMm / total >= PEAK_SHARE }
        val main = shares.maxBy { it.value }
        val wetParts = shares.filter { it.value >= SOME_SHARE }.keys.sortedBy { it.ordinal }
        val sunrise = parts.daylight == Daylight.NORMAL
        return when {
            main.key == PartOfDay.EARLY && main.value > 0.95 -> Timing(TimingShape.CLEARING_BY_MORNING, listOf(PartOfDay.EARLY), null, sunrise)
            main.value >= MOSTLY_SHARE -> Timing(TimingShape.MOSTLY, listOf(main.key), peak, sunrise)
            wetParts.size >= 3 -> Timing(TimingShape.ON_AND_OFF, wetParts, peak, sunrise)
            else -> Timing(TimingShape.MOSTLY, wetParts.ifEmpty { listOf(main.key) }, peak, sunrise)
        }
    }

    /** "Mostly in the afternoon, heaviest around 4 PM." · "On and off all day." · "Before sunrise, clearing by morning." */
    fun timingSentence(timing: Timing): String {
        val base = when (timing.shape) {
            TimingShape.CLEARING_BY_MORNING -> return "${timing.early.replaceFirstChar { it.uppercase() }}, clearing by morning."
            TimingShape.ON_AND_OFF -> "On and off all day"
            TimingShape.MOSTLY -> "Mostly " + joinParts(timing.parts) { if (it == PartOfDay.EARLY) timing.early else it.phrase }
        }
        val peak = timing.peak?.let { ", heaviest around ${formatHour(it, Locale.US)}" } ?: ""
        return "$base$peak."
    }

    /** The same for today, in the hero's explanation: "mostly this evening", "on and off", "before sunrise". */
    fun timingToday(timing: Timing): String = when (timing.shape) {
        TimingShape.CLEARING_BY_MORNING -> "${timing.early}, clearing by morning"
        TimingShape.ON_AND_OFF -> "on and off"
        TimingShape.MOSTLY -> "mostly " + joinParts(timing.parts) { if (it == PartOfDay.EARLY) timing.early else it.today }
    }

    /** "in the morning and afternoon", "before sunrise and in the morning": the article isn't repeated. */
    private fun joinParts(parts: List<PartOfDay>, phrase: (PartOfDay) -> String): String {
        val first = phrase(parts.first())
        val article = listOf("in the ", "this ").firstOrNull { first.startsWith(it) }
        val rest = parts.drop(1).map { p -> phrase(p).let { if (article != null) it.removePrefix(article) else it } }
        return (listOf(first) + rest).joinToString(" and ")
    }

    /** "about 4.1 mm over 5 hours, mostly this evening" for [span] of [rain]'s day; "a few drops", "up to 2 mm". */
    fun spanPhrase(span: RainSpan, rain: DayRain, unit: TempUnit): String {
        val lead = if (rain.rough) "up to" else "about"
        val amount = when {
            rain.showsSnow && span.snowCm >= SNOW_HOUR_MIN_CM -> "$lead ${proseSnow(span.snowCm, unit, rain.rough)}"
            !rain.rough && span.mm < FEW_DROPS_MM -> "a few drops"
            else -> "$lead ${proseRain(span.mm, unit, rain.rough)}"
        }
        val hours = span.wetHours.takeIf { it > 0 }?.let { " over ${plural(it, "hour")}" } ?: ""
        val timing = span.timing?.let { ", ${timingToday(it)}" } ?: ""
        return "$amount$hours$timing"
    }

    internal fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

    private const val HOURS_PER_DAY = 24
}

enum class Likelihood(val word: String) { LIKELY("likely"), POSSIBLE("possible"), SMALL("a small chance"), UNLIKELY("unlikely") }

/** How a day (or part of one, or an hour) is judged: dry, possible ([MIXED]) or likely, a wet day ([WET]). */
enum class DayKind { WET, MIXED, DRY }

/** [Precip.classify]'s answer: the [kind] of day, and whether its amount is worth a number. */
data class RainCall(val kind: DayKind, val amountShown: Boolean) {
    val dry: Boolean get() = kind == DayKind.DRY
}

enum class PrecipKind(val word: String) {
    RAIN("Rain"), SHOWERS("Showers"), SNOW("Snow"), RAIN_AND_SNOW("Rain and snow"), THUNDERSTORMS("Thunderstorms"),
}

/** What a day's counted parts hold: only rain, only snow (by what most of each part's water is), or some of each. */
enum class Mix { RAIN, SNOW, RAIN_AND_SNOW }

/** The day's parts for timing words, with the same bounds as the day page's rows (the daytime split at noon). */
enum class PartOfDay(val phrase: String, val today: String) {
    /** Midnight to sunrise: "before sunrise", or "in the early hours" when the sun doesn't rise. */
    EARLY("before sunrise", "before sunrise"),
    MORNING("in the morning", "this morning"),
    AFTERNOON("in the afternoon", "this afternoon"),
    EVENING("in the evening", "this evening"),
}

/**
 * The bounds of a day's parts, as stamps ([start], [end]] (see [Precip]): before sunrise up to [sunrise], daytime up to
 * [sunset], then the evening. [start] is 11 PM the evening before, so the 00:00 stamp is the day's first.
 */
data class DayParts(val date: LocalDate, val sunrise: LocalDateTime, val sunset: LocalDateTime, val daylight: Daylight) {
    val start: LocalDateTime get() = date.atStartOfDay().minusHours(1)
    val end: LocalDateTime get() = date.atTime(23, 0)

    fun partOf(stamp: LocalDateTime): PartOfDay {
        val noon = maxOf(date.atTime(12, 0), sunrise)
        return when {
            stamp <= sunrise -> PartOfDay.EARLY
            stamp <= noon -> PartOfDay.MORNING
            stamp <= maxOf(sunset, noon) -> PartOfDay.AFTERNOON
            else -> PartOfDay.EVENING
        }
    }

    /**
     * The rows: "Before sunrise", "Daytime" (sunrise to sunset), "Evening" (sunset to midnight). When the sun doesn't
     * rise or set they run 7 AM to 7 PM and read "Early", "Midday" and "Evening". Parts without an hour are left out.
     */
    fun periods(hours: List<HourForecast>): List<RainPeriod> {
        val sun = daylight == Daylight.NORMAL
        val polar = daylight == Daylight.POLAR_NIGHT || daylight == Daylight.MIDNIGHT_SUN
        return listOfNotNull(
            period(hours, if (sun) "Before sunrise" else "Early", start, sunrise),
            period(hours, if (polar) "Midday" else "Daytime", sunrise, sunset),
            period(hours, "Evening", sunset, end),
        )
    }

    private fun period(hours: List<HourForecast>, name: String, from: LocalDateTime, to: LocalDateTime): RainPeriod? {
        val expected = ChronoUnit.HOURS.between(from, to).toInt()
        if (expected <= 0) return null
        val inside = hours.filter { it.time > from && it.time <= to }
        if (inside.isEmpty()) return null
        return RainPeriod(
            name = name,
            date = date,
            start = from,
            end = to,
            complete = inside.size >= expected,
            chance = inside.maxOf { it.precipChance },
            totalMm = inside.sumOf { it.precipMm ?: 0.0 },
            snowCm = inside.sumOf { it.snowCm ?: 0.0 },
            wetHours = inside.count { (it.precipMm ?: 0.0) >= Precip.HOUR_AMOUNT_MIN_MM },
        )
    }
}

enum class TimingShape { MOSTLY, ON_AND_OFF, CLEARING_BY_MORNING }

/**
 * When a day's rain falls: its [shape], the [parts] of the day involved, and the [peak] hour (the hour the rain falls
 * in) if one stands out. [sunrise] is false on a day the sun doesn't rise, which has early hours instead.
 */
data class Timing(val shape: TimingShape, val parts: List<PartOfDay>, val peak: LocalDateTime?, val sunrise: Boolean = true) {
    val early: String get() = if (sunrise) PartOfDay.EARLY.phrase else "in the early hours"
}

/**
 * Rain over a part of [date]: the stamps after [start] up to [end], with their highest hourly chance, their total, their
 * snow and the hours with at least 0.1 mm. [complete] once every hour of it is in the data.
 */
data class RainPeriod(
    val name: String,
    val date: LocalDate,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val complete: Boolean,
    val chance: Int,
    val totalMm: Double,
    val snowCm: Double,
    val wetHours: Int,
) {
    /** Judged on its own hours, by the same rule as a day. */
    val call: RainCall get() = Precip.classify(chance, totalMm)

    val dry: Boolean get() = call.dry

    /** Amount-driven: under 20% but with a real amount, so the amount is rounded and reads "up to". */
    val rough: Boolean get() = chance < Precip.DAY_CHANCE_MIN && call.amountShown

    /** Mostly snow. */
    val snow: Boolean get() = Precip.isSnow(snowCm, totalMm, Precip.SNOW_DAY_MIN_CM)

    /** "12 AM" for a part that starts the day (its first stamp, 00:00, ends the hour before midnight). */
    val labelStart: LocalDateTime get() = if (start < date.atStartOfDay()) date.atStartOfDay() else start

    /** "12 AM" (midnight) for a part that ends the day. */
    val labelEnd: LocalDateTime get() = if (end >= date.atTime(23, 0)) date.plusDays(1).atStartOfDay() else end

    operator fun contains(stamp: LocalDateTime): Boolean = stamp > start && stamp <= end

    /** "90% · 11 mm · 5 h", "40% · 3 cm snow · 4 h", "15% · up to 2 mm · 6 h", "45%". [snowWord] false drops "snow". */
    fun describe(unit: TempUnit, snowWord: Boolean = true): String = listOfNotNull(
        "$chance%",
        amount(unit)?.let { if (snow && snowWord) "$it snow" else it },
        wetHours.takeIf { it > 0 && call.amountShown }?.let { "$it h" },
    ).joinToString(" · ")

    /** "40 percent chance, about 3 millimetres, over 4 hours". */
    fun spoken(unit: TempUnit): String {
        val lead = if (rough) "up to" else "about"
        return listOfNotNull(
            "$chance percent chance",
            when {
                !call.amountShown -> null
                snow -> "$lead ${Precip.spokenSnow(snowCm, unit, rough)}"
                rough || totalMm >= Precip.DAY_TOTAL_MIN_MM -> "$lead ${Precip.spokenRain(totalMm, unit, rough)}"
                else -> "a few drops"
            },
            wetHours.takeIf { it > 0 && call.amountShown }?.let { "over ${Precip.plural(it, "hour")}" },
        ).joinToString(", ")
    }

    private fun amount(unit: TempUnit): String? {
        val lead = if (rough) "up to " else ""
        return when {
            !call.amountShown -> null
            snow -> lead + Precip.formatSnow(snowCm, unit, rough)
            rough || totalMm >= Precip.DAY_TOTAL_MIN_MM -> lead + Precip.formatRain(totalMm, unit, rough)
            else -> "a few drops"
        }
    }
}

/** Part of a day's counted rain, such as what's still to come: water, snow, wet hours and when it falls. */
data class RainSpan(val mm: Double, val snowCm: Double, val wetHours: Int, val timing: Timing?)

/** Everything about one day's rain or snow; see [Precip.dayRain]. */
data class DayRain(
    val date: LocalDate,
    /** The day's weather code, for "Showers" and "Thunderstorms". */
    val code: Int,
    /** The day's highest hourly chance. */
    val chance: Int,
    /** The counted water, mm: the parts that aren't dry. 0 on a dry day. */
    val totalMm: Double,
    /** The water in the parts that are mostly rain, and the snow (cm) in those that are mostly snow. */
    val rainMm: Double,
    val snowCm: Double,
    /** Counted hours with at least 0.1 mm. */
    val wetHours: Int,
    val call: RainCall,
    val mix: Mix,
    /** Whether the hourly data covers the whole day with amounts, so the figures are the parts' and the rows show. */
    val complete: Boolean,
    val parts: DayParts,
    /** The day's stamps, 00:00 to 23:00. */
    val hours: List<HourForecast>,
    /** The day's parts in order, dry ones included; empty without hourly amounts. */
    val periods: List<RainPeriod>,
    /** The stamps of the parts that aren't dry: what the figures, the chart and the timing are made of. */
    val counted: List<HourForecast>,
    val timing: Timing?,
    /** The next day's first part (midnight to its sunrise), when it isn't dry. */
    val carryOn: RainPeriod?,
) {
    val kind: DayKind get() = call.kind
    val dry: Boolean get() = call.dry
    val amountShown: Boolean get() = call.amountShown && !dry

    /** Amount-driven: under 20% but with a real amount, so the amount is rounded and reads "up to". */
    val rough: Boolean get() = chance < Precip.DAY_CHANCE_MIN && call.amountShown

    /** Whether the day's amount is shown as snow: most of the counted water fell as snow. */
    val showsSnow: Boolean get() = mix == Mix.SNOW || (mix == Mix.RAIN_AND_SNOW && Precip.isSnow(snowCm, totalMm, Precip.SNOW_DAY_MIN_CM))

    /** "Rain" or "Snow", for labels like the hero pill. */
    val noun: String get() = if (showsSnow) "Snow" else "Rain"

    /** The day page card's title: "Rain", "Snow" or "Rain and snow". */
    val title: String get() = when (mix) {
        Mix.RAIN -> "Rain"
        Mix.SNOW -> "Snow"
        Mix.RAIN_AND_SNOW -> "Rain and snow"
    }

    /** What falls: storms, snow, showers or rain, from the day's weather code and its mix. */
    val word: PrecipKind get() = when {
        code in 95..99 -> PrecipKind.THUNDERSTORMS
        mix == Mix.SNOW -> PrecipKind.SNOW
        mix == Mix.RAIN_AND_SNOW -> PrecipKind.RAIN_AND_SNOW
        code in 80..82 -> PrecipKind.SHOWERS
        else -> PrecipKind.RAIN
    }

    /** The parts to list on the day page: those that aren't dry, once the data covers the whole day. */
    val rows: List<RainPeriod> get() = if (complete && !dry) periods.filter { !it.dry } else emptyList()

    /** The counted rain from the hour that ends after [now] on (today's "still to come"). */
    fun stillToCome(now: LocalDateTime): RainSpan {
        val ahead = counted.filter { it.time > now }
        val snowParts = periods.filter { !it.dry && it.snow }
        return RainSpan(
            mm = ahead.sumOf { it.precipMm ?: 0.0 },
            snowCm = ahead.filter { h -> snowParts.any { h.time in it } }.sumOf { it.snowCm ?: 0.0 },
            wetHours = ahead.count { (it.precipMm ?: 0.0) >= Precip.HOUR_AMOUNT_MIN_MM },
            timing = Precip.timing(ahead, parts),
        )
    }

    /** The whole day's counted rain as a span. */
    val whole: RainSpan get() = RainSpan(totalMm, snowCm, wetHours, timing)
}
