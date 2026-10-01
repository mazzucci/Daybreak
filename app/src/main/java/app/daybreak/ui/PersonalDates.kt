@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.daybreak.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.daybreak.domain.DEFAULT_REMINDER_TIME
import app.daybreak.domain.PERSONAL_DATE_NAME_MAX
import app.daybreak.domain.PersonalDate
import app.daybreak.domain.REMINDERS_MAX
import app.daybreak.domain.Reminder
import app.daybreak.domain.formatTimeOfDay
import app.daybreak.domain.hasShortReminder
import app.daybreak.domain.label
import app.daybreak.domain.nextReminderLine
import app.daybreak.domain.reminderOf
import app.daybreak.domain.reminderPresets
import app.daybreak.domain.reminderSummary
import app.daybreak.domain.remindersFor
import app.daybreak.domain.shortTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * What the system lets reminders do: show at all (notifications on, the Android 13 permission granted) and go off on
 * the minute (exact alarms allowed). Both true where it can't be known (previews, tests).
 */
data class ReminderAccess(val notificationsAllowed: Boolean = true, val exactAlarms: Boolean = true)

/**
 * Shows a short message with "Undo" ([message], then [onUndo] if it's tapped), where the app has a snackbar host;
 * null where it hasn't (screenshot tests), and then there's no undo.
 */
val LocalUndoMessage = compositionLocalOf<((message: String, onUndo: () -> Unit) -> Unit)?> { null }

/** Opens the date editor: for one of your dates, or ("Add a date") the range picker and then a new one. */
@Stable
class PersonalDateEditing internal constructor(val edit: (PersonalDate) -> Unit, val add: () -> Unit)

/**
 * Holds the date editor and its range picker for [content], which opens them through the [PersonalDateEditing] it's
 * given: Settings' "Your dates" and Home's Coming up share it. [onSave] gets the date as it was (null for a new one)
 * and as saved; Remove in the editor calls [onRemove], with "Undo" ([onRestore]) where there's a snackbar.
 */
