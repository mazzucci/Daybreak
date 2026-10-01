package app.daybreak.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The one rule set for rain and snow, so the hourly strip, the 10-day list, the day page, the hero pill, Home,
 * the widget and the summary never disagree about when a chance or an amount is worth showing, or what to call it.
 * Thresholds and wording follow docs/research/accuweather.md §9.2.
 *
 * Amounts are kept in mm (water) and cm (snow depth) as Open-Meteo sends them, and shown in one unit system that
 * follows the temperature setting: mm and cm with °C, inches with °F. Rain numbers are read for size, not converted,
 * so unlike temperatures they're never shown in both.
 */
object Precip {
    /** An hourly chance below this is noise: the cell leaves its line blank. */
    const val HOUR_CHANCE_MIN = 10

    /** From here a chance is worth planning around, and is drawn in the rain colour. */
    const val CHANCE_HIGHLIGHT = 40

    /** A daily chance below this isn't printed (the 10-day rows, Home, the widget). */
    const val DAY_CHANCE_MIN = 20

    /** Open-Meteo's probability is defined on 0.1 mm an hour; below it is a trace. */
    const val HOUR_AMOUNT_MIN_MM = 0.1

    /** The smallest day total worth a number. */
    const val DAY_TOTAL_MIN_MM = 0.5

    /** The smallest snowfall that makes it a snow day, or a snow hour. */
    const val SNOW_DAY_MIN_CM = 0.5
    const val SNOW_HOUR_MIN_CM = 0.1

    /** A wet day needs both a real chance and a real amount; a dry day lacks either. */
    const val WET_DAY_CHANCE = 50
    const val WET_DAY_MM = 1.0
    const val DRY_DAY_CHANCE = 20
    const val DRY_DAY_MM = 0.2

    /** Chance words: likely, possible, a small chance, unlikely. */
    const val LIKELY = 70
    const val POSSIBLE = 40

    /** Below this a day's rain is "a few drops", so the verdict doesn't print a number. */
    const val FEW_DROPS_MM = 1.0

    /** Rain-rate bands, mm an hour (Met Office style): light below 0.5, heavy above 4. */
    const val LIGHT_RATE_MM = 0.5
    const val HEAVY_RATE_MM = 4.0

