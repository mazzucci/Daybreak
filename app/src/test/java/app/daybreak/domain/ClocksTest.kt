package app.daybreak.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ClocksTest {
    private val la = ZoneId.of("America/Los_Angeles")
    private val bucharest = ZoneId.of("Europe/Bucharest")

    @Test fun `a clock reads its time, day and offset against yours`() {
        // 2:42 PM in Los Angeles is 12:42 AM tomorrow in Bucharest, ten hours ahead (both on summer time).
        val r = readClock(Instant.parse("2026-09-28T21:42:00Z"), la, bucharest)
        assertEquals(LocalTime.of(0, 42), r.time.toLocalTime())
        assertEquals("Tomorrow", r.day)
        assertEquals("+10 h", r.offset)
        assertEquals("UTC+3", r.utc)
        assertTrue(r.night)
        val back = readClock(Instant.parse("2026-09-28T21:42:00Z"), bucharest, la)
        assertEquals("Yesterday", back.day)
        assertEquals("−10 h", back.offset)
        assertEquals("same time as you", readClock(Instant.parse("2026-09-28T21:42:00Z"), la, la).offset)
    }

    @Test fun `offsets show halves and quarters, and UTC shows minutes`() {
        assertEquals("+5½ h", formatOffset(5 * 3600 + 1800))
        assertEquals("+5¾ h", formatOffset(5 * 3600 + 2700))
        assertEquals("−3½ h", formatOffset(-(3 * 3600 + 1800)))
        assertEquals("UTC+5:30", formatUtc(5 * 3600 + 1800))
        assertEquals("UTC−7", formatUtc(-7 * 3600))
        assertEquals("UTC", formatUtc(0))
    }

    @Test fun `converting follows each place's daylight saving on that date`() {
        // 28 October 2026: Europe is back on winter time (25 Oct) but the US isn't until 1 November, so the gap is
        // nine hours for that week.
        assertEquals(LocalTime.of(21, 0), convertTime(LocalTime.NOON, LocalDate.of(2026, 10, 28), la, bucharest).toLocalTime())
        // In summer the gap is ten hours.
        assertEquals(LocalTime.of(22, 0), convertTime(LocalTime.NOON, LocalDate.of(2026, 7, 1), la, bucharest).toLocalTime())
        // A mid-January morning in Bucharest is the evening before in Los Angeles.
        val jan = convertTime(LocalTime.of(8, 0), LocalDate.of(2027, 1, 15), bucharest, la)
        assertEquals(LocalDate.of(2027, 1, 14), jan.toLocalDate())
        assertEquals(LocalTime.of(22, 0), jan.toLocalTime())
    }

    @Test fun `city names come from the zone id`() {
        assertEquals("Los Angeles", cityOf(la))
        assertEquals("Buenos Aires", cityOf(ZoneId.of("America/Argentina/Buenos_Aires")))
    }

    @Test fun `a place without a time zone can't be a clock`() {
        val place = Place("geo:1", "Nowhere", latitude = 0.0, longitude = 0.0)
        assertNull(Clock.of(place))
        assertEquals("Europe/Bucharest", Clock.of(place.copy(zoneId = "Europe/Bucharest"))?.zoneId)
        assertFalse(isNightHour(6))
        assertTrue(isNightHour(18))
    }
}
