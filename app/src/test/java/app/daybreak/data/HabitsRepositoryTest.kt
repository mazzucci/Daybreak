package app.daybreak.data

import app.daybreak.domain.Badge
import app.daybreak.domain.Habit
import app.daybreak.domain.HabitColor
import app.daybreak.domain.HabitKind
import app.daybreak.domain.HabitPeriod
import app.daybreak.domain.HabitsData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitsRepositoryTest {
    private val store = InMemoryStore()
    private val repo = HabitsRepository(store)
    private val today = LocalDate.of(2026, 9, 28)

    private val water = Habit("w", "Drink water", HabitColor.BLUE, HabitKind.BUILD, HabitPeriod.DAY, 8, today.minusDays(10))
    private val bike = Habit("b", "Ride a bike", HabitColor.GREEN, HabitKind.BUILD, HabitPeriod.WEEK, 1, today.minusDays(3))
    private val takeout = Habit("t", "No takeout", HabitColor.CORAL, HabitKind.AVOID, HabitPeriod.WEEK, 0, today)

    private fun titles() = repo.data.value.habits.map { it.title }

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

    @Test fun `update keeps the log and start day`() {
        repo.add(water)
        repo.log("w", today, +3)
        repo.update(water.copy(title = "Water", target = 6, color = HabitColor.TEAL, created = today, log = emptyMap()))
        val h = repo.data.value.habits.single()
        assertEquals("Water", h.title)
        assertEquals(6, h.target)
        assertEquals(HabitColor.TEAL, h.color)
        assertEquals(water.created, h.created)
        assertEquals(mapOf(today to 3), h.log)
    }

    @Test fun `moves and ignores out-of-range moves`() {
        repo.add(water); repo.add(bike); repo.add(takeout)
        repo.move(0, 2)
        assertEquals(listOf("Ride a bike", "No takeout", "Drink water"), titles())
        repo.move(5, 0)
        repo.move(-1, 1)
        assertEquals(listOf("Ride a bike", "No takeout", "Drink water"), titles())
    }

    @Test fun `remove banks points and badges`() {
        repo.add(water); repo.add(bike)
        repo.remove("w", points = 120, badges = setOf(Badge.FIRST_LOG))
        repo.remove("b", points = 30, badges = setOf(Badge.FIRST_LOG, Badge.WEEKS_4))
        repo.remove("gone", points = 1000)
        assertEquals(HabitsData(emptyList(), 150, setOf(Badge.FIRST_LOG, Badge.WEEKS_4)), repo.data.value)
        assertEquals(repo.data.value, HabitsRepository(store).data.value)
    }

    @Test fun `persists everything across instances`() {
        repo.add(water); repo.add(bike); repo.add(takeout)
        repo.log("w", today, +5)
        repo.log("b", today.minusDays(2), +1)
        repo.log("t", today, +1)
        repo.remove("t", points = 40, badges = setOf(Badge.MONTH_AVOIDED))
        assertEquals(repo.data.value, HabitsRepository(store).data.value)
    }

    @Test fun `clears the key when nothing is left`() {
        repo.add(water)
        assertTrue(store.getString("habits") != null)
        repo.remove("w")
        assertNull(store.getString("habits"))
    }

    @Test fun `drops entries that don't parse and keeps the rest`() {
        val raw = """
            {"habits": [
              {"id": "w", "title": "Drink water", "color": "BLUE", "kind": "BUILD", "period": "DAY", "target": 8,
               "created": "2026-09-18", "log": {"2026-09-27": 8, "not a date": 3, "2026-09-28": "lots", "2026-09-26": 0}},
              {"id": "x", "title": "Broken", "color": "BLUE", "kind": "SOMETIMES", "period": "DAY", "target": 1, "created": "2026-09-18"},
              {"id": "y", "title": "No date", "color": "BLUE", "kind": "BUILD", "period": "DAY", "target": 1, "created": "soon"},
              "not an object",
              {"id": "t", "title": "No takeout", "color": "ULTRAVIOLET", "kind": "AVOID", "period": "WEEK", "target": -3,
               "created": "2026-09-28"},
              {"id": "w", "title": "Duplicate", "color": "BLUE", "kind": "BUILD", "period": "DAY", "target": 1, "created": "2026-09-18"}
            ],
            "bankedPoints": 70, "bankedBadges": ["FIRST_LOG", "SHINY"]}
        """.trimIndent()
        val data = HabitsRepository(InMemoryStore(mapOf("habits" to raw))).data.value
        assertEquals(listOf("w", "t"), data.habits.map { it.id })
        assertEquals(mapOf(LocalDate.of(2026, 9, 27) to 8), data.habits[0].log)
        // An unknown colour falls back; an impossible allowance is clamped.
        assertEquals(HabitColor.BLUE, data.habits[1].color)
        assertEquals(0, data.habits[1].target)
        assertEquals(70, data.bankedPoints)
        assertEquals(setOf(Badge.FIRST_LOG), data.bankedBadges)
    }

    @Test fun `corrupt store starts empty`() {
        assertEquals(HabitsData(), HabitsRepository(InMemoryStore(mapOf("habits" to "{nope"))).data.value)
        assertEquals(HabitsData(), HabitsRepository(InMemoryStore(mapOf("habits" to "[]"))).data.value)
    }
}
