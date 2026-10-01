package app.daybreak.reminders

import app.daybreak.data.InMemoryStore
import app.daybreak.domain.ClockFormat
import app.daybreak.domain.PersonalDate
import app.daybreak.domain.Reminder.DaysBefore
import app.daybreak.domain.Reminder.MinutesBefore
import app.daybreak.domain.ReminderFire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderSchedulerTest {
    private val ny = ZoneId.of("America/New_York")
    private val la = ZoneId.of("America/Los_Angeles")

    private var zone: ZoneId = ny
    private var now: Instant = at("2026-10-01T08:00")
    private var dates: List<PersonalDate> = emptyList()
    private val store = InMemoryStore()

    /** The alarm as the fake AlarmManager has it. */
    private var alarm: Instant? = null
    private val shown = mutableListOf<Pair<ReminderFire, String>>()

    /** Notification ids as posted, by date name. */
    private val ids = mutableListOf<Pair<String, Int>>()

    /** What the fake notifier does with a reminder: post it (true), find notifications off (false), or throw. */
    private var post: (ReminderFire) -> Boolean = { true }

    private val scheduler = ReminderScheduler(
        store,
        dates = { dates },
        alarms = object : AlarmScheduler {
            override fun set(at: Instant) { alarm = at }
            override fun cancel() { alarm = null }
        },
        notifier = object : ReminderNotifier {
            override fun show(fire: ReminderFire, text: String, id: Int): Boolean {
                if (!post(fire)) return false
                shown += fire to text
                ids += fire.date.name to id
                return true
            }
        },
        now = { now },
        zone = { zone },
    )

    /** A scheduler as the receiver makes one: fresh, over the same store. */
    private fun another() = ReminderScheduler(
        store, { dates },
        object : AlarmScheduler {
            override fun set(at: Instant) { alarm = at }
            override fun cancel() { alarm = null }
        },
        object : ReminderNotifier {
            override fun show(fire: ReminderFire, text: String, id: Int): Boolean { shown += fire to text; ids += fire.date.name to id; return true }
        },
        { now }, { zone },
    )

    private fun texts() = shown.map { "${it.first.date.name}: ${it.second}" }

    private fun at(text: String, z: ZoneId = ny): Instant = LocalDateTime.parse(text).atZone(z).toInstant()

    private val talk = PersonalDate(
        LocalDate.of(2026, 10, 1), name = "Presentation", time = LocalTime.of(14, 0),
        reminders = listOf(MinutesBefore(60), MinutesBefore(15)), id = "talk",
    )
    private val birthday = PersonalDate(
        LocalDate.of(2025, 10, 3), name = "Mom's birthday", yearly = true,
        reminders = listOf(DaysBefore(1), DaysBefore(0)), id = "mom",
    )

    @Before fun setUp() {
        ClockFormat.use24Hour = false
    }

    /** Moves the clock to the alarm and lets it go off. */
    private fun fireAlarm(lateBy: java.time.Duration = java.time.Duration.ZERO): List<ReminderFire> {
        now = alarm!!.plus(lateBy)
        return scheduler.run(ReminderTrigger.ALARM)
    }

    @Test fun `a change sets one alarm for the soonest reminder and shows nothing`() {
        dates = listOf(birthday, talk)
        assertEquals(emptyList<ReminderFire>(), scheduler.run(ReminderTrigger.CHANGED))
        assertEquals(at("2026-10-01T13:00"), alarm)
        assertEquals(alarm, scheduler.alarmAt())
    }

    @Test fun `each alarm shows its reminder and sets the next, through to next year`() {
        dates = listOf(birthday, talk)
        scheduler.run(ReminderTrigger.CHANGED)
        fireAlarm()
        assertEquals(listOf("Presentation" to "In 1 hour · 2:00 PM"), shown.map { it.first.date.title to it.second })
        assertEquals(at("2026-10-01T13:45"), alarm)
        fireAlarm()
        assertEquals("In 15 minutes · 2:00 PM", shown.last().second)
        assertEquals(at("2026-10-02T09:00"), alarm)
        fireAlarm()
        assertEquals("Mom's birthday" to "Tomorrow", shown.last().let { it.first.date.title to it.second })
        fireAlarm()
        assertEquals("Today", shown.last().second)
        // The birthday comes round again: its reminders with it.
        assertEquals(at("2027-10-02T09:00"), alarm)
        assertEquals(4, shown.size)
    }

    @Test fun `no reminders left, no alarm`() {
        dates = listOf(talk)
        scheduler.run(ReminderTrigger.CHANGED)
        fireAlarm()
        fireAlarm()
        assertNull(alarm)
        dates = listOf(talk.copy(reminders = emptyList()))
        now = at("2026-10-01T09:00")
        scheduler.run(ReminderTrigger.CHANGED)
        assertNull(alarm)
    }

    @Test fun `editing dates moves the alarm, and removing one takes its reminders away`() {
        dates = listOf(talk)
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(at("2026-10-01T13:00"), alarm)
        dates = listOf(talk.copy(time = LocalTime.of(16, 0)))
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(at("2026-10-01T15:00"), alarm)
        dates = emptyList()
        scheduler.run(ReminderTrigger.CHANGED)
        assertNull(alarm)
    }

    @Test fun `a reminder added after its time is skipped, not shown`() {
        scheduler.run(ReminderTrigger.CHANGED)
        now = at("2026-10-01T13:05")
        dates = listOf(talk) // its hour-before was at 1:00
        assertEquals(emptyList<ReminderFire>(), scheduler.run(ReminderTrigger.CHANGED))
        assertEquals(at("2026-10-01T13:45"), alarm)
    }

    @Test fun `an inexact alarm that's late still shows, saying how soon it really is`() {
        dates = listOf(talk.copy(reminders = listOf(MinutesBefore(60))))
        scheduler.run(ReminderTrigger.CHANGED)
        fireAlarm(lateBy = java.time.Duration.ofMinutes(7))
        assertEquals("In 53 minutes · 2:00 PM", shown.single().second)
    }

    @Test fun `when one alarm covers two reminders of a date, only the latest shows`() {
        dates = listOf(talk)
        scheduler.run(ReminderTrigger.CHANGED)
        fireAlarm(lateBy = java.time.Duration.ofMinutes(50)) // 1:50, past both
        assertEquals(listOf("In 10 minutes · 2:00 PM"), shown.map { it.second })
        assertEquals(MinutesBefore(15), shown.single().first.reminder)
    }

    @Test fun `after a restart, the reminders of the last two hours show, one per date`() {
        dates = listOf(
            talk,
            PersonalDate(LocalDate.of(2026, 10, 1), name = "Call the bank", reminders = listOf(DaysBefore(0, LocalTime.of(12, 0))), id = "bank"),
        )
        now = at("2026-10-01T11:00")
        scheduler.run(ReminderTrigger.CHANGED)
        // Off from 11:30 until 1:55: the bank at 12:00, the talk's hour-before at 1:00 and its 1:45 all went by.
        now = at("2026-10-01T13:55")
        val after = scheduler.run(ReminderTrigger.BOOT)
        assertEquals(listOf("Call the bank: Today", "Presentation: In 5 minutes · 2:00 PM"), texts())
        assertEquals(listOf("Call the bank", "Presentation"), after.map { it.date.title })
        assertNull(alarm) // nothing left today
    }

    @Test fun `after days off, a restart shows nothing old`() {
        dates = listOf(birthday, talk)
        scheduler.run(ReminderTrigger.CHANGED)
        now = at("2026-10-05T10:00")
        scheduler.run(ReminderTrigger.BOOT)
        assertTrue(shown.isEmpty())
        assertEquals(at("2027-10-02T09:00"), alarm)
    }

    @Test fun `a restart overnight with a late unlock still shows the morning's reminder`() {
        // The phone updates and restarts at 2 AM; BOOT_COMPLETED only comes once it's unlocked, at 9:30.
        dates = listOf(PersonalDate(LocalDate.of(2026, 10, 1), name = "Dentist", reminders = listOf(DaysBefore(0)), id = "d"))
        now = at("2026-10-01T02:00")
        scheduler.run(ReminderTrigger.CHANGED)
        now = at("2026-10-01T09:30")
        scheduler.run(ReminderTrigger.BOOT)
        assertEquals(listOf("Dentist: Today"), texts())
    }

    @Test fun `after a restart, the promised reminder shows however late while its date is on, and older ones don't`() {
        dates = listOf(
            talk.copy(reminders = listOf(MinutesBefore(60))),
            PersonalDate(LocalDate.of(2026, 10, 1), name = "Call the bank", reminders = listOf(DaysBefore(0, LocalTime.of(12, 0))), id = "bank"),
        )
        now = at("2026-10-01T11:00")
        scheduler.run(ReminderTrigger.CHANGED) // promises the bank at 12:00
        now = at("2026-10-01T15:30") // unlocked three and a half hours later
        scheduler.run(ReminderTrigger.BOOT)
        // The bank was promised and it's still the day; the talk's 1:00 wasn't promised and is over two hours old.
        assertEquals(listOf("Call the bank: Today"), texts())
    }

    @Test fun `opening the app while a late alarm is still on its way delivers it once`() {
        dates = listOf(talk.copy(reminders = listOf(MinutesBefore(60))))
        scheduler.run(ReminderTrigger.CHANGED)
        now = at("2026-10-01T13:04") // the 1:00 alarm hasn't come yet
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(listOf("In 56 minutes · 2:00 PM"), shown.map { it.second })
        // The old alarm, had it come after all, finds nothing new.
        now = at("2026-10-01T13:10")
        scheduler.run(ReminderTrigger.ALARM)
        assertEquals(1, shown.size)
    }

    @Test fun `the clock jumping ahead skips what it jumped over but the promised reminder of a date still on`() {
        dates = listOf(birthday, talk)
        scheduler.run(ReminderTrigger.CHANGED) // promises the talk's 1:00
        now = at("2026-10-03T08:00")
        scheduler.run(ReminderTrigger.CHANGED) // TIME_SET
        // The talk was on the 1st: over. The birthday's day before wasn't promised.
        assertTrue(shown.isEmpty())
        assertEquals(at("2026-10-03T09:00"), alarm)
    }

    @Test fun `the clock jumping ahead past a promised reminder of a date still on shows it, as of now`() {
        dates = listOf(birthday)
        scheduler.run(ReminderTrigger.CHANGED) // promises the day before, the 2nd at 9 AM
        now = at("2026-10-03T08:00")
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(listOf("Mom's birthday: Today"), texts())
        assertEquals(at("2026-10-03T09:00"), alarm)
    }

    @Test fun `the clock going back doesn't show anything twice`() {
        dates = listOf(birthday)
        scheduler.run(ReminderTrigger.CHANGED)
        fireAlarm() // 2 Oct, 9 AM
        now = at("2026-10-02T08:00")
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(at("2026-10-03T09:00"), alarm)
        assertEquals(1, shown.size)
    }

    @Test fun `flying west after a reminder doesn't bring it back at 9 AM there`() {
        dates = listOf(birthday)
        scheduler.run(ReminderTrigger.CHANGED)
        fireAlarm() // 9 AM on the 2nd in New York
        // Land in Los Angeles at 7 AM local (10 AM in New York): 9 AM there is still to come.
        now = at("2026-10-02T07:00", la)
        zone = la
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(at("2026-10-03T09:00", la), alarm)
        assertEquals(1, shown.size)
    }

    @Test fun `flying east keeps 9 AM local`() {
        dates = listOf(birthday)
        now = at("2026-10-01T20:00")
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(at("2026-10-02T09:00"), alarm)
        zone = ZoneId.of("Europe/Lisbon")
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(at("2026-10-02T09:00", zone), alarm)
    }

    @Test fun `a broken state starts again from now`() {
        dates = listOf(talk)
        store.putString(ReminderScheduler.KEY, "not json")
        now = at("2026-10-01T13:50")
        scheduler.run(ReminderTrigger.ALARM)
        assertTrue(shown.isEmpty())
        assertNull(alarm)
    }

    @Test fun `broadcasts map to triggers`() {
        assertEquals(ReminderTrigger.ALARM, reminderTriggerFor(ReminderReceiver.ACTION_ALARM))
        assertEquals(ReminderTrigger.BOOT, reminderTriggerFor("android.intent.action.BOOT_COMPLETED"))
        listOf(
            "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        ).forEach { assertEquals(it, ReminderTrigger.CHANGED, reminderTriggerFor(it)) }
        assertEquals(ReminderTrigger.BOOT, reminderTriggerFor("android.intent.action.QUICKBOOT_POWERON"))
        assertNull(reminderTriggerFor("android.intent.action.SCREEN_ON"))
        assertNull(reminderTriggerFor(null))
    }

    // --- The alarm promised a reminder: CHANGED delivers it and what came after, never what came before -----------

    @Test fun `opening the app after an overdue inexact alarm shows its reminder and the ones since`() {
        dates = listOf(
            PersonalDate(LocalDate.of(2026, 10, 1), name = "One", reminders = listOf(DaysBefore(0, LocalTime.of(9, 0))), id = "1"),
            PersonalDate(LocalDate.of(2026, 10, 1), name = "Two", reminders = listOf(DaysBefore(0, LocalTime.of(9, 20))), id = "2"),
        )
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(at("2026-10-01T09:00"), alarm)
        now = at("2026-10-01T09:40") // the 9:00 alarm is held back; the app is opened
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(listOf("One: Today", "Two: Today"), texts())
        // The held-back alarm turns up after all: nothing twice.
        now = at("2026-10-01T09:41")
        scheduler.run(ReminderTrigger.ALARM)
        assertEquals(2, shown.size)
        assertNull(alarm)
    }

    @Test fun `a reminder added after its time isn't shown while the alarm is set for a later one`() {
        dates = listOf(PersonalDate(LocalDate.of(2026, 10, 1), name = "Later", time = LocalTime.of(11, 0), reminders = listOf(MinutesBefore(60)), id = "l"))
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(at("2026-10-01T10:00"), alarm)
        now = at("2026-10-01T09:30")
        dates = dates + PersonalDate(LocalDate.of(2026, 10, 1), name = "New", reminders = listOf(DaysBefore(0)), id = "n") // 9:00, gone
        assertEquals(emptyList<ReminderFire>(), scheduler.run(ReminderTrigger.CHANGED))
        assertEquals(at("2026-10-01T10:00"), alarm)
        fireAlarm()
        assertEquals(listOf("Later: In 1 hour · 11:00 AM"), texts())
    }

    @Test fun `a reminder added after its time, once the alarm is overdue, still isn't shown`() {
        dates = listOf(PersonalDate(LocalDate.of(2026, 10, 1), name = "Later", time = LocalTime.of(11, 0), reminders = listOf(MinutesBefore(60)), id = "l"))
        scheduler.run(ReminderTrigger.CHANGED) // 10:00
        now = at("2026-10-01T10:30")
        dates = dates + PersonalDate(LocalDate.of(2026, 10, 1), name = "New", reminders = listOf(DaysBefore(0)), id = "n") // 9:00
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(listOf("Later: In 30 minutes · 11:00 AM"), texts())
    }

    @Test fun `at the very moment of a reminder, a change shows it if it was promised and the alarm then finds nothing`() {
        dates = listOf(talk.copy(reminders = listOf(MinutesBefore(60))))
        scheduler.run(ReminderTrigger.CHANGED)
        now = at("2026-10-01T13:00") // == the promised reminder
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(listOf("Presentation: In 1 hour · 2:00 PM"), texts())
        scheduler.run(ReminderTrigger.ALARM)
        assertEquals(1, shown.size)
    }

    @Test fun `at the very moment of a reminder no alarm promised, a change skips it and the alarm shows it`() {
        scheduler.run(ReminderTrigger.CHANGED)
        now = at("2026-10-01T13:00")
        dates = listOf(talk.copy(reminders = listOf(MinutesBefore(60), MinutesBefore(0))))
        scheduler.run(ReminderTrigger.CHANGED) // added right at 1:00: not promised
        assertTrue(shown.isEmpty())
        assertEquals(at("2026-10-01T14:00"), alarm)
        // An alarm right on its reminder shows it.
        fireAlarm()
        assertEquals(listOf("Presentation: Starting now"), texts())
    }

    // --- Flying ----------------------------------------------------------------------------------------------------

    @Test fun `flying east past 9 AM delivers the promised reminder once`() {
        dates = listOf(PersonalDate(LocalDate.of(2025, 10, 2), name = "Birthday", yearly = true, reminders = listOf(DaysBefore(0)), id = "b"))
        now = at("2026-10-01T18:00")
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(at("2026-10-02T09:00"), alarm)
        // Land in Tokyo at 9 PM on the 2nd: 9 AM there went by while the phone was still on New York time.
        val tokyo = ZoneId.of("Asia/Tokyo")
        now = Instant.parse("2026-10-02T12:00:00Z")
        zone = tokyo
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(listOf("Birthday: Today"), texts())
        assertEquals(at("2027-10-02T09:00", tokyo), alarm)
        // The New York alarm, had it come, and any later change find nothing more.
        now = Instant.parse("2026-10-02T13:00:00Z")
        scheduler.run(ReminderTrigger.ALARM)
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(1, shown.size)
    }

    @Test fun `flying east the day after doesn't bring back a date that's over`() {
        dates = listOf(PersonalDate(LocalDate.of(2026, 10, 2), name = "Lunch", reminders = listOf(DaysBefore(0, LocalTime.of(23, 0))), id = "l"))
        now = at("2026-10-02T20:00")
        scheduler.run(ReminderTrigger.CHANGED)
        // In Tokyo it's already the 3rd: Lunch is over, so its 11 PM isn't caught up.
        now = at("2026-10-02T21:00")
        zone = ZoneId.of("Asia/Tokyo")
        scheduler.run(ReminderTrigger.CHANGED)
        assertTrue(shown.isEmpty())
    }

    @Test fun `flying London to New York, a reminder shown in London isn't shown again and one still to come waits for 9 AM`() {
        val london = ZoneId.of("Europe/London")
        dates = listOf(
            PersonalDate(LocalDate.of(2026, 10, 2), name = "Shown", reminders = listOf(DaysBefore(0, LocalTime.of(8, 0))), id = "s"),
            PersonalDate(LocalDate.of(2026, 10, 2), name = "Waits", reminders = listOf(DaysBefore(0, LocalTime.of(9, 0))), id = "w"),
        )
        zone = london
        now = at("2026-10-02T07:00", london)
        scheduler.run(ReminderTrigger.CHANGED)
        fireAlarm() // 8 AM in London
        assertEquals(listOf("Shown: Today"), texts())
        // The phone is off from 8:30 London time until landing in New York at 7 AM there.
        now = at("2026-10-02T07:00", ny)
        zone = ny
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(1, shown.size)
        // 8 AM is done (it's known by its local time), so the alarm is for "Waits" at 9 AM New York time.
        assertEquals(at("2026-10-02T09:00", ny), alarm)
        fireAlarm()
        assertEquals(listOf("Shown: Today", "Waits: Today"), texts())
    }

    // --- Delivery ----------------------------------------------------------------------------------------------------

    @Test fun `the exact alarm and its inexact backup both arriving show the reminder once`() {
        dates = listOf(talk.copy(reminders = listOf(MinutesBefore(60))))
        scheduler.run(ReminderTrigger.CHANGED)
        val set = alarm
        fireAlarm()
        now = now.plusSeconds(90) // the backup, a little later
        assertEquals(emptyList<ReminderFire>(), scheduler.run(ReminderTrigger.ALARM))
        assertEquals(1, shown.size)
        assertEquals(set, at("2026-10-01T13:00"))
    }

    @Test fun `a notification that throws doesn't stop the alarm, the state or the others`() {
        dates = listOf(
            PersonalDate(LocalDate.of(2026, 10, 1), name = "Broken", reminders = listOf(DaysBefore(0)), id = "x"),
            PersonalDate(LocalDate.of(2026, 10, 1), name = "Fine", reminders = listOf(DaysBefore(0)), id = "y"),
            PersonalDate(LocalDate.of(2026, 10, 2), name = "Tomorrow's", reminders = listOf(DaysBefore(0)), id = "z"),
        )
        post = { if (it.date.name == "Broken") throw IllegalStateException("bad notification") else true }
        scheduler.run(ReminderTrigger.CHANGED)
        val result = fireAlarm()
        assertEquals(listOf("Fine"), result.map { it.date.name })
        assertEquals(listOf("Fine: Today"), texts())
        assertEquals(at("2026-10-02T09:00"), alarm)
        // Dealt with all the same: a second alarm doesn't try again.
        post = { true }
        scheduler.run(ReminderTrigger.ALARM)
        assertEquals(1, shown.size)
    }

    @Test fun `what couldn't be shown isn't reported as shown`() {
        dates = listOf(talk.copy(reminders = listOf(MinutesBefore(60))))
        post = { false } // notifications off
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(emptyList<ReminderFire>(), fireAlarm())
    }

    @Test fun `an alarm the system held back for hours still shows its reminder while the date is on`() {
        dates = listOf(talk.copy(reminders = listOf(MinutesBefore(60))), birthday)
        scheduler.run(ReminderTrigger.CHANGED)
        fireAlarm(lateBy = java.time.Duration.ofHours(4)) // 5 PM, for 1 PM
        assertEquals(listOf("Presentation: Started at 2:00 PM"), texts())
        assertEquals(at("2026-10-01T13:00"), shown.single().first.instant)
        fireAlarm(lateBy = java.time.Duration.ofHours(10)) // 7 PM on the 2nd, for its 9 AM: the birthday is still tomorrow
        assertEquals("Mom's birthday: Tomorrow", texts().last())
    }

    @Test fun `a held-back alarm doesn't bring back a date that's over`() {
        dates = listOf(PersonalDate(LocalDate.of(2026, 10, 1), name = "Lunch", reminders = listOf(DaysBefore(0, LocalTime.of(12, 0))), id = "l"))
        scheduler.run(ReminderTrigger.CHANGED)
        now = at("2026-10-02T08:00")
        scheduler.run(ReminderTrigger.ALARM)
        assertTrue(shown.isEmpty())
    }

    @Test fun `a date's reminders share one notification id, other dates get their own, and they last across runs`() {
        dates = listOf(birthday, talk)
        scheduler.run(ReminderTrigger.CHANGED)
        repeat(4) { fireAlarm() }
        assertEquals(listOf("Presentation", "Presentation", "Mom's birthday", "Mom's birthday"), ids.map { it.first })
        val (talkId, birthdayId) = ids[0].second to ids[2].second
        assertEquals(talkId, ids[1].second)
        assertEquals(birthdayId, ids[3].second)
        assertTrue(talkId != birthdayId)
        // Next year's birthday is a new time round: a new id, from a scheduler made afresh.
        now = alarm!!
        another().run(ReminderTrigger.ALARM)
        assertTrue(ids.last().second !in setOf(talkId, birthdayId))
    }

    // --- Full cycles ---------------------------------------------------------------------------------------------

    @Test fun `a day before and an hour before across the night the clocks go back`() {
        // New York goes from 2:00 back to 1:00 on 1 November 2026.
        dates = listOf(PersonalDate(LocalDate.of(2026, 11, 2), name = "Early", time = LocalTime.of(1, 30), reminders = listOf(MinutesBefore(24 * 60), MinutesBefore(60)), id = "e"))
        now = at("2026-10-30T12:00")
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), alarm) // 1:30 AM EDT, the first of the two
        fireAlarm()
        assertEquals(Instant.parse("2026-11-02T05:30:00Z"), alarm) // 12:30 AM EST
        fireAlarm()
        assertNull(alarm)
        assertEquals(listOf("Early: Tomorrow at 1:30 AM", "Early: In 1 hour · 1:30 AM"), texts())
    }

    @Test fun `a day before and at the time across the night the clocks go forward`() {
        // New York goes from 2:00 to 3:00 on 14 March 2027: 2:30 that night is 3:30.
        dates = listOf(PersonalDate(LocalDate.of(2027, 3, 14), name = "Gap", time = LocalTime.of(2, 30), reminders = listOf(MinutesBefore(0), MinutesBefore(24 * 60)), id = "g"))
        now = at("2027-03-10T12:00")
        scheduler.run(ReminderTrigger.CHANGED)
        assertEquals(at("2027-03-13T02:30"), alarm)
        fireAlarm()
        assertEquals(at("2027-03-14T03:30"), alarm)
        fireAlarm()
        assertNull(alarm)
        assertEquals(listOf("Gap: Tomorrow at 2:30 AM", "Gap: Starting now"), texts())
    }

    @Test fun `29 February through two years of alarms`() {
        dates = listOf(PersonalDate(LocalDate.of(2024, 2, 29), name = "Leap", yearly = true, reminders = listOf(DaysBefore(0), DaysBefore(1)), id = "leap"))
        now = at("2027-01-01T12:00")
        scheduler.run(ReminderTrigger.CHANGED)
        val went = mutableListOf<String>()
        repeat(4) {
            went += alarm!!.atZone(ny).toLocalDateTime().toString()
            fireAlarm()
        }
        assertEquals(listOf("2027-02-27T09:00", "2027-02-28T09:00", "2028-02-28T09:00", "2028-02-29T09:00"), went)
        assertEquals(listOf("Leap: Tomorrow", "Leap: Today", "Leap: Tomorrow", "Leap: Today"), texts())
        assertEquals(at("2029-02-27T09:00"), alarm)
    }
}
