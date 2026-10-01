package app.daybreak.domain

import app.daybreak.domain.Reminder.DaysBefore
import app.daybreak.domain.Reminder.MinutesBefore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RemindersTest {
    private val ny = ZoneId.of("America/New_York")
    private val la = ZoneId.of("America/Los_Angeles")

    private fun at(text: String, zone: ZoneId = ny): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun next(dates: List<PersonalDate>, after: String, zone: ZoneId = ny) = nextReminder(dates, at(after, zone), zone)

    private fun nextLocal(date: PersonalDate, after: String, zone: ZoneId = ny) = next(listOf(date), after, zone)?.at?.toLocalDateTime()

    // --- When reminders go off ------------------------------------------------------------------------------------

    @Test fun `an all-day date's reminders go off at 9 AM on the day, the day before and a week before`() {
        val birthday = PersonalDate(LocalDate.of(2026, 10, 10), name = "Mom's birthday", reminders = reminderPresets(timed = false))
        val fires = reminderFires(listOf(birthday), at("2026-10-01T00:00"), at("2026-10-31T00:00"), ny)
        assertEquals(
            listOf("2026-10-03T09:00", "2026-10-09T09:00", "2026-10-10T09:00").map(LocalDateTime::parse),
            fires.map { it.at.toLocalDateTime() },
        )
    }

    @Test fun `a timed date's reminders count back from its time`() {
        val talk = PersonalDate(LocalDate.of(2026, 10, 8), name = "Presentation", time = LocalTime.of(14, 0), reminders = reminderPresets(timed = true))
        val fires = reminderFires(listOf(talk), at("2026-10-01T00:00"), at("2026-10-31T00:00"), ny)
        assertEquals(
            listOf("2026-10-07T14:00", "2026-10-08T13:00", "2026-10-08T13:45", "2026-10-08T14:00").map(LocalDateTime::parse),
            fires.map { it.at.toLocalDateTime() },
        )
    }

    @Test fun `a minutes-before reminder never goes off for a date without a time`() {
        val d = PersonalDate(LocalDate.of(2026, 10, 8), name = "x", reminders = listOf(MinutesBefore(15)))
        assertNull(next(listOf(d), "2026-10-01T00:00"))
    }

    @Test fun `a custom time of day is kept`() {
        val d = PersonalDate(LocalDate.of(2026, 10, 8), name = "x", reminders = listOf(DaysBefore(2, LocalTime.of(18, 30))))
        assertEquals(LocalDateTime.parse("2026-10-06T18:30"), nextLocal(d, "2026-10-01T00:00"))
    }

    @Test fun `a run of days is reminded of from its first day and time`() {
        val trip = PersonalDate(
            LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16), "Lisbon", time = LocalTime.of(7, 15),
            reminders = listOf(MinutesBefore(60)),
        )
        val fires = reminderFires(listOf(trip), at("2026-10-01T00:00"), at("2026-10-31T00:00"), ny)
        assertEquals(listOf(LocalDateTime.parse("2026-10-12T06:15")), fires.map { it.at.toLocalDateTime() })
    }

    @Test fun `past reminders are not next, and a one-off date's are over with it`() {
        val talk = PersonalDate(LocalDate.of(2026, 10, 8), name = "Talk", time = LocalTime.of(14, 0), reminders = listOf(MinutesBefore(60), MinutesBefore(0)))
        assertEquals(LocalDateTime.parse("2026-10-08T14:00"), nextLocal(talk, "2026-10-08T13:30"))
        assertNull(nextLocal(talk, "2026-10-08T14:00"))
        assertNull(nextLocal(talk, "2027-01-01T00:00"))
    }

    @Test fun `a yearly date repeats its reminders every year`() {
        val birthday = PersonalDate(LocalDate.of(2025, 3, 14), name = "Mum", yearly = true, reminders = listOf(DaysBefore(1)))
        assertEquals(LocalDateTime.parse("2026-03-13T09:00"), nextLocal(birthday, "2026-01-01T00:00"))
        // This year's has gone: next year's.
        assertEquals(LocalDateTime.parse("2027-03-13T09:00"), nextLocal(birthday, "2026-03-13T09:00"))
    }

    @Test fun `a week before 3 January goes off in the December before`() {
        val d = PersonalDate(LocalDate.of(2025, 1, 3), name = "x", yearly = true, reminders = listOf(DaysBefore(7)))
        assertEquals(LocalDateTime.parse("2026-12-27T09:00"), nextLocal(d, "2026-06-01T00:00"))
    }

    @Test fun `29 February comes round on the 28th in other years`() {
        val leap = PersonalDate(LocalDate.of(2024, 2, 29), name = "Leap birthday", yearly = true, reminders = listOf(DaysBefore(0), DaysBefore(1)))
        assertEquals(LocalDateTime.parse("2027-02-27T09:00"), nextLocal(leap, "2027-01-01T00:00"))
        assertEquals(LocalDateTime.parse("2027-02-28T09:00"), nextLocal(leap, "2027-02-27T09:00"))
        assertEquals(LocalDateTime.parse("2028-02-28T09:00"), nextLocal(leap, "2027-02-28T09:00"))
        assertEquals(LocalDateTime.parse("2028-02-29T09:00"), nextLocal(leap, "2028-02-28T09:00"))
    }

    @Test fun `the soonest reminder of all dates is next, in order`() {
        val dates = listOf(
            PersonalDate(LocalDate.of(2026, 10, 20), name = "Later", reminders = listOf(DaysBefore(0))),
            PersonalDate(LocalDate.of(2026, 10, 9), name = "Talk", time = LocalTime.of(10, 0), reminders = listOf(MinutesBefore(15))),
            PersonalDate(LocalDate.of(2026, 10, 9), name = "Birthday", reminders = listOf(DaysBefore(1), DaysBefore(0))),
        )
        assertEquals("Birthday", next(dates, "2026-10-01T00:00")!!.date.name)
        val fires = reminderFires(dates, at("2026-10-01T00:00"), at("2026-10-31T00:00"), ny)
        assertEquals(
            listOf("Birthday" to "2026-10-08T09:00", "Birthday" to "2026-10-09T09:00", "Talk" to "2026-10-09T09:45", "Later" to "2026-10-20T09:00"),
            fires.map { it.date.name to it.at.toLocalDateTime().toString() },
        )
        // Already dealt with: the next one along.
        val first = fires.first()
        assertEquals(fires[1], nextReminder(dates, at("2026-10-01T00:00"), ny, done = setOf(first.key)))
    }

    @Test fun `two reminders for the same moment are one`() {
        val d = PersonalDate(LocalDate.of(2026, 10, 9), name = "x", time = LocalTime.of(9, 0), reminders = listOf(MinutesBefore(0), DaysBefore(0)))
        assertEquals(1, reminderFires(listOf(d), at("2026-10-01T00:00"), at("2026-10-31T00:00"), ny).size)
    }

    // --- Daylight saving and travel -------------------------------------------------------------------------------

    @Test fun `a time the clocks skip moves on by the gap`() {
        // New York goes from 2:00 to 3:00 on 8 March 2026.
        val d = PersonalDate(LocalDate.of(2026, 3, 8), name = "x", time = LocalTime.of(2, 30), reminders = listOf(MinutesBefore(0)))
        val fire = next(listOf(d), "2026-03-01T00:00")!!.at
        assertEquals(ZonedDateTime.of(LocalDateTime.parse("2026-03-08T03:30"), ny), fire)
    }

    @Test fun `minutes and hours before are elapsed time across the change`() {
        // An hour before 3:30 AM EDT is 1:30 AM EST (2:30 doesn't exist that night).
        val d = PersonalDate(LocalDate.of(2026, 3, 8), name = "x", time = LocalTime.of(3, 30), reminders = listOf(MinutesBefore(60)))
        assertEquals(LocalDateTime.parse("2026-03-08T01:30"), nextLocal(d, "2026-03-01T00:00"))
    }

    @Test fun `a day before keeps the clock time across the change`() {
        // 2 PM the Monday after the change: 2 PM on Sunday, 23 hours earlier, not 1 PM.
        val d = PersonalDate(LocalDate.of(2026, 3, 9), name = "x", time = LocalTime.of(14, 0), reminders = listOf(MinutesBefore(24 * 60)))
        assertEquals(LocalDateTime.parse("2026-03-08T14:00"), nextLocal(d, "2026-03-01T00:00"))
        val allDay = PersonalDate(LocalDate.of(2026, 3, 9), name = "y", reminders = listOf(DaysBefore(1)))
        assertEquals(LocalDateTime.parse("2026-03-08T09:00"), nextLocal(allDay, "2026-03-01T00:00"))
    }

    @Test fun `a time that happens twice is the first`() {
        // New York goes from 2:00 back to 1:00 on 1 November 2026.
        val d = PersonalDate(LocalDate.of(2026, 11, 1), name = "x", time = LocalTime.of(1, 30), reminders = listOf(MinutesBefore(0)))
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), next(listOf(d), "2026-10-31T00:00")!!.instant)
    }

    @Test fun `9 AM is 9 AM wherever the phone is`() {
        val d = PersonalDate(LocalDate.of(2026, 10, 9), name = "x", reminders = listOf(DaysBefore(0)))
        val inNewYork = next(listOf(d), "2026-10-01T00:00", ny)!!
        val inLosAngeles = next(listOf(d), "2026-10-01T00:00", la)!!
        assertEquals(LocalDateTime.parse("2026-10-09T09:00"), inLosAngeles.at.toLocalDateTime())
        assertEquals(3, java.time.Duration.between(inNewYork.instant, inLosAngeles.instant).toHours())
        assertEquals(inNewYork.key, inLosAngeles.key) // the same reminder, so it never goes off twice
    }

    // --- Presets, labels and switching between all-day and timed ---------------------------------------------------

    @Test fun `labels`() {
        assertEquals(
            listOf("On the day, 9 AM", "1 day before", "1 week before"),
            reminderPresets(timed = false).map { it.label(use24Hour = false) },
        )
        assertEquals(
            listOf("When it starts", "15 minutes before", "1 hour before", "1 day before"),
            reminderPresets(timed = true).map { it.label(use24Hour = false) },
        )
        assertEquals("2 weeks before", DaysBefore(14).label(use24Hour = false))
        assertEquals("3 days before, 6:30 PM", DaysBefore(3, LocalTime.of(18, 30)).label(use24Hour = false))
        assertEquals("On the day, 6 PM", DaysBefore(0, LocalTime.of(18, 0)).label(use24Hour = false))
        assertEquals("On the day, 09:00", DaysBefore(0).label(use24Hour = true))
        assertEquals("36 hours before", MinutesBefore(36 * 60).label())
        assertEquals("90 minutes before", MinutesBefore(90).label())
        assertEquals("2 hours before", MinutesBefore(120).label())
    }

    @Test fun `the chips leave out 9 AM, which the next-reminder line says, but keep a time of its own`() {
        assertEquals(
            listOf("On the day", "1 day before", "1 week before"),
            reminderPresets(timed = false).map { it.label(use24Hour = false, withDefaultTime = false) },
        )
        assertEquals("3 days before, 6:30 PM", DaysBefore(3, LocalTime.of(18, 30)).label(use24Hour = false, withDefaultTime = false))
        assertEquals("On the day, 6 PM", DaysBefore(0, LocalTime.of(18, 0)).label(use24Hour = false, withDefaultTime = false))
    }

    @Test fun `a date's reminders in one line share their tail, nearest first`() {
        fun summary(vararg r: Reminder) = reminderSummary(r.toList(), use24Hour = false)
        assertEquals("1 hour and 1 day before", summary(MinutesBefore(24 * 60), MinutesBefore(60)))
        assertEquals("On the day, 3 days and 1 week before", summary(DaysBefore(7), DaysBefore(0), DaysBefore(3)))
        assertEquals("When it starts, 15 minutes and 1 hour before", summary(MinutesBefore(60), MinutesBefore(0), MinutesBefore(15)))
        assertEquals("1 day, 1 week and 2 weeks before", summary(DaysBefore(1), DaysBefore(7), DaysBefore(14)))
        assertEquals("1 day before", summary(DaysBefore(1)))
        assertEquals("On the day", summary(DaysBefore(0)))
        assertEquals("On the day, 1 week before, 3 days before at 6 PM", summary(DaysBefore(0), DaysBefore(7), DaysBefore(3, LocalTime.of(18, 0))))
        assertEquals("", summary())
    }

    @Test fun `a reminder at a time of its own keeps it when a time is added or taken off`() {
        val evening = DaysBefore(2, LocalTime.of(18, 30))
        assertEquals(listOf(MinutesBefore(0), evening), remindersFor(listOf(DaysBefore(0), evening), timed = true))
        assertEquals(listOf(DaysBefore(0), evening), remindersFor(listOf(MinutesBefore(0), evening), timed = false))
        // And it still goes off at its time on a timed date.
        val d = PersonalDate(LocalDate.of(2026, 10, 8), name = "x", time = LocalTime.of(14, 0), reminders = listOf(evening))
        assertEquals(LocalDateTime.parse("2026-10-06T18:30"), nextLocal(d, "2026-10-01T00:00"))
    }

    @Test fun `adding a time converts the picks, and taking it off converts them back`() {
        val allDay = listOf(DaysBefore(0), DaysBefore(1), DaysBefore(7))
        val timed = remindersFor(allDay, timed = true)
        assertEquals(listOf(MinutesBefore(0), MinutesBefore(24 * 60), MinutesBefore(7 * 24 * 60)), timed)
        assertEquals(allDay, remindersFor(timed, timed = false))
        // Shorter than a day becomes on the day, once.
        assertEquals(listOf(DaysBefore(0), DaysBefore(1)), remindersFor(listOf(MinutesBefore(15), MinutesBefore(60), MinutesBefore(24 * 60)), timed = false))
    }

    @Test fun `keys round trip`() {
        (reminderPresets(true) + reminderPresets(false) + DaysBefore(3, LocalTime.of(18, 30))).forEach {
            assertEquals(it, reminderOf(it.key))
        }
        assertNull(reminderOf("x9"))
        assertNull(reminderOf("d99@09:00")) // more than 8 weeks
    }

    // --- Notification text ----------------------------------------------------------------------------------------

    private fun text(date: PersonalDate, now: String) = reminderText(date, ZonedDateTime.of(LocalDateTime.parse(now), ny), use24Hour = false)

    @Test fun `an all-day date says how far off it is`() {
        val d = PersonalDate(LocalDate.of(2026, 10, 10), name = "Mom's birthday")
        assertEquals("Today", text(d, "2026-10-10T09:00"))
        assertEquals("Tomorrow", text(d, "2026-10-09T09:00"))
        assertEquals("In 1 week · Saturday, Oct 10", text(d, "2026-10-03T09:00"))
        assertEquals("In 3 days · Saturday, Oct 10", text(d, "2026-10-07T09:00"))
        assertEquals("In 2 weeks · Saturday, Oct 10", text(d, "2026-09-26T09:00"))
    }

    @Test fun `a run of days says when it ends once it's here`() {
        val trip = PersonalDate(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 14), "Lisbon")
        assertEquals("Today – Wed, Oct 14", text(trip, "2026-10-10T09:00"))
        assertEquals("Until Wed, Oct 14", text(trip, "2026-10-12T09:00"))
        assertEquals("Tomorrow", text(trip, "2026-10-09T09:00"))
    }

    @Test fun `the next-reminder line says when the next one or two go off`() {
        val now = ZonedDateTime.of(LocalDateTime.parse("2026-09-27T10:00"), ny)
        val talk = PersonalDate(LocalDate.of(2026, 9, 29), name = "Talk", time = LocalTime.of(14, 0), reminders = listOf(MinutesBefore(60), MinutesBefore(24 * 60)))
        assertEquals("Next reminder: Mon, Sep 28 at 2:00 PM, then Tue, Sep 29 at 1:00 PM", nextReminderLine(talk, now, use24Hour = false))
        val three = talk.copy(reminders = listOf(MinutesBefore(0), MinutesBefore(60), MinutesBefore(24 * 60)))
        assertEquals("Next reminder: Mon, Sep 28 at 2:00 PM, then Tue, Sep 29 at 1:00 PM", nextReminderLine(three, now, use24Hour = false))
        val one = talk.copy(reminders = listOf(MinutesBefore(15)))
        assertEquals("Next reminder: Tue, Sep 29 at 1:45 PM", nextReminderLine(one, now, use24Hour = false))
        // A yearly date whose day has gone this year: next year's, with the year, then the year after's.
        val birthday = PersonalDate(LocalDate.of(2025, 3, 14), name = "Mum", yearly = true, reminders = listOf(DaysBefore(1)))
        assertEquals(
            "Next reminder: Sat, Mar 13, 2027 at 9:00 AM, then Mon, Mar 13, 2028 at 9:00 AM",
            nextReminderLine(birthday, now, use24Hour = false),
        )
        assertNull(nextReminderLine(talk.copy(reminders = emptyList()), now))
        assertNull(nextReminderLine(talk, now.plusDays(5)))
    }

    @Test fun `a timed date says how soon and when`() {
        val d = PersonalDate(LocalDate.of(2026, 10, 10), name = "Talk", time = LocalTime.of(14, 0))
        assertEquals("Starting now", text(d, "2026-10-10T14:00"))
        assertEquals("Starting now", text(d, "2026-10-10T14:05"))
        assertEquals("Started at 2:00 PM", text(d, "2026-10-10T14:06"))
        assertEquals("Started at 2:00 PM", text(d, "2026-10-10T14:59"))
        assertEquals("Started at 2:00 PM", text(d, "2026-10-10T18:00")) // an alarm the system held back
        assertEquals("Started yesterday at 2:00 PM", text(d, "2026-10-11T01:00"))
        assertEquals("In 15 minutes · 2:00 PM", text(d, "2026-10-10T13:45"))
        assertEquals("In 15 minutes · 2:00 PM", text(d, "2026-10-10T13:45:00.4")) // an alarm a moment late
        assertEquals("In 1 minute · 2:00 PM", text(d, "2026-10-10T13:59"))
        assertEquals("In 1 hour · 2:00 PM", text(d, "2026-10-10T13:00"))
        assertEquals("In 3 hours · 2:00 PM", text(d, "2026-10-10T11:00"))
        assertEquals("Today at 2:00 PM", text(d, "2026-10-10T09:00"))
        assertEquals("Today at 2:00 PM", text(d, "2026-10-10T12:40"))
        assertEquals("Tomorrow at 2:00 PM", text(d, "2026-10-09T14:00"))
        assertEquals("In 1 week · Saturday, Oct 10 · 2:00 PM", text(d, "2026-10-03T14:00"))
        assertEquals(
            "In 15 minutes · 14:00",
            reminderText(d, ZonedDateTime.of(LocalDateTime.parse("2026-10-10T13:45"), ny), use24Hour = true),
        )
    }

    @Test fun `short reminders are the ones that need exact alarms`() {
        val t = LocalTime.of(14, 0)
        val d = LocalDate.of(2026, 10, 10)
        assertEquals(true, PersonalDate(d, time = t, reminders = listOf(MinutesBefore(0))).hasShortReminder())
        assertEquals(true, PersonalDate(d, time = t, reminders = listOf(MinutesBefore(60))).hasShortReminder())
        assertEquals(false, PersonalDate(d, time = t, reminders = listOf(MinutesBefore(24 * 60))).hasShortReminder())
        assertEquals(false, PersonalDate(d, time = t, reminders = listOf(MinutesBefore(2 * 24 * 60))).hasShortReminder())
        // Elapsed time past a day (36 hours before) needs the minute just as an hour before does.
        assertEquals(true, PersonalDate(d, time = t, reminders = listOf(MinutesBefore(36 * 60))).hasShortReminder())
        assertEquals(false, PersonalDate(d, time = t, reminders = listOf(DaysBefore(1, LocalTime.of(18, 0)))).hasShortReminder())
        assertEquals(false, PersonalDate(d, reminders = listOf(DaysBefore(0))).hasShortReminder())
    }

    @Test fun `coming up shows a single date's time`() {
        val today = LocalDate.of(2026, 10, 1)
        val talk = PersonalDate(today.plusDays(1), name = "Presentation", time = LocalTime.of(14, 0))
        assertEquals(LocalTime.of(14, 0), upcomingPersonalDates(today, listOf(talk)).single().time)
    }
}
