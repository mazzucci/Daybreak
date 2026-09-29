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

    @Test fun `a failure returns nothing and isn't cached`() = runTest {
        val api = FakeHolidays().apply { fail = true }
        val repo = HolidayRepository(api, InMemoryStore())
        assertTrue(repo.around(today, "US").holidays.isEmpty())
        api.fail = false
        assertEquals(2, repo.around(today, "US").holidays.size)
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
