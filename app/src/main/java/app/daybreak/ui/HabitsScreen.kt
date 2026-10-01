package app.daybreak.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.daybreak.domain.Badge
import app.daybreak.domain.HABIT_COUNT_MAX
import app.daybreak.domain.HABIT_PRESETS
import app.daybreak.domain.HABIT_TITLE_MAX
import app.daybreak.domain.Habit
import app.daybreak.domain.HabitColor
import app.daybreak.domain.HabitDraft
import app.daybreak.domain.HabitKind
import app.daybreak.domain.HabitPeriod
import app.daybreak.domain.HabitStats
import app.daybreak.domain.HabitsData
import app.daybreak.domain.HabitsSummary
import app.daybreak.domain.Level
import app.daybreak.domain.goalLabel
import app.daybreak.domain.heatMap
import app.daybreak.domain.isDailyAllowance
import app.daybreak.domain.progressLine
import app.daybreak.domain.sinceLine
import app.daybreak.domain.streakLine
import app.daybreak.domain.summarize
import app.daybreak.domain.toDraft
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale

// --- Colours ------------------------------------------------------------------------------------------------------

/** Each palette colour's light-theme shade (white text on it is at least 4.5:1) and its dark-theme one. */
private val HabitShades = mapOf(
    HabitColor.BLUE to (Color(0xFF1E6FC0) to Color(0xFF8AB4F8)),
    HabitColor.TEAL to (Color(0xFF00796B) to Color(0xFF4DD0C4)),
    HabitColor.GREEN to (Color(0xFF2E7D32) to Color(0xFF6CCB8F)),
    HabitColor.OLIVE to (Color(0xFF5F6F12) to Color(0xFFC5D86D)),
    HabitColor.AMBER to (Color(0xFFB45309) to Color(0xFFFFB74D)),
    HabitColor.CORAL to (Color(0xFFC63D2B) to Color(0xFFFF8A75)),
    HabitColor.PINK to (Color(0xFFC2185B) to Color(0xFFF48FB1)),
    HabitColor.PURPLE to (Color(0xFF7346B8) to Color(0xFFC3A6F2)),
)

@Composable
fun habitColor(c: HabitColor): Color = HabitShades.getValue(c).let { if (MaterialTheme.isDark) it.second else it.first }

/** Text and glyphs drawn on a habit colour. */
@Composable
private fun onHabitColor(): Color = if (MaterialTheme.isDark) Color(0xFF0F1522) else Color.White

/** "Today" for the habits: the phone's own date, following the clock past midnight. */
@Composable
fun rememberToday(): LocalDate = rememberMinuteClock().atZone(ZoneId.systemDefault()).toLocalDate()

// --- The tab ------------------------------------------------------------------------------------------------------

