@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazzucci.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import com.mazzucci.weather.domain.formatBothUnits
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.Place
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.formatDegrees
import com.mazzucci.weather.domain.Activity
import com.mazzucci.weather.domain.ActivityScorer
import com.mazzucci.weather.domain.CommuteSettings
import com.mazzucci.weather.domain.Countdown
import com.mazzucci.weather.domain.commuteAdvice
import com.mazzucci.weather.domain.DaySummary
import com.mazzucci.weather.narration.Meme
import com.mazzucci.weather.domain.Daylight
import com.mazzucci.weather.domain.describeUv
import com.mazzucci.weather.domain.describeWeatherCode
import com.mazzucci.weather.domain.formatClock
import com.mazzucci.weather.domain.formatDayLabel
import com.mazzucci.weather.domain.formatDayName
import com.mazzucci.weather.domain.formatHour
import java.time.LocalDate
import kotlin.math.roundToInt
import com.mazzucci.weather.domain.formatTemp
import com.mazzucci.weather.domain.formatWind
import com.mazzucci.weather.domain.other
import com.mazzucci.weather.narration.Narration
import com.mazzucci.weather.narration.NarrationSource

/** Height of the transparent action row that floats over each page's hero (below the status bar). */
private val TopBarHeight = 56.dp
/** Up to this many pages the indicator is a row of dots; beyond it a compact "3 / 12" label. */
private const val MAX_DOTS = 6
private val PageMargin = 20.dp
private val HeroCorner = 28.dp

/** Stateless main screen: one swipeable page per place. Kept free of ViewModel so screenshot tests can render it. */
@Composable
fun WeatherPagerScreen(
    state: WeatherUiState,
    pagerState: PagerState,
    onRefresh: (key: String) -> Unit,
    onRequestPermission: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.pages.isEmpty()) {
            EmptyState(onOpenSearch, onUseCurrentLocation)
        } else {
            HorizontalPager(pagerState, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { index ->
                val page = state.pages[index]
                WeatherPage(
                    page = page,
                    unit = state.settings.primaryUnit,
                    activity = state.settings.activity,
                    // The commute is about one place: the first page (your location, or your first saved place).
                    commute = state.settings.commute.takeIf { index == 0 },
                    onRefresh = { onRefresh(page.key) },
                    onRequestPermission = onRequestPermission,
                    onOpenSearch = onOpenSearch,
                )
            }
        }
        // Floating action row below the status bar; every page starts with a gradient, so white icons always
        // have contrast. The indicator takes whatever width the four buttons leave, so they never get pushed off.
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(TopBarHeight).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterStart) {
                    if (state.pages.size > 1) PageIndicator(state.pages, pagerState.currentPage)
                }
                if (state.pages.isNotEmpty()) {
                    IconButton({ state.pages.getOrNull(pagerState.currentPage)?.let { onRefresh(it.key) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
                IconButton(onOpenSearch) { Icon(Icons.Default.Add, contentDescription = "Add place") }
                IconButton(onOpenPlaces) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Places") }
                IconButton(onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
        }
    }
}

/**
 * Where you are in the pager: dots for a few pages, a "3 / 12" label for many. Purely informative (the pager
 * itself is swiped), so it's one accessibility node that names the current page.
 */
@Composable
private fun PageIndicator(pages: List<PageUi>, current: Int) {
    val name = pages.getOrNull(current)?.let { it.place?.name ?: "My location" } ?: ""
    val description = "Page ${current + 1} of ${pages.size}: $name"
    if (pages.size <= MAX_DOTS) {
        Row(
            Modifier.height(TopBarHeight).semantics { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            pages.indices.forEach { i ->
                val selected = i == current
                Box(
                    Modifier
                        .size(width = if (selected) 18.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.45f))
                )
            }
        }
    } else {
        Text(
            "${current + 1} / ${pages.size}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.18f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { contentDescription = description },
        )
    }
}

@Composable
fun WeatherPage(
    page: PageUi,
    unit: TempUnit,
    activity: Activity? = null,
    commute: CommuteSettings? = null,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val dark = MaterialTheme.isDark
    val content = page.content
    val loaded = content as? PageContent.Loaded
    val night = loaded?.forecast?.isNightNow ?: false
    val gradient = loaded?.let { heroGradient(skyOf(it.forecast.current.code), night, dark) } ?: neutralGradient(dark)

    val refreshing = page.refreshing && loaded != null
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            // Below the status bar and the action row, not under them.
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = TopBarHeight),
            )
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Hero(gradient) {
                PlaceHeader(page)
                when (content) {
                    is PageContent.Loaded -> HeroForecast(content.forecast, content.summary, unit, night)
                    else -> Spacer(Modifier.height(24.dp))
                }
            }
            Column(Modifier.fillMaxWidth()) {
                when (content) {
                    PageContent.Loading -> LoadingSkeleton()
                    PageContent.NeedsPermission -> StateCard(
                        icon = Icons.Default.LocationOn,
                        title = "Where are you?",
                        text = "Allow location access to see the weather where you are. Only a rough position is used and it never leaves the phone.",
                    ) {
                        Button(onRequestPermission) { Text("Allow location") }
                        TextButton(onOpenSearch) { Text("Search for a place instead") }
                    }
                    is PageContent.Failed -> StateCard(
                        icon = Icons.Default.Warning,
                        title = "Couldn't load the weather",
                        text = content.message,
                        iconTint = MaterialTheme.colorScheme.error,
                    ) {
                        Button(onRefresh) { Text("Try again") }
                        if (page.key == Place.CURRENT_LOCATION_ID) {
                            TextButton(onOpenSearch) { Text("Search for a place instead") }
                        }
                    }
                    is PageContent.Loaded -> BodyForecast(content.forecast, unit, night, content.meme, activity, content.comingUp, commute)
                }
                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/** The gradient block at the top of every page. Content is drawn in white; the action row floats above it. */
@Composable
private fun Hero(colors: List<Color>, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = HeroCorner, bottomEnd = HeroCorner))
            .background(Brush.verticalGradient(colors))
            .windowInsetsPadding(WindowInsets.statusBars) // gradient extends behind the status bar
            .padding(top = TopBarHeight, bottom = 24.dp)
            .padding(horizontal = PageMargin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) { content() }
    }
}

