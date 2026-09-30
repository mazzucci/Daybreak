package app.daybreak.domain

import app.daybreak.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals("A dry ride home · 61°", describeCommute(midday, Activity.CYCLING, TempUnit.F, monday7am.toLocalDate()).detail)
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
        val copy = describeCommute(advice, Activity.CYCLING, TempUnit.F, monday7am.toLocalDate())
        assertEquals("Office day, with a catch", copy.headline)
        assertEquals("Dark on the way home · take lights at 5 PM", copy.detail)
    }

    @Test fun `the weekend follows the country`() {
        assertEquals(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), weekendDays("SA"))
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), weekendDays("GB"))
        assertEquals(setOf(DayOfWeek.FRIDAY), weekendDays("af"))
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), weekendDays(null))
        // Thursday evening in Riyadh: the next workday is Sunday.
        val thursday = LocalDateTime.of(2026, 10, 1, 18, 0)
        val advice = commuteAdvice(forecast(thursday), Activity.CYCLING, settings, weekend = weekendDays("SA"))!!
        assertEquals(DayOfWeek.SUNDAY, advice.day.dayOfWeek)
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
            "Rain possible on the way in · 35% at 8 AM",
            describeCommute(showery, Activity.CYCLING, TempUnit.F, monday7am.toLocalDate()).detail,
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
            CommuteCopy("Today's commute", "Office day", "A dry ride both ways · 61°", "A dry ride both ways, 61°F (16°C)"),
            describeCommute(dry, Activity.CYCLING, TempUnit.F, today),
        )
        val rainHome = commuteAdvice(forecast(monday7am.withHour(18)) { if (it.hour == 17) 80 else 0 }, Activity.CYCLING, settings)!!
        assertEquals(
            CommuteCopy("Tomorrow's commute", "Maybe work from home", "Rain likely on the way home · 80% at 5 PM", "Rain likely on the way home, 80% at 5 PM"),
            describeCommute(rainHome, Activity.CYCLING, TempUnit.F, today),
        )
        val showery = commuteAdvice(forecast(monday7am) { if (it.hour == 8) 35 else 0 }, Activity.CYCLING, settings)!!
        assertEquals(
            CommuteCopy("Today's commute", "Office day, with a catch", "Rain possible on the way in · 35% at 8 AM", "Rain possible on the way in, 35% at 8 AM"),
            describeCommute(showery, Activity.CYCLING, TempUnit.F, today),
        )
    }

    /** A wider spread between the trips shows as a range, in both units when spoken; Friday's check names the day. */
    @Test fun `office day gives the temperature range and names a later day`() {
        val today = monday7am.toLocalDate()
        val friday = commuteAdvice(forecast(LocalDateTime.of(2026, 10, 1, 18, 0)), Activity.WALKING, settings)!!
        assertEquals("Friday's commute", describeCommute(friday, Activity.WALKING, TempUnit.F, today).eyebrow)
        val f = forecast(monday7am)
        val warmer = f.copy(hours = f.hours.map { if (it.time.hour == 17) it.copy(tempC = 20.0) else it })
        val copy = describeCommute(commuteAdvice(warmer, Activity.WALKING, settings)!!, Activity.WALKING, TempUnit.C, today)
        assertEquals("A dry walk both ways · 16–20°", copy.detail)
        assertEquals("A dry walk both ways, 16 to 20°C (61 to 68°F)", copy.spokenDetail)
    }

    @Test fun `with an office, each trip is judged at its worse end`() {
        val today = monday7am.toLocalDate()
        fun detail(home: Forecast, office: Forecast): Pair<CommuteAdvice, String> {
            val advice = commuteAdvice(home, Activity.CYCLING, settings, office = office)!!
            return advice to describeCommute(advice, Activity.CYCLING, TempUnit.F, today).detail
        }
        val dry = forecast(monday7am)
        val (wetEvening, eveningText) = detail(dry, forecast(monday7am) { if (it.hour == 17) 80 else 0 })
        assertEquals(CommuteAdvice.Verdict.WORK_FROM_HOME, wetEvening.verdict)
        assertTrue(wetEvening.inboundAtOffice)
        assertFalse(wetEvening.outboundAtOffice)
        assertEquals("Rain likely leaving the office · 80% at 5 PM", eveningText)

        val (showery, morningText) = detail(dry, forecast(monday7am) { if (it.hour == 8) 35 else 0 })
        assertEquals(CommuteAdvice.Verdict.OFFICE_WITH_CAVEAT, showery.verdict)
        assertEquals("Rain possible arriving at the office · 35% at 8 AM", morningText)

        // Wetter at home: the trip's words stay "on the way".
        val (homeWet, homeText) = detail(forecast(monday7am) { if (it.hour == 17) 80 else 0 }, forecast(monday7am) { if (it.hour == 17) 40 else 0 })
        assertFalse(homeWet.inboundAtOffice)
        assertEquals("Rain likely on the way home · 80% at 5 PM", homeText)

        // Dry at both ends is an ordinary office day.
        assertEquals("A dry ride both ways · 61°", detail(dry, dry).second)
    }

    @Test fun `an office forecast that doesn't reach the trips is left out`() {
        val office = forecast(monday7am) { 90 }.copy(hours = emptyList())
        val advice = commuteAdvice(forecast(monday7am), Activity.CYCLING, settings, office = office)!!
        assertEquals(CommuteAdvice.Verdict.OFFICE, advice.verdict)
        assertFalse(advice.inboundAtOffice)
    }

    @Test fun `an office in another time zone is lined up by the clock, and reported in home's hours`() {
        // The office is an hour ahead: home's 5 PM is its 6 PM, when it rains there.
        val office = forecast(monday7am.plusHours(1)) { if (it.hour == 18) 80 else 0 }.copy(utcOffsetSeconds = 3600)
        val advice = commuteAdvice(forecast(monday7am), Activity.CYCLING, settings, office = office)!!
        assertTrue(advice.inboundAtOffice)
        assertEquals(monday7am.withHour(17), advice.inbound.hour.time)
        assertEquals(
            "Rain likely leaving the office · 80% at 5 PM",
            describeCommute(advice, Activity.CYCLING, TempUnit.F, monday7am.toLocalDate()).detail,
        )
    }

    @Test fun `removing home removes the office with it`() {
        val home = TestData.sanFrancisco
        val both = CommuteSettings().with(CommuteEnd.HOME, home).with(CommuteEnd.OFFICE, TestData.london)
        assertEquals(Place.COMMUTE_HOME_ID, both.home?.id)
        assertEquals(Place.COMMUTE_OFFICE_ID, both.office?.id)
        assertEquals(both.copy(office = null), both.with(CommuteEnd.OFFICE, null))
        assertEquals(CommuteSettings(), both.with(CommuteEnd.HOME, null))
    }
}
