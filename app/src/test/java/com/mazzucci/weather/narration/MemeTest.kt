package com.mazzucci.weather.narration

import com.mazzucci.weather.TestData
import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.TempUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MemeTest {
    private fun day(code: Int, highC: Double = 20.0, precip: Int = 0, gust: Double? = null): Forecast {
        val f = TestData.forecast()
        return f.copy(days = f.days.mapIndexed { i, d ->
            if (i == 0) d.copy(code = code, highC = highC, precipChance = precip, gustMaxKmh = gust) else d
        })
    }

    private fun input(f: Forecast = TestData.forecast()) = NarrationInput("San Francisco", f, TempUnit.F)

    @Test fun `mood picks the most dramatic part of the day`() {
        assertEquals(MemeMood.STORM, memeMoodOf(day(95, precip = 90)))
        assertEquals(MemeMood.SNOW, memeMoodOf(day(73, highC = -2.0)))
        assertEquals(MemeMood.RAIN, memeMoodOf(day(61)))
        assertEquals(MemeMood.RAIN, memeMoodOf(day(2, precip = 70)))
        assertEquals(MemeMood.HEAT, memeMoodOf(day(0, highC = 34.0)))
        assertEquals(MemeMood.COLD, memeMoodOf(day(3, highC = 1.0)))
        assertEquals(MemeMood.WIND, memeMoodOf(day(2, gust = 65.0)))
        assertEquals(MemeMood.FOG, memeMoodOf(day(45)))
        assertEquals(MemeMood.GLOOM, memeMoodOf(day(3)))
        assertEquals(MemeMood.SUN, memeMoodOf(day(0)))
        assertEquals(MemeMood.MIXED, memeMoodOf(day(2)))
    }

    @Test fun `every mood has captions and none contain numbers`() {
        MemeMood.entries.forEach { mood ->
            val pool = TemplateMemes.CAPTIONS.getValue(mood)
            assertTrue(pool.isNotEmpty())
            pool.forEach { (top, bottom) ->
                assertTrue("$top / $bottom", (top + bottom).none { it.isDigit() })
                assertTrue(MemeValidator().parse("TOP: $top\nBOTTOM: $bottom") != null)
            }
        }
    }

    @Test fun `template meme is stable for a place and day but varies between places`() {
        val date = TestData.now.toLocalDate()
        val a = TemplateMemes.pick(MemeMood.RAIN, memeSeed("geo:1", date))
        assertEquals(a, TemplateMemes.pick(MemeMood.RAIN, memeSeed("geo:1", date)))
        val others = (2..20).map { TemplateMemes.pick(MemeMood.RAIN, memeSeed("geo:$it", date)) }
        assertTrue(others.any { it != a })
        assertEquals(NarrationSource.TEMPLATE, a.source)
    }

    @Test fun `prompt describes the day in words only`() {
        val prompt = MemePrompt.build(input(day(0, highC = 34.0, gust = 45.0)), MemeMood.HEAT)
        assertTrue("San Francisco" in prompt)
        assertTrue("hot" in prompt && "windy" in prompt && "heat" in prompt)
        assertFalse(prompt.any { it.isDigit() })
    }

    @Test fun `validator accepts the requested format with a little noise`() {
        val v = MemeValidator()
        assertEquals("Me: I'll walk" to "The sky: no", v.parse("Sure!\nTOP: \"Me: I'll walk\"\nBOTTOM: **The sky: no**"))
        assertEquals("Hot" to "Hotter", v.parse("top: Hot\nbottom: Hotter"))
    }

    @Test fun `validator rejects numbers, missing lines, long lines, tokens and rude words`() {
        val v = MemeValidator()
        assertNull(v.parse("TOP: It's 74 degrees\nBOTTOM: Nice"))
        assertNull(v.parse("TOP: Only a top"))
        assertNull(v.parse("A meme about rain"))
        assertNull(v.parse("TOP: ${"very ".repeat(12)}long\nBOTTOM: yes"))
        assertNull(v.parse("TOP: <start_of_turn>\nBOTTOM: model"))
        assertNull(v.parse("TOP: What the hell\nBOTTOM: rain"))
        assertNull(v.parse("TOP: Same\nBOTTOM: same"))
        // Word boundaries: "hello" and "class" are fine.
        assertEquals("Hello rain" to "Class cancelled", v.parse("TOP: Hello rain\nBOTTOM: Class cancelled"))
    }

    @Test fun `validator closes the format holes`() {
        val v = MemeValidator()
        // An empty TOP must not swallow the BOTTOM label.
        assertNull(v.parse("TOP:\nBOTTOM: Rain again"))
        assertNull(v.parse("TOP: BOTTOM: Rain\nBOTTOM: again"))
        // An echo of the prompt's format, then a real answer: the answer wins.
        assertEquals("Wet socks" to "Again", v.parse("TOP: <top line>\nBOTTOM: <bottom line>\nTOP: Wet socks\nBOTTOM: Again"))
        // Emoji and symbols are out; plain punctuation is fine.
        assertNull(v.parse("TOP: Sunny ☀️\nBOTTOM: Nice"))
        assertEquals("Wait…" to "It's raining — again!", v.parse("TOP: Wait…\nBOTTOM: It's raining — again!"))
    }

    @Test fun `blocklist catches variants but not ordinary idioms`() {
        val v = MemeValidator()
        listOf("Shitty weather", "Total asshole wind", "Hellish heat", "Damned rain", "Sh*t it rains").forEach {
            assertNull(it, v.parse("TOP: $it\nBOTTOM: Yes"))
        }
        listOf("Dead heat", "Die-hard fans", "The wind will die down", "Assume rain").forEach {
            assertEquals(it, it to "Yes", v.parse("TOP: $it\nBOTTOM: Yes"))
        }
    }

    @Test fun `writer uses the model's caption when it passes`() = runTest {
        var seen: Int? = null
        val writer = MemeWriter({ _, t, seed -> seen = seed; assertTrue(t > 0.5f); "TOP: Fog rolls in\nBOTTOM: Bridge has left" })
        val meme = writer.fromModel(input(day(45)), "geo:1")!!
        assertEquals(Meme("Fog rolls in", "Bridge has left", MemeMood.FOG, NarrationSource.GEMMA), meme)
        assertEquals(memeSeed("geo:1", TestData.now.toLocalDate()), seen)
    }

    @Test fun `writer returns null on a bad caption or a failure`() = runTest {
        assertNull(MemeWriter({ _, _, _ -> "TOP: 42\nBOTTOM: x" }).fromModel(input(), "k"))
        assertNull(MemeWriter({ _, _, _ -> throw IOException("timeout") }).fromModel(input(), "k"))
        assertNull(MemeWriter().fromModel(input(), "k"))
        assertFalse(MemeWriter().canUseModel)
        assertNotEquals(null, MemeWriter().template(input(), "k"))
    }
}
