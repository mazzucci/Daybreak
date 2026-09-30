package app.daybreak.narration

import app.daybreak.domain.Forecast
import app.daybreak.domain.TempUnit
import app.daybreak.domain.Tone

/**
 * What a narrator describes: one place's forecast, in the unit the user reads first, in the chosen [tone].
 * [aboutMe] is the user's optional note about themselves, for Gemma to decide what matters.
 */
data class NarrationInput(
    val placeName: String,
    val forecast: Forecast,
    val unit: TempUnit,
    val tone: Tone = Tone.FRIENDLY,
    val aboutMe: String = "",
)

enum class NarrationSource { TEMPLATE, GEMMA }

data class Narration(val text: String, val source: NarrationSource)

/** Turns a forecast into a short, human-readable description (1–2 sentences; up to 4 for the playful voices). */
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
) : AutoCloseable {
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

    /** Frees the LLM's memory now, e.g. after its model was removed; it reloads on the next narration. */
    fun releaseResources() = (primary as? GemmaNarrator)?.releaseEngine()

    override fun close() = (primary as? AutoCloseable)?.close() ?: Unit
}
