package app.daybreak.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.isSpecified
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.app.Activity
import java.time.LocalDate
import app.daybreak.domain.Forecast

/** The bottom bar's sections. Home is where the app opens. */
enum class Tab(val label: String) { Home("Home"), Weather("Weather"), Clocks("Clocks"), Settings("Settings") }

/** Full-screen tasks opened from a tab; the bottom bar hides while one is open. */
private enum class Overlay { Search, Places, Day }

/** What the day overlay shows for the page [key] and [date] it was opened with. */
internal sealed interface DayOverlay {
    data class Show(val page: PageUi, val forecast: Forecast, val date: LocalDate) : DayOverlay
    /** The page is still on its way: after the process was stopped, the state is read and the forecast fetched again. */
    data object Wait : DayOverlay
    /** The place was removed, its forecast failed, or a refresh moved the forecast past that day. */
    data object Close : DayOverlay
}

internal fun dayOverlay(state: WeatherUiState, key: String?, date: LocalDate?): DayOverlay {
    if (date == null) return DayOverlay.Close
    if (!state.ready) return DayOverlay.Wait
    val page = state.pages.firstOrNull { it.key == key } ?: return DayOverlay.Close
    return when (val content = page.content) {
        is PageContent.Loaded -> if (content.forecast.day(date) != null) DayOverlay.Show(page, content.forecast, date) else DayOverlay.Close
        PageContent.Loading -> DayOverlay.Wait
        else -> DayOverlay.Close
    }
}

