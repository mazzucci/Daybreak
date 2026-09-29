package com.mazzucci.weather.data

import com.mazzucci.weather.domain.Holiday
import com.mazzucci.weather.domain.LongWeekend
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class HolidayRepositoryTest {
    private class FakeHolidays : HolidayApi {
        val calls = mutableListOf<String>()
        var fail = false
        override suspend fun publicHolidays(year: Int, countryCode: String): List<Holiday> {
            calls += "h$year$countryCode"
            if (fail) throw IOException("offline")
            return listOf(Holiday(LocalDate.of(year, 12, 25), "Christmas Day"))
        }
        override suspend fun longWeekends(year: Int, countryCode: String): List<LongWeekend> {
            calls += "w$year$countryCode"
            return listOf(LongWeekend(LocalDate.of(year, 12, 25), LocalDate.of(year, 12, 27), 3, emptyList()))
        }
    }

    private val today = LocalDate.of(2026, 9, 28)

    @Test fun `fetches this year and next once, then serves them from the store`() = runTest {
        val api = FakeHolidays()
        val store = InMemoryStore()
        val first = HolidayRepository(api, store).around(today, "US")
        assertEquals(listOf(LocalDate.of(2026, 12, 25), LocalDate.of(2027, 12, 25)), first.holidays.map { it.date })
        assertEquals(4, api.calls.size)
        val again = HolidayRepository(api, store).around(today, "US") // a new instance: from the store
        assertEquals(first, again)
        assertEquals(4, api.calls.size)
    }

    @Test fun `a failure returns nothing, waits a few minutes, then retries`() = runTest {
        val api = FakeHolidays().apply { fail = true }
        var clock = 0L
        val repo = HolidayRepository(api, InMemoryStore(), now = { clock })
        assertTrue(repo.around(today, "US").holidays.isEmpty())
        api.fail = false
        val calls = api.calls.size
        assertTrue(repo.around(today, "US").holidays.isEmpty()) // too soon to retry
        assertEquals(calls, api.calls.size)
        clock += 11 * 60_000
        assertEquals(2, repo.around(today, "US").holidays.size)
    }

    @Test fun `cached years are refreshed after a month and last year's is dropped`() = runTest {
        val api = FakeHolidays()
        val store = InMemoryStore(mapOf("holidays:US:2025" to "{}"))
        var clock = 0L
        val repo = HolidayRepository(api, store, now = { clock })
        repo.around(today, "US")
        assertEquals(null, store.getString("holidays:US:2025"))
        clock += 29L * 24 * 3600_000
        repo.around(today, "US")
        assertEquals(4, api.calls.size)
        clock += 2L * 24 * 3600_000
        repo.around(today, "US")
        assertEquals(8, api.calls.size)
    }

    @Test fun `countries Nager doesn't cover mean no holidays, not an error`() = runTest {
        val api = NagerHolidayApi { url -> if ("PublicHolidays" in url) throw HttpException(204) else throw HttpException(404) }
        assertTrue(api.publicHolidays(2026, "IN").isEmpty())
        assertTrue(api.longWeekends(2026, "XK").isEmpty())
        val broken = NagerHolidayApi { throw HttpException(500) }
        assertThrowsIo { broken.publicHolidays(2026, "US") }
    }

    private suspend fun assertThrowsIo(block: suspend () -> Unit) {
        try {
            block()
            throw AssertionError("expected an IOException")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test fun `requests go to Nager with just the year and country`() = runTest {
        val urls = mutableListOf<String>()
        val api = NagerHolidayApi { url -> urls += url; "[]" }
        api.publicHolidays(2026, "gb")
        api.longWeekends(2026, "GB")
        assertEquals(
            listOf("https://date.nager.at/api/v3/PublicHolidays/2026/GB", "https://date.nager.at/api/v3/LongWeekend/2026/GB"),
            urls,
        )
    }
}
