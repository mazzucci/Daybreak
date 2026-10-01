package app.daybreak.domain

import app.daybreak.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

class PrecipTest {
    private val date = LocalDate.of(2026, 9, 28)
    private val alps = TestData.alps()

    private fun day(chance: Int, sumMm: Double?, code: Int = 61, hours: Double? = null, snowCm: Double? = null) =
        DaySummary(date, 20.0, 10.0, chance, code, precipSumMm = sumMm, precipHours = hours, snowSumCm = snowCm)

    private fun hour(at: Int, mm: Double?, chance: Int = 50, snowCm: Double? = null) =
        HourForecast(date.atTime(at, 0), 10.0, chance, 61, precipMm = mm, snowCm = snowCm)

    /** A day of hours with [mm] at the given hours and nothing elsewhere. */
    private fun hoursWith(vararg mm: Pair<Int, Double>) = (0 until 24).map { h -> hour(h, mm.toMap()[h] ?: 0.0) }

    // --- Thresholds ---------------------------------------------------------------------------------

    @Test fun `chances are shown from 10 percent an hour and 20 a day, blue from 40`() {
        assertFalse(Precip.showHourChance(9))
        assertTrue(Precip.showHourChance(10))
        assertFalse(Precip.showDayChance(19))
        assertTrue(Precip.showDayChance(20))
        assertFalse(Precip.highlightChance(39))
        assertTrue(Precip.highlightChance(40))
    }

    @Test fun `chance words`() {
        assertEquals(Likelihood.UNLIKELY, Precip.likelihood(19))
        assertEquals(Likelihood.SMALL, Precip.likelihood(20))
        assertEquals(Likelihood.SMALL, Precip.likelihood(39))
        assertEquals(Likelihood.POSSIBLE, Precip.likelihood(40))
        assertEquals(Likelihood.POSSIBLE, Precip.likelihood(69))
        assertEquals(Likelihood.LIKELY, Precip.likelihood(70))
    }

    @Test fun `amount and intensity words`() {
        assertEquals("a few drops", Precip.amountWord(0.6))
        assertEquals("light", Precip.amountWord(3.0))
        assertEquals("a proper soaking", Precip.amountWord(12.0))
        assertEquals("heavy rain", Precip.amountWord(22.0))
        assertEquals(Intensity.LIGHT, Precip.intensity(0.3))
        assertEquals(Intensity.MODERATE, Precip.intensity(2.0))
        assertEquals(Intensity.HEAVY, Precip.intensity(6.0))
    }

    @Test fun `wet, dry and mixed days need both chance and amount`() {
        assertEquals(DayKind.WET, Precip.classify(day(60, 3.0)))
        assertEquals(DayKind.DRY, Precip.classify(day(15, 5.0))) // a big total nobody expects
        assertEquals(DayKind.DRY, Precip.classify(day(60, 0.1))) // a likely trace
        assertEquals(DayKind.MIXED, Precip.classify(day(30, 3.0))) // a gamble, not a spell
        assertEquals(DayKind.MIXED, Precip.classify(day(60, 0.5)))
    }

    @Test fun `snow when there's enough and it's most of the water`() {
        assertTrue(Precip.isSnowDay(day(70, 4.3, snowCm = 3.0)))
        assertFalse(Precip.isSnowDay(day(70, 0.6, snowCm = 0.4))) // too little to call
        assertFalse(Precip.isSnowDay(day(70, 10.0, snowCm = 1.0))) // mostly rain
        assertFalse(Precip.isSnowDay(day(70, 4.0, snowCm = null)))
        assertTrue(Precip.isSnowHour(hour(3, 0.3, snowCm = 0.21)))
        assertFalse(Precip.isSnowHour(hour(3, 0.1, snowCm = 0.05)))
    }

    // --- Amounts as text ----------------------------------------------------------------------------

    @Test fun `rain in mm with C and in inches with F`() {
        assertEquals("0.6 mm", Precip.formatRain(0.6, TempUnit.C))
        assertEquals("4 mm", Precip.formatRain(4.0, TempUnit.C))
        assertEquals("4.3 mm", Precip.formatRain(4.3, TempUnit.C))
        assertEquals("10 mm", Precip.formatRain(9.96, TempUnit.C))
        assertEquals("18 mm", Precip.formatRain(18.4, TempUnit.C))
        assertEquals("0.02 in", Precip.formatRain(0.5, TempUnit.F))
        assertEquals("0.01 in", Precip.formatRain(0.1, TempUnit.F)) // never "0.00"
        assertEquals("0.1 in", Precip.formatRain(3.8, TempUnit.F))
        assertEquals("0.7 in", Precip.formatRain(17.8, TempUnit.F))
        assertEquals("1 in", Precip.formatRain(25.4, TempUnit.F))
        assertEquals("1.2 in", Precip.formatRain(30.5, TempUnit.F))
    }

