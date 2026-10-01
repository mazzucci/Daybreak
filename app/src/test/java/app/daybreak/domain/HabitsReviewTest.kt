package app.daybreak.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** Goal history, part weeks, banking, caps, clock skew and the words and map that follow from them. */
class HabitsReviewTest {
    private val uk = WeekFields.of(Locale.UK) // Monday-first
    private val monday = LocalDate.of(2026, 9, 28)

    private fun d(day: Int, month: Int = 9) = LocalDate.of(2026, month, day)

    private fun days(from: LocalDate, to: LocalDate, count: Int): Map<LocalDate, Int> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.associateWith { count }

    private fun build(target: Int, period: HabitPeriod = HabitPeriod.DAY, created: LocalDate = d(1), log: Map<LocalDate, Int> = emptyMap()) =
        Habit("b", "Drink water", HabitColor.BLUE, HabitKind.BUILD, period, target, created, log)

    private fun avoid(allowance: Int, period: HabitPeriod = HabitPeriod.WEEK, created: LocalDate = d(1), log: Map<LocalDate, Int> = emptyMap()) =
        Habit("a", "No takeout", HabitColor.CORAL, HabitKind.AVOID, period, allowance, created, log)

    // --- Goal history ---

    @Test fun `raising the goal never re-judges the past`() {
        val before = build(2, created = d(21), log = days(d(21), d(27), 2))
        val after = before.withGoal(HabitKind.BUILD, HabitPeriod.DAY, 5, monday)
        assertEquals(2, after.goals.size)
        val was = habitStats(before, monday, uk)
        val now = habitStats(after, monday, uk)
        // The 21st to the 27th were met at 2 a day and stay met: the streak, best and points are all kept.
        assertEquals(7, now.streak)
        assertEquals(was.bestStreak, now.bestStreak)
        assertEquals(was.points, now.points)
        // Today is judged by the new goal.
        assertEquals("0 of 5 today", progressLine(now))
    }

    @Test fun `lowering the goal gains nothing for the past`() {
        val h = build(8, created = d(21), log = days(d(21), d(27), 4))
        val lowered = h.withGoal(HabitKind.BUILD, HabitPeriod.DAY, 4, monday)
        val was = habitStats(h, monday, uk)
        val now = habitStats(lowered, monday, uk)
        assertEquals(0, now.bestStreak)
        assertEquals(was.points, now.points)
        // Logging 4 today meets the new goal, from today on.
        val met = habitStats(lowered.copy(log = lowered.log + (monday to 4)), monday, uk)
        assertEquals(1, met.streak)
    }

    @Test fun `a goal changed twice in a day keeps the last, and the same goal changes nothing`() {
        val h = build(2, created = d(1))
        assertTrue(h === h.withGoal(HabitKind.BUILD, HabitPeriod.DAY, 2, monday))
        val twice = h.withGoal(HabitKind.BUILD, HabitPeriod.DAY, 3, monday).withGoal(HabitKind.BUILD, HabitPeriod.DAY, 4, monday)
        assertEquals(listOf(2, 4), twice.goals.map { it.target })
        // Back to where it was that day: no change at all.
        assertEquals(listOf(2), h.withGoal(HabitKind.BUILD, HabitPeriod.DAY, 3, monday).withGoal(HabitKind.BUILD, HabitPeriod.DAY, 2, monday).goals.map { it.target })
        // Edited the day it was added: one goal, from that day.
        val fresh = build(2, created = monday).withGoal(HabitKind.AVOID, HabitPeriod.WEEK, 0, monday)
        assertEquals(listOf(HabitGoal(monday, HabitKind.AVOID, HabitPeriod.WEEK, 0)), fresh.goals)
    }

    @Test fun `a weekly goal changed mid-week takes the whole week`() {
        // Wednesday the 30th: 2 logged Monday, then the goal goes from 5 to 2 a week.
        val h = build(5, HabitPeriod.WEEK, created = d(7), log = mapOf(d(28) to 2))
            .withGoal(HabitKind.BUILD, HabitPeriod.WEEK, 2, d(30))
        val s = habitStats(h, d(30), uk)
        assertEquals(2, s.count)
        assertTrue(s.onTrack)
    }

    @Test fun `switching daily to weekly leaves the old goal a part week`() {
        // Daily 1 since the 7th, met every day; on Wednesday the 30th it becomes 3 a week.
        val h = build(1, created = d(7), log = days(d(7), d(29), 1) + (d(30) to 1))
            .withGoal(HabitKind.BUILD, HabitPeriod.WEEK, 3, d(30))
        val daily = habitStats(build(1, created = d(7), log = days(d(7), d(29), 1)), d(29), uk)
        val s = habitStats(h, d(30), uk)
        // The days up to Tuesday stay met (their points and the daily best are kept); the week counts from Wednesday.
        assertEquals(daily.progress.dailyRun, s.progress.dailyRun)
        assertEquals(1, s.count)
        assertEquals(0, s.streak)
        assertTrue(s.points >= daily.points)
    }

