package com.mazzucci.weather.narration

import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.describeWeatherCode
import java.time.LocalDate

/** The overall feel of a place's day, which picks the meme's backdrop and caption pool. */
enum class MemeMood { STORM, SNOW, RAIN, HEAT, COLD, WIND, FOG, GLOOM, SUN, MIXED }

/** A two-line, classic-format meme caption about today's weather. */
data class Meme(val top: String, val bottom: String, val mood: MemeMood, val source: NarrationSource)

/** What the day is mostly about, most dramatic first: a storm beats rain, rain beats a hot day, and so on. */
fun memeMoodOf(forecast: Forecast): MemeMood {
    val today = forecast.today
    return when {
        today.code in 95..99 -> MemeMood.STORM
        today.code in SNOW_CODES -> MemeMood.SNOW
        today.code in RAIN_CODES || today.precipChance >= 60 -> MemeMood.RAIN
        today.highC >= 30 -> MemeMood.HEAT
        today.highC <= 3 -> MemeMood.COLD
        (today.gustMaxKmh ?: 0.0) >= 50 -> MemeMood.WIND
        today.code == 45 || today.code == 48 -> MemeMood.FOG
        today.code == 3 -> MemeMood.GLOOM
        today.code == 0 || today.code == 1 -> MemeMood.SUN
        else -> MemeMood.MIXED
    }
}

private val SNOW_CODES = setOf(71, 73, 75, 77, 85, 86)
private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)

/** Same place and day → same number, so the fallback meme and Gemma's sampling are stable for the day. */
fun memeSeed(placeKey: String, date: LocalDate): Int = "$placeKey|$date".hashCode()

/** Hand-written captions, always available. No numbers, so they can never contradict the forecast. */
object TemplateMemes {
    fun pick(mood: MemeMood, seed: Int): Meme {
        val pool = CAPTIONS.getValue(mood)
        val (top, bottom) = pool[Math.floorMod(seed, pool.size)]
        return Meme(top, bottom, mood, NarrationSource.TEMPLATE)
    }

    val CAPTIONS: Map<MemeMood, List<Pair<String, String>>> = mapOf(
        MemeMood.STORM to listOf(
            "Thunder rolls" to "My dog under the bed",
            "Today's plans" to "Cancelled by thunder",
            "Nature said" to "Turn it up to eleven",
        ),
        MemeMood.SNOW to listOf(
            "Snow day?" to "Snow day.",
            "Everyone today" to "Professional snowman architect",
            "Roads: icy" to "Hot chocolate: mandatory",
        ),
        MemeMood.RAIN to listOf(
            "Me: I'll just run to the car" to "The sky: bold of you",
            "Leaves umbrella at home" to "Rain has entered the chat",
            "Cloudy with a chance" to "Of wet socks",
        ),
        MemeMood.HEAT to listOf(
            "It's not the heat" to "Okay it's the heat",
            "Me stepping outside" to "Instant rotisserie chicken",
            "Ice cream melting" to "Faster than my plans",
        ),
        MemeMood.COLD to listOf(
            "Layers?" to "Yes. All of them.",
            "My fingers" to "Filed a formal complaint",
            "Outside: freezing" to "Blanket: stay",
        ),
        MemeMood.WIND to listOf(
            "My hair today" to "Chose violence",
            "Umbrella opens" to "Umbrella leaves forever",
            "Hold onto your hat" to "Literally",
        ),
        MemeMood.FOG to listOf(
            "Where did the city go" to "Fog: it's mine now",
            "Visibility" to "Vibes only",
            "Mysterious fog" to "But make it breakfast",
        ),
        MemeMood.GLOOM to listOf(
            "Grey sky" to "Great coffee",
            "The sun" to "Is on vacation",
            "Cloudy all day" to "Blanket fort weather",
        ),
        MemeMood.SUN to listOf(
            "The sun woke up" to "And chose happiness",
            "Sunscreen exists" to "Me: I'll be fine",
            "Today's forecast" to "Main character energy",
        ),
        MemeMood.MIXED to listOf(
            "Sun or clouds?" to "Why not both",
            "The sky can't decide" to "Neither can I",
            "Partly cloudy" to "Fully fine",
        ),
    )
}

