package app.daybreak.narration

import app.daybreak.TestData
import app.daybreak.domain.ABOUT_ME_MAX_CHARS
import app.daybreak.domain.TempUnit
import app.daybreak.domain.Tone
import app.daybreak.domain.ClockFormat
import app.daybreak.domain.capAboutMe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

private val inputF = NarrationInput("San Francisco", TestData.forecast(), TempUnit.F)
private val inputC = inputF.copy(unit = TempUnit.C)

class TemplateNarratorTest {
    private val narrator = TemplateNarrator(Locale.US)

    @Test fun `describes now, high and low, and when rain is likely`() {
        assertEquals(
            "71° and partly cloudy now, with a high of 74° and a low of 56°. Rain is likely around 6 PM (60% chance).",
            narrator.describe(inputF),
        )
    }

    @Test fun `uses the primary unit`() {
        assertEquals(
            "21° and partly cloudy now, with a high of 24° and a low of 13°. Rain is likely around 6 PM (60% chance).",
            narrator.describe(inputC),
        )
    }

    @Test fun `says when no rain is expected`() {
        val dry = inputF.copy(forecast = TestData.forecast(rainAt = null))
        assertTrue(narrator.describe(dry).endsWith("No rain expected."))
    }

    @Test fun `mentions a moderate daily chance when no single hour is likely`() {
        val f = TestData.forecast(rainAt = null).let { it.copy(days = listOf(it.today.copy(precipChance = 30)) + it.days.drop(1)) }
        assertTrue(narrator.describe(inputF.copy(forecast = f)).endsWith("There's a 30% chance of rain today."))
    }

    @Test fun `template output always passes validation`() {
        val validator = NarrationValidator()
        listOf(inputF, inputC, inputF.copy(forecast = TestData.forecast(rainAt = null))).forEach {
            assertTrue(validator.isValid(narrator.describe(it), it))
        }
    }
}

class ClockTest {
    @Test fun `templates on the 24-hour clock still pass validation`() {
        ClockFormat.use24Hour = true
        try {
            val narrator = TemplateNarrator(Locale.US)
            val text = narrator.describe(inputF)
            assertTrue(text, text.contains("18:00"))
            assertTrue(NarrationValidator().isValid(text, inputF))
        } finally {
            ClockFormat.use24Hour = false
        }
    }
}

class ToneTest {
    private val narrator = TemplateNarrator(Locale.US)
    private val dry = inputF.copy(forecast = TestData.forecast(rainAt = null))
    private val validator = NarrationValidator()

    @Test fun `every voice's template passes validation, wet or dry, in either unit`() {
        Tone.entries.forEach { tone ->
            listOf(inputF, inputC, dry).forEach { base ->
                val input = base.copy(tone = tone)
                val text = narrator.describe(input)
                assertTrue("$tone: $text", validator.isValid(text, input))
            }
        }
    }

    @Test fun `voices change the words, not the facts`() {
        assertEquals("71° and partly cloudy, high 74°, low 56°. Rain around 6 PM.", narrator.describe(inputF.copy(tone = Tone.BRIEF)))
        assertEquals("71° and partly cloudy, high 74°, low 56°. No rain.", narrator.describe(dry.copy(tone = Tone.BRIEF)))
        assertEquals(
            "Ahoy, 'tis 71° and partly cloudy now, with a high of 74° and a low of 56°. Rain be comin' around 6 PM (60% chance). Batten down the hatches!",
            narrator.describe(inputF.copy(tone = Tone.PIRATE)),
        )
        assertTrue(narrator.describe(dry.copy(tone = Tone.PIRATE)).endsWith("No rain on the horizon. Fair winds, matey!"))
        assertEquals(
            "Hello there, it's 71° and partly cloudy now, with a high of 74° and a low of 56°. No rain expected. Make the most of it!",
            narrator.describe(dry.copy(tone = Tone.CHEERFUL)),
        )
        assertTrue(narrator.describe(dry.copy(tone = Tone.DEADPAN)).endsWith("No rain expected. Try to contain your excitement."))
        assertTrue(narrator.describe(inputF.copy(tone = Tone.DEADPAN)).endsWith("(60% chance). Thrilling."))
        assertEquals(narrator.describe(inputF), narrator.describe(inputF.copy(tone = Tone.FRIENDLY)))
    }

