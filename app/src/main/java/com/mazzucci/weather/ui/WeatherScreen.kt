@file:OptIn(ExperimentalMaterial3Api::class)

package com.mazzucci.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.HourForecast
import com.mazzucci.weather.domain.Place
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.describeWeatherCode
import com.mazzucci.weather.domain.formatDegrees
import com.mazzucci.weather.domain.formatHour
import com.mazzucci.weather.domain.formatTemp
import com.mazzucci.weather.domain.formatWind
import com.mazzucci.weather.domain.other
import com.mazzucci.weather.narration.Narration
import com.mazzucci.weather.narration.NarrationSource

/** Stateless main screen: one swipeable page per place. Kept free of ViewModel so screenshot tests can render it. */
@Composable
fun WeatherPagerScreen(
    state: WeatherUiState,
    pagerState: PagerState,
    onRefresh: (key: String) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                actions = {
                    IconButton(onClick = { state.pages.getOrNull(pagerState.currentPage)?.let { onRefresh(it.key) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSearch) { Icon(Icons.Default.Add, contentDescription = "Add place") }
                    IconButton(onClick = onOpenPlaces) { Icon(Icons.Default.List, contentDescription = "Places") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.pages.isEmpty()) {
                EmptyState(onOpenSearch, Modifier.weight(1f))
            } else {
                HorizontalPager(pagerState, Modifier.weight(1f)) { index ->
                    WeatherPage(
                        page = state.pages[index],
                        unit = state.settings.primaryUnit,
                        onRetry = { onRefresh(state.pages[index].key) },
                        onRequestPermission = onRequestPermission,
                        onOpenSearch = onOpenSearch,
                    )
                }
                if (state.pages.size > 1) PageDots(state.pages.size, pagerState.currentPage)
            }
        }
    }
}

@Composable
private fun EmptyState(onOpenSearch: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No places yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Search for a city, or turn on current location in Places.", textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onOpenSearch) { Text("Add a place") }
    }
}

@Composable
fun WeatherPage(
    page: PageUi,
    unit: TempUnit,
    onRetry: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PlaceHeader(page)
        Spacer(Modifier.height(16.dp))
        when (val c = page.content) {
            PageContent.Loading -> CenteredBlock { CircularProgressIndicator() }
            PageContent.NeedsPermission -> CenteredBlock {
                Text("Location permission is needed to show your local weather.", textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onRequestPermission) { Text("Grant permission") }
                TextButton(onOpenSearch) { Text("Search for a place instead") }
            }
            is PageContent.Failed -> CenteredBlock {
                Text(c.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onRetry) { Text("Try again") }
            }
            is PageContent.Loaded -> ForecastContent(c.forecast, c.summary, unit)
        }
    }
}

@Composable
private fun PlaceHeader(page: PageUi) {
    val isCurrent = page.key == Place.CURRENT_LOCATION_ID
    Text(
        page.place?.name ?: "My location",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    // Until the device location resolves there's no name yet, so the title alone says it.
    val detail = if (isCurrent) "Current location".takeIf { page.place != null } else page.place?.detail
    if (detail != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isCurrent) {
                Icon(Icons.Default.LocationOn, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CenteredBlock(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun ForecastContent(forecast: Forecast, summary: Narration, unit: TempUnit) {
    val cur = forecast.current
    SummaryCard(summary)
    Spacer(Modifier.height(24.dp))

    Row(verticalAlignment = Alignment.Bottom) {
        Text(formatTemp(cur.tempC, unit), fontSize = 88.sp, fontWeight = FontWeight.Light, lineHeight = 88.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            formatTemp(cur.tempC, unit.other()),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp),
        )
    }
    Text(cur.description, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "H ${formatDegrees(forecast.today.highC, unit)}  ·  L ${formatDegrees(forecast.today.lowC, unit)}" +
            "  ·  Rain ${forecast.today.precipChance}%",
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(20.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Feels like", formatDegrees(cur.feelsLikeC, unit), Modifier.weight(1f))
        StatTile("Humidity", "${cur.humidity}%", Modifier.weight(1f))
        StatTile("Wind", formatWind(cur.windKmh, unit), Modifier.weight(1f))
    }
    Spacer(Modifier.height(20.dp))

    Text(
        "Next ${forecast.nextHours.size} hours",
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    HourlyStrip(forecast.nextHours, unit)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun SummaryCard(summary: Narration) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(summary.text, style = MaterialTheme.typography.bodyLarge)
            if (summary.source == NarrationSource.GEMMA) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "✦ Written by Gemma on this device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun HourlyStrip(hours: List<HourForecast>, unit: TempUnit) {
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(hours) { i, hour ->
            Card {
                Column(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp).width(52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (i == 0) "Now" else formatHour(hour.time),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(formatDegrees(hour.tempC, unit), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${hour.precipChance}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hour.precipChance >= 30) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        shortCondition(hour.code),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** One word that fits under an hourly tile. */
private fun shortCondition(code: Int): String = when (code) {
    0, 1 -> "Clear"
    2 -> "Clouds"
    3 -> "Overcast"
    45, 48 -> "Fog"
    95, 96, 99 -> "Storm"
    else -> describeWeatherCode(code).substringBefore(' ')
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (i == current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}
