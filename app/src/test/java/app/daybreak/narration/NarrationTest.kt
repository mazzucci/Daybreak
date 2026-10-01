package app.daybreak.narration

import app.daybreak.TestData
import app.daybreak.domain.TempUnit
import app.daybreak.domain.ClockFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val inputF = NarrationInput("San Francisco", TestData.forecast(), TempUnit.F)
private val inputC = inputF.copy(unit = TempUnit.C)

class TemplateNarratorTest {
    private val narrator = TemplateNarrator()

    @Test fun `describes now, high and low, and when rain is possible, with how much is still to come`() {
        // The 60% stamped 6 PM is the chance of rain from 5 to 6 PM; the rain still to come today is 4.1 mm.
        assertEquals(
            "71° and partly cloudy now, with a high of 74° and a low of 56°. Rain is possible around 5 PM (60% chance, about 0.16 inches still to come today).",
            narrator.describe(inputF),
        )
    }

    @Test fun `the widget's version leaves the amount out`() {
        assertTrue(narrator.describe(inputC, withTotal = false).endsWith("Rain is possible around 5 PM (60% chance)."))
    }

    @Test fun `uses the primary unit`() {
        assertEquals(
            "21° and partly cloudy now, with a high of 24° and a low of 13°. Rain is possible around 5 PM (60% chance, about 4.1 mm still to come today).",
            narrator.describe(inputC),
        )
    }

    @Test fun `only counts the rain still to come today`() {
        // Later in the evening the first two wet hours are gone: 1.7 mm is left.
        val base = TestData.forecast()
        val late = base.copy(current = base.current.copy(time = base.current.time.withHour(19).withMinute(10)))
        assertTrue(narrator.describe(inputC.copy(forecast = late)), narrator.describe(inputC.copy(forecast = late)).endsWith("(40% chance, about 1.7 mm still to come today)."))
    }

    @Test fun `says likely from 70 percent, and leaves out an amount under a millimetre`() {
        val base = TestData.forecast()
        val f = base.copy(
            hours = base.hours.map { it.copy(precipChance = if (it.precipChance == 60) 80 else it.precipChance, precipMm = it.precipMm?.div(10)) },
        )
        assertTrue(narrator.describe(inputC.copy(forecast = f)).endsWith("Rain is likely around 5 PM (80% chance)."))
    }

    @Test fun `a snowy day says snow`() {
        val base = TestData.forecast(tempC = -2.0)
        val f = base.copy(
            hours = base.hours.map { if (it.precipChance >= 40) it.copy(precipChance = 80, snowCm = 1.0, precipMm = 1.4) else it },
            days = listOf(base.today.copy(precipSumMm = 6.0, snowSumCm = 4.2)) + base.days.drop(1),
        )
        assertTrue(narrator.describe(inputC.copy(forecast = f)).endsWith("Snow is likely around 5 PM (80% chance, about 6 cm still to come today)."))
    }

    @Test fun `a low chance with a real amount is a small chance`() {
        val f = TestData.forecast(rainAt = null).let { it.copy(days = listOf(it.today.copy(precipChance = 15, precipSumMm = 2.4)) + it.days.drop(1)) }
        assertTrue(narrator.describe(inputF.copy(forecast = f)).endsWith("There's a small chance of rain today."))
    }

    @Test fun `a small daily chance under 20 percent isn't mentioned`() {
        val f = TestData.forecast(rainAt = null).let { it.copy(days = listOf(it.today.copy(precipChance = 15)) + it.days.drop(1)) }
        assertTrue(narrator.describe(inputF.copy(forecast = f)).endsWith("No rain expected."))
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
            val text = TemplateNarrator().describe(inputF)
            assertTrue(text, text.endsWith("Rain is possible around 17:00 (60% chance, about 0.16 inches still to come today)."))
        } finally {
            ClockFormat.use24Hour = false
        }
    }
}