    @Test fun `a habit switched from build to avoid counts days since from the switch`() {
        val h = build(1, created = d(1), log = mapOf(d(20) to 1)).withGoal(HabitKind.AVOID, HabitPeriod.WEEK, 0, d(24))
        val s = habitStats(h, monday, uk)
        assertNull(s.daysSinceLast)
        assertEquals(4, s.cleanDays)
        // The build days before still earned their badge.
        assertTrue(s.progress.firstLog)
    }

    // --- Part weeks ---

    @Test fun `a weekly habit added mid-week counts that week only if met`() {
        // Added Wednesday the 23rd, goal 3 a week.
        val met = habitStats(build(3, HabitPeriod.WEEK, created = d(23), log = mapOf(d(23) to 1, d(24) to 1, d(25) to 1)), monday, uk)
        assertEquals(1, met.streak)
        assertEquals(3 * LOG_POINTS + KEPT_WEEK_POINTS, met.points)
        // Unmet, it doesn't count against: this week's goal met makes a streak of one, not a broken one.
        val short = build(3, HabitPeriod.WEEK, created = d(23), log = mapOf(d(23) to 1) + days(d(28), d(30), 1))
        val s = habitStats(short, d(30), uk)
        assertEquals(1, s.streak)
        assertEquals(1, s.bestStreak)
    }

    @Test fun `an avoid habit's part first week earns nothing`() {
        val s = habitStats(avoid(0, created = d(23)), monday, uk)
        assertEquals(0, s.points)
        assertEquals(0, s.streak)
        // A full week after it does.
        val later = habitStats(avoid(0, created = d(16)), monday, uk)
        assertEquals(KEPT_WEEK_POINTS, later.points)
    }

    // --- Points and badges ---

    @Test fun `log points are capped at the goal and at ten a period`() {
        val s = habitStats(build(20, created = d(27), log = mapOf(d(27) to 25)), d(27), uk)
        assertEquals(LOG_POINTS_CAP * LOG_POINTS + KEPT_DAY_POINTS, s.points)
        val w = habitStats(build(3, HabitPeriod.WEEK, created = d(28), log = mapOf(d(28) to 9)), d(28), uk)
        assertEquals(3 * LOG_POINTS + KEPT_WEEK_POINTS, w.points)
    }

    @Test fun `check-ins count at most the goal a day, or ten for an avoid habit`() {
        // 60 days of 5 against a goal of 1: 60 check-ins, not 300.
        assertEquals(60, habitStats(build(1, created = d(30, 7), log = days(d(30, 7), d(27), 5)), monday, uk).progress.checkIns)
        // A slip of 30 in a day is 10 check-ins; 10 such days earn the badge.
        val slips = habitStats(avoid(0, created = d(1), log = days(d(1), d(10), 30)), monday, uk)
        assertEquals(100, slips.progress.checkIns)
        assertTrue(Badge.LOGS_100 in badgesFor(listOf(slips.progress)))
        assertEquals("100 check-ins", Badge.LOGS_100.title)
        assertEquals("A clean month", Badge.MONTH_AVOIDED.title)
    }

    @Test fun `what today and this week earned isn't settled`() {
        // Met yesterday and today: only yesterday is banked if it's deleted.
        val s = habitStats(build(1, created = d(27), log = mapOf(d(27) to 1, monday to 1)), monday, uk)
        assertEquals(2 * (LOG_POINTS + KEPT_DAY_POINTS), s.points)
        assertEquals(LOG_POINTS + KEPT_DAY_POINTS, s.settledPoints)
        // A first log today isn't a settled first step.
        val first = summarize(HabitsData(listOf(build(1, created = monday, log = mapOf(monday to 1)))), monday, uk)
        assertEquals(setOf(Badge.FIRST_LOG), first.badges)
        assertEquals(emptySet<Badge>(), first.settledBadges)
        // A weekly goal met this week isn't settled until the week is over.
        val w = habitStats(build(1, HabitPeriod.WEEK, created = d(21), log = mapOf(d(22) to 1, d(29) to 1)), d(30), uk)
        assertEquals(2 * (LOG_POINTS + KEPT_WEEK_POINTS), w.points)
        assertEquals(LOG_POINTS + KEPT_WEEK_POINTS, w.settledPoints)
        assertEquals(1, w.settledProgress.weeklyRun)
    }

    // --- Clock skew ---