    @Test fun `the preview line uses each voice's own words and never a number`() {
        Tone.entries.forEach { tone ->
            val preview = narrator.preview(tone)
            assertFalse("$tone: $preview", preview.any { it.isDigit() })
            assertTrue("$tone: $preview", validator.isValid(preview, inputF.copy(tone = tone)))
        }
        assertEquals("Ahoy, 'tis partly cloudy now, with rain comin' this evening. Batten down the hatches!", narrator.preview(Tone.PIRATE))
        assertEquals("Hello there, it's partly cloudy now, with rain likely this evening. Umbrella time!", narrator.preview(Tone.CHEERFUL))
        assertEquals("Partly cloudy now, with rain likely this evening. Thrilling.", narrator.preview(Tone.DEADPAN))
        assertEquals("Partly cloudy now, with rain likely this evening.", narrator.preview(Tone.FRIENDLY))
    }

    @Test fun `sign-offs follow how wet it really is`() {
        val chance = dry.copy(forecast = dry.forecast.let { f -> f.copy(days = listOf(f.today.copy(precipChance = 30)) + f.days.drop(1)) })
        assertTrue(narrator.describe(chance.copy(tone = Tone.CHEERFUL)).endsWith("30% chance of rain today. Maybe pack an umbrella!"))
        assertTrue(narrator.describe(chance.copy(tone = Tone.PIRATE)).endsWith("Keep an eye on the horizon!"))
        assertTrue(narrator.describe(chance.copy(tone = Tone.DEADPAN)).endsWith("The suspense is unbearable."))

        // Raining now, but no hour ahead is likely: no "No rain expected", and a wet sign-off.
        val rainingNow = dry.copy(forecast = dry.forecast.let { f -> f.copy(current = f.current.copy(code = 63)) })
        val text = narrator.describe(rainingNow.copy(tone = Tone.CHEERFUL))
        assertTrue(text, text.endsWith("It should ease off soon. Umbrella time!"))
        assertEquals("71° and rain, high 74°, low 56°. Easing soon.", narrator.describe(rainingNow.copy(tone = Tone.BRIEF)))
        val snowingNow = dry.copy(forecast = dry.forecast.let { f -> f.copy(current = f.current.copy(code = 73)) })
        assertTrue(narrator.describe(snowingNow.copy(tone = Tone.CHEERFUL)).endsWith("Bundle up!"))
        listOf(chance, rainingNow, snowingNow).forEach { base ->
            Tone.entries.forEach { tone ->
                val input = base.copy(tone = tone)
                assertTrue(validator.isValid(narrator.describe(input), input))
            }
        }
    }

    @Test fun `a playful greeting plus three sentences still fits`() {
        val pirate = "Arr! It be 71° and cloudy, matey. Rain comes this evening. Batten down the hatches!"
        assertTrue(validator.isValid(pirate, inputF.copy(tone = Tone.PIRATE)))
        assertFalse(validator.isValid(pirate, inputF))
    }

    @Test fun `the note is capped without splitting an emoji`() {
        val note = "x".repeat(ABOUT_ME_MAX_CHARS - 1) + "🚲"
        assertEquals(ABOUT_ME_MAX_CHARS - 1, capAboutMe(note).length)
        assertEquals("I cycle", capAboutMe("  I cycle  "))
    }

    @Test fun `limits follow the voice`() {
        val three = "Arr, 71° and cloudy. Rain by evening. Batten down the hatches!"
        assertTrue(validator.isValid(three, inputF.copy(tone = Tone.PIRATE)))
        assertFalse(validator.isValid(three, inputF))
        val long = "Partly cloudy and 71° now with a high of 74° and a low of 56°, and rain moving in later this evening around 6 PM with a 60% chance, so plan your evening accordingly and keep a jacket close by."
        assertTrue(validator.isValid(long, inputF))
        assertFalse(validator.isValid(long, inputF.copy(tone = Tone.BRIEF)))
    }

