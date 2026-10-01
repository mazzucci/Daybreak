package app.daybreak.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/** A good habit to do more of, or a bad one to keep under an allowance. */
enum class HabitKind { BUILD, AVOID }

/** What a habit's count is measured over: each day, or each week (starting on the stored first day of the week). */
enum class HabitPeriod { DAY, WEEK }

/** The habit palette, stored by name; the UI gives each a light- and a dark-theme shade. */
enum class HabitColor(val label: String) {
    BLUE("Blue"), TEAL("Teal"), GREEN("Green"), OLIVE("Olive"), AMBER("Amber"), CORAL("Coral"), PINK("Pink"), PURPLE("Purple"),
}

const val HABIT_TITLE_MAX = 40
const val HABIT_COUNT_MAX = 99

/**
 * A habit's goal from [from] on. [target] is the count to reach each period for a build habit, and the most allowed
 * each period for an avoid one (0 for none at all).
 */
data class HabitGoal(val from: LocalDate, val kind: HabitKind, val period: HabitPeriod, val target: Int) {
    fun sameGoal(kind: HabitKind, period: HabitPeriod, target: Int) = this.kind == kind && this.period == period && this.target == target
}

/**
 * One habit and its log. [goals] holds every goal it has had, oldest first, each from the day it was set; the
 * first is from [created] and the last is the current one. Each day or week is judged by the goal in force then,
 * so editing a habit never re-judges the past. Days and weeks before [created] count neither for nor against it.
 */
data class Habit(
    val id: String,
    val title: String,
    val color: HabitColor,
    val created: LocalDate,
    val goals: List<HabitGoal>,
    /** The count logged on each day; days with nothing logged are absent. */
    val log: Map<LocalDate, Int> = emptyMap(),
) {
    /** A habit with one goal from the day it was added. */
    constructor(
        id: String,
        title: String,
        color: HabitColor = HabitColor.BLUE,
        kind: HabitKind = HabitKind.BUILD,
        period: HabitPeriod = HabitPeriod.DAY,
        target: Int = 1,
        created: LocalDate,
        log: Map<LocalDate, Int> = emptyMap(),
    ) : this(id, title, color, created, listOf(HabitGoal(created, kind, period, target)), log)

    init {
        require(goals.isNotEmpty()) { "A habit always has a goal" }
    }

    /** The goal in force now. */
    val goal: HabitGoal get() = goals.last()
    val kind: HabitKind get() = goal.kind
    val period: HabitPeriod get() = goal.period
    val target: Int get() = goal.target
}

/**
 * This habit with a new goal from [today]. Earlier goals stay as they were; one already set today (or, after the
 * clock went back, later) is replaced. The same goal again changes nothing.
 */
fun Habit.withGoal(kind: HabitKind, period: HabitPeriod, target: Int, today: LocalDate): Habit {
    if (goal.sameGoal(kind, period, target)) return this
    val earlier = goals.filter { it.from.isBefore(today) }
    val next = when {
        earlier.isEmpty() -> listOf(HabitGoal(minOf(created, today), kind, period, target))
        earlier.last().sameGoal(kind, period, target) -> earlier
        else -> earlier + HabitGoal(today, kind, period, target)
    }
    return copy(goals = next)
}

/** What the add/edit dialog and the presets produce: a habit without its id, start day or log. */
data class HabitDraft(
    val title: String,
    val color: HabitColor = HabitColor.BLUE,
    val kind: HabitKind = HabitKind.BUILD,
    val period: HabitPeriod = HabitPeriod.DAY,
    val target: Int = 1,
) {
    fun toHabit(id: String, created: LocalDate) = Habit(id, title, color, kind, period, target, created)
}

fun Habit.toDraft() = HabitDraft(title, color, kind, period, target)

/** An avoid habit with some allowed each day (coffee, at most 2 a day): it's about today's count, not days without. */
val Habit.isDailyAllowance: Boolean get() = kind == HabitKind.AVOID && period == HabitPeriod.DAY && target > 0

