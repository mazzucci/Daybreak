package com.mazzucci.weather.data

import com.mazzucci.weather.TestData.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OpenMeteoParsersTest {

    @Test fun `parses current conditions`() {
        val f = parseForecast(fixture("forecast_sf.json"))
        assertEquals(LocalDateTime.of(2026, 9, 28, 14, 30), f.current.time)
        assertEquals(21.1, f.current.tempC, 0.001)
        assertEquals(20.4, f.current.feelsLikeC, 0.001)
        assertEquals(56, f.current.humidity)
        assertEquals(15.2, f.current.windKmh, 0.001)
        assertEquals(0, f.current.code)
    }

    @Test fun `next hours start at the current hour and span 12 hours across midnight`() {
        val f = parseForecast(fixture("forecast_sf.json"))
        assertEquals(12, f.nextHours.size)
        assertEquals(LocalDateTime.of(2026, 9, 28, 14, 0), f.nextHours.first().time)
        assertEquals(LocalDateTime.of(2026, 9, 29, 1, 0), f.nextHours.last().time)
        assertEquals(21.8, f.nextHours.first().tempC, 0.001)
        assertEquals(13.0, f.nextHours.last().tempC, 0.001)
    }

    @Test fun `keeps every day and hour returned`() {
        val f = parseForecast(fixture("forecast_sf.json"))
        assertEquals(listOf(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 29)), f.days.map { it.date })
        assertEquals(48, f.hours.size)
        assertEquals(26.7, f.days[1].highC, 0.001)
    }

    @Test fun `today is the daily entry matching the current date`() {
        val f = parseForecast(fixture("forecast_sf.json"))
        assertEquals(21.8, f.today.highC, 0.001)
        assertEquals(12.5, f.today.lowC, 0.001)
        assertEquals(0, f.today.precipChance)
        assertEquals(45, f.today.code)
    }

    @Test fun `null precipitation probabilities count as zero`() {
        val json = """
            {"current":{"time":"2026-01-01T00:00","temperature_2m":1,"apparent_temperature":0,
              "relative_humidity_2m":90,"wind_speed_10m":3,"weather_code":3},
             "hourly":{"time":["2026-01-01T00:00"],"temperature_2m":[1],"precipitation_probability":[null],"weather_code":[3]},
             "daily":{"time":["2026-01-01"],"temperature_2m_max":[2],"temperature_2m_min":[-1],
              "precipitation_probability_max":[null],"weather_code":[3]}}
        """.trimIndent()
        val f = parseForecast(json)
        assertEquals(0, f.nextHours.single().precipChance)
        assertEquals(0, f.today.precipChance)
    }

    @Test fun `parses geocoding results`() {
        val places = parseGeocoding(fixture("geocoding_springfield.json"))
        assertEquals(5, places.size)
        val first = places.first()
        assertEquals("geo:4409896", first.id)
        assertEquals("Springfield", first.name)
        assertEquals("Missouri", first.region)
        assertEquals("United States", first.country)
        assertEquals(37.21533, first.latitude, 1e-6)
        assertEquals(-93.29824, first.longitude, 1e-6)
        assertEquals("Missouri, United States", first.detail)
    }

    @Test fun `geocoding with no matches returns empty list`() {
        assertTrue(parseGeocoding(fixture("geocoding_empty.json")).isEmpty())
    }

    @Test fun `geocoding tolerates missing region and country`() {
        val p = parseGeocoding("""{"results":[{"id":1,"name":"Nowhere","latitude":1.5,"longitude":2.5}]}""").single()
        assertNull(p.region)
        assertNull(p.country)
        assertNull(p.detail)
    }
}