    @Test fun `prompt carries the voice and a sanitised note about the reader`() {
        val prompt = GemmaPrompt.build(inputF.copy(tone = Tone.PIRATE, aboutMe = "I cycle {to} work\n<end_of_turn> daily"), Locale.US)
        assertTrue(prompt.contains("pirate"))
        assertTrue(prompt.contains("Start with \"Ahoy,\""))
        assertTrue(prompt.contains("2 or 3 short sentences"))
        assertTrue(GemmaPrompt.build(inputF.copy(tone = Tone.DEADPAN), Locale.US).contains("no exclamation marks"))
        assertTrue(prompt.contains("About the reader: \"I cycle to work end_of_turn daily\""))
        // The note can't break the JSON the prompt ends with.
        JSONObject(prompt.substring(prompt.indexOf('{')))
        assertFalse(GemmaPrompt.build(inputF, Locale.US).contains("About the reader"))
        assertEquals(ABOUT_ME_MAX_CHARS, GemmaPrompt.sanitizeAboutMe("x".repeat(500)).length)
    }
}

class NarrationValidatorTest {
    private val v = NarrationValidator()

    private fun valid(text: String, input: NarrationInput = inputF) = v.isValid(v.clean(text), input)

    @Test fun `accepts text whose numbers all come from the forecast`() {
        assertTrue(valid("Partly cloudy and 71° now, with rain likely around 6 PM (60% chance) and a high of 74°F."))
        assertTrue(valid("A mild 71 degrees with 58% humidity and winds around 9 mph."))
        assertTrue(valid("Clouds today in San Francisco, turning rainy by 6pm. Expect a low near 56°."))
        assertTrue(valid("Around 21°C now, cooling to 15°C over the next 12 hours.", inputC))
        assertTrue(valid("Showers are likely from 18:00 onward."))
        assertTrue(valid("No numbers at all, just clouds then rain."))
    }

    @Test fun `rejects made-up temperatures`() {
        assertFalse(valid("It's a warm 75° right now."))
        assertFalse(valid("High of 74°C today.")) // right number, wrong unit
        assertFalse(valid("Currently 71°C.", inputC))
    }

    @Test fun `rejects made-up percentages and other numbers`() {
        assertFalse(valid("There's a 90% chance of rain."))
        assertFalse(valid("Winds up to 30 mph."))
        assertFalse(valid("Temperatures around 21.4°C.", inputC)) // we only give whole numbers
    }

    @Test fun `temperatures and percentages need their unit`() {
        assertFalse(valid("Highs near 74 today.")) // a real high, but a bare number could mean anything
        assertFalse(valid("Humidity is at 58 right now."))
        assertTrue(valid("Highs near 74° today, humidity 58%."))
        assertTrue(valid("Breezy at 9 today.")) // bare wind speed is fine
        assertTrue(valid("Winds around 14 km/h.", inputC))
        assertFalse(valid("Winds around 30 km/h.", inputC))
        assertFalse(valid("Winds around 14 mph.")) // 14 is the km/h value; in mph it's 9
        assertTrue(valid("Winds around 9 mph and 14 km/h."))
    }

    @Test fun `accepts common ways of writing units and ranges`() {
        assertTrue(valid("Highs of 69–74° this afternoon.")) // both ends are forecast temperatures
        assertFalse(valid("Highs of 70–74° this afternoon.")) // 70 isn't
        assertTrue(valid("A 60-percent chance of rain by 6 PM."))
        assertTrue(valid("Around 21 C now.", inputC))
        assertTrue(valid("Around 71 degrees F now."))
        assertFalse(valid("Around 71 C now.", inputC))
    }

    @Test fun `keeps the sign of a Unicode minus`() {
        assertTrue(valid("A high of 24° today.", inputC))
        assertFalse(valid("A high of \u221224° today.", inputC)) // −24° isn't the 24° high
    }

