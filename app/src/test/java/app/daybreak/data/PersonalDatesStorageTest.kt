package app.daybreak.data

import app.daybreak.domain.PersonalDate
import app.daybreak.domain.Reminder.DaysBefore
import app.daybreak.domain.Reminder.MinutesBefore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class PersonalDatesStorageTest {
    private val talk = PersonalDate(
        LocalDate.of(2026, 10, 8), name = "Presentation", time = LocalTime.of(14, 0),
        reminders = listOf(MinutesBefore(15), MinutesBefore(24 * 60)), id = "1b2c",
    )
    private val birthday = PersonalDate(
        LocalDate.of(2025, 3, 14), name = "Mum's birthday", yearly = true,
        reminders = listOf(DaysBefore(0), DaysBefore(7, LocalTime.of(18, 30))), id = "9f0e",
    )
    private val trip = PersonalDate(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16), "Lisbon", dayOff = true)

    @Test fun `times, reminders and ids round trip through the private store`() {
        val store = InMemoryStore()
        val private = InMemoryStore()
        val trip = trip.copy(id = "77aa")
        SettingsRepository(store, privateStore = private).update { it.copy(personalDates = listOf(birthday, talk, trip)) }
        assertEquals(listOf(birthday, talk, trip), SettingsRepository(store, privateStore = private).settings.value.personalDates)
        assertEquals(listOf(birthday, talk, trip), decodePersonalDates(encodePersonalDates(listOf(birthday, talk, trip))))
    }

    @Test fun `dates saved before reminders load with no time or reminders, and get ids that are written down once`() {
        val old = """[{"start":"2026-10-12","end":"2026-10-16","name":"Lisbon","dayOff":true,"yearly":false},""" +
            """{"start":"2025-03-14","end":"2025-03-14","name":"Mum's birthday","dayOff":false,"yearly":true}]"""
        val private = InMemoryStore(mapOf(PERSONAL_DATES_KEY to old))
        val loaded = SettingsRepository(InMemoryStore(), privateStore = private).settings.value.personalDates
        assertEquals(
            listOf(PersonalDate(LocalDate.of(2025, 3, 14), name = "Mum's birthday", yearly = true), trip),
            loaded.map { it.copy(id = "") },
        )
        assertTrue(loaded.all { it.id.isNotBlank() })
        // The same ids however often they're read (the receiver may read before the app writes them down)…
        assertEquals(loaded, decodePersonalDates(old))
        // …and written down when the app first loads them.
        assertEquals(loaded, decodePersonalDates(private.getString(PERSONAL_DATES_KEY)!!))
        assertTrue(private.getString(PERSONAL_DATES_KEY)!!.contains(loaded[0].id))
        // A date without a time or reminders is written without them.
        assertEquals(
            setOf("start", "end", "name", "dayOff", "yearly", "id"),
            org.json.JSONArray(encodePersonalDates(listOf(trip.copy(id = "t")))).getJSONObject(0).keySet(),
        )
    }

    @Test fun `two old dates stored the same get different ids`() {
        val same = """{"start":"2026-10-12","end":"2026-10-12","name":"Day off","dayOff":true,"yearly":false}"""
        val dates = decodePersonalDates("[$same,$same]")
        assertEquals(2, dates.size)
        assertTrue(dates[0].id != dates[1].id)
    }

    @Test fun `dates with ids aren't written again on load`() {
        val raw = encodePersonalDates(listOf(talk))
        val private = object : KeyValueStore by InMemoryStore(mapOf(PERSONAL_DATES_KEY to raw)) {
            var writes = 0
            override fun putString(key: String, value: String) { writes++ }
        }
        SettingsRepository(InMemoryStore(), privateStore = private)
        assertEquals(0, private.writes)
    }

    @Test fun `the last reminders picked for each kind of date are kept`() {
        val store = InMemoryStore()
        SettingsRepository(store).update {
            it.copy(lastAllDayReminders = listOf(DaysBefore(0), DaysBefore(1, LocalTime.of(18, 0))), lastTimedReminders = listOf(MinutesBefore(15)))
        }
        val s = SettingsRepository(store).settings.value
        assertEquals(listOf(DaysBefore(0), DaysBefore(1, LocalTime.of(18, 0))), s.lastAllDayReminders)
        assertEquals(listOf(MinutesBefore(15)), s.lastTimedReminders)
        assertEquals(emptyList<Any>(), SettingsRepository(InMemoryStore()).settings.value.lastTimedReminders)
    }

    @Test fun `a reminder or time that doesn't parse is dropped, keeping the date and its other reminders`() {
        val raw = """[{"start":"2026-10-08","end":"2026-10-08","name":"Talk","time":"two pm","id":"a",""" +
            """"reminders":[{"minutes":15},{"weeks":2},{"days":400},"nonsense",{"days":1,"at":"25:00"},{"days":1}]}]"""
        assertEquals(
            listOf(PersonalDate(LocalDate.of(2026, 10, 8), name = "Talk", id = "a", reminders = listOf(MinutesBefore(15), DaysBefore(1)))),
            decodePersonalDates(raw),
        )
    }

    @Test fun `no more than three reminders are kept`() {
        val raw = """[{"start":"2026-10-08","end":"2026-10-08","name":"x","reminders":[{"days":0},{"days":1},{"days":2},{"days":3}]}]"""
        assertEquals(3, decodePersonalDates(raw).single().reminders.size)
    }
}
