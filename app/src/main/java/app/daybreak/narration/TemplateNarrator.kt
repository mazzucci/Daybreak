package app.daybreak.narration

import app.daybreak.domain.DaySummary
import app.daybreak.domain.Precip
import app.daybreak.domain.describeWeatherCode
import app.daybreak.domain.formatDegrees
import app.daybreak.domain.formatHour
import java.util.Locale

/** The summary line: a deterministic, always-available description of the forecast. */
class TemplateNarrator(private val locale: Locale = Locale.getDefault()) {

    /**
     * The summary. [withTotal] adds the day's rain total to the rain sentence; the widget leaves it out, since its
     * three lines can't hold it (and totals aren't for a glance).
     */
    fun describe(input: NarrationInput, withTotal: Boolean = true): String {
        val f = input.forecast
        val u = input.unit
        val condition = describeWeatherCode(f.current.code).lowercase(locale)
        val now = formatDegrees(f.current.tempC, u)
        val high = formatDegrees(f.today.highC, u)
        val low = formatDegrees(f.today.lowC, u)
        return "$now and $condition now, with a high of $high and a low of $low. ${rainSentence(input, withTotal)}"
    }

    private fun precipitatingNow(input: NarrationInput): Boolean = input.forecast.current.code in WET_CODES

    /**
     * The rain (or snow) sentence, with the shared chance words: the first hour ahead where it's likely, else the
     * first where it's possible; otherwise whether it's easing, today's chance, or none. The day's total joins the
     * chance once there's at least a millimetre: "Rain is likely around 6 PM (80% chance, about 4 mm)."
     */
    private fun rainSentence(input: NarrationInput, withTotal: Boolean): String {
        val f = input.forecast
        val today = f.today
        val wetHour = f.nextHours.firstOrNull { it.precipChance >= Precip.LIKELY }
            ?: f.nextHours.firstOrNull { it.precipChance >= Precip.POSSIBLE }
        return when {
            wetHour != null -> {
                val snow = Precip.isSnowHour(wetHour) || (wetHour.time.toLocalDate() == today.date && Precip.isSnowDay(today))
                val what = if (snow) "Snow" else "Rain"
                val word = Precip.likelihood(wetHour.precipChance).word
                // Only today's total: an hour after midnight belongs to tomorrow's.
                val amount = today.takeIf { withTotal && wetHour.time.toLocalDate() == it.date }?.let { totalPhrase(it, snow, input) }
                "$what is $word around ${formatHour(wetHour.time, locale)} (${wetHour.precipChance}% chance${amount?.let { ", $it" } ?: ""})."
            }
            // Already falling (the first sentence says so) but no hour ahead is likely: it's on its way out.
            precipitatingNow(input) -> "It should ease off soon."
            Precip.showDayChance(today.precipChance) ->
                "There's a ${today.precipChance}% chance of ${if (Precip.isSnowDay(today)) "snow" else "rain"} today."
            else -> "No rain expected."
        }
    }

    /** "about 4 mm" (or "about 3 cm" of snow) once the day's total reaches a millimetre of water. */
    private fun totalPhrase(day: DaySummary, snow: Boolean, input: NarrationInput): String? {
        val mm = day.precipSumMm?.takeIf { it >= NARRATE_TOTAL_MM } ?: return null
        return if (snow && day.snowSumCm != null) "about ${Precip.proseSnow(day.snowSumCm, input.unit, locale)}"
        else "about ${Precip.proseRain(mm, input.unit, locale)}"
    }

    private companion object {
        /** The summary mentions the day's total from a millimetre up. */
        const val NARRATE_TOTAL_MM = 1.0
        val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
        /** Drizzle, rain, showers, snow and storms. */
        val WET_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99) + SNOW_CODES
    }
}
