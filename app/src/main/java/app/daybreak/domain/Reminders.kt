package app.daybreak.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

/** At most this many reminders per date. */
const val REMINDERS_MAX = 3

/** When an all-day date's reminders go off unless they say otherwise. */
val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)

private const val DAY_MINUTES = 24 * 60
private const val WEEK_MINUTES = 7 * DAY_MINUTES

/**
 * When to be reminded of a date. Every time is wall-clock in the zone the phone is in when it goes off, like a
 * calendar's local event: a 9 AM reminder is still 9 AM after a flight.
 */
sealed interface Reminder {
    /** Tells two reminders apart when they're stored or picked. */
    val key: String

    /** [days] before the date (0 is on the day) at [at]. Any date can have one; the presets are for all-day dates. */
    data class DaysBefore(val days: Int, val at: LocalTime = DEFAULT_REMINDER_TIME) : Reminder {
        init {
            require(days in 0..DAYS_MAX) { "A reminder is 0 to $DAYS_MAX days before" }
        }

        override val key: String get() = "d$days@$at"
    }

    /**
     * [minutes] before a timed date starts (0 is when it starts). Whole days keep the clock time (a day before 2 PM is
     * 2 PM the day before, even over a daylight-saving change); anything shorter is elapsed time (an hour before
     * 3:30 AM on the night the clocks go forward is 1:30 AM). A date without a time never goes off for one.
     */
    data class MinutesBefore(val minutes: Int) : Reminder {
        init {
            require(minutes in 0..MINUTES_MAX) { "A reminder is 0 to $MINUTES_MAX minutes before" }
        }

        override val key: String get() = "m$minutes"
    }

    companion object {
        /** Eight weeks: further ahead than that isn't a reminder, it's a plan. */
        const val DAYS_MAX = 56
        const val MINUTES_MAX = DAYS_MAX * DAY_MINUTES
    }
}

/** The reminder [Reminder.key] stands for; null if it isn't one. */
fun reminderOf(key: String): Reminder? = runCatching {
    when {
        key.startsWith("d") -> key.drop(1).split("@").let { (days, at) -> Reminder.DaysBefore(days.toInt(), LocalTime.parse(at)) }
        key.startsWith("m") -> Reminder.MinutesBefore(key.drop(1).toInt())
        else -> null
    }
}.getOrNull()

/**
 * Reminders that need the minute: elapsed-time ones (when it starts, minutes or hours before, however many hours,
 * even past a day). Whole days keep the clock time and can be a little late, so they don't count.
 */
fun PersonalDate.hasShortReminder(): Boolean = time != null && reminders.any { it.isElapsed }

/** A minutes-before reminder that's elapsed time rather than whole days (see [Reminder.MinutesBefore]). */
val Reminder.isElapsed: Boolean get() = this is Reminder.MinutesBefore && (minutes == 0 || minutes % DAY_MINUTES != 0)

/** The one-tap choices: on the day, a day and a week before for an all-day date; when it starts down to a day before for a timed one. */
fun reminderPresets(timed: Boolean): List<Reminder> =
    if (timed) {
        listOf(Reminder.MinutesBefore(0), Reminder.MinutesBefore(15), Reminder.MinutesBefore(60), Reminder.MinutesBefore(DAY_MINUTES))
    } else {
        listOf(Reminder.DaysBefore(0), Reminder.DaysBefore(1), Reminder.DaysBefore(7))
    }

/**
 * A date's reminders once it gains or loses its time ([timed]): on the day becomes when it starts and back, whole
 * days and weeks stay as they are, and anything shorter than a day becomes on the day (9 AM). A reminder at a time
 * of its own ("3 days before, 6:30 PM") keeps it either way. Duplicates go, and no more than [REMINDERS_MAX] are kept.
 */
