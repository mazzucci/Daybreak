package app.daybreak.domain

import app.daybreak.TestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PrecipTest {
    private val date = LocalDate.of(2026, 9, 28)
    private val alps = TestData.alps()
    private fun alpsDay(d: Int) = Precip.dayRain(alps, LocalDate.of(2026, 10, d))

    private fun day(chance: Int, sumMm: Double?, code: Int = 61, hours: Double? = null, snowCm: Double? = null) =
        DaySummary(date, 20.0, 10.0, chance, code, precipSumMm = sumMm, precipHours = hours, snowSumCm = snowCm)

    /** A day with only its daily figures (no hourly data), as the verdict sees it. */
    private fun verdictOf(day: DaySummary, unit: TempUnit = TempUnit.C): String =
        Precip.verdict(Precip.dayRain(Forecast(TestData.forecast().current, listOf(day), emptyList()), day.date), unit)

    private fun hour(at: Int, mm: Double?, chance: Int = 50, snowCm: Double? = null) =
        HourForecast(date.atTime(at, 0), 10.0, chance, 61, precipMm = mm, snowCm = snowCm)

    /**
     * [date] and the day after, hour by hour, with [mm] stamped at the given hours of [date] (at [chance]) and nothing
     * elsewhere (at 5%); sun 7:02 to 18:56 unless [sunrise] and [sunset] say otherwise.
     */
    private fun forecastWith(
        vararg mm: Pair<Int, Double>,
        chance: Int = 50,
        snowCm: Map<Int, Double> = emptyMap(),
        sunrise: (LocalDate) -> LocalDateTime? = { it.atTime(7, 2) },
        sunset: (LocalDate) -> LocalDateTime? = { it.atTime(18, 56) },
    ): Forecast {
        val wet = mm.toMap()
        val hours = (0 until 48).map { i ->
            val wetHere = i < 24 && i in wet
            HourForecast(
                date.atStartOfDay().plusHours(i.toLong()), 10.0, if (wetHere) chance else 5, 61,
                precipMm = if (wetHere) wet.getValue(i) else 0.0,
                snowCm = if (i < 24) snowCm[i] ?: 0.0 else 0.0,
            )
        }
        val days = listOf(date, date.plusDays(1)).map { d ->
            DaySummary(d, 12.0, 6.0, hours.filter { it.time.toLocalDate() == d }.maxOf { it.precipChance }, 61, sunrise(d), sunset(d))
        }
        return Forecast(CurrentConditions(date.atTime(6, 30), 8.0, 7.0, 80, 10.0, 61), days, hours)
    }

    // --- Thresholds and the one classifier ----------------------------------------------------------

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

    @Test fun `one classifier judges chance and amount together`() {
        assertEquals(RainCall(DayKind.WET, true), Precip.classify(60, 3.0)) // a wet day, for the outlook
        assertEquals(RainCall(DayKind.MIXED, true), Precip.classify(30, 3.0)) // a gamble, not a spell
        assertEquals(RainCall(DayKind.MIXED, true), Precip.classify(60, 0.5))
        // A real modelled amount counts at a low chance; a trace doesn't.
        assertEquals(RainCall(DayKind.MIXED, true), Precip.classify(15, 2.4))
        assertEquals(RainCall(DayKind.DRY, false), Precip.classify(15, 0.6))
        // A real chance stays possible without a modelled amount, but has no amount to show.
        assertEquals(RainCall(DayKind.MIXED, false), Precip.classify(60, 0.0))
        assertEquals(RainCall(DayKind.MIXED, false), Precip.classify(25, 0.05))
        assertEquals(RainCall(DayKind.MIXED, false), Precip.classify(45, null))
        assertEquals(RainCall(DayKind.DRY, false), Precip.classify(5, null))
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

    @Test fun `rain in mm with C`() {
        assertEquals("0.6 mm", Precip.formatRain(0.6, TempUnit.C))
        assertEquals("4 mm", Precip.formatRain(4.0, TempUnit.C))
        assertEquals("4.3 mm", Precip.formatRain(4.3, TempUnit.C))
        assertEquals("10 mm", Precip.formatRain(9.96, TempUnit.C))
        assertEquals("18 mm", Precip.formatRain(18.4, TempUnit.C))
        assertEquals("2 mm", Precip.formatRain(2.4, TempUnit.C, rough = true))
        assertEquals("1 mm", Precip.formatRain(0.6, TempUnit.C, rough = true))
    }

    @Test fun `inches take two decimals under an inch and one from there, never 0 point 00`() {
        assertEquals("0.02 in", Precip.formatRain(0.5, TempUnit.F))
        assertEquals("0.01 in", Precip.formatRain(0.1, TempUnit.F))
        assertEquals("0.15 in", Precip.formatRain(3.8, TempUnit.F))
        assertEquals("0.26 in", Precip.formatRain(6.6, TempUnit.F))
        assertEquals("0.1 in", Precip.formatRain(2.54, TempUnit.F)) // 0.10: the trailing zero goes
        assertEquals("0.7 in", Precip.formatRain(17.8, TempUnit.F))
        assertEquals("0.99 in", Precip.formatRain(25.2, TempUnit.F))
        assertEquals("1 in", Precip.formatRain(25.3, TempUnit.F))
        assertEquals("1 in", Precip.formatRain(25.4, TempUnit.F))
        assertEquals("1.2 in", Precip.formatRain(30.5, TempUnit.F))
        assertEquals("3 in", Precip.formatRain(76.2, TempUnit.F))
        assertEquals("0 in", Precip.formatRain(0.0, TempUnit.F))
    }

    @Test fun `snow in cm with C and in inches with F`() {
        assertEquals("0.4 cm", Precip.formatSnow(0.4, TempUnit.C))
        assertEquals("3 cm", Precip.formatSnow(3.0, TempUnit.C))
        assertEquals("19 cm", Precip.formatSnow(19.32, TempUnit.C))
        assertEquals("1.2 in", Precip.formatSnow(3.0, TempUnit.F))
        assertEquals("0.16 in", Precip.formatSnow(0.4, TempUnit.F))
    }

    @Test fun `prose and spoken amounts`() {
        assertEquals("6.5 mm", Precip.proseRain(6.5, TempUnit.C))
        assertEquals("0.26 inches", Precip.proseRain(6.5, TempUnit.F))
        assertEquals("1 inch", Precip.proseRain(25.4, TempUnit.F))
        assertEquals("0.6 millimetres", Precip.spokenRain(0.6, TempUnit.C))
        assertEquals("0.02 inches", Precip.spokenRain(0.5, TempUnit.F))
        assertEquals("3 centimetres of snow", Precip.spokenSnow(3.0, TempUnit.C))
    }

    @Test fun `an hour's amount follows the classifier, as snow when it's snow`() {
        assertNull(Precip.hourAmount(hour(3, 0.05), TempUnit.C))
        assertNull(Precip.hourAmount(hour(3, null), TempUnit.C)) // a model without amounts
        assertEquals("0.1 mm", Precip.hourAmount(hour(3, 0.1), TempUnit.C))
        assertEquals("1.8 mm", Precip.hourAmount(hour(3, 1.8), TempUnit.C))
        assertEquals("0.07 in", Precip.hourAmount(hour(3, 1.8), TempUnit.F))
        assertEquals("0.4 cm", Precip.hourAmount(hour(3, 0.6, snowCm = 0.4), TempUnit.C))
        assertNull(Precip.hourAmount(hour(3, 0.4, chance = 5), TempUnit.C)) // a trace nobody expects
        assertEquals("1.5 mm", Precip.hourAmount(hour(3, 1.5, chance = 5), TempUnit.C)) // a real amount
        assertEquals("about 1.8 millimetres", Precip.hourAmountSpoken(hour(3, 1.8), TempUnit.C))
    }

    @Test fun `the hourly strip's hour gets the values stamped at its end`() {
        val f = forecastWith(15 to 2.0)
        val threePm = f.hourAt(date.atTime(14, 0))!!
        assertEquals(date.atTime(15, 0), f.rainDuring(threePm)!!.time)
        assertEquals(2.0, f.rainDuring(threePm)!!.precipMm!!, 0.0)
        assertNull(f.rainDuring(f.hours.last())) // past the end of the data
    }

    // --- Verdicts -----------------------------------------------------------------------------------

    @Test fun `verdict pairs the chance word with the total and hours`() {
        assertEquals("Rain likely · about 12 mm over 6 hours", verdictOf(day(75, 12.0, hours = 6.0)))
        assertEquals("Rain likely · about 0.47 inches over 1 hour", verdictOf(day(75, 12.0, hours = 1.0), TempUnit.F))
        assertEquals("Showers possible · a few drops", verdictOf(day(50, 0.6, code = 80)))
        assertEquals("A small chance of rain · about 3 mm", verdictOf(day(30, 3.0)))
        assertEquals("Thunderstorms possible · about 7 mm over 3 hours", verdictOf(day(45, 7.0, code = 95, hours = 3.0)))
        assertEquals("Snow likely · about 3 cm over 5 hours", verdictOf(day(80, 4.3, code = 73, hours = 5.0, snowCm = 3.0)))
        assertEquals("Rain unlikely", verdictOf(day(10, 0.0)))
        assertEquals("Rain possible", verdictOf(day(45, 0.0))) // a chance with no amount says just that
        assertEquals("Rain possible", verdictOf(day(45, null)))
    }

    @Test fun `a low chance with a real amount is a small chance, up to a rounded amount`() {
        assertEquals("A small chance of rain · up to 2 mm over 6 hours", verdictOf(day(15, 2.4, hours = 6.0)))
        assertEquals("Rain unlikely", verdictOf(day(15, 0.6, hours = 2.0))) // a trace: nothing to show
        val oct4 = alpsDay(4) // 15% at most, 2.4 mm in the afternoon
        assertEquals("A small chance of rain · up to 2 mm over 6 hours", Precip.verdict(oct4, TempUnit.C))
        assertEquals("2 mm", Precip.dayAmount(oct4, TempUnit.C))
        assertEquals("up to 2 millimetres", Precip.dayAmountSpoken(oct4, TempUnit.C))
        assertEquals(listOf("14% · up to 2 mm · 6 h"), oct4.rows.map { it.describe(TempUnit.C) })
    }

    @Test fun `verdicts on a real forecast`() {
        assertEquals("Showers possible · about 4.2 mm over 7 hours", Precip.verdict(alpsDay(1), TempUnit.C))
        assertEquals("Showers possible · about 12 mm over 5 hours", Precip.verdict(alpsDay(2), TempUnit.C))
        assertEquals("A small chance of rain", Precip.verdict(alpsDay(7), TempUnit.C)) // 32%, nothing modelled
        assertEquals("Snow possible · about 7.6 inches over 21 hours", Precip.verdict(alpsDay(8), TempUnit.F))
        assertEquals("Rain unlikely", Precip.verdict(alpsDay(6), TempUnit.C))
    }

    @Test fun `the 10-day row's total is the verdict's`() {
        assertEquals("4.2 mm", Precip.dayAmount(alpsDay(1), TempUnit.C))
        assertEquals("19 cm", Precip.dayAmount(alpsDay(8), TempUnit.C))
        assertEquals("about 19 centimetres of snow", Precip.dayAmountSpoken(alpsDay(8), TempUnit.C))
        assertNull(Precip.dayAmount(alpsDay(7), TempUnit.C)) // a chance, no amount
        assertNull(Precip.dayAmount(alpsDay(6), TempUnit.C))
    }

    // --- The day's rows -----------------------------------------------------------------------------

    @Test fun `rows partition the calendar day and add up to the verdict`() {
        val rain = alpsDay(1)
        assertEquals(listOf("Before sunrise", "Daytime", "Evening"), rain.periods.map { it.name })
        // Sunrise 7:26 and sunset 19:08 round to the hour; the day runs midnight to midnight.
        assertEquals(listOf("12 AM–7 AM", "7 AM–7 PM", "7 PM–12 AM"), rain.periods.map { "${formatHour(it.labelStart)}–${formatHour(it.labelEnd)}" })
        // Every stamp of the day is in exactly one row, and nothing else is.
        assertTrue(rain.hours.all { h -> rain.periods.count { h.time in it } == 1 })
        assertEquals(24, rain.hours.size)

        // Before sunrise has a 43% chance but no modelled amount: a row with just the chance.
        assertEquals(listOf("43%", "60% · 2.1 mm · 3 h", "50% · 2.1 mm · 4 h"), rain.rows.map { it.describe(TempUnit.C) })
        assertEquals(rain.totalMm, rain.rows.sumOf { it.totalMm }, 1e-9)
        assertEquals(rain.wetHours, rain.rows.sumOf { it.wetHours })
        assertEquals(4.2, rain.totalMm, 1e-9)
        assertEquals(7, rain.wetHours)
        assertEquals(alps.day(rain.date)!!.precipSumMm!!, rain.totalMm, 1e-9) // and Open-Meteo's daily sum
        assertEquals("50 percent chance, about 0.08 inches, over 4 hours", rain.rows.last().spoken(TempUnit.F))
    }

    @Test fun `periods take the hours after their start up to their end`() {
        val rain = alpsDay(1)
        val (early, daytime) = rain.periods
        // The 7:00 stamp is rain from 6 to 7, before sunrise; 19:00 is 6 to 7 PM, still daytime.
        assertTrue(LocalDateTime.of(2026, 10, 1, 7, 0) in early)
        assertFalse(LocalDateTime.of(2026, 10, 1, 7, 0) in daytime)
        assertTrue(LocalDateTime.of(2026, 10, 1, 19, 0) in daytime)
        // The 00:00 stamp opens the day, as in Open-Meteo's daily sums.
        assertTrue(LocalDateTime.of(2026, 10, 1, 0, 0) in early)
    }

    @Test fun `a trace in a dry part is left out of the rows and the verdict alike`() {
        // Oct 9: snow before sunrise (35%), then 0.1 mm an hour at 14 to 16% through the afternoon and evening.
        val rain = alpsDay(9)
        assertEquals(listOf("Before sunrise"), rain.rows.map { it.name })
        assertEquals(rain.rows.sumOf { it.snowCm }, rain.snowCm, 1e-9)
        assertEquals(rain.rows.sumOf { it.wetHours }, rain.wetHours)
        assertEquals("A small chance of snow · about 5.9 cm over 9 hours", Precip.verdict(rain, TempUnit.C))
        assertTrue(rain.counted.all { it.time.hour <= 8 })
    }

    @Test fun `no rain is counted on two days`() {
        val days = alps.days.map { Precip.dayRain(alps, it.date) }
        days.forEach { rain -> assertTrue(rain.counted.all { it.time.toLocalDate() == rain.date }) }
        val stamps = days.flatMap { it.counted.map { h -> h.time } }
        assertEquals(stamps.size, stamps.toSet().size)
    }

    @Test fun `the next day's early rain is one line, and only that crosses midnight`() {
        val oct1 = alpsDay(1)
        val next = oct1.carryOn!!
        assertEquals(LocalDate.of(2026, 10, 2), next.date)
        assertEquals("Carries on after midnight: about 12 mm by 7 AM Friday.", Precip.carryOnLine(next, TempUnit.C))
        assertEquals(next.totalMm, alpsDay(2).rows.first().totalMm, 1e-9) // the same hours as the next page's first row
        assertTrue(oct1.counted.none { it.time.toLocalDate() == next.date })
        assertEquals("Carries on after midnight: about 2.3 inches of snow by 8 AM Friday.", Precip.carryOnLine(alpsDay(8).carryOn!!, TempUnit.F))
        assertNull(alpsDay(2).carryOn) // a dry early morning on the 3rd
        assertNull(alpsDay(10).carryOn) // the end of the data
    }

    @Test fun `a snow card names its rows without saying snow again, a rain card says snow where it is`() {
        val snowy = alpsDay(8)
        assertEquals("Snow", snowy.title)
        assertEquals(Mix.SNOW, snowy.mix)
        assertEquals(listOf("45% · 1.3 cm · 6 h", "53% · 13 cm · 11 h", "53% · 5.5 cm · 4 h"), snowy.rows.map { it.describe(TempUnit.C, snowWord = false) })
        assertEquals("45% · 1.3 cm snow · 6 h", snowy.rows.first().describe(TempUnit.C))
        assertEquals(19.32, snowy.snowCm, 1e-9)
        assertEquals("Snow possible · about 19 cm over 21 hours", Precip.verdict(snowy, TempUnit.C))
    }

    @Test fun `rain by day and snow by night makes a rain and snow card`() {
        val f = forecastWith(10 to 2.0, 11 to 2.0, 21 to 1.5, 22 to 1.5, snowCm = mapOf(21 to 1.5, 22 to 1.5))
        val rain = Precip.dayRain(f, date)
        assertEquals(Mix.RAIN_AND_SNOW, rain.mix)
        assertEquals("Rain and snow", rain.title)
        assertEquals("Rain and snow possible · about 4 mm of rain and 3 cm of snow over 4 hours", Precip.verdict(rain, TempUnit.C))
        assertEquals(listOf("50% · 4 mm · 2 h", "50% · 3 cm snow · 2 h"), rain.rows.map { it.describe(TempUnit.C) })
    }

    @Test fun `an older response without amounts has no rows and no timing, and falls back to the daily figures`() {
        val bare = alps.copy(hours = alps.hours.map { it.copy(precipMm = null, snowCm = null) })
        val rain = Precip.dayRain(bare, LocalDate.of(2026, 10, 2))
        assertFalse(rain.complete)
        assertTrue(rain.rows.isEmpty())
        assertNull(rain.timing)
        assertEquals("Showers possible · about 12 mm over 5 hours", Precip.verdict(rain, TempUnit.C))
    }

    // --- Timing -------------------------------------------------------------------------------------

    private fun timingOf(vararg mm: Pair<Int, Double>) = Precip.dayRain(forecastWith(*mm), date).timing

    @Test fun `timing finds the part of the day and names the hour the heaviest rain falls in`() {
        val afternoon = timingOf(13 to 0.4, 14 to 0.6, 15 to 0.8, 16 to 2.5, 17 to 0.5)!!
        assertEquals(TimingShape.MOSTLY, afternoon.shape)
        assertEquals(listOf(PartOfDay.AFTERNOON), afternoon.parts)
        assertEquals(date.atTime(15, 0), afternoon.peak) // stamped 16:00: it falls from 3 to 4 PM
        assertEquals("Mostly in the afternoon, heaviest around 3 PM.", Precip.timingSentence(afternoon))

        assertEquals("On and off all day.", Precip.timingSentence(timingOf(2 to 1.0, 8 to 1.0, 14 to 1.0, 21 to 1.0)!!))

        val two = timingOf(9 to 1.0, 10 to 1.0, 13 to 1.0, 14 to 1.0)!!
        assertEquals("Mostly in the morning and afternoon.", Precip.timingSentence(two))
        assertEquals("mostly this morning and afternoon", Precip.timingToday(two))

        assertNull(timingOf(10 to 0.05))
        assertNull(timingOf())
    }

    @Test fun `the hours before sunrise are before sunrise, never overnight`() {
        val earlyAndMorning = timingOf(4 to 1.0, 5 to 1.0, 9 to 1.0, 10 to 1.0)!!
        assertEquals("Mostly before sunrise and in the morning.", Precip.timingSentence(earlyAndMorning))
        assertEquals("mostly before sunrise and this morning", Precip.timingToday(earlyAndMorning))
        val clearing = timingOf(1 to 1.0, 2 to 1.0, 3 to 1.0)!!
        assertEquals("Before sunrise, clearing by morning.", Precip.timingSentence(clearing))
        assertEquals("before sunrise, clearing by morning", Precip.timingToday(clearing))
        assertEquals("Before sunrise, clearing by morning.", Precip.timingSentence(alpsDay(2).timing!!))
        assertEquals("Mostly in the afternoon and evening.", Precip.timingSentence(alpsDay(1).timing!!))
    }

    @Test fun `a single wet hour or a tiny total has no heaviest`() {
        assertNull(timingOf(15 to 3.0)!!.peak)
        assertNull(timingOf(15 to 0.4, 16 to 0.2)!!.peak)
    }

    @Test fun `today's rain still to come counts the hours that end after now`() {
        val rain = alpsDay(1)
        val evening = rain.stillToCome(LocalDateTime.of(2026, 10, 1, 19, 30))
        assertEquals(2.1, evening.mm, 1e-9)
        assertEquals(4, evening.wetHours)
        assertEquals("about 2.1 mm over 4 hours, mostly this evening", Precip.spanPhrase(evening, rain, TempUnit.C))
        assertEquals(0.0, rain.stillToCome(LocalDateTime.of(2026, 10, 1, 23, 30)).mm, 0.0)
    }

    // --- Days without a sunrise or a sunset ---------------------------------------------------------

    @Test fun `polar night and midnight sun run 7 to 7 with a midday, and leave no hour between rows`() {
        val night = forecastWith(1 to 1.0, 2 to 1.0, 3 to 1.0, sunrise = { it.atStartOfDay() }, sunset = { it.atStartOfDay() })
        val sun = forecastWith(1 to 1.0, sunrise = { it.atStartOfDay() }, sunset = { it.plusDays(1).atStartOfDay() })
        for (f in listOf(night, sun)) {
            val rain = Precip.dayRain(f, date)
            assertEquals(listOf("Early", "Midday", "Evening"), rain.periods.map { it.name })
            assertEquals(listOf("12 AM–7 AM", "7 AM–7 PM", "7 PM–12 AM"), rain.periods.map { "${formatHour(it.labelStart)}–${formatHour(it.labelEnd)}" })
            assertTrue(rain.hours.all { h -> rain.periods.count { h.time in it } == 1 })
        }
        assertEquals("In the early hours, clearing by morning.", Precip.timingSentence(Precip.dayRain(night, date).timing!!))
    }

    @Test fun `a sunset after midnight ends the daytime at midnight, with no evening`() {
        val f = forecastWith(22 to 1.0, sunrise = { it.atTime(2, 40) }, sunset = { it.plusDays(1).atTime(0, 20) })
        val rain = Precip.dayRain(f, date)
        assertEquals(listOf("Before sunrise", "Daytime"), rain.periods.map { it.name })
        assertEquals("3 AM–12 AM", rain.periods.last().let { "${formatHour(it.labelStart)}–${formatHour(it.labelEnd)}" })
        assertTrue(rain.hours.all { h -> rain.periods.count { h.time in it } == 1 })
    }
}
