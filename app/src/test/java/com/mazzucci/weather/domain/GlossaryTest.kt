package com.mazzucci.weather.domain

import com.mazzucci.weather.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossaryTest {
    private val f = TestData.forecast() // 71°F, feels 68°F, humidity 58%, wind 9 mph, UV 6.2, sun 7:02–18:56, rain 60%
    private val night = TestData.rainyNight() // 10:30 PM, 27 km/h, rain 90%

    @Test fun `every term explains itself with today's values`() {
        Term.entries.forEach { term ->
            val e = explain(term, f, TempUnit.F)
            assertTrue(term.name, e.title.isNotBlank() && e.value.isNotBlank() && e.now.isNotBlank() && e.meaning.isNotBlank())
        }
    }

    @Test fun `feels like says why it differs`() {
        val e = explain(Term.FEELS_LIKE, f, TempUnit.F)
        assertEquals("68°F", e.value)
        assertEquals("20°C", e.detail)
        assertEquals("68°F (20°C)", e.spoken)
        assertEquals("Colder than the air (71°F): a breeze or dry air takes heat away from your skin.", e.now)
        val windy = f.copy(current = f.current.copy(windKmh = 30.0))
        assertTrue(explain(Term.FEELS_LIKE, windy, TempUnit.F).now.contains("the 19 mph wind"))
        val muggy = f.copy(current = f.current.copy(feelsLikeC = 26.0, humidity = 80))
        assertTrue(explain(Term.FEELS_LIKE, muggy, TempUnit.C).now.contains("80% humidity"))
        val same = f.copy(current = f.current.copy(feelsLikeC = f.current.tempC))
        assertTrue(explain(Term.FEELS_LIKE, same, TempUnit.C).now.contains("About the same"))
    }

    @Test fun `UV, humidity, wind and rain give advice for the value`() {
        val uv = explain(Term.UV, f, TempUnit.F)
        assertEquals("6", uv.value)
        assertEquals("Today's peak: High", uv.detail)
        assertEquals("Sunscreen, a hat and shade around midday: unprotected skin can burn in about 20 to 30 minutes.", uv.now)

        val humidity = explain(Term.HUMIDITY, f, TempUnit.F)
        assertEquals("58%", humidity.value)
        assertEquals("Comfortable", humidity.detail)
        assertEquals("Neither dry nor sticky.", humidity.now)

        val wind = explain(Term.WIND, f, TempUnit.F)
        assertEquals("9 mph", wind.value)
        assertEquals("Gusts to 15 mph", wind.detail)
        assertTrue(wind.now.startsWith("A gentle to moderate breeze"))
        assertNull(explain(Term.WIND, night, TempUnit.C).detail) // no gust data

        val rain = explain(Term.RAIN_CHANCE, f, TempUnit.F)
        assertEquals("60%", rain.value)
        assertEquals("Highest hourly chance today", rain.detail)
        assertEquals("About 0.26 inches expected in total. Worth having an umbrella nearby.", rain.now)
        assertEquals("About 6.5 mm expected in total. Worth having an umbrella nearby.", explain(Term.RAIN_CHANCE, f, TempUnit.C).now)
    }

    @Test fun `gauges put the value on their scale`() {
        val uv = explain(Term.UV, f, TempUnit.F).gauge as Gauge.Scale
        assertEquals(6.2f / 11, uv.fraction, 0.001f)
        assertEquals(Gauge.Scale.Kind.INTENSITY, uv.kind)
        val humidity = explain(Term.HUMIDITY, f, TempUnit.F).gauge as Gauge.Scale
        assertEquals(0.58f, humidity.fraction, 0.001f)
        assertEquals(Gauge.Scale.Kind.MOISTURE, humidity.kind)
        // A storm well past gale force pins the marker at the end rather than running off it.
        val storm = explain(Term.WIND, f.copy(current = f.current.copy(windKmh = 120.0)), TempUnit.C).gauge as Gauge.Scale
        assertEquals(1f, storm.fraction, 0f)

        val sun = explain(Term.SUN, f, TempUnit.F).gauge as Gauge.Sun
        assertEquals(448f / 714, sun.progress!!, 0.001f) // 2:30 PM is 448 of 714 daylight minutes in
        assertNull((explain(Term.SUN, night, TempUnit.C).gauge as Gauge.Sun).progress)
        assertNull(explain(Term.FEELS_LIKE, f, TempUnit.F).gauge)
    }

    @Test fun `daylight length, the next sun event and polar days`() {
        val sun = explain(Term.SUN, f, TempUnit.F)
        assertEquals("11h 54m", sun.value)
        assertEquals("Daylight today", sun.detail)
        assertEquals("11 hours 54 minutes of daylight today", sun.spoken)
        assertEquals("Sunset in 4 hours 26 minutes.", sun.now)
        assertEquals("Sunrise in 8 hours 32 minutes.", explain(Term.SUN, night, TempUnit.C).now)
        val shortening = f.copy(days = f.days.mapIndexed { i, d -> if (i == 1) d.copy(sunset = d.date.atTime(18, 54)) else d })
        assertEquals("Sunset in 4 hours 26 minutes. Tomorrow gets 2 minutes less.", explain(Term.SUN, shortening, TempUnit.F).now)

        val polar = f.copy(days = f.days.map { it.copy(sunrise = it.date.atStartOfDay(), sunset = it.date.atStartOfDay()) })
        val e = explain(Term.DAYLIGHT, polar, TempUnit.F)
        assertEquals("Daylight", e.title)
        assertEquals("None", e.value)
        assertEquals("Polar night", e.detail)
        assertNull(e.gauge)
    }
}