fun remindersFor(reminders: List<Reminder>, timed: Boolean): List<Reminder> =
    reminders.map { r ->
        when {
            timed && r is Reminder.DaysBefore && r.at == DEFAULT_REMINDER_TIME -> Reminder.MinutesBefore(r.days * DAY_MINUTES)
            !timed && r is Reminder.MinutesBefore -> Reminder.DaysBefore(r.minutes / DAY_MINUTES)
            else -> r
        }
    }.distinct().take(REMINDERS_MAX)

/**
 * "On the day, 9 AM", "1 day before", "2 weeks before", "3 days before, 6:30 PM", "When it starts", "15 minutes
 * before", "1 hour before". With [withDefaultTime] false the 9 AM of on the day goes too ("On the day"): for the
 * chips, where the next-reminder line says when.
 */
fun Reminder.label(use24Hour: Boolean = ClockFormat.use24Hour, withDefaultTime: Boolean = true): String {
    val at = (this as? Reminder.DaysBefore)?.at?.takeIf { it != DEFAULT_REMINDER_TIME || (days == 0 && withDefaultTime) }
    return listOfNotNull(lead(), at?.let { shortTime(it, use24Hour) }).joinToString(", ")
}

/** "On the day", "When it starts", or how long before ("1 day before"), without a time. */
private fun Reminder.lead(): String = when {
    this is Reminder.DaysBefore && days == 0 -> "On the day"
    this is Reminder.MinutesBefore && minutes == 0 -> "When it starts"
    else -> "${amount()} before"
}

/** How long before, without "before": "1 day", "2 weeks", "15 minutes", "36 hours". */
private fun Reminder.amount(): String = when (this) {
    is Reminder.DaysBefore -> if (days % 7 == 0) plural(days / 7, "week") else plural(days, "day")
    is Reminder.MinutesBefore -> when {
        minutes % WEEK_MINUTES == 0 -> plural(minutes / WEEK_MINUTES, "week")
        minutes % DAY_MINUTES == 0 -> plural(minutes / DAY_MINUTES, "day")
        minutes % 60 == 0 -> plural(minutes / 60, "hour")
        else -> plural(minutes, "minute")
    }
}

/** How far ahead it goes off, for putting a date's reminders in order: the nearest first. */
private val Reminder.leadMinutes: Int get() = when (this) {
    is Reminder.DaysBefore -> days * DAY_MINUTES - at.toSecondOfDay() / 60 + DEFAULT_REMINDER_TIME.toSecondOfDay() / 60
    is Reminder.MinutesBefore -> minutes
}

/**
 * A date's reminders in one line, nearest first, the plain ones sharing "before": "1 hour and 1 day before", "On
 * the day, 3 days and 1 week before", "When it starts, 15 minutes and 1 hour before". One at a time of its own
 * keeps it after "at" ("3 days before at 6:30 PM"), so its comma doesn't run into the list's.
 */
fun reminderSummary(reminders: List<Reminder>, use24Hour: Boolean = ClockFormat.use24Hour): String {
    val sorted = reminders.sortedBy { it.leadMinutes }
    fun customAt(r: Reminder) = (r as? Reminder.DaysBefore)?.at?.takeIf { it != DEFAULT_REMINDER_TIME }
    val first = sorted.filter { it.lead() != "${it.amount()} before" && customAt(it) == null }.map { it.lead() }
    val plain = sorted.filter { it.lead() == "${it.amount()} before" && customAt(it) == null }.map { it.amount() }
    val custom = sorted.mapNotNull { r -> customAt(r)?.let { "${r.lead()} at ${shortTime(it, use24Hour)}" } }
    val shared = if (plain.isEmpty()) null else "${andList(plain)} before"
    return (first + listOfNotNull(shared) + custom).joinToString(", ")
}

/** "a", "a and b", "a, b and c". */
private fun andList(items: List<String>): String =
    if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " and " + items.last()

/** "9 AM", "9:30 AM", or on the 24-hour clock "09:00". */
fun shortTime(t: LocalTime, use24Hour: Boolean = ClockFormat.use24Hour, locale: Locale = Locale.US): String =
    t.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else if (t.minute == 0) "h a" else "h:mm a", locale))

