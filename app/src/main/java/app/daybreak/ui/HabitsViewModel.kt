package app.daybreak.ui

import androidx.lifecycle.ViewModel
import app.daybreak.data.HabitsRepository
import app.daybreak.domain.HabitDraft
import app.daybreak.domain.HabitsData
import app.daybreak.domain.periodStart
import app.daybreak.domain.summarize
import app.daybreak.domain.celebrate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

/** A one-line cheer on one habit after a log, shown briefly in place. [id] tells two in a row apart. */
data class Celebration(val habitId: String, val message: String, val id: Long)

/**
 * The habits and the brief celebration after a log. The stats themselves are worked out on screen for the day it
 * is there; [today] and [weekFields] are what a log is filed under, injectable for tests.
 */
class HabitsViewModel(
    private val repo: HabitsRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val weekFields: () -> WeekFields = { WeekFields.of(Locale.getDefault()) },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    val data: StateFlow<HabitsData> = repo.data

    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()
    private var celebrations = 0L

    /** +1 for today, with a celebration when it meets a goal, reaches a milestone or earns a level or badge. */
    fun log(id: String) {
        val day = today()
        val wf = weekFields()
        val before = summarize(repo.data.value, day, wf)
        repo.log(id, day, +1)
        val message = celebrate(before, summarize(repo.data.value, day, wf), id)
        _celebration.value = message?.let { Celebration(id, it, ++celebrations) }
            ?: _celebration.value?.takeIf { it.habitId != id }
    }

    /** Takes back the latest log of this day or week (today's first). False when there's nothing to take back. */
    fun undo(id: String): Boolean {
        val habit = repo.data.value.habits.firstOrNull { it.id == id } ?: return false
        val day = today()
        val from = periodStart(day, habit.period, weekFields())
        val date = habit.log.keys.filter { !it.isBefore(from) && !it.isAfter(day) }.maxOrNull() ?: return false
        repo.log(id, date, -1)
        if (_celebration.value?.habitId == id) _celebration.value = null
        return true
    }

    /** Adds a new habit from [draft], starting today. */
    fun add(draft: HabitDraft) {
        repo.add(draft.toHabit(newId(), today()))
    }

    /** Saves [draft] over habit [id], keeping its log. */
    fun update(id: String, draft: HabitDraft) {
        val habit = repo.data.value.habits.firstOrNull { it.id == id } ?: return
        repo.update(draft.toHabit(id, habit.created))
    }

    /** Deletes a habit; what it earned (its points, and every badge so far) is kept. */
    fun remove(id: String) {
        val summary = summarize(repo.data.value, today(), weekFields())
        repo.remove(id, summary.of(id)?.points ?: 0, summary.badges)
        if (_celebration.value?.habitId == id) _celebration.value = null
    }

    fun move(from: Int, to: Int) = repo.move(from, to)

    /** Called once a celebration has had its moment. */
    fun celebrationShown(id: Long) {
        if (_celebration.value?.id == id) _celebration.value = null
    }
}
