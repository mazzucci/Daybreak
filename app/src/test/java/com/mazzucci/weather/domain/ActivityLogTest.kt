package com.mazzucci.weather.domain

import com.mazzucci.weather.TestData.fixture
import com.mazzucci.weather.data.parseRideHours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ActivityLogTest {
    private val hours = parseRideHours(fixture("ride_weather_sf.json"))
    private fun ride(start: String, minutes: Long, indoor: Boolean = false, kind: ExerciseKind = ExerciseKind.RIDE) =
        Instant.parse(start).let { Exercise("id-$start", kind, null, it, it.plusSeconds(minutes * 60), "com.garmin.android.apps.connectmobile", indoor) }

    @Test fun `each workout gets the weather it overlapped`() {
        val w = withWeather(listOf(ride("2026-09-20T15:00:00Z", 90)), hours).single()
        assertEquals(13.6, w.minTempC!!, 0.001)
        assertEquals(14.0, w.maxTempC!!, 0.001)
        assertEquals(12.7, w.maxWindKmh!!, 0.001)
        assertEquals(0.0, w.rainMm, 0.001)
    }

    @Test fun `rain counts through the workout's last hour`() {
        val wet = hours.map { if (it.time == Instant.parse("2026-09-20T17:00:00Z")) it.copy(precipitationMm = 2.0) else it }
        assertEquals(2.0, withWeather(listOf(ride("2026-09-20T16:05:00Z", 20)), wet).single().rainMm, 0.001)
    }

    @Test fun `indoor workouts and missing hours have no weather`() {
        val indoor = withWeather(listOf(ride("2026-09-20T15:00:00Z", 60, indoor = true)), hours).single()
        assertNull(indoor.minTempC)
        assertNull(indoor.code)
        assertNull(withWeather(listOf(ride("2030-01-01T15:00:00Z", 60)), hours).single().minTempC)
    }

    @Test fun `summary line`() {
        val dry = withWeather(listOf(ride("2026-09-20T15:00:00Z", 60), ride("2026-09-20T17:00:00Z", 30, kind = ExerciseKind.RUN)), hours)
        assertEquals("2 workouts in 30 days", describeLog(dry, 30, TempUnit.F))
        assertEquals("No workouts in the last 30 days", describeLog(emptyList(), 30, TempUnit.F))
        val gym = withWeather(listOf(ride("2026-09-20T15:00:00Z", 60, indoor = true)), hours)
        assertEquals("1 workout in 30 days · all indoors", describeLog(gym, 30, TempUnit.C))
        val gale = hours.map { it.copy(windKmh = 38.0) }
        assertEquals("1 workout in 30 days · windiest 38 km/h", describeLog(withWeather(listOf(ride("2026-09-20T15:00:00Z", 60)), gale), 30, TempUnit.C))
    }

    @Test fun `weeks run Monday to Sunday, newest first, named from today`() {
        val items = withWeather(
            listOf(
                ride("2026-09-20T15:00:00Z", 60), // Sunday, last day of the week of Sep 14
                ride("2026-09-14T07:00:00Z", 30), // Monday of the same week
                ride("2026-09-13T07:00:00Z", 45), // the Sunday before
                ride("2026-08-30T07:00:00Z", 20), // a week straddling the month
            ),
            hours,
        )
        // Seen on the Sunday itself, the newest two are this week; seen on the Tuesday after, they're last week.
        val weeks = groupByWeek(items, LocalDate.of(2026, 9, 20), ZoneOffset.UTC)
        assertEquals(listOf("This week", "Last week", "Aug 24–30"), weeks.map { it.label })
        assertEquals(listOf("Last week", "Sep 7–13", "Aug 24–30"), groupByWeek(items, LocalDate.of(2026, 9, 22), ZoneOffset.UTC).map { it.label })
        assertEquals(listOf(60L, 30L), weeks[0].items.map { it.exercise.duration.toMinutes() })
        assertEquals(90L, weeks[0].duration.toMinutes())
        val straddling = withWeather(listOf(ride("2026-09-02T07:00:00Z", 20)), hours)
        assertEquals("Aug 31 – Sep 6", groupByWeek(straddling, LocalDate.of(2026, 9, 22), ZoneOffset.UTC).single().label)
    }

    @Test fun `durations`() {
        assertEquals("42 min", formatDuration(Duration.ofMinutes(42)))
        assertEquals("1h 05m", formatDuration(Duration.ofMinutes(65)))
        assertEquals("42 minutes", spokenDuration(Duration.ofMinutes(42)))
        assertEquals("1 hour 5 minutes", spokenDuration(Duration.ofMinutes(65)))
        assertEquals("2 hours", spokenDuration(Duration.ofMinutes(120)))
        assertEquals("0 minutes", spokenDuration(Duration.ZERO))
    }

    @Test fun `a workout recorded in another time zone gets no weather`() {
        val tokyo = ride("2026-09-20T15:00:00Z", 60).copy(zoneOffsetSeconds = 9 * 3600)
        val home = ride("2026-09-20T17:00:00Z", 60).copy(zoneOffsetSeconds = -7 * 3600)
        val dstShift = ride("2026-09-20T16:00:00Z", 60).copy(zoneOffsetSeconds = -8 * 3600)
        val items = withWeather(listOf(tokyo, home, dstShift), hours, placeOffsetSeconds = -7 * 3600)
        assertEquals(listOf(true, false, false), items.map { it.elsewhere })
        assertNull(items[0].minTempC)
        assertEquals(14.0, items[1].minTempC!!, 0.001)
    }
}
