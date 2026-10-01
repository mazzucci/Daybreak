package app.daybreak.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.daybreak.domain.ClockFormat
import java.time.LocalTime

/**
 * The Material time picker in a dialog, on the phone's 12- or 24-hour clock, starting at [initial]. [extraAction]
 * sits before Cancel (Clocks' "Now", the date editor's "Remove").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = ClockFormat.use24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onPick(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = {
            Row {
                extraAction?.invoke()
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
        // The app's display style is sized for the big temperature (104sp); the picker's digits need the standard one.
        text = {
            val digits = MaterialTheme.typography.displayLarge.copy(
                fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp, fontWeight = FontWeight.Normal,
            )
            MaterialTheme(typography = MaterialTheme.typography.copy(displayLarge = digits)) {
                TimePicker(state)
            }
        },
    )
}
