package app.daybreak.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.daybreak.data.ImageLoader
import app.daybreak.data.OnThisDayRepository
import app.daybreak.data.OnThisDayToday
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The "On this day" card's day: its picks and the one showing, or null while there's nothing to show (not loaded
 * yet, offline, or switched off), which hides the card. [load] is cheap to call often (on resume, on a new day, on
 * pull-to-refresh): it does nothing while today's picks are in hand or on their way. [images] loads the pictures.
 */
class OnThisDayViewModel(
    private val repo: OnThisDayRepository,
    val images: ImageLoader? = null,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    private val _day = MutableStateFlow<OnThisDayToday?>(null)
    val day: StateFlow<OnThisDayToday?> = _day.asStateFlow()

    private var loading: Job? = null
    /** The date [loading] is for: a load still running for yesterday (just after midnight) is replaced. */
    private var loadingDate: LocalDate? = null

    /**
     * Fetches today's picks unless they're in hand (or on their way). A failure leaves the card hidden until the
     * next try; [force] (pull-to-refresh) tries at once rather than after the few minutes' wait a failure sets.
     */
    fun load(force: Boolean = false) {
        val date = today()
        val shown = _day.value
        if (shown?.date == date && shown.complete) return
        if (loading?.isActive == true) {
            if (loadingDate == date && !force) return
            loading?.cancel()
        }
        loadingDate = date
        loading = viewModelScope.launch {
            val fresh = repo.today(date, force)
            val before = _day.value?.takeIf { it.date == date }
            // Yesterday's never stands in for today's: hidden until today's arrive (or for good, offline). Today's
            // few stay up if asking again for the rest fails; when the rest arrive, the pick showing stays.
            val next = when {
                fresh == null -> before
                before?.current != null && before.current in fresh.picks -> fresh.copy(index = fresh.picks.indexOf(before.current))
                else -> fresh
            }
            _day.value = next
            if (fresh != null && next != null && next.index != fresh.index) repo.setIndex(date, next.index)
        }
    }

    /** "Another": the next of the day's picks, round to the first again; kept for the rest of the day. */
    fun another() {
        val day = _day.value ?: return
        if (day.picks.size < 2) return
        val next = day.copy(index = (day.index + 1) % day.picks.size)
        _day.value = next
        viewModelScope.launch { repo.setIndex(next.date, next.index) }
    }

    /** Switched off in Settings: hidden at once (the day's picks stay stored for when it's switched back on). */
    fun clear() {
        loading?.cancel()
        loading = null
        loadingDate = null
        _day.value = null
    }
}
