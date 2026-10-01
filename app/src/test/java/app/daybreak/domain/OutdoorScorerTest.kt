package app.daybreak.domain

import app.daybreak.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class OutdoorScorerTest {
    private val base = TestData.forecast() // 2:30 PM, sunrise 7:02, sunset 18:56
    private val noon = base.current.time.withHour(12).withMinute(0)
    private val outdoor = WeatherProfile.OUTDOOR

    private fun hour(
        time: LocalDateTime = noon,
        tempC: Double = 18.0,
        precip: Int = 0,
        code: Int = 1,
        wind: Double? = 10.0,
        gust: Double? = 15.0,
    ) = HourForecast(time, tempC, precip, code, wind, gust, isDay = null)

    private fun score(h: HourForecast) = OutdoorScorer.score(h, base, outdoor)

    @Test fun `a dry, mild, calm daylight hour is perfect`() {
        val s = score(hour())
        assertEquals(100, s.score)
        assertTrue(s.limits.isEmpty())
    }

    @Test fun `storms rule an hour out, snow only dents it`() {
        assertEquals(0, score(hour(code = 95, precip = 60)).score)
        assertEquals(setOf(Limit.STORM, Limit.RAIN), score(hour(code = 95, precip = 60)).limits)
        val snow = score(hour(code = 73, tempC = 2.0, precip = 25))
        assertTrue(snow.score in 1 until Outlook.GOOD_MIN)
        assertTrue(Limit.SNOW in snow.limits)
        // A profile that can't take snow rules it out.
        assertEquals(0, OutdoorScorer.score(hour(code = 73, precip = 25), outdoor.copy(snowOk = false), dark = false).score)
    }

    @Test fun `codes only count when the hour's chance and amount aren't dry`() {
        // A rain or snow code with a 5% chance and nothing falling costs nothing.
        assertEquals(100, score(hour(code = 63, precip = 5)).score)
        assertEquals(100, score(hour(code = 73, precip = 5)).score)
        // A storm code with dry numbers costs a little, and is still named.
        val storm = score(hour(code = 95, precip = 5))
        assertEquals(70, storm.score)
        assertEquals(setOf(Limit.STORM), storm.limits)
    }

    @Test fun `each limit lowers the score and is named`() {
        assertEquals(setOf(Limit.RAIN), score(hour(precip = 80, code = 63)).limits)
        assertEquals(setOf(Limit.WIND), score(hour(wind = 40.0, gust = 65.0)).limits)
        assertEquals(setOf(Limit.COLD), score(hour(tempC = -3.0)).limits)
        assertEquals(setOf(Limit.HEAT), score(hour(tempC = 35.0)).limits)
        assertEquals(setOf(Limit.DARK), score(hour(time = noon.withHour(22))).limits)
        listOf(hour(precip = 80, code = 63), hour(tempC = -3.0), hour(tempC = 35.0), hour(time = noon.withHour(22)))
            .forEach { assertTrue(score(it).score < Outlook.MEH_MIN) }
        // Slightly cool but acceptable costs a little, not a lot.
        assertTrue(score(hour(tempC = 9.0)).score >= Outlook.GREAT_MIN)
    }

    @Test fun `missing wind data doesn't count against an hour`() {
        assertEquals(100, score(hour(wind = null, gust = null)).score)
    }

    @Test fun `crossing a wind or rain limit costs a step`() {
        assertTrue(score(hour(wind = 38.0, gust = null)).score < Outlook.GREAT_MIN)
        assertTrue(score(hour(precip = 35)).score >= Outlook.GOOD_MIN) // just over: dented, still fine
        assertEquals(setOf(Limit.RAIN), score(hour(precip = 35)).limits)
    }

    @Test fun `daylight is judged mid-hour, and now follows the current conditions`() {
        val sunrise = noon.withHour(7).withMinute(2)
        val sunset = noon.withHour(18).withMinute(5)
        val f = base.copy(days = base.days.map { it.copy(sunrise = it.date.atTime(7, 2), sunset = it.date.atTime(18, 5)) })
        assertTrue(Limit.DARK !in OutdoorScorer.score(hour(time = sunrise.withMinute(0)), f, outdoor).limits)
        assertTrue(Limit.DARK in OutdoorScorer.score(hour(time = sunset.withMinute(0)), f, outdoor).limits)
        val dawn = f.copy(current = f.current.copy(time = sunrise.withMinute(30), isDay = true))
        assertTrue(Limit.DARK !in OutdoorScorer.score(hour(time = sunrise.withMinute(0)), dawn, outdoor, isNow = true).limits)
    }

    @Test fun `a dark hour is never more than poor`() {
        assertTrue(OutdoorScorer.score(hour(), outdoor, dark = true).score < Outlook.MEH_MIN)
    }
}
