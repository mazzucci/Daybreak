package app.daybreak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.daybreak.domain.Countdown
import app.daybreak.domain.DaySummary
import app.daybreak.domain.Forecast
import app.daybreak.domain.TempUnit
import app.daybreak.domain.describeWeatherCode
import app.daybreak.domain.formatBothUnits
import app.daybreak.domain.formatCountdown
import app.daybreak.domain.formatDegrees
import app.daybreak.domain.formatTimeOfDay
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Holidays, long weekends and the next season, soonest first, with the countdown on the right. When the date is
 * inside the forecast, the row also shows that day's weather, so "Monday off" comes with "and it'll be sunny".
 */
@Composable
fun ComingUpCard(
    items: List<Countdown>,
    /** For each date's weather when it's in range; null while the forecast isn't loaded (dates still count down). */
    forecast: Forecast?,
    unit: TempUnit,
    modifier: Modifier = Modifier,
    today: java.time.LocalDate = forecast?.current?.time?.toLocalDate() ?: java.time.LocalDate.now(),
    /** Opens the editor for one of your dates, by its id; null where dates can't be edited (their rows aren't tappable). */
    onEditDate: ((String) -> Unit)? = null,
) {
    val palette = cardIconPalette()
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val day = forecast?.days?.firstOrNull { it.date == item.date }
                // A range shows both ends ("Oct 12–16"); a single day keeps its weekday. One of your dates with a
                // time has it after the day ("Thu, Oct 1 · 2:00 PM").
                val time = item.time?.let { formatTimeOfDay(it) }
                val date = listOfNotNull(
                    item.endDate?.let { formatRange(item.date, it, spoken = false) }
                        ?: item.date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)),
                    time,
                ).joinToString(" · ")
                val countdown = formatCountdown(item.daysFrom(today))
                // TalkBack gets the full weekday and month (the short form's comma would split the list) and the
                // high in both units, like the 7-day rows.
                val spokenDate = (
                    item.endDate?.let { formatRange(item.date, it, spoken = true) }
                        ?: item.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))
                    ) + (time?.let { " at $it" } ?: "")
                val spokenWeather = day?.let { "${describeWeatherCode(it.code)}, high ${formatBothUnits(it.highC, unit)}" }
                CountdownRow(
                    item, day, unit, palette,
                    date = date,
                    countdown = countdown,
                    Modifier
                        .fillMaxWidth()
                        .then(
                            item.dateId?.let { id -> onEditDate?.let { edit -> Modifier.clickable(onClickLabel = "Edit") { edit(id) } } }
                                ?: Modifier,
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clearAndSetSemantics {
                            contentDescription = listOfNotNull(item.title, spokenDate, item.note, countdown, spokenWeather).joinToString(", ")
                        },
                )
            }
        }
    }
}

/**
 * One row: glyph, then the name over "date · note", with the countdown (and that day's high, when the forecast
 * has it) on the right. The name leads, since it's what you're counting to; the countdown is the accent.
 *
 * On a narrow screen or at a large font size the countdown would squeeze the name into a sliver, so when it
 * needs more than its fair share of the row it moves under the details instead, as one left-aligned line.
 */
@Composable
private fun CountdownRow(
    item: Countdown,
    day: DaySummary?,
    unit: TempUnit,
    palette: IconPalette,
    date: String,
    countdown: String,
    modifier: Modifier = Modifier,
) {
    val titleStyle = MaterialTheme.typography.titleSmall
    val detailStyle = MaterialTheme.typography.bodySmall
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val high = day?.let { formatDegrees(it.highC, unit) }
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val trailingWidth = with(LocalDensity.current) {
            maxOf(
                measurer.measure(countdown, titleStyle, maxLines = 1, softWrap = false).size.width.toDp(),
                high?.let { measurer.measure(it, detailStyle, maxLines = 1, softWrap = false).size.width.toDp() + WeatherIconSize + 4.dp } ?: 0.dp,
            )
        }
        // The text column must keep at least 1.6x the countdown's width, or the countdown goes underneath.
        val textWidth = maxWidth - GlyphSize - 12.dp - 12.dp - trailingWidth
        val stacked = trailingWidth > textWidth * 0.6f
        Row(verticalAlignment = Alignment.CenterVertically) {
            KindGlyph(item.kind)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = titleStyle)
                // Non-breaking spaces around every separator (the note can carry one of its own), so a wrapped
                // line never ends or starts with a dot.
                Text(
                    listOfNotNull(date, item.note).joinToString(" · ").replace(" · ", " · "),
                    style = detailStyle,
                    color = secondary,
                )
                if (stacked) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountdownText(countdown, titleStyle)
                        if (day != null && high != null) {
                            Spacer(Modifier.width(10.dp))
                            DayWeather(day, high, palette, detailStyle, secondary)
                        }
                    }
                }
            }
            if (!stacked) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    CountdownText(countdown, titleStyle)
                    if (day != null && high != null) DayWeather(day, high, palette, detailStyle, secondary)
                }
            }
        }
    }
}

