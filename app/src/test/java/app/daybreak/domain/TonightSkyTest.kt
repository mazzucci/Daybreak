package app.daybreak.domain

import app.daybreak.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TonightSkyTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private fun at(date: String) = Instant.parse("${date}T12:00:00Z")

    @Test fun `phases match the real moon within a day`() {
        // Full moon 26 September 2026, new moon 10 October 2026, first quarter around 18 October.
        assertEquals("Full moon", moonPhase(at("2026-09-26")).name)
        assertTrue(moonPhase(at("2026-09-26")).illumination > 0.99)
        assertEquals("New moon", moonPhase(at("2026-10-10")).name)
        assertTrue(moonPhase(at("2026-10-10")).illumination < 0.02)
        assertEquals("First quarter", moonPhase(at("2026-10-18")).name)
        assertEquals("Waning gibbous", moonPhase(at("2026-09-30")).name)
        assertFalse(moonPhase(at("2026-09-30")).waxing)
        assertTrue(moonPhase(at("2026-10-14")).waxing)
        // Five days after full it's still gibbous (about two-thirds lit), not yet a quarter.
        assertEquals("Waning gibbous", moonPhase(at("2026-10-31")).name)
        assertEquals("Last quarter", moonPhase(at("2026-11-02")).name)
    }

    @Test fun `the next full moon and its old name`() {
        // The October full moon is at 04:12 UTC on the 26th: the evening of the 25th in Los Angeles.
        assertEquals(LocalDate.of(2026, 10, 25), nextFullMoon(at("2026-09-30"), la))
        assertEquals(LocalDate.of(2026, 10, 26), nextFullMoon(at("2026-09-30"), ZoneId.of("Europe/Bucharest")))
        assertEquals("Harvest Moon", fullMoonName(LocalDate.of(2026, 9, 26)))
        assertEquals("Hunter's Moon", fullMoonName(LocalDate.of(2026, 10, 26)))
        assertEquals("Wolf Moon", fullMoonName(LocalDate.of(2027, 1, 22)))
        // A full moon early in September isn't the Harvest Moon when October's is nearer the equinox.
        assertEquals("Corn Moon", fullMoonName(LocalDate.of(2025, 9, 7)))
    }

    @Test fun `meteor showers show when they're close, including over New Year`() {
        assertEquals("Orionids", nextMeteorShower(LocalDate.of(2026, 10, 10))?.first?.name)
        assertEquals("Draconids", nextMeteorShower(LocalDate.of(2026, 9, 30))?.first?.name)
        assertNull(nextMeteorShower(LocalDate.of(2026, 6, 1)))
        assertEquals(LocalDate.of(2027, 1, 3), nextMeteorShower(LocalDate.of(2026, 12, 28))?.second)
    }

    @Test fun `tonight is clear, clearing or cloudy from the forecast`() {
        val base = TestData.forecast()
        val now = LocalDateTime.of(2026, 9, 28, 17, 0)
        fun sky(code: (Int) -> Int) = nightSky(
            base.copy(
                current = base.current.copy(time = now),
                days = base.days.map { it.copy(sunset = now.withHour(19)) },
                hours = (0 until 24).map { i ->
                    val t = now.plusHours(i.toLong())
                    HourForecast(t, 15.0, 0, code(t.hour), 5.0, 8.0)
                },
            ),
        )
        assertTrue(sky { 0 }!!.clear)
        assertTrue(sky { 3 }!!.cloudy)
        val clearing = sky { if (it in 19..21) 3 else 1 }!!
        assertFalse(clearing.clear)
        assertEquals(LocalDateTime.of(2026, 9, 28, 22, 0), clearing.clearFrom)
    }

    @Test fun `the card's words`() {
        val copy = describeSky(Instant.parse("2026-10-10T20:00:00Z"), la, null, use24Hour = false)
        assertEquals("New moon · a dark sky", copy.phase)
        assertEquals("Full moon in 15 days, the Hunter's Moon", copy.moon)
        // The moon is three-quarters lit on the Orionids' night.
        assertEquals("Orionids peak in 11 days · up to 20 an hour, though the moon will be bright", copy.meteors)
        assertNull(copy.tonight)
        val full = describeSky(Instant.parse("2026-09-26T20:00:00Z"), la, null)
        assertEquals("It's the Harvest Moon", full.moon)
        // The evening after the exact moment is still the full moon's night.
        assertEquals("It's the Hunter's Moon", describeSky(Instant.parse("2026-10-26T20:00:00Z"), la, null).moon)
        assertEquals("Draconids peak in 12 days · up to 10 an hour", full.meteors)
    }
}