@Composable
private fun PlaceHeader(page: PageUi) {
    val isCurrent = page.key == Place.CURRENT_LOCATION_ID
    Spacer(Modifier.height(8.dp))
    Text(
        page.place?.name ?: "My location",
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
    )
    // Until the device location resolves there's no name yet, so the title alone says it.
    val detail = if (isCurrent) "Current location".takeIf { page.place != null } else page.place?.detail
    if (detail != null) {
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isCurrent) {
                Icon(Icons.Default.LocationOn, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Summary, big temperature, condition and today's range, all on the gradient. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroForecast(forecast: Forecast, summary: Narration, unit: TempUnit, night: Boolean) {
    val cur = forecast.current
    Spacer(Modifier.height(20.dp))
    SummaryBlock(summary)
    Spacer(Modifier.height(20.dp))

    val other = unit.other()
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${formatTemp(cur.tempC, unit)}, ${formatTemp(cur.tempC, other)}, ${cur.description}"
        },
    ) {
        Text(formatTemp(cur.tempC, unit), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.width(10.dp))
        Text(
            formatTemp(cur.tempC, other),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 18.dp),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(cur.code, night, monoPalette(Color.White), size = 30.dp, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(cur.description, style = MaterialTheme.typography.titleLarge)
    }
    Spacer(Modifier.height(16.dp))
    // Wraps onto a second line on narrow screens or with large text, rather than squeezing a pill.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeroPill("High", formatDegrees(forecast.today.highC, unit), formatBothUnits(forecast.today.highC, unit))
        HeroPill("Low", formatDegrees(forecast.today.lowC, unit), formatBothUnits(forecast.today.lowC, unit))
        HeroPill("Rain", "${forecast.today.precipChance}%")
    }
}

@Composable
private fun HeroPill(label: String, value: String, spoken: String = value) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label $spoken" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun SummaryBlock(summary: Narration) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(summary.text, style = MaterialTheme.typography.bodyLarge)
        if (summary.source == NarrationSource.GEMMA) {
            Spacer(Modifier.height(8.dp))
            Text(
                "✦ Written by Gemma on this device",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFFFD08A),
            )
        }
    }
}