    @Test fun `entries after today are left out, and a future start is today`() {
        val h = build(1, created = d(2, 10), log = mapOf(d(1, 10) to 1, monday to 1))
        val s = habitStats(h, monday, uk)
        assertEquals(1, s.count)
        assertEquals(0, s.daysTracked)
        assertEquals(1, s.progress.checkIns)
        val ahead = summarize(HabitsData(listOf(build(1, created = d(20), log = days(d(29), d(30, 11), 1)))), monday, uk)
        assertEquals(emptySet<Badge>(), ahead.badges)
        assertEquals(0, ahead.points)
        // The map counts today as tracked even though the habit says it starts later.
        val cells = heatMap(h, monday, uk).flatten().filterNotNull()
        assertTrue(cells.single { it.date == monday }.tracked)
        assertFalse(cells.single { it.date == d(27) }.tracked)
    }

    // --- The map and its words ---

    @Test fun `a daily allowance's kept days aren't slips`() {
        val coffee = avoid(2, HabitPeriod.DAY, created = d(21), log = mapOf(d(21) to 2, d(22) to 1, d(23) to 3))
        val week = heatMap(coffee, monday, uk)[10]
        assertEquals(HEAT_KEPT, week[0]!!.strength) // 2 of 2: kept
        assertEquals(HEAT_KEPT, week[1]!!.strength) // 1 of 2
        assertTrue(week[2]!!.slip) // 3 of 2
        assertFalse(week[0]!!.slip)
        assertEquals(HEAT_KEPT, week[3]!!.strength) // none
        // A weekly allowance marks any day with one.
        val takeout = heatMap(avoid(1, created = d(21), log = mapOf(d(22) to 1)), monday, uk)[10]
        assertTrue(takeout[1]!!.slip)
        assertEquals(HEAT_KEPT, takeout[0]!!.strength)
    }

    @Test fun `the map in words counts only tracked days`() {
        val h = build(2, created = d(24), log = mapOf(d(24) to 2, d(25) to 1, d(26) to 2, d(20) to 5))
        assertEquals("Goal met on 2 of the last 5 days", heatSummary(h, heatMap(h, monday, uk)))
        val a = avoid(0, created = d(24), log = mapOf(d(20) to 3, d(25) to 1))
        assertEquals("1 in the last 12 weeks", heatSummary(a, heatMap(a, monday, uk)))
    }

    @Test fun `none allowed and none this week drops the allowance line`() {
        assertEquals(
            listOf("Not once in 27 days", "None this week"),
            habitLines(habitStats(avoid(0, created = d(1)), monday, uk)),
        )
        assertEquals(
            listOf("Last one today", "1 this week", "Over the allowance this week"),
            habitLines(habitStats(avoid(0, created = d(1), log = mapOf(monday to 1)), monday, uk)),
        )
        // An allowance of one keeps its line.
        assertEquals(3, habitLines(habitStats(avoid(1, created = d(1)), monday, uk)).size)
        assertEquals(listOf("0 of 8 today", "Start a streak today"), habitLines(habitStats(build(8, created = monday), monday, uk)))
    }

    @Test fun `weeks can start on any stored day`() {
        val sat = HabitsData(weekStart = DayOfWeek.SATURDAY).weekFields
        assertEquals(d(26), weekStart(monday, sat))
        assertEquals(d(26), heatMap(build(1), monday, sat).last()[0]!!.date)
    }

    // --- A long history ---

    @Test fun `thirty habits over five years add up`() {
        val start = monday.minusYears(5)
        val total = java.time.temporal.ChronoUnit.DAYS.between(start, monday).toInt() + 1
        val habits = (0 until 30).map { i ->
            when (i % 3) {
                // Met every day: one long streak.
                0 -> Habit("d$i", "Daily $i", HabitColor.BLUE, HabitKind.BUILD, HabitPeriod.DAY, 2, start, days(start, monday, 2))
                // Twice a week, Monday-first, every week.
                1 -> Habit(
                    "w$i", "Weekly $i", HabitColor.GREEN, HabitKind.BUILD, HabitPeriod.WEEK, 2, start,
                    days(start, monday, 1).filterKeys { it.dayOfWeek == DayOfWeek.MONDAY || it.dayOfWeek == DayOfWeek.TUESDAY },
                )
                // Never once.
                else -> Habit("a$i", "No sugar $i", HabitColor.CORAL, HabitKind.AVOID, HabitPeriod.DAY, 0, start)
            }
        }
        val summary = summarize(HabitsData(habits, weekStart = DayOfWeek.MONDAY), monday)
        val daily = summary.of("d0")!!
        assertEquals(total, daily.streak)
        val bonuses = MILESTONE_BONUS.values.sum()
        assertEquals(total * (2 * LOG_POINTS + KEPT_DAY_POINTS) + bonuses, daily.points)
        val avoid = summary.of("a2")!!
        assertEquals(total - 1, avoid.streak) // today isn't over
        assertEquals(total - 1, avoid.cleanDays)
        val weekly = summary.of("w1")!!
        assertTrue(weekly.streak > 250)
        assertEquals(weekly.streak, weekly.bestStreak)
        assertEquals(Badge.entries.toSet(), summary.badges)
        assertEquals(10 * daily.points + 10 * weekly.points + 10 * avoid.points, summary.points)
    }
}
