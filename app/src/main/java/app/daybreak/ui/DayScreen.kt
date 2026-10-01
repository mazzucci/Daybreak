package app.daybreak.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.daybreak.domain.DaySummary
import app.daybreak.domain.Forecast
import app.daybreak.domain.HourForecast
import app.daybreak.domain.Precip
import app.daybreak.domain.RainPeriod
import app.daybreak.domain.TempUnit
import app.daybreak.domain.Term
import app.daybreak.domain.describeWeatherCode
import app.daybreak.domain.explain
import app.daybreak.domain.formatBothUnits
import app.daybreak.domain.formatDegrees
import app.daybreak.domain.formatHour
import app.daybreak.domain.formatTemp
import app.daybreak.domain.formatWind
import app.daybreak.domain.keepUnitsTogether
import app.daybreak.domain.other
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.min

/** Height of the floating back-arrow row over the day's sky. */
private val DayBarHeight = 56.dp

/** Hours a day's bar chart has, one bar each. */
private const val CHART_HOURS = 24

/** Bars stop growing at this many mm an hour, so one downpour doesn't flatten the rest. */
private const val BAR_CAP_MM = 4.0

/**
 * One day in full, opened from a row of the 10-day list: the day's sky with its name, condition, high and low in both
 * units and how warm or cold it'll feel; the temperature hour by hour; the rain (or snow) card; wind; and sun and UV.
 * Full screen over the tabs, with a back arrow, like Search and Places.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayScreen(
    placeName: String,
    forecast: Forecast,
    date: LocalDate,
    unit: TempUnit,
    onBack: () -> Unit,
) {
    val day = forecast.day(date) ?: return
    val today = forecast.today.date
    val isToday = date == today
    val dark = MaterialTheme.isDark
    val gradient = heroGradient(skyOf(day.code), night = false, darkTheme = dark)
    var explaining by rememberSaveable { mutableStateOf<Term?>(null) }
    explaining?.let { term ->
        ExplainSheet(explain(term, forecast, unit, Locale.getDefault(), date)) { explaining = null }
    }
    val openExplanation = remember { { t: Term -> explaining = t } }
    CompositionLocalProvider(LocalExplain provides openExplanation) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val scroll = rememberScrollState()
            var heroBottom by remember { mutableStateOf(Int.MAX_VALUE) }
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Hero(gradient, Modifier.onSizeChanged { heroBottom = it.height }, top = DayBarHeight) {
                    DayHeader(placeName, forecast, day, unit)
                }
                Spacer(Modifier.height(24.dp))
                SectionHeading("Hour by hour")
                Spacer(Modifier.height(12.dp))
                // Today starts at the current hour: the hours already gone aren't worth scrolling past.
                val nowHour = forecast.current.time.truncatedTo(ChronoUnit.HOURS)
                val hours = forecast.hoursOf(date).filter { !isToday || !it.time.isBefore(nowHour) }
                if (hours.isNotEmpty()) {
                    HourStrip(
                        hours = hours,
                        unit = unit,
                        label = { i, h -> if (isToday && i == 0) "Now" else formatHour(h.time) },
                        night = { i, h -> if (isToday && i == 0) forecast.isNightNow else forecast.isNight(h) },
                        showPrecip = false,
                        highlightFirst = isToday,
                    )
                }
                Spacer(Modifier.height(16.dp))
                RainCard(forecast, day, unit, Modifier.padding(horizontal = PageMargin))
                val daysAhead = ChronoUnit.DAYS.between(today, date)
                if (daysAhead >= 2 && (Precip.dayAmount(day, unit) != null || Precip.dayRain(forecast, date).timing != null)) {
                    Text(
                        "Amounts this far ahead are rough; the chance is the better guide.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = PageMargin + 4.dp, vertical = 8.dp),
                    )
                }
                WindTiles(day, unit)
                SunAndUv(day, explainable = false)
                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
            StatusBarScrim(scroll, heroBottom, gradient.first(), extra = DayBarHeight)
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().height(DayBarHeight).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            }
        }
    }
}

/**
 * The day's name and date, its condition, and pills for the high and low (both units) and the feels-like range,
 * on the day's sky.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayHeader(placeName: String, forecast: Forecast, day: DaySummary, unit: TempUnit) {
    val today = forecast.today.date
    val locale = Locale.getDefault()
    val title = when (day.date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> day.date.format(DateTimeFormatter.ofPattern("EEEE", Locale.US))
    }
    // "Thursday, October 8" for today and tomorrow (whose title isn't a weekday), "October 8" otherwise; in the
    // phone's own order.
    val skeleton = if (title == "Today" || title == "Tomorrow") "EEEEMMMMd" else "MMMMd"
    val date = day.date.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale))
    Spacer(Modifier.height(4.dp))
    Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(2.dp))
    Text("$date · $placeName", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(day.code, night = false, monoPalette(Color.White), size = 30.dp, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(describeWeatherCode(day.code), style = MaterialTheme.typography.titleLarge)
    }
    Spacer(Modifier.height(12.dp))
    val other = unit.other()
    val feels = forecast.hoursOf(day.date).mapNotNull { it.feelsLikeC }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        HeroPill("High", formatDegrees(day.highC, unit), formatBothUnits(day.highC, unit), secondary = formatTemp(day.highC, other))
        HeroPill("Low", formatDegrees(day.lowC, unit), formatBothUnits(day.lowC, unit), secondary = formatTemp(day.lowC, other))
        if (feels.isNotEmpty()) {
            val hi = feels.max()
            val lo = feels.min()
            HeroPill(
                "Feels like",
                "${formatDegrees(hi, unit)} / ${formatDegrees(lo, unit)}",
                "high ${formatBothUnits(hi, unit)}, low ${formatBothUnits(lo, unit)}",
            )
        }
    }
}

/** The day's strongest wind with where it comes from, and its strongest gust; nothing when neither is known. */
@Composable
private fun WindTiles(day: DaySummary, unit: TempUnit) {
    val wind = day.windMaxKmh
    val gust = day.gustMaxKmh
    if (wind == null && gust == null) return
    Spacer(Modifier.height(16.dp))
    TileRow {
        if (wind != null) {
            StatTile("Wind", keepUnitsTogether("Up to ${formatWind(wind, unit)}"), Modifier.weight(1f), direction = day.windDirectionDeg)
        }
        if (gust != null) StatTile("Gusts", keepUnitsTogether("Up to ${formatWind(gust, unit)}"), Modifier.weight(1f))
    }
}