/** Detail tiles and the hourly strip, on the normal surface below the hero. */
@Composable
private fun BodyForecast(
    forecast: Forecast,
    unit: TempUnit,
    night: Boolean,
    meme: Meme?,
    activity: Activity?,
    comingUp: List<Countdown>,
    commute: CommuteSettings? = null,
) {
    val cur = forecast.current
    if (commute != null) {
        // Walking is the fallback mode when outdoor plans are off; the card says which in its detail line.
        val mode = activity ?: Activity.WALKING
        val advice = remember(forecast, mode, commute) { commuteAdvice(forecast, mode, commute) }
        if (advice != null) {
            Spacer(Modifier.height(20.dp))
            CommuteCard(advice, mode, unit, cur.time.toLocalDate(), Modifier.padding(horizontal = PageMargin))
        }
    }
    Spacer(Modifier.height(20.dp))
    TileRow {
        StatTile(
            "Feels like", formatDegrees(cur.feelsLikeC, unit), Modifier.weight(1f),
            detail = formatTemp(cur.feelsLikeC, unit.other()),
        )
        StatTile("Humidity", "${cur.humidity}%", Modifier.weight(1f))
        val gust = forecast.nextHours.firstOrNull()?.gustKmh
        StatTile(
            "Wind",
            formatWind(cur.windKmh, unit),
            Modifier.weight(1f),
            detail = gust?.takeIf { it > cur.windKmh }?.let { "Gusts ${formatWind(it, unit)}" },
        )
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "Next ${forecast.nextHours.size} hours",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = PageMargin),
    )
    Spacer(Modifier.height(12.dp))
    HourlyStrip(forecast, unit, night)
    if (activity != null) {
        val plan = remember(forecast, activity) { ActivityScorer.plan(forecast, activity) }
        if (plan.hours.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ActivityCard(plan, unit, forecast.current.time.toLocalDate(), Modifier.padding(horizontal = PageMargin))
        }
    }
    SunAndUv(forecast.today, unit)
    val week = forecast.upcomingDays()
    if (week.size > 1) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Next ${week.size} days",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = PageMargin),
        )
        Spacer(Modifier.height(12.dp))
        DailyList(week, forecast.today.date, unit)
    }
    if (comingUp.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Coming up",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = PageMargin).semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        ComingUpCard(comingUp, forecast, unit, Modifier.padding(horizontal = PageMargin))
    }
    if (meme != null) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Today's weather meme",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = PageMargin).semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        MemeCard(meme, Modifier.padding(horizontal = PageMargin))
    }
}

/** Sunrise, sunset and UV for today; skipped when the forecast has none of them. */
@Composable
private fun SunAndUv(today: DaySummary, unit: TempUnit) {
    val daylight = today.daylight
    val uv = today.uvIndexMax
    if (daylight == Daylight.UNKNOWN && uv == null) return
    Spacer(Modifier.height(16.dp))
    TileRow {
        when (daylight) {
            Daylight.NORMAL -> {
                val sunrise = today.sunrise!!
                val sunset = today.sunset!!
                StatTile("Sunrise", formatClock(sunrise), Modifier.weight(1f), compact = true)
                StatTile(
                    "Sunset", formatClock(sunset), Modifier.weight(1f), compact = true,
                    detail = "next day".takeIf { sunset.toLocalDate() != today.date },
                )
            }
            Daylight.POLAR_NIGHT -> StatTile("Daylight", "None", Modifier.weight(2f), detail = "Polar night")
            Daylight.MIDNIGHT_SUN -> StatTile("Daylight", "24 hours", Modifier.weight(2f), detail = "Midnight sun")
            Daylight.UNKNOWN -> Unit
        }
        if (uv != null) StatTile("UV index", "${uv.roundToInt()}", Modifier.weight(1f), detail = describeUv(uv))
    }
}

/** A row of equal-height tiles, so a tile with a detail line doesn't stand taller than its neighbours. */
@Composable
private fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PageMargin).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * A temperature in the primary unit with the other unit in small muted text underneath ("71°" over "21°C"),
 * the same pairing the hero uses. [primaryStyle] sets the size of the main line; the secondary is always
 * labelSmall so it reads as an annotation, not a second value to compare.
 */
