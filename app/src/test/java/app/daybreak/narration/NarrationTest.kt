package app.daybreak.narration

import app.daybreak.TestData
import app.daybreak.domain.TempUnit
import app.daybreak.domain.ClockFormat
import org.junit.Assert.assertEquals
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

    @Test fun `says rain falling now will ease when no hour ahead is likely`() {
        val f = TestData.forecast(rainAt = null).let { it.copy(current = it.current.copy(code = 63)) }
        assertEquals(
            "71° and rain now, with a high of 74° and a low of 56°. It should ease off soon.",
            narrator.describe(inputF.copy(forecast = f)),
        )
    }
}

class ClockTest {
    @Test fun `the template follows the 24-hour clock`() {
        ClockFormat.use24Hour = true
        try {
            val text = TemplateNarrator(Locale.US).describe(inputF)
            assertTrue(text, text.endsWith("Rain is likely around 18:00 (60% chance)."))
        } finally {
            ClockFormat.use24Hour = false
        }
    }
}
