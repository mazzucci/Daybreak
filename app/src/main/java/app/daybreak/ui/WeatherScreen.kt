@file:OptIn(ExperimentalMaterial3Api::class)

package app.daybreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.ScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
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
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.clickable
import app.daybreak.domain.explain
import app.daybreak.domain.Term
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
import app.daybreak.domain.formatBothUnits
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.daybreak.domain.Forecast
import app.daybreak.domain.Place
import app.daybreak.domain.TempUnit
import app.daybreak.domain.formatDegrees
import app.daybreak.domain.Activity
import app.daybreak.domain.ActivityScorer
import androidx.compose.material3.minimumInteractiveComponentSize
import app.daybreak.domain.DaySummary
import app.daybreak.domain.Daylight
import app.daybreak.domain.describeUv
import app.daybreak.domain.describeWeatherCode
import app.daybreak.domain.formatClock
import app.daybreak.domain.formatDayLabel
import app.daybreak.domain.formatDayName
import app.daybreak.domain.formatHour
import java.time.LocalDate
import kotlin.math.roundToInt
import app.daybreak.domain.formatTemp
import app.daybreak.domain.formatWind
import app.daybreak.domain.other
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import app.daybreak.domain.HourForecast
import app.daybreak.domain.LESS_CERTAIN_FROM
import app.daybreak.domain.Precip
import app.daybreak.domain.feelsLikeWorthShowing
import app.daybreak.domain.formatUpdated
import app.daybreak.domain.formatWindFrom
import app.daybreak.domain.formatWindFromSpoken
import app.daybreak.domain.isStale
import java.time.Instant