    @Test fun `snow in cm with C and in inches with F`() {
        assertEquals("0.4 cm", Precip.formatSnow(0.4, TempUnit.C))
        assertEquals("3 cm", Precip.formatSnow(3.0, TempUnit.C))
        assertEquals("19 cm", Precip.formatSnow(19.32, TempUnit.C))
        assertEquals("1.2 in", Precip.formatSnow(3.0, TempUnit.F))
        assertEquals("0.2 in", Precip.formatSnow(0.4, TempUnit.F))
    }

    @Test fun `prose and spoken amounts`() {
        assertEquals("6.5 mm", Precip.proseRain(6.5, TempUnit.C))
        assertEquals("0.3 inches", Precip.proseRain(6.5, TempUnit.F))
        assertEquals("1 inch", Precip.proseRain(25.4, TempUnit.F))
        assertEquals("about 0.6 millimetres", Precip.spokenRain(0.6, TempUnit.C))
        assertEquals("about 0.02 inches", Precip.spokenRain(0.5, TempUnit.F))
        assertEquals("about 3 centimetres of snow", Precip.spokenSnow(3.0, TempUnit.C))
    }

    @Test fun `an hour's amount shows from 0 point 1 mm, as snow when it's snow`() {
        assertNull(Precip.hourAmount(hour(3, 0.05), TempUnit.C))
        assertNull(Precip.hourAmount(hour(3, null), TempUnit.C)) // an older response without amounts
        assertEquals("0.1 mm", Precip.hourAmount(hour(3, 0.1), TempUnit.C))
        assertEquals("1.8 mm", Precip.hourAmount(hour(3, 1.8), TempUnit.C))
        assertEquals("0.07 in", Precip.hourAmount(hour(3, 1.8), TempUnit.F))
        assertEquals("0.4 cm", Precip.hourAmount(hour(3, 0.6, snowCm = 0.4), TempUnit.C))
        assertEquals("about 1.8 millimetres", Precip.hourAmountSpoken(hour(3, 1.8), TempUnit.C))
    }

    @Test fun `a day's total shows from 0 point 5 mm, snow days in cm of snow`() {
        assertNull(Precip.dayTotal(day(60, 0.4), TempUnit.C))
        assertNull(Precip.dayTotal(day(60, null), TempUnit.C))
        assertEquals("4 mm", Precip.dayTotal(day(60, 4.0), TempUnit.C))
        assertEquals("0.2 in", Precip.dayTotal(day(60, 4.0), TempUnit.F))
        assertEquals("3 cm snow", Precip.dayTotal(day(70, 4.3, snowCm = 3.0), TempUnit.C))
        assertEquals("1.2 in snow", Precip.dayTotal(day(70, 4.3, snowCm = 3.0), TempUnit.F))
        assertEquals("3 cm", Precip.dayAmount(day(70, 4.3, snowCm = 3.0), TempUnit.C))
        assertEquals("Snow", Precip.noun(day(70, 4.3, snowCm = 3.0)))
        assertEquals("about 4 millimetres", Precip.dayTotalSpoken(day(60, 4.0), TempUnit.C))
    }

    // --- The day page -------------------------------------------------------------------------------

    @Test fun `verdict pairs the chance word with the total and hours`() {
        assertEquals("Rain likely · about 12 mm over 6 hours", Precip.verdict(day(75, 12.0, hours = 6.0), TempUnit.C))
        assertEquals("Rain likely · about 0.5 inches over 1 hour", Precip.verdict(day(75, 12.0, hours = 1.0), TempUnit.F))
        assertEquals("Showers possible · a few drops", Precip.verdict(day(50, 0.6, code = 80), TempUnit.C))
        assertEquals("A small chance of rain · about 3 mm", Precip.verdict(day(30, 3.0), TempUnit.C))
        assertEquals("Thunderstorms possible · about 7 mm over 3 hours", Precip.verdict(day(45, 7.0, code = 95, hours = 3.0), TempUnit.C))
        assertEquals("Snow likely · about 3 cm", Precip.verdict(day(80, 4.3, code = 73, hours = 5.0, snowCm = 3.0), TempUnit.C))
        assertEquals("Rain unlikely", Precip.verdict(day(10, 0.0), TempUnit.C))
        assertEquals("Rain possible", Precip.verdict(day(45, 0.0), TempUnit.C)) // a chance with no amount says just that
        assertEquals("Rain possible", Precip.verdict(day(45, null), TempUnit.C))
    }

    @Test fun `verdicts on a real forecast`() {
        assertEquals("Showers possible · about 12 mm over 5 hours", Precip.verdict(alps.day(LocalDate.of(2026, 10, 2))!!, TempUnit.C))
        assertEquals("Snow possible · about 7.6 inches", Precip.verdict(alps.day(LocalDate.of(2026, 10, 8))!!, TempUnit.F))
        assertEquals("Rain unlikely", Precip.verdict(alps.day(LocalDate.of(2026, 10, 6))!!, TempUnit.C))
    }