@Composable
private fun CountdownText(countdown: String, style: TextStyle) {
    Text(countdown, style = style, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
}

/** That day's forecast, as quiet as the date line: the promise is "and it'll be sunny". */
@Composable
private fun DayWeather(day: DaySummary, high: String, palette: IconPalette, style: TextStyle, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(day.code, night = false, palette, size = WeatherIconSize, contentDescription = null)
        Spacer(Modifier.width(4.dp))
        Text(high, style = style, color = color, maxLines = 1, softWrap = false)
    }
}

/** A calendar page for holidays and long weekends, a leaf for seasons, a suitcase for days off, a star for your own dates. */
@Composable
private fun KindGlyph(kind: Countdown.Kind) {
    val color = when (kind) {
        Countdown.Kind.SEASON -> MaterialTheme.weatherColors.success
        Countdown.Kind.DAY_OFF, Countdown.Kind.PERSONAL -> MaterialTheme.weatherColors.sun
        else -> MaterialTheme.colorScheme.primary
    }
    Box(Modifier.size(GlyphSize).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(18.dp)) {
            val s = size.minDimension
            val width = s * 0.1f
            if (kind == Countdown.Kind.SEASON) {
                // A leaf on the diagonal: a pointed blade (two curves bulging away from the midrib), the midrib
                // running most of the way to the apex, and a short stem past the base.
                val base = Offset(s * 0.2f, s * 0.8f)
                val apex = Offset(s * 0.86f, s * 0.14f)
                val blade = Path().apply {
                    moveTo(base.x, base.y)
                    quadraticTo(s * 0.16f, s * 0.1f, apex.x, apex.y)
                    quadraticTo(s * 0.9f, s * 0.84f, base.x, base.y)
                    close()
                }
                drawPath(blade, color, style = Stroke(width, join = StrokeJoin.Miter))
                drawLine(color, base, Offset(s * 0.73f, s * 0.27f), width, StrokeCap.Round)
                drawLine(color, base, Offset(s * 0.08f, s * 0.92f), width, StrokeCap.Round)
            } else if (kind == Countdown.Kind.PERSONAL) {
                // A five-pointed star, outlined like the other glyphs.
                val star = Path().apply {
                    for (i in 0 until 10) {
                        val r = if (i % 2 == 0) s * 0.44f else s * 0.19f
                        val a = Math.toRadians(-90.0 + i * 36.0)
                        val p = Offset(s * 0.5f + r * kotlin.math.cos(a).toFloat(), s * 0.53f + r * kotlin.math.sin(a).toFloat())
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }
                drawPath(star, color, style = Stroke(width, join = StrokeJoin.Round))
            } else if (kind == Countdown.Kind.DAY_OFF) {
                // A suitcase: the case, its handle on top, and a band across the middle.
                val stroke = Stroke(width)
                drawRoundRect(
                    color, Offset(s * 0.1f, s * 0.3f), Size(s * 0.8f, s * 0.58f),
                    androidx.compose.ui.geometry.CornerRadius(s * 0.1f), style = stroke,
                )
                drawRoundRect(
                    color, Offset(s * 0.35f, s * 0.12f), Size(s * 0.3f, s * 0.18f),
                    androidx.compose.ui.geometry.CornerRadius(s * 0.06f), style = stroke,
                )
                drawLine(color, Offset(s * 0.1f, s * 0.56f), Offset(s * 0.9f, s * 0.56f), width)
            } else {
                val stroke = Stroke(width)
                drawRoundRect(
                    color, Offset(s * 0.1f, s * 0.18f), Size(s * 0.8f, s * 0.72f),
                    androidx.compose.ui.geometry.CornerRadius(s * 0.12f), style = stroke,
                )
                drawLine(color, Offset(s * 0.1f, s * 0.4f), Offset(s * 0.9f, s * 0.4f), width)
                drawLine(color, Offset(s * 0.32f, s * 0.08f), Offset(s * 0.32f, s * 0.26f), width)
                drawLine(color, Offset(s * 0.68f, s * 0.08f), Offset(s * 0.68f, s * 0.26f), width)
                drawCircle(color, s * 0.08f, Offset(s * 0.5f, s * 0.64f))
            }
        }
    }
}

/** "Oct 12–16", "Oct 30 – Nov 2"; spoken, "October 12 to 16". */
private fun formatRange(start: java.time.LocalDate, end: java.time.LocalDate, spoken: Boolean): String {
    val month = DateTimeFormatter.ofPattern(if (spoken) "MMMM d" else "MMM d", Locale.US)
    val to = if (spoken) " to " else if (start.month == end.month) "–" else " – "
    val last = if (start.month == end.month) end.dayOfMonth.toString() else end.format(month)
    // Non-breaking space inside each date, so "Nov" never ends a line on its own.
    return "${start.format(month).replace(' ', '\u00A0')}$to${last.replace(' ', '\u00A0')}"
}

private val GlyphSize = 36.dp
private val WeatherIconSize = 18.dp
