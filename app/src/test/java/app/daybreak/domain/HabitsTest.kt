package app.daybreak.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

class HabitsTest {
    /** Weeks from Sunday (US) and from Monday (UK). 2026-09-28 is a Monday. */
    private val us = WeekFields.of(Locale.US)
    private val uk = WeekFields.of(Locale.UK)
    private val monday = LocalDate.of(2026, 9, 28)

    private fun d(day: Int, month: Int = 9) = LocalDate.of(2026, month, day)

    private fun build(target: Int, period: HabitPeriod = HabitPeriod.DAY, created: LocalDate = d(1), log: Map<LocalDate, Int> = emptyMap()) =
        Habit("b", "Drink water", HabitColor.BLUE, HabitKind.BUILD, period, target, created, log)

    private fun avoid(allowance: Int, period: HabitPeriod = HabitPeriod.WEEK, created: LocalDate = d(1), log: Map<LocalDate, Int> = emptyMap()) =
        Habit("a", "No takeout", HabitColor.CORAL, HabitKind.AVOID, period, allowance, created, log)

    /** [count] on each day from [from] to [to], inclusive. */
    private fun days(from: LocalDate, to: LocalDate, count: Int): Map<LocalDate, Int> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.associateWith { count }

    // --- Weeks ---

    @Test fun `weeks start on the locale's first day`() {
        val wednesday = d(30)
        assertEquals(d(27), weekStart(wednesday, us))
        assertEquals(d(28), weekStart(wednesday, uk))
        assertEquals(d(27), weekStart(d(27), us))
        assertEquals(d(21), weekStart(d(27), uk))
    }

    // --- Daily streaks ---

    @Test fun `daily streak counts met days up to today`() {
        val s = habitStats(build(2, log = days(d(25), d(28), 2)), monday, uk)
        assertEquals(4, s.streak)
        assertEquals(4, s.bestStreak)
        assertTrue(s.onTrack)
        assertEquals(2, s.count)
    }

    @Test fun `today not yet met doesn't break the streak`() {
        val s = habitStats(build(2, log = days(d(25), d(27), 2) + (d(28) to 1)), monday, uk)
        assertEquals(3, s.streak)
        assertFalse(s.onTrack)
    }

    @Test fun `a missed day resets the streak and the best is kept`() {
        val log = days(d(10), d(15), 2) + days(d(20), d(22), 2) + days(d(24), d(27), 2)
        val s = habitStats(build(2, log = log), monday, uk)
        assertEquals(4, s.streak)
        assertEquals(6, s.bestStreak)
    }

    @Test fun `a missed yesterday means no current streak`() {
        val s = habitStats(build(1, log = days(d(20), d(26), 1)), monday, uk)
        assertEquals(0, s.streak)
        assertEquals(7, s.bestStreak)
    }

    @Test fun `a short day doesn't count`() {
        val s = habitStats(build(8, log = mapOf(d(27) to 7, d(28) to 8)), monday, uk)
        assertEquals(1, s.streak)
        assertEquals(1, s.bestStreak)
    }

    // --- Weekly streaks ---

    @Test fun `weekly goal counts days across the locale's week`() {
        // Saturday 19th and Sunday 20th: one Monday-first week, but two Sunday-first ones.
        val log = mapOf(d(19) to 1, d(20) to 2)
        val wednesday = d(23)
        assertEquals(1, habitStats(build(3, HabitPeriod.WEEK, log = log), wednesday, uk).bestStreak)
        assertEquals(0, habitStats(build(3, HabitPeriod.WEEK, log = log), wednesday, us).bestStreak)
    }

    @Test fun `weekly streak runs across week boundaries and this week doesn't break it`() {
        // Monday-first weeks of the 14th and 21st met; this week (from the 28th) has one of three so far.
        val log = mapOf(d(14) to 3, d(21) to 1, d(26) to 1, d(27) to 1, d(28) to 1)
        val s = habitStats(build(3, HabitPeriod.WEEK, log = log), monday, uk)
        assertEquals(2, s.streak)
        assertEquals(1, s.count)
        val done = habitStats(build(3, HabitPeriod.WEEK, log = log + (d(28) to 3)), monday, uk)
        assertEquals(3, done.streak)
        assertTrue(done.onTrack)
    }

    @Test fun `a week without the goal resets the weekly streak`() {
        val log = mapOf(d(1) to 1, d(8) to 1)
        val s = habitStats(build(1, HabitPeriod.WEEK, log = log), monday, uk)
        assertEquals(0, s.streak)
        assertEquals(2, s.bestStreak)
    }