@Composable
private fun DualTemp(
    c: Double,
    unit: TempUnit,
    primaryStyle: TextStyle,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    /** If set, the primary value is centred in a box this tall, to line up with other cells in a row. */
    primaryLine: Dp? = null,
) {
    Column(modifier, horizontalAlignment = alignment) {
        if (primaryLine != null) {
            Box(Modifier.height(primaryLine), contentAlignment = Alignment.Center) {
                Text(formatDegrees(c, unit), style = primaryStyle, maxLines = 1, softWrap = false)
            }
        } else {
            Text(formatDegrees(c, unit), style = primaryStyle, maxLines = 1, softWrap = false)
        }
        Text(
            formatTemp(c, unit.other()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * One row per day: name, icon with the rain chance under it, and the low–high range drawn on a bar shared by the
 * whole week, so warmer and cooler days line up visually. Low and high each carry the other unit underneath.
 * Every column has one width for the whole week (the widest entry), so the bars start and end at the same x in
 * every row whatever the font size or the values.
 */
@Composable
private fun DailyList(days: List<DaySummary>, today: LocalDate, unit: TempUnit) {
    val palette = cardIconPalette()
    val rainColor = MaterialTheme.weatherColors.rain
    val weekLow = days.minOf { it.lowC }
    val weekHigh = days.maxOf { it.highC }
    val type = MaterialTheme.typography
    val lowStyle = type.bodyMedium
    val highStyle = type.titleSmall
    val secondaryStyle = type.labelSmall
    val other = unit.other()
    val dayWidth = widestText(days.map { formatDayLabel(it.date, today) }, highStyle)
    val lowWidth = maxOf(
        widestText(days.map { formatDegrees(it.lowC, unit) }, lowStyle),
        widestText(days.map { formatTemp(it.lowC, other) }, secondaryStyle),
    )
    val highWidth = maxOf(
        widestText(days.map { formatDegrees(it.highC, unit) }, highStyle),
        widestText(days.map { formatTemp(it.highC, other) }, secondaryStyle),
    )
    val iconWidth = maxOf(26.dp, widestText(listOf("100%"), secondaryStyle))
    val density = LocalDensity.current
    // Line heights scale with the font, so the bar keeps tracking the primary numbers at any font size.
    val primaryLine = with(density) { highStyle.lineHeight.toDp() }
    val secondaryLine = with(density) { secondaryStyle.lineHeight.toDp() }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = PageMargin),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            days.forEach { day ->
                val rain = day.precipChance.takeIf { it >= 20 }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = buildString {
                                append("${formatDayName(day.date, today)}, ${describeWeatherCode(day.code)}, ")
                                append("high ${formatBothUnits(day.highC, unit)}, low ${formatBothUnits(day.lowC, unit)}")
                                if (rain != null) append(", $rain% chance of rain")
                            }
                        },
                    verticalAlignment = Alignment.Top,
                ) {
                    // Everything is top-aligned and each cell starts with a primary-line-tall box, so the day,
                    // icon, numbers and bar all centre on the same line; the second line holds the other unit
                    // (and the rain chance under the icon).
                    Box(Modifier.width(dayWidth).height(primaryLine), contentAlignment = Alignment.CenterStart) {
                        Text(formatDayLabel(day.date, today), style = highStyle, maxLines = 1, softWrap = false)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.width(iconWidth), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.height(primaryLine), contentAlignment = Alignment.Center) {
                            WeatherIcon(
                                day.code, night = false, palette, Modifier.requiredSize(26.dp), size = 26.dp,
                                contentDescription = null,
                            )
                        }
                        if (rain != null) {
                            Text("$rain%", style = secondaryStyle, color = rainColor, maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    DualTemp(
                        day.lowC, unit,
                        primaryStyle = lowStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.width(lowWidth),
                        alignment = Alignment.End,
                        primaryLine = primaryLine,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.weight(1f).height(primaryLine),
                        contentAlignment = Alignment.Center,
                    ) {
                        RangeBar(day.lowC, day.highC, weekLow, weekHigh, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.width(8.dp))
                    DualTemp(
                        day.highC, unit,
                        primaryStyle = highStyle,
                        modifier = Modifier.width(highWidth),
                        alignment = Alignment.Start,
                        primaryLine = primaryLine,
                    )
                }
            }
        }
    }
}

/** Width of the widest of [texts] set in [style] on one line, so a column can use one width for every row. */
@Composable
private fun widestText(texts: List<String>, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val px = texts.maxOfOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width } ?: 0
    // A pixel of slack so rounding never pushes the text onto a clip.
    return with(LocalDensity.current) { (px + 1).toDp() }
}

/** A track spanning [min]–[max] with the [low]–[high] segment filled in cool-to-warm. */
@Composable
private fun RangeBar(low: Double, high: Double, min: Double, max: Double, modifier: Modifier = Modifier) {
    val span = (max - min).takeIf { it > 0.0 } ?: 1.0
    val start = ((low - min) / span).toFloat().coerceIn(0f, 1f)
    val end = ((high - min) / span).toFloat().coerceIn(start, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val cool = MaterialTheme.weatherColors.rain
    val warm = MaterialTheme.weatherColors.sun
    Box(modifier.height(6.dp).clip(CircleShape).background(track)) {
        Row(Modifier.fillMaxWidth()) {
            if (start > 0f) Spacer(Modifier.weight(start))
            // At least a dot, so a day with no range still shows where it sits.
            Box(
                Modifier
                    .weight((end - start).coerceAtLeast(0.04f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(cool, warm)))
            )
            if (end < 1f) Spacer(Modifier.weight(1f - end))
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    compact: Boolean = false,
) {
    Card(
        modifier.fillMaxHeight().semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull(label, value, detail).joinToString(" ")
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HourlyStrip(forecast: Forecast, unit: TempUnit, nightNow: Boolean) {
    val hours = forecast.nextHours
    val type = MaterialTheme.typography
    // One width for every card (the widest label or value), so large fonts widen the strip evenly.
    val cardWidth = maxOf(
        52.dp,
        widestText(hours.mapIndexed { i, h -> if (i == 0) "Now" else formatHour(h.time) }, type.labelMedium),
        widestText(hours.map { formatDegrees(it.tempC, unit) }, type.titleMedium),
        widestText(hours.map { formatTemp(it.tempC, unit.other()) }, type.labelSmall),
    )
    val palette = cardIconPalette()
    val rainColor = MaterialTheme.weatherColors.rain
    // The strip runs edge to edge so a partly visible last tile hints that it scrolls.
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = PageMargin),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(hours) { i, hour ->
            val label = if (i == 0) "Now" else formatHour(hour.time)
            val night = if (i == 0) nightNow else forecast.isNight(hour)
            Card(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "$label, ${formatBothUnits(hour.tempC, unit)}, ${hour.precipChance}% chance of rain"
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (i == 0) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer
                ),
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 12.dp).width(cardWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(8.dp))
                    WeatherIcon(hour.code, night, palette, size = 30.dp)
                    Spacer(Modifier.height(8.dp))
                    DualTemp(hour.tempC, unit, primaryStyle = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${hour.precipChance}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hour.precipChance >= 30) rainColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Grey blocks in the shape of the loaded layout, so the page doesn't jump when data arrives. */
@Composable
private fun LoadingSkeleton() {
    val color = MaterialTheme.weatherColors.skeleton
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier.fillMaxWidth().padding(horizontal = PageMargin).semantics { contentDescription = "Loading the forecast" }
    ) {
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { Box(Modifier.weight(1f).height(76.dp).clip(shape).background(color)) }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.width(120.dp).height(18.dp).clip(CircleShape).background(color))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(shape), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { Box(Modifier.width(72.dp).height(138.dp).clip(shape).background(color)) }
        }
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                "Getting the forecast…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Message + actions used for the permission prompt and errors. */
@Composable
private fun StateCard(
    icon: ImageVector,
    title: String,
    text: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    actions: @Composable () -> Unit,
) {
    Spacer(Modifier.height(24.dp))
    Card(
        Modifier.fillMaxWidth().padding(horizontal = PageMargin),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, Modifier.size(40.dp), tint = iconTint)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            actions()
        }
    }
}

/** First run with location off and nothing saved: explain the two ways to get a forecast. */
@Composable
private fun EmptyState(onOpenSearch: () -> Unit, onUseCurrentLocation: () -> Unit) {
    val dark = MaterialTheme.isDark
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Hero(neutralGradient(dark)) {
            Spacer(Modifier.height(8.dp))
            Text("Weather", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(2.dp))
            Text("Local forecasts, in °F and °C", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = PageMargin, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(size = 120.dp)
            Spacer(Modifier.height(24.dp))
            Text("Pick a place to start", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Search for any city, or use your current location. You can save as many places as you like and swipe between them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(onOpenSearch, Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.Search, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Search for a city")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onUseCurrentLocation, Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Default.LocationOn, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Use my location")
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
