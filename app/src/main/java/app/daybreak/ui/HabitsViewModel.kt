package app.daybreak.ui

import androidx.lifecycle.ViewModel
import app.daybreak.data.HabitsRepository
import app.daybreak.domain.HabitDraft
import app.daybreak.domain.HabitsData
import app.daybreak.domain.HabitsSummary
import app.daybreak.domain.celebrate
import app.daybreak.domain.periodStart
import app.daybreak.domain.summarize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

/** A one-line cheer on one habit after a log, shown briefly in place. [id] tells two in a row apart. */
data class Celebration(val habitId: String, val message: String, val id: Long)

/**
 * The habits, worked out once per change into [summary] for both Home and the tab, and the brief celebration
 * after a log. [today] is what a log is filed under, injectable for tests; the screens call [refresh] when the date
 * changes. Weeks start on the day stored with the habits.
 */
class HabitsViewModel(
    private val repo: HabitsRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    val data: StateFlow<HabitsData> = repo.data

    private var summarized: HabitsData = repo.data.value
    private val _summary = MutableStateFlow(summarize(summarized, today()))
    val summary: StateFlow<HabitsSummary> = _summary.asStateFlow()

    private val _celebration = MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration.asStateFlow()
    private var celebrations = 0L

    /** Home's "Hold to undo", shown after the first log there ever, until something is taken back. */
    private val _undoHint = MutableStateFlow(false)
    val undoHint: StateFlow<Boolean> = _undoHint.asStateFlow()

    /** The summary for [day], worked out again only when the habits or the day have changed. */
    private fun current(day: LocalDate): HabitsSummary {
        val d = repo.data.value
        if (d !== summarized || _summary.value.today != day) {
            summarized = d
            _summary.value = summarize(d, day)
        }
        return _summary.value
    }

    /** Brings the summary up to date, as when the date has changed. */
    fun refresh() {
        current(today())
    }

    /**
     * +1 for today, with a celebration when it meets a goal, reaches a milestone or earns a level or badge. From
     * Home, the first log ever also shows how to take one back.
     */
    fun log(id: String, fromHome: Boolean = false) {
        val day = today()
        val before = current(day)
        repo.log(id, day, +1)
        val message = celebrate(before, current(day), id)
        _celebration.value = message?.let { Celebration(id, it, ++celebrations) }
            ?: _celebration.value?.takeIf { it.habitId != id }
        if (fromHome && !repo.data.value.undoHintShown) {
            repo.markUndoHintShown()
            current(day)
            _undoHint.value = true
        }
    }

    /**
     * Takes back the latest log of this day or week (today's first, or one filed after today while the clock was
     * ahead). False when there's nothing to take back.
     */
    fun undo(id: String): Boolean {
        val habit = repo.data.value.habits.firstOrNull { it.id == id } ?: return false
        val day = today()
        val from = periodStart(day, habit.period, repo.data.value.weekFields)
        val date = habit.log.keys.filter { !it.isBefore(from) }.maxOrNull() ?: return false
        repo.log(id, date, -1)
        current(day)
        if (_celebration.value?.habitId == id) _celebration.value = null
        _undoHint.value = false
        return true
    }

    /** Adds a new habit from [draft], starting today. */
    fun add(draft: HabitDraft) {
        repo.add(draft.toHabit(newId(), today()))
        refresh()
    }

    /** Saves [draft] over habit [id], keeping its log; a changed goal applies from today, never to the past. */
    fun update(id: String, draft: HabitDraft) {
        repo.update(id, draft)
        refresh()
    }

    /**
     * Deletes a habit, banking what its finished days and weeks earned (points and badges); what today or this week
     * would have added goes with it, so deleting and re-adding a habit never earns twice.
     */
    fun remove(id: String) {
        val summary = current(today())
        repo.remove(id, summary.of(id)?.settledPoints ?: 0, summary.settledBadges)
        refresh()
        if (_celebration.value?.habitId == id) _celebration.value = null
    }

    /** Moves habit [id] [by] places, −1 being up. */
    fun move(id: String, by: Int) {
        repo.move(id, by)
        refresh()
    }

    /** Changes the first day of the week, on purpose (Settings): every week is re-bucketed. */
    fun setWeekStart(day: DayOfWeek) {
        repo.setWeekStart(day)
        refresh()
    }

    /** Called once a celebration has had its moment. */
    fun celebrationShown(id: Long) {
        if (_celebration.value?.id == id) _celebration.value = null
    }
}
