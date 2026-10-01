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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.DayOfWeek

class HabitsViewModelTest {
    private var today = LocalDate.of(2026, 9, 28) // a Monday
    private val repo = HabitsRepository(InMemoryStore(), { today }, { DayOfWeek.MONDAY }, CoroutineScope(Dispatchers.Unconfined))
    private var ids = 0
    private val vm = HabitsViewModel(repo, { today }, { "h${++ids}" })

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

    @Test fun `deleting keeps what finished days earned`() {
        vm.add(HabitDraft("Stretch", target = 1))
        vm.log("h1")
        today = today.plusDays(1)
        vm.log("h1")
        vm.remove("h1")
        val data = repo.data.value
        assertTrue(data.habits.isEmpty())
        // Yesterday's log and met day; today's go with the habit.
        assertEquals(LOG_POINTS + KEPT_DAY_POINTS, data.bankedPoints)
        assertEquals(setOf(Badge.FIRST_LOG), data.bankedBadges)
        assertNull(vm.celebration.value)
        assertEquals(LOG_POINTS + KEPT_DAY_POINTS, vm.summary.value.points)
    }

    @Test fun `deleting and re-adding earns nothing`() {
        repeat(3) {
            vm.add(HabitDraft("Stretch", target = 1))
            vm.log("h${it + 1}")
            vm.remove("h${it + 1}")
        }
        assertEquals(0, repo.data.value.bankedPoints)
        assertEquals(emptySet<Badge>(), repo.data.value.bankedBadges)
        assertEquals(0, vm.summary.value.points)
    }

    @Test fun `a weekly goal met this week isn't banked`() {
        vm.add(HabitDraft("Exercise", period = HabitPeriod.WEEK, target = 1))
        vm.log("h1")
        today = today.plusDays(3)
        vm.remove("h1")
        assertEquals(0, repo.data.value.bankedPoints)
    }

    @Test fun `editing the goal keeps the past as it was judged`() {
        vm.add(HabitDraft("Stretch", target = 1))
        vm.log("h1")
        today = today.plusDays(1)
        val before = vm.summary.value.points
        vm.update("h1", HabitDraft("Stretch", target = 3))
        val s = vm.summary.value.of("h1")!!
        assertEquals(before, vm.summary.value.points)
        assertEquals(1, s.streak)
        assertEquals("0 of 3 today", app.daybreak.domain.progressLine(s))
    }

    @Test fun `the summary is worked out once per change`() {
        vm.add(HabitDraft("Stretch", target = 2))
        val first = vm.summary.value
        vm.refresh()
        assertTrue(first === vm.summary.value)
        vm.log("h1")
        assertEquals(1, vm.summary.value.of("h1")!!.count)
        // A new day is picked up on refresh.
        today = today.plusDays(1)
        vm.refresh()
        assertEquals(today, vm.summary.value.today)
        assertEquals(0, vm.summary.value.of("h1")!!.count)
    }

    @Test fun `moves by id`() {
        vm.add(HabitDraft("A"))
        vm.add(HabitDraft("B"))
        vm.add(HabitDraft("C"))
        vm.move("h3", -1)
        vm.move("h3", -1)
        assertEquals(listOf("C", "A", "B"), vm.summary.value.stats.map { it.habit.title })
    }

    @Test fun `undo takes back one filed after today`() {
        repo.add(app.daybreak.domain.Habit("x", "Read", created = today, log = mapOf(today to 1, today.plusDays(2) to 1)))
        assertTrue(vm.undo("x"))
        assertEquals(mapOf(today to 1), log("x"))
    }

    @Test fun `home shows how to undo after its first log, once`() {
        vm.add(HabitDraft("Stretch", target = 3))
        vm.log("h1")
        assertFalse(vm.undoHint.value)
        vm.log("h1", fromHome = true)
        assertTrue(vm.undoHint.value)
        assertTrue(repo.data.value.undoHintShown)
        vm.undo("h1")
        assertFalse(vm.undoHint.value)
        vm.log("h1", fromHome = true)
        assertFalse(vm.undoHint.value)
    }

    @Test fun `changing the week start re-buckets on purpose`() {
        vm.add(HabitDraft("Exercise", period = HabitPeriod.WEEK, target = 2))
        today = LocalDate.of(2026, 10, 4) // Sunday
        vm.log("h1")
        today = LocalDate.of(2026, 10, 5) // Monday: a new Monday-first week, but not a new Saturday-first one
        vm.refresh()
        assertEquals(0, vm.summary.value.of("h1")!!.count)
        vm.setWeekStart(DayOfWeek.SATURDAY)
        assertEquals(1, vm.summary.value.of("h1")!!.count)
    }
}