/**
 * Habits: your level and points at the top, then each habit with this period's progress, a +1 (long-press or −
 * to undo), its streak or clean run, and the last 12 weeks. Edit reorders and deletes; tapping a habit edits it.
 * With none yet, a short explanation and three one-tap presets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    data: HabitsData,
    celebration: Celebration?,
    onLog: (String) -> Unit,
    onUndo: (String) -> Unit,
    onAdd: (HabitDraft) -> Unit,
    onUpdate: (String, HabitDraft) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onCelebrationShown: (Long) -> Unit,
    /** Fixed in screenshot tests; otherwise the phone's date, following midnight. */
    today: LocalDate? = null,
    weekFields: WeekFields = WeekFields.of(Locale.getDefault()),
    initiallyEditing: Boolean = false,
) {
    val day = today ?: rememberToday()
    val summary = remember(data, day, weekFields) { summarize(data, day, weekFields) }
    var editing by rememberSaveable { mutableStateOf(initiallyEditing) }
    // The habit open in the editor: "" for a new one, null when it's closed.
    var editorFor by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(data.habits.isEmpty()) { if (data.habits.isEmpty()) editing = false }
    CelebrationTimer(celebration, onCelebrationShown)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Habits") },
                actions = {
                    if (data.habits.isNotEmpty()) TextButton({ editing = !editing }) { Text(if (editing) "Done" else "Edit") }
                    IconButton({ editorFor = "" }) { Icon(Icons.Default.Add, contentDescription = "Add a habit") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = PageMargin),
        ) {
            Spacer(Modifier.height(8.dp))
            if (data.habits.isEmpty()) {
                EmptyHabits(onPreset = onAdd, onNew = { editorFor = "" })
            } else {
                LevelCard(summary)
                summary.stats.forEachIndexed { i, s ->
                    Spacer(Modifier.height(12.dp))
                    HabitCard(
                        s, day, weekFields, editing,
                        celebration = celebration?.takeIf { it.habitId == s.habit.id }?.message,
                        onLog = { onLog(s.habit.id) },
                        onUndo = { onUndo(s.habit.id) },
                        onEdit = { editorFor = s.habit.id },
                        onUp = if (i > 0) ({ onMove(i, i - 1) }) else null,
                        onDown = if (i < summary.stats.lastIndex) ({ onMove(i, i + 1) }) else null,
                        onRemove = { confirmDelete = s.habit.id },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editorFor?.let { id ->
        val habit = data.habits.firstOrNull { it.id == id }
        HabitEditorDialog(
            initial = habit?.toDraft(),
            onDismiss = { editorFor = null },
            onSave = { draft ->
                if (habit == null) onAdd(draft) else onUpdate(habit.id, draft)
                editorFor = null
            },
        )
    }
    confirmDelete?.let { id ->
        val habit = data.habits.firstOrNull { it.id == id }
        if (habit == null) {
            confirmDelete = null
        } else {
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("Delete “${habit.title}”?") },
                text = { Text("Its log and streaks go with it. The points and badges it earned stay.") },
                confirmButton = {
                    TextButton(
                        { onRemove(id); confirmDelete = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } },
            )
        }
    }
}

/** Lets a celebration be seen for a few seconds, then clears it. */
@Composable
fun CelebrationTimer(celebration: Celebration?, onShown: (Long) -> Unit) {
    LaunchedEffect(celebration?.id) {
        val c = celebration ?: return@LaunchedEffect
        delay(3_500)
        onShown(c.id)
    }
}

/** "Level 4", the points and how many to the next level on an amber bar, and the badges earned. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelCard(summary: HabitsSummary) {
    val level = summary.level
    val amber = MaterialTheme.weatherColors.sun
    val badges = Badge.entries.filter { it in summary.badges }
    val spoken = "Level ${level.number}, ${level.points} points, ${level.toGo} to go to level ${level.number + 1}." +
        if (badges.isEmpty()) "" else " Badges: ${badges.joinToString(", ") { it.title }}."
    Card(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(amber.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Star, contentDescription = null, Modifier.size(20.dp), tint = amber)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Level ${level.number}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${level.points} pts · ${level.toGo} to Level\u00A0${level.number + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            LevelBar(level, amber)
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { BadgePill(it, amber) }
                }
            }
        }
    }
}

@Composable
private fun LevelBar(level: Level, color: Color) {
    val progress by animateFloatAsState(level.progress, label = "level")
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        color = color,
        trackColor = color.copy(alpha = 0.18f),
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}

@Composable
private fun BadgePill(badge: Badge, amber: Color) {
    Row(
        Modifier.clip(CircleShape).background(amber.copy(alpha = 0.14f)).padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, contentDescription = null, Modifier.size(14.dp), tint = amber)
        Spacer(Modifier.width(4.dp))
        Text(badge.title, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * One habit: its ring (or clean-days disc) and lines on top, the 12-week map and the log buttons under them. The
 * words and the map are one node for TalkBack; tapping them edits the habit.
 */
@Composable
private fun HabitCard(
    s: HabitStats,
    today: LocalDate,
    weekFields: WeekFields,
    editing: Boolean,
    celebration: String?,
    onLog: () -> Unit,
    onUndo: () -> Unit,
    onEdit: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    val h = s.habit
    val color = habitColor(h.color)
    val build = h.kind == HabitKind.BUILD
    val cells = remember(h, today, weekFields) { heatMap(h, today, weekFields) }
    // A daily allowance is about today's count; "days since" only means something when none (or few a week) are allowed.
    val counted = build || h.isDailyAllowance
    val first = if (counted) progressLine(s) else sinceLine(s)
    val second = if (counted) streakLine(s) else progressLine(s)
    val third = if (counted) null else streakLine(s)
    val spoken = listOfNotNull(h.title, goalLabel(h.kind, h.period, h.target), first, second, third, heatSummary(h, cells), celebration)
        .joinToString(". ")
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Edit", onClick = onEdit).clearAndSetSemantics {
                contentDescription = spoken
                onClick("Edit") { onEdit(); true }
                if (editing) {
                    customActions = listOfNotNull(
                        onUp?.let { f -> CustomAccessibilityAction("Move up") { f(); true } },
                        onDown?.let { f -> CustomAccessibilityAction("Move down") { f(); true } },
                        CustomAccessibilityAction("Delete") { onRemove(); true },
                    )
                }
            }.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        ) {
            Row {
                if (counted) {
                    // An allowance's ring fills as it's used, without a check: reaching it isn't a goal.
                    ProgressRing(s.count, h.target, color, build && s.onTrack, 48.dp)
                } else {
                    CleanDaysDisc(s.cleanDays, color)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    // Met goals in the success green; an avoid habit's words stay neutral whatever happened.
                    Text(
                        first,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (build && s.onTrack) MaterialTheme.weatherColors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (third != null) Text(third, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CelebrationLine(celebration)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            HeatMap(cells, color, Modifier.padding(bottom = 4.dp))
            Spacer(Modifier.weight(1f))
            if (editing) {
                IconButton({ onUp?.invoke() }, enabled = onUp != null) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${h.title} up")
                }
                IconButton({ onDown?.invoke() }, enabled = onDown != null) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${h.title} down")
                }
                IconButton(onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${h.title}", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                if (s.count > 0) UndoButton(h.title, onUndo)
                Spacer(Modifier.width(8.dp))
                LogButton(h.title, color, onLog, onUndo.takeIf { s.count > 0 })
            }
        }
    }
}

/** The celebration, in place under a habit's lines: grows in, then fades. */
@Composable
fun CelebrationLine(message: String?, modifier: Modifier = Modifier) {
    // Keep the last message while it fades out.
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    AnimatedVisibility(message != null, modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        val style = MaterialTheme.typography.labelLarge
        Row(Modifier.padding(top = 4.dp)) {
            // Centred on the first line, however the words wrap.
            Box(Modifier.height(with(LocalDensity.current) { style.lineHeight.toDp() }), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Star, contentDescription = null, Modifier.size(14.dp), tint = MaterialTheme.weatherColors.sun)
            }
            Spacer(Modifier.width(4.dp))
            Text(last.orEmpty(), style = style, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/** "Goal met on 40 of the last 84 days", "9 logged in the last 12 weeks", "2 in the last 12 weeks". */
private fun heatSummary(h: Habit, cells: List<List<app.daybreak.domain.HeatCell?>>): String {
    val days = cells.flatten().filterNotNull()
    return when {
        h.kind == HabitKind.AVOID -> "${days.sumOf { it.count }} in the last 12 weeks"
        h.period == HabitPeriod.DAY -> "Goal met on ${days.count { it.count >= h.target }} of the last ${days.size} days"
        else -> "${days.sumOf { it.count }} logged in the last 12 weeks"
    }
}

/**
 * Progress to a goal as a ring in the habit's colour, with "5/8" inside; once met it fills and a check pops in.
 * The numbers inside keep their size at large fonts (the same words are in the lines beside it).
 */
@Composable
fun ProgressRing(count: Int, target: Int, color: Color, met: Boolean, size: Dp, modifier: Modifier = Modifier, showCount: Boolean = true) {
    val fraction by animateFloatAsState((count.toFloat() / target.coerceAtLeast(1)).coerceAtMost(1f), label = "ring")
    val check by animateFloatAsState(if (met) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "check")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = (size.toPx() / 12f).coerceAtLeast(3.dp.toPx())
            val inset = w / 2
            val arcSize = Size(this.size.width - w, this.size.height - w)
            drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
            if (check > 0f) drawCircle(color, (this.size.minDimension / 2 - w * 1.5f) * check.coerceAtMost(1f))
        }
        if (check > 0.01f) {
            Icon(Icons.Default.Check, contentDescription = null, Modifier.size(size * 0.5f).scale(check), tint = onHabitColor())
        } else if (showCount) {
            val px = with(LocalDensity.current) { (size.value * if (target >= 10 || count >= 10) 0.24f else 0.28f).dp.toSp() }
            Text(
                "$count/$target",
                style = TextStyle(fontSize = px, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** An avoid habit's clean run as a number of days on a disc of its colour. */
@Composable
private fun CleanDaysDisc(days: Int, color: Color) {
    val density = LocalDensity.current
    Box(Modifier.size(48.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$days",
                style = TextStyle(fontSize = with(density) { (if (days >= 100) 14.dp else 17.dp).toSp() }, fontWeight = FontWeight.SemiBold, lineHeight = with(density) { 18.dp.toSp() }),
                color = color,
                maxLines = 1,
            )
            Text(
                if (days == 1) "day" else "days",
                style = TextStyle(fontSize = with(density) { 10.dp.toSp() }, lineHeight = with(density) { 11.dp.toSp() }),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The +1: a disc in the habit's colour. Long-press takes the last one back. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogButton(title: String, color: Color, onLog: () -> Unit, onUndo: (() -> Unit)?, size: Dp = 48.dp) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier.size(size).clip(CircleShape).background(color)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Log one",
                onLongClickLabel = if (onUndo != null) "Undo" else null,
                onLongClick = onUndo?.let { f -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); f() } },
                onClick = onLog,
            )
            .clearAndSetSemantics { contentDescription = "Add one to $title" },
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        Text(
            "+1",
            style = TextStyle(fontSize = with(density) { 17.dp.toSp() }, fontWeight = FontWeight.Bold),
            color = onHabitColor(),
        )
    }
}

@Composable
private fun UndoButton(title: String, onUndo: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape)
            .clickable(role = Role.Button, onClick = onUndo)
            .clearAndSetSemantics { contentDescription = "Take one back from $title" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(14.dp, 2.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant))
    }
}

/**
 * The last 12 weeks like GitHub's contribution map: a column per week, oldest on the left, each from the
 * locale's first weekday; today is the last filled square.
 */
@Composable
private fun HeatMap(cells: List<List<app.daybreak.domain.HeatCell?>>, color: Color, modifier: Modifier = Modifier) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val cell = 9.dp
    val gap = 2.dp
    Canvas(modifier.size(cell * cells.size + gap * (cells.size - 1), cell * 7 + gap * 6)) {
        val c = cell.toPx()
        val g = gap.toPx()
        val r = CornerRadius(2.dp.toPx())
        cells.forEachIndexed { w, week ->
            week.forEachIndexed { d, day ->
                if (day == null) return@forEachIndexed
                val fill = if (day.strength <= 0f) empty else lerp(empty, color, 0.3f + 0.7f * day.strength)
                drawRoundRect(fill, Offset(w * (c + g), d * (c + g)), Size(c, c), r)
            }
        }
    }
}

// --- Empty state --------------------------------------------------------------------------------------------------

@Composable
private fun EmptyHabits(onPreset: (HabitDraft) -> Unit, onNew: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Track a habit", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Something to do more of, like drinking water, or less of, like ordering takeout. Tap +1 when it " +
                    "happens; streaks, points and a 12-week map follow. Kept on this phone, not backed up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text("Start with one", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HABIT_PRESETS.forEach { PresetRow(it) { onPreset(it) } }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onNew) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Make your own")
            }
        }
    }
}

