package com.mazzucci.weather.domain

import com.mazzucci.weather.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class CommuteTest {
    /** Monday 2026-09-28; hours cover four days, dry and mild unless [wet] says otherwise. */
    private fun forecast(now: LocalDateTime, wet: (LocalDateTime) -> Int = { 0 }): Forecast {
        val base = TestData.forecast()
        val start = now.toLocalDate().atStartOfDay()
        val hours = (0 until 96).map { i ->
            val t = start.plusHours(i.toLong())
            val p = wet(t)
            HourForecast(t, tempC = 16.0, precipChance = p, code = if (p >= 60) 63 else 1, windKmh = 10.0, gustKmh = 15.0)
        }
        return base.copy(current = base.current.copy(time = now, isDay = true), hours = hours)
    }

    private val monday7am = LocalDateTime.of(2026, 9, 28, 7, 0)
    private val settings = CommuteSettings(leaveHour = 8, returnHour = 17)

    @Test fun `before leaving it's about today, after that the next weekday`() {
        assertEquals(monday7am.toLocalDate(), commuteAdvice(forecast(monday7am), Activity.CYCLING, settings)!!.day)
        val monday9am = monday7am.withHour(9)
        assertEquals(DayOfWeek.TUESDAY, commuteAdvice(forecast(monday9am), Activity.CYCLING, settings)!!.day.dayOfWeek)
    }

    @Test fun `weekends are skipped`() {
        val friday = LocalDateTime.of(2026, 10, 2, 10, 0)
        val f = forecast(friday)
        assertEquals(DayOfWeek.MONDAY, commuteAdvice(f, Activity.CYCLING, settings)!!.day.dayOfWeek)
    }

    @Test fun `verdicts follow the worse trip`() {
        val dry = commuteAdvice(forecast(monday7am), Activity.CYCLING, settings)!!
        assertEquals(CommuteAdvice.Verdict.OFFICE, dry.verdict)
        val rainHome = commuteAdvice(forecast(monday7am) { if (it.hour == 17) 80 else 0 }, Activity.CYCLING, settings)!!
        assertEquals(CommuteAdvice.Verdict.WORK_FROM_HOME, rainHome.verdict)
        val showery = commuteAdvice(forecast(monday7am) { if (it.hour == 8) 35 else 0 }, Activity.CYCLING, settings)!!
        assertEquals(CommuteAdvice.Verdict.OFFICE_WITH_CAVEAT, showery.verdict)
    }

    @Test fun `no advice when the forecast doesn't reach the trips`() {
        val late = TestData.forecast() // only 12 hours ahead of Monday 2:30 PM
        assertNull(commuteAdvice(late, Activity.CYCLING, settings))
    }

    @Test fun `wording names the day and the problem`() {
        val today = monday7am.toLocalDate()
        val dry = commuteAdvice(forecast(monday7am), Activity.CYCLING, settings)!!
        assertEquals(
            "Office day" to "Good to ride both ways: dry, 61° going in, dry, 61° coming home.",
            describeCommute(dry, Activity.CYCLING, TempUnit.F, today),
        )
        val rainHome = commuteAdvice(forecast(monday7am.withHour(9)) { if (it.hour == 17) 80 else 0 }, Activity.CYCLING, settings)!!
        assertEquals(
            "Tomorrow: maybe work from home" to "Rain likely (80%) on the way home at 5 PM.",
            describeCommute(rainHome, Activity.CYCLING, TempUnit.F, today),
        )
    }
}