@Composable
fun PersonalDateEditorHost(
    dates: List<PersonalDate>,
    today: LocalDate,
    onSave: (old: PersonalDate?, new: PersonalDate) -> Unit,
    onRemove: (PersonalDate) -> Unit,
    onRestore: (PersonalDate) -> Unit = {},
    access: ReminderAccess = ReminderAccess(),
    onAllowExactAlarms: () -> Unit = {},
    lastAllDay: List<Reminder> = emptyList(),
    lastTimed: List<Reminder> = emptyList(),
    content: @Composable (PersonalDateEditing) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    // The editor's dates (epoch days, so they survive rotation) and which date it edits (its id; null for a new one).
    var editorStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> dates.firstOrNull { it.id == id } }
    val close = { editorStart = null; editorEnd = null; editingId = null }
    // Removed meanwhile (from the other screen): nothing left to edit.
    if (editingId != null && editing == null) LaunchedEffect(Unit) { close() }
    val undo = LocalUndoMessage.current

    content(
        remember {
            PersonalDateEditing(
                edit = { d ->
                    editingId = d.id
                    editorStart = d.start.toEpochDay()
                    editorEnd = d.end.toEpochDay()
                },
                add = {
                    editingId = null
                    picking = true
                },
            )
        },
    )

    val start = editorStart
    val end = editorEnd
    if (start != null && end != null && (editingId == null || editing != null)) {
        BasicAlertDialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            DateEditor(
                start = LocalDate.ofEpochDay(start),
                end = LocalDate.ofEpochDay(end),
                initial = editing,
                today = today,
                onChangeDates = { picking = true },
                onDismiss = close,
                onSave = { saved ->
                    onSave(editing, saved)
                    close()
                },
                onRemove = editing?.let { d ->
                    {
                        onRemove(d)
                        close()
                        undo?.invoke("Removed ${d.title}") { onRestore(d) }
                    }
                },
                lastAllDay = lastAllDay,
                lastTimed = lastTimed,
                exactAlarms = access.exactAlarms,
                onAllowExactAlarms = onAllowExactAlarms,
                modifier = Modifier.padding(horizontal = 24.dp).imePadding(),
            )
        }
    }
    if (picking) {
        // The picker works in UTC midnights. From a year back, so a birthday can be picked where it last was (it
        // then comes round every year); a past date can only be added as a yearly one. Changing the dates of one
        // being edited starts from them.
        val yearAgoMillis = today.minusYears(1).toEpochDay() * DAY_MILLIS
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = editorStart?.times(DAY_MILLIS)?.takeIf { it >= yearAgoMillis },
            initialSelectedEndDateMillis = editorEnd?.times(DAY_MILLIS)?.takeIf { it >= yearAgoMillis && editorEnd != editorStart },
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= yearAgoMillis
                override fun isSelectableYear(year: Int) = year >= today.year - 1
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        editorStart = state.selectedStartDateMillis?.div(DAY_MILLIS)
                        editorEnd = (state.selectedEndDateMillis ?: state.selectedStartDateMillis)?.div(DAY_MILLIS)
                        picking = false
                    },
                    enabled = state.selectedStartDateMillis != null,
                ) { Text(if (editorStart == null) "Next" else "OK") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(
                state,
                title = { Text("Pick a day, or the first and last day", Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * "Your dates": birthdays, big days and time off still ahead; tapping one edits it (and removes it, in the editor).
 * "Add a date" asks for the dates (a range picker, so one day is a tap and a week is two), then the editor. Under the
 * list, a hint when reminders can't show or may be late.
 */
@Composable
internal fun PersonalDatesCard(
    dates: List<PersonalDate>,
    shown: Boolean,
    today: LocalDate,
    onEdit: (PersonalDate) -> Unit = {},
    onAdd: () -> Unit = {},
    access: ReminderAccess = ReminderAccess(),
    onOpenNotificationSettings: () -> Unit = {},
    onAllowExactAlarms: () -> Unit = {},
) {
    SettingsCard {
        Text("Your dates", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            "Birthdays, big days, time off — counted down on Home once they're within four months, with a reminder " +
                "if you like. Days off count as a break. Kept on this phone, not backed up." +
                if (shown) "" else " Shown once Holidays and countdowns is on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val ahead = dates.mapNotNull { d -> d.next(today)?.let { d to it } }.sortedBy { it.second.start }
        if (ahead.isNotEmpty()) Spacer(Modifier.height(8.dp))
        ahead.forEach { (d, next) -> PersonalDateRow(d, next, today, onEdit = { onEdit(d) }) }
        val withReminders = ahead.map { it.first }.filter { it.reminders.isNotEmpty() }
        if (withReminders.isNotEmpty() && !access.notificationsAllowed) {
            Spacer(Modifier.height(8.dp))
            NotificationsOffHint(onOpenNotificationSettings)
        } else if (withReminders.any { it.hasShortReminder() } && !access.exactAlarms) {
            Spacer(Modifier.height(8.dp))
            ExactAlarmHint(onAllowExactAlarms)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onAdd) {
            Icon(Icons.Default.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Add a date")
        }
    }
}

/**
 * One of your dates: its name, then "Sat, Oct 10 · 2:00 PM · every year · day off", then its reminders after a bell
 * ("1 hour and 1 day before"), with a chevron: the whole row opens the editor.
 */
@Composable
private fun PersonalDateRow(d: PersonalDate, next: PersonalDate, today: LocalDate, onEdit: () -> Unit) {
    // A yearly date is its day and month ("Aug 19 · every year"); the rest keep weekday and, if not this year's, year.
    val facts = listOfNotNull(
        if (d.yearly) formatYearly(next) else formatPersonalDates(next, today.year),
        d.time?.let { formatTimeOfDay(it) },
        "every year".takeIf { d.yearly },
        "day off".takeIf { d.dayOff },
    )
    val reminders = reminderSummary(d.reminders)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    // No-break space before each dot, so a wrapped line never starts with one; each date keeps its month and day
    // ("Oct 14") and each time is kept whole: lines break between them.
    fun whole(text: String) = text.replace(' ', '\u00A0')
    val detail = facts.joinToString("\u00A0· ") { f -> f.split(" – ").joinToString(" – ") { whole(it).replace(",\u00A0", ", ") } }
    val spoken = listOfNotNull(d.title, facts.joinToString(", "), reminders.takeIf { it.isNotEmpty() }?.let { "Reminders: $it" }).joinToString(", ")
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Edit", onClick = onEdit)
            .clearAndSetSemantics { contentDescription = spoken }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = secondary)
            if (reminders.isNotEmpty()) {
                Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Top) {
                    // 16dp at the default font size, growing with it, and centred on the first line of text.
                    val line = with(LocalDensity.current) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }
                    val bell = (line * 0.8f).coerceAtLeast(16.dp)
                    Icon(
                        Icons.Outlined.Notifications, contentDescription = null,
                        Modifier.padding(top = ((line - bell) / 2).coerceAtLeast(0.dp)).size(bell), tint = secondary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(reminders, style = MaterialTheme.typography.bodySmall, color = secondary)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, Modifier.size(24.dp), tint = secondary)
    }
}

/** A quiet note about reminders with the one thing to do about it: an icon, the text, and a text button under it. */
@Composable
private fun ReminderHint(icon: ImageVector, tint: Color, text: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, Modifier.padding(top = 2.dp).size(16.dp), tint = tint)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onAction, Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                Text(action)
            }
        }
    }
}

