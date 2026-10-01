package app.daybreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import app.daybreak.domain.Place
import app.daybreak.domain.Precip
import app.daybreak.domain.TempUnit
import app.daybreak.domain.countryCodeOf
import app.daybreak.domain.describeWeatherCode
import app.daybreak.domain.formatBothUnits
import app.daybreak.domain.formatDegrees
import app.daybreak.domain.upcomingPersonalDates
import app.daybreak.domain.describeSky
import app.daybreak.domain.moonPhase
import app.daybreak.domain.weekendDays
import app.daybreak.domain.WeekOutlook
import app.daybreak.domain.keepUnitsTogether
import app.daybreak.domain.weekOutlook
import app.daybreak.domain.HabitsSummary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The page Home follows (the glance, the holidays and the meme): the first page, unless it's
 * the current location still waiting for permission and a saved place can stand in. A failed page stays, so a
 * failed refresh shows its error (and "Try again") rather than quietly switching Home to another city. -1 with no
 * pages at all.
 */
fun glancePageIndex(pages: List<PageUi>): Int {
    if (pages.isEmpty()) return -1
    val ok = pages.indexOfFirst { it.content !is PageContent.NeedsPermission }
    return if (ok >= 0) ok else 0
}

/**
 * Home: the day at a glance. A greeting on the sky of the glance place, the weather glance (which opens that place
 * on the Weather tab), then the personal cards in a fixed order: today's habits, what's coming up, tonight's sky,
 * and the meme last since it's the tallest and the least to act on. A card that's off or has nothing to say isn't
 * shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: WeatherUiState,
    onOpenWeather: (pageIndex: Int) -> Unit,
    onRefresh: (key: String) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    /** Drives the date and greeting; fixed in screenshot tests, otherwise the clock, ticking each minute. */
    now: LocalDateTime? = null,
    /** The phone's zone, for tonight's sky; fixed in screenshot tests. */
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    /** The habits worked out for today (by the ViewModel); null or empty for none. */
    habits: HabitsSummary? = null,
    celebration: Celebration? = null,
    onLogHabit: (String) -> Unit = {},
    onUndoHabit: (String) -> Unit = {},
    onCelebrationShown: (Long) -> Unit = {},
    habitsUndoHint: Boolean = false,
    onOpenHabits: () -> Unit = {},
) {
    val now = now ?: rememberMinuteClock().atZone(zone).toLocalDateTime()
    val index = glancePageIndex(state.pages)
    val page = state.pages.getOrNull(index)
    val loaded = page?.content as? PageContent.Loaded
    val forecast = loaded?.forecast
    val unit = state.settings.primaryUnit
    val dark = MaterialTheme.isDark
    val gradient = forecast?.let { heroGradient(skyOf(it.current.code), it.isNightNow, dark) } ?: neutralGradient(dark)
    val refreshing = page?.refreshing == true && loaded != null
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { page?.let { onRefresh(it.key) } },
        state = refreshState,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
            )
        },
    ) {
        val scroll = rememberScrollState()
        var heroBottom by remember { mutableStateOf(Int.MAX_VALUE) }
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Hero(gradient, Modifier.onSizeChanged { heroBottom = it.height }, top = 16.dp, alignment = Alignment.Start) {
                Text(
                    // "Monday, September 28" in the phone's own language and order.
                    now.format(DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd"), Locale.getDefault())),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(2.dp))
                Text(greeting(now.hour), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            }
            Spacer(Modifier.height(16.dp))
            WeatherGlance(
                page, unit,
                onOpen = { onOpenWeather(index) },
                onRetry = { page?.let { onRefresh(it.key) } },
                onRequestPermission = onRequestPermission,
                onOpenSearch = onOpenSearch,
                modifier = Modifier.padding(horizontal = PageMargin),
            )
            // Today's habits, between the weather and what's coming up; only once there's a habit.
            val showHabits = state.settings.habitsOnHome && !habits?.stats.isNullOrEmpty()
            if (showHabits && habits != null) {
                CelebrationTimer(celebration, onCelebrationShown)
                SectionHeading("Today's habits")
                HabitsHomeCard(
                    habits, celebration, onLogHabit, onUndoHabit, Modifier.padding(horizontal = PageMargin),
                    undoHint = habitsUndoHint, onOpenHabits = onOpenHabits,
                )
            }
            HomeCards(state, page, loaded, now, zone)
            // Only when every card is switched off (not while they're waiting for a forecast), or habits have none.
            val settings = state.settings
            if (!settings.comingUpEnabled && !settings.skyEnabled && !settings.memesEnabled && !showHabits) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Turn on more cards in Settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = PageMargin - 4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button, onClick = onOpenSettings)
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
        StatusBarScrim(scroll, heroBottom, gradient.first())
    }
}

/** The personal cards under the glance, in order. */
@Composable
private fun HomeCards(state: WeatherUiState, page: PageUi?, loaded: PageContent.Loaded?, now: LocalDateTime, zone: java.time.ZoneId) {
    val settings = state.settings
    val unit = settings.primaryUnit
    val forecast = loaded?.forecast
    val today = forecast?.current?.time?.toLocalDate() ?: now.toLocalDate()
    val holidays = loaded?.holidays.orEmpty()
    val weekend = weekendDays(page?.place?.let(::countryCodeOf))

    // The place's holidays and seasons, with your own next dates among them.
    val upcoming = remember(loaded?.comingUp, settings.personalDates, settings.comingUpEnabled, holidays, weekend, today) {
        if (!settings.comingUpEnabled) emptyList()
        else (loaded?.comingUp.orEmpty() + upcomingPersonalDates(today, settings.personalDates, holidays, weekend)).sortedBy { it.date }
    }
    if (upcoming.isNotEmpty()) {
        SectionHeading("Coming up")
        ComingUpCard(upcoming, forecast, unit, Modifier.padding(horizontal = PageMargin), today = today)
    }

    if (settings.skyEnabled) {
        val instant = now.atZone(zone).toInstant()
        // The viewer's hemisphere: the Home place's, or the northern one until it's known.
        val latitude = page?.place?.latitude ?: 45.0
        val phase = remember(instant) { moonPhase(instant) }
        val sky = remember(instant, forecast, latitude) { describeSky(instant, zone, forecast, latitude) }
        SectionHeading("Tonight's sky")
        SkyCard(phase, sky, Modifier.padding(horizontal = PageMargin), southern = latitude < 0)
    }

    val meme = loaded?.meme
    if (meme != null) {
        SectionHeading("Today's weather meme")
        MemeCard(meme, Modifier.padding(horizontal = PageMargin))
    }
}

@Composable
private fun SectionHeading(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = PageMargin).semantics { heading() },
    )
    Spacer(Modifier.height(12.dp))
}

