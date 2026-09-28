package com.mazzucci.weather.data

import com.mazzucci.weather.TestData.fixture
import com.mazzucci.weather.domain.Daylight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test fun `an empty daily forecast is a friendly error`() {
        val json = fixture("forecast_sf.json").let { raw ->
            val root = org.json.JSONObject(raw)
            val daily = root.getJSONObject("daily")
            daily.keys().asSequence().toList().forEach { daily.put(it, org.json.JSONArray()) }
            root.toString()
        }
        val e = assertThrows(java.io.IOException::class.java) { parseForecast(json) }
        assertEquals("Weather service returned no daily forecast", e.message)
    }

    @Test fun `parses a full week with wind, sun times and UV`() {
        val f = parseForecast(fixture("forecast_sf_week.json"))
        assertEquals(8, f.days.size)
        assertEquals(192, f.hours.size)
        assertEquals(LocalDate.of(2026, 9, 28), f.today.date)
        assertEquals(7, f.upcomingDays().size)
        assertEquals(LocalDate.of(2026, 10, 4), f.upcomingDays().last().date)

        val today = f.today
        assertEquals(LocalDateTime.of(2026, 9, 28, 7, 2), today.sunrise)
        assertEquals(LocalDateTime.of(2026, 9, 28, 18, 56), today.sunset)
        assertEquals(25.0, today.windMaxKmh!!, 0.001)
        assertEquals(36.7, today.gustMaxKmh!!, 0.001)
        assertEquals(0.0, today.precipSumMm!!, 0.001)
        assertEquals(6.05, today.uvIndexMax!!, 0.001)

        assertEquals(true, f.current.isDay)
        val now = f.nextHours.first()
        assertEquals(LocalDateTime.of(2026, 9, 28, 16, 0), now.time)
        assertEquals(21.9, now.windKmh!!, 0.001)
        assertEquals(31.7, now.gustKmh!!, 0.001)
        assertEquals(true, now.isDay)
        assertEquals(false, f.nextHours.last().isDay)
    }

    @Test fun `older responses without the detail fields still parse`() {
        val f = parseForecast(fixture("forecast_sf.json"))
        assertNull(f.today.sunrise)
        assertNull(f.today.uvIndexMax)
        assertNull(f.nextHours.first().gustKmh)
        assertNull(f.nextHours.first().isDay)
    }

    @Test fun `null detail values become null, not zero`() {
        val json = """
            {"current":{"time":"2026-06-21T12:00","temperature_2m":1,"apparent_temperature":0,
              "relative_humidity_2m":90,"wind_speed_10m":3,"weather_code":3,"is_day":null},
             "hourly":{"time":["2026-06-21T12:00"],"temperature_2m":[1],"precipitation_probability":[0],"weather_code":[3],
              "wind_speed_10m":[null],"wind_gusts_10m":[null],"is_day":[null]},
             "daily":{"time":["2026-06-21"],"temperature_2m_max":[2],"temperature_2m_min":[-1],
              "precipitation_probability_max":[0],"weather_code":[3],"sunrise":[null],"sunset":["not a time"],
              "wind_speed_10m_max":[null],"wind_gusts_10m_max":[null],"precipitation_sum":[null],"uv_index_max":[null]}}
        """.trimIndent()
        val f = parseForecast(json)
        assertNull(f.current.isDay)
        with(f.today) {
            assertNull(sunrise)
            assertNull(sunset)
            assertNull(windMaxKmh)
            assertNull(gustMaxKmh)
            assertNull(precipSumMm)
            assertNull(uvIndexMax)
        }
        with(f.nextHours.single()) {
            assertNull(windKmh)
            assertNull(gustKmh)
            assertNull(isDay)
        }
    }

    @Test fun `polar sun times come through as the API sends them`() {
        // Shapes returned by the live API: polar night has sunrise == sunset, midnight sun a 24 h gap.
        fun day(date: String, sunrise: String, sunset: String) = """
            {"current":{"time":"${date}T12:00","temperature_2m":1,"apparent_temperature":0,
              "relative_humidity_2m":90,"wind_speed_10m":3,"weather_code":3,"is_day":false},
             "hourly":{"time":["${date}T12:00"],"temperature_2m":[1],"precipitation_probability":[0],"weather_code":[3]},
             "daily":{"time":["$date"],"temperature_2m_max":[2],"temperature_2m_min":[-1],
              "precipitation_probability_max":[0],"weather_code":[3],"sunrise":["$sunrise"],"sunset":["$sunset"]}}
        """.trimIndent()
        val night = parseForecast(day("2026-12-21", "2026-12-21T00:00", "2026-12-21T00:00"))
        assertEquals(Daylight.POLAR_NIGHT, night.today.daylight)
        assertEquals(false, night.current.isDay) // JSON booleans are accepted as well as 0/1
        val sun = parseForecast(day("2026-06-21", "2026-06-21T00:00", "2026-06-22T00:00"))
        assertEquals(Daylight.MIDNIGHT_SUN, sun.today.daylight)
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