/** Reminders are set but can't show: notifications are off for the app (or its Reminders channel). */
@Composable
private fun NotificationsOffHint(onOpenSettings: () -> Unit) {
    ReminderHint(
        Icons.Default.Warning, MaterialTheme.weatherColors.attention,
        "Notifications are off for Daybreak, so reminders won't show.", "Turn on notifications", onOpenSettings,
    )
}

/** Exact alarms aren't allowed, so a reminder minutes or hours before something may come a little late. */
@Composable
private fun ExactAlarmHint(onAllow: () -> Unit) {
    ReminderHint(
        ScheduleIcon, MaterialTheme.colorScheme.onSurfaceVariant,
        "Reminders may run a few minutes late.", "Allow exact timing", onAllow,
    )
}

/** Material's outlined "schedule" clock (the core icon set doesn't have it). */
private val ScheduleIcon: ImageVector by lazy {
    ImageVector.Builder("Schedule", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 " +
                    "-8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z",
            ),
            fill = SolidColor(Color.Black),
        )
        .build()
}

/**
 * Adding or editing one of your dates, between [start] and [end] (picked already; "Change" picks them again): a
 * label, day off, every year, an optional time, and up to [REMINDERS_MAX] reminders from the presets for an all-day
 * or a timed date (or a custom one), with when the next ones go off. [initial] is the date being edited, null for a
 * new one, which starts with the reminders last saved for its kind of date ([lastAllDay], [lastTimed]). [onRemove],
 * when editing, removes it.
 */
