package com.mazzucci.weather.domain

import com.mazzucci.weather.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ForecastTest {
    private val date = TestData.now.toLocalDate()

    @Test fun `night follows that day's sunrise and sunset`() {
        val f = TestData.forecast() // sunrise 7:02, sunset 18:56
        assertTrue(f.isNight(date.atTime(7, 1)))
        assertFalse(f.isNight(date.atTime(7, 2)))
        assertFalse(f.isNight(date.atTime(18, 55)))
        assertTrue(f.isNight(date.atTime(18, 56)))
        assertTrue(f.isNight(date.plusDays(1).atTime(6, 30)))
    }

    @Test fun `without sun times night is 8 pm to 6 am`() {
        val f = TestData.forecast().let { it.copy(days = it.days.map { d -> d.copy(sunrise = null, sunset = null) }) }
        assertTrue(f.isNight(date.atTime(5, 59)))
        assertFalse(f.isNight(date.atTime(6, 0)))
        assertFalse(f.isNight(date.atTime(19, 30)))
        assertTrue(f.isNight(date.atTime(20, 0)))
        // A time past the last forecast day has no sun times either.
        assertTrue(f.isNight(LocalDateTime.of(2030, 1, 1, 23, 0)))
    }

    @Test fun `is_day flags win over sun times`() {
        val base = TestData.forecast()
        val f = base.copy(current = base.current.copy(isDay = false))
        assertTrue(f.isNightNow) // 2:30 PM, but the API says it's dark (e.g. polar night)
        val hour = f.nextHours.first()
        assertTrue(f.isNight(hour.copy(isDay = false)))
        assertFalse(f.isNight(hour.copy(isDay = null)))
        assertFalse(base.isNightNow)
    }

    @Test fun `upcoming days start today and stop at a week`() {
        val f = TestData.forecast()
        assertEquals(8, f.days.size)
        val week = f.upcomingDays()
        assertEquals(WEEK_DAYS, week.size)
        assertEquals(date, week.first().date)
        assertEquals(date.plusDays(6), week.last().date)
    }

    @Test fun `upcoming days skip days before the place's today`() {
        // Just after midnight the API can still return yesterday first.
        val f = TestData.forecast().let { it.copy(current = it.current.copy(time = date.plusDays(1).atTime(0, 10))) }
        assertEquals(date.plusDays(1), f.upcomingDays().first().date)
        assertEquals(7, f.upcomingDays().size)
    }
}
