package app.daybreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.daybreak.domain.DayKind
import app.daybreak.domain.OutlookDay
import app.daybreak.domain.OutlookTier
import app.daybreak.domain.Term
import app.daybreak.domain.WeekOutlook
import app.daybreak.domain.keepUnitsTogether
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * "This week" on the Weather page: the heading with "How it works", then [WeekOutlookCard]. The link opens the
 * explanation through [LocalExplain], so it's left out where explanations aren't available.
 */
@Composable
fun WeekOutlookSection(outlook: WeekOutlook, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    SectionHeading("This week") { HowItWorks() }
    Spacer(Modifier.height(4.dp))
    WeekOutlookCard(outlook, today, onOpenDay, Modifier.padding(horizontal = PageMargin))
}

/** "How it works ⓘ", a 48dp target that opens the explanation of the outlook. */
@Composable
private fun HowItWorks() {
    val explain = LocalExplain.current ?: return
    val style = MaterialTheme.typography.labelMedium
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Explain", role = Role.Button) { explain(Term.WEEK) }
            .padding(horizontal = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "How This week works" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("How it works", style = style, color = muted, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        InfoGlyph(MaterialTheme.colorScheme.outline, style)
    }
}

/**
 * The outlook: the today line in titleMedium, the week lines under it, then a strip of the seven days, each a bar
 * as tall as its score and coloured by its tier, a rain or snow glyph under wet days, and the best day outlined and
 * labelled "Best". Each day opens its details. The lines are one node for TalkBack, and each day says what it is
 * ("Saturday, best day, great for being outside, dry").
 */
@Composable
fun WeekOutlookCard(outlook: WeekOutlook, today: LocalDate, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
            val spoken = (listOf(outlook.today) + outlook.week).joinToString(". ") { it.spoken } + "."
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clearAndSetSemantics { contentDescription = spoken },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(keepUnitsTogether(outlook.today.text), style = MaterialTheme.typography.titleMedium)
                outlook.week.forEach {
                    Text(keepUnitsTogether(it.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (outlook.days.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                DayStrip(outlook.days, today, onOpenDay, Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

/** Height of a day's bar track; a bar fills it by the day's score. */
private val BarHeight = 52.dp
private val BarWidth = 14.dp
private val GlyphSize = 16.dp
private val ColumnShape = RoundedCornerShape(12.dp)

/**
 * One column per day. The names are "Today" and "Fri", "Sat"…; all three-letter when "Today" doesn't fit a column,
 * and initials when those don't either (a narrow phone at a large font). Same for "Best", which becomes a star.
 */
@Composable
private fun DayStrip(days: List<OutlookDay>, today: LocalDate, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val labelStyle = MaterialTheme.typography.labelMedium
    val bestStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Columns get their share of the width; names need a little air inside the best day's outline.
        val room = maxWidth / days.size - 4.dp
        val labels = remember(days, today, room, labelStyle, density) {
            val bold = labelStyle.copy(fontWeight = FontWeight.Bold)
            fun fits(names: List<String>) = measurer.widest(names, bold, density) <= room
            fun pattern(p: String) = days.map { it.date.format(DateTimeFormatter.ofPattern(p, Locale.US)) }
            val short = pattern("EEE")
            val withToday = days.mapIndexed { i, d -> if (d.date == today) "Today" else short[i] }
            when {
                fits(withToday) -> withToday
                fits(short) -> short
                else -> pattern("EEEEE")
            }
        }
        val bestFits = remember(room, bestStyle, density) { measurer.widest(listOf("Best"), bestStyle, density) <= room }
        val anyBest = days.any { it.isBest }
        val anyWet = days.any { it.rain == DayKind.WET }
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, day ->
                DayColumn(
                    day, labels[i], isToday = day.date == today, showGlyphRow = anyWet, showBestRow = anyBest, bestAsWord = bestFits,
                    onClick = { onOpenDay(day.date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: OutlookDay,
    label: String,
    isToday: Boolean,
    showGlyphRow: Boolean,
    showBestRow: Boolean,
    bestAsWord: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val success = MaterialTheme.weatherColors.success
    val highlight = if (day.isBest) {
        Modifier.border(1.5.dp, success, ColumnShape).background(success.copy(alpha = 0.08f), ColumnShape)
    } else Modifier
    Column(
        modifier
            .padding(horizontal = 1.dp)
            .clip(ColumnShape)
            .then(highlight)
            .clickable(onClickLabel = "Open details", role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = day.spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium),
                color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(6.dp))
            ScoreBar(day.score, day.tier)
            // The glyph row is there for every day once any day is wet, so the bars line up.
            if (showGlyphRow) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(GlyphSize), contentAlignment = Alignment.Center) {
                    if (day.rain == DayKind.WET) {
                        WeatherIcon(if (day.snow) 73 else 63, night = false, cardIconPalette(), size = GlyphSize, contentDescription = null)
                    }
                }
            }
            if (showBestRow) {
                Spacer(Modifier.height(2.dp))
                val style = MaterialTheme.typography.labelSmall
                val density = LocalDensity.current
                Box(Modifier.height(with(density) { style.lineHeight.toDp() }), contentAlignment = Alignment.Center) {
                    when {
                        !day.isBest -> Unit
                        bestAsWord -> Text("Best", style = style, color = success, maxLines = 1, softWrap = false)
                        else -> Icon(Icons.Filled.Star, contentDescription = null, Modifier.size(with(density) { style.fontSize.toDp() }), tint = success)
                    }
                }
            }
        }
    }
}

/** A rounded track filled from the bottom by the score, in the tier's colour; an empty track for a day with none. */
@Composable
private fun ScoreBar(score: Int?, tier: OutlookTier?) {
    val track = if (MaterialTheme.isDark) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = RoundedCornerShape(BarWidth / 2)
    Box(Modifier.size(BarWidth, BarHeight).clip(shape).background(track), contentAlignment = Alignment.BottomCenter) {
        if (score != null && tier != null) {
            // Never quite empty, so a stay-in day still shows its colour.
            val fraction = 0.14f + 0.86f * score.coerceIn(0, 100) / 100f
            Box(Modifier.fillMaxWidth().height(BarHeight * fraction).clip(shape).background(tierColor(tier)))
        }
    }
}

@Composable
internal fun tierColor(tier: OutlookTier): Color {
    val c = MaterialTheme.weatherColors
    return when (tier) {
        OutlookTier.GREAT -> c.outlookGreat
        OutlookTier.GOOD -> c.outlookGood
        OutlookTier.MEH -> c.outlookMeh
        OutlookTier.STAY_IN -> c.outlookStayIn
    }
}
