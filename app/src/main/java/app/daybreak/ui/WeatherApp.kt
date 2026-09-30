package app.daybreak.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.daybreak.domain.CommuteEnd

/** The bottom bar's sections. Home is where the app opens. */
enum class Tab(val label: String) { Home("Home"), Weather("Weather"), Settings("Settings") }

/** Full-screen tasks opened from a tab; the bottom bar hides while one is open. */
private enum class Overlay { Search, Places }

/** Wires the ViewModel to the stateless screens and owns navigation and system pickers/prompts. */
@Composable
fun WeatherApp(vm: WeatherViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    // Each tab keeps its own scroll and state while another is shown.
    val tabStates = rememberSaveableStateHolder()
    var scrollTo by rememberSaveable { mutableStateOf<Int?>(null) }
    // Set while the search screen is choosing a commute end rather than adding a page.
    var searchingFor by rememberSaveable { mutableStateOf<CommuteEnd?>(null) }
    // The commute end waiting on the location prompt's answer.
    var locatingFor by rememberSaveable { mutableStateOf<CommuteEnd?>(null) }
    val pagerState = rememberPagerState { state.pages.size }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(), vm::onLocationPermissionResult,
    )
    val requestPermission = { permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    val commutePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // A refusal still goes through, so the row says it couldn't get the location.
        locatingFor?.let(vm::setCommutePlaceHere)
        locatingFor = null
        if (granted && state.settings.useCurrentLocation) vm.onLocationPermissionResult(true)
    }
    val commutePlaceHere = { end: CommuteEnd ->
        if (vm.needsLocationPermission()) {
            locatingFor = end
            commutePermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            vm.setCommutePlaceHere(end)
        }
    }
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

    // White status bar icons over Home's and Weather's sky; the theme's own on the plain tabs and overlays.
    val view = LocalView.current
    val dark = MaterialTheme.isDark
    val onSky = overlay == null && tab != Tab.Settings
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark && !onSky }
        }
    }

    // An overlay goes back to the one it came from, or to its tab; a tab goes back to Home, and Home exits.
    val back = {
        overlay = when {
            overlay == Overlay.Search && searchingFor == null && tab == Tab.Weather && state.savedPlaces.isNotEmpty() -> Overlay.Places
            else -> null
        }
    }
    BackHandler(enabled = overlay != null || tab != Tab.Home) {
        if (overlay != null) {
            vm.clearSearch()
            back()
            searchingFor = null
        } else {
            tab = Tab.Home
        }
    }
    val openSearch = { overlay = Overlay.Search }

    when (overlay) {
        Overlay.Search -> SearchScreen(
            search = state.search,
            savedIds = if (searchingFor == null) state.savedPlaces.map { it.id }.toSet() else emptySet(),
            onQueryChange = vm::onSearchQueryChange,
            onPick = { place ->
                val end = searchingFor
                if (end != null) {
                    vm.setCommutePlace(end, place)
                    vm.clearSearch()
                    searchingFor = null
                    overlay = null
                } else {
                    scrollTo = vm.addPlace(place)
                    overlay = null
                    tab = Tab.Weather
                }
            },
            onBack = {
                vm.clearSearch()
                back()
                searchingFor = null
            },
            title = searchingFor?.let { "Your ${it.label.lowercase()}" } ?: "Add a place",
        )
        Overlay.Places -> PlacesScreen(
            places = state.savedPlaces,
            useCurrentLocation = state.settings.useCurrentLocation,
            onUseCurrentLocationChange = { enabled ->
                if (enabled) enableCurrentLocation() else vm.setUseCurrentLocation(false)
            },
            onMove = vm::movePlace,
            onRemove = { vm.removePlace(it.id) },
            onAdd = { overlay = Overlay.Search },
            onBack = { overlay = null },
        )
        null -> Scaffold(
            bottomBar = { DaybreakNavigationBar(tab) { tab = it } },
            // Each tab handles the status bar itself (Home and Weather draw their sky behind it).
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
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
                        )
                        Tab.Settings -> SettingsTab(state, vm, commutePlaceHere, modelPicker::launch) { end ->
                            searchingFor = end
                            overlay = Overlay.Search
                        }
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
    commutePlaceHere: (CommuteEnd) -> Unit,
    launchModelPicker: (Array<String>) -> Unit,
    onCommutePlaceSearch: (CommuteEnd) -> Unit,
) {
    SettingsScreen(
            settings = state.settings,
            modelStatus = state.modelStatus,
            onUnitChange = vm::setPrimaryUnit,
            onGemmaEnabledChange = vm::setGemmaEnabled,
            onMemesEnabledChange = vm::setMemesEnabled,
            onComingUpEnabledChange = vm::setComingUpEnabled,
            onAddPersonalDate = vm::addPersonalDate,
            onRemovePersonalDate = vm::removePersonalDate,
            onToneChange = vm::setTone,
            onAboutMeChange = vm::setAboutMe,
            onActivityChange = vm::setActivity,
            onCommuteChange = vm::setCommute,
            commuteLocating = state.commuteLocating,
            onCommutePlaceHere = commutePlaceHere,
            onCommutePlaceSearch = onCommutePlaceSearch,
            onDownloadModel = vm::downloadModel,
            onCancelDownload = vm::cancelModelDownload,
            onImportModel = { launchModelPicker(arrayOf("*/*")) },
            onRemoveModel = vm::removeModel,
            onBack = null,
        )
}

/** Home · Weather · Settings, labels always shown, flat on the card colour like the cards themselves. */
@Composable
fun DaybreakNavigationBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
        Tab.entries.forEach { t ->
            val on = t == selected
            NavigationBarItem(
                selected = on,
                onClick = { onSelect(t) },
                label = { Text(t.label) },
                icon = {
                    when (t) {
                        Tab.Home -> Icon(if (on) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = null)
                        Tab.Weather -> WeatherTabIcon()
                        Tab.Settings -> Icon(if (on) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = null)
                    }
                },
            )
        }
    }
}
