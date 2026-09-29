package com.mazzucci.weather.domain

import com.mazzucci.weather.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test fun `today until the trip home, then the next weekday`() {
        val early = commuteAdvice(forecast(monday7am), Activity.CYCLING, settings)!!
        assertEquals(monday7am.toLocalDate(), early.day)
        assertNotNull(early.outbound)
        // Leaving now: the trip in is the current hour, still judged.
        assertNotNull(commuteAdvice(forecast(monday7am.withHour(8).withMinute(10)), Activity.CYCLING, settings)!!.outbound)
        // Mid-morning: only the way home is left.
        val midday = commuteAdvice(forecast(monday7am.withHour(10)), Activity.CYCLING, settings)!!
        assertEquals(monday7am.toLocalDate(), midday.day)
        assertNull(midday.outbound)
        // After the trip home: tomorrow.
        assertEquals(DayOfWeek.TUESDAY, commuteAdvice(forecast(monday7am.withHour(18)), Activity.CYCLING, settings)!!.day.dayOfWeek)
    }

    @Test fun `a night shift's trip home is the next morning`() {
        val night = CommuteSettings(leaveHour = 22, returnHour = 6)
        val advice = commuteAdvice(forecast(monday7am.withHour(21)) { if (it.hour == 6 && it.dayOfMonth == 29) 80 else 0 }, Activity.CYCLING, night)!!
        assertEquals(monday7am.toLocalDate(), advice.day)
        assertEquals(monday7am.plusDays(1).withHour(6), advice.inbound.hour.time)
        assertEquals(CommuteAdvice.Verdict.WORK_FROM_HOME, advice.verdict)
        // At 1 AM on Tuesday the shift that started Monday still has its trip home ahead.
        val onShift = commuteAdvice(forecast(monday7am.plusDays(1).withHour(1)), Activity.CYCLING, night)!!
        assertEquals(monday7am.toLocalDate(), onShift.day)
        assertNull(onShift.outbound)
    }

    @Test fun `darkness is a caveat, never a reason to stay home`() {
        val winter = forecast(monday7am).let { f -> f.copy(days = f.days.map { it.copy(sunrise = it.date.atTime(7, 30), sunset = it.date.atTime(16, 50)) }) }
        val advice = commuteAdvice(winter, Activity.CYCLING, settings)!!
        assertEquals(CommuteAdvice.Verdict.OFFICE_WITH_CAVEAT, advice.verdict)
        assertEquals(
            "Office day, with a catch" to "Dark on the way home at 5 PM: take lights.",
            describeCommute(advice, Activity.CYCLING, TempUnit.F, monday7am.toLocalDate()),
        )
    }

    @Test fun `public holidays are skipped like weekends`() {
        val tuesday = monday7am.toLocalDate().plusDays(1)
        val advice = commuteAdvice(forecast(monday7am.withHour(18)), Activity.CYCLING, settings, holidays = setOf(tuesday))!!
        assertEquals(DayOfWeek.WEDNESDAY, advice.day.dayOfWeek)
    }

    @Test fun `a missing daylight-saving hour uses the next one`() {
        val gap = forecast(monday7am).let { f -> f.copy(hours = f.hours.filterNot { it.time == monday7am.withHour(17) }) }
        assertEquals(monday7am.withHour(18), commuteAdvice(gap, Activity.CYCLING, settings)!!.inbound.hour.time)
    }

    @Test fun `weekends are skipped`() {
        val friday = LocalDateTime.of(2026, 10, 2, 18, 0) // after Friday's trip home
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
        assertEquals(
            "A chance of rain (35%) on the way in at 8 AM.",
            describeCommute(showery, Activity.CYCLING, TempUnit.F, monday7am.toLocalDate()).second,
        )
    }

    @Test fun `no advice when the forecast doesn't reach the trips`() {
        val evening = forecast(monday7am.withHour(18)).let { f -> f.copy(hours = f.hours.filter { it.time.toLocalDate() == monday7am.toLocalDate() }) }
        assertNull(commuteAdvice(evening, Activity.CYCLING, settings)) // Tuesday's trips aren't in the data
        assertNull(commuteAdvice(TestData.forecast().copy(hours = emptyList()), Activity.CYCLING, settings))
    }

    @Test fun `wording names the day and the problem`() {
        val today = monday7am.toLocalDate()
        val dry = commuteAdvice(forecast(monday7am), Activity.CYCLING, settings)!!
        assertEquals(
            "Office day" to "Good to ride both ways: dry, 61° going in, dry, 61° coming home.",
            describeCommute(dry, Activity.CYCLING, TempUnit.F, today),
        )
        val rainHome = commuteAdvice(forecast(monday7am.withHour(18)) { if (it.hour == 17) 80 else 0 }, Activity.CYCLING, settings)!!
        assertEquals(
            "Tomorrow: maybe work from home" to "Rain likely (80%) on the way home at 5 PM.",
            describeCommute(rainHome, Activity.CYCLING, TempUnit.F, today),
        )
    }
}