/** A preset as one tap: its colour, name and goal, and a +. */
@Composable
private fun PresetRow(draft: HabitDraft, onClick: () -> Unit) {
    val color = habitColor(draft.color)
    val goal = goalLabel(draft.kind, draft.period, draft.target)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(color.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClickLabel = "Add", onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Add ${draft.title}, ${goal.lowercase()}"
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(draft.title, style = MaterialTheme.typography.titleSmall)
            Text(
                if (draft.kind == HabitKind.AVOID) "Avoid · $goal" else goal,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Default.Add, contentDescription = null, tint = color)
    }
}

// --- Add / edit ---------------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitEditorDialog(initial: HabitDraft?, onDismiss: () -> Unit, onSave: (HabitDraft) -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HabitEditor(initial, onDismiss, onSave, Modifier.padding(horizontal = 24.dp).imePadding())
    }
}

/**
 * The add/edit form: a name, build or avoid, the count per day or week, and a colour. [initial] is null for a
 * new habit.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditor(initial: HabitDraft?, onDismiss: () -> Unit, onSave: (HabitDraft) -> Unit, modifier: Modifier = Modifier) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: HabitKind.BUILD) }
    var period by rememberSaveable { mutableStateOf(initial?.period ?: HabitPeriod.DAY) }
    var target by rememberSaveable { mutableStateOf(initial?.target ?: 1) }
    var color by rememberSaveable { mutableStateOf(initial?.color ?: HabitColor.BLUE) }
    val min = if (kind == HabitKind.BUILD) 1 else 0
    val canSave = title.isNotBlank()
    val save = { if (canSave) onSave(HabitDraft(title.trim().take(HABIT_TITLE_MAX), color, kind, period, target.coerceIn(min, HABIT_COUNT_MAX))) }

    Surface(
        modifier.widthIn(max = 420.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(
                if (initial == null) "New habit" else "Edit habit",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(HABIT_TITLE_MAX) },
                label = { Text("Name") },
                placeholder = { Text(if (kind == HabitKind.BUILD) "Drink water" else "No takeout") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(HabitKind.BUILD to "Build", HabitKind.AVOID to "Avoid").forEachIndexed { i, (k, label) ->
                    SegmentedButton(
                        selected = kind == k,
                        onClick = {
                            kind = k
                            // A new avoid habit starts at none; a build one needs at least one.
                            if (k == HabitKind.AVOID && initial == null && target == 1) target = 0
                            if (k == HabitKind.BUILD && target < 1) target = 1
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (kind == HabitKind.BUILD) "Something to do more of, with a goal to reach." else "Something to cut down on, with an allowance to stay within.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(if (kind == HabitKind.BUILD) "Goal" else "Allowance", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            // Wraps at large fonts, each part kept whole and centred on the stepper's height.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.height(40.dp), contentAlignment = Alignment.CenterStart) {
                    Text(if (kind == HabitKind.BUILD) "At least" else "At most", style = MaterialTheme.typography.bodyLarge)
                }
                Stepper(target, min, HABIT_COUNT_MAX) { target = it }
                Box(Modifier.height(40.dp), contentAlignment = Alignment.CenterStart) {
                    Text(if (period == HabitPeriod.DAY) "a day" else "a week", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(HabitPeriod.DAY to "Daily", HabitPeriod.WEEK to "Weekly").forEachIndexed { i, (p, label) ->
                    SegmentedButton(selected = period == p, onClick = { period = p }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Colour", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HabitColor.entries.forEach { c ->
                    val selected = c == color
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(habitColor(c))
                            .selectable(selected, role = Role.RadioButton) { color = c }
                            .semantics { contentDescription = c.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Default.Check, contentDescription = null, Modifier.size(20.dp), tint = onHabitColor())
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss) { Text("Cancel") }
                TextButton(save, enabled = canSave) { Text(if (initial == null) "Add" else "Save") }
            }
        }
    }
}

/** − n +, for the count; TalkBack hears "8, a day" with adjust actions. */
@Composable
private fun Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.clip(CircleShape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton({ onChange(value - 1) }, enabled = value > min, modifier = Modifier.size(40.dp).semantics { contentDescription = "One fewer" }) {
            Box(
                Modifier.size(12.dp, 2.dp).clip(CircleShape)
                    .background(if (value > min) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)),
            )
        }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp).semantics { stateDescription = "$value" },
        )
        IconButton({ onChange(value + 1) }, enabled = value < max, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Add, contentDescription = "One more", Modifier.size(18.dp))
        }
    }
}
