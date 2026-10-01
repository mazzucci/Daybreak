package app.daybreak.domain

import app.daybreak.TestData
import app.daybreak.TestData.DaySpec
import app.daybreak.TestData.Sun
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class WeekOutlookTest {
    /** Monday, October 5, 2026, 8 AM; the synthetic days have sunrise at 7 AM and sunset at 7 PM. */
    private val monday = LocalDateTime.of(2026, 10, 5, 8, 0)

    private val fine = DaySpec()
    private val breezy = DaySpec(windKmh = 38.0, gustKmh = 50.0) // 79: good, not great
    private fun rainy(hours: IntRange = 0..22, chance: Int = 85) = DaySpec(highC = 13.0, rainHours = hours, chance = chance)

    /** Breezy today, a rainy spell Tuesday to Thursday, a cool Friday, a sunny weekend. */
    private val mixed = listOf(breezy, rainy(8..20), rainy(), rainy(5..15), DaySpec(highC = 10.0, lowC = 5.0), DaySpec(highC = 21.0), DaySpec(highC = 19.0)) +
        List(3) { fine }

    private fun outlook(days: List<DaySpec>, at: LocalDateTime = monday, unit: TempUnit = TempUnit.C, sun: Sun = Sun.NORMAL, weekend: Set<DayOfWeek> = weekendDays(null)) =
        weekOutlook(TestData.synthetic(days, at, sun), unit, weekend)

    private fun WeekOutlook.lines() = week.map { it.text }

    @After fun clockBack() {
        ClockFormat.use24Hour = false
    }

    // --- The mixed week ---------------------------------------------------------------------------

    @Test fun `a mixed week names the spell and the weekend`() {
        val o = outlook(mixed)
        assertEquals("Good day for a walk", o.today.text)
        assertEquals(listOf("Rainy spell from tomorrow until Thursday", "Saturday is the best day this week"), o.lines())
        assertEquals(7, o.days.size)
        assertEquals(listOf(LocalDate.of(2026, 10, 10)), o.days.filter { it.isBest }.map { it.date })
        assertEquals(
            listOf(OutlookTier.GOOD, OutlookTier.STAY_IN, OutlookTier.STAY_IN, OutlookTier.MEH, OutlookTier.GREAT, OutlookTier.GREAT, OutlookTier.GREAT),
            o.days.map { it.tier },
        )
        assertEquals(listOf(DayKind.DRY, DayKind.WET, DayKind.WET, DayKind.WET, DayKind.DRY, DayKind.DRY, DayKind.DRY), o.days.map { it.rain })
        assertEquals("Saturday, best day, great for being outside, dry", o.days[5].spoken)
        assertEquals("Tomorrow, one for staying in, rain likely", o.days[1].spoken)
        assertEquals("This week. Good day for a walk. Rainy spell from tomorrow until Thursday. Saturday is the best day this week.", o.spoken)
    }

    @Test fun `a day that's rainy for most of its daylight is mixed at best, however dry its end`() {
        val thursday = outlook(mixed).days[3]
        assertEquals(OutlookTier.MEH, thursday.tier)
        assertEquals(Outlook.GOOD_MIN - 1, thursday.score)
    }

    // --- Spells -----------------------------------------------------------------------------------

    @Test fun `a spell that starts today says when it ends`() {
        val o = outlook(listOf(rainy(), rainy(), rainy(), fine, fine, fine, fine))
        assertEquals("A day for staying in: rain most of the day", o.today.text)
        assertEquals("Wet until Wednesday, then drier", o.week.first().text)
        assertEquals(LineKind.WET_SPELL, o.week.first().kind)
    }

    @Test fun `a spell that runs past the week goes into next week`() {
        val o = outlook(listOf(fine, fine, fine, fine, rainy(), rainy(), rainy(), rainy(), rainy(), fine))
        assertEquals("Rainy from Friday, into next week", o.week.first().text)
        // Starting on the last day of the week, it still counts when the day after is wet too.
        val late = outlook(listOf(fine, fine, fine, fine, fine, fine, rainy(), rainy(), fine, fine))
        assertEquals("Rainy from Sunday, into next week", late.week.first().text)
        val all = outlook(List(10) { rainy() })
        assertEquals("Wet into next week", all.week.first().text)
    }

    @Test fun `single wet days aren't a spell`() {
        val o = outlook(listOf(fine, rainy(), fine, rainy(), fine, rainy(), fine, rainy(), fine, fine))
        assertTrue(o.week.none { it.kind == LineKind.WET_SPELL })
    }

    @Test fun `short daily bursts are rain on and off`() {
        val o = outlook(listOf(fine, fine, fine, rainy(9..12), rainy(9..12), rainy(9..12), fine, fine))
        assertEquals("Rain on and off Thursday to Saturday", o.week.first().text)
    }

    @Test fun `a snow spell says snow`() {
        val snowy = DaySpec(highC = 0.0, lowC = -4.0, rainHours = 6..18, snow = true)
        val o = outlook(listOf(DaySpec(highC = 3.0, lowC = -2.0), snowy, snowy, DaySpec(highC = 2.0, lowC = -3.0), fine, fine, fine))
        assertEquals("Snowy spell from tomorrow until Wednesday", o.week.first().text)
        assertTrue(o.days[1].snow)
        assertEquals("Tomorrow, one for staying in, snow likely", o.days[1].spoken)
    }

    @Test fun `a wet day on its own turns dry again`() {
        val small = DaySpec(rainHours = 10..10, chance = 30, mmPerHour = 0.3) // possible, not dry
        val o = outlook(listOf(rainy(), small, fine, fine, fine, fine, fine))
        assertEquals("Dry again from Wednesday", o.week.first().text)
        assertEquals(LineKind.DRY_TURN, o.week.first().kind)
    }

    // --- The best day -------------------------------------------------------------------------------

    @Test fun `a weekend day within five points is named over the best weekday`() {
        val cool = DaySpec(highC = 11.0, lowC = 9.0) // 97
        val o = outlook(listOf(breezy, breezy, rainy(), fine, breezy, cool, breezy))
        assertEquals("Saturday is the best day this week", o.week.single { it.kind == LineKind.BEST_DAY }.text)
        // Further behind, the best day wins.
        val cold = DaySpec(highC = 8.0, lowC = 6.0) // 88
        val o2 = outlook(listOf(breezy, breezy, rainy(), fine, breezy, cold, breezy))
        assertEquals("Thursday is the best day this week", o2.week.single { it.kind == LineKind.BEST_DAY }.text)
    }

    @Test fun `the weekend follows the place's country`() {
        val cool = DaySpec(highC = 11.0, lowC = 9.0)
        val o = outlook(listOf(breezy, breezy, rainy(), fine, cool, breezy, breezy), weekend = weekendDays("SA"))
        assertEquals("Friday is the best day this week", o.week.single { it.kind == LineKind.BEST_DAY }.text)
    }

    @Test fun `today can be the best day`() {
        val o = outlook(listOf(fine, breezy, breezy, rainy(), breezy, breezy, breezy))
        assertEquals("Today is the best day this week; make the most of it", o.week.single { it.kind == LineKind.BEST_DAY }.text)
        assertTrue(o.days.first().isBest)
    }

    @Test fun `no best day when nothing stands out`() {
        val o = outlook(List(7) { fine })
        assertTrue(o.days.none { it.isBest })
        assertEquals(listOf("Dry all week"), o.lines())
    }

    @Test fun `with no good days, the least bad is named but not marked best`() {
        val gale = DaySpec(windKmh = 50.0, gustKmh = 80.0) // stay in
        val blowy = DaySpec(windKmh = 45.0, gustKmh = 65.0) // mixed
        val o = outlook(listOf(gale, gale, gale, blowy, gale, gale, gale))
        assertTrue(o.week.any { it.text == "No great days this week; Thursday is the least bad" })
        assertTrue(o.days.none { it.isBest })
    }

    // --- The today line -----------------------------------------------------------------------------

    @Test fun `the today line follows the main limit`() {
        assertEquals("Great day to be outside", outlook(List(7) { fine }).today.text)
        assertEquals("Great day to be outside, best before 1 PM", outlook(listOf(rainy(13..16)) + List(6) { fine }).today.text)
        assertEquals("Mixed day: dry until 1 PM, then rain", outlook(listOf(rainy(13..18)) + List(6) { fine }).today.text)
        assertEquals("Mixed day: rain until 2 PM, then drier", outlook(listOf(rainy(6..13)) + List(6) { fine }).today.text)
        assertEquals("Too windy to enjoy outside: gusts to 85 km/h", outlook(listOf(DaySpec(windKmh = 50.0, gustKmh = 85.0)) + List(6) { fine }).today.text)
        assertEquals("Hot one: 34° by 3 PM; go out early", outlook(listOf(DaySpec(highC = 34.0, lowC = 22.0)) + List(6) { fine }).today.text)
        assertEquals("Cold but fine for a walk", outlook(listOf(DaySpec(highC = 4.0, lowC = 0.0)) + List(6) { fine }).today.text)
        assertEquals("Blustery day: gusts to 50 km/h", outlook(listOf(DaySpec(highC = 2.0, lowC = 0.0, windKmh = 38.0, gustKmh = 50.0)) + List(6) { fine }).today.text)
        assertEquals("Too cold to enjoy outside: -6° at best", outlook(listOf(DaySpec(highC = -6.0, lowC = -12.0)) + List(6) { fine }).today.text)
    }

    @Test fun `the today line only counts the daylight still ahead`() {
        val showersLater = listOf(rainy(14..18)) + List(6) { fine }
        assertEquals("Great day to be outside, best before 2 PM", outlook(showersLater).today.text)
        assertEquals("A day for staying in: rain most of the day", outlook(showersLater, monday.withHour(14).withMinute(10)).today.text)
    }

    @Test fun `in the evening the today line is about tomorrow`() {
        val evening = monday.withHour(19).withMinute(20)
        val o = outlook(List(7) { fine }, evening)
        assertEquals("Tomorrow looks great for being outside", o.today.text)
        assertNull(o.days.first().score)
        assertNull(o.days.first().tier)
        assertEquals("Today, daylight's over, dry", o.days.first().spoken)
        assertEquals(LocalDate.of(2026, 10, 6), o.focus?.date)
        assertEquals("Tomorrow's one for staying in: rain most of the day", outlook(mixed, evening).today.text)
    }

    @Test fun `under two daylight hours left today has no score`() {
        assertNull(outlook(List(7) { fine }, monday.withHour(17).withMinute(45)).days.first().score) // only 6 PM is left
        assertEquals(100, outlook(List(7) { fine }, monday.withHour(16).withMinute(45)).days.first().score) // 5 PM and 6 PM
    }

    @Test fun `polar night scores the waking hours, never above mixed`() {
        val o = outlook(List(7) { DaySpec(highC = 14.0, lowC = 12.0) }, monday.withHour(10), sun = Sun.POLAR_NIGHT)
        assertTrue(o.days.all { it.tier == OutlookTier.MEH })
        assertEquals("No daylight today, but dry", o.today.text)
        assertEquals("Too cold to enjoy outside: -6° at best", outlook(List(7) { DaySpec(highC = -6.0, lowC = -12.0) }, monday.withHour(10), sun = Sun.POLAR_NIGHT).today.text)
    }

    @Test fun `polar day scores the waking hours`() {
        val days = List(7) { DaySpec(highC = 14.0, lowC = 6.0) }
        val night = outlook(days, monday.withHour(23), sun = Sun.MIDNIGHT_SUN)
        assertNull(night.days.first().score) // still light, but past waking hours
        assertTrue(night.today.text.startsWith("Tomorrow looks great"))
        val read = night.focus!!
        assertEquals(6, read.hours.first().hour.time.hour)
        assertEquals(21, read.hours.last().hour.time.hour)
    }

    // --- Week lines ----------------------------------------------------------------------------------

    @Test fun `a heat spell, and its end isn't a cold snap`() {
        val o = outlook(listOf(DaySpec(highC = 34.0, lowC = 22.0), DaySpec(highC = 35.0, lowC = 23.0), DaySpec(highC = 31.0, lowC = 20.0), DaySpec(highC = 27.0), fine, fine, fine))
        assertEquals(listOf("Hot until Wednesday, up to 35°"), o.lines())
        val later = outlook(listOf(fine, fine, DaySpec(highC = 31.0), DaySpec(highC = 33.0), DaySpec(highC = 30.0), fine, fine))
        assertEquals(listOf("Hot spell Wednesday to Friday, up to 33°"), later.lines())
    }

    @Test fun `a cold snap and the first frost`() {
        val o = outlook(listOf(DaySpec(highC = 18.0), DaySpec(highC = 16.0), DaySpec(highC = 9.0, lowC = 2.0), DaySpec(highC = 8.0, lowC = -1.0), fine, fine, fine))
        assertEquals(listOf("Much colder Wednesday: 9°, down from 18° today"), o.lines())
        val frost = outlook(listOf(DaySpec(highC = 12.0, lowC = 3.0), DaySpec(highC = 10.0, lowC = 1.0), DaySpec(highC = 9.0, lowC = -2.0), fine, fine, fine, fine))
        assertEquals(listOf("First frost by Wednesday morning: down to -2°"), frost.lines())
        // Not news when it's already freezing.
        assertTrue(outlook(listOf(DaySpec(highC = 3.0, lowC = -1.0), DaySpec(highC = 3.0, lowC = -3.0), fine, fine, fine, fine, fine)).week.none { it.kind == LineKind.FROST })
    }

    @Test fun `big wind skips the day the today line already calls too windy`() {
        val o = outlook(listOf(DaySpec(windKmh = 50.0, gustKmh = 85.0), fine, DaySpec(gustKmh = 75.0), fine, fine, fine, fine))
        assertEquals("Too windy to enjoy outside: gusts to 85 km/h", o.today.text)
        assertEquals(listOf("Very windy Wednesday: gusts to 75 km/h"), o.lines())
    }

    @Test fun `at most two week lines, highest priority first`() {
        val o = outlook(listOf(DaySpec(highC = 31.0), DaySpec(highC = 32.0), rainy(), rainy(), DaySpec(gustKmh = 80.0), DaySpec(highC = 12.0, lowC = -1.0), fine))
        assertEquals(listOf(LineKind.WET_SPELL, LineKind.BIG_WIND), o.week.map { it.kind })
    }

    // --- Units and words -----------------------------------------------------------------------------

    @Test fun `temperatures and wind follow the unit, and screen readers hear both`() {
        val hot = outlook(listOf(DaySpec(highC = 34.0, lowC = 22.0)) + List(6) { fine }, unit = TempUnit.F)
        assertEquals("Hot one: 93° by 3 PM; go out early", hot.today.text)
        assertEquals("Hot one: 93°F (34°C) by 3 PM; go out early", hot.today.spoken)
        val windy = outlook(listOf(DaySpec(windKmh = 50.0, gustKmh = 85.0)) + List(6) { fine }, unit = TempUnit.F)
        assertEquals("Too windy to enjoy outside: gusts to 53 mph", windy.today.text)
        val snap = outlook(listOf(DaySpec(highC = 18.0), DaySpec(highC = 16.0), DaySpec(highC = 9.0, lowC = 2.0), fine, fine, fine, fine), unit = TempUnit.F)
        assertEquals("Much colder Wednesday: 48°, down from 64° today", snap.week.first().text)
    }

    @Test fun `hour spans are compact, and follow the 24-hour clock`() {
        val day = LocalDate.of(2026, 10, 5)
        assertEquals("1–5 PM", formatSpan(day.atTime(13, 0), day.atTime(17, 0)))
        assertEquals("11 AM–2 PM", formatSpan(day.atTime(11, 0), day.atTime(14, 0)))
        assertEquals("9 PM–12 AM", formatSpan(day.atTime(21, 0), day.plusDays(1).atStartOfDay()))
        ClockFormat.use24Hour = true
        assertEquals("13:00–17:00", formatSpan(day.atTime(13, 0), day.atTime(17, 0)))
    }

    @Test fun `day names`() {
        val today = LocalDate.of(2026, 10, 5) // a Monday
        assertEquals("today", outlookDayName(today, today))
        assertEquals("tomorrow", outlookDayName(today.plusDays(1), today))
        assertEquals("Saturday", outlookDayName(today.plusDays(5), today))
        assertEquals("Sunday", outlookDayName(today.plusDays(6), today))
        assertEquals("next Monday", outlookDayName(today.plusDays(7), today))
    }

    // --- Never contradicting Precip ------------------------------------------------------------------

    private val rainWords = Regex("""\b(rain|rainy|wet|showers|snow|snowy|thunderstorms|drizzle)\b""", RegexOption.IGNORE_CASE)

    /** Every outlook surface agrees with the day page's rules, whatever the forecast and the time. */
    private fun assertAgreesWithPrecip(forecast: Forecast) {
        val o = weekOutlook(forecast, TempUnit.C)
        o.days.forEach { day ->
            val rain = Precip.dayRain(forecast, day.date)
            assertEquals(rain.kind, day.rain)
            // The strip says what the day page's verdict says ("rain likely", "showers possible"), or "dry".
            val verdict = if (rain.dry) "dry" else Precip.verdict(rain, TempUnit.C).substringBefore(" · ").replaceFirstChar { it.lowercase() }
            assertTrue("${day.spoken} vs $verdict", day.spoken.endsWith(verdict))
            // A dry day is never called rainy.
            if (rain.dry) {
                (listOf(o.today) + o.week).filter { it.date == day.date && it.kind != LineKind.DRY_TURN }.forEach {
                    assertFalse(it.text, rainWords.containsMatchIn(it.text))
                }
            }
        }
        o.week.filter { it.kind == LineKind.WET_SPELL }.forEach { spell ->
            val start = spell.date!!
            assertEquals(DayKind.WET, Precip.dayRain(forecast, start).kind)
            assertEquals(DayKind.WET, Precip.dayRain(forecast, start.plusDays(1)).kind)
        }
        o.week.filter { it.kind == LineKind.DRY_TURN }.forEach { assertTrue(Precip.dayRain(forecast, it.date!!).dry) }
        // "Dry until 2 PM": every hour before then is dry by the classifier.
        Regex("""dry until (\d+) (AM|PM)""").find(o.today.text)?.let { m ->
            val h = m.groupValues[1].toInt() % 12 + if (m.groupValues[2] == "PM") 12 else 0
            val date = o.today.date!!
            forecast.hoursOf(date).filter { it.time.hour in 1..h && it.time > forecast.current.time }.forEach {
                assertTrue("$it", Precip.classify(it.precipChance, it.precipMm).dry)
            }
        }
    }

    @Test fun `the outlook never contradicts the rain rules`() {
        val alps = TestData.alps()
        listOf(3, 9, 12, 15, 18).forEach { h ->
            assertAgreesWithPrecip(alps.copy(current = alps.current.copy(time = LocalDateTime.of(2026, 10, 1, h, 15))))
        }
        assertAgreesWithPrecip(TestData.forecast())
        assertAgreesWithPrecip(TestData.rainyNight())
        assertAgreesWithPrecip(TestData.synthetic(mixed, monday))
        assertAgreesWithPrecip(TestData.synthetic(listOf(rainy(13..18)) + List(6) { fine }, monday))
    }

    @Test fun `a wet day with a chance under 70 percent is possible, not likely`() {
        val o = outlook(listOf(fine, DaySpec(rainHours = 9..12, chance = 55), fine, fine, fine, fine, fine))
        assertEquals(DayKind.WET, o.days[1].rain)
        assertTrue(o.days[1].spoken.endsWith("rain possible"))
    }

    // --- Fixtures ------------------------------------------------------------------------------------

    @Test fun `the Alps fixture`() {
        val alps = TestData.alps() // Thursday 3:15 AM, 3,200 m: showers today, wet overnight, then dry and near freezing
        val o = weekOutlook(alps, TempUnit.C)
        assertEquals("Mixed day: showers on and off", o.today.text)
        assertEquals(listOf("Rain on and off until tomorrow, then drier", "First frost by Tuesday morning: down to -2°"), o.lines())
        // Friday's rain is overnight: a wet day by the rain rules, a good one for being outside.
        assertEquals(DayKind.WET, o.days[1].rain)
        assertEquals(OutlookTier.GOOD, o.days[1].tier)
        val evening = weekOutlook(alps.copy(current = alps.current.copy(time = LocalDateTime.of(2026, 10, 1, 18, 40))), TempUnit.C)
        assertEquals("Tomorrow looks cold but fine for a walk", evening.today.text)
    }

    @Test fun `the San Francisco week is dry and great`() {
        val sf = app.daybreak.data.parseForecast(TestData.fixture("forecast_sf_week.json"))
        val o = weekOutlook(sf, TempUnit.F)
        assertEquals("Great day to be outside", o.today.text)
        assertEquals(listOf("Dry all week"), o.lines())
        assertTrue(o.days.all { it.tier == OutlookTier.GREAT && it.rain == DayKind.DRY })
    }

    @Test fun `days past the hourly data are scored from their daily figures`() {
        val o = weekOutlook(TestData.forecast(), TempUnit.F) // 12 hours of hourly data
        assertEquals("Mixed day: dry until 5 PM, then rain", o.today.text)
        assertEquals(OutlookTier.STAY_IN, o.days[2].tier) // the wet Wednesday
        assertEquals(OutlookTier.GREAT, o.days[1].tier)
    }

    @Test fun `the explanation describes the day the today line is about`() {
        val f = TestData.synthetic(listOf(rainy(13..18)) + List(6) { fine }, monday)
        val e = explain(Term.WEEK, f, TempUnit.C)
        assertEquals("How This week works", e.title)
        assertEquals("Mixed", e.value)
        assertEquals("Today: 59 out of 100", e.detail)
        assertTrue(e.now, e.now.startsWith("Today scores 59 out of 100, from its best 3 hours of daylight still ahead (10 AM–1 PM)."))
        assertTrue(e.now, e.now.endsWith("Rain is what holds it back most."))
        assertTrue(e.meaning, e.meaning.contains("outside 12–26°C."))
        assertTrue(explain(Term.WEEK, f, TempUnit.F).meaning.contains("outside 54–79°F."))
        val evening = explain(Term.WEEK, TestData.synthetic(List(7) { fine }, monday.withHour(20)), TempUnit.F)
        assertTrue(evening.now, evening.now.startsWith("Today's daylight is over, so the outlook looks at tomorrow. Tomorrow scores 100"))
    }
}
