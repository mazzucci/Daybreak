package app.daybreak.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/** A good habit to do more of, or a bad one to keep under an allowance. */
enum class HabitKind { BUILD, AVOID }

/** What a habit's count is measured over: each day, or each week (starting on the locale's first day). */
enum class HabitPeriod { DAY, WEEK }

/** The habit palette, stored by name; the UI gives each a light- and a dark-theme shade. */
enum class HabitColor(val label: String) {
    BLUE("Blue"), TEAL("Teal"), GREEN("Green"), OLIVE("Olive"), AMBER("Amber"), CORAL("Coral"), PINK("Pink"), PURPLE("Purple"),
}

const val HABIT_TITLE_MAX = 40
const val HABIT_COUNT_MAX = 99

/**
 * One habit and its log. [target] is the count to reach each period for a build habit, and the most allowed each
 * period for an avoid one (0 for none at all). Days and weeks before [created] count neither for nor against it.
 */
data class Habit(
    val id: String,
    val title: String,
    val color: HabitColor = HabitColor.BLUE,
    val kind: HabitKind = HabitKind.BUILD,
    val period: HabitPeriod = HabitPeriod.DAY,
    val target: Int = 1,
    val created: LocalDate,
    /** The count logged on each day; days with nothing logged are absent. */
    val log: Map<LocalDate, Int> = emptyMap(),
)

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
    MONTH_AVOIDED("A month avoided", "Kept within an allowance for a month"),
    LOGS_100("100 logs", "Logged good habits 100 times"),
}

/** Everything stored: the habits in display order, plus the points and badges of habits since deleted. */
data class HabitsData(
    val habits: List<Habit> = emptyList(),
    val bankedPoints: Int = 0,
    val bankedBadges: Set<Badge> = emptySet(),
)

// --- Points ----------------------------------------------------------------------------------------------------

/** Per logged unit of a build habit, up to its goal for the period (logging past the goal earns nothing more). */
const val LOG_POINTS = 1
/** Per day a daily goal is met, or a daily allowance kept. */
const val KEPT_DAY_POINTS = 10
/** Per week a weekly goal is met, or a weekly allowance kept. */
const val KEPT_WEEK_POINTS = 50
/** Bonuses when a run of met (or kept) days or weeks reaches 7, 30 and 100. */
val MILESTONE_BONUS = mapOf(7 to 50, 30 to 150, 100 to 500)

/** The first day of [date]'s week, by [weekFields] (Sunday in the US, Monday in most of Europe). */
fun weekStart(date: LocalDate, weekFields: WeekFields): LocalDate = date.with(weekFields.dayOfWeek(), 1)

/** The first day of the day or week [date] falls in. */
fun periodStart(date: LocalDate, period: HabitPeriod, weekFields: WeekFields): LocalDate =
    if (period == HabitPeriod.DAY) date else weekStart(date, weekFields)

private fun nextPeriod(start: LocalDate, period: HabitPeriod) = if (period == HabitPeriod.DAY) start.plusDays(1) else start.plusWeeks(1)

/** One habit worked out for [today]. */
data class HabitStats(
    val habit: Habit,
    /** Today's count for a daily habit, this week's for a weekly one. */
    val count: Int,
    /** Build: the goal is reached this period. Avoid: still within the allowance this period. */
    val onTrack: Boolean,
    /**
     * Build: met days (or weeks) in a row up to now; today (or this week) counts once it's met and doesn't break the
     * run while it's still open. Avoid: kept days (or weeks) in a row, counting finished ones only.
     */
    val streak: Int,
    val bestStreak: Int,
    /** Days since the last one was logged (0 today); null if there's never been one. */
    val daysSinceLast: Int?,
    /** Days since the habit was added (0 on its first day). */
    val daysTracked: Int,
    /** A daily avoid habit's days this week so far (from when it was added) and how many kept the allowance. */
    val daysThisWeek: Int,
    val keptDaysThisWeek: Int,
    /** Everything this habit has earned: logs, met or kept periods, and milestone bonuses. */
    val points: Int,
    /** Milestone bonuses earned so far (a run reaching 7, 30 or 100), so a new one can be celebrated. */
    val milestones: Int,
) {
    /** An avoid habit's clean run in days: since the last one, or since it was added if there's never been one. */
    val cleanDays: Int get() = daysSinceLast ?: daysTracked
}

/**
 * Works out [habit] as of [today]: walks every day (or week) from the one it was added in, keeping the run of met
 * (or kept) periods, the best run, and the points along the way.
 */