/**
 * Asks the model for a caption in a fixed "TOP: … / BOTTOM: …" format. The prompt describes the day in words
 * only, so there are no numbers for the model to repeat (and get wrong); [MemeValidator] rejects any digit.
 */
object MemePrompt {
    fun build(input: NarrationInput, mood: MemeMood): String {
        val today = input.forecast.today
        val feel = when {
            today.highC >= 30 -> "hot"
            today.highC >= 22 -> "warm"
            today.highC >= 12 -> "mild"
            today.highC >= 3 -> "chilly"
            else -> "freezing"
        }
        val rain = when {
            today.precipChance >= 60 -> "rain likely"
            today.precipChance >= 20 -> "a chance of rain"
            else -> "dry"
        }
        val windy = if ((today.gustMaxKmh ?: 0.0) >= 40) ", windy" else ""
        return """
            Write a funny, family-friendly meme caption about today's weather in ${input.placeName}.
            Today: ${describeWeatherCode(today.code).lowercase()}, $feel, $rain$windy. Theme: ${mood.name.lowercase()}.
            Use the classic two-line meme format: the top line sets it up, the bottom line is the punchline.
            At most six words per line. No numbers, no hashtags, no emoji, no quotes.
            Answer in exactly this format and nothing else:
            TOP: <top line>
            BOTTOM: <bottom line>
        """.trimIndent()
    }
}

/** Parses and checks a model caption. Anything odd is rejected and the template meme is used instead. */
class MemeValidator(private val maxChars: Int = 48) {
    fun parse(raw: String): Pair<String, String>? {
        val top = LINE_TOP.find(raw)?.groupValues?.get(1)?.let(::clean)
        val bottom = LINE_BOTTOM.find(raw)?.groupValues?.get(1)?.let(::clean)
        if (top == null || bottom == null) return null
        return (top to bottom).takeIf { isValidLine(top) && isValidLine(bottom) && !top.equals(bottom, ignoreCase = true) }
    }

    private fun clean(line: String) = line
        .replace(Regex("[*_`#\"“”]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun isValidLine(line: String): Boolean =
        line.length in 2..maxChars &&
            line.none { it.isDigit() } && // no numbers at all: nothing to get wrong
            !line.contains('<') && !line.contains('{') && // leaked template tokens or JSON
            !line.contains("http", ignoreCase = true) &&
            BLOCKLIST.none { Regex("\\b$it\\b", RegexOption.IGNORE_CASE).containsMatchIn(line) }

    private companion object {
        val LINE_TOP = Regex("(?im)^\\s*top\\s*:\\s*(.+)$")
        val LINE_BOTTOM = Regex("(?im)^\\s*bottom\\s*:\\s*(.+)$")
        /** A last line of defence for a family-friendly card; the prompt asks for clean humour anyway. */
        val BLOCKLIST = listOf("damn", "hell", "shit", "fuck\\w*", "crap", "ass", "bitch", "sexy", "kill", "die", "dead")
    }
}

/** Free-form generation by the on-device model, for features beyond the summary line. */
fun interface TextGenerator {
    /** [temperature] 0 = literal, 1 = playful; [seed] makes the sampling repeatable. May throw. */
    suspend fun generate(prompt: String, temperature: Float, seed: Int): String
}

/**
 * Writes the day's meme: the template instantly, Gemma's when [generator] is available and its caption passes
 * [MemeValidator].
 */
class MemeWriter(
    private val generator: TextGenerator? = null,
    private val validator: MemeValidator = MemeValidator(),
) {
    val canUseModel: Boolean get() = generator != null

    fun template(input: NarrationInput, placeKey: String): Meme =
        TemplateMemes.pick(memeMoodOf(input.forecast), memeSeed(placeKey, input.forecast.today.date))

    /** Gemma's meme, or null if the model failed or its caption didn't pass the checks. */
    suspend fun fromModel(input: NarrationInput, placeKey: String): Meme? {
        val gen = generator ?: return null
        val mood = memeMoodOf(input.forecast)
        val raw = try {
            gen.generate(MemePrompt.build(input, mood), temperature = 0.9f, seed = memeSeed(placeKey, input.forecast.today.date))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val (top, bottom) = validator.parse(raw) ?: return null
        return Meme(top, bottom, mood, NarrationSource.GEMMA)
    }
}