/** "Good morning" 05–11, "Good afternoon" 12–17, "Good evening" 18–21, "Good night" 22–04. */
fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..21 -> "Good evening"
    else -> "Good night"
}

/**
 * The glance place's weather in one card: icon, place, "Partly cloudy · ↑74° ↓56° · Rain 60%", and the
 * temperature in both units. Tapping opens that place on the Weather tab. While there's no forecast the same
 * frame says why, with the one thing to do about it.
 */
@Composable
private fun WeatherGlance(
    page: PageUi?,
    unit: TempUnit,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val content = page?.content
    if (content is PageContent.Loaded) {
        val f = content.forecast
        val name = page.place?.name ?: "My location"
        val today = f.today
        // The same classifier as everywhere else: the chance from 20%, and "Snow" when it's mostly snow.
        val todayRain = remember(f) { Precip.dayRain(f, today.date) }
        val rain = todayRain.chance.takeIf { !todayRain.dry && Precip.showDayChance(it) }
        val condition = describeWeatherCode(f.current.code)
        val range = "↑${formatDegrees(today.highC, unit)} ↓${formatDegrees(today.lowC, unit)}"
        // Don't say rain twice: when the sky already is rain (or snow, or storms), the chance stands alone.
        val chance = rain?.let { if (condition in DRY_SKIES) "${todayRain.noun} $it%" else "$it% chance" }
        val line = listOfNotNull(condition, range, chance).joinToString(" · ")
        // The "This week" today line, quietly under the glance; the weekend doesn't matter to it.
        val outlook = remember(f, unit) { weekOutlook(f, unit) }
        val spoken = listOfNotNull(
            name,
            formatBothUnits(f.current.tempC, unit),
            describeWeatherCode(f.current.code).lowercase(),
            "high ${formatDegrees(today.highC, unit)}, low ${formatDegrees(today.lowC, unit)}",
            rain?.let { "$it percent chance of ${todayRain.noun.lowercase()}" },
        ).joinToString(", ") + ". ${outlook.today.spoken}. Opens Weather."
        Card(
            onClick = onOpen,
            modifier = modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            },
            colors = colors,
        ) {
            Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                WeatherIcon(f.current.code, night = f.isNightNow, cardIconPalette(), size = 36.dp, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (page.key == Place.CURRENT_LOCATION_ID) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // One line when it fits; otherwise the chance moves to a second line whole, so no line ends on a dot.
                    var wrap by remember(line) { mutableStateOf(false) }
                    Text(
                        if (wrap && chance != null) "$condition · $range\n$chance" else line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (wrap) 2 else 1,
                        softWrap = wrap,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!wrap && it.hasVisualOverflow) wrap = true },
                    )
                }
                Spacer(Modifier.width(12.dp))
                DualTemp(f.current.tempC, unit, MaterialTheme.typography.headlineMedium, alignment = Alignment.End)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            OutlookGlanceLine(outlook)
        }
        return
    }
    Card(modifier.fillMaxWidth(), colors = colors) {
        when (content) {
            null -> GlanceMessage(Icons.Default.Search, "Pick a place to start", "Search for a city, or use your location.") {
                TextButton(onOpenSearch) { Text("Add a place") }
            }
            PageContent.NeedsPermission -> GlanceMessage(Icons.Default.LocationOn, "Where are you?", "Allow location to see the weather here.") {
                TextButton(onRequestPermission) { Text("Allow location") }
                TextButton(onOpenSearch) { Text("Search for a place instead") }
            }
            is PageContent.Failed -> GlanceMessage(
                Icons.Default.Warning, "Couldn't load the weather", content.message, MaterialTheme.colorScheme.error,
            ) {
                TextButton(onRetry) { Text("Try again") }
            }
            else -> {
                // Loading: bars in the shape of the loaded card, so nothing jumps when it lands.
                val skeleton = MaterialTheme.weatherColors.skeleton
                Row(
                    Modifier.padding(16.dp).heightIn(min = 40.dp).fillMaxWidth().semantics { contentDescription = "Loading the weather" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(skeleton))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(120.dp).height(16.dp).clip(CircleShape).background(skeleton))
                        Box(Modifier.width(200.dp).height(14.dp).clip(CircleShape).background(skeleton))
                    }
                    Box(Modifier.size(44.dp, 28.dp).clip(MaterialTheme.shapes.small).background(skeleton))
                }
            }
        }
    }
}

/**
 * The outlook's today line under the glance, one line, starting under the place name: a dot in the tier's colour
 * (centred under the weather icon) and the line in bodySmall.
 */
@Composable
private fun OutlookGlanceLine(outlook: WeekOutlook) {
    val tier = outlook.focus?.tier
    Row(Modifier.padding(start = 30.dp, top = 6.dp, end = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (tier != null) tierColor(tier) else MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.width(26.dp))
        Text(
            keepUnitsTogether(outlook.today.text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Conditions that aren't precipitation, so the rain chance needs its word. */
private val DRY_SKIES = setOf("Clear sky", "Mainly clear", "Partly cloudy", "Overcast", "Fog", "Unknown")

@Composable
private fun GlanceMessage(
    icon: ImageVector,
    title: String,
    text: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    actions: @Composable () -> Unit,
) {
    Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
        Icon(icon, contentDescription = null, Modifier.size(28.dp), tint = tint)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 4.dp)) { actions() }
        }
    }
}