/** "2:00 PM" or "14:00": a date's own time, always with its minutes. */
fun formatTimeOfDay(t: LocalTime, use24Hour: Boolean = ClockFormat.use24Hour, locale: Locale = Locale.US): String =
    t.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale))

private fun plural(n: Int, unit: String) = if (n == 1) "1 $unit" else "$n ${unit}s"

/**
 * When [this] reminder goes off for [occurrence] (a yearly date's time round, or the date itself) in [zone]. A time
 * that doesn't exist that night (the clocks went forward) moves on by the gap; one that happens twice (they went
 * back) is the first. Null for a minutes-before reminder of a date without a time.
 */
fun Reminder.fireAt(occurrence: PersonalDate, zone: ZoneId): ZonedDateTime? = when (this) {
    is Reminder.DaysBefore -> ZonedDateTime.of(occurrence.start.minusDays(days.toLong()), at, zone)
    is Reminder.MinutesBefore -> occurrence.time?.let { t ->
        val start = occurrence.start.atTime(t)
        if (minutes % DAY_MINUTES == 0) ZonedDateTime.of(start.minusDays((minutes / DAY_MINUTES).toLong()), zone)
        else ZonedDateTime.of(start, zone).minusMinutes(minutes.toLong())
    }
}

/** One reminder of one date going off: for [occurrence] (the date, or a yearly one's time round) at [at]. */
data class ReminderFire(val date: PersonalDate, val occurrence: PersonalDate, val reminder: Reminder, val at: ZonedDateTime) {
    val instant: Instant get() = at.toInstant()

    /**
     * The same for this date, time round and local time however and wherever it's worked out, so a reminder that
     * has gone off is known again after a flight west (and two reminders for the same moment are one).
     */
    val key: String get() = "${dateKey(date)}|${occurrence.start}|${at.toLocalDateTime()}"

    /** One per date and time round: a later reminder for it replaces the earlier one's notification. */
    val notificationKey: String get() = "${dateKey(date)}|${occurrence.start}"
}

/** A date's identity: its id (every stored date has one; see decodePersonalDates), else its first day and name. */
fun dateKey(d: PersonalDate): String = d.id.ifBlank { "${d.start}|${d.name}" }

/**
 * Every reminder of [dates] that goes off after [from] and no later than [to], soonest first (then by name, so
 * the order is stable). Yearly dates repeat theirs every year.
 */
fun reminderFires(dates: List<PersonalDate>, from: Instant, to: Instant, zone: ZoneId): List<ReminderFire> {
    if (!to.isAfter(from)) return emptyList()
    val years = from.atZone(zone).year - 1..to.atZone(zone).year + 1
    return dates.asSequence()
        .filter { it.reminders.isNotEmpty() }
        .flatMap { d ->
            val rounds = if (d.yearly) years.map(d::inYear) else listOf(d)
            rounds.asSequence().flatMap { occ -> d.reminders.asSequence().mapNotNull { r -> r.fireAt(occ, zone)?.let { ReminderFire(d, occ, r, it) } } }
        }
        .filter { it.instant.isAfter(from) && !it.instant.isAfter(to) }
        .distinctBy { it.key }
        .sortedWith(compareBy<ReminderFire>({ it.instant }, { it.date.title }))
        .toList()
}

/** The soonest reminder of all [dates] after [after], leaving out those whose key is in [done]. */
fun nextReminder(dates: List<PersonalDate>, after: Instant, zone: ZoneId, done: Set<String> = emptySet()): ReminderFire? =
    // Two years and a bit reaches every yearly date's next time round plus its earliest reminder.
    reminderFires(dates, after, after.plus(Duration.ofDays(800)), zone).firstOrNull { it.key !in done }