    // --- Avoid ---

    @Test fun `days since the last one`() {
        val s = habitStats(avoid(0, log = mapOf(d(10) to 1, d(16) to 2)), monday, uk)
        assertEquals(12, s.daysSinceLast)
        assertEquals(12, s.cleanDays)
        assertEquals("12 days since the last one", sinceLine(s))
    }

    @Test fun `never logged counts from the start`() {
        val s = habitStats(avoid(0, created = d(24)), monday, uk)
        assertNull(s.daysSinceLast)
        assertEquals(4, s.cleanDays)
        assertEquals("Not once in 4 days", sinceLine(s))
        assertEquals("4 days without takeout", homeAvoidLine(s))
        assertEquals("Started today", sinceLine(habitStats(avoid(0, created = monday), monday, uk)))
        assertEquals("Last one today", sinceLine(habitStats(avoid(0, log = mapOf(monday to 1)), monday, uk)))
        assertEquals("1 day since the last one", sinceLine(habitStats(avoid(0, log = mapOf(d(27) to 1)), monday, uk)))
    }

    @Test fun `home line names what's avoided when the title says it`() {
        val s = habitStats(avoid(0, created = d(24)).copy(title = "Order unhealthy food"), monday, uk)
        assertEquals("Order unhealthy food · not once in 4 days", homeAvoidLine(s))
        val today = habitStats(avoid(0, log = mapOf(monday to 1)), monday, uk)
        assertEquals("No takeout · last one today", homeAvoidLine(today))
    }

    @Test fun `a daily allowance is about today`() {
        val s = habitStats(avoid(2, HabitPeriod.DAY, log = mapOf(monday to 1)).copy(title = "Coffee"), monday, uk)
        assertEquals("Coffee · 1 of 2 allowed today", homeAvoidLine(s))
    }

    @Test fun `weekly allowance kept or not`() {
        val kept = habitStats(avoid(1, log = mapOf(d(28) to 1)), d(30), uk)
        assertTrue(kept.onTrack)
        assertEquals("1 of 1 allowed this week", progressLine(kept))
        assertEquals("Allowance kept this week", streakLine(kept))
        val over = habitStats(avoid(1, log = mapOf(d(28) to 1, d(29) to 1)), d(30), uk)
        assertFalse(over.onTrack)
        assertEquals("2 this week, 1 allowed", progressLine(over))
        assertEquals("Over the allowance this week", streakLine(over))
        // In the US the 27th (Sunday) is already this week.
        assertFalse(habitStats(avoid(1, log = mapOf(d(27) to 1, d(29) to 1)), d(30), us).onTrack)
        assertTrue(habitStats(avoid(1, log = mapOf(d(27) to 1, d(29) to 1)), d(30), uk).onTrack)
    }

    @Test fun `none allowed`() {
        assertEquals("None this week", progressLine(habitStats(avoid(0), monday, uk)))
        assertEquals("1 this week", progressLine(habitStats(avoid(0, log = mapOf(monday to 1)), monday, uk)))
    }

    @Test fun `daily allowance counts the kept days this week`() {
        // Wednesday 30th, Monday-first: Mon kept, Tue over, Wed kept so far.
        val s = habitStats(avoid(2, HabitPeriod.DAY, log = mapOf(d(28) to 2, d(29) to 3)), d(30), uk)
        assertEquals(3, s.daysThisWeek)
        assertEquals(2, s.keptDaysThisWeek)
        assertEquals("Kept 2 of 3 days this week", streakLine(s))
        assertEquals("0 of 2 allowed today", progressLine(s))
    }

    @Test fun `a slip doesn't count against finished days`() {
        // Daily allowance of none: kept every finished day but the 20th; today's slip only shows once the day is over.
        val s = habitStats(avoid(0, HabitPeriod.DAY, created = d(14), log = mapOf(d(20) to 1, d(28) to 1)), monday, uk)
        assertEquals(7, s.streak) // 21st to 27th
        assertEquals(7, s.bestStreak)
        assertFalse(s.onTrack)
    }

    // --- Points, levels, badges ---

    @Test fun `points for logs up to the goal and for met days`() {
        val s = habitStats(build(2, created = d(26), log = mapOf(d(26) to 2, d(27) to 3, d(28) to 1)), monday, uk)
        // Logs: 2 + 2 (the third is past the goal) + 1, at 2 each; met days: 26th and 27th.
        assertEquals(5 * LOG_POINTS + 2 * KEPT_DAY_POINTS, s.points)
    }

