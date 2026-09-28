package com.mazzucci.weather.data

import com.mazzucci.weather.TestData.fixture
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
        assertEquals(12, f.nextHours.size)
    }

    @Test fun `search URL-encodes the query`() = runTest {
        api(fixture("geocoding_empty.json")).searchPlaces("  São Paulo ")
        val url = requested.single()
        assertTrue(url.startsWith("https://geocoding-api.open-meteo.com/v1/search?name=S%C3%A3o+Paulo&"))
    }
}
