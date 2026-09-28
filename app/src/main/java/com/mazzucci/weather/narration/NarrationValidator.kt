package com.mazzucci.weather.narration

import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.degrees
import com.mazzucci.weather.domain.kmhToMph
import kotlin.math.roundToInt

/**
 * Guards against a small on-device model making things up: every number it mentions must be one
 * that appears in the forecast we gave it, and the text has to look like a short plain-text summary.
 */
class NarrationValidator(private val maxChars: Int = 280) {

    /** Strips wrapping quotes, markdown emphasis and extra whitespace the model tends to add. */
    fun clean(raw: String): String = raw
        .replace(Regex("[*_`#]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('"', '“', '”')
        .trim()

    fun isValid(text: String, input: NarrationInput): Boolean {
        if (text.isBlank() || text.length > maxChars) return false
        if (text.contains('<') || text.contains('{')) return false // leaked template tokens or JSON

        // Hour labels ("3 PM", "15:00") are fine as long as they're real clock times.
        val withoutTimes = TIME.replace(text) { m ->
            if (m.groupValues[1].toInt() in 0..23) "TIME" else m.value
        }
        if (SENTENCE_END.findAll(withoutTimes).count() > 2) return false

        val temps = allowedTemps(input)
        val percents = allowedPercents(input)
        val winds = allowedWinds(input)
        return NUMBER.findAll(withoutTimes).all { m ->
            val value = m.groupValues[1].toDoubleOrNull() ?: return@all false
            if (value != value.roundToInt().toDouble()) return@all false // we only give it whole numbers
            val n = value.roundToInt()
            val suffix = m.groupValues[2].replace(" ", "").lowercase()
            when {
                suffix == "°f" -> n in temps.getValue(TempUnit.F)
                suffix == "°c" -> n in temps.getValue(TempUnit.C)
                suffix.startsWith("°") || suffix.startsWith("degree") -> n in temps.getValue(input.unit)
                suffix == "%" || suffix.startsWith("percent") -> n in percents
                suffix == "mph" || suffix == "km/h" || suffix == "kph" -> n in winds
                // A bare number can only be a wind speed or the hour window: temperatures and percentages
                // must carry their unit, so a stray "74" can't pass as some other value that happens to match.
                else -> n in winds || n == input.forecast.nextHours.size
            }
        }
    }

    private fun allowedTemps(input: NarrationInput): Map<TempUnit, Set<Int>> {
        val f = input.forecast
        val celsius = listOf(f.current.tempC, f.current.feelsLikeC, f.today.highC, f.today.lowC) +
            f.nextHours.map { it.tempC }
        return TempUnit.entries.associateWith { unit -> celsius.map { degrees(it, unit) }.toSet() }
    }

    private fun allowedPercents(input: NarrationInput): Set<Int> {
        val f = input.forecast
        return (f.nextHours.map { it.precipChance } + f.today.precipChance + f.current.humidity).toSet()
    }

    private fun allowedWinds(input: NarrationInput): Set<Int> {
        val wind = input.forecast.current.windKmh
        return setOf(wind.roundToInt(), kmhToMph(wind).roundToInt())
    }

    private companion object {
        val SENTENCE_END = Regex("[.!?](\\s|$)")
        /** "3 PM", "3pm", "3 p.m.", "3:30 pm", "15:00". Group 1 is the hour. */
        val TIME = Regex("\\b(\\d{1,2})(?::\\d{2}(?:\\s?[AaPp]\\.?[Mm]\\.?)?|\\s?[AaPp]\\.?[Mm]\\.?)(?![A-Za-z0-9])")
        val NUMBER = Regex("(-?\\d+(?:\\.\\d+)?)(\\s?°\\s?[FfCc]?|\\s?%|\\s?percent|\\s?degrees?|\\s?mph|\\s?km/h|\\s?kph)?")
    }
}
