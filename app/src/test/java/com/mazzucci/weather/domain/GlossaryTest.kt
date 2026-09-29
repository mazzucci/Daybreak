package com.mazzucci.weather.domain

import com.mazzucci.weather.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossaryTest {
    private val f = TestData.forecast() // 71°F, feels 68°F, humidity 58%, wind 9 mph, UV 6.2, sun 7:02–18:56, rain 60%

    @Test fun `every term explains itself with today's values`() {
        Term.entries.forEach { term ->
            val e = explain(term, f, TempUnit.F)
            assertTrue(term.name, e.title.isNotBlank() && e.meaning.isNotBlank() && e.now.isNotBlank())
        }
    }

    @Test fun `feels like says why it differs`() {
        assertEquals(
            "Now 68°F. Colder than the air (71°F): a breeze or dry air takes heat away from your skin.",
            explain(Term.FEELS_LIKE, f, TempUnit.F).now,
        )
        val windy = f.copy(current = f.current.copy(windKmh = 30.0))
        assertTrue(explain(Term.FEELS_LIKE, windy, TempUnit.F).now.contains("the 19 mph wind"))
        val muggy = f.copy(current = f.current.copy(feelsLikeC = 26.0, humidity = 80))
        assertTrue(explain(Term.FEELS_LIKE, muggy, TempUnit.C).now.contains("80% humidity"))
        val same = f.copy(current = f.current.copy(feelsLikeC = f.current.tempC))
        assertTrue(explain(Term.FEELS_LIKE, same, TempUnit.C).now.contains("About the same"))
    }

    @Test fun `UV, humidity, wind and rain give advice for the value`() {
        assertEquals("Today's peak: 6 (High). High: sunscreen, a hat and shade around midday; skin can burn in about 20 to 30 minutes.", explain(Term.UV, f, TempUnit.F).now)
        assertEquals("Now 58%. Comfortable.", explain(Term.HUMIDITY, f, TempUnit.F).now)
        assertTrue(explain(Term.WIND, f, TempUnit.F).now.startsWith("Now 9 mph, gusting to 15 mph."))
        assertEquals("Today's highest hourly chance: 60%. About 6.5 mm expected in total. Worth having an umbrella nearby.", explain(Term.RAIN_CHANCE, f, TempUnit.F).now)
    }

    @Test fun `daylight length and polar days`() {
        assertEquals("Sunrise 7:02 AM, sunset 6:56 PM: 11 hours 54 minutes of daylight.", explain(Term.SUN, f, TempUnit.F).now)
        val shortening = f.copy(days = f.days.mapIndexed { i, d -> if (i == 1) d.copy(sunset = d.date.atTime(18, 54)) else d })
        assertTrue(explain(Term.SUN, shortening, TempUnit.F).now.endsWith("Tomorrow gets 2 minutes less."))
        val polar = f.copy(days = f.days.map { it.copy(sunrise = it.date.atStartOfDay(), sunset = it.date.atStartOfDay()) })
        assertTrue(explain(Term.DAYLIGHT, polar, TempUnit.F).now.contains("polar night"))
    }
}