/**
 * The notification's line under the date's name, as of [now].
 *
 * An all-day date: "Today", "Tomorrow", "In 5 days · Saturday, Oct 10", "In 1 week · Saturday, Oct 10"; a run of days
 * on its first day "Today – Wed, Oct 14", and once under way "Until Wed, Oct 14".
 *
 * A timed one: "In 15 minutes · 2:00 PM", "In 1 hour · 2:00 PM", "Today at 2:00 PM", "Tomorrow at 2:00 PM", "In 1
 * week · Saturday, Oct 10 · 2:00 PM"; "Starting now" from its time to 5 minutes after, and "Started at 2:00 PM" once
 * it's further past (a late alarm, or one the system held back).
 */
fun reminderText(
    occurrence: PersonalDate,
    now: ZonedDateTime,
    use24Hour: Boolean = ClockFormat.use24Hour,
    locale: Locale = Locale.US,
): String {
    val today = now.toLocalDate()
    val days = ChronoUnit.DAYS.between(today, occurrence.start)
    fun later() = "${inDays(days)} · ${occurrence.start.format(DateTimeFormatter.ofPattern("EEEE, MMM d", locale))}"
    fun short(d: java.time.LocalDate) = d.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))
    val time = occurrence.time ?: return when {
        days < 0 && occurrence.end != occurrence.start -> "Until ${short(occurrence.end)}"
        days <= 0 && occurrence.end != occurrence.start -> "Today – ${short(occurrence.end)}"
        days <= 0 -> "Today"
        days == 1L -> "Tomorrow"
        else -> later()
    }
    val clock = formatTimeOfDay(time, use24Hour, locale)
    val minutes = (Duration.between(now, ZonedDateTime.of(occurrence.start, time, now.zone)).seconds / 60.0).roundToLong()
    return when {
        minutes in -STARTING_NOW_MINUTES..0L -> "Starting now"
        minutes < 0 && days == 0L -> "Started at $clock"
        minutes < 0 && days == -1L -> "Started yesterday at $clock"
        minutes < 0 -> "Started ${occurrence.start.format(DateTimeFormatter.ofPattern("EEEE, MMM d", locale))} at $clock"
        minutes in 1L..59L -> "In ${plural(minutes.toInt(), "minute")} · $clock"
        minutes in 60L..180L && minutes % 60 == 0L -> "In ${plural((minutes / 60).toInt(), "hour")} · $clock"
        days <= 0 -> "Today at $clock"
        days == 1L -> "Tomorrow at $clock"
        else -> "${later()} · $clock"
    }
}

/** Up to this many minutes after a timed date's start, its reminder still says "Starting now". */
private const val STARTING_NOW_MINUTES = 5L

/**
 * The editor's line under the reminders: when the next of [date]'s reminders go off after [now] (up to two), in
 * [now]'s zone. "Next reminder: Mon, Sep 28 at 2:00 PM", "Next reminder: Mon, Sep 28 at 2:00 PM, then Tue, Sep 29 at
 * 1:00 PM"; with the year when it isn't this one. Null when none is still to come.
 */
fun nextReminderLine(
    date: PersonalDate,
    now: ZonedDateTime,
    use24Hour: Boolean = ClockFormat.use24Hour,
    locale: Locale = Locale.US,
): String? {
    val fires = reminderFires(listOf(date), now.toInstant(), now.toInstant().plus(Duration.ofDays(800)), now.zone).take(2)
    if (fires.isEmpty()) return null
    fun f(fire: ReminderFire): String {
        val at = fire.at
        val day = at.format(DateTimeFormatter.ofPattern(if (at.year == now.year) "EEE, MMM d" else "EEE, MMM d, yyyy", locale))
        return "$day at ${formatTimeOfDay(at.toLocalTime(), use24Hour, locale)}"
    }
    return "Next reminder: " + fires.joinToString(", then ") { f(it) }
}

/** "In 5 days", "In 1 week", "In 2 weeks". */
private fun inDays(days: Long): String = if (days % 7 == 0L) "In ${plural((days / 7).toInt(), "week")}" else "In ${plural(days.toInt(), "day")}"
