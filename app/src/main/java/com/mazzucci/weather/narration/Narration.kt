package com.mazzucci.weather.narration

import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.TempUnit

/** What a narrator describes: one place's forecast, in the unit the user reads first. */
data class NarrationInput(
    val placeName: String,
    val forecast: Forecast,
    val unit: TempUnit,
)

enum class NarrationSource { TEMPLATE, GEMMA }

data class Narration(val text: String, val source: NarrationSource)

/** Turns a forecast into a short, human-readable description (1–2 sentences). */
fun interface WeatherNarrator {
    /** May throw (model missing, timeout, …); callers that need a guaranteed answer use [ValidatingNarrator]. */
    suspend fun narrate(input: NarrationInput): String
}

/**
 * Tries [primary] (the LLM), and keeps its text only if [NarrationValidator] accepts it;
 * otherwise, or on any failure, falls back to [fallback] (the template).
 */
class ValidatingNarrator(
    private val primary: WeatherNarrator,
    private val fallback: WeatherNarrator = TemplateNarrator(),
    private val validator: NarrationValidator = NarrationValidator(),
) {
    suspend fun narrate(input: NarrationInput): Narration {
        val llm = try {
            primary.narrate(input)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val cleaned = llm?.let(validator::clean)
        if (cleaned != null && validator.isValid(cleaned, input)) return Narration(cleaned, NarrationSource.GEMMA)
        return Narration(fallback.narrate(input), NarrationSource.TEMPLATE)
    }
}