/** Height of the transparent action row that floats over each page's hero (below the status bar). */
private val TopBarHeight = 56.dp
/** Up to this many pages the indicator is a row of dots; beyond it a compact "3 / 12" label. */
private const val MAX_DOTS = 6
internal val PageMargin = 20.dp
private val HeroCorner = 28.dp
/** Inline-content id of the "tap to explain" glyph in a tile's label. */
private const val INFO_GLYPH = "info"

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
    /** A day in a page's 10-day list was tapped: open its details. */
    onOpenDay: (pageKey: String, date: LocalDate) -> Unit = { _, _ -> },
    /** The moment the "Updated … ago" lines count from; ticks with the clock when null (screenshots pass one). */
    now: Instant? = null,
) {
    val moment = now ?: rememberMinuteClock()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (state.pages.isEmpty()) {
            EmptyState(onOpenSearch, onUseCurrentLocation)
        } else {
            // Keyed by place, so per-page state (an open explanation) stays with its place when places move.
            HorizontalPager(
                pagerState, Modifier.fillMaxSize(), beyondViewportPageCount = 1,
                key = { state.pages[it].key },
            ) { index ->
                val page = state.pages[index]
                WeatherPage(
                    page = page,
                    unit = state.settings.primaryUnit,
                    activity = state.settings.activity,
                    onRefresh = { onRefresh(page.key) },
                    onRequestPermission = onRequestPermission,
                    onOpenSearch = onOpenSearch,
                    onOpenDay = { onOpenDay(page.key, it) },
                    now = moment,
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
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDay: (LocalDate) -> Unit = {},
    now: Instant = Instant.now(),
) {
    val dark = MaterialTheme.isDark
    val content = page.content
    val loaded = content as? PageContent.Loaded
    // Which term's explanation is open; kept across rotation, cleared when the page has no forecast.
    var explaining by rememberSaveable { mutableStateOf<Term?>(null) }
    val explainTerm = explaining
    if (explainTerm != null && loaded != null) {
        ExplainSheet(explain(explainTerm, loaded.forecast, unit, Locale.getDefault())) { explaining = null }
    }
    val night = loaded?.forecast?.isNightNow ?: false
    val gradient = loaded?.let { heroGradient(skyOf(it.forecast.current.code), night, dark) } ?: neutralGradient(dark)

    val refreshing = page.refreshing && loaded != null
    val refreshState = rememberPullToRefreshState()
    // Tiles and pills anywhere on the page open their explanation through this.
    // Close the sheet for good when the forecast goes away (a failed refresh), so it can't reappear by itself later.
    LaunchedEffect(loaded == null) { if (loaded == null) explaining = null }
    val openExplanation = remember { { t: Term -> explaining = t } }
    CompositionLocalProvider(LocalExplain provides openExplanation) {
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
        val scroll = rememberScrollState()
        var heroBottom by remember { mutableStateOf(Int.MAX_VALUE) }
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Hero(gradient, Modifier.onSizeChanged { heroBottom = it.height }) {
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
                    is PageContent.Loaded -> BodyForecast(content.forecast, unit, night, activity, content.fetchedAt, now, onOpenDay)
                }
                Spacer(Modifier.height(24.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
        StatusBarScrim(scroll, heroBottom, gradient.first(), extra = TopBarHeight)
    }
    }
}

/**
 * Once the sky has scrolled up under the status bar (and, on Weather, the floating action row: [extra]), a strip
 * in the sky's top colour fades in behind them, so the clock, the icons and the white buttons never sit on top of
 * the cards. Put it last in the page's Box, with the scroll and how far down the hero ends.
 */
@Composable
internal fun BoxScope.StatusBarScrim(scroll: ScrollState, heroBottomPx: Int, color: Color, extra: Dp = 0.dp) {
    val density = LocalDensity.current
    val barPx = WindowInsets.statusBars.getTop(density) + with(density) { extra.toPx() }
    val fade = with(density) { 24.dp.toPx() }
    // Fully there by the time the sky's bottom edge reaches the bar. Read in the layer, so scrolling doesn't recompose.
    val layer: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
        alpha = ((scroll.value - (heroBottomPx - barPx - fade)) / fade).coerceIn(0f, 1f)
    }
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
            .graphicsLayer(layer).background(color),
    )
    if (extra > 0.dp) {
        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().height(extra).graphicsLayer(layer).background(color))
    }
}

/**
 * The gradient block at the top of every page. Content is drawn in white; on Weather the action row floats above
 * it ([top] leaves room for it), Home's header starts right under the status bar.
 */
@Composable
internal fun Hero(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    top: Dp = TopBarHeight,
    alignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = HeroCorner, bottomEnd = HeroCorner))
            .background(Brush.verticalGradient(colors))
            .windowInsetsPadding(WindowInsets.statusBars) // gradient extends behind the status bar
            .padding(top = top, bottom = 24.dp)
            .padding(horizontal = PageMargin),
        horizontalAlignment = alignment,
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
private fun HeroForecast(forecast: Forecast, summary: String, unit: TempUnit, night: Boolean) {
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
    // Each pill's 48dp touch target adds 4dp of empty space above and below the visible pill, so the spacer
    // and the row gap below are 4dp smaller than they look: 16dp between the condition and the pills, 8dp
    // between wrapped rows.
    Spacer(Modifier.height(12.dp))
    // Wraps onto a second line on narrow screens or with large text, rather than squeezing a pill.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        HeroPill("High", formatDegrees(forecast.today.highC, unit), formatBothUnits(forecast.today.highC, unit))
        HeroPill("Low", formatDegrees(forecast.today.lowC, unit), formatBothUnits(forecast.today.lowC, unit))
        val (rainValue, rainSpoken) = rainPill(forecast.today, unit)
        HeroPill(Precip.noun(forecast.today), rainValue, rainSpoken, term = Term.RAIN_CHANCE)
    }
}

/**
 * The rain pill's value and its spoken form: "60% · 4 mm" (the chance from 20%, the total from 0.5 mm, by the
 * shared rules), "unlikely" when neither is worth a number.
 */
private fun rainPill(day: DaySummary, unit: TempUnit): Pair<String, String> {
    val chance = day.precipChance.takeIf { Precip.showDayChance(it) }
    val amount = Precip.dayAmount(day, unit)
    if (chance == null && amount == null) return "unlikely" to "unlikely"
    val shown = listOfNotNull(chance?.let { "$it%" }, amount).joinToString(" · ")
    val spoken = listOfNotNull(
        chance?.let { "$it% chance" },
        Precip.dayTotalSpoken(day, unit)?.removeSuffix(" of snow"),
    ).joinToString(", ")
    return shown to spoken
}