@Composable
fun DateEditor(
    start: LocalDate,
    end: LocalDate,
    initial: PersonalDate?,
    today: LocalDate,
    onChangeDates: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (PersonalDate) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    lastAllDay: List<Reminder> = emptyList(),
    lastTimed: List<Reminder> = emptyList(),
    /** Whether alarms go off on the minute; if not, picking a reminder under a day before a timed date says so. */
    exactAlarms: Boolean = true,
    onAllowExactAlarms: () -> Unit = {},
    /** For the next-reminder line; fixed in screenshot tests, otherwise when the editor opened. */
    now: ZonedDateTime? = null,
    /** For screenshots: the label field isn't focused (the cursor blinks). */
    focusLabel: Boolean = true,
) {
    val range = PersonalDate(start, end)
    val past = range.end.isBefore(today)
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var dayOff by rememberSaveable { mutableStateOf(initial?.dayOff ?: false) }
    // A date already gone this year is a yearly one (a birthday picked where it last was).
    var yearly by rememberSaveable { mutableStateOf(initial?.yearly ?: past) }
    LaunchedEffect(past) { if (past) yearly = true }
    var timeMinutes by rememberSaveable { mutableStateOf(initial?.time?.let { it.hour * 60 + it.minute }) }
    val time = timeMinutes?.let { LocalTime.of(it / 60, it % 60) }
    var reminderKeys by rememberSaveable { mutableStateOf((initial?.reminders ?: lastAllDay).joinToString(",") { it.key }) }
    val reminders = reminderKeys.split(",").mapNotNull(::reminderOf)
    // Until the reminders of a new date are touched, adding or taking off its time swaps in the last picks for that kind.
    var touched by rememberSaveable { mutableStateOf(initial != null) }
    fun setReminders(list: List<Reminder>) { reminderKeys = list.joinToString(",") { it.key } }
    fun pick(list: List<Reminder>) { touched = true; setReminders(list) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    val nextTime = range.copy(yearly = true).next(today)
    // A day off can go unnamed ("Day off"); anything else needs its label. A past date must come round again.
    val canSave = (name.isNotBlank() || dayOff) && (!past || yearly)
    val draft = range.copy(
        name = name.trim().take(PERSONAL_DATE_NAME_MAX), dayOff = dayOff, yearly = yearly, time = time, reminders = reminders,
        id = initial?.id?.ifBlank { null } ?: "",
    )
    val save = { if (canSave) onSave(draft.copy(id = draft.id.ifBlank { UUID.randomUUID().toString() })) }
    val focus = remember { FocusRequester() }
    if (focusLabel && initial == null) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // "Needed unless it's a day off" only once the label has been left, not while it's being typed.
    var labelFocused by remember { mutableStateOf(false) }
    var labelLeft by rememberSaveable { mutableStateOf(false) }
    val opened = remember { ZonedDateTime.now() }

    Surface(
        modifier.widthIn(max = 420.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // Month and day kept together, so a narrow dialog wraps after the weekday.
                    formatPersonalDates(if (yearly && nextTime != null) nextTime else range, today.year)
                        .replace(Regex("([A-Z][a-z]{2}) (\\d)"), "$1\u00A0$2"),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextButton(onChangeDates) { Text("Change") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                // Up to two lines, so a long label is seen whole at a large font; still one line of text.
                onValueChange = { name = it.replace('\n', ' ').take(PERSONAL_DATE_NAME_MAX) },
                label = { Text("Label") },
                placeholder = { Text("Mum's birthday") },
                maxLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                supportingText = if (labelLeft && name.isBlank() && !dayOff) {
                    { Text("Needed unless it's a day off") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged {
                    if (labelFocused && !it.isFocused) labelLeft = true
                    labelFocused = it.isFocused
                },
            )
            Spacer(Modifier.height(4.dp))
            SwitchRow("Day off", "Counts as a break", dayOff) { dayOff = it }
            SwitchRow(
                "Every year",
                if (past && nextTime != null) "Next: ${formatPersonalDates(nextTime, today.year)}" else "Like a birthday",
                yearly,
            ) { yearly = it }
            TimeRow(time, multiDay = end != start, onPick = { pickingTime = true }) {
                timeMinutes = null
                setReminders(if (touched) remindersFor(reminders, timed = false) else lastAllDay)
            }
            Spacer(Modifier.height(12.dp))
            Text("Remind me", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            val full = reminders.size >= REMINDERS_MAX
            // The presets swap when a time is added or taken off: a quick fade rather than a jump.
            AnimatedContent(
                targetState = time != null,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "presets",
            ) { timed ->
                val presets = reminderPresets(timed)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The presets for this kind of date, then any other picked (a custom one, or one from before).
                    (presets + reminders.filter { it !in presets }).forEach { r ->
                        val selected = r in reminders
                        FilterChip(
                            selected = selected,
                            onClick = { pick(if (selected) reminders - r else reminders + r) },
                            enabled = selected || !full,
                            // The next-reminder line says when, so the chips needn't ("On the day", not "…, 9 AM").
                            label = { Text(r.label(withDefaultTime = false)) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else {
                                null
                            },
                            // Several can be on at once: checkboxes to TalkBack, with their state.
                            modifier = Modifier.semantics {
                                role = Role.Checkbox
                                stateDescription = if (selected) "On" else if (full) "Off, $REMINDERS_MAX already on" else "Off"
                            },
                        )
                    }
                    // Kept in place when three are on (disabled), so the chips don't reflow.
                    AssistChip(
                        onClick = { custom = true },
                        enabled = !full,
                        label = { Text("Custom…") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }
            }
            // When they come: the next one or two, worked out as the alarm will; and the limit once it's reached.
            val note = listOfNotNull(
                nextReminderLine(draft, now ?: opened)?.let { "$it." },
                "Up to $REMINDERS_MAX for a date.".takeIf { full },
            ).joinToString(" ")
            if (note.isNotBlank()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (draft.hasShortReminder() && !exactAlarms) {
                Spacer(Modifier.height(8.dp))
                ExactAlarmHint(onAllowExactAlarms)
            }
            Spacer(Modifier.height(20.dp))
            // Remove at the start, Cancel and Save at the end; on a narrow screen at a large font, Cancel and Save
            // go under Remove rather than squeezing.
            FlowRow(Modifier.fillMaxWidth()) {
                if (onRemove != null) {
                    TextButton(onRemove, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Remove", maxLines = 1)
                    }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text("Cancel", maxLines = 1) }
                    TextButton(save, enabled = canSave) { Text(if (initial == null) "Add" else "Save", maxLines = 1) }
                }
            }
        }
    }

    if (pickingTime) {
        TimePickerDialog(
            initial = time ?: LocalTime.of(9, 0),
            onDismiss = { pickingTime = false },
            onPick = {
                if (time == null) setReminders(if (touched) remindersFor(reminders, timed = true) else lastTimed)
                timeMinutes = it.hour * 60 + it.minute
                pickingTime = false
            },
        )
    }
    if (custom) {
        BasicAlertDialog(onDismissRequest = { custom = false }) {
            CustomReminderPanel(
                timed = time != null,
                onDismiss = { custom = false },
                onPick = { r ->
                    if (r !in reminders) pick(reminders + r)
                    custom = false
                },
            )
        }
    }
}

/** "Add a time" (all day), or "Starts at 2:00 PM" with a way to take it off again ("On the first day" for a run). */
@Composable
private fun TimeRow(time: LocalTime?, multiDay: Boolean, onPick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = if (time == null) "Add a time" else "Change the time", onClick = onPick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (time == null) "Add a time" else "Starts at ${formatTimeOfDay(time).replace(' ', '\u00A0')}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (time == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            val detail = when {
                time == null -> "All day"
                multiDay -> "On the first day"
                else -> null
            }
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (time != null) {
            IconButton(onRemove) { Icon(Icons.Default.Close, contentDescription = "Remove the time") }
        }
    }
}

/**
 * A reminder that isn't a preset: so many minutes, hours or days before a timed date, or days or weeks before an
 * all-day one at a time of its own (9 AM unless it's changed), up to eight weeks. Drawn as a dialog's panel, so a
 * screenshot can show it.
 */
@Composable
internal fun CustomReminderPanel(
    timed: Boolean,
    onDismiss: () -> Unit,
    onPick: (Reminder) -> Unit,
    initialAt: LocalTime = DEFAULT_REMINDER_TIME,
) {
    val units = if (timed) listOf("Minutes" to 1, "Hours" to 60, "Days" to 24 * 60) else listOf("Days" to 1, "Weeks" to 7)
    var unit by rememberSaveable { mutableStateOf(if (timed) 1 else 0) }
    var amount by rememberSaveable { mutableStateOf("2") }
    var atMinutes by rememberSaveable { mutableStateOf(initialAt.hour * 60 + initialAt.minute) }
    val at = LocalTime.of(atMinutes / 60, atMinutes % 60)
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val n = amount.toIntOrNull()
    val reminder = n?.takeIf { it > 0 }?.let { it * units[unit].second }?.let { total ->
        runCatching { if (timed) Reminder.MinutesBefore(total) else Reminder.DaysBefore(total, at) }.getOrNull()
    }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(24.dp)) {
            Text("Remind me", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter(Char::isDigit).take(4) },
                label = { Text("How many") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text(reminder?.label() ?: "Up to 8 weeks before") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                units.forEachIndexed { i, (label, _) ->
                    SegmentedButton(selected = unit == i, onClick = { unit = i }, shape = SegmentedButtonDefaults.itemShape(i, units.size)) {
                        Text(label, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (timed) {
                Text("before it starts", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 12.dp))
            } else {
                // "before, at 9 AM": the time is the way to change it.
                Row {
                    Text("before,", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alignByBaseline())
                    Text(
                        "at ${shortTime(at).replace(' ', '\u00A0')}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alignByBaseline().clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = "Change the time", role = Role.Button) { pickingTime = true }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.align(Alignment.End)) {
                TextButton(onDismiss) { Text("Cancel") }
                TextButton({ reminder?.let(onPick) }, enabled = reminder != null) { Text("Add") }
            }
        }
    }
    if (pickingTime) {
        TimePickerDialog(
            initial = at,
            onDismiss = { pickingTime = false },
            onPick = {
                atMinutes = it.hour * 60 + it.minute
                pickingTime = false
            },
        )
    }
}

/** A label, a quiet line under it, and a switch; the whole row toggles. */
@Composable
private fun SwitchRow(label: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A yearly date without its weekday or year: "Aug 19", "Dec 30 – Jan 2". */
private fun formatYearly(d: PersonalDate): String {
    fun f(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
    return if (d.start == d.end) f(d.start) else "${f(d.start)} – ${f(d.end)}"
}

/** "Mon, Oct 13", or "Mon, Oct 13 – Fri, Oct 17" (with the year when it isn't [thisYear]). */
private fun formatPersonalDates(d: PersonalDate, thisYear: Int): String {
    fun f(date: LocalDate) = date.format(DateTimeFormatter.ofPattern(if (date.year == thisYear) "EEE, MMM d" else "EEE, MMM d, yyyy", Locale.US))
    return if (d.start == d.end) f(d.start) else "${f(d.start)} – ${f(d.end)}"
}

private const val DAY_MILLIS = 86_400_000L
