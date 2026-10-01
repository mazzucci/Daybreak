package app.daybreak.narration

import app.daybreak.domain.Forecast
import app.daybreak.domain.TempUnit

/** What the summary and the meme describe: one place's forecast, in the unit the user reads first. */
data class NarrationInput(
    val placeName: String,
    val forecast: Forecast,
    val unit: TempUnit,
)

/** Who wrote a meme's caption: the hand-written templates or Gemma. */
enum class NarrationSource { TEMPLATE, GEMMA }
