package app.daybreak.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.daybreak.domain.HabitKind
import app.daybreak.domain.HabitPeriod
import app.daybreak.domain.HabitStats
import app.daybreak.domain.HabitsSummary
import app.daybreak.domain.homeAvoidLine
import app.daybreak.domain.progressLine

/**
 * "Today's habits" on Home: each build habit as a chip that logs one per tap (long-press takes it back), with its
 * ring filling as it goes, then each avoid habit's clean run ("4 days without takeout"). Nothing to log for an
 * avoid habit here: a slip is logged on the Habits tab, on purpose.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HabitsHomeCard(
    summary: HabitsSummary,
    celebration: Celebration?,
    onLog: (String) -> Unit,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val build = summary.stats.filter { it.habit.kind == HabitKind.BUILD }
    val avoid = summary.stats.filter { it.habit.kind == HabitKind.AVOID }
    val cheer = celebration?.takeIf { c -> build.any { it.habit.id == c.habitId } }?.let { c ->
        "${build.first { it.habit.id == c.habitId }.habit.title}: ${c.message}"
    }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp)) {
            if (build.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    build.forEach { HabitChip(it, { onLog(it.habit.id) }, { onUndo(it.habit.id) }) }
                }
                CelebrationLine(cheer, Modifier.padding(start = 4.dp))
            }
            if (build.isNotEmpty() && avoid.isNotEmpty()) Spacer(Modifier.height(12.dp))
            avoid.forEachIndexed { i, s ->
                if (i > 0) Spacer(Modifier.height(6.dp))
                val style = MaterialTheme.typography.bodyMedium
                Row {
                    // Centred on the first line, however the words wrap.
                    Box(Modifier.height(with(LocalDensity.current) { style.lineHeight.toDp() }), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(habitColor(s.habit.color)))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(homeAvoidLine(s), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A build habit as a tap target: its ring, its name over "5/8 today" (or "1/3 this week"), on a tint of its colour. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitChip(s: HabitStats, onLog: () -> Unit, onUndo: () -> Unit) {
    val h = s.habit
    val color = habitColor(h.color)
    val haptics = LocalHapticFeedback.current
    val count = "${s.count}/${h.target} " + if (h.period == HabitPeriod.WEEK) "this week" else "today"
    Row(
        Modifier.heightIn(min = 44.dp).clip(CircleShape).background(color.copy(alpha = 0.12f))
            .combinedClickable(
                onLongClick = if (s.count > 0) ({ haptics.performHapticFeedback(HapticFeedbackType.LongPress); onUndo() }) else null,
                onClick = onLog,
            )
            .clearAndSetSemantics {
                contentDescription = "${h.title}, ${progressLine(s)}"
                role = Role.Button
                onClick("Add one") { onLog(); true }
                if (s.count > 0) customActions = listOf(CustomAccessibilityAction("Take one back") { onUndo(); true })
            }
            .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(s.count, h.target, color, s.onTrack, 32.dp, showCount = false)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(h.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