    @Test fun `milestone bonus at 7 days`() {
        val s = habitStats(build(1, created = d(22), log = days(d(22), d(28), 1)), monday, uk)
        assertEquals(7, s.streak)
        assertEquals(1, s.milestones)
        assertEquals(7 * (LOG_POINTS + KEPT_DAY_POINTS) + 50, s.points)
    }

    @Test fun `weekly goals earn per met week`() {
        val s = habitStats(build(1, HabitPeriod.WEEK, created = d(14), log = mapOf(d(15) to 1, d(22) to 2)), monday, uk)
        assertEquals(2 * LOG_POINTS + 2 * KEPT_WEEK_POINTS, s.points)
    }

    @Test fun `avoid habits earn for finished kept days only`() {
        val s = habitStats(avoid(0, HabitPeriod.DAY, created = d(21)), monday, uk)
        // The 21st to the 27th, with the 7-day bonus; today isn't over yet.
        assertEquals(7 * KEPT_DAY_POINTS + 50, s.points)
        val weekly = habitStats(avoid(0, HabitPeriod.WEEK, created = d(14), log = mapOf(d(22) to 1)), monday, uk)
        assertEquals(KEPT_WEEK_POINTS, weekly.points)
    }

    @Test fun `levels`() {
        assertEquals(Level(1, 0, 0, 100), levelFor(0))
        assertEquals(1, levelFor(99).number)
        assertEquals(2, levelFor(100).number)
        assertEquals(Level(3, 370, 300, 600), levelFor(370))
        val four = levelFor(770)
        assertEquals(4, four.number)
        assertEquals(230, four.toGo)
        assertEquals(0.425f, four.progress, 0.001f)
        assertEquals(5, levelFor(1000).number)
    }

    @Test fun `badges`() {
        fun badges(vararg h: Habit) = earnedBadges(h.map { habitStats(it, monday, uk) })
        assertEquals(emptySet<Badge>(), badges(build(1)))
        assertEquals(setOf(Badge.FIRST_LOG), badges(build(1, log = mapOf(monday to 1))))
        assertEquals(setOf(Badge.FIRST_LOG, Badge.STREAK_7), badges(build(1, log = days(d(20), d(26), 1))))
        assertEquals(
            setOf(Badge.FIRST_LOG, Badge.STREAK_7, Badge.STREAK_30, Badge.LOGS_100),
            badges(build(1, created = d(1, 8), log = days(d(1, 8), d(28), 4))),
        )
        assertEquals(setOf(Badge.FIRST_LOG, Badge.WEEKS_4), badges(build(1, HabitPeriod.WEEK, log = mapOf(d(1) to 1, d(8) to 1, d(15) to 1, d(22) to 1))))
        // A slip isn't a first step.
        assertEquals(emptySet<Badge>(), badges(avoid(0, created = monday, log = mapOf(monday to 1))))
        assertEquals(setOf(Badge.MONTH_AVOIDED), badges(avoid(0, HabitPeriod.DAY, created = d(28, 8))))
        assertEquals(emptySet<Badge>(), badges(avoid(0, HabitPeriod.DAY, created = d(30, 8))))
        assertEquals(setOf(Badge.MONTH_AVOIDED), badges(avoid(1, HabitPeriod.WEEK, created = d(31, 8))))
    }

    @Test fun `summary adds banked points and badges`() {
        val data = HabitsData(listOf(build(1, created = monday, log = mapOf(monday to 1))), bankedPoints = 500, bankedBadges = setOf(Badge.STREAK_30))
        val s = summarize(data, monday, uk)
        assertEquals(500 + LOG_POINTS + KEPT_DAY_POINTS, s.points)
        assertEquals(setOf(Badge.FIRST_LOG, Badge.STREAK_30), s.badges)
        assertEquals(3, s.level.number)
    }

    // --- Celebrations ---

    private fun cheer(before: Habit, after: Habit, banked: Int = 0, badges: Set<Badge> = emptySet()) = celebrate(
        summarize(HabitsData(listOf(before), banked, badges), monday, uk),
        summarize(HabitsData(listOf(after), banked, badges), monday, uk),
        before.id,
    )