    @Test fun `timing finds the part of the day and a standout hour`() {
        val afternoon = Precip.timing(hoursWith(13 to 0.4, 14 to 0.6, 15 to 0.8, 16 to 2.5, 17 to 0.5))!!
        assertEquals(TimingShape.MOSTLY, afternoon.shape)
        assertEquals(listOf(PartOfDay.AFTERNOON), afternoon.parts)
        assertEquals(date.atTime(16, 0), afternoon.peak)
        assertEquals("Mostly in the afternoon, heaviest around 4 PM.", Precip.timingSentence(afternoon, Locale.US))

        val spread = Precip.timing(hoursWith(2 to 1.0, 8 to 1.0, 14 to 1.0, 20 to 1.0))!!
        assertEquals("On and off all day.", Precip.timingSentence(spread, Locale.US))

        val two = Precip.timing(hoursWith(9 to 1.0, 10 to 1.0, 13 to 1.0, 14 to 1.0))!!
        assertEquals("Mostly in the morning and afternoon.", Precip.timingSentence(two, Locale.US))
        assertEquals("mostly this morning and afternoon", Precip.timingToday(two))

        val nightAndMorning = Precip.timing(hoursWith(4 to 1.0, 5 to 1.0, 7 to 1.0, 8 to 1.0))!!
        assertEquals("Mostly overnight and in the morning.", Precip.timingSentence(nightAndMorning, Locale.US))

        assertNull(Precip.timing(hoursWith(10 to 0.05)))
        assertNull(Precip.timing(hoursWith()))
    }

    @Test fun `a single wet hour or a tiny total has no heaviest`() {
        assertNull(Precip.timing(hoursWith(15 to 3.0))!!.peak)
        assertNull(Precip.timing(hoursWith(15 to 0.4, 16 to 0.2))!!.peak)
    }

    @Test fun `timing on a real forecast`() {
        fun sentence(d: Int) = Precip.timingSentence(Precip.dayRain(alps, LocalDate.of(2026, 10, d)).timing!!, Locale.US)
        assertEquals("Mostly in the evening.", sentence(1))
        assertEquals("Overnight, clearing by morning.", sentence(2))
        assertEquals("Mostly in the afternoon and evening.", sentence(8))
        assertNull(Precip.dayRain(alps, LocalDate.of(2026, 10, 6)).timing)
    }

    @Test fun `daytime runs sunrise to sunset and overnight to the next sunrise`() {
        val rain = Precip.dayRain(alps, LocalDate.of(2026, 10, 1))
        val day = rain.daytime!!
        // Sunrise 7:26 and sunset 19:08 round to the hour.
        assertEquals(LocalDateTime.of(2026, 10, 1, 7, 0), day.start)
        assertEquals(LocalDateTime.of(2026, 10, 1, 19, 0), day.end)
        assertEquals(60, day.chance)
        assertEquals(1.9, day.totalMm, 0.001)
        assertEquals(2, day.wetHours)
        assertEquals("60% · 1.9 mm · 2 h", day.describe(TempUnit.C))
        assertFalse(day.dry)

        val night = rain.overnight!!
        assertEquals(LocalDateTime.of(2026, 10, 2, 7, 0), night.end) // the next sunrise, 7:27
        assertEquals(58, night.chance)
        assertEquals(14.4, night.totalMm, 0.001)
        assertEquals(10, night.wetHours)
        assertEquals("58% · 14 mm · 10 h", night.describe(TempUnit.C))
        assertEquals("58 percent chance, about 0.6 inches, over 10 hours", night.spoken(TempUnit.F))

        assertTrue(Precip.dayRain(alps, LocalDate.of(2026, 10, 6)).daytime!!.dry)
        assertTrue(rain.hasAmounts)
        assertEquals(24, rain.hours.size)
    }

    @Test fun `a snowy night says snow, and the last day has no overnight`() {
        val snowy = Precip.dayRain(alps, LocalDate.of(2026, 10, 8)).overnight!!
        assertTrue(snowy.snow)
        assertTrue(snowy.describe(TempUnit.C), snowy.describe(TempUnit.C).contains("cm snow"))
        assertNull(Precip.dayRain(alps, LocalDate.of(2026, 10, 10)).overnight)
    }

    @Test fun `without sun times the day runs 7 to 7`() {
        val noSun = alps.copy(days = alps.days.map { it.copy(sunrise = null, sunset = null) })
        val rain = Precip.dayRain(noSun, LocalDate.of(2026, 10, 1))
        assertEquals(LocalDateTime.of(2026, 10, 1, 7, 0), rain.daytime!!.start)
        assertEquals(LocalDateTime.of(2026, 10, 1, 19, 0), rain.daytime!!.end)
        assertEquals(LocalDateTime.of(2026, 10, 2, 7, 0), rain.overnight!!.end)
    }

    @Test fun `an older response without amounts has no chart and no timing`() {
        val bare = alps.copy(hours = alps.hours.map { it.copy(precipMm = null, snowCm = null) })
        val rain = Precip.dayRain(bare, LocalDate.of(2026, 10, 2))
        assertFalse(rain.hasAmounts)
        assertNull(rain.timing)
        assertTrue(rain.overnight!!.dry)
    }
}
