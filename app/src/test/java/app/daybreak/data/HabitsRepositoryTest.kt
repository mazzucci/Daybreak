package app.daybreak.data

import app.daybreak.domain.Badge
import app.daybreak.domain.Habit
import app.daybreak.domain.HabitColor
import app.daybreak.domain.HabitDraft
import app.daybreak.domain.HabitGoal
import app.daybreak.domain.HabitKind
import app.daybreak.domain.HabitPeriod
import app.daybreak.domain.HabitsData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitsRepositoryTest {
    private val store = InMemoryStore()
    private var today = LocalDate.of(2026, 9, 28)

    /** Writes inline, so the store can be checked straight after a change. */
    private fun repo(s: KeyValueStore = store, weekStart: DayOfWeek = DayOfWeek.MONDAY) =
        HabitsRepository(s, { today }, { weekStart }, CoroutineScope(Dispatchers.Unconfined))

    private val repo = repo()

    private val water = Habit("w", "Drink water", HabitColor.BLUE, HabitKind.BUILD, HabitPeriod.DAY, 8, today.minusDays(10))
    private val bike = Habit("b", "Ride a bike", HabitColor.GREEN, HabitKind.BUILD, HabitPeriod.WEEK, 1, today.minusDays(3))
    private val takeout = Habit("t", "No takeout", HabitColor.CORAL, HabitKind.AVOID, HabitPeriod.WEEK, 0, today)

    private fun titles() = repo.data.value.habits.map { it.title }
    private fun stored() = JSONObject(store.getString("habits")!!)

    @Test fun `starts empty`() = assertEquals(HabitsData(), repo.data.value)

    @Test fun `adds in order and ignores a taken id`() {
        assertTrue(repo.add(water))
        assertTrue(repo.add(bike))
        assertFalse(repo.add(water.copy(title = "Again")))
        assertEquals(listOf("Drink water", "Ride a bike"), titles())
    }

    @Test fun `logs per day, never below zero`() {
        repo.add(water)
        repo.log("w", today, +1)
        repo.log("w", today, +1)
        repo.log("w", today.minusDays(1), +1)
        assertEquals(mapOf(today to 2, today.minusDays(1) to 1), repo.data.value.habits[0].log)
        repo.log("w", today.minusDays(1), -1)
        repo.log("w", today.minusDays(1), -1)
        // A day back at zero is dropped, not stored as 0.
        assertEquals(mapOf(today to 2), repo.data.value.habits[0].log)
        repo.log("nope", today, +1)
        assertEquals(1, repo.data.value.habits.size)
    }

    @Test fun `start days and logs are never filed after today, but one there can be taken back`() {
        repo.add(water.copy(created = today.plusDays(3), goals = listOf(HabitGoal(today.plusDays(3), HabitKind.BUILD, HabitPeriod.DAY, 8))))
        val h = repo.data.value.habits.single()
        assertEquals(today, h.created)
        assertEquals(today, h.goals.single().from)
        repo.log("w", today.plusDays(2), +1)
        assertEquals(mapOf(today to 1), repo.data.value.habits[0].log)
        // One stored ahead (the clock was ahead when it was logged) can still be undone.
        val ahead = repo(InMemoryStore(mapOf("habits" to v1(""""log": {"2026-10-05": 2}"""))))
        ahead.log("w", LocalDate.of(2026, 10, 5), -1)
        assertEquals(mapOf(LocalDate.of(2026, 10, 5) to 1), ahead.data.value.habits[0].log)
    }

    @Test fun `update keeps the log and start day, and adds the goal from today`() {
        repo.add(water)
        repo.log("w", today, +3)
        repo.update("w", HabitDraft("Water", HabitColor.TEAL, HabitKind.BUILD, HabitPeriod.DAY, 6))
        val h = repo.data.value.habits.single()
        assertEquals("Water", h.title)
        assertEquals(6, h.target)
        assertEquals(HabitColor.TEAL, h.color)
        assertEquals(water.created, h.created)
        assertEquals(mapOf(today to 3), h.log)
        assertEquals(listOf(HabitGoal(water.created, HabitKind.BUILD, HabitPeriod.DAY, 8), HabitGoal(today, HabitKind.BUILD, HabitPeriod.DAY, 6)), h.goals)
        assertEquals(repo.data.value, repo().data.value)
        assertEquals(2, stored().getJSONArray("habits").getJSONObject(0).getJSONArray("goals").length())
    }

    @Test fun `moves by id and ignores moves past the ends`() {
        repo.add(water); repo.add(bike); repo.add(takeout)
        repo.move("w", +2)
        assertEquals(listOf("Ride a bike", "No takeout", "Drink water"), titles())
        repo.move("w", +1)
        repo.move("b", -1)
        repo.move("gone", -1)
        assertEquals(listOf("Ride a bike", "No takeout", "Drink water"), titles())
        repo.move("w", -1)
        assertEquals(listOf("Ride a bike", "Drink water", "No takeout"), titles())
    }

    @Test fun `remove banks points and badges`() {
        repo.add(water); repo.add(bike)
        repo.remove("w", points = 120, badges = setOf(Badge.FIRST_LOG))
        repo.remove("b", points = 30, badges = setOf(Badge.FIRST_LOG, Badge.WEEKS_4))
        repo.remove("gone", points = 1000)
        assertEquals(HabitsData(emptyList(), 150, setOf(Badge.FIRST_LOG, Badge.WEEKS_4)), repo.data.value)
        assertEquals(repo.data.value, repo().data.value)
    }

    @Test fun `persists everything across instances`() {
        repo.add(water); repo.add(bike); repo.add(takeout)
        repo.log("w", today, +5)
        repo.log("b", today.minusDays(2), +1)
        repo.log("t", today, +1)
        repo.remove("t", points = 40, badges = setOf(Badge.MONTH_AVOIDED))
        repo.markUndoHintShown()
        assertEquals(repo.data.value, repo().data.value)
        assertEquals(HabitsRepository.VERSION, stored().getInt("version"))
    }

    @Test fun `keeps the week start when nothing is left`() {
        repo.add(water)
        repo.remove("w")
        assertEquals("MONDAY", stored().getString("weekStart"))
    }

    // --- The first day of the week ---

    @Test fun `the first day of the week is frozen when first stored`() {
        val us = repo(weekStart = DayOfWeek.SUNDAY)
        us.add(water)
        assertEquals(DayOfWeek.SUNDAY, us.data.value.weekStart)
        // The phone's region changes: the stored day stays.
        assertEquals(DayOfWeek.SUNDAY, repo(weekStart = DayOfWeek.MONDAY).data.value.weekStart)
        // Settings changes it on purpose.
        us.setWeekStart(DayOfWeek.SATURDAY)
        assertEquals(DayOfWeek.SATURDAY, repo(weekStart = DayOfWeek.MONDAY).data.value.weekStart)
    }

    // --- Version 1, and what doesn't parse ---

    /** A version-1 store (no version, goals or week start) with one habit "w", plus [extra] fields on it. */
    private fun v1(extra: String = "") = """
        {"habits": [{"id": "w", "title": "Drink water", "color": "BLUE", "kind": "BUILD", "period": "DAY", "target": 8,
          "created": "2026-09-18"${if (extra.isEmpty()) "" else ", $extra"}}],
         "bankedPoints": 70, "bankedBadges": ["FIRST_LOG"]}
    """.trimIndent()

    @Test fun `version 1 migrates to one goal from the start and freezes the week start`() {
        val s = InMemoryStore(mapOf("habits" to v1(""""log": {"2026-09-27": 8}""")))
        val data = repo(s, weekStart = DayOfWeek.SUNDAY).data.value
        val h = data.habits.single()
        assertEquals(listOf(HabitGoal(LocalDate.of(2026, 9, 18), HabitKind.BUILD, HabitPeriod.DAY, 8)), h.goals)
        assertEquals(DayOfWeek.SUNDAY, data.weekStart)
        // Written back at once, so the region changing later doesn't move it.
        val root = JSONObject(s.getString("habits")!!)
        assertEquals(HabitsRepository.VERSION, root.getInt("version"))
        assertEquals("SUNDAY", root.getString("weekStart"))
        assertEquals(DayOfWeek.SUNDAY, repo(s, weekStart = DayOfWeek.MONDAY).data.value.weekStart)
    }

    private val messy = """
        {"version": 2, "weekStart": "SUNDAY", "futureSetting": {"x": 1},
         "habits": [
          {"id": "w", "title": "Drink water", "color": "BLUE", "kind": "BUILD", "period": "DAY", "target": 8,
           "created": "2026-09-18", "reminder": "08:00",
           "log": {"2026-09-27": 8, "not a date": 3, "2026-09-28": "lots", "2026-09-26": 0}},
          {"id": "x", "title": "Broken", "color": "BLUE", "kind": "SOMETIMES", "period": "DAY", "target": 1, "created": "2026-09-18"},
          {"id": "y", "title": "No date", "color": "BLUE", "kind": "BUILD", "period": "DAY", "target": 1, "created": "soon"},
          "not an object",
          {"id": "t", "title": "No takeout", "color": "ULTRAVIOLET", "kind": "AVOID", "period": "WEEK", "target": -3,
           "created": "2026-09-28"},
          {"id": "w", "title": "Duplicate", "color": "BLUE", "kind": "BUILD", "period": "DAY", "target": 1, "created": "2026-09-18"}
         ],
         "bankedPoints": 70, "bankedBadges": ["FIRST_LOG", "SHINY"]}
    """.trimIndent()

    @Test fun `uses what parses and keeps the rest`() {
        val data = repo(InMemoryStore(mapOf("habits" to messy))).data.value
        assertEquals(listOf("w", "t"), data.habits.map { it.id })
        assertEquals(mapOf(LocalDate.of(2026, 9, 27) to 8), data.habits[0].log)
        // An unknown colour shows as blue; an impossible allowance is clamped.
        assertEquals(HabitColor.BLUE, data.habits[1].color)
        assertEquals(0, data.habits[1].target)
        assertEquals(70, data.bankedPoints)
        assertEquals(setOf(Badge.FIRST_LOG), data.bankedBadges)
        assertEquals(DayOfWeek.SUNDAY, data.weekStart)
    }

    @Test fun `what doesn't parse is written back unchanged on save`() {
        val s = InMemoryStore(mapOf("habits" to messy))
        val r = repo(s)
        r.log("w", today, +1)
        val root = JSONObject(s.getString("habits")!!)
        val habits = root.getJSONArray("habits")
        // The two that parse, then the four that don't, as they were.
        assertEquals(6, habits.length())
        val w = habits.getJSONObject(0)
        assertEquals(1, w.getJSONObject("log").getInt("2026-09-28"))
        assertEquals(3, w.getJSONObject("log").getInt("not a date"))
        assertEquals("08:00", w.getString("reminder"))
        // The unknown colour round-trips through a save.
        assertEquals("ULTRAVIOLET", habits.getJSONObject(1).getString("color"))
        assertEquals("SOMETIMES", habits.getJSONObject(2).getString("kind"))
        assertEquals("soon", habits.getJSONObject(3).getString("created"))
        assertEquals("not an object", habits.getString(4))
        assertEquals("Duplicate", habits.getJSONObject(5).getString("title"))
        assertEquals(listOf("FIRST_LOG", "SHINY"), root.getJSONArray("bankedBadges").let { a -> (0 until a.length()).map(a::getString) })
        assertEquals(1, root.getJSONObject("futureSetting").getInt("x"))
        // And again through a second instance and save: still all there.
        val again = repo(s)
        again.move("t", -1)
        assertEquals(6, JSONObject(s.getString("habits")!!).getJSONArray("habits").length())
        assertEquals("ULTRAVIOLET", JSONObject(s.getString("habits")!!).getJSONArray("habits").getJSONObject(0).getString("color"))
    }

    @Test fun `an unknown colour goes once another is chosen`() {
        val s = InMemoryStore(mapOf("habits" to messy))
        val r = repo(s)
        // Renamed, its colour left as shown: the stored one stays.
        r.update("t", HabitDraft("No delivery", HabitColor.BLUE, HabitKind.AVOID, HabitPeriod.WEEK, 0))
        assertEquals("No delivery", JSONObject(s.getString("habits")!!).getJSONArray("habits").getJSONObject(1).getString("title"))
        assertEquals("ULTRAVIOLET", JSONObject(s.getString("habits")!!).getJSONArray("habits").getJSONObject(1).getString("color"))
        r.update("t", HabitDraft("No takeout", HabitColor.PINK, HabitKind.AVOID, HabitPeriod.WEEK, 0))
        assertEquals("PINK", JSONObject(s.getString("habits")!!).getJSONArray("habits").getJSONObject(1).getString("color"))
    }

    @Test fun `a corrupt store is backed up before anything is written over it`() {
        val s = InMemoryStore(mapOf("habits" to "{nope"))
        val r = repo(s)
        assertEquals(emptyList<Habit>(), r.data.value.habits)
        assertEquals("{nope", s.getString(HabitsRepository.BACKUP_KEY))
        r.add(water)
        assertEquals("{nope", s.getString(HabitsRepository.BACKUP_KEY))
        assertEquals(1, repo(s).data.value.habits.size)
        // Another corrupt store later goes to the next key; the same one again isn't copied twice.
        s.putString("habits", "[]")
        repo(s)
        repo(s)
        assertEquals("{nope", s.getString(HabitsRepository.BACKUP_KEY))
        assertEquals("[]", s.getString("${HabitsRepository.BACKUP_KEY}.2"))
        assertNull(s.getString("${HabitsRepository.BACKUP_KEY}.3"))
        // "habits" that isn't a list is corrupt too, not an empty list to write over.
        val odd = InMemoryStore(mapOf("habits" to """{"habits": "lots"}"""))
        repo(odd)
        assertEquals("""{"habits": "lots"}""", odd.getString(HabitsRepository.BACKUP_KEY))
    }

    // --- Writing ---

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun `writes off the calling thread, latest first and once`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val writes = mutableListOf<String>()
        val counting = object : KeyValueStore by store {
            override fun putString(key: String, value: String) {
                writes += value
                store.putString(key, value)
            }
        }
        val r = HabitsRepository(counting, { today }, { DayOfWeek.MONDAY }, scope)
        r.add(water)
        repeat(5) { r.log("w", today, +1) }
        // Nothing written yet: the state is up to date, the store catches up on the write dispatcher.
        assertEquals(5, r.data.value.habits[0].log[today])
        assertNull(store.getString("habits"))
        scope.testScheduler.advanceUntilIdle()
        assertEquals(1, writes.size)
        assertEquals(r.data.value, repo().data.value)
    }
}