    /** Open-Meteo: "for the water equivalent in millimeter, divide by 7", i.e. 7 cm of snow is about 10 mm of water. */
    private const val SNOW_CM_PER_WATER_MM = 0.7

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
        chance >= DRY_DAY_CHANCE -> Likelihood.SMALL
        else -> Likelihood.UNLIKELY
    }

    /** "a few drops", "light", "a proper soaking", "heavy rain": a day's total in round words. */
    fun amountWord(mm: Double): String = when {
        mm < 1.0 -> "a few drops"
        mm < 5.0 -> "light"
        mm <= 15.0 -> "a proper soaking"
        else -> "heavy rain"
    }

    /** How hard it falls in one hour. */
    fun intensity(mmPerHour: Double): Intensity = when {
        mmPerHour < LIGHT_RATE_MM -> Intensity.LIGHT
        mmPerHour <= HEAVY_RATE_MM -> Intensity.MODERATE
        else -> Intensity.HEAVY
    }

    /** Snow when there's enough of it and it makes up most of the water that falls. */
    fun isSnow(snowCm: Double?, precipMm: Double?, minCm: Double): Boolean {
        if (snowCm == null || snowCm < minCm) return false
        val waterMm = snowCm / SNOW_CM_PER_WATER_MM
        return waterMm >= (precipMm ?: 0.0) / 2
    }

    /** Whether a day is wet (worth calling rainy), dry, or in between. */
    fun classify(day: DaySummary): DayKind {
        val sum = day.precipSumMm ?: 0.0
        return when {
            day.precipChance < DRY_DAY_CHANCE || sum < DRY_DAY_MM -> DayKind.DRY
            day.precipChance >= WET_DAY_CHANCE && sum >= WET_DAY_MM -> DayKind.WET
            else -> DayKind.MIXED
        }
    }

    // --- Amounts as text --------------------------------------------------------------------------

    /**
     * Rain (water) for cells and rows: "0.6 mm", "4 mm", "18 mm" with °C (one decimal under 10 mm, a whole ".0"
     * dropped); "0.02 in", "0.2 in", "1.2 in" with °F (two decimals under a tenth, else one).
     */
    fun formatRain(mm: Double, unit: TempUnit, locale: Locale = Locale.US): String = when (unit) {
        TempUnit.C -> "${metric(mm, locale)} mm"
        TempUnit.F -> "${inches(mm / 25.4, locale)} in"
    }

    /** Snow depth for cells and rows: "0.4 cm", "3 cm", or "0.2 in", "1.2 in". */
    fun formatSnow(cm: Double, unit: TempUnit, locale: Locale = Locale.US): String = when (unit) {
        TempUnit.C -> "${metric(cm, locale)} cm"
        TempUnit.F -> "${inches(cm / 2.54, locale)} in"
    }

    /** Rain in a sentence: "4 mm", or "0.2 inches" (one inch is "1 inch"). */
    fun proseRain(mm: Double, unit: TempUnit, locale: Locale = Locale.US): String = when (unit) {
        TempUnit.C -> "${metric(mm, locale)} mm"
        TempUnit.F -> inches(mm / 25.4, locale).let { if (it == "1") "1 inch" else "$it inches" }
    }

    /** Snow in a sentence: "3 cm", or "1.2 inches". */
    fun proseSnow(cm: Double, unit: TempUnit, locale: Locale = Locale.US): String = when (unit) {
        TempUnit.C -> "${metric(cm, locale)} cm"
        TempUnit.F -> inches(cm / 2.54, locale).let { if (it == "1") "1 inch" else "$it inches" }
    }

    /** For screen readers: "about 0.6 millimetres", "about 0.02 inches", "about 3 centimetres of snow". */
    fun spokenRain(mm: Double, unit: TempUnit, locale: Locale = Locale.US): String = when (unit) {
        TempUnit.C -> "about ${metric(mm, locale)} millimetres"
        TempUnit.F -> "about ${proseRain(mm, unit, locale)}"
    }

    fun spokenSnow(cm: Double, unit: TempUnit, locale: Locale = Locale.US): String = when (unit) {
        TempUnit.C -> "about ${metric(cm, locale)} centimetres of snow"
        TempUnit.F -> "about ${proseSnow(cm, unit, locale)} of snow"
    }

    private fun metric(v: Double, locale: Locale): String =
        if (v >= 9.95) "${v.roundToInt()}" else String.format(locale, "%.1f", v).removeSuffix(".0").removeSuffix(",0")

    /** Never "0.00": the smallest amount we'd show (0.1 mm) still reads as 0.01 in. */
    private fun inches(v: Double, locale: Locale): String = when {
        v < 0.095 -> String.format(locale, "%.2f", v.coerceAtLeast(0.01))
        else -> String.format(locale, "%.1f", v).removeSuffix(".0").removeSuffix(",0")
    }

    // --- Hours ------------------------------------------------------------------------------------

    fun isSnowHour(hour: HourForecast): Boolean = isSnow(hour.snowCm, hour.precipMm, SNOW_HOUR_MIN_CM)

    /** The hour's amount for its cell ("0.6 mm", "0.4 cm"), or null below the threshold or when it's missing. */
    fun hourAmount(hour: HourForecast, unit: TempUnit, locale: Locale = Locale.US): String? = when {
        isSnowHour(hour) -> formatSnow(hour.snowCm!!, unit, locale)
        (hour.precipMm ?: 0.0) >= HOUR_AMOUNT_MIN_MM -> formatRain(hour.precipMm!!, unit, locale)
        else -> null
    }

    /** The same, spoken: "about 0.6 millimetres". */
    fun hourAmountSpoken(hour: HourForecast, unit: TempUnit, locale: Locale = Locale.US): String? = when {
        isSnowHour(hour) -> spokenSnow(hour.snowCm!!, unit, locale)
        (hour.precipMm ?: 0.0) >= HOUR_AMOUNT_MIN_MM -> spokenRain(hour.precipMm!!, unit, locale)
        else -> null
    }

    // --- Days -------------------------------------------------------------------------------------

    fun isSnowDay(day: DaySummary): Boolean = isSnow(day.snowSumCm, day.precipSumMm, SNOW_DAY_MIN_CM)

    /** "Rain" or "Snow", for labels like the hero pill. */
    fun noun(day: DaySummary): String = if (isSnowDay(day)) "Snow" else "Rain"

    /** The day's total for the end of its row ("4 mm", "3 cm snow"), or null when there's too little to mention. */
    fun dayTotal(day: DaySummary, unit: TempUnit, locale: Locale = Locale.US): String? = when {
        isSnowDay(day) -> "${formatSnow(day.snowSumCm!!, unit, locale)} snow"
        (day.precipSumMm ?: 0.0) >= DAY_TOTAL_MIN_MM -> formatRain(day.precipSumMm!!, unit, locale)
        else -> null
    }

    /** The day's total as an amount only ("4 mm", "3 cm"), for places that already say rain or snow. */
    fun dayAmount(day: DaySummary, unit: TempUnit, locale: Locale = Locale.US): String? = when {
        isSnowDay(day) -> formatSnow(day.snowSumCm!!, unit, locale)
        (day.precipSumMm ?: 0.0) >= DAY_TOTAL_MIN_MM -> formatRain(day.precipSumMm!!, unit, locale)
        else -> null
    }

    fun dayTotalSpoken(day: DaySummary, unit: TempUnit, locale: Locale = Locale.US): String? = when {
        isSnowDay(day) -> spokenSnow(day.snowSumCm!!, unit, locale)
        (day.precipSumMm ?: 0.0) >= DAY_TOTAL_MIN_MM -> spokenRain(day.precipSumMm!!, unit, locale)
        else -> null
    }

    /** What falls on a day: storms, snow, showers or rain, from the day's weather code and its snowfall. */
    fun kind(day: DaySummary): PrecipKind = when {
        day.code in 95..99 -> PrecipKind.THUNDERSTORMS
        isSnowDay(day) -> PrecipKind.SNOW
        day.code in 80..82 -> PrecipKind.SHOWERS
        else -> PrecipKind.RAIN
    }

    /**
     * The day page's headline: "Rain likely · about 12 mm over 6 hours", "Showers possible · a few drops",
     * "Snow likely · about 3 cm", "Rain unlikely". The chance word comes from the day's highest hourly chance, the
     * same number the row shows, so the two never disagree.
     */
    fun verdict(day: DaySummary, unit: TempUnit, locale: Locale = Locale.US): String {
        val likelihood = likelihood(day.precipChance)
        if (likelihood == Likelihood.UNLIKELY) return "Rain unlikely"
        val kind = kind(day)
        val head = when (likelihood) {
            Likelihood.SMALL -> "A small chance of ${kind.word.lowercase(locale)}"
            else -> "${kind.word} ${likelihood.word}"
        }
        val sum = day.precipSumMm ?: return head
        val amount = when {
            kind == PrecipKind.SNOW -> "about ${proseSnow(day.snowSumCm!!, unit, locale)}"
            sum <= 0.0 -> return head
            sum < FEW_DROPS_MM -> "a few drops"
            else -> {
                val hours = day.precipHours?.roundToInt()?.takeIf { it > 0 }
                "about ${proseRain(sum, unit, locale)}" + (hours?.let { " over ${plural(it, "hour")}" } ?: "")
            }
        }
        return "$head · $amount"
    }

    /** The day's hourly amounts, its timing, and its daytime and overnight halves, for the day page. */
    fun dayRain(forecast: Forecast, date: LocalDate): DayRain {
        val day = forecast.day(date)
        val hours = forecast.hoursOf(date)
        val (dayStart, dayEnd) = daytimeBounds(day, date)
        val nextSunrise = forecast.day(date.plusDays(1))?.takeIf { it.daylight == Daylight.NORMAL }?.sunrise
            ?.let(::nearestHour) ?: dayStart.plusDays(1)
        return DayRain(
            hours = hours,
            hasAmounts = hours.any { it.precipMm != null },
            timing = timing(hours),
            beforeSunrise = period(forecast, date.atStartOfDay(), dayStart),
            daytime = period(forecast, dayStart, dayEnd),
            overnight = period(forecast, dayEnd, nextSunrise),
        )
    }

    /**
     * When in the day the rain falls, from the hourly amounts: mostly in one part of the day, in two, on and off
     * all day, or overnight and then clearing; with the heaviest hour when one stands out. Null when the hours hold
     * no rain (or no amounts at all).
     */
    fun timing(hours: List<HourForecast>): Timing? {
        val amounts = hours.map { it.time to (it.precipMm ?: 0.0) }
        val total = amounts.sumOf { it.second }
        if (total < HOUR_AMOUNT_MIN_MM) return null
        val shares = PartOfDay.entries.associateWith { part ->
            amounts.filter { PartOfDay.of(it.first) == part }.sumOf { it.second } / total
        }
        val wetHours = amounts.count { it.second >= HOUR_AMOUNT_MIN_MM }
        val (peakTime, peakMm) = amounts.maxBy { it.second }
        val peak = peakTime.takeIf { wetHours >= 2 && total >= FEW_DROPS_MM && peakMm / total >= PEAK_SHARE }
        val main = shares.maxBy { it.value }
        val wetParts = shares.filter { it.value >= SOME_SHARE }.keys.sortedBy { it.ordinal }
        return when {
            main.key == PartOfDay.NIGHT && shares.getValue(PartOfDay.NIGHT) > 0.95 -> Timing(TimingShape.CLEARING_BY_MORNING, listOf(PartOfDay.NIGHT), null)
            main.value >= MOSTLY_SHARE -> Timing(TimingShape.MOSTLY, listOf(main.key), peak)
            wetParts.size >= 3 -> Timing(TimingShape.ON_AND_OFF, wetParts, peak)
            else -> Timing(TimingShape.MOSTLY, wetParts.ifEmpty { listOf(main.key) }, peak)
        }
    }

    /** "Mostly in the afternoon, heaviest around 4 PM." · "On and off all day." · "Overnight, clearing by morning." */
    fun timingSentence(timing: Timing, locale: Locale = Locale.getDefault()): String {
        val base = when (timing.shape) {
            TimingShape.CLEARING_BY_MORNING -> return "Overnight, clearing by morning."
            TimingShape.ON_AND_OFF -> "On and off all day"
            TimingShape.MOSTLY -> "Mostly " + joinParts(timing.parts) { it.phrase }
        }
        val peak = timing.peak?.let { ", heaviest around ${formatHour(it, locale)}" } ?: ""
        return "$base$peak."
    }

    /** The same for today, in the hero's explanation: "mostly this evening", "on and off all day", "overnight". */
    fun timingToday(timing: Timing): String = when (timing.shape) {
        TimingShape.CLEARING_BY_MORNING -> "overnight"
        TimingShape.ON_AND_OFF -> "on and off all day"
        TimingShape.MOSTLY -> "mostly " + joinParts(timing.parts) { it.today }
    }

    /** "in the morning and afternoon", "overnight and in the morning": the article isn't repeated. */
    private fun joinParts(parts: List<PartOfDay>, phrase: (PartOfDay) -> String): String {
        val first = phrase(parts.first())
        val article = listOf("in the ", "this ").firstOrNull { first.startsWith(it) }
        val rest = parts.drop(1).map { p -> phrase(p).let { if (article != null) it.removePrefix(article) else it } }
        return (listOf(first) + rest).joinToString(" and ")
    }

    /** Sunrise to sunset rounded to the hour; 7 AM to 7 PM when the day has no ordinary sunrise and sunset. */
    private fun daytimeBounds(day: DaySummary?, date: LocalDate): Pair<LocalDateTime, LocalDateTime> =
        if (day?.daylight == Daylight.NORMAL) nearestHour(day.sunrise!!) to nearestHour(day.sunset!!)
        else date.atTime(7, 0) to date.atTime(19, 0)

    private fun nearestHour(t: LocalDateTime): LocalDateTime =
        t.truncatedTo(ChronoUnit.HOURS).let { if (t.minute >= 30) it.plusHours(1) else it }

    /** The hours from [start] up to [end]; null unless the forecast covers all of them. */
    private fun period(forecast: Forecast, start: LocalDateTime, end: LocalDateTime): RainPeriod? {
        val hours = forecast.hours.filter { !it.time.isBefore(start) && it.time.isBefore(end) }
        val expected = ChronoUnit.HOURS.between(start, end).toInt()
        if (expected <= 0 || hours.size < expected) return null
        return RainPeriod(
            start = start,
            end = end,
            chance = hours.maxOf { it.precipChance },
            totalMm = hours.sumOf { it.precipMm ?: 0.0 },
            snowCm = hours.sumOf { it.snowCm ?: 0.0 },
            wetHours = hours.count { (it.precipMm ?: 0.0) >= HOUR_AMOUNT_MIN_MM },
        )
    }

    private fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"
}

