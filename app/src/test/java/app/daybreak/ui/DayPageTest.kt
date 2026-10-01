package app.daybreak.ui

import app.daybreak.TestData
import app.daybreak.TestData.london
import app.daybreak.domain.Precip
import app.daybreak.domain.TempUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DayPageTest {
    private val alps = TestData.alps()
    private val oct2 = LocalDate.of(2026, 10, 2)

    private fun state(content: PageContent, ready: Boolean = true) =
        WeatherUiState(pages = listOf(PageUi(london.id, london, content)), savedPlaces = listOf(london), ready = ready)

    @Test fun `an open day page waits for its forecast after the process was stopped`() {
        // The placeholder state before the ViewModel's first one: no pages yet.
        assertEquals(DayOverlay.Wait, dayOverlay(WeatherUiState(), london.id, oct2))
        assertEquals(DayOverlay.Wait, dayOverlay(state(PageContent.Loading), london.id, oct2))
        assertTrue(dayOverlay(state(PageContent.Loaded(alps, "")), london.id, oct2) is DayOverlay.Show)
    }

    @Test fun `an open day page closes when its page is gone, failed, or past that day`() {
        assertEquals(DayOverlay.Close, dayOverlay(state(PageContent.Loaded(alps, "")), "geo:gone", oct2))
        assertEquals(DayOverlay.Close, dayOverlay(state(PageContent.Failed("offline")), london.id, oct2))
        assertEquals(DayOverlay.Close, dayOverlay(state(PageContent.Loaded(alps, "")), london.id, LocalDate.of(2026, 10, 30)))
        assertEquals(DayOverlay.Close, dayOverlay(state(PageContent.Loaded(alps, "")), london.id, null))
        // A failed refresh keeps the forecast, so the page stays open.
        assertTrue(dayOverlay(state(PageContent.Loaded(alps, "", refreshFailed = true)), london.id, oct2) is DayOverlay.Show)
    }

    @Test fun `the hero pill follows the classifier`() {
        fun pill(day: Int) = rainPill(Precip.dayRain(alps, LocalDate.of(2026, 10, day)), TempUnit.C)
        assertEquals("60% · 4.2 mm" to "60% chance, about 4.2 millimetres", pill(1))
        assertEquals("small chance · up to 2 mm" to "small chance, up to 2 millimetres", pill(4))
        assertEquals("32%" to "32% chance", pill(7)) // a chance with nothing modelled: no amount
        assertEquals("unlikely" to "unlikely", pill(6))
        assertEquals("53% · 19 cm" to "53% chance, about 19 centimetres", pill(8))
    }

    @Test fun `the feels-like pill shows once it's 3 degrees from the high or the low`() {
        val day = alps.day(LocalDate.of(2026, 10, 1))!! // 3.1 to 1.6°C
        assertFalse(feelsWorthAPill(1.6, 0.0, day, TempUnit.C)) // 2° off at each end
        assertTrue(feelsWorthAPill(0.0, 0.0, day, TempUnit.C)) // 3° under the high
        assertTrue(feelsWorthAPill(3.1, -1.4, day, TempUnit.C)) // 3° under the low
        assertTrue(feelsWorthAPill(1.6, 0.0, day, TempUnit.F)) // 35°F against a 38°F high: 3° in Fahrenheit
    }

    @Test fun `the hourly strip shows each hour's own rain`() {
        // The cell for 5 PM shows what's stamped 6 PM: the rain from 5 to 6.
        val f = TestData.forecast()
        val five = f.hourAt(LocalDateTime.of(2026, 9, 28, 17, 0))!!
        assertEquals(60, f.rainDuring(five)!!.precipChance)
        assertEquals("0.6 mm", Precip.hourAmount(f.rainDuring(five)!!, TempUnit.C))
    }
}
