package com.mazzucci.weather.domain

import com.mazzucci.weather.TestData
import com.mazzucci.weather.TestData.fixture
import com.mazzucci.weather.data.parseLongWeekends
import com.mazzucci.weather.data.parsePublicHolidays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ComingUpTest {
    private val holidays = parsePublicHolidays(fixture("holidays_us_2026.json"))
    private val weekends = parseLongWeekends(fixture("long_weekends_us_2026.json"))
    private val today = LocalDate.of(2026, 9, 28)

    @Test fun `parses nationwide public holidays only`() {
        assertTrue(holidays.none { it.name == "Columbus Day" }) // a bank holiday nationally, public only in some states
        assertTrue(holidays.any { it.name == "Thanksgiving Day" && it.date == LocalDate.of(2026, 11, 26) })
        assertEquals(10, holidays.size)
        val thanksgiving = weekends.single { it.start == LocalDate.of(2026, 11, 26) }
        assertEquals(4, thanksgiving.dayCount)
        assertEquals(listOf(LocalDate.of(2026, 11, 27)), thanksgiving.bridgeDays)
    }

    @Test fun `next holiday, next long weekend and next season, soonest first`() {
        val items = comingUp(today, holidays, weekends, latitude = 37.8)
        assertEquals(
            listOf(
                Countdown(Countdown.Kind.HOLIDAY, "Veterans Day", LocalDate.of(2026, 11, 11)),
                Countdown(
                    Countdown.Kind.LONG_WEEKEND, "4-day weekend", LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 29),
                    note = "Thanksgiving Day · take Friday off",
                ),
                Countdown(Countdown.Kind.SEASON, "First day of winter", LocalDate.of(2026, 12, 21)),
            ),
            items,
        )
    }

    @Test fun `a holiday inside a long weekend says so instead of listing the weekend twice`() {
        val items = comingUp(LocalDate.of(2026, 11, 20), holidays, weekends, latitude = 37.8)
        val thanksgiving = items.first()
        assertEquals("Thanksgiving Day", thanksgiving.title)
        assertEquals("Take Friday off", thanksgiving.note)
        assertTrue(items.none { it.kind == Countdown.Kind.LONG_WEEKEND && it.date == LocalDate.of(2026, 11, 26) })
    }

    @Test fun `seasons follow the hemisphere and skip the tropics`() {
        assertEquals("First day of winter", nextSeason(today, 51.5)?.title)
        assertEquals("First day of summer", nextSeason(today, -33.9)?.title)
        assertNull(nextSeason(today, 1.3))
        assertEquals(LocalDate.of(2027, 3, 20), nextSeason(LocalDate.of(2026, 12, 21), 40.0)?.date)
    }

    @Test fun `only things within the horizon count`() {
        val items = comingUp(LocalDate.of(2026, 1, 2), emptyList(), emptyList(), latitude = 40.0, horizonDays = 30)
        assertTrue(items.isEmpty()) // spring is 77 days away
    }

    @Test fun `countdown wording`() {
        assertEquals("Today", formatCountdown(0))
        assertEquals("Tomorrow", formatCountdown(1))
        assertEquals("In 13 days", formatCountdown(13))
        assertEquals("In 6 weeks", formatCountdown(44))
        assertEquals("In 3 months", formatCountdown(84))
    }

    @Test fun `country code comes from the geocoder or the country name`() {
        assertEquals("GB", countryCodeOf(TestData.london.copy(countryCode = "GB")))
        assertEquals("GB", countryCodeOf(TestData.london)) // "United Kingdom"
        assertEquals("US", countryCodeOf(TestData.sanFrancisco)) // "United States"
        assertNull(countryCodeOf(TestData.london.copy(country = null)))
    }
}
