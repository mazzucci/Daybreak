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
        assertEquals("Take Friday off for a 4-day weekend", thanksgiving.note)
        assertTrue(items.none { it.kind == Countdown.Kind.LONG_WEEKEND && it.date == LocalDate.of(2026, 11, 26) })
    }

    @Test fun `a day of leave already booked isn't suggested again`() {
        val friday = setOf(LocalDate.of(2026, 11, 27))
        assertEquals("Thanksgiving Day", comingUp(today, holidays, weekends, latitude = 37.8, offDates = friday)[1].note)
        assertEquals("4-day weekend", comingUp(LocalDate.of(2026, 11, 20), holidays, weekends, latitude = 37.8, offDates = friday).first().note)
    }

    @Test fun `a long weekend under way says when it ends, not which day to take off`() {
        val saturday = LocalDate.of(2026, 11, 28)
        val items = comingUp(saturday, holidays, weekends, latitude = 37.8)
        val weekend = items.first { it.kind == Countdown.Kind.LONG_WEEKEND }
        assertEquals("Thanksgiving Day · ends Sunday", weekend.note)
        assertEquals(saturday, weekend.date)
    }

    @Test fun `a season starting today is shown as today`() {
        val solstice = LocalDate.of(2026, 12, 21)
        assertEquals(solstice, nextSeason(solstice, 40.0)?.date)
        assertEquals("Today", formatCountdown(nextSeason(solstice, 40.0)!!.daysFrom(solstice)))
    }

    @Test fun `country name aliases`() {
        listOf("Ivory Coast" to "CI", "DR Congo" to "CD", "Hong Kong" to "HK", "Türkiye" to "TR", "Bosnia and Herzegovina" to "BA", "Japan" to "JP")
            .forEach { (name, code) -> assertEquals(name, code, countryCodeOf(TestData.tokyo.copy(country = name, countryCode = null))) }
    }

    @Test fun `seasons follow the hemisphere and skip the tropics`() {
        assertEquals("First day of winter", nextSeason(today, 51.5)?.title)
        assertEquals("First day of summer", nextSeason(today, -33.9)?.title)
        assertNull(nextSeason(today, 1.3))
        assertEquals(LocalDate.of(2027, 3, 20), nextSeason(LocalDate.of(2026, 12, 22), 40.0)?.date)
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
        assertEquals("GB", countryCodeOf(TestData.london.copy(countryCode = null))) // "United Kingdom"
        assertEquals("US", countryCodeOf(TestData.sanFrancisco.copy(countryCode = null))) // "United States"
        assertNull(countryCodeOf(TestData.london.copy(country = null, countryCode = null)))
    }

    @Test fun `days off count down with the break they make`() {
        val wed = LocalDate.of(2026, 9, 30)
        val friday = LocalDate.of(2026, 10, 2)
        fun next(vararg d: PersonalDate, holidays: Set<LocalDate> = emptySet()) = upcomingPersonalDates(wed, d.toList(), holidays).first()
        // A Friday off makes a 3-day weekend; with Monday's holiday, a 4-day one.
        assertEquals(
            Countdown(Countdown.Kind.DAY_OFF, "Day off", friday, note = "Makes a 3-day weekend"),
            next(PersonalDate(friday, dayOff = true)),
        )
        assertEquals("Makes a 4-day weekend", next(PersonalDate(friday, dayOff = true), holidays = setOf(friday.plusDays(3))).note)
        // A working week off, weekends either side: nine days.
        val week = PersonalDate(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16), "Lisbon trip", dayOff = true)
        assertEquals(Countdown(Countdown.Kind.DAY_OFF, "Lisbon trip", week.start, week.end, "9 days in a row"), next(week))
        // Tuesday to Thursday: nothing to join up with.
        assertEquals("3 days", next(PersonalDate(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 8), dayOff = true)).note)
        assertNull(next(PersonalDate(LocalDate.of(2026, 10, 7), dayOff = true)).note)
    }

    @Test fun `other dates just count down, and only the next three within the horizon show`() {
        val wed = LocalDate.of(2026, 9, 30)
        val talk = PersonalDate(LocalDate.of(2026, 10, 8), name = "Board presentation")
        assertEquals(listOf(Countdown(Countdown.Kind.PERSONAL, "Board presentation", talk.start)), upcomingPersonalDates(wed, listOf(talk)))
        // Next to a weekend, but not a day off: no break to speak of.
        assertNull(upcomingPersonalDates(wed, listOf(PersonalDate(LocalDate.of(2026, 10, 2), name = "Launch"))).single().note)
        val many = (1..5L).map { PersonalDate(wed.plusDays(it * 7), name = "Week $it") } +
            PersonalDate(wed.minusDays(3), name = "Past") + PersonalDate(wed.plusDays(200), name = "Far")
        assertEquals(listOf("Week 1", "Week 2", "Week 3"), upcomingPersonalDates(wed, many).map { it.title })
    }

    @Test fun `yearly dates come round again`() {
        val birthday = PersonalDate(LocalDate.of(2026, 3, 14), name = "Mum's birthday", yearly = true)
        val wed = LocalDate.of(2026, 9, 30)
        assertEquals(LocalDate.of(2027, 3, 14), birthday.next(wed)?.start)
        assertEquals(LocalDate.of(2026, 3, 14), birthday.next(LocalDate.of(2026, 3, 14))?.start)
        assertNull(birthday.copy(yearly = false).next(wed))
        // 29 February falls on the 28th in other years.
        assertEquals(LocalDate.of(2027, 2, 28), PersonalDate(LocalDate.of(2028, 2, 29), yearly = true).next(LocalDate.of(2027, 1, 1))?.start)
        assertEquals(LocalDate.of(2027, 3, 14), upcomingPersonalDates(LocalDate.of(2027, 1, 1), listOf(birthday)).single().date)
    }

    @Test fun `a date under way counts as today and says when it ends`() {
        val trip = PersonalDate(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 2), "Lisbon trip", dayOff = true)
        val wed = LocalDate.of(2026, 9, 30)
        assertEquals(Countdown(Countdown.Kind.DAY_OFF, "Lisbon trip", wed, trip.end, "Until Friday"), upcomingPersonalDates(wed, listOf(trip)).single())
        assertEquals("Last day", upcomingPersonalDates(trip.end, listOf(trip)).single().note)
    }

    @Test fun `day off dates cover every day of each day off, including a yearly one's next two`() {
        val today = LocalDate.of(2026, 9, 30)
        val dates = dayOffDates(
            listOf(
                PersonalDate(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14), dayOff = true),
                PersonalDate(LocalDate.of(2026, 12, 24), dayOff = true, yearly = true),
                PersonalDate(LocalDate.of(2026, 10, 20), name = "Presentation"),
            ),
            today,
        )
        assertEquals(setOf(12, 13, 14).map { LocalDate.of(2026, 10, it) }.toSet() + LocalDate.of(2026, 12, 24) + LocalDate.of(2027, 12, 24), dates)
    }

    @Test fun `a yearly date over New Year is still under way in January`() {
        val holidays = PersonalDate(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 4), "Winter break", dayOff = true, yearly = true)
        val jan4 = LocalDate.of(2027, 1, 4)
        assertEquals(PersonalDate(LocalDate.of(2026, 12, 30), jan4, "Winter break", dayOff = true, yearly = true), holidays.next(jan4))
        assertEquals("Last day", upcomingPersonalDates(jan4, listOf(holidays)).single().note)
        assertTrue(jan4 in dayOffDates(listOf(holidays), jan4))
        assertEquals(LocalDate.of(2027, 12, 30), holidays.next(jan4.plusDays(1))?.start)
    }

    @Test fun `a yearly range over 29 February keeps its end date in other years`() {
        val leap = PersonalDate(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 1), yearly = true)
        assertEquals(PersonalDate(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 1), yearly = true), leap.next(LocalDate.of(2027, 1, 1)))
    }

    @Test fun `a day off on a day that was off anyway makes no break, and only weekends make weekends`() {
        val wed = LocalDate.of(2026, 9, 30)
        assertNull(upcomingPersonalDates(wed, listOf(PersonalDate(LocalDate.of(2026, 10, 3), dayOff = true))).single().note)
        val holiday = LocalDate.of(2026, 10, 7)
        assertNull(upcomingPersonalDates(wed, listOf(PersonalDate(holiday, dayOff = true)), setOf(holiday)).single().note)
        // Tuesday off before a Wednesday holiday: two days, but no weekend.
        assertEquals("2 days in a row", upcomingPersonalDates(wed, listOf(PersonalDate(LocalDate.of(2026, 10, 6), dayOff = true)), setOf(holiday)).single().note)
    }

    @Test fun `yesterday's day off still counts, for a night shift's trip home`() {
        val today = LocalDate.of(2026, 9, 30)
        assertTrue(today.minusDays(1) in dayOffDates(listOf(PersonalDate(today.minusDays(1), dayOff = true)), today))
    }
}
