package app.daybreak.domain

import app.daybreak.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TonightSkyTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private val london = ZoneId.of("Europe/London")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun near(expected: String, actual: Instant) =
        assertTrue("$actual vs $expected", Duration.between(Instant.parse(expected), actual).abs() < Duration.ofMinutes(10))

    @Test fun `full and new moons match the published times`() {
        // USNO/timeanddate: 7 Oct 2025 03:47, 26 Sep 2026 16:49, 26 Oct 2026 04:11, 20 Apr 2027 22:27; new 10 Oct 2026 15:50.
        near("2025-10-07T03:47:00Z", fullMoonsAround(Instant.parse("2025-10-01T00:00:00Z")).second)
        near("2026-09-26T16:49:00Z", fullMoonsAround(Instant.parse("2026-09-20T00:00:00Z")).second)
        near("2026-10-26T04:11:00Z", fullMoonsAround(Instant.parse("2026-10-20T00:00:00Z")).second)
        near("2027-04-20T22:27:00Z", fullMoonsAround(Instant.parse("2027-04-15T00:00:00Z")).second)
        assertTrue(moonPhase(Instant.parse("2026-10-10T15:50:00Z")).illumination < 0.001)
    }

    @Test fun `phases are named by how lit the moon is`() {
        assertEquals("First quarter", moonPhase(Instant.parse("2026-10-18T12:00:00Z")).name)
        assertEquals("Waning gibbous", moonPhase(Instant.parse("2026-10-31T12:00:00Z")).name)
        assertEquals("Last quarter", moonPhase(Instant.parse("2026-11-01T20:00:00Z")).name)
        assertFalse(moonPhase(Instant.parse("2026-09-30T12:00:00Z")).waxing)
        assertTrue(moonPhase(Instant.parse("2026-10-14T12:00:00Z")).waxing)
    }

    @Test fun `each year has one Harvest Moon, the one nearest the equinox, and one Hunter's Moon after it`() {
        fun name(t: String, zone: ZoneId = london) = fullMoonName(fullMoonsAround(Instant.parse(t)).second, zone)
        // 2025: 7 September was 15 days before the equinox and 7 October 14.4 days after.
        assertEquals("Corn Moon", name("2025-09-01T00:00:00Z"))
        assertEquals("Harvest Moon", name("2025-10-01T00:00:00Z"))
        assertEquals("Hunter's Moon", name("2025-11-01T00:00:00Z"))
        assertEquals("Harvest Moon", name("2025-10-01T00:00:00Z", tokyo))
        assertEquals("Harvest Moon", name("2026-09-20T00:00:00Z", la))
        assertEquals("Hunter's Moon", name("2026-10-20T00:00:00Z", la))
        assertEquals("Wolf Moon", name("2027-01-15T00:00:00Z"))
        assertNull(fullMoonName(Instant.parse("2026-10-26T04:11:00Z"), la, latitude = -33.9))
    }

    @Test fun `the small hours belong to the night before`() {
        assertEquals(LocalDate.of(2026, 8, 12), nightOf(LocalDateTime.of(2026, 8, 13, 1, 30)))
        assertEquals(LocalDate.of(2026, 8, 13), nightOf(LocalDateTime.of(2026, 8, 13, 21, 0)))
        // At 1:30 AM on the 13th the Perseids are still peaking tonight.
        assertEquals("Perseids", nextMeteorShower(nightOf(LocalDateTime.of(2026, 8, 13, 1, 30)))?.first?.name)
    }

    @Test fun `meteor showers are this week's, and only ones visible from here`() {
        assertEquals("Draconids", nextMeteorShower(LocalDate.of(2026, 10, 2))?.first?.name)
        assertNull(nextMeteorShower(LocalDate.of(2026, 9, 30)))
        assertEquals(LocalDate.of(2027, 1, 3), nextMeteorShower(LocalDate.of(2026, 12, 28))?.second)
        // The Draconids' radiant never rises far over Sydney.
        assertNull(nextMeteorShower(LocalDate.of(2026, 10, 2), latitude = -33.9))
    }

    private fun night(codes: (Int) -> Int, midnightSun: Boolean = false): NightSky? {
        val base = TestData.forecast()
        val start = LocalDateTime.of(2026, 9, 28, 12, 0)
        val day = base.days.first().copy(
            date = start.toLocalDate(),
            sunrise = start.withHour(7),
            sunset = if (midnightSun) start.withHour(7).plusHours(24) else start.withHour(19),
        )
        return nightSky(
            base.copy(
                current = base.current.copy(time = start.withHour(17)),
                days = listOf(day, day.copy(date = day.date.plusDays(1))),
                hours = (0 until 30).map { i -> start.plusHours(i.toLong()).let { t -> HourForecast(t, 15.0, 0, codes(t.hour), 5.0, 8.0) } },
            ),
            Instant.parse("2026-09-28T17:00:00Z"), // the fixture's offset is 0
        )
    }

    @Test fun `tonight is clear, clearing for good, or cloudy, and there's no night under the midnight sun`() {
        assertTrue(night({ 0 })!!.clear)
        assertTrue(night({ 3 })!!.cloudy)
        // Cloudy 7 PM to midnight, clear after: half the night, clearing at midnight.
        val clearing = night({ if (it in 19..23) 3 else 1 })!!
        assertFalse(clearing.clear)
        assertEquals(LocalDateTime.of(2026, 9, 29, 0, 0), clearing.clearFrom)
        // Clear for a moment, then cloudy again: not "clearing".
        assertNull(night({ if (it == 22) 0 else 3 })!!.clearFrom)
        assertTrue(night({ 0 }, midnightSun = true)!!.noDark)
    }

    @Test fun `the card's words, with full moon on the night it falls`() {
        val newMoon = describeSky(Instant.parse("2026-10-11T03:00:00Z"), la, null, use24Hour = false)
        assertEquals("New moon · a dark sky", newMoon.phase)
        assertEquals("Full moon Oct 25: the Hunter's Moon", newMoon.moon)
        assertNull(newMoon.meteors)
        assertNull(newMoon.tonight)
        // The October full moon is at 9:11 PM on the 25th in Los Angeles: that evening is its night.
        val full = describeSky(Instant.parse("2026-10-26T03:00:00Z"), la, null)
        assertEquals("It's the Hunter's Moon tonight", full.moon)
        assertTrue(full.phase.startsWith("Full moon"))
        // The night before, 98% lit, is still "gibbous", and full is tomorrow.
        val eve = describeSky(Instant.parse("2026-10-25T03:00:00Z"), la, null)
        assertTrue(eve.phase, eve.phase.startsWith("Waxing gibbous"))
        assertEquals("Full moon tomorrow: the Hunter's Moon", eve.moon)
        assertEquals("Just past full: the Hunter's Moon", describeSky(Instant.parse("2026-10-28T03:00:00Z"), la, null).moon)
        // The Orionids' night has a waxing moon, which sets before the small hours: the count stands.
        assertEquals("Orionids peak Wednesday · up to 20 an hour", describeSky(Instant.parse("2026-10-19T03:00:00Z"), la, null).meteors)
        // South of the equator the northern names are dropped.
        assertEquals("Full moon tonight", describeSky(Instant.parse("2026-10-26T03:00:00Z"), la, null, latitude = -33.9).moon)
        assertTrue(full.spoken.contains("Full moon, "))
    }
}
