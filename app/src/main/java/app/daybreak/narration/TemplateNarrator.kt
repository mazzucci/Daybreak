package app.daybreak.narration

import app.daybreak.domain.DayRain
import app.daybreak.domain.Precip
import app.daybreak.domain.TempUnit
import app.daybreak.domain.describeWeatherCode
import app.daybreak.domain.formatDegrees
import app.daybreak.domain.formatHour
import java.time.LocalDateTime
import java.util.Locale

/**
 * The summary line: a deterministic, always-available description of the forecast. English, with numbers and times
 * written the US way like the rest of the app's copy.
 */
class TemplateNarrator {

    /**
     * The summary. [withTotal] adds how much of today's rain is still to come; the widget leaves it out, since its
     * three lines can't hold it (and amounts aren't for a glance).
     */
    fun describe(input: NarrationInput, withTotal: Boolean = true): String {
        val f = input.forecast
        val u = input.unit
        val condition = describeWeatherCode(f.current.code).lowercase(Locale.US)
        val now = formatDegrees(f.current.tempC, u)
        val high = formatDegrees(f.today.highC, u)
        val low = formatDegrees(f.today.lowC, u)
        return "$now and $condition now, with a high of $high and a low of $low. ${rainSentence(input, withTotal)}"
    }

    private fun precipitatingNow(input: NarrationInput): Boolean = input.forecast.current.code in WET_CODES

    /**
     * The rain (or snow) sentence, with the shared chance words: the first hour ahead where it's likely, else the
     * first where it's possible (each hour with the chance stamped at its end, as the hourly strip shows it);
     * otherwise whether it's easing, today's chance, or none. Today's amount still to come joins the chance once it's
     * at least a millimetre: "Rain is likely around 5 PM (80% chance, about 4 mm still to come today)."
     */
    private fun rainSentence(input: NarrationInput, withTotal: Boolean): String {
        val f = input.forecast
        val today = Precip.dayRain(f, f.today.date)
        val ahead = f.nextHours.mapNotNull { h -> f.rainDuring(h)?.let { h to it } }
        val wet = ahead.firstOrNull { it.second.precipChance >= Precip.LIKELY }
            ?: ahead.firstOrNull { it.second.precipChance >= Precip.POSSIBLE }
        return when {
            wet != null -> {
                val (hour, rain) = wet
                // Today's figures hold the hours stamped today: an hour stamped after midnight belongs to tomorrow's.
                val isToday = rain.time.toLocalDate() == today.date
                val snow = Precip.isSnowHour(rain) || (isToday && today.showsSnow)
                val what = if (snow) "Snow" else "Rain"
                val word = Precip.likelihood(rain.precipChance).word
                val amount = if (withTotal && isToday) stillToCome(today, f.current.time, input.unit) else null
                "$what is $word around ${formatHour(hour.time, Locale.US)} (${rain.precipChance}% chance${amount?.let { ", $it" } ?: ""})."
            }
            // Already falling (the first sentence says so) but no hour ahead is likely: it's on its way out.
            precipitatingNow(input) -> "It should ease off soon."
            today.dry -> "No rain expected."
            Precip.showDayChance(today.chance) -> "There's a ${today.chance}% chance of ${today.noun.lowercase(Locale.US)} today."
            else -> "There's a small chance of ${today.noun.lowercase(Locale.US)} today."
        }
    }

    /** "about 4 mm still to come today" (or "about 3 cm" of snow) once that's at least a millimetre of water. */
    private fun stillToCome(today: DayRain, now: LocalDateTime, unit: TempUnit): String? {
        if (!today.amountShown) return null
        val rest = today.stillToCome(now)
        if (rest.mm < NARRATE_TOTAL_MM) return null
        val lead = if (today.rough) "up to" else "about"
        val amount = if (today.showsSnow && rest.snowCm >= Precip.SNOW_HOUR_MIN_CM) Precip.proseSnow(rest.snowCm, unit, today.rough) else Precip.proseRain(rest.mm, unit, today.rough)
        return "$lead $amount still to come today"
    }

    private companion object {
        /** The summary mentions the amount still to come from a millimetre up. */
        const val NARRATE_TOTAL_MM = 1.0
        val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
        /** Drizzle, rain, showers, snow and storms. */
        val WET_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99) + SNOW_CODES
    }
}
