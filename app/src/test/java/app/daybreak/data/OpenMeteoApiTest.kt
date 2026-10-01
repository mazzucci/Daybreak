package app.daybreak.data

import app.daybreak.TestData.fixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoApiTest {
    private val requested = mutableListOf<String>()

    private fun api(body: String) = OpenMeteoApi { url -> requested += url; body }

    @Test fun `forecast requests current, hourly and daily data over HTTPS in local time`() = runTest {
        val f = api(fixture("forecast_sf.json")).forecast(37.77, -122.42)
        val url = requested.single()
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?latitude=37.77&longitude=-122.42"))
        assertTrue("current=" in url && "hourly=" in url && "daily=" in url)
        assertTrue("timezone=auto" in url)
        assertTrue("forecast_days=10" in url)
        listOf(
            "sunrise", "sunset", "wind_gusts_10m_max", "uv_index_max", "precipitation_sum", "wind_gusts_10m", "is_day",
            "apparent_temperature", "precipitation", "snowfall", "wind_direction_10m", "precipitation_hours",
            "snowfall_sum", "precipitation_probability_mean", "wind_direction_10m_dominant",
        ).forEach { assertTrue("missing $it", Regex("[=,]$it(,|&)").containsMatchIn(url)) }
        assertEquals(12, f.nextHours.size)
    }

    @Test fun `search URL-encodes the query`() = runTest {
        api(fixture("geocoding_empty.json")).searchPlaces("  São Paulo ")
        val url = requested.single()
        assertTrue(url.startsWith("https://geocoding-api.open-meteo.com/v1/search?name=S%C3%A3o+Paulo&"))
    }

    @Test fun `HTTP errors name the service`() = runTest {
        val failing = OpenMeteoApi { throw HttpException(503) }
        val e = runCatching { failing.forecast(1.0, 2.0) }.exceptionOrNull()
        assertEquals("Weather service returned HTTP 503", e?.message)
        assertEquals("Place search returned HTTP 503", runCatching { failing.searchPlaces("Rome") }.exceptionOrNull()?.message)
    }

    @Test fun `geocoding keeps the country code`() {
        val place = parseGeocoding("""{"results":[{"id":1,"name":"Rome","latitude":41.9,"longitude":12.5,"country":"Italy","country_code":"it"}]}""").single()
        assertEquals("IT", place.countryCode)
    }
}