    @Test fun `celebrates a met goal`() {
        val h = build(3, created = d(27), log = mapOf(d(27) to 1, monday to 2))
        assertEquals("Done for today · +11", cheer(h, h.copy(log = h.log + (monday to 3)), badges = setOf(Badge.FIRST_LOG)))
        val w = build(2, HabitPeriod.WEEK, created = d(27), log = mapOf(monday to 1))
        assertEquals("Done for this week · +51", cheer(w, w.copy(log = mapOf(monday to 2)), badges = setOf(Badge.FIRST_LOG)))
    }

    @Test fun `a plain log isn't celebrated`() {
        val h = build(8, log = mapOf(d(27) to 8, monday to 2))
        assertNull(cheer(h, h.copy(log = h.log + (monday to 3))))
        // Nor is one past the goal, which earns nothing.
        assertNull(cheer(h.copy(log = h.log + (monday to 8)), h.copy(log = h.log + (monday to 9))))
    }

    @Test fun `celebrates a milestone`() {
        val h = build(1, created = d(22), log = days(d(22), d(27), 1))
        assertEquals("7 days! +61", cheer(h, h.copy(log = h.log + (monday to 1)), banked = 1000, badges = setOf(Badge.FIRST_LOG, Badge.STREAK_7)))
    }

    @Test fun `celebrates a new level, then a new badge`() {
        val h = build(2, created = monday)
        assertEquals("Level 2! +1", cheer(h, h.copy(log = mapOf(monday to 1)), banked = 99, badges = setOf(Badge.FIRST_LOG)))
        assertEquals("New badge: First step · +1", cheer(h, h.copy(log = mapOf(monday to 1))))
    }

    @Test fun `a slip is never celebrated`() {
        val h = avoid(2, created = d(1))
        assertNull(cheer(h, h.copy(log = mapOf(monday to 1))))
    }

    // --- Words and the map ---

    @Test fun `words`() {
        assertEquals("8 a day", goalLabel(HabitKind.BUILD, HabitPeriod.DAY, 8))
        assertEquals("None a week", goalLabel(HabitKind.AVOID, HabitPeriod.WEEK, 0))
        assertEquals("At most 2 a day", goalLabel(HabitKind.AVOID, HabitPeriod.DAY, 2))
        val s = habitStats(build(8, log = days(d(20), d(27), 8) + days(d(1), d(12), 8) + (monday to 5)), monday, uk)
        assertEquals("5 of 8 today", progressLine(s))
        assertEquals("8-day streak · best 12", streakLine(s))
        assertEquals("1-week streak", streakLine(habitStats(build(1, HabitPeriod.WEEK, log = mapOf(monday to 1)), monday, uk)))
        assertEquals("Start a streak today", streakLine(habitStats(build(1), monday, uk)))
        assertEquals("Start a streak this week", streakLine(habitStats(build(1, HabitPeriod.WEEK), monday, uk)))
        assertEquals("Best streak 2 days", streakLine(habitStats(build(1, log = mapOf(d(20) to 1, d(21) to 1)), monday, uk)))
    }

    @Test fun `heat map is 12 locale weeks up to today`() {
        val h = build(4, created = d(20), log = mapOf(d(20) to 2, d(27) to 4, monday to 9))
        val map = heatMap(h, monday, us)
        assertEquals(12, map.size)
        assertTrue(map.all { it.size == 7 })
        // Sunday-first: the last column starts on Sunday the 27th, and only Sunday and Monday are past.
        val last = map.last()
        assertEquals(d(27), last[0]!!.date)
        assertEquals(monday, last[1]!!.date)
        assertTrue(last.drop(2).all { it == null })
        assertEquals(d(27).minusWeeks(11), map.first()[0]!!.date)
        assertEquals(1f, last[0]!!.strength)
        assertEquals(1f, last[1]!!.strength) // past the goal stays full
        assertEquals(0.5f, map[10][0]!!.strength) // the 20th: 2 of 4
        assertFalse(map[9][6]!!.tracked) // the 19th, before it was added
        // Monday-first, today is the first square of the last column.
        assertEquals(monday, heatMap(h, monday, uk).last()[0]!!.date)
    }

    @Test fun `avoid heat map colours clean days and pales slips`() {
        val h = avoid(0, HabitPeriod.DAY, created = d(21), log = mapOf(d(25) to 1))
        val week = heatMap(h, monday, uk)[10] // 21st to 27th
        assertEquals(0.4f, week[0]!!.strength)
        assertEquals(0.05f, week[4]!!.strength)
        assertEquals(0f, heatMap(h, monday, uk)[9][0]!!.strength) // before it was added
    }
}
