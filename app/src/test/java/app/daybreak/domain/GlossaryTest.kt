package app.daybreak.domain

import app.daybreak.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalDate

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
        assertEquals("Colder than the air (71°F): without sunshine on you (in the shade, under cloud or after dark), even a light breeze feels cool.", e.now)
        val windy = f.copy(current = f.current.copy(windKmh = 30.0))
        assertTrue(explain(Term.FEELS_LIKE, windy, TempUnit.F).now.contains("the 19 mph wind"))
        val muggy = f.copy(current = f.current.copy(tempC = 24.0, feelsLikeC = 28.0, humidity = 80))
        assertTrue(explain(Term.FEELS_LIKE, muggy, TempUnit.C).now.contains("muggy"))
        // Humid but cool: no talk of sweat.
        val coolDamp = f.copy(current = f.current.copy(tempC = 12.0, feelsLikeC = 14.0, humidity = 90))
        assertTrue(!explain(Term.FEELS_LIKE, coolDamp, TempUnit.C).now.contains("sweat"))
        val same = f.copy(current = f.current.copy(feelsLikeC = f.current.tempC))
        assertTrue(explain(Term.FEELS_LIKE, same, TempUnit.C).now.contains("About the same"))
    }

    @Test fun `UV, humidity, wind and rain give advice for the value`() {
        val uv = explain(Term.UV, f, TempUnit.F)
        assertEquals("6", uv.value)
        assertEquals("Today's peak: High", uv.detail)
        assertEquals("Sunscreen, a hat and shade around midday: fair skin can burn in about 20 to 30 minutes.", uv.now)

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
        assertEquals("Chance of rain", rain.title)
        assertEquals("60%", rain.value)
        assertEquals("Highest hourly chance today", rain.detail)
        // At 2:30 PM: the rain from 5 PM on is all still to come.
        assertEquals(
            "Still to come today: about 0.16 inches over 5 hours, mostly this afternoon and evening. Worth having an umbrella nearby.",
            rain.now,
        )
        assertEquals(
            "Still to come today: about 4.1 mm over 5 hours, mostly this afternoon and evening. Worth having an umbrella nearby.",
            explain(Term.RAIN_CHANCE, f, TempUnit.C).now,
        )
    }

    @Test fun `today's rain says how much is still to come, or that the figure is the whole day's`() {
        val alps = TestData.alps()
        fun at(hour: Int, minute: Int = 30) = alps.copy(current = alps.current.copy(time = LocalDateTime.of(2026, 10, 1, hour, minute)))
        assertEquals(
            "Still to come today: about 2.1 mm over 4 hours, mostly this evening. Worth having an umbrella nearby.",
            explain(Term.RAIN_CHANCE, at(19), TempUnit.C).now,
        )
        assertEquals(
            "Today in all: about 4.2 mm over 7 hours, mostly this afternoon and evening. Worth having an umbrella nearby.",
            explain(Term.RAIN_CHANCE, at(23), TempUnit.C).now,
        )
        // A snowy day is a chance of snow.
        val snowDay = alps.copy(current = alps.current.copy(time = LocalDateTime.of(2026, 10, 8, 10, 0)))
        assertEquals("Chance of snow", explain(Term.RAIN_CHANCE, snowDay, TempUnit.C).title)
        // A dry day has no amount at all.
        val dryDay = alps.copy(current = alps.current.copy(time = LocalDateTime.of(2026, 10, 6, 10, 0)))
        assertEquals("Unlikely to need an umbrella.", explain(Term.RAIN_CHANCE, dryDay, TempUnit.C).now)
    }

    @Test fun `a day's rain explains its total, hours and timing`() {
        val alps = TestData.alps()
        val wet = explain(Term.RAIN_DAY, alps, TempUnit.C, LocalDate.of(2026, 10, 2))
        assertEquals("Rain", wet.title)
        assertEquals("12 mm", wet.value)
        assertEquals("Expected total for Friday", wet.detail)
        assertEquals("About 5 hours of rain. Before sunrise, clearing by morning. Worth having an umbrella nearby.", wet.now)
        assertEquals(12.1f / 20, (wet.gauge as Gauge.Scale).fraction, 0.001f)
        assertEquals("0 mm", (wet.gauge as Gauge.Scale).low)
        assertEquals("20 mm", (wet.gauge as Gauge.Scale).high)

        val snow = explain(Term.RAIN_DAY, alps, TempUnit.F, LocalDate.of(2026, 10, 8))
        assertEquals("Snow", snow.title)
        assertEquals("7.6 in", snow.value)
        assertEquals("Expected snowfall for Thursday", snow.detail)
        assertTrue(snow.now, snow.now.startsWith("About 21 hours of snow. "))
        // Snow is measured on a depth gauge: 0 to 8 inches (20 cm), not millimetres of water.
        val gauge = snow.gauge as Gauge.Scale
        assertEquals("0 in", gauge.low)
        assertEquals("8 in", gauge.high)
        assertEquals(7.606f / 8, gauge.fraction, 0.001f)
        val snowC = explain(Term.RAIN_DAY, alps, TempUnit.C, LocalDate.of(2026, 10, 8)).gauge as Gauge.Scale
        assertEquals("0 cm", snowC.low)
        assertEquals("20 cm", snowC.high)

        val dry = explain(Term.RAIN_DAY, alps, TempUnit.C, LocalDate.of(2026, 10, 6))
        assertEquals("0 mm", dry.value)
        assertEquals("No rain expected. Unlikely to need an umbrella.", dry.now)
        assertEquals("0 in", explain(Term.RAIN_DAY, alps, TempUnit.F, LocalDate.of(2026, 10, 6)).value)
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