/** Wires the ViewModel to the stateless screens and owns navigation and system pickers/prompts. */
@Composable
fun WeatherApp(vm: WeatherViewModel, clocksVm: ClocksViewModel? = null) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val clocks = clocksVm?.clocks?.collectAsStateWithLifecycle()?.value.orEmpty()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    // Set while Search is adding a clock rather than a page.
    var addingClock by rememberSaveable { mutableStateOf(false) }
    // Whether Search was opened from Places (its "Add"), so back returns there.
    var searchFromPlaces by rememberSaveable { mutableStateOf(false) }
    // The page and date (ISO) of the day whose details are open.
    var dayPage by rememberSaveable { mutableStateOf<String?>(null) }
    var dayDate by rememberSaveable { mutableStateOf<String?>(null) }
    // White status-bar icons over Home's and Weather's sky (and the strip that replaces it when scrolled); the
    // theme's own on Clocks, Settings and the overlays.
    val view = LocalView.current
    val dark = MaterialTheme.isDark
    val onSky = (overlay == null && (tab == Tab.Home || tab == Tab.Weather)) || overlay == Overlay.Day
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark && !onSky }
        }
    }
    // Each tab keeps its own scroll and state while another is shown.
    val tabStates = rememberSaveableStateHolder()
    var scrollTo by rememberSaveable { mutableStateOf<Int?>(null) }
    val pagerState = rememberPagerState { state.pages.size }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(), vm::onLocationPermissionResult,
    )
    val requestPermission = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    val enableCurrentLocation = {
        vm.setUseCurrentLocation(true)
        if (vm.needsLocationPermission()) requestPermission()
    }
    // .task files have no registered MIME type, so accept anything and validate the name on import.
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importModel(it.toString()) }
    }

    LaunchedEffect(Unit) {
        if (vm.shouldRequestLocationOnStart()) requestPermission()
    }
    LaunchedEffect(tab, overlay, scrollTo, state.pages.size) {
        val target = scrollTo ?: return@LaunchedEffect
        if (tab == Tab.Weather && overlay == null && target < state.pages.size) {
            pagerState.scrollToPage(target)
            scrollTo = null
        }
    }

    // An overlay goes back to the one it came from, or to its tab; a tab goes back to Home, and Home exits.
    val back = {
        val from = overlay
        overlay = when {
            from == Overlay.Search && searchFromPlaces -> Overlay.Places
            else -> null
        }
        if (from == Overlay.Search) {
            searchFromPlaces = false
            addingClock = false
        }
    }
    BackHandler(enabled = overlay != null || tab != Tab.Home) {
        if (overlay != null) {
            vm.clearSearch()
            back()
        } else {
            tab = Tab.Home
        }
    }
    val openSearch = {
        searchFromPlaces = false
        overlay = Overlay.Search
    }

    when (overlay) {
        Overlay.Day -> {
            val date = dayDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            when (val day = dayOverlay(state, dayPage, date)) {
                is DayOverlay.Show -> DayScreen(
                    placeName = day.page.place?.name ?: "My location",
                    forecast = day.forecast,
                    date = day.date,
                    unit = state.settings.primaryUnit,
                    onBack = { overlay = null },
                )
                DayOverlay.Wait -> DayLoading(onBack = { overlay = null })
                DayOverlay.Close -> LaunchedEffect(Unit) { overlay = null }
            }
        }
        Overlay.Search -> SearchScreen(
            search = state.search,
            savedIds = if (addingClock) clocks.map { it.id }.toSet() else state.savedPlaces.map { it.id }.toSet(),
            onQueryChange = vm::onSearchQueryChange,
            onPick = { place ->
                if (addingClock) {
                    // A place without a time zone this phone knows can't be a clock: stay on the search.
                    if (app.daybreak.domain.Clock.of(place) != null) {
                        clocksVm?.add(place)
                        vm.clearSearch()
                        addingClock = false
                        overlay = null
                    }
                } else {
                    scrollTo = vm.addPlace(place)
                    searchFromPlaces = false
                    overlay = null
                    tab = Tab.Weather
                }
            },
            onBack = {
                vm.clearSearch()
                back()
            },
            title = if (addingClock) "Add a clock" else "Add a place",
        )
        Overlay.Places -> PlacesScreen(
            places = state.savedPlaces,
            useCurrentLocation = state.settings.useCurrentLocation,
            onUseCurrentLocationChange = { enabled ->
                if (enabled) enableCurrentLocation() else vm.setUseCurrentLocation(false)
            },
            onMove = vm::movePlace,
            onRemove = { vm.removePlace(it.id) },
            onAdd = {
                searchFromPlaces = true
                overlay = Overlay.Search
            },
            onBack = { overlay = null },
        )
        null -> Scaffold(
            bottomBar = { DaybreakNavigationBar(tab) { tab = it } },
            // Each tab handles the status bar itself (Home and Weather draw their sky behind it).
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            // Side insets (landscape navigation buttons, cutouts) for every tab; the top is each tab's own.
            Box(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                tabStates.SaveableStateProvider(tab) {
                    when (tab) {
                        Tab.Home -> HomeScreen(
                            state = state,
                            onOpenWeather = { index ->
                                scrollTo = index
                                tab = Tab.Weather
                            },
                            onRefresh = vm::refresh,
                            onRequestPermission = requestPermission,
                            onOpenSearch = openSearch,
                            onOpenSettings = { tab = Tab.Settings },
                        )
                        Tab.Weather -> WeatherPagerScreen(
                            state = state,
                            pagerState = pagerState,
                            onRefresh = vm::refresh,
                            onRequestPermission = requestPermission,
                            onUseCurrentLocation = enableCurrentLocation,
                            onOpenSearch = openSearch,
                            onOpenPlaces = { overlay = Overlay.Places },
                            onOpenDay = { key, date ->
                                dayPage = key
                                dayDate = date.toString()
                                overlay = Overlay.Day
                            },
                        )
                        Tab.Clocks -> ClocksScreen(
                            clocks = clocks,
                            onAdd = {
                                addingClock = true
                                searchFromPlaces = false
                                overlay = Overlay.Search
                            },
                            onRemove = { clocksVm?.remove(it) },
                            onMove = { from, to -> clocksVm?.move(from, to) },
                        )
                        Tab.Settings -> SettingsTab(state, vm, modelPicker::launch)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    state: WeatherUiState,
    vm: WeatherViewModel,
    launchModelPicker: (Array<String>) -> Unit,
) {
    SettingsScreen(
            settings = state.settings,
            modelStatus = state.modelStatus,
            onUnitChange = vm::setPrimaryUnit,
            onGemmaEnabledChange = vm::setGemmaEnabled,
            onMemesEnabledChange = vm::setMemesEnabled,
            onSkyEnabledChange = vm::setSkyEnabled,
            onComingUpEnabledChange = vm::setComingUpEnabled,
            onAddPersonalDate = vm::addPersonalDate,
            onRemovePersonalDate = vm::removePersonalDate,
            onActivityChange = vm::setActivity,
            onDownloadModel = vm::downloadModel,
            onCancelDownload = vm::cancelModelDownload,
            onImportModel = { launchModelPicker(arrayOf("*/*")) },
            onRemoveModel = vm::removeModel,
            onBack = null,
        )
}

/** The bar's label style, growing with the font size setting only up to 1.3x, so four labels always fit. */
@Composable
private fun cappedLabel(): androidx.compose.ui.text.TextStyle {
    val style = MaterialTheme.typography.labelMedium
    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    if (scale <= 1.3f) return style
    val f = 1.3f / scale
    return style.copy(fontSize = style.fontSize * f, lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * f else style.lineHeight)
}

/** Home · Weather · Clocks · Settings, labels always shown, flat on the card colour like the cards themselves. */
@Composable
fun DaybreakNavigationBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        Tab.entries.forEach { t ->
            val on = t == selected
            NavigationBarItem(
                selected = on,
                onClick = { onSelect(t) },
                // One line always; at the largest font sizes the labels stop growing at 1.3x rather than breaking.
                label = { Text(t.label, maxLines = 1, softWrap = false, style = cappedLabel()) },
                icon = {
                    when (t) {
                        Tab.Home -> Icon(if (on) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = null)
                        Tab.Weather -> WeatherTabIcon()
                        Tab.Clocks -> ClocksTabIcon(on)
                        Tab.Settings -> Icon(if (on) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = null)
                    }
                },
            )
        }
    }
}