/** The empty state's one-tap starts. */
val HABIT_PRESETS = listOf(
    HabitDraft("Drink water", HabitColor.BLUE, HabitKind.BUILD, HabitPeriod.DAY, 8),
    HabitDraft("Exercise", HabitColor.GREEN, HabitKind.BUILD, HabitPeriod.WEEK, 3),
    HabitDraft("No takeout", HabitColor.CORAL, HabitKind.AVOID, HabitPeriod.WEEK, 0),
)

/** The few badges there are. Once earned they stay, even if the habit that earned them is deleted. */
enum class Badge(val title: String, val detail: String) {
    FIRST_LOG("First step", "Logged a habit for the first time"),
    STREAK_7("7-day streak", "A daily goal met 7 days in a row"),
    STREAK_30("30-day streak", "A daily goal met 30 days in a row"),
    STREAK_100("100-day streak", "A daily goal met 100 days in a row"),
    WEEKS_4("4 good weeks", "A weekly goal met 4 weeks running"),
    MONTH_AVOIDED("A clean month", "Kept within an allowance for a month"),
    LOGS_100("100 check-ins", "Checked in 100 times"),
}

/**
 * Everything stored: the habits in display order, the points and badges of habits since deleted, and the first
 * day of the week, set when the habits were first stored so a change of language or region doesn't re-bucket the
 * past (Settings can change it on purpose).
 */
