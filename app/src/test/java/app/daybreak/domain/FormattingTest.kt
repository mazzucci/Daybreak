package app.daybreak.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class FormattingTest {

    @Test fun `converts celsius to fahrenheit`() {
        assertEquals(32.0, cToF(0.0), 1e-9)
        assertEquals(212.0, cToF(100.0), 1e-9)
        assertEquals(-40.0, cToF(-40.0), 1e-9)
    }

    @Test fun `formats temperatures rounded in either unit`() {
        assertEquals("71°F", formatTemp(21.4, TempUnit.F))
        assertEquals("21°C", formatTemp(21.4, TempUnit.C))
        assertEquals("22°C", formatTemp(21.5, TempUnit.C))
        assertEquals("-3°C", formatTemp(-2.6, TempUnit.C))
        assertEquals("27°", formatDegrees(-2.6, TempUnit.F))
    }

    @Test fun `wind follows the primary unit`() {
        assertEquals("9 mph", formatWind(14.2, TempUnit.F))
        assertEquals("14 km/h", formatWind(14.2, TempUnit.C))
    }

    @Test fun `both units, primary first`() {
        assertEquals("74°F (23°C)", formatBothUnits(23.4, TempUnit.F))
        assertEquals("-12°C (10°F)", formatBothUnits(-12.0, TempUnit.C))
        assertEquals("0°C (31°F)", formatBothUnits(-0.4, TempUnit.C)) // each unit rounds from the raw value
    }

    @Test fun `other unit`() {
        assertEquals(TempUnit.C, TempUnit.F.other())
        assertEquals(TempUnit.F, TempUnit.C.other())
    }

    @Test fun `hour labels`() {
        assertEquals("3 PM", formatHour(LocalDateTime.of(2026, 1, 1, 15, 0), Locale.US))
        assertEquals("12 AM", formatHour(LocalDateTime.of(2026, 1, 1, 0, 0), Locale.US))
    }

    @Test fun `24-hour clock`() {
        assertEquals("18:00", formatHour(LocalDateTime.of(2026, 1, 1, 18, 0), Locale.US, use24Hour = true))
        assertEquals("06:56", formatClock(LocalDateTime.of(2026, 9, 28, 6, 56), Locale.US, use24Hour = true))
    }

    @Test fun `clock times and day labels`() {
        assertEquals("7:02 AM", formatClock(LocalDateTime.of(2026, 9, 28, 7, 2), Locale.US))
        assertEquals("6:56 PM", formatClock(LocalDateTime.of(2026, 9, 28, 18, 56), Locale.US))
        val today = LocalDate.of(2026, 9, 28) // a Monday
        assertEquals("Today", formatDayLabel(today, today, Locale.US))
        assertEquals("Tue", formatDayLabel(today.plusDays(1), today, Locale.US))
        assertEquals("Tuesday", formatDayName(today.plusDays(1), today, Locale.US))
    }

    @Test fun `UV categories follow the WHO bands`() {
        assertEquals("Low", describeUv(2.4))
        assertEquals("Moderate", describeUv(2.6))
        assertEquals("High", describeUv(6.05))
        assertEquals("Very high", describeUv(10.0))
        assertEquals("Extreme", describeUv(11.2))
    }

    @Test fun `describes WMO codes`() {
        assertEquals("Clear sky", describeWeatherCode(0))
        assertEquals("Partly cloudy", describeWeatherCode(2))
        assertEquals("Fog", describeWeatherCode(48))
        assertEquals("Rain", describeWeatherCode(63))
        assertEquals("Snow showers", describeWeatherCode(86))
        assertEquals("Thunderstorm with hail", describeWeatherCode(99))
        assertEquals("Unknown", describeWeatherCode(42))
    }

    @Test fun `feels-like shows from 3 degrees away, in the unit shown`() {
        assertNull(feelsLikeWorthShowing(20.0, 19.0, TempUnit.C)) // 2°C
        assertEquals(17.0, feelsLikeWorthShowing(20.0, 17.0, TempUnit.C)!!, 0.0)
        assertEquals(18.3, feelsLikeWorthShowing(20.0, 18.3, TempUnit.F)!!, 0.0) // 68°F vs 65°F
        assertNull(feelsLikeWorthShowing(20.0, 18.3, TempUnit.C)) // 20°C vs 18°C
        assertNull(feelsLikeWorthShowing(20.0, null, TempUnit.F))
    }

    @Test fun `compass points and wind direction words`() {
        assertEquals("N", compassPoint(0.0))
        assertEquals("N", compassPoint(22.4))
        assertEquals("NE", compassPoint(22.5))
        assertEquals("SW", compassPoint(225.0))
        assertEquals("NW", compassPoint(-45.0))
        assertEquals("N", compassPoint(359.0))
        assertEquals("from the SW", formatWindFrom(230.0))
        assertEquals("from the southwest", formatWindFromSpoken(230.0))
    }

    @Test fun `updated lines count from our fetch time`() {
        val at = java.time.Instant.parse("2026-10-01T12:00:00Z")
        assertEquals("Updated just now", formatUpdated(at, at.plusSeconds(30)))
        assertEquals("Updated 8 min ago", formatUpdated(at, at.plusSeconds(8 * 60)))
        assertEquals("Updated 1 hour ago", formatUpdated(at, at.plusSeconds(61 * 60)))
        assertEquals("Updated 2 hours ago", formatUpdated(at, at.plusSeconds(150 * 60)))
        assertEquals("Updated 3 days ago", formatUpdated(at, at.plusSeconds(3 * 24 * 3600)))
        assertEquals("Updated just now", formatUpdated(at, at.minusSeconds(60))) // a clock that went back
        assertFalse(isStale(at, at.plusSeconds(90 * 60)))
        assertTrue(isStale(at, at.plusSeconds(91 * 60)))
    }
}