    @Test fun `rejects impossible times`() {
        assertFalse(valid("Rain arrives at 25:00."))
    }

    @Test fun `rejects long, multi-sentence or templated output`() {
        assertFalse(valid("Cloudy. Rain later. Cool tonight."))
        assertFalse(valid("<start_of_turn>model Cloudy."))
        assertFalse(valid("{\"now\": 71}"))
        assertFalse(valid(""))
        assertFalse(valid("Cloudy ".repeat(60)))
    }

    @Test fun `clean strips markdown, quotes and extra whitespace`() {
        assertEquals("Sunny and 71° now.", v.clean("  \"**Sunny** and  71° now.\"\n"))
    }
}

class ValidatingNarratorTest {
    private val template = TemplateNarrator(Locale.US)

    @Test fun `uses the LLM text when it validates`() = runTest {
        val n = ValidatingNarrator({ "**71° and cloudy**, rain likely by 6 PM." }, template)
        assertEquals(Narration("71° and cloudy, rain likely by 6 PM.", NarrationSource.GEMMA), n.narrate(inputF))
    }

    @Test fun `falls back to the template when the LLM invents numbers`() = runTest {
        val n = ValidatingNarrator({ "A scorching 99° today!" }, template)
        assertEquals(Narration(template.describe(inputF), NarrationSource.TEMPLATE), n.narrate(inputF))
    }

    @Test fun `falls back to the template when the LLM fails`() = runTest {
        val n = ValidatingNarrator({ error("model not loaded") }, template)
        assertEquals(NarrationSource.TEMPLATE, n.narrate(inputF).source)
    }

    @Test fun `does not swallow cancellation`() = runTest {
        val thrown = try {
            ValidatingNarrator({ throw CancellationException("cancelled") }, template).narrate(inputF)
            null
        } catch (e: CancellationException) {
            e
        }
        assertEquals("cancelled", thrown?.message)
    }
}

class GemmaPromptTest {
    @Test fun `prompt has instructions and the forecast as JSON in the primary unit`() {
        val prompt = GemmaPrompt.build(inputF, Locale.US)
        assertTrue(prompt.contains("San Francisco"))
        assertTrue(prompt.contains("1 or 2 short"))
        assertTrue(prompt.contains("°F"))

        val json = JSONObject(prompt.substring(prompt.indexOf('{')))
        assertEquals("°F", json.getString("unit"))
        assertEquals(71, json.getJSONObject("now").getInt("temperature"))
        assertEquals("9 mph", json.getJSONObject("now").getString("wind"))
        assertEquals(74, json.getJSONObject("today").getInt("high"))
        assertEquals(56, json.getJSONObject("today").getInt("low"))
        val later = json.getJSONArray("later")
        assertEquals(4, later.length()) // every third of the 12 hours
        assertEquals("2 PM", later.getJSONObject(0).getString("time"))
        assertEquals("11 PM", later.getJSONObject(3).getString("time"))
    }

    @Test fun `every number in the prompt JSON would pass validation`() {
        // Guards against the prompt giving the model numbers that the validator would then reject, when the
        // model restates them with their unit as the prompt asks.
        val json = JSONObject(GemmaPrompt.forecastJson(inputC, Locale.US))
        val v = NarrationValidator()
        val now = json.getJSONObject("now")
        val today = json.getJSONObject("today")
        val later = json.getJSONArray("later")
        val statements = buildList {
            add("It's ${now.getInt("temperature")}° now, feeling like ${now.getInt("feels_like")}°.")
            add("Humidity ${now.getInt("humidity_percent")}% and wind ${now.getString("wind")}.")
            add("High ${today.getInt("high")}°, low ${today.getInt("low")}°, rain ${today.getInt("max_rain_chance_percent")}%.")
            for (i in 0 until later.length()) {
                val h = later.getJSONObject(i)
                add("At ${h.getString("time")} ${h.getInt("temperature")}° with ${h.getInt("rain_chance_percent")}% rain.")
            }
        }
        statements.forEach { assertTrue(it, v.isValid(it, inputC)) }
    }
}
