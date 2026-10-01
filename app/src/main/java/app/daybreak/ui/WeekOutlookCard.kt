package app.daybreak.ui

import androidx.compose.foundation.background
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.daybreak.domain.DayKind
import app.daybreak.domain.Forecast
import app.daybreak.domain.outlookMoment
import app.daybreak.domain.OutlookDay
import app.daybreak.domain.OutlookTier
import app.daybreak.domain.Term
import app.daybreak.domain.WeekOutlook
import app.daybreak.domain.keepUnitsTogether
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The moment "This week" is for at [forecast]'s place ([outlookMoment]): the later of the forecast's own time and the
 * clock, to the hour. [fixed] stands in for the clock (screenshot tests). Reads the minute clock through
 * derivedStateOf, so the caller only recomposes when the hour moves on.
 */
@Composable
fun rememberOutlookNow(forecast: Forecast, fixed: Instant? = null): LocalDateTime {
    if (fixed != null) return remember(forecast, fixed) { outlookMoment(forecast, fixed) }
    val clock = rememberMinuteClockState()
    val moment by remember(forecast, clock) { derivedStateOf { outlookMoment(forecast, clock.value) } }
    return moment
}

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
            .semantics(mergeDescendants = true) { contentDescription = "How the outlook works" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("How it works", style = style, color = muted, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        InfoGlyph(MaterialTheme.colorScheme.outline, style)
    }
}

/**
 * The outlook: the today line in titleMedium, the week lines under it, then a strip of the seven days, each a bar
 * rising from a shared baseline as tall as its score and coloured by its tier, a rain or snow glyph under wet days,
 * and "Best" under the best day. Each day opens its details. The lines are one node for TalkBack, and each day says
 * what it is ("Saturday, best day, great for being outside, dry").
 */
@Composable
fun WeekOutlookCard(outlook: WeekOutlook, today: LocalDate, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val anyBest = outlook.days.any { it.isBest }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        // The "Best" row ends in its own air; without it the strip needs a little more under it.
        Column(Modifier.padding(top = 16.dp, bottom = if (anyBest) 8.dp else 12.dp)) {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clearAndSetSemantics { contentDescription = outlook.spoken },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(keepUnitsTogether(outlook.today.text), style = MaterialTheme.typography.titleMedium)
                outlook.week.forEach {
                    Text(keepUnitsTogether(it.text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (outlook.days.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                DayStrip(outlook.days, today, onOpenDay, Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

/** Whether the strip's bars grow from the baseline when it first appears; screenshot tests turn it off. */
val LocalOutlookGrowth = staticCompositionLocalOf { true }

/** The tallest a day's bar can be; a bar rises from the baseline by the day's score. */
private val BarHeight = 52.dp
private val BarWidth = 14.dp
private val GlyphSize = 16.dp
private val ColumnShape = RoundedCornerShape(12.dp)

/** A stay-in day's bar is never shorter than this share of [BarHeight], so its colour still shows. */
private const val MIN_FILL = 0.18f

/**
 * One column per day. The names are "Today" and "Fri", "Sat"…; all three-letter when "Today" doesn't fit a column,
 * and initials when those don't either (a narrow phone at a large font). Same for "Best", which becomes a star. The
 * bars grow from the baseline once, when the strip first appears (screenshots show them grown).
 */
@Composable
private fun DayStrip(days: List<OutlookDay>, today: LocalDate, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val labelStyle = MaterialTheme.typography.labelMedium
    val bestStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Grown from the start in previews and screenshots, which only see the first frame.
    val animate = LocalOutlookGrowth.current && !LocalInspectionMode.current
    var grown by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { grown = true }
    val growth by animateFloatAsState(if (grown) 1f else 0f, tween(durationMillis = 300, easing = FastOutSlowInEasing), label = "bars")
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Columns get their share of the width, with a little air between names.
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
        // The bars stand on one hairline across the strip: under the column's padding, its name and the gap above the bar.
        val baseline = remember(labelStyle, density) {
            with(density) { (8.dp + 6.dp + BarHeight).toPx() } + measurer.measure("Today", labelStyle, density = density).size.height
        }
        val line = MaterialTheme.colorScheme.outlineVariant
        Row(Modifier.fillMaxWidth().drawBehind { drawRect(line, Offset(0f, baseline), Size(size.width, 1.dp.toPx())) }) {
            days.forEachIndexed { i, day ->
                DayColumn(
                    day, labels[i], isToday = day.date == today, showGlyphRow = anyWet, showBestRow = anyBest, bestAsWord = bestFits,
                    growth = { growth },
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
    growth: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val success = MaterialTheme.weatherColors.success
    Column(
        modifier
            .clip(ColumnShape)
            .clickable(onClickLabel = "Open details", role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp)
            .semantics { contentDescription = day.spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                // Today stands out by colour as well as weight, so it still does as an initial.
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium),
                color = when {
                    isToday -> MaterialTheme.colorScheme.primary
                    day.isBest -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(6.dp))
            ScoreBar(day.score, day.tier, growth)
            // The baseline, one hairline across the strip, is drawn by [DayStrip] under the bars.
            Spacer(Modifier.height(1.dp))
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

/**
 * A bar standing on the baseline, as tall as the score and in the tier's colour, rounded at the top; for a day with
 * no score (today, in the evening), a short dash on the baseline.
 */
@Composable
private fun ScoreBar(score: Int?, tier: OutlookTier?, growth: () -> Float) {
    Box(Modifier.size(BarWidth, BarHeight), contentAlignment = Alignment.BottomCenter) {
        if (score != null && tier != null) {
            val fraction = MIN_FILL + (1 - MIN_FILL) * score.coerceIn(0, 100) / 100f
            val color = tierColor(tier)
            val shape = RoundedCornerShape(topStart = BarWidth / 2, topEnd = BarWidth / 2)
            Box(
                Modifier.fillMaxWidth().height(BarHeight * fraction)
                    .graphicsLayer {
                        scaleY = growth()
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
                    .clip(shape).background(color),
            )
        } else {
            Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
    }
}

@Composable
internal fun tierColor(tier: OutlookTier): Color {
    val c = MaterialTheme.weatherColors
    return when (tier) {
        OutlookTier.GREAT -> c.outlookGreat
        OutlookTier.GOOD -> c.outlookGood
        OutlookTier.MEH, OutlookTier.STAY_IN -> c.outlookRest
    }
}