enum class Likelihood(val word: String) { LIKELY("likely"), POSSIBLE("possible"), SMALL("a small chance"), UNLIKELY("unlikely") }

enum class Intensity { LIGHT, MODERATE, HEAVY }

enum class DayKind { WET, MIXED, DRY }

enum class PrecipKind(val word: String) { RAIN("Rain"), SHOWERS("Showers"), SNOW("Snow"), THUNDERSTORMS("Thunderstorms") }

/** Quarters of the day by the hour on the clock. */
enum class PartOfDay(val phrase: String, val today: String) {
    NIGHT("overnight", "overnight"),
    MORNING("in the morning", "this morning"),
    AFTERNOON("in the afternoon", "this afternoon"),
    EVENING("in the evening", "this evening");

    companion object {
        fun of(t: LocalDateTime): PartOfDay = when (t.hour) {
            in 0..5 -> NIGHT
            in 6..11 -> MORNING
            in 12..17 -> AFTERNOON
            else -> EVENING
        }
    }
}

enum class TimingShape { MOSTLY, ON_AND_OFF, CLEARING_BY_MORNING }

/** When the day's rain falls: its [shape], the [parts] of the day involved, and the [peak] hour if one stands out. */
data class Timing(val shape: TimingShape, val parts: List<PartOfDay>, val peak: LocalDateTime?)