fun habitStats(habit: Habit, today: LocalDate, weekFields: WeekFields): HabitStats {
    val period = habit.period
    val build = habit.kind == HabitKind.BUILD
    val created = minOf(habit.created, today)
    val totals = HashMap<LocalDate, Int>()
    habit.log.forEach { (date, n) -> if (n > 0 && !date.isAfter(today)) totals.merge(periodStart(date, period, weekFields), n, Int::plus) }
    val current = periodStart(today, period, weekFields)
    val keptPoints = if (period == HabitPeriod.DAY) KEPT_DAY_POINTS else KEPT_WEEK_POINTS

    var run = 0
    var best = 0
    var points = 0
    var milestones = 0
    var p = periodStart(created, period, weekFields)
    while (!p.isAfter(current)) {
        val total = totals[p] ?: 0
        val open = p == current
        if (build) points += minOf(total, habit.target) * LOG_POINTS
        // A build goal counts as soon as it's met; an allowance only once its day or week is over.
        val kept = if (build) total >= habit.target else !open && total <= habit.target
        if (kept) {
            run++
            points += keptPoints
            MILESTONE_BONUS[run]?.let { points += it; milestones++ }
        } else if (!open) {
            run = 0
        }
        best = maxOf(best, run)
        p = nextPeriod(p, period)
    }

    val count = totals[current] ?: 0
    val last = habit.log.filter { (d, n) -> n > 0 && !d.isAfter(today) }.keys.maxOrNull()
    val weekFrom = maxOf(weekStart(today, weekFields), created)
    val daysThisWeek = ChronoUnit.DAYS.between(weekFrom, today).toInt() + 1
    val keptDays = if (period == HabitPeriod.DAY && !build) {
        (0 until daysThisWeek).count { (habit.log[weekFrom.plusDays(it.toLong())] ?: 0) <= habit.target }
    } else 0
    return HabitStats(
        habit = habit,
        count = count,
        onTrack = if (build) count >= habit.target else count <= habit.target,
        streak = run,
        bestStreak = best,
        daysSinceLast = last?.let { ChronoUnit.DAYS.between(it, today).toInt() },
        daysTracked = ChronoUnit.DAYS.between(created, today).toInt(),
        daysThisWeek = daysThisWeek,
        keptDaysThisWeek = keptDays,
        points = points,
        milestones = milestones,
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

/** The badges [stats] have earned (not counting ones banked from deleted habits). */
fun earnedBadges(stats: List<HabitStats>): Set<Badge> {
    val build = stats.filter { it.habit.kind == HabitKind.BUILD }
    val daily = build.filter { it.habit.period == HabitPeriod.DAY }.maxOfOrNull { it.bestStreak } ?: 0
    val weekly = build.filter { it.habit.period == HabitPeriod.WEEK }.maxOfOrNull { it.bestStreak } ?: 0
    val logs = build.sumOf { s -> s.habit.log.values.sum() }
    val avoided = stats.any { s ->
        s.habit.kind == HabitKind.AVOID && s.bestStreak >= if (s.habit.period == HabitPeriod.DAY) 30 else 4
    }
    return buildSet {
        if (logs > 0) add(Badge.FIRST_LOG)
        if (daily >= 7) add(Badge.STREAK_7)
        if (daily >= 30) add(Badge.STREAK_30)
        if (daily >= 100) add(Badge.STREAK_100)
        if (weekly >= 4) add(Badge.WEEKS_4)
        if (avoided) add(Badge.MONTH_AVOIDED)
        if (logs >= 100) add(Badge.LOGS_100)
    }
}

/** All habits worked out for [today], with the points, level and badges they add up to. */
data class HabitsSummary(val stats: List<HabitStats>, val points: Int, val level: Level, val badges: Set<Badge>) {
    fun of(id: String): HabitStats? = stats.firstOrNull { it.habit.id == id }
}

fun summarize(data: HabitsData, today: LocalDate, weekFields: WeekFields): HabitsSummary {
    val stats = data.habits.map { habitStats(it, today, weekFields) }
    val points = data.bankedPoints + stats.sumOf { it.points }
    return HabitsSummary(stats, points, levelFor(points), data.bankedBadges + earnedBadges(stats))
}

/**
 * The one line to show after logging [id], or null for an ordinary +1: a milestone ("7 days! +62"), then a new
 * level, then a new badge, then the goal met for the day or week. Logging an avoid habit is never celebrated.
 */
fun celebrate(before: HabitsSummary, after: HabitsSummary, id: String): String? {
    val b = before.of(id) ?: return null
    val a = after.of(id) ?: return null
    if (a.habit.kind == HabitKind.AVOID) return null
    val gained = after.points - before.points
    if (gained <= 0) return null
    val plus = "+$gained"
    val newBadge = Badge.entries.firstOrNull { it in after.badges && it !in before.badges }
    return when {
        a.milestones > b.milestones -> "${periods(a.streak, a.habit.period)}! $plus"
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

/** A heat-map square: a day, its count, and how strongly to colour it (0 for nothing, up to 1). */
data class HeatCell(val date: LocalDate, val count: Int, val strength: Float, val tracked: Boolean)

/**
 * The last [weeks] weeks as columns of 7 days (oldest first, each from the locale's first day), up to today; days
 * after today are null. A build habit's day is coloured by how much of the day's share of the goal was logged; an
 * avoid habit's clean days are a mid tint and a day with one is paler, so a slip is a lighter square, not a hole.
 */
fun heatMap(habit: Habit, today: LocalDate, weekFields: WeekFields, weeks: Int = 12): List<List<HeatCell?>> {
    val first = weekStart(today, weekFields).minusWeeks((weeks - 1).toLong())
    // A weekly goal of 3 is met by three separate days, so each logged day is a third of it.
    val perDay = if (habit.period == HabitPeriod.DAY) habit.target.coerceAtLeast(1) else 1
    return (0 until weeks).map { w ->
        (0 until 7).map { d ->
            val date = first.plusDays(w * 7L + d)
            if (date.isAfter(today)) return@map null
            val n = habit.log[date] ?: 0
            val tracked = !date.isBefore(habit.created)
            val strength = when {
                !tracked && n == 0 -> 0f
                habit.kind == HabitKind.BUILD -> (n.toFloat() / perDay).coerceAtMost(1f)
                n == 0 -> 0.4f
                else -> 0.05f
            }
            HeatCell(date, n, strength, tracked)
        }
    }
}