/**
 * A rounded label-and-value chip on the sky ("High 74°"), with an optional [secondary] value in small text
 * ("23°C"). Tappable with an info mark when it has a [term] to explain.
 */
@Composable
internal fun HeroPill(label: String, value: String, spoken: String = value, term: Term? = null, secondary: String? = null) {
    val explain = LocalExplain.current
    val onClick = if (term != null && explain != null) ({ explain(term) }) else null
    Row(
        Modifier
            // A 48dp touch target around every pill (they line up whether or not they're tappable).
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Explain", role = Role.Button, onClick = onClick) else Modifier)
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label $spoken" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        if (secondary != null) {
            Spacer(Modifier.width(5.dp))
            Text(secondary, style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.85f), maxLines = 1)
        }
        if (onClick != null) {
            // Marks this pill as the one that opens something; High and Low next to it don't.
            Spacer(Modifier.width(5.dp))
            InfoGlyph(LocalContentColor.current.copy(alpha = 0.8f), MaterialTheme.typography.labelLarge)
        }
    }
}

/** The "tap to explain" mark beside a label, sized to that label's text so it grows with the font setting. */
@Composable
internal fun InfoGlyph(tint: Color, style: TextStyle) {
    val size = with(LocalDensity.current) { style.fontSize.toDp() }
    Icon(Icons.Outlined.Info, contentDescription = null, Modifier.size(size), tint = tint)
}

