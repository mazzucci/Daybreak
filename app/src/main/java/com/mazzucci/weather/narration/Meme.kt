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

/** Hand-written captions (six per mood), always available. No numbers, so they can never contradict the forecast. */
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
            "The sky today" to "Free light show",
            "Thunder outside" to "Blanket fort inside",
            "Me: a quick walk" to "Lightning: absolutely not",
            "Storm rolls in" to "Plans roll out",
        ),
        MemeMood.SNOW to listOf(
            "Snow day?" to "Snow day.",
            "Job title today" to "Snowman architect",
            "Roads: icy" to "Hot chocolate: mandatory",
            "Snow outside" to "Hot cocoa inside",
            "Me: a short drive" to "The snow: hold my mittens",
            "Everything is white" to "Except my nose",
        ),
        MemeMood.RAIN to listOf(
            "Me: I'll just run to the car" to "The sky: bold of you",
            "Leaves umbrella at home" to "Rain has entered the chat",
            "Cloudy with a chance" to "Of wet socks",
            "Umbrella: forgotten" to "Hair: ruined",
            "The clouds today" to "Crying for no reason",
            "Puddles everywhere" to "Choose your jump wisely",
        ),
        MemeMood.HEAT to listOf(
            "It's not the heat" to "Okay it's the heat",
            "Me stepping outside" to "Instant rotisserie chicken",
            "Today's ice cream" to "Now a beverage",
            "Stepped outside" to "Instantly toasted",
            "The sidewalk today" to "Basically a frying pan",
            "Shade is" to "The best real estate",
        ),
        MemeMood.COLD to listOf(
            "Layers?" to "Yes. All of them.",
            "My fingers" to "Filed a formal complaint",
            "Outside: freezing" to "Blanket: never letting go",
            "Me: one more layer" to "Me: now a burrito",
            "Car windshield" to "Frozen solid",
            "Warm coffee" to "My only friend today",
        ),
        MemeMood.WIND to listOf(
            "My hair today" to "Styled by the wind",
            "Umbrella opens" to "Umbrella leaves forever",
            "Hold onto your hat" to "Literally",
            "Me: nice hat" to "The wind: my hat now",
            "Trash bins today" to "Going on an adventure",
            "Walking to work" to "Mostly sideways",
        ),
        MemeMood.FOG to listOf(
            "Where did the city go" to "Fog: it's mine now",
            "Visibility" to "Vibes only",
            "Thick fog today" to "The city is buffering",
            "Is it fog" to "Or did I forget my glasses",
            "Low clouds" to "Came to say hello",
            "The view today" to "Loading, please wait",
        ),
        MemeMood.GLOOM to listOf(
            "Grey sky" to "Great coffee",
            "The sun today" to "Out of office",
            "Cloudy all day" to "Blanket fort weather",
            "Grey skies" to "Cozy sweater energy",
            "No sun today" to "Extra coffee instead",
            "The sky" to "Set to grayscale",
        ),
        MemeMood.SUN to listOf(
            "The sun woke up" to "And chose happiness",
            "Sunscreen: exists" to "Me: I'll be fine",
            "Today's forecast" to "Main character energy",
            "Clear skies" to "Big smile energy",
            "Sunglasses" to "Finally earning their keep",
            "Me after a sunny day" to "Human solar panel",
        ),
        MemeMood.MIXED to listOf(
            "Sun or clouds?" to "Why not both",
            "The sky can't decide" to "Neither can I",
            "Partly cloudy" to "Fully fine",
            "Sunglasses or umbrella?" to "Bring both",
            "The clouds" to "Playing peekaboo with the sun",
            "Weather today" to "A little bit of everything",
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
    /**
     * The last valid TOP line followed by a valid BOTTOM line, so a model that first echoes the prompt's format
     * and then answers still counts. Labels must be followed by text on the same line.
     */
    fun parse(raw: String): Pair<String, String>? {
        var top: String? = null
        var result: Pair<String, String>? = null
        raw.lines().forEach { line ->
            LINE.matchEntire(line)?.let { m ->
                val text = m.groupValues[2]
                val valid = isValidLine(text)
                if (m.groupValues[1].equals("top", ignoreCase = true)) {
                    top = if (valid) clean(text) else null
                } else {
                    val t = top
                    if (t != null && valid && !t.equals(clean(text), ignoreCase = true)) result = t to clean(text)
                    top = null
                }
            }
        }
        return result
    }

    private fun clean(line: String) = line
        .replace(Regex("[*_`#\"“”]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Checked on the raw text, so "sh*t" is caught before the asterisk would be stripped. */
    private fun isValidLine(raw: String): Boolean {
        if (Regex("\\w[*_]+\\w").containsMatchIn(raw)) return false // censored swear words
        val line = clean(raw)
        return line.length in 2..maxChars &&
            ALLOWED.matches(line) && // letters and plain punctuation only: no digits, emoji, tokens, URLs
            !LABEL.containsMatchIn(line) &&
            BLOCKLIST.none { it.containsMatchIn(line) }
    }

    private companion object {
        val LINE = Regex("(?i)^[ \t]*(top|bottom)[ \t]*:[ \t]*(\\S.*)$")
        val LABEL = Regex("(?i)^(top|bottom)\\s*:")
        val ALLOWED = Regex("^[\\p{L}\\p{M} '’.,!?:;&…–—-]+$")
        /** A last line of defence for a family-friendly card; the prompt asks for clean humour anyway. */
        val BLOCKLIST = listOf("shit\\w*", "fuck\\w*", "ass(hole|holes|es)?", "damn\\w*", "bitch\\w*", "crap\\w*", "hell(ish)?", "sexy", "piss\\w*")
            .map { Regex("\\b$it\\b", RegexOption.IGNORE_CASE) }
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