/** Rain over part of a day: the highest hourly chance, the total, and the hours with at least 0.1 mm. */
data class RainPeriod(
    val start: LocalDateTime,
    val end: LocalDateTime,
    val chance: Int,
    val totalMm: Double,
    val snowCm: Double,
    val wetHours: Int,
) {
    /** Dry by the same rule as a dry day: too small a chance, or too little rain. */
    val dry: Boolean get() = chance < Precip.DRY_DAY_CHANCE || totalMm < Precip.DRY_DAY_MM

    val snow: Boolean get() = Precip.isSnow(snowCm, totalMm, Precip.SNOW_DAY_MIN_CM)

    /** "90% · 11 mm · 5 h", "40% · 3 cm snow · 4 h". */
    fun describe(unit: TempUnit, locale: Locale = Locale.US): String = listOfNotNull(
        "$chance%",
        when {
            snow -> "${Precip.formatSnow(snowCm, unit, locale)} snow"
            totalMm >= Precip.DAY_TOTAL_MIN_MM -> Precip.formatRain(totalMm, unit, locale)
            else -> "a few drops"
        },
        wetHours.takeIf { it > 0 }?.let { "$it h" },
    ).joinToString(" · ")

    /** "40 percent chance, about 3 millimetres, over 4 hours". */
    fun spoken(unit: TempUnit, locale: Locale = Locale.US): String = listOfNotNull(
        "$chance percent chance",
        when {
            snow -> Precip.spokenSnow(snowCm, unit, locale)
            totalMm >= Precip.DAY_TOTAL_MIN_MM -> Precip.spokenRain(totalMm, unit, locale)
            else -> "a few drops"
        },
        wetHours.takeIf { it > 0 }?.let { "over $it hour${if (it == 1) "" else "s"}" },
    ).joinToString(", ")
}

/** Everything the day page's rain card shows. */
data class DayRain(
    /** The day's hours, midnight to 11 PM. */
    val hours: List<HourForecast>,
    /** Whether the hours carry amounts at all (a response cached by an older version has none). */
    val hasAmounts: Boolean,
    val timing: Timing?,
    /**
     * Midnight to sunrise (the end of the previous night, which belongs to the day's total but to neither half
     * below), sunrise to sunset, and sunset to the next sunrise; each null when the forecast doesn't cover it.
     */
    val beforeSunrise: RainPeriod?,
    val daytime: RainPeriod?,
    val overnight: RainPeriod?,
)
