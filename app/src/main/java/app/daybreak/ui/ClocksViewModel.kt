package app.daybreak.ui

import androidx.lifecycle.ViewModel
import app.daybreak.data.ClocksRepository
import app.daybreak.domain.Clock
import app.daybreak.domain.Place
import kotlinx.coroutines.flow.StateFlow

/** The saved clocks; the converter's picks are UI state. */
class ClocksViewModel(private val repo: ClocksRepository) : ViewModel() {
    val clocks: StateFlow<List<Clock>> = repo.clocks

    /** Adds [place] as a clock; false when it has no time zone or is already there. */
    fun add(place: Place): Boolean = Clock.of(place)?.let(repo::add) ?: false

    fun remove(clock: Clock) = repo.remove(clock.id)

    fun move(from: Int, to: Int) = repo.move(from, to)
}