@Composable
internal fun SummaryBlock(summary: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(summary, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Detail tiles and the hourly strip, on the normal surface below the hero. */
@Composable
private fun BodyForecast(
    forecast: Forecast,
    unit: TempUnit,
    night: Boolean,
    activity: Activity?,
    fetchedAt: Instant?,
    now: Instant,
    onOpenDay: (LocalDate) -> Unit,
) {
    val cur = forecast.current
    Spacer(Modifier.height(20.dp))
    TileRow {
        StatTile(
            "Feels like", formatDegrees(cur.feelsLikeC, unit), Modifier.weight(1f),
            detail = formatTemp(cur.feelsLikeC, unit.other()),
            term = Term.FEELS_LIKE,
        )
        StatTile("Humidity", "${cur.humidity}%", Modifier.weight(1f), term = Term.HUMIDITY)
        val thisHour = forecast.nextHours.firstOrNull()
        val gust = thisHour?.gustKmh
        StatTile(
            "Wind",
            formatWind(cur.windKmh, unit),
            Modifier.weight(1f),
            detail = gust?.takeIf { it > cur.windKmh }?.let { "Gusts ${formatWind(it, unit)}" },
            direction = thisHour?.windDirectionDeg,
            term = Term.WIND,
        )
    }
    Spacer(Modifier.height(24.dp))
    SectionHeading("Next ${forecast.nextHours.size} hours") {
        if (fetchedAt != null) UpdatedLine(fetchedAt, now)
    }
    Spacer(Modifier.height(12.dp))
    HourStrip(
        hours = forecast.nextHours,
        unit = unit,
        label = { i, h -> if (i == 0) "Now" else formatHour(h.time) },
        night = { i, h -> if (i == 0) night else forecast.isNight(h) },
    )
    if (activity != null) {
        val plan = remember(forecast, activity) { ActivityScorer.plan(forecast, activity) }
        if (plan.hours.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ActivityCard(plan, unit, forecast.current.time.toLocalDate(), Modifier.padding(horizontal = PageMargin))
        }
    }
    SunAndUv(forecast.today)
    val days = forecast.upcomingDays()
    if (days.size > 1) {
        Spacer(Modifier.height(24.dp))
        SectionHeading("Next ${days.size} days") {
            Text(
                "Tap a day for details",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        DailyList(days, forecast.today.date, unit, onOpenDay)
    }
}

/**
 * A section title with something small at its other end ("Updated 8 min ago", "Tap a day for details"); the
 * extra moves under the title when both don't fit on one line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SectionHeading(title: String, extra: @Composable () -> Unit = {}) {
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = PageMargin),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 12.dp).align(Alignment.CenterVertically).semantics { heading() },
        )
        Box(Modifier.align(Alignment.CenterVertically)) { extra() }
    }
}

/**
 * "Updated 8 min ago" from our own fetch time; past 90 minutes it turns amber and suggests pulling to refresh.
 * Spoken with the same words.
 */
@Composable
private fun UpdatedLine(fetchedAt: Instant, now: Instant) {
    val stale = isStale(fetchedAt, now)
    Text(
        formatUpdated(fetchedAt, now) + if (stale) " · pull to refresh" else "",
        style = MaterialTheme.typography.labelMedium,
        color = if (stale) MaterialTheme.weatherColors.attention else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Sunrise, sunset and UV for [day]; skipped when the forecast has none of them. On the Weather page ([explainable])
 * each tile opens its explanation, which describes today; the day page shows the same tiles without one.
 */
@Composable
internal fun SunAndUv(day: DaySummary, explainable: Boolean = true) {
    val daylight = day.daylight
    val uv = day.uvIndexMax
    if (daylight == Daylight.UNKNOWN && uv == null) return
    fun term(t: Term) = t.takeIf { explainable }
    Spacer(Modifier.height(16.dp))
    TileRow {
        when (daylight) {
            Daylight.NORMAL -> {
                val sunrise = day.sunrise!!
                val sunset = day.sunset!!
                StatTile("Sunrise", formatClock(sunrise), Modifier.weight(1f), compact = true, term = term(Term.SUN))
                StatTile(
                    "Sunset", formatClock(sunset), Modifier.weight(1f), compact = true,
                    detail = "next day".takeIf { sunset.toLocalDate() != day.date },
                    term = term(Term.SUN),
                )
            }
            Daylight.POLAR_NIGHT -> StatTile("Daylight", "None", Modifier.weight(2f), detail = "Polar night", term = term(Term.DAYLIGHT))
            Daylight.MIDNIGHT_SUN -> StatTile("Daylight", "24 hours", Modifier.weight(2f), detail = "Midnight sun", term = term(Term.DAYLIGHT))
            Daylight.UNKNOWN -> Unit
        }
        if (uv != null) StatTile("UV index", "${uv.roundToInt()}", Modifier.weight(1f), detail = describeUv(uv), term = term(Term.UV))
    }
}

/** A row of equal-height tiles, so a tile with a detail line doesn't stand taller than its neighbours. */
@Composable
internal fun TileRow(content: @Composable RowScope.() -> Unit) {
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
internal fun DualTemp(
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
 * One row per day: name, icon with the rain chance under it, the low–high range drawn on a bar shared by the
 * whole list (so warmer and cooler days line up visually), and the day's rain or snow total at the end. Low and
 * high each carry the other unit underneath. Every column has one width for the whole list (the widest entry),
 * so the bars start and end at the same x in every row whatever the font size or the values. Days from the
 * eighth on are drawn lighter under a "less certain" rule. Each row opens that day's details.
 */
@Composable
private fun DailyList(days: List<DaySummary>, today: LocalDate, unit: TempUnit, onOpenDay: (LocalDate) -> Unit) {
    val palette = cardIconPalette()
    val rainColor = MaterialTheme.weatherColors.rain
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val listLow = days.minOf { it.lowC }
    val listHigh = days.maxOf { it.highC }
    val type = MaterialTheme.typography
    val lowStyle = type.bodyMedium
    val highStyle = type.titleSmall
    val secondaryStyle = type.labelSmall
    val other = unit.other()
    // Snow totals take two lines ("3 cm" over "snow") so the column stays narrow enough for the bar.
    val totals = days.map { Precip.dayAmount(it, unit) }
    val dayWidth = widestText(days.map { formatDayLabel(it.date, today) }, highStyle)
    val lowWidth = maxOf(
        widestText(days.map { formatDegrees(it.lowC, unit) }, lowStyle),
        widestText(days.map { formatTemp(it.lowC, other) }, secondaryStyle),
    )
    val highWidth = maxOf(
        widestText(days.map { formatDegrees(it.highC, unit) }, highStyle),
        widestText(days.map { formatTemp(it.highC, other) }, secondaryStyle),
    )
    val totalWidth = if (totals.all { it == null }) 0.dp else maxOf(
        widestText(totals.filterNotNull(), secondaryStyle),
        if (days.any { Precip.isSnowDay(it) }) widestText(listOf("snow"), secondaryStyle) else 0.dp,
    )
    val iconWidth = maxOf(26.dp, widestText(listOf("100%"), secondaryStyle))
    val density = LocalDensity.current
    // Line heights scale with the font, so the bar keeps tracking the primary numbers at any font size.
    val primaryLine = with(density) { highStyle.lineHeight.toDp() }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = PageMargin),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        BoxWithConstraints {
        // The totals get their own column at the end of the row while that leaves the bar room to read; on a narrow
        // screen with large text they move to a line of their own under the bar instead.
        val fixed = 32.dp + dayWidth + 8.dp + iconWidth + 12.dp + lowWidth + 8.dp + 8.dp + highWidth
        val totalsAtEnd = totalWidth > 0.dp && maxWidth - fixed - 6.dp - totalWidth >= MIN_BAR
        val barStart = 16.dp + dayWidth + 8.dp + iconWidth + 12.dp + lowWidth + 8.dp
        Column(Modifier.padding(vertical = 6.dp)) {
            days.forEachIndexed { index, day ->
                val lessCertain = index >= LESS_CERTAIN_FROM
                if (index == LESS_CERTAIN_FROM) LessCertainRule()
                val rain = day.precipChance.takeIf { Precip.showDayChance(it) }
                val total = totals[index]
                val snow = Precip.isSnowDay(day)
                val name = formatDayName(day.date, today)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = "Open details", role = Role.Button) { onOpenDay(day.date) }
                        .padding(vertical = 8.dp)
                        // Further out than a week: drawn lighter, the same way for every part of the row.
                        .graphicsLayer { alpha = if (lessCertain) LESS_CERTAIN_ALPHA else 1f }
                        .semantics(mergeDescendants = true) {
                            contentDescription = buildString {
                                append("$name, ${describeWeatherCode(day.code)}, ")
                                append("high ${formatBothUnits(day.highC, unit)}, low ${formatBothUnits(day.lowC, unit)}")
                                if (rain != null) append(", $rain% chance of ${if (snow) "snow" else "rain"}")
                                Precip.dayTotalSpoken(day, unit)?.let { append(", $it") }
                                if (lessCertain) append(", less certain")
                            }
                        },
                ) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
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
                            Text(
                                "$rain%", style = secondaryStyle, maxLines = 1, softWrap = false,
                                color = if (Precip.highlightChance(rain)) rainColor else muted,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    DualTemp(
                        day.lowC, unit,
                        primaryStyle = lowStyle.copy(color = muted),
                        modifier = Modifier.width(lowWidth),
                        alignment = Alignment.End,
                        primaryLine = primaryLine,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.weight(1f).height(primaryLine),
                        contentAlignment = Alignment.Center,
                    ) {
                        RangeBar(day.lowC, day.highC, listLow, listHigh, Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.width(8.dp))
                    DualTemp(
                        day.highC, unit,
                        primaryStyle = highStyle,
                        modifier = Modifier.width(highWidth),
                        alignment = Alignment.Start,
                        primaryLine = primaryLine,
                    )
                    if (totalsAtEnd) {
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.width(totalWidth), horizontalAlignment = Alignment.End) {
                            Box(Modifier.height(primaryLine), contentAlignment = Alignment.CenterEnd) {
                                if (total != null) Text(total, style = secondaryStyle, color = muted, maxLines = 1, softWrap = false)
                            }
                            if (total != null && snow) Text("snow", style = secondaryStyle, color = muted, maxLines = 1, softWrap = false)
                        }
                    }
                }
                if (!totalsAtEnd && total != null) {
                    Text(
                        if (snow) "$total snow" else total,
                        style = secondaryStyle,
                        color = muted,
                        modifier = Modifier.padding(start = barStart, end = 16.dp, top = 2.dp),
                    )
                }
                }
            }
        }
        }
    }
}

/** Below this the range bar is too short to read, so the day totals leave the end of the row. */
private val MIN_BAR = 48.dp

/** How much lighter the days beyond a week are drawn. */
private const val LESS_CERTAIN_ALPHA = 0.6f

/** A hairline with "less certain" in it, above the days beyond a week. Read out with each of those days instead. */
@Composable
private fun LessCertainRule() {
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(line))
        Text(
            "less certain",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(line))
    }
}

/** Width of the widest of [texts] set in [style] on one line, so a column can use one width for every row. */
@Composable
internal fun widestText(texts: List<String>, style: TextStyle): Dp {
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

/**
 * A labelled value on a card. [detail] is a small line under the value; [direction] (degrees, where the wind comes
 * from) adds a small arrow and "from the SW" above it. With a [term], tapping opens its explanation.
 */
@Composable
internal fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    compact: Boolean = false,
    term: Term? = null,
    direction: Double? = null,
) {
    val explain = LocalExplain.current
    val onClick = if (term != null && explain != null) ({ explain(term) }) else null
    val tileModifier = modifier.fillMaxHeight().semantics(mergeDescendants = true) {
        contentDescription = listOfNotNull(label, value, direction?.let { formatWindFromSpoken(it) }, detail).joinToString(" ")
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onClick != null) {
        // Card's own click has no role; TalkBack should say "button" and offer "Explain".
        val explainable = tileModifier.semantics {
            role = Role.Button
            onClick(label = "Explain", action = null)
        }
        Card(onClick = onClick, modifier = explainable, colors = colors) {
            StatTileContent(label, value, detail, direction, compact, explainable = true)
        }
    } else {
        Card(tileModifier, colors = colors) { StatTileContent(label, value, detail, direction, compact, explainable = false) }
    }
}

@Composable
private fun StatTileContent(label: String, value: String, detail: String?, direction: Double?, compact: Boolean, explainable: Boolean) {
    run {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // The glyph is part of the label's text, so on a narrow tile at a large font size the line breaks
            // fall between words ("Feels" / "like ⓘ") rather than the glyph squeezing the label mid-word.
            val labelStyle = MaterialTheme.typography.labelMedium
            val glyphTint = MaterialTheme.colorScheme.outline
            Text(
                buildAnnotatedString {
                    append(label)
                    if (explainable) {
                        append(' ')
                        appendInlineContent(INFO_GLYPH)
                    }
                },
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                inlineContent = mapOf(
                    INFO_GLYPH to InlineTextContent(
                        Placeholder(labelStyle.fontSize, labelStyle.fontSize, PlaceholderVerticalAlign.TextCenter),
                    ) { Icon(Icons.Outlined.Info, contentDescription = null, Modifier.fillMaxSize(), tint = glyphTint) },
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (direction != null) WindFrom(direction)
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

/** A small arrow showing where the wind blows, then "from the SW" (where it comes from, as forecasts say it). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WindFrom(degrees: Double, style: TextStyle = MaterialTheme.typography.labelSmall) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val text = formatWindFrom(degrees)
    // The arrow is part of the text, so it wraps with it and grows with the font size.
    Text(
        buildAnnotatedString {
            appendInlineContent(ARROW_GLYPH)
            append(' ')
            append(text)
        },
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        inlineContent = mapOf(
            ARROW_GLYPH to InlineTextContent(
                Placeholder(style.fontSize, style.fontSize, PlaceholderVerticalAlign.TextCenter),
            ) { WindArrow(degrees, color, Modifier.fillMaxSize()) },
        ),
    )
}

/** Inline-content id of the wind arrow in "↗ from the SW". */
private const val ARROW_GLYPH = "arrow"

/**
 * An arrow pointing the way the wind blows: downwind, opposite [fromDegrees], with north up. Hidden from screen
 * readers; the words carry the direction.
 */
@Composable
internal fun WindArrow(fromDegrees: Double, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.rotate((fromDegrees + 180).toFloat())) {
        val w = size.width
        val h = size.height
        val stroke = (w * 0.13f).coerceAtLeast(1.dp.toPx())
        // Shaft from the bottom to near the top, and an arrowhead at the top.
        drawLine(color, Offset(w / 2, h * 0.92f), Offset(w / 2, h * 0.14f), strokeWidth = stroke, cap = StrokeCap.Round)
        val head = Path().apply {
            moveTo(w / 2, h * 0.04f)
            lineTo(w * 0.82f, h * 0.42f)
            lineTo(w * 0.18f, h * 0.42f)
            close()
        }
        drawPath(head, color)
    }
}

/**
 * A row of hour cards: label, icon, temperature in both units, then muted lines for the feels-like temperature
 * (when it's 3° or more away), the chance of rain (10% and up, blue from 40%) and the amount (0.1 mm and up). A line
 * is reserved in every card once any card needs it, so the cards stay level; a line nobody needs isn't drawn.
 * [showPrecip] false leaves the rain lines out (the day page has its own rain card). The first card is highlighted
 * when [highlightFirst] (it's "Now").
 */
@Composable
internal fun HourStrip(
    hours: List<HourForecast>,
    unit: TempUnit,
    label: (Int, HourForecast) -> String,
    night: (Int, HourForecast) -> Boolean,
    showPrecip: Boolean = true,
    highlightFirst: Boolean = true,
) {
    val type = MaterialTheme.typography
    val labels = hours.mapIndexed(label)
    val feels = hours.map { h -> feelsLikeWorthShowing(h.tempC, h.feelsLikeC, unit)?.let { "feels ${formatDegrees(it, unit)}" } }
    val chances = hours.map { h -> h.precipChance.takeIf { showPrecip && Precip.showHourChance(it) } }
    val amounts = hours.map { h -> if (showPrecip) Precip.hourAmount(h, unit) else null }
    val feelsLine = feels.any { it != null }
    val chanceLine = chances.any { it != null }
    val amountLine = amounts.any { it != null }
    // One width for every card (the widest label or value), so large fonts widen the strip evenly.
    val cardWidth = maxOf(
        52.dp,
        widestText(labels, type.labelMedium),
        widestText(hours.map { formatDegrees(it.tempC, unit) }, type.titleMedium),
        widestText(hours.map { formatTemp(it.tempC, unit.other()) } + feels.filterNotNull() + amounts.filterNotNull(), type.labelSmall),
    )
    val palette = cardIconPalette()
    val rainColor = MaterialTheme.weatherColors.rain
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // The strip runs edge to edge so a partly visible last tile hints that it scrolls.
    LazyRow(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = PageMargin),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(hours) { i, hour ->
            val chance = chances[i]
            Card(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(
                        labels[i],
                        formatBothUnits(hour.tempC, unit),
                        feels[i]?.let { "feels like ${formatBothUnits(hour.feelsLikeC!!, unit)}" },
                        chance?.let { "$it% chance of ${if (Precip.isSnowHour(hour)) "snow" else "rain"}" },
                        if (showPrecip) Precip.hourAmountSpoken(hour, unit) else null,
                    ).joinToString(", ")
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (i == 0 && highlightFirst) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer
                ),
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 12.dp).width(cardWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(labels[i], style = type.labelMedium, maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(8.dp))
                    WeatherIcon(hour.code, night(i, hour), palette, size = 30.dp)
                    Spacer(Modifier.height(8.dp))
                    DualTemp(hour.tempC, unit, primaryStyle = type.titleMedium)
                    if (feelsLine) {
                        Text(feels[i] ?: "", style = type.labelSmall, color = muted, maxLines = 1, softWrap = false)
                    }
                    if (chanceLine || amountLine) Spacer(Modifier.height(4.dp))
                    if (chanceLine) {
                        Text(
                            chance?.let { "$it%" } ?: "",
                            style = type.labelSmall,
                            color = if (chance != null && Precip.highlightChance(chance)) rainColor else muted,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    if (amountLine) {
                        Text(amounts[i] ?: "", style = type.labelSmall, color = muted, maxLines = 1, softWrap = false)
                    }
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
internal fun StateCard(
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
