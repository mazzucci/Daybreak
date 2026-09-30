package com.mazzucci.weather.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private enum class Screen { Weather, Search, Places, Settings, Ride }

/** Wires the ViewModel to the stateless screens and owns navigation and system pickers/prompts. */
@Composable
fun WeatherApp(vm: WeatherViewModel, rideVm: RideViewModel? = null) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.Weather) }
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

    val rideState = rideVm?.state?.collectAsStateWithLifecycle()
    // GPX has no reliably registered MIME type either, so accept anything; the parser checks the content.
    val gpxPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { rideVm?.open(it.toString()) }
    }

    LaunchedEffect(Unit) {
        if (vm.shouldRequestLocationOnStart()) requestPermission()
    }
    LaunchedEffect(screen, scrollTo, state.pages.size) {
        val target = scrollTo ?: return@LaunchedEffect
        if (screen == Screen.Weather && target < state.pages.size) {
            pagerState.scrollToPage(target)
            scrollTo = null
        }
    }

    val back = {
        screen = when {
            screen == Screen.Search && state.savedPlaces.isNotEmpty() -> Screen.Places
            screen == Screen.Ride -> Screen.Settings
            else -> Screen.Weather
        }
    }
    BackHandler(enabled = screen != Screen.Weather) {
        vm.clearSearch()
        back()
    }

    when (screen) {
        Screen.Weather -> WeatherPagerScreen(
            state = state,
            pagerState = pagerState,
            onRefresh = vm::refresh,
            onRequestPermission = requestPermission,
            onUseCurrentLocation = enableCurrentLocation,
            onOpenSearch = { screen = Screen.Search },
            onOpenPlaces = { screen = Screen.Places },
            onOpenSettings = { screen = Screen.Settings },
        )
        Screen.Search -> SearchScreen(
            search = state.search,
            savedIds = state.savedPlaces.map { it.id }.toSet(),
            onQueryChange = vm::onSearchQueryChange,
            onPick = { place ->
                scrollTo = vm.addPlace(place)
                screen = Screen.Weather
            },
            onBack = {
                vm.clearSearch()
                back()
            },
        )
        Screen.Places -> PlacesScreen(
            places = state.savedPlaces,
            useCurrentLocation = state.settings.useCurrentLocation,
            onUseCurrentLocationChange = { enabled ->
                if (enabled) enableCurrentLocation() else vm.setUseCurrentLocation(false)
            },
            onMove = vm::movePlace,
            onRemove = { vm.removePlace(it.id) },
            onAdd = { screen = Screen.Search },
            onBack = { screen = Screen.Weather },
        )
        Screen.Ride -> RideScreen(
            state = rideState?.value ?: RideUi.Idle,
            unit = state.settings.primaryUnit,
            onPickFile = { gpxPicker.launch(arrayOf("*/*")) },
            onBack = { back() },
        )
        Screen.Settings -> SettingsScreen(
            settings = state.settings,
            modelStatus = state.modelStatus,
            onUnitChange = vm::setPrimaryUnit,
            onGemmaEnabledChange = vm::setGemmaEnabled,
            onMemesEnabledChange = vm::setMemesEnabled,
            onComingUpEnabledChange = vm::setComingUpEnabled,
            onToneChange = vm::setTone,
            onAboutMeChange = vm::setAboutMe,
            onActivityChange = vm::setActivity,
            onCommuteChange = vm::setCommute,
            onOpenRideReplay = if (rideVm != null) ({ screen = Screen.Ride }) else null,
            onDownloadModel = vm::downloadModel,
            onCancelDownload = vm::cancelModelDownload,
            onImportModel = { modelPicker.launch(arrayOf("*/*")) },
            onRemoveModel = vm::removeModel,
            onBack = { screen = Screen.Weather },
        )
    }
}
