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

    private fun outlook(
        days: List<DaySpec>,
        at: LocalDateTime = monday,
        unit: TempUnit = TempUnit.C,
        sun: Sun = Sun.NORMAL,
        weekend: Set<DayOfWeek> = weekendDays(null),
        now: LocalDateTime = at,
    ) = weekOutlook(TestData.synthetic(days, at, sun), unit, weekend, now = now)

    /** [edit] applied to the hours of [date] stamped in [stamps]. */
    private fun Forecast.editHours(date: LocalDate, stamps: IntRange, edit: (HourForecast) -> HourForecast) =
        copy(hours = hours.map { if (it.time.toLocalDate() == date && it.time.hour in stamps) edit(it) else it })

    /** Rain (80%, 1.5 mm, code 63) falling during the hours starting at [hours] of [date]: stamped an hour later. */
    private fun Forecast.rainDuring(date: LocalDate, vararg hours: Int) =
        copy(hours = this.hours.map { h -> if (h.time.toLocalDate() == date && h.time.hour - 1 in hours) h.copy(precipChance = 80, precipMm = 1.5, code = 63) else h })

    private val mondayDate: LocalDate = monday.toLocalDate()

    private fun WeekOutlook.lines() = week.map { it.text }

    @After fun clockBack() {
        ClockFormat.use24Hour = false
    }

    // --- The mixed week ---------------------------------------------------------------------------

    @Test fun `a mixed week names the spell and the weekend`() {
        val o = outlook(mixed)
        assertEquals("Good day to be outside", o.today.text)
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
        assertEquals("Good day to be outside. Rainy spell from tomorrow until Thursday. Saturday is the best day this week.", o.spoken)
    }

    @Test fun `a day that's rainy for most of its daylight is mixed at best, however dry its end`() {
        val thursday = outlook(mixed).days[3]
        assertEquals(OutlookTier.MEH, thursday.tier)
        assertEquals(Outlook.GOOD_MIN - 1, thursday.score)
    }

    // --- Spells -----------------------------------------------------------------------------------

    @Test fun `a spell that starts today says when it ends`() {
        val o = outlook(listOf(rainy(), rainy(), rainy(), fine, fine, fine, fine))
        assertEquals("Better stay in: rain most of the day", o.today.text)
        assertEquals("Wet until Wednesday, drier from Thursday", o.week.first().text)
        assertEquals(LocalDate.of(2026, 10, 7), o.week.first().end)
        assertEquals(LineKind.WET_SPELL, o.week.first().kind)
    }

    @Test fun `a spell past the week names its end when the data shows it`() {
        val o = outlook(listOf(fine, fine, fine, fine, rainy(), rainy(), rainy(), rainy(), rainy(), fine))
        assertEquals("Rainy spell Friday to next Tuesday", o.week.first().text)
        // Starting on the last day of the week, it still counts when the day after is wet too.
        val late = outlook(listOf(fine, fine, fine, fine, fine, fine, rainy(), rainy(), fine, fine))
        assertEquals("Rainy spell Sunday to next Monday", late.week.first().text)
        // "Into next week" only when it runs to the end of the data.
        val open = outlook(listOf(fine, fine, fine, fine, rainy(), rainy(), rainy(), rainy(), rainy(), rainy()))
        assertEquals("Rainy from Friday, into next week", open.week.first().text)
        val all = outlook(List(10) { rainy() })
        assertEquals("Wet into next week", all.week.first().text)
    }

    @Test fun `a spell from today that ends tomorrow`() {
        val o = outlook(listOf(rainy(), rainy(), fine, fine, fine, fine, fine))
        assertEquals("Rain today and tomorrow, then drier", o.week.first().text)
        val showery = DaySpec(highC = 13.0, rainHours = 0..22, chance = 85)
        val f = TestData.synthetic(listOf(showery, showery) + List(5) { fine }, monday).let { f ->
            f.copy(days = f.days.mapIndexed { i, d -> if (i < 2) d.copy(code = 81) else d })
        }
        assertEquals("Showers today and tomorrow, then drier", weekOutlook(f, TempUnit.C).week.first().text)
    }

    @Test fun `a showery spell is showers on and off`() {
        val f = TestData.synthetic(listOf(fine, fine, fine, rainy(), rainy(), rainy(), fine, fine), monday).let { f ->
            f.copy(days = f.days.mapIndexed { i, d -> if (i in 3..5) d.copy(code = 80) else d })
        }
        assertEquals("Showers on and off Thursday to Saturday", weekOutlook(f, TempUnit.C).week.first().text)
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
        assertEquals("Better stay in: rain most of the day", o.today.text)
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
        assertEquals("Today's the best day this week", o.week.single { it.kind == LineKind.BEST_DAY }.text)
        assertTrue(o.days.first().isBest)
    }

    @Test fun `no best day when nothing stands out`() {
        val o = outlook(List(7) { fine })
        assertTrue(o.days.none { it.isBest })
        assertEquals(listOf("Great all week"), o.lines())
        assertEquals(listOf("Good all week"), outlook(List(7) { breezy }).lines())
        val blowy = DaySpec(windKmh = 45.0, gustKmh = 65.0) // mixed
        assertEquals(listOf("Dry all week"), outlook(listOf(fine, fine, blowy, fine, fine, fine, fine)).lines())
    }

    @Test fun `with no good days, the least bad is named but not marked best`() {
        val gale = DaySpec(windKmh = 50.0, gustKmh = 80.0) // stay in
        val blowy = DaySpec(windKmh = 45.0, gustKmh = 65.0) // mixed
        val o = outlook(listOf(gale, gale, gale, blowy, gale, gale, gale))
        assertTrue(o.week.any { it.text == "Thursday is the best of a poor week" })
        assertTrue(o.days.none { it.isBest })
    }

    // --- The today line -----------------------------------------------------------------------------

    @Test fun `the today line follows the main limit`() {
        assertEquals("Great day to be outside", outlook(List(7) { fine }).today.text)
        assertEquals("Good day to be outside", outlook(List(7) { breezy }).today.text)
        assertEquals("Mixed day: dry until 1 PM, then rain", outlook(listOf(rainy(13..18)) + List(6) { fine }).today.text)
        assertEquals("Mixed day: rain until 2 PM, then drier", outlook(listOf(rainy(6..13)) + List(6) { fine }).today.text)
        assertEquals("Too windy to enjoy outside: gusts to 85 km/h", outlook(listOf(DaySpec(windKmh = 50.0, gustKmh = 85.0)) + List(6) { fine }).today.text)
        assertEquals("Hot day: 34° by 3 PM, best before 12 PM", outlook(listOf(DaySpec(highC = 34.0, lowC = 22.0)) + List(6) { fine }).today.text)
        assertEquals("Cold but good to be outside", outlook(listOf(DaySpec(highC = 4.0, lowC = 0.0)) + List(6) { fine }).today.text)
        assertEquals("Blustery day: gusts to 50 km/h", outlook(listOf(DaySpec(highC = 2.0, lowC = 0.0, windKmh = 38.0, gustKmh = 50.0)) + List(6) { fine }).today.text)
        assertEquals("Too cold to enjoy outside: no warmer than \u22126°", outlook(listOf(DaySpec(highC = -6.0, lowC = -12.0)) + List(6) { fine }).today.text)
        // Cold but otherwise fine, under polar night: mixed at best, and the cold is why.
        assertEquals("Cold day: no warmer than 4°", outlook(List(7) { DaySpec(highC = 4.0, lowC = 2.0) }, monday.withHour(10), sun = Sun.POLAR_NIGHT).today.text)
        // Tomorrow, in the evening.
        val evening = monday.withHour(20)
        assertEquals("Tomorrow looks good outside", outlook(List(7) { breezy }, evening).today.text)
        assertEquals("Better stay in tomorrow: rain most of the day", outlook(listOf(fine, rainy()) + List(5) { fine }, evening).today.text)
        assertEquals("Hot day tomorrow: 34° by 3 PM, best before 11 AM", outlook(List(7) { DaySpec(highC = 34.0, lowC = 22.0) }, evening).today.text)
        // No semicolons anywhere.
        listOf(mixed, List(7) { DaySpec(highC = 34.0, lowC = 22.0) }, List(7) { DaySpec(highC = 2.0, lowC = -2.0) }).forEach { days ->
            val o = outlook(days)
            (listOf(o.today) + o.week).forEach { assertFalse(it.text, it.text.contains(';')) }
        }
    }

    @Test fun `the today line only counts the daylight still ahead`() {
        val showersLater = listOf(rainy(14..18)) + List(6) { fine }
        assertEquals("Mixed day: dry until 2 PM, then rain", outlook(showersLater).today.text)
        assertEquals("Better stay in: rain most of the day", outlook(showersLater, monday.withHour(14).withMinute(10)).today.text)
    }

    @Test fun `in the evening the today line is about tomorrow`() {
        val evening = monday.withHour(19).withMinute(20)
        val o = outlook(List(7) { fine }, evening)
        assertEquals("Tomorrow looks great outside", o.today.text)
        assertNull(o.days.first().score)
        assertNull(o.days.first().tier)
        assertEquals("Today, daylight's over, dry", o.days.first().spoken)
        assertEquals(LocalDate.of(2026, 10, 6), o.focus?.date)
        assertEquals("Better stay in tomorrow: rain most of the day", outlook(mixed, evening).today.text)
    }

    @Test fun `under two daylight hours left today has no score`() {
        val late = outlook(List(7) { fine }, monday.withHour(17).withMinute(45)) // only 6 PM is left
        assertNull(late.days.first().score)
        assertEquals("Today, not enough daylight left, dry", late.days.first().spoken)
        assertEquals(100, outlook(List(7) { fine }, monday.withHour(16).withMinute(45)).days.first().score) // 5 PM and 6 PM
    }

    @Test fun `a day with under two hours of daylight is judged like polar night`() {
        fun short(at: LocalDateTime) = TestData.synthetic(List(7) { DaySpec(highC = 14.0, lowC = 12.0) }, monday).let { f ->
            f.copy(
                current = f.current.copy(time = at),
                days = f.days.mapIndexed { i, d -> if (i == 0) d.copy(sunrise = d.date.atTime(11, 40), sunset = d.date.atTime(12, 20)) else d },
            )
        }
        val morning = weekOutlook(short(monday.withHour(10)), TempUnit.C)
        val score = morning.days.first().score!!
        assertTrue("$score", score <= Outlook.GOOD_MIN - 1)
        assertTrue(morning.focus!!.shortDay)
        assertTrue(morning.today.text, morning.today.text.startsWith("Little daylight today, but dry"))
        val after = weekOutlook(short(monday.withHour(12).withMinute(30)), TempUnit.C)
        assertNull(after.days.first().score)
        assertEquals("Today, daylight's over, dry", after.days.first().spoken)
        // The explanation gives no hours for a day without daylight.
        val e = explain(Term.WEEK, short(monday.withHour(10)), TempUnit.C)
        assertTrue(e.now, e.now.startsWith("Today scores $score out of 100, from its best 3 waking hours still ahead."))
        assertFalse(e.now, e.now.contains("("))
    }

    @Test fun `polar night scores the waking hours, never above mixed`() {
        val o = outlook(List(7) { DaySpec(highC = 14.0, lowC = 12.0) }, monday.withHour(10), sun = Sun.POLAR_NIGHT)
        assertTrue(o.days.all { it.tier == OutlookTier.MEH })
        assertEquals("No daylight today, but dry", o.today.text)
        assertEquals("Too cold to enjoy outside: no warmer than \u22126°", outlook(List(7) { DaySpec(highC = -6.0, lowC = -12.0) }, monday.withHour(10), sun = Sun.POLAR_NIGHT).today.text)
        val e = explain(Term.WEEK, TestData.synthetic(List(7) { DaySpec(highC = 14.0, lowC = 12.0) }, monday.withHour(19), Sun.POLAR_NIGHT), TempUnit.C)
        assertFalse(e.now, e.now.contains("PM)"))
        assertTrue(e.now, e.now.endsWith("It's polar night, so it can't score higher than mixed."))
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
        assertEquals(listOf("Frost by Wednesday morning: down to \u22122°"), frost.lines())
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
        assertEquals("Hot day: 93° by 3 PM, best before 12 PM", hot.today.text)
        assertEquals("Hot day: 93°F (34°C) by 3 PM, best before 12 PM", hot.today.spoken)
        val spell = outlook(listOf(fine, fine, DaySpec(highC = 31.0), DaySpec(highC = 33.0), DaySpec(highC = 30.0), fine, fine), unit = TempUnit.F)
        assertEquals("Hot spell Wednesday to Friday, up to 91°", spell.week.first().text)
        assertEquals("Hot spell Wednesday to Friday, up to 91°F (33°C)", spell.week.first().spoken)
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

    private val goodLine = Regex("""^(Great day|Good day|Cold but good|Tomorrow looks (great|good|cold but good))""")

    private val rainWords = Regex("""\b(rain|rainy|wet|showers|snow|snowy|thunderstorms|drizzle)\b""", RegexOption.IGNORE_CASE)

    /** Every outlook surface agrees with the day page's rules, whatever the forecast and the time. */
    private fun assertAgreesWithPrecip(forecast: Forecast, now: LocalDateTime = forecast.current.time) {
        val o = weekOutlook(forecast, TempUnit.C, now = now)
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
        // Every day of a spell is wet.
        val spells = o.week.filter { it.kind == LineKind.WET_SPELL }
        spells.forEach { spell ->
            var d = spell.date!!
            while (d <= spell.end!!) {
                assertEquals("$d in ${spell.text}", DayKind.WET, Precip.dayRain(forecast, d).kind)
                d = d.plusDays(1)
            }
        }
        fun inSpell(date: LocalDate) = spells.any { date >= it.date!! && date <= it.end!! }
        o.week.filter { it.kind == LineKind.DRY_TURN }.forEach {
            assertTrue(Precip.dayRain(forecast, it.date!!).dry)
            // "Dry again" only after a wet today line.
            assertEquals(o.today.text, OutlookTopic.WET, o.today.topic)
        }
        // The best day is never wet, nor inside the spell.
        (o.days.filter { it.isBest }.map { it.date } + o.week.filter { it.kind == LineKind.BEST_DAY }.map { it.date!! }).forEach {
            assertTrue("$it", Precip.dayRain(forecast, it).kind != DayKind.WET)
            assertFalse("$it", inSpell(it))
        }
        // A good or great today line never sits next to a spell over its own day.
        if (goodLine.containsMatchIn(o.today.text)) assertFalse("${o.today.text} vs ${o.lines()}", inSpell(o.today.date!!))
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
        repros.forEach { days ->
            listOf(7, 8, 13, 17, 20).forEach { h -> assertAgreesWithPrecip(TestData.synthetic(days, monday.withHour(h).withMinute(15))) }
        }
    }

    /** The forecasts behind the review's bugs: rain outside daylight, a wet day inside a spell, a wet Saturday. */
    private val small = DaySpec(rainHours = 10..10, chance = 30, mmPerHour = 0.3)
    private val repros by lazy {
        listOf(
            listOf(rainy(0..5), rainy(0..5)) + List(5) { fine },
            listOf(rainy(0..5), small) + List(5) { fine },
            listOf(breezy, rainy(), rainy(0..4), rainy()) + List(3) { breezy },
            listOf(breezy, breezy, rainy(), fine, breezy, rainy(0..4), breezy),
            listOf(rainy(20..22), rainy(0..3)) + List(5) { fine },
        )
    }

    // --- Lines that agree with each other (B1) -------------------------------------------------------

    @Test fun `rain before sunrise doesn't start a spell next to a great day`() {
        val o = outlook(repros[0])
        assertEquals("Great day to be outside", o.today.text)
        assertTrue(o.lines().toString(), o.week.none { it.kind == LineKind.WET_SPELL || it.kind == LineKind.DRY_TURN })
        // Still wet by the rain rules: the strip keeps its glyphs.
        assertEquals(DayKind.WET, o.days[0].rain)
    }

    @Test fun `dry again only follows a wet today line`() {
        assertTrue(outlook(repros[1]).week.none { it.kind == LineKind.DRY_TURN })
    }

    @Test fun `the best day is never a wet day or inside the spell`() {
        val o = outlook(repros[2])
        assertEquals("Rainy spell from tomorrow until Thursday", o.week.first().text)
        assertTrue(o.lines().toString(), o.week.none { it.kind == LineKind.BEST_DAY })
        assertTrue(o.days.none { it.isBest })
    }

    @Test fun `a wet Saturday isn't picked by the weekend rule`() {
        val o = outlook(repros[3])
        assertEquals("Thursday is the best day this week", o.week.single { it.kind == LineKind.BEST_DAY }.text)
    }

    @Test fun `the weekend pick has to stand out itself`() {
        // Saturday (97) is within five points of Thursday (100), but not ten over the median (88): Thursday it is.
        val cold = DaySpec(highC = 8.0, lowC = 6.0) // 88
        val cool = DaySpec(highC = 11.0, lowC = 9.0) // 97
        val o = outlook(listOf(cold, cold, cold, fine, cold, cool, rainy()))
        assertEquals("Thursday is the best day this week", o.week.single { it.kind == LineKind.BEST_DAY }.text)
    }

    @Test fun `tonight's rain doesn't start a spell next to tomorrow's great day`() {
        val o = outlook(repros[4], monday.withHour(20))
        assertTrue(o.today.text, o.today.text.startsWith("Tomorrow looks great"))
        assertTrue(o.lines().toString(), o.week.none { it.kind == LineKind.WET_SPELL })
    }

    // --- The clock (R1) -------------------------------------------------------------------------------

    @Test fun `at 8 PM a forecast from 2 PM is about tomorrow`() {
        val f = TestData.synthetic(listOf(rainy(9..14)) + List(6) { fine }, monday.withHour(14))
        assertEquals("Great day to be outside, best 3–6 PM".substringBefore(","), weekOutlook(f, TempUnit.C).today.text.substringBefore(","))
        val clock = monday.withHour(20).toInstant(java.time.ZoneOffset.UTC)
        val later = weekOutlook(f, TempUnit.C, now = outlookMoment(f, clock))
        assertFalse(later.today.text, later.today.text.contains("3–6 PM"))
        assertEquals("Tomorrow looks great outside", later.today.text)
        assertNull(later.days.first().score)
    }

    @Test fun `the outlook's moment is the later of the forecast and the clock, at the place, to the hour`() {
        val f = TestData.synthetic(List(7) { fine }, monday.withHour(14).withMinute(20)).copy(utcOffsetSeconds = 7200)
        // 18:40 UTC is 20:40 at the place: 8 PM.
        assertEquals(monday.withHour(20), outlookMoment(f, java.time.Instant.parse("2026-10-05T18:40:00Z")))
        // A clock behind the forecast (a phone that's slow) never takes it back.
        assertEquals(monday.withHour(14).withMinute(20), outlookMoment(f, java.time.Instant.parse("2026-10-05T10:00:00Z")))
    }

    @Test fun `daylight saving - the moment follows the forecast's own offset, and a short night still scores`() {
        // Fetched in summer time (UTC+2) before the clocks go back on Sunday, October 25; viewed after.
        val sunday = LocalDateTime.of(2026, 10, 25, 8, 0)
        val f = TestData.synthetic(List(7) { fine }, sunday).copy(utcOffsetSeconds = 7200)
        // 9:30 UTC is 10:30 on the phone (UTC+1 by then), but 11:30 in the forecast's stamps.
        assertEquals(sunday.withHour(11), outlookMoment(f, java.time.Instant.parse("2026-10-25T09:30:00Z")))
        // A day missing its 2 AM stamp (the spring change) is scored as usual, not as a short day.
        val spring = f.copy(hours = f.hours.filterNot { it.time == sunday.plusDays(1).withHour(2) })
        val read = weekOutlook(spring, TempUnit.C).days[1]
        assertEquals(OutlookTier.GREAT, read.tier)
    }

    // --- Rain in a good day (R2) and the pattern -----------------------------------------------------

    @Test fun `a good day says where its rain falls`() {
        val morning = TestData.synthetic(List(7) { fine }, monday).rainDuring(mondayDate, 8, 9)
        assertEquals("Great day to be outside, best from 10 AM after rain", weekOutlook(morning, TempUnit.C).today.text)
        val later = TestData.synthetic(List(7) { fine }, monday).rainDuring(mondayDate, 17, 18)
        assertEquals("Great day to be outside, best before 5 PM, then rain", weekOutlook(later, TempUnit.C).today.text)
    }

    @Test fun `rain in a third of the hours makes a mixed day`() {
        val o = outlook(listOf(rainy(13..16)) + List(6) { fine })
        assertEquals(OutlookTier.MEH, o.days.first().tier)
        assertEquals("Mixed day: dry until 1 PM, then rain", o.today.text)
    }

    @Test fun `rain early and late is on and off, not until then drier`() {
        val f = TestData.synthetic(List(7) { fine }, monday).rainDuring(mondayDate, 8, 9, 15, 16)
        assertEquals("Mixed day: rain on and off", weekOutlook(f, TempUnit.C).today.text)
    }

    // --- Codes and wind (R3, R4, R5, B3) ---------------------------------------------------------

    @Test fun `a rain code with dry numbers costs nothing, a storm code costs a little and is named`() {
        val drizzleCode = TestData.synthetic(List(7) { fine }, monday).editHours(mondayDate, 0..23) { it.copy(code = 61) }
        assertEquals(100, weekOutlook(drizzleCode, TempUnit.C).days.first().score)
        val stormy = TestData.synthetic(List(7) { fine }, monday).editHours(mondayDate, 13..15) { it.copy(code = 95) }
        val o = weekOutlook(stormy, TempUnit.C)
        assertEquals("Great day to be outside, best before 1 PM, risk of thunderstorms", o.today.text)
        // Storms with a real chance but no rain worth the name: no rain pattern, still a reason.
        val near = TestData.synthetic(List(7) { fine }, monday).editHours(mondayDate, 0..23) { it.copy(code = 95, precipChance = 35) }
        assertEquals("Better stay in: risk of thunderstorms", weekOutlook(near, TempUnit.C).today.text)
    }

    @Test fun `the outlook reads the code at the hour, rain and gusts from the hour after`() {
        val f = TestData.synthetic(List(7) { fine }, monday)
            .editHours(mondayDate, 15..15) { it.copy(code = 95) }
            .editHours(mondayDate, 16..16) { it.copy(gustKmh = 80.0, precipChance = 60, precipMm = 2.0) }
        val three = withRainDuring(f, f.hourAt(monday.withHour(15))!!)
        assertEquals(95, three.code)
        assertEquals(80.0, three.gustKmh!!, 0.0)
        assertEquals(60, three.precipChance)
        assertEquals(1, withRainDuring(f, f.hourAt(monday.withHour(14))!!).code)
    }

    @Test fun `wind without gusts says wind`() {
        val noGusts = TestData.synthetic(List(7) { fine }, monday).editHours(mondayDate, 0..23) { it.copy(windKmh = 50.0, gustKmh = null) }
        assertEquals("Blustery day: wind to 50 km/h", weekOutlook(noGusts, TempUnit.C).today.text)
        val weakGusts = TestData.synthetic(List(7) { fine }, monday).editHours(mondayDate, 0..23) { it.copy(windKmh = 50.0, gustKmh = 30.0) }
        assertEquals("Blustery day: wind to 50 km/h", weekOutlook(weakGusts, TempUnit.C).today.text)
    }

    @Test fun `a gale counts only in the scored hours, and not on a today that's over`() {
        val wednesday = mondayDate.plusDays(2)
        val night = TestData.synthetic(List(7) { fine }, monday).editHours(wednesday, 0..4) { it.copy(gustKmh = 90.0) }.let { f ->
            f.copy(days = f.days.map { if (it.date == wednesday) it.copy(gustMaxKmh = 90.0) else it })
        }
        assertTrue(weekOutlook(night, TempUnit.C).week.none { it.kind == LineKind.BIG_WIND })
        val day = TestData.synthetic(List(7) { fine }, monday).editHours(wednesday, 12..14) { it.copy(gustKmh = 90.0) }
        assertEquals("Very windy Wednesday: gusts to 90 km/h", weekOutlook(day, TempUnit.C).week.single { it.kind == LineKind.BIG_WIND }.text)
        val evening = monday.withHour(20)
        val today = TestData.synthetic(List(7) { fine }, evening).editHours(mondayDate, 10..14) { it.copy(gustKmh = 90.0) }
        assertTrue(weekOutlook(today, TempUnit.C).week.none { it.kind == LineKind.BIG_WIND })
    }

    // --- Frost (R8) -----------------------------------------------------------------------------------

    @Test fun `frost needs a morning that rounds below zero, and first frost needs five frost-free mornings`() {
        val nearly = outlook(listOf(DaySpec(highC = 12.0, lowC = 3.0), DaySpec(highC = 9.0, lowC = -0.4), fine, fine, fine, fine, fine))
        assertTrue(nearly.week.none { it.kind == LineKind.FROST })
        val nearlyF = outlook(listOf(DaySpec(highC = 12.0, lowC = 3.0), DaySpec(highC = 9.0, lowC = -0.4), fine, fine, fine, fine, fine), unit = TempUnit.F)
        assertTrue(nearlyF.week.none { it.kind == LineKind.FROST })
        val frostF = outlook(listOf(DaySpec(highC = 12.0, lowC = 3.0), DaySpec(highC = 10.0, lowC = 1.0), DaySpec(highC = 9.0, lowC = -2.0), fine, fine, fine, fine), unit = TempUnit.F)
        assertEquals(listOf("Frost by Wednesday morning: down to 28°"), frostF.lines())
        val first = outlook(listOf(fine, fine, fine, fine, fine, DaySpec(highC = 13.0, lowC = -2.0), fine))
        assertEquals(listOf("First frost by Saturday morning: down to \u22122°"), first.lines())
    }

    @Test fun `the morning low comes from midnight to 9 AM`() {
        // A cold evening on Tuesday doesn't make Tuesday morning frosty; a cold 3 AM does.
        val base = TestData.synthetic(List(7) { DaySpec(highC = 12.0, lowC = 3.0) }, monday)
        val tuesday = mondayDate.plusDays(1)
        val evening = base.editHours(tuesday, 21..23) { it.copy(tempC = -3.0) }
        assertTrue(weekOutlook(evening, TempUnit.C).week.none { it.kind == LineKind.FROST })
        val small = base.editHours(tuesday, 3..3) { it.copy(tempC = -3.0) }
        assertEquals("Frost by tomorrow morning: down to \u22123°", weekOutlook(small, TempUnit.C).week.single { it.kind == LineKind.FROST }.text)
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
        assertEquals(listOf("Showers today and tomorrow, then drier", "First frost by Tuesday morning: down to \u22122°"), o.lines())
        // Friday's rain is overnight: a wet day by the rain rules, a good one for being outside.
        assertEquals(DayKind.WET, o.days[1].rain)
        assertEquals(OutlookTier.GOOD, o.days[1].tier)
        val evening = weekOutlook(alps.copy(current = alps.current.copy(time = LocalDateTime.of(2026, 10, 1, 18, 40))), TempUnit.C)
        assertEquals("Tomorrow looks cold but good outside", evening.today.text)
    }

    @Test fun `the San Francisco week is dry and great`() {
        val sf = app.daybreak.data.parseForecast(TestData.fixture("forecast_sf_week.json"))
        val o = weekOutlook(sf, TempUnit.F)
        assertEquals("Great day to be outside", o.today.text)
        assertEquals(listOf("Great all week"), o.lines())
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
        assertEquals("How the outlook works", e.title)
        assertEquals("Mixed", e.value)
        assertEquals("Today: 59 out of 100", e.detail)
        assertTrue(e.now, e.now.startsWith("Today scores 59 out of 100, from its best 3 hours of daylight still ahead (10 AM–1 PM)."))
        assertTrue(e.now, e.now.endsWith("Rain falls in at least a third of its hours, so it can't score higher than mixed."))
        assertTrue(e.meaning, e.meaning.contains("A day with rain in a third of its hours or more is mixed at best"))
        assertTrue(e.meaning, e.meaning.contains("outside 12–26°C."))
        assertTrue(explain(Term.WEEK, f, TempUnit.F).meaning.contains("outside 54–79°F."))
        val evening = explain(Term.WEEK, TestData.synthetic(List(7) { fine }, monday.withHour(20)), TempUnit.F)
        assertTrue(evening.now, evening.now.startsWith("Today's daylight is over, so the outlook looks at tomorrow. Tomorrow scores 100"))
    }
}
