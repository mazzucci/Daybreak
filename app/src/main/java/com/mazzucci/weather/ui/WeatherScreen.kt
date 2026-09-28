@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazzucci.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.HourForecast
import com.mazzucci.weather.domain.Place
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.formatDegrees
import com.mazzucci.weather.domain.formatHour
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
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val dark = MaterialTheme.isDark
    val content = page.content
    val loaded = content as? PageContent.Loaded
    val night = loaded?.let { isNight(it.forecast.current.time) } ?: false
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
                    is PageContent.Loaded -> BodyForecast(content.forecast, unit, night)
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HeroPill("High", formatDegrees(forecast.today.highC, unit))
        HeroPill("Low", formatDegrees(forecast.today.lowC, unit))
        HeroPill("Rain", "${forecast.today.precipChance}%")
    }
}

@Composable
private fun HeroPill(label: String, value: String) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
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
private fun BodyForecast(forecast: Forecast, unit: TempUnit, night: Boolean) {
    val cur = forecast.current
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth().padding(horizontal = PageMargin), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Feels like", formatDegrees(cur.feelsLikeC, unit), Modifier.weight(1f))
        StatTile("Humidity", "${cur.humidity}%", Modifier.weight(1f))
        StatTile("Wind", formatWind(cur.windKmh, unit), Modifier.weight(1f))
    }
    Spacer(Modifier.height(24.dp))
    Text(
        "Next ${forecast.nextHours.size} hours",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = PageMargin),
    )
    Spacer(Modifier.height(12.dp))
    HourlyStrip(forecast.nextHours, unit, night)
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$label $value" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun HourlyStrip(hours: List<HourForecast>, unit: TempUnit, nightNow: Boolean) {
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
            val night = if (i == 0) nightNow else isNight(hour.time)
            Card(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "$label, ${formatDegrees(hour.tempC, unit)}, ${hour.precipChance}% chance of rain"
                },
                colors = CardDefaults.cardColors(
                    containerColor = if (i == 0) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainer
                ),
            ) {
                Column(
                    Modifier.padding(horizontal = 10.dp, vertical = 12.dp).width(52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    WeatherIcon(hour.code, night, palette, size = 30.dp)
                    Spacer(Modifier.height(8.dp))
                    Text(formatDegrees(hour.tempC, unit), style = MaterialTheme.typography.titleMedium)
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
            repeat(5) { Box(Modifier.width(72.dp).height(124.dp).clip(shape).background(color)) }
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
