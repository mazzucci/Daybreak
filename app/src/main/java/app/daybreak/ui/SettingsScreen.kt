@file:OptIn(ExperimentalMaterial3Api::class)

package app.daybreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import app.daybreak.domain.Activity
import app.daybreak.domain.AppSettings
import app.daybreak.domain.TempUnit
import app.daybreak.narration.GemmaModelSource
import app.daybreak.narration.ModelStatus
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.daybreak.domain.PersonalDate
import app.daybreak.domain.PERSONAL_DATE_NAME_MAX
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import java.util.Locale

private val ScreenMargin = 16.dp
private const val MODEL_SIZE_HINT = "about 550 MB"

@Composable
fun SettingsScreen(
    settings: AppSettings,
    modelStatus: ModelStatus,
    onUnitChange: (TempUnit) -> Unit,
    onGemmaEnabledChange: (Boolean) -> Unit,
    onMemesEnabledChange: (Boolean) -> Unit,
    onSkyEnabledChange: (Boolean) -> Unit = {},
    onHabitsOnHomeChange: (Boolean) -> Unit = {},
    onComingUpEnabledChange: (Boolean) -> Unit,
    onAddPersonalDate: (PersonalDate) -> Unit = {},
    onRemovePersonalDate: (PersonalDate) -> Unit = {},
    /** For your dates (listed while they're ahead); fixed in screenshot tests. */
    today: LocalDate = LocalDate.now(),
    onActivityChange: (Activity?) -> Unit,
    onDownloadModel: (hfToken: String) -> Unit,
    onCancelDownload: () -> Unit,
    onImportModel: () -> Unit,
    onRemoveModel: () -> Unit,
    /** Null when Settings is a tab rather than a page with a way back. */
    onBack: (() -> Unit)?,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = ScreenMargin),
        ) {
            Spacer(Modifier.height(4.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Temperature", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    // The chosen unit is the big number; the other one is shown small next to it.
                    SingleChoiceSegmentedButtonRow(Modifier.width(200.dp)) {
                        TempUnit.entries.forEachIndexed { i, unit ->
                            SegmentedButton(
                                selected = settings.primaryUnit == unit,
                                onClick = { onUnitChange(unit) },
                                shape = SegmentedButtonDefaults.itemShape(i, TempUnit.entries.size),
                            ) { Text(if (unit == TempUnit.F) "°F first" else "°C first") }
                        }
                    }
                }
            }

            SectionTitle("Habits")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Habits on Home", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Today's habits under the weather, to log with a tap. Shown once you have a habit. " +
                                "Your habits are kept on this phone, not backed up.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = settings.habitsOnHome,
                        onCheckedChange = onHabitsOnHomeChange,
                        modifier = Modifier.semantics { contentDescription = "Habits on Home" },
                    )
                }
            }

            // Planning information, not a joke, so it sits with the forecast-page settings rather than under "Fun";
            // the section title matches the card's heading so the switch is easy to find.
            SectionTitle("Coming up")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Holidays and countdowns", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "The next public holiday, long weekend and season for each place, with the forecast when " +
                                "it's within the week. Holidays come from Nager.Date online, which sees your IP address and each place's country.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = settings.comingUpEnabled,
                        onCheckedChange = onComingUpEnabledChange,
                        modifier = Modifier.semantics { contentDescription = "Holidays and countdowns" },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PersonalDatesCard(settings.personalDates, settings.comingUpEnabled, today, onAddPersonalDate, onRemovePersonalDate)

            SectionTitle("Outdoor plans")
            SettingsCard {
                Text("Activity", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Each place's page shows the best time in the next 24 hours for it, judged on rain, wind, temperature and daylight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                // One choice out of four: a radio group to TalkBack, with a check that makes the pick readable
                // without relying on the fill colour alone.
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Activity.entries + null).forEach { activity ->
                        val selected = settings.activity == activity
                        FilterChip(
                            selected = selected,
                            onClick = { onActivityChange(activity) },
                            modifier = Modifier.semantics { role = Role.RadioButton },
                            label = { Text(activity?.label ?: "Off") },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            SectionTitle("Fun")
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily weather meme", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "A silly two-line caption on Home, made on this phone. " +
                                "Switch Gemma on below for a fresh one every day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = settings.memesEnabled,
                        onCheckedChange = onMemesEnabledChange,
                        modifier = Modifier.semantics { contentDescription = "Daily weather meme" },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SettingsCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tonight's sky", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "The moon's phase, the next full moon, meteor showers, and whether it's clear enough to " +
                                "look up. Worked out on this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = settings.skyEnabled,
                        onCheckedChange = onSkyEnabledChange,
                        modifier = Modifier.semantics { contentDescription = "Tonight's sky" },
                    )
                }
            }

            SectionTitle("Gemma")
            SettingsCard {
                val modelInstalled = modelStatus is ModelStatus.Installed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use Gemma for the meme", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Writes a fresh meme caption each day, entirely on this phone. Without it, a hand-written one is used." +
                                if (modelInstalled) "" else " Download or import the model below to turn this on.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    // Without a model the switch would promise something that can't happen, so show it off and disabled.
                    Switch(
                        checked = settings.gemmaEnabled && modelInstalled,
                        onCheckedChange = onGemmaEnabledChange,
                        enabled = modelInstalled,
                        modifier = Modifier.semantics { contentDescription = "Use Gemma for the meme" },
                    )
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
                ModelSection(
                    status = modelStatus,
                    onDownloadModel = onDownloadModel,
                    onCancelDownload = onCancelDownload,
                    onImportModel = onImportModel,
                    onRemoveModel = onRemoveModel,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * "Your dates": birthdays, big days and time off still ahead, each with a remove button, and "Add a date", which
 * asks for the dates (a range picker, so one day is a tap and a week is two), then a label and whether it's a day
 * off and whether it comes round every year.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalDatesCard(
    dates: List<PersonalDate>,
    shown: Boolean,
    today: LocalDate,
    onAdd: (PersonalDate) -> Unit,
    onRemove: (PersonalDate) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    // The dates picked, waiting for a label (epoch days, so they survive rotation).
    var pickedStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickedEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    SettingsCard {
        Text("Your dates", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            "Birthdays, big days, time off: counted down on Home alongside the holidays (once within four " +
                "months). Days off count as a break. Kept on this phone, not backed up." +
                if (shown) "" else " Shown once Holidays and countdowns is on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val ahead = dates.mapNotNull { d -> d.next(today)?.let { d to it } }.sortedBy { it.second.start }
        if (ahead.isNotEmpty()) Spacer(Modifier.height(8.dp))
        ahead.forEach { (d, next) ->
            // A yearly date is its day and month ("Aug 19 · every year"); the rest keep weekday and, if not this year's, year.
            val detail = listOfNotNull(
                if (d.yearly) formatYearly(next) else formatPersonalDates(next, today.year),
                "every year".takeIf { d.yearly },
                "day off".takeIf { d.dayOff },
            ).joinToString(" · ")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Text(d.title, style = MaterialTheme.typography.bodyLarge)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ onRemove(d) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${d.title}, $detail")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton({ picking = true }) {
            Icon(Icons.Default.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Add a date")
        }
    }
    if (picking) {
        // The picker works in UTC midnights. From a year back, so a birthday can be picked where it last was (it
        // then comes round every year); a past date can only be added as a yearly one.
        val yearAgoMillis = today.minusYears(1).toEpochDay() * DAY_MILLIS
        val state = rememberDateRangePickerState(
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
                        pickedStart = state.selectedStartDateMillis?.div(DAY_MILLIS)
                        pickedEnd = (state.selectedEndDateMillis ?: state.selectedStartDateMillis)?.div(DAY_MILLIS)
                        picking = false
                    },
                    enabled = state.selectedStartDateMillis != null,
                ) { Text("Next") }
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
    val start = pickedStart
    val end = pickedEnd
    if (start != null && end != null) {
        val range = PersonalDate(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end))
        val past = range.end.isBefore(today)
        var name by rememberSaveable { mutableStateOf("") }
        var dayOff by rememberSaveable { mutableStateOf(false) }
        // A date already gone this year is a yearly one (a birthday picked where it last was).
        var yearly by rememberSaveable { mutableStateOf(past) }
        val nextTime = range.copy(yearly = true).next(today)
        // A day off can go unnamed ("Day off"); anything else needs its label. A past date must come round again.
        val canAdd = (name.isNotBlank() || dayOff) && (!past || yearly)
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val close = { pickedStart = null; pickedEnd = null }
        val add = {
            if (canAdd) {
                onAdd(range.copy(name = name.trim().take(PERSONAL_DATE_NAME_MAX), dayOff = dayOff, yearly = yearly))
                close()
            }
        }
        AlertDialog(
            onDismissRequest = close,
            title = { Text(formatPersonalDates(if (yearly && nextTime != null) nextTime else range, today.year)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(PERSONAL_DATE_NAME_MAX) },
                        label = { Text("Label") },
                        placeholder = { Text("Mum's birthday") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                        supportingText = { if (name.isBlank() && !dayOff) Text("Needed unless it's a day off") },
                        modifier = Modifier.focusRequester(focus),
                    )
                    Spacer(Modifier.height(8.dp))
                    SwitchRow("Day off", "Counts as a break", dayOff) { dayOff = it }
                    SwitchRow(
                        "Every year",
                        if (past && nextTime != null) "Next: ${formatPersonalDates(nextTime, today.year)}" else "Like a birthday",
                        yearly,
                    ) { yearly = it }
                }
            },
            confirmButton = { TextButton(add, enabled = canAdd) { Text("Add") } },
            dismissButton = { TextButton(close) { Text("Cancel") } },
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** The whole Gemma model story: what's installed, and a numbered walk-through when nothing is. */
@Composable
private fun ModelSection(
    status: ModelStatus,
    onDownloadModel: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onImportModel: () -> Unit,
    onRemoveModel: () -> Unit,
) {
    Text("Gemma model", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(12.dp))
    when (status) {
        is ModelStatus.Installed -> InstalledState(status, onRemoveModel)
        is ModelStatus.Downloading -> DownloadingState(status, onCancelDownload)
        ModelStatus.Verifying -> BusyState("Checking the download…", "Making sure the file arrived intact.")
        ModelStatus.Importing -> BusyState("Copying the model…", "This can take a minute for a large file.")
        ModelStatus.NotInstalled -> SetupSteps(onDownloadModel, onImportModel)
        is ModelStatus.Failed -> {
            FailedBanner(status.message)
            Spacer(Modifier.height(16.dp))
            SetupSteps(onDownloadModel, onImportModel)
        }
    }
}

@Composable
private fun InstalledState(status: ModelStatus.Installed, onRemoveModel: () -> Unit) {
    val success = MaterialTheme.weatherColors.success
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(success.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(20.dp), tint = success) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Installed", style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatSize(status.sizeBytes)} in app storage; it runs entirely on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onRemoveModel, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
        Text("Remove model")
    }
}

@Composable
private fun DownloadingState(status: ModelStatus.Downloading, onCancelDownload: () -> Unit) {
    val total = status.totalBytes
    val paused = status.pausedReason
    val fraction = total?.let { (status.downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    val title = when {
        paused != null -> "Download paused"
        total == null -> "Starting download…"
        else -> "Downloading"
    }
    val progress = when {
        total != null -> "${formatSize(status.downloadedBytes)} of ${formatSize(total)}" +
            (fraction?.let { " · ${(it * 100).toInt()}%" } ?: "")
        else -> "Asking Hugging Face for the file"
    }
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    if (paused != null) {
        Text(paused, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(2.dp))
    }
    Text(progress, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    val bar = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
    when {
        // A paused download keeps its (static) progress on screen; the paused text above says why it isn't moving.
        fraction != null -> LinearProgressIndicator({ fraction }, bar)
        else -> LinearProgressIndicator(bar)
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "You can leave the app; the download continues in the background and shows in your notifications.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onCancelDownload) { Text("Cancel download") }
}

@Composable
private fun BusyState(title: String, detail: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
}

@Composable
private fun FailedBanner(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Setup didn't finish",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                "Check the steps below and try again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/**
 * The guided download. The token lives only in composition memory while the screen is open (deliberately not
 * rememberSaveable, so it never enters saved instance state); it's passed to [onDownloadModel] once and never
 * written anywhere.
 */
@Composable
private fun SetupSteps(onDownloadModel: (String) -> Unit, onImportModel: () -> Unit) {
    var token by remember { mutableStateOf("") }

    Step(1, "Accept the license", "Sign in to Hugging Face (free) and accept Google's terms on the model page.") {
        LinkButton("Open model page", GemmaModelSource.MODEL_PAGE)
    }
    Step(2, "Create a read token", "In your account settings, make an access token with read permission and copy it.") {
        LinkButton("Get a token", GemmaModelSource.TOKENS_PAGE)
    }
    Step(3, "Paste the token and download", "One-time download, $MODEL_SIZE_HINT, Wi-Fi recommended. The token is used once to start it and isn't stored.", last = true) {
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Hugging Face token") },
            placeholder = { Text("hf_…") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            { onDownloadModel(token); token = "" },
            enabled = token.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Download Gemma") }
    }
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Already have ${GemmaModelSource.FILE_NAME}?",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onImportModel) { Text("Import file…") }
    }
}

/**
 * Opens [url] in the browser; devices without one (or with it disabled) get the address inline instead of a
 * crash, since [androidx.compose.ui.platform.UriHandler.openUri] throws when no activity can handle it.
 */
@Composable
private fun LinkButton(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }
    OutlinedButton({ failed = runCatching { uriHandler.openUri(url) }.isFailure }) { Text(label) }
    if (failed) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Couldn't open a browser. On another device, visit ${url.removePrefix("https://")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * One numbered step: a badge and a connector line on the left, title/text/action on the right. The row is
 * sized to its content's intrinsic height so the connector can fill it (a weight inside the scrolling column
 * would get no height at all).
 */
@Composable
private fun Step(number: Int, title: String, text: String, last: Boolean = false, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            if (!last) {
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(bottom = if (last) 0.dp else 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            action()
        }
    }
}

private fun formatSize(bytes: Long): String =
    String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0))