/**
 * The day's rain (or snow): a verdict with the chance word, total and hours; when in the day it falls; a bar per
 * hour with the chance every three hours; and the daytime and overnight halves. Tapping it explains the total.
 */
@Composable
internal fun RainCard(forecast: Forecast, day: DaySummary, unit: TempUnit, modifier: Modifier = Modifier) {
    val rain = remember(forecast, day.date) { Precip.dayRain(forecast, day.date) }
    val title = if (Precip.isSnowDay(day)) "Snow" else "Rain"
    val verdict = Precip.verdict(day, unit)
    val timing = rain.timing?.let { Precip.timingSentence(it) }
    val showChart = rain.hasAmounts && rain.hours.any { (it.precipMm ?: 0.0) >= Precip.HOUR_AMOUNT_MIN_MM }
    // The halves of the day that aren't dry, in order; "Before sunrise" only shows up when the night before
    // carries on past midnight, so the day's total never goes missing from the rows.
    val periods = listOfNotNull(
        rain.beforeSunrise?.takeIf { !it.dry }?.let { "Before sunrise" to it },
        rain.daytime?.takeIf { !it.dry }?.let { "Daytime" to it },
        rain.overnight?.takeIf { !it.dry }?.let { "Overnight" to it },
    )
    val explain = LocalExplain.current
    val spoken = buildList {
        add(title)
        add(verdict.replace(" · ", ", "))
        timing?.let { add(it) }
        periods.forEach { (name, p) -> add("$name, ${formatHour(p.start)} to ${formatHour(p.end)}, ${p.spoken(unit)}") }
    }.joinToString(". ")
    Card(
        onClick = { explain?.invoke(Term.RAIN_DAY) },
        enabled = explain != null,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = spoken
            role = Role.Button
            onClick(label = "Explain", action = null)
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (explain != null) InfoGlyph(MaterialTheme.colorScheme.outline, MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Text(keepUnitsTogether(verdict), style = MaterialTheme.typography.titleLarge)
            if (timing != null) {
                Spacer(Modifier.height(2.dp))
                Text(keepUnitsTogether(timing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showChart) {
                Spacer(Modifier.height(16.dp))
                RainChart(rain.hours, day.date)
            }
            if (periods.isNotEmpty()) {
                Spacer(Modifier.height(if (showChart) 16.dp else 12.dp))
                periods.forEachIndexed { i, (name, period) ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    PeriodRow(name, period, unit)
                }
            }
        }
    }
}

/**
 * A bar per hour of the day, its height the amount (capped at 4 mm an hour, where the bar darkens), with the chance
 * under every third hour ("·" below 10%) and the time every six. One blue at different heights stands in for
 * light, moderate and heavy. Hidden from screen readers: the verdict and the rows carry the same numbers.
 */
@Composable
private fun RainChart(hours: List<HourForecast>, date: LocalDate) {
    val rainColor = MaterialTheme.weatherColors.rain
    val capColor = if (MaterialTheme.isDark) lerp(rainColor, Color.White, 0.45f) else lerp(rainColor, Color.Black, 0.35f)
    val baseColor = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val small = MaterialTheme.typography.labelSmall
    val byHour = hours.associateBy { it.time.hour }
    BoxWithConstraints(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        // "40%" when three bars leave room for "100%", else plain numbers.
        val withPercent = maxWidth * 3 / CHART_HOURS >= widestText(listOf("100%"), small) + 4.dp
        Column {
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                val slot = size.width / CHART_HOURS
                val gap = min(4.dp.toPx(), slot * 0.3f)
                val bar = slot - gap
                val floor = size.height - 1.dp.toPx()
                drawLine(baseColor, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
                byHour.forEach { (hour, h) ->
                    val mm = h.precipMm ?: 0.0
                    if (mm < Precip.HOUR_AMOUNT_MIN_MM) return@forEach
                    val fraction = (min(mm, BAR_CAP_MM) / BAR_CAP_MM).toFloat()
                    val height = (fraction * floor).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = if (mm >= BAR_CAP_MM) capColor else rainColor,
                        topLeft = Offset(hour * slot + gap / 2, floor - height),
                        size = Size(bar, height),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            TickRow((0 until CHART_HOURS step 3).toList()) { hour ->
                val chance = byHour[hour]?.precipChance ?: 0
                val shown = Precip.showHourChance(chance)
                Text(
                    if (shown) "$chance${if (withPercent) "%" else ""}" else "·",
                    style = small,
                    color = if (shown && Precip.highlightChance(chance)) rainColor else muted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.height(2.dp))
            TickRow(listOf(0, 6, 12, 18)) { hour ->
                Text(formatHour(date.atTime(hour, 0)), style = small, color = muted, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * Labels centred under the bars of [hours] (kept inside the row at its ends). When large text leaves no room, a
 * label that would touch the one before it is left out, so they thin out rather than overlap.
 */
@Composable
private fun TickRow(hours: List<Int>, label: @Composable (Int) -> Unit) {
    Layout(content = { hours.forEach { label(it) } }, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = 4.dp.roundToPx()
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            var free = 0
            placeables.forEachIndexed { i, p ->
                val center = (hours[i] + 0.5f) * width / CHART_HOURS
                val x = (center - p.width / 2f).toInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
                if (x >= free) {
                    p.place(x, 0)
                    free = x + p.width + gap
                }
            }
        }
    }
}

/** "Daytime  7 AM–7 PM ……… 90% · 11 mm · 5 h"; the numbers move under the name when large text leaves no room. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodRow(name: String, period: RainPeriod, unit: TempUnit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.padding(end = 12.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${formatHour(period.start)}–${formatHour(period.end)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            keepUnitsTogether(period.describe(unit)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
    }
}
