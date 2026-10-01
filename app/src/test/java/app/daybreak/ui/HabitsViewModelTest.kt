package app.daybreak.ui

import app.daybreak.data.HabitsRepository
import app.daybreak.data.InMemoryStore
import app.daybreak.domain.Badge
import app.daybreak.domain.HABIT_PRESETS
import app.daybreak.domain.HabitDraft
import app.daybreak.domain.HabitKind
import app.daybreak.domain.HabitPeriod
import app.daybreak.domain.KEPT_DAY_POINTS
import app.daybreak.domain.LOG_POINTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

class HabitsViewModelTest {
    private var today = LocalDate.of(2026, 9, 28) // a Monday
    private val repo = HabitsRepository(InMemoryStore())
    private var ids = 0
    private val vm = HabitsViewModel(repo, { today }, { WeekFields.of(Locale.UK) }, { "h${++ids}" })

    private fun log(id: String) = repo.data.value.habits.first { it.id == id }.log

    @Test fun `adds a habit starting today`() {
        vm.add(HABIT_PRESETS[0])
        val h = repo.data.value.habits.single()
        assertEquals("h1", h.id)
        assertEquals("Drink water", h.title)
        assertEquals(today, h.created)
    }

    @Test fun `logs today and celebrates the goal, then a first badge`() {
        vm.add(HabitDraft("Stretch", target = 2))
        vm.log("h1")
        assertEquals("New badge: First step · +${LOG_POINTS}", vm.celebration.value?.message)
        vm.log("h1")
        assertEquals(mapOf(today to 2), log("h1"))
        val c = vm.celebration.value!!
        assertEquals("h1", c.habitId)
        assertEquals("Done for today · +${LOG_POINTS + KEPT_DAY_POINTS}", c.message)
        // A log past the goal earns nothing, and clears the cheer for that habit.
        vm.log("h1")
        assertNull(vm.celebration.value)
    }

    @Test fun `celebration clears once shown, but not a newer one`() {
        vm.add(HabitDraft("Stretch", target = 1))
        vm.add(HabitDraft("Read", target = 1))
        vm.log("h1")
        val first = vm.celebration.value!!
        vm.log("h2")
        val second = vm.celebration.value!!
        vm.celebrationShown(first.id)
        assertEquals(second, vm.celebration.value)
        vm.celebrationShown(second.id)
        assertNull(vm.celebration.value)
    }

    @Test fun `a slip isn't celebrated`() {
        vm.add(HabitDraft("No takeout", kind = HabitKind.AVOID, period = HabitPeriod.WEEK, target = 0))
        vm.log("h1")
        assertNull(vm.celebration.value)
        assertEquals(mapOf(today to 1), log("h1"))
    }

    @Test fun `undo takes back the latest log of this period`() {
        vm.add(HabitDraft("Exercise", period = HabitPeriod.WEEK, target = 3))
        today = LocalDate.of(2026, 9, 29)
        vm.log("h1")
        today = LocalDate.of(2026, 9, 30)
        // Nothing today, so Tuesday's log goes.
        assertTrue(vm.undo("h1"))
        assertEquals(emptyMap<LocalDate, Int>(), log("h1"))
        assertFalse(vm.undo("h1"))
    }

    @Test fun `undo doesn't reach into last week`() {
        vm.add(HabitDraft("Exercise", period = HabitPeriod.WEEK, target = 3))
        today = LocalDate.of(2026, 10, 4) // Sunday, still the same Monday-first week
        vm.log("h1")
        today = LocalDate.of(2026, 10, 5) // Monday, a new week
        assertFalse(vm.undo("h1"))
        assertEquals(mapOf(LocalDate.of(2026, 10, 4) to 1), log("h1"))
    }

    @Test fun `update keeps the log`() {
        vm.add(HabitDraft("Water", target = 8))
        vm.log("h1")
        vm.update("h1", HabitDraft("Drink water", target = 6))
        val h = repo.data.value.habits.single()
        assertEquals("Drink water", h.title)
        assertEquals(6, h.target)
        assertEquals(mapOf(today to 1), h.log)
    }

    @Test fun `deleting keeps what was earned`() {
        vm.add(HabitDraft("Stretch", target = 1))
        vm.log("h1")
        vm.remove("h1")
        val data = repo.data.value
        assertTrue(data.habits.isEmpty())
        assertEquals(LOG_POINTS + KEPT_DAY_POINTS, data.bankedPoints)
        assertEquals(setOf(Badge.FIRST_LOG), data.bankedBadges)
        assertNull(vm.celebration.value)
    }
}