data class HabitsData(
    val habits: List<Habit> = emptyList(),
    val bankedPoints: Int = 0,
    val bankedBadges: Set<Badge> = emptySet(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** Whether Home has shown its "Hold to undo" hint, which it does once. */
    val undoHintShown: Boolean = false,
) {
    val weekFields: WeekFields get() = WeekFields.of(weekStart, 1)
}

// --- Points ----------------------------------------------------------------------------------------------------

/** Per logged unit of a build habit, up to its goal for the period and at most [LOG_POINTS_CAP] a period. */
const val LOG_POINTS = 1
const val LOG_POINTS_CAP = 10
/** Per day a daily goal is met, or a daily allowance kept. */
const val KEPT_DAY_POINTS = 10
/** Per week a weekly goal is met, or a weekly allowance kept. */
const val KEPT_WEEK_POINTS = 50
/** Bonuses when a run of met (or kept) days or weeks reaches 7, 30 and 100. */
val MILESTONE_BONUS = mapOf(7 to 50, 30 to 150, 100 to 500)
/** The most a day of an avoid habit adds to the check-ins badge. */
const val AVOID_CHECK_INS_PER_DAY = 10

/** The first day of [date]'s week, by [weekFields]. */
fun weekStart(date: LocalDate, weekFields: WeekFields): LocalDate = date.with(weekFields.dayOfWeek(), 1)

/** The first day of the day or week [date] falls in. */
fun periodStart(date: LocalDate, period: HabitPeriod, weekFields: WeekFields): LocalDate =
    if (period == HabitPeriod.DAY) date else weekStart(date, weekFields)

private fun nextPeriod(start: LocalDate, period: HabitPeriod) = if (period == HabitPeriod.DAY) start.plusDays(1) else start.plusWeeks(1)

/** A goal and the days it judges, [start] to [end] inclusive. */
internal data class GoalSpan(val goal: HabitGoal, val start: LocalDate, val end: LocalDate)

/**
 * The days each goal judges, oldest first, from the day the habit was added (or today, if that's somehow later) to
 * today. A goal changed within a period takes over that whole period when it's still a daily (or weekly) goal,
 * since the period was still open when it was changed; a switch between daily and weekly takes over from its day,
 * leaving the old goal a part week.
 */
internal fun goalSpans(habit: Habit, today: LocalDate, weekFields: WeekFields): List<GoalSpan> {
    val created = minOf(habit.created, today)
    val starts = ArrayList<Pair<HabitGoal, LocalDate>>()
    habit.goals.forEach { g ->
        val start = if (starts.isEmpty()) {
            created
        } else {
            val from = minOf(maxOf(g.from, created), today)
            maxOf(if (starts.last().first.period == g.period) periodStart(from, g.period, weekFields) else from, created)
        }
        // A goal from no later than the one before it replaces it.
        while (starts.isNotEmpty() && !start.isAfter(starts.last().second)) starts.removeAt(starts.lastIndex)
        starts += g to start
    }
    return starts.mapIndexed { i, (g, start) ->
        GoalSpan(g, start, starts.getOrNull(i + 1)?.second?.minusDays(1) ?: today)
    }
}

/** What the badges are judged on, for one habit. */
data class BadgeProgress(
    /** A build habit has been logged at least once. */
    val firstLog: Boolean = false,
    /** The longest runs of met daily and weekly goals, and of kept daily and weekly allowances. */
    val dailyRun: Int = 0,
    val weeklyRun: Int = 0,
    val avoidDayRun: Int = 0,
    val avoidWeekRun: Int = 0,
    /** Logs, at most the goal a day for a build habit and [AVOID_CHECK_INS_PER_DAY] for an avoid one. */
    val checkIns: Int = 0,
)

/** One habit worked out for [today]. */
data class HabitStats(
    val habit: Habit,
    /** Today's count for a daily habit, this week's for a weekly one (since the goal took over, in a part week). */
    val count: Int,
    /** Build: the goal is reached this period. Avoid: still within the allowance this period. */
    val onTrack: Boolean,
    /**
     * Build: met days (or weeks) in a row up to now; today (or this week) counts once it's met and doesn't break the
     * run while it's still open. Avoid: kept days (or weeks) in a row, counting finished ones only. A run restarts
     * when a habit changes between build and avoid, or between daily and weekly.
     */
    val streak: Int,
    val bestStreak: Int,
    /** Days since the last one was logged (0 today), since it became this kind of habit; null if none since. */
    val daysSinceLast: Int?,
    /** Days since the habit was added, or since it last changed between build and avoid (0 on that first day). */
    val daysTracked: Int,
    /** A daily avoid habit's days this week so far (from when its goal took over) and how many kept the allowance. */
    val daysThisWeek: Int,
    val keptDaysThisWeek: Int,
    /** Everything this habit has earned: logs, met or kept periods, and milestone bonuses. */
    val points: Int,
    /** Milestone bonuses earned so far (a run reaching 7, 30 or 100), so a new one can be celebrated. */
    val milestones: Int,
    /** What finished periods earned: what's banked if the habit is deleted (not today, or this week). */
    val settledPoints: Int = points,
    val progress: BadgeProgress = BadgeProgress(),
    val settledProgress: BadgeProgress = progress,
) {
    /** An avoid habit's clean run in days: since the last one, or since it was added if there's never been one. */
    val cleanDays: Int get() = daysSinceLast ?: daysTracked
}

/**
 * Works out [habit] as of [today]: walks every day (or week) from the one it was added in, each judged by the goal
 * in force then, keeping the run of met (or kept) periods, the best run, and the points along the way. Anything
 * logged after today (the clock was ahead) is left out.
 *
 * A week cut short (the habit was added mid-week, or switched between daily and weekly) counts for a build habit
 * only if its goal was met, and never against it; for an avoid habit it earns nothing, though going over still
 * ends the run.
 */
fun habitStats(habit: Habit, today: LocalDate, weekFields: WeekFields): HabitStats {
    val spans = goalSpans(habit, today, weekFields)
    var run = 0
    var runType: Pair<HabitKind, HabitPeriod>? = null
    val best = HashMap<Pair<HabitKind, HabitPeriod>, Int>()
    var points = 0
    var milestones = 0
    var checkIns = 0
    var firstLog = false
    var settledPoints = 0
    var settledProgress: BadgeProgress? = null
    var count = 0

    fun progress() = BadgeProgress(
        firstLog = firstLog,
        dailyRun = best[HabitKind.BUILD to HabitPeriod.DAY] ?: 0,
        weeklyRun = best[HabitKind.BUILD to HabitPeriod.WEEK] ?: 0,
        avoidDayRun = best[HabitKind.AVOID to HabitPeriod.DAY] ?: 0,
        avoidWeekRun = best[HabitKind.AVOID to HabitPeriod.WEEK] ?: 0,
        checkIns = checkIns,
    )

    spans.forEachIndexed { i, span ->
        val g = span.goal
        val build = g.kind == HabitKind.BUILD
        val type = g.kind to g.period
        if (type != runType) {
            run = 0
            runType = type
        }
        val keptPoints = if (g.period == HabitPeriod.DAY) KEPT_DAY_POINTS else KEPT_WEEK_POINTS
        var p = periodStart(span.start, g.period, weekFields)
        while (!p.isAfter(span.end)) {
            val fullEnd = nextPeriod(p, g.period).minusDays(1)
            val from = maxOf(p, span.start)
            val to = minOf(fullEnd, span.end)
            // Only the last goal's period holding today is still open.
            val open = i == spans.lastIndex && !fullEnd.isBefore(today)
            if (open && settledProgress == null) {
                settledPoints = points
                settledProgress = progress()
            }
            val part = from.isAfter(p) || (to.isBefore(fullEnd) && !open)
            var total = 0
            var d = from
            while (!d.isAfter(to)) {
                val n = habit.log[d] ?: 0
                if (n > 0) {
                    total += n
                    checkIns += minOf(n, if (build) g.target else AVOID_CHECK_INS_PER_DAY)
                    if (build) firstLog = true
                }
                d = d.plusDays(1)
            }
            if (open) count = total
            if (build) points += minOf(total, g.target, LOG_POINTS_CAP) * LOG_POINTS
            val kept = total >= g.target && build || total <= g.target && !build
            val counts = when {
                build -> kept
                // An allowance is only kept once its day or week is over, and a part week's is too easy to count.
                else -> kept && !open && !part
            }
            when {
                counts -> {
                    run++
                    points += keptPoints
                    MILESTONE_BONUS[run]?.let { points += it; milestones++ }
                }
                // An open period can still be met (or kept); a part week only counts for, never against.
                open -> Unit
                build && part -> Unit
                !build && kept -> Unit
                else -> run = 0
            }
            best[type] = maxOf(best[type] ?: 0, run)
            p = nextPeriod(p, g.period)
        }
    }

    val current = spans.last()
    val goal = current.goal
    val build = goal.kind == HabitKind.BUILD
    // Days since the last one, and the days tracked, run from when the habit became this kind.
    val kindSince = spans.takeLastWhile { it.goal.kind == goal.kind }.first().start
    val last = habit.log.filter { (d, n) -> n > 0 && !d.isAfter(today) && !d.isBefore(kindSince) }.keys.maxOrNull()
    val weekFrom = maxOf(weekStart(today, weekFields), current.start)
    val daysThisWeek = ChronoUnit.DAYS.between(weekFrom, today).toInt() + 1
    val keptDays = if (goal.period == HabitPeriod.DAY && !build) {
        (0 until daysThisWeek).count { (habit.log[weekFrom.plusDays(it.toLong())] ?: 0) <= goal.target }
    } else 0
    val progress = progress()
    return HabitStats(
        habit = habit,
        count = count,
        onTrack = if (build) count >= goal.target else count <= goal.target,
        streak = run,
        bestStreak = best[goal.kind to goal.period] ?: 0,
        daysSinceLast = last?.let { ChronoUnit.DAYS.between(it, today).toInt() },
        daysTracked = ChronoUnit.DAYS.between(kindSince, today).toInt(),
        daysThisWeek = daysThisWeek,
        keptDaysThisWeek = keptDays,
        points = points,
        milestones = milestones,
        settledPoints = settledPoints,
        progress = progress,
        settledProgress = settledProgress ?: progress,
    )
}

/** A level from points: level n starts at 50·n·(n−1) points, so each level takes 100 more than the last. */
data class Level(val number: Int, val points: Int, val start: Int, val next: Int) {
    val toGo: Int get() = next - points
    val progress: Float get() = (points - start).toFloat() / (next - start)
}

fun levelStart(n: Int): Int = 50 * n * (n - 1)

fun levelFor(points: Int): Level {
    var n = 1
    while (points >= levelStart(n + 1)) n++
    return Level(n, points, levelStart(n), levelStart(n + 1))
}

/** The badges this progress adds up to (check-ins add up across habits; runs are each habit's own). */
fun badgesFor(progress: List<BadgeProgress>): Set<Badge> {
    val daily = progress.maxOfOrNull { it.dailyRun } ?: 0
    val weekly = progress.maxOfOrNull { it.weeklyRun } ?: 0
    val checkIns = progress.sumOf { it.checkIns }
    return buildSet {
        if (progress.any { it.firstLog }) add(Badge.FIRST_LOG)
        if (daily >= 7) add(Badge.STREAK_7)
        if (daily >= 30) add(Badge.STREAK_30)
        if (daily >= 100) add(Badge.STREAK_100)
        if (weekly >= 4) add(Badge.WEEKS_4)
        if (progress.any { it.avoidDayRun >= 30 || it.avoidWeekRun >= 4 }) add(Badge.MONTH_AVOIDED)
        if (checkIns >= 100) add(Badge.LOGS_100)
    }
}

/** The badges [stats] have earned (not counting ones banked from deleted habits). */
fun earnedBadges(stats: List<HabitStats>): Set<Badge> = badgesFor(stats.map { it.progress })

/** All habits worked out for [today], with the points, level and badges they add up to. */
data class HabitsSummary(
    val stats: List<HabitStats>,
    val points: Int,
    val level: Level,
    val badges: Set<Badge>,
    val today: LocalDate = LocalDate.MIN,
    val weekFields: WeekFields = WeekFields.ISO,
    /** The badges finished periods have earned, which is what deleting a habit banks. */
    val settledBadges: Set<Badge> = badges,
) {
    fun of(id: String): HabitStats? = stats.firstOrNull { it.habit.id == id }
}

fun summarize(data: HabitsData, today: LocalDate, weekFields: WeekFields = data.weekFields): HabitsSummary {
    val stats = data.habits.map { habitStats(it, today, weekFields) }
    val points = data.bankedPoints + stats.sumOf { it.points }
    return HabitsSummary(
        stats = stats,
        points = points,
        level = levelFor(points),
        badges = data.bankedBadges + earnedBadges(stats),
        today = today,
        weekFields = weekFields,
        settledBadges = data.bankedBadges + badgesFor(stats.map { it.settledProgress }),
    )
}

/**
 * The one line to show after logging [id], or null for an ordinary +1: a milestone ("7-day streak! +61"), then a
 * new level, then a new badge, then the goal met for the day or week. Logging an avoid habit is never celebrated.
 */
fun celebrate(before: HabitsSummary, after: HabitsSummary, id: String): String? {
    val b = before.of(id) ?: return null
    val a = after.of(id) ?: return null
    if (a.habit.kind == HabitKind.AVOID) return null
    val gained = after.points - before.points
    if (gained <= 0) return null
    val plus = "+$gained"
    val newBadge = Badge.entries.firstOrNull { it in after.badges && it !in before.badges }
    val unit = if (a.habit.period == HabitPeriod.DAY) "day" else "week"
    return when {
        a.milestones > b.milestones -> "${a.streak}-$unit streak! $plus"
        after.level.number > before.level.number -> "Level ${after.level.number}! $plus"
        newBadge != null -> "New badge: ${newBadge.title} · $plus"
        a.onTrack && !b.onTrack -> (if (a.habit.period == HabitPeriod.DAY) "Done for today · " else "Done for this week · ") + plus
        else -> null
    }
}

// --- Words ------------------------------------------------------------------------------------------------------

/** "1 day", "7 days", "3 weeks". */
fun periods(n: Int, period: HabitPeriod): String = if (period == HabitPeriod.DAY) plural(n, "day") else plural(n, "week")

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

private fun HabitPeriod.span() = if (this == HabitPeriod.DAY) "today" else "this week"

/** The goal in a few words: "8 a day", "3 a week", "None a week", "At most 2 a day". */
fun goalLabel(kind: HabitKind, period: HabitPeriod, target: Int): String {
    val per = if (period == HabitPeriod.DAY) "a day" else "a week"
    return when {
        kind == HabitKind.BUILD -> "$target $per"
        target == 0 -> "None $per"
        else -> "At most $target $per"
    }
}

/** Where this period stands: "5 of 8 today", "1 of 3 this week", "None this week", "1 of 2 allowed today". */
fun progressLine(s: HabitStats): String {
    val h = s.habit
    val span = h.period.span()
    return when {
        h.kind == HabitKind.BUILD -> "${s.count} of ${h.target} $span"
        h.target == 0 -> if (s.count == 0) "None $span" else "${s.count} $span"
        s.count <= h.target -> "${s.count} of ${h.target} allowed $span"
        else -> "${s.count} $span, ${h.target} allowed"
    }
}

/**
 * The run line. Build: "4-day streak · best 12", "Best streak 12 days", "Start a streak today". Avoid: whether the
 * allowance is kept this week, said plainly, never as a broken streak.
 */
fun streakLine(s: HabitStats): String {
    val h = s.habit
    if (h.kind == HabitKind.AVOID) {
        return if (h.period == HabitPeriod.WEEK) {
            if (s.onTrack) "Allowance kept this week" else "Over the allowance this week"
        } else if (s.keptDaysThisWeek == s.daysThisWeek) {
            if (s.daysThisWeek == 1) "Kept today" else "Kept every day this week"
        } else {
            "Kept ${s.keptDaysThisWeek} of ${s.daysThisWeek} days this week"
        }
    }
    val unit = if (h.period == HabitPeriod.DAY) "day" else "week"
    return when {
        s.streak > 0 && s.bestStreak > s.streak -> "${s.streak}-$unit streak · best ${s.bestStreak}"
        s.streak > 0 -> "${s.streak}-$unit streak"
        s.bestStreak > 0 -> "Best streak ${periods(s.bestStreak, h.period)}"
        else -> "Start a streak ${h.period.span()}"
    }
}

/** An avoid habit's clean run: "12 days since the last one", "Last one today", "Not once in 12 days", "Started today". */
fun sinceLine(s: HabitStats): String {
    val since = s.daysSinceLast
    return when {
        since == 0 -> "Last one today"
        since != null -> "${plural(since, "day")} since the last one"
        s.daysTracked == 0 -> "Started today"
        else -> "Not once in ${plural(s.daysTracked, "day")}"
    }
}

/**
 * The lines under a habit's title, the first being where this period stands. Build habits and daily allowances:
 * the progress, then the run. Other avoid habits: the clean run, the progress, then whether the allowance is kept,
 * which is left out when none are allowed and none were logged this week, as the line above already says so.
 */
fun habitLines(s: HabitStats): List<String> {
    val h = s.habit
    if (h.kind == HabitKind.BUILD || h.isDailyAllowance) return listOf(progressLine(s), streakLine(s))
    val quietWeek = h.target == 0 && if (h.period == HabitPeriod.WEEK) s.count == 0 else s.keptDaysThisWeek == s.daysThisWeek
    return listOfNotNull(sinceLine(s), progressLine(s), streakLine(s).takeUnless { quietWeek })
}

/**
 * Home's line for an avoid habit: "4 days without takeout" when the title says what's avoided ("No takeout",
 * "Avoid sugar"), otherwise "Order food · 4 days since the last one". A daily allowance says where today stands
 * instead ("Coffee · 1 of 2 allowed today"), since a day without one isn't the point.
 */
fun homeAvoidLine(s: HabitStats): String {
    if (s.habit.isDailyAllowance) return "${s.habit.title} · ${progressLine(s)}"
    val what = Regex("^(?:no|avoid|quit|stop|skip|less)\\s+(.+)$", RegexOption.IGNORE_CASE).find(s.habit.title.trim())
        ?.groupValues?.get(1)
    val days = s.cleanDays
    return when {
        what == null -> "${s.habit.title} · ${sinceLine(s).replaceFirstChar { it.lowercase() }}"
        s.daysSinceLast == 0 -> "${s.habit.title} · last one today"
        else -> "${plural(days, "day")} without ${what.replaceFirstChar { it.lowercase() }}"
    }
}

/**
 * A heat-map square: a day, its count, and how strongly to colour it (0 for nothing, up to 1). [met] is a daily
 * goal reached that day; [slip] is an avoid habit over its allowance (for a daily one) or logged at all (weekly).
 */
data class HeatCell(
    val date: LocalDate,
    val count: Int,
    val strength: Float,
    val tracked: Boolean,
    val met: Boolean = false,
    val slip: Boolean = false,
)

/** How strongly an avoid habit's kept days are coloured, so a slip (an open square) stands out against them. */
const val HEAT_KEPT = 0.25f

/**
 * The last [weeks] weeks as columns of 7 days (oldest first, each from the stored first day of the week), up to
 * today; days after today are null. Each day is judged by the goal in force then. A build habit's day is coloured by
 * how much of the day's share of the goal was logged; an avoid habit's kept days are a light tint and a slip is
 * marked, never filled, so it reads as a lapse rather than progress.
 */
fun heatMap(habit: Habit, today: LocalDate, weekFields: WeekFields, weeks: Int = 12): List<List<HeatCell?>> {
    val first = weekStart(today, weekFields).minusWeeks((weeks - 1).toLong())
    val spans = goalSpans(habit, today, weekFields)
    val created = minOf(habit.created, today)
    return (0 until weeks).map { w ->
        (0 until 7).map { d ->
            val date = first.plusDays(w * 7L + d)
            if (date.isAfter(today)) return@map null
            val n = habit.log[date] ?: 0
            val tracked = !date.isBefore(created)
            if (!tracked) return@map HeatCell(date, n, 0f, false)
            val g = (spans.lastOrNull { !date.isBefore(it.start) } ?: spans.first()).goal
            if (g.kind == HabitKind.BUILD) {
                // A weekly goal of 3 is met by three separate days, so each logged day is a third of it.
                val perDay = if (g.period == HabitPeriod.DAY) g.target.coerceAtLeast(1) else 1
                HeatCell(date, n, (n.toFloat() / perDay).coerceAtMost(1f), true, met = g.period == HabitPeriod.DAY && n >= g.target)
            } else {
                // A daily allowance is kept up to its count; a weekly one marks each day with one.
                val slip = n > if (g.period == HabitPeriod.DAY) g.target else 0
                HeatCell(date, n, if (slip) 0f else HEAT_KEPT, true, slip = slip)
            }
        }
    }
}

/** The map in words: "Goal met on 40 of the last 84 days", "9 logged in the last 12 weeks", "2 in the last 12 weeks". */
fun heatSummary(habit: Habit, cells: List<List<HeatCell?>>): String {
    val days = cells.flatten().filterNotNull().filter { it.tracked }
    return when {
        habit.kind == HabitKind.AVOID -> "${days.sumOf { it.count }} in the last 12 weeks"
        habit.period == HabitPeriod.DAY -> "Goal met on ${days.count { it.met }} of the last ${plural(days.size, "day")}"
        else -> "${days.sumOf { it.count }} logged in the last 12 weeks"
    }
}
