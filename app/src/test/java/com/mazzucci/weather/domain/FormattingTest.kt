package com.mazzucci.weather.domain

import org.junit.Assert.assertEquals
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

    @Test fun `other unit`() {
        assertEquals(TempUnit.C, TempUnit.F.other())
        assertEquals(TempUnit.F, TempUnit.C.other())
    }

    @Test fun `hour labels`() {
        assertEquals("3 PM", formatHour(LocalDateTime.of(2026, 1, 1, 15, 0), Locale.US))
        assertEquals("12 AM", formatHour(LocalDateTime.of(2026, 1, 1, 0, 0), Locale.US))
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
}
