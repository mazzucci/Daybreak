package com.mazzucci.weather.domain

import com.mazzucci.weather.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ActivityScorerTest {
    private val base = TestData.forecast() // 2:30 PM, sunrise 7:02, sunset 18:56
    private val noon = base.current.time.withHour(12).withMinute(0)
    private val cycling = Activity.CYCLING.profile

    private fun hour(
        time: LocalDateTime = noon,
        tempC: Double = 18.0,
        precip: Int = 0,
        code: Int = 1,
        wind: Double? = 10.0,
        gust: Double? = 15.0,
    ) = HourForecast(time, tempC, precip, code, wind, gust, isDay = null)

    private fun score(h: HourForecast, profile: WeatherProfile = cycling) = ActivityScorer.score(h, base, profile)

    @Test fun `a dry, mild, calm daylight hour is perfect`() {
        val s = score(hour())
        assertEquals(100, s.score)
        assertTrue(s.limits.isEmpty())
    }

    @Test fun `storms and snow rule cycling out, snow only dents running`() {
        assertEquals(0, score(hour(code = 95)).score)
        assertEquals(setOf(Limit.STORM), score(hour(code = 95)).limits)
        assertEquals(0, score(hour(code = 73, tempC = 0.0)).score)
        val run = score(hour(code = 73, tempC = 2.0), Activity.RUNNING.profile)
        assertTrue(run.score in 1 until ActivityScorer.GOOD)
        assertTrue(Limit.SNOW in run.limits)
    }

    @Test fun `each limit lowers the score and is named`() {
        assertEquals(setOf(Limit.RAIN), score(hour(precip = 80, code = 63)).limits)
        assertEquals(setOf(Limit.WIND), score(hour(wind = 40.0, gust = 65.0)).limits)
        assertEquals(setOf(Limit.COLD), score(hour(tempC = -3.0)).limits)
        assertEquals(setOf(Limit.HEAT), score(hour(tempC = 35.0)).limits)
        assertEquals(setOf(Limit.DARK), score(hour(time = noon.withHour(22))).limits)
        listOf(hour(precip = 80, code = 63), hour(wind = 40.0, gust = 65.0), hour(tempC = -3.0), hour(tempC = 35.0), hour(time = noon.withHour(22)))
            .forEach { assertTrue(score(it).score < ActivityScorer.GOOD) }
        // Slightly cool but acceptable costs a little, not a lot.
        assertTrue(score(hour(tempC = 9.0)).score >= ActivityScorer.GOOD)
    }

    @Test fun `missing wind data doesn't count against an hour`() {
        assertEquals(100, score(hour(wind = null, gust = null)).score)
    }

    @Test fun `windows are runs of good hours and the best one wins`() {
        fun scored(vararg s: Int) = s.mapIndexed { i, v -> HourScore(hour(time = noon.plusHours(i.toLong())), v, emptySet()) }
        val w = ActivityScorer.windows(scored(90, 90, 20, 75, 80, 85, 90, 10, 95))
        assertEquals(listOf(2, 4, 1), w.map { it.hours.size })
        assertEquals(noon.plusHours(3), w[1].start)
        assertEquals(noon.plusHours(7), w[1].end)
    }

    @Test fun `plan finds the dry afternoon before the evening rain`() {
        val plan = ActivityScorer.plan(base, Activity.CYCLING) // rain likely from 6 PM
        val best = assertNotNullAndGet(plan.best)
        assertEquals(base.current.time.withMinute(0), best.start) // from now…
        assertTrue(!best.end.isAfter(base.current.time.withHour(18).withMinute(0))) // …until the rain
        assertTrue(plan.blockers.isEmpty())
        assertEquals(base.nextHours.size, plan.hours.size) // the fixture only has 12 hours ahead
    }

    @Test fun `no window names what's in the way, ignoring the dark`() {
        val wet = base.copy(hours = base.hours.map { it.copy(precipChance = 90, code = 63, gustKmh = 70.0) })
        val plan = ActivityScorer.plan(wet, Activity.CYCLING)
        assertNull(plan.best)
        assertEquals(Limit.RAIN, plan.blockers.first())
        assertTrue(Limit.WIND in plan.blockers)
        assertTrue(Limit.DARK !in plan.blockers)
        assertEquals("Rain and strong wind", describeBlockers(plan.blockers))
    }

    @Test fun `a daylight-saving gap doesn't split a window`() {
        // Spring forward: 1:00 is followed by 3:00 in local time. Walking the list keeps them consecutive.
        val times = listOf(noon, noon.plusHours(1), noon.plusHours(3), noon.plusHours(4))
        val scored = times.map { HourScore(hour(time = it), 90, emptySet()) }
        val w = ActivityScorer.windows(scored).single()
        assertEquals(4, w.hours.size)
        assertEquals(noon.plusHours(5), w.end)
    }

    @Test fun `window text`() {
        val w = ActivityWindow(listOf(HourScore(hour(tempC = 17.0), 90, emptySet()), HourScore(hour(time = noon.plusHours(1), tempC = 21.0, wind = 20.0), 90, emptySet())))
        assertEquals("12 PM–2 PM", formatWindow(w, java.util.Locale.US))
        assertEquals("Dry · light wind · 63–70°", describeWindow(w, TempUnit.F))
        assertEquals("Dry · light wind · 17–21°", describeWindow(w, TempUnit.C))
    }

    private fun <T> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
