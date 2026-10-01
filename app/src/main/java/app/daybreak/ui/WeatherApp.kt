package app.daybreak.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.unit.isSpecified
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.app.Activity
import java.time.LocalDate
import app.daybreak.domain.Forecast
import app.daybreak.reminders.Reminders
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.content.Context
import android.os.Build
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import app.daybreak.domain.PersonalDate
import kotlinx.coroutines.launch

/** What the system lets reminders do right now. */
private fun reminderAccess(context: Context) =
    ReminderAccess(notificationsAllowed = Reminders.notificationsAllowed(context), exactAlarms = Reminders.canScheduleExact(context))

/** The bottom bar's sections. Home is where the app opens. */
enum class Tab(val label: String) { Home("Home"), Weather("Weather"), Habits("Habits"), Clocks("Clocks"), Settings("Settings") }

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
fun WeatherApp(
    vm: WeatherViewModel,
    clocksVm: ClocksViewModel? = null,
    habitsVm: HabitsViewModel? = null,
    onThisDayVm: OnThisDayViewModel? = null,
    /** Goes up each time a reminder is tapped while the app is open: back to Home. */
    homeRequests: kotlinx.coroutines.flow.StateFlow<Int>? = null,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val clocks = clocksVm?.clocks?.collectAsStateWithLifecycle()?.value.orEmpty()
    val habits = habitsVm?.summary?.collectAsStateWithLifecycle()?.value
    val habitsData = habitsVm?.data?.collectAsStateWithLifecycle()?.value
    val celebration = habitsVm?.celebration?.collectAsStateWithLifecycle()?.value
    val undoHint = habitsVm?.undoHint?.collectAsStateWithLifecycle()?.value ?: false
    if (habitsVm != null) OnNewDay(habitsVm::refresh)
    val onThisDay = onThisDayVm?.day?.collectAsStateWithLifecycle()?.value
    // Once the settings are read, so a card that's switched off is never fetched.
    if (onThisDayVm != null && state.ready) OnThisDayLoader(onThisDayVm, state.settings.onThisDayEnabled)
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
    // theme's own on Habits, Clocks, Settings and the overlays.
    val view = LocalView.current
    val dark = MaterialTheme.isDark
    val onSky = (overlay == null && (tab == Tab.Home || tab == Tab.Weather)) || overlay == Overlay.Day
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark && !onSky }
        }
    }
    // Each tab keeps its own scroll and state while another is shown.
    val homeRequest = homeRequests?.collectAsStateWithLifecycle()?.value ?: 0
    LaunchedEffect(homeRequest) {
        if (homeRequest > 0) {
            overlay = null
            tab = Tab.Home
        }
    }
    val context = LocalContext.current
    // What the system lets reminders do, read again whenever the app comes back to the front: the user may have just
    // changed it in the system's pages.
    var access by remember { mutableStateOf(reminderAccess(context)) }
    LifecycleResumeEffect(Unit) {
        access = reminderAccess(context)
        onPauseOrDispose {}
    }
    // Asked for once, when a date is first saved with a reminder and no date had one; after that the hint offers the way.
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        access = reminderAccess(context)
    }
    val saveDate = { old: PersonalDate?, new: PersonalDate ->
        val first = new.reminders.isNotEmpty() && state.settings.personalDates.none { it.reminders.isNotEmpty() }
        vm.savePersonalDate(old, new)
        if (first && !askedForNotifications && Reminders.needsNotificationPermission(context) && Build.VERSION.SDK_INT >= 33) {
            askedForNotifications = true
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val undoMessage: (String, () -> Unit) -> Unit = { message, onUndo ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) onUndo()
        }
    }
    /** The date editor, for Settings' "Your dates" and Home's Coming up alike. */
    val editorHost: @Composable (@Composable (PersonalDateEditing) -> Unit) -> Unit = { content ->
        PersonalDateEditorHost(
            state.settings.personalDates, LocalDate.now(), saveDate, vm::removePersonalDate, vm::addPersonalDate,
            access, { Reminders.exactAlarmSettings(context)?.let { runCatching { context.startActivity(it) } } },
            state.settings.lastAllDayReminders, state.settings.lastTimedReminders, content,
        )
    }
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
            snackbarHost = { SnackbarHost(snackbar) },
            // Each tab handles the status bar itself (Home and Weather draw their sky behind it).
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            // Side insets (landscape navigation buttons, cutouts) for every tab; the top is each tab's own.
            Box(
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                tabStates.SaveableStateProvider(tab) {
                    CompositionLocalProvider(LocalUndoMessage provides undoMessage) {
                        when (tab) {
                            Tab.Home -> editorHost { editor ->
                                HomeScreen(
                                    state = state,
                                    onOpenWeather = { index ->
                                        scrollTo = index
                                        tab = Tab.Weather
                                    },
                                    onRefresh = { key ->
                                        vm.refresh(key)
                                        // A card that couldn't be fetched (offline) gets another go.
                                        if (state.settings.onThisDayEnabled) onThisDayVm?.load(force = true)
                                    },
                                    onRequestPermission = requestPermission,
                                    onOpenSearch = openSearch,
                                    onOpenSettings = { tab = Tab.Settings },
                                    habits = habits,
                                    celebration = celebration,
                                    onLogHabit = { habitsVm?.log(it, fromHome = true) },
                                    onUndoHabit = { habitsVm?.undo(it) },
                                    onCelebrationShown = { habitsVm?.celebrationShown(it) },
                                    habitsUndoHint = undoHint,
                                    onOpenHabits = { tab = Tab.Habits },
                                    onThisDay = onThisDay,
                                    onAnotherOnThisDay = { onThisDayVm?.another() },
                                    images = onThisDayVm?.images,
                                    onEditDate = { id -> state.settings.personalDates.firstOrNull { it.id == id }?.let(editor.edit) },
                                )
                            }
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
                            Tab.Habits -> HabitsScreen(
                                summary = habits ?: app.daybreak.domain.summarize(app.daybreak.domain.HabitsData(), java.time.LocalDate.now()),
                                celebration = celebration,
                                onLog = { habitsVm?.log(it) },
                                onUndo = { habitsVm?.undo(it) },
                                onAdd = { habitsVm?.add(it) },
                                onUpdate = { id, draft -> habitsVm?.update(id, draft) },
                                onRemove = { habitsVm?.remove(it) },
                                onMove = { id, by -> habitsVm?.move(id, by) },
                                onCelebrationShown = { habitsVm?.celebrationShown(it) },
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
                            Tab.Settings -> SettingsTab(
                                state, vm, modelPicker::launch,
                                habitsWeekStart = habitsData?.weekStart,
                                onHabitsWeekStartChange = { habitsVm?.setWeekStart(it) },
                                access = access,
                                onSaveDate = saveDate,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Calls [onDay] when the date changes (past midnight), in its own scope so the minute ticks recompose nothing else. */
@Composable
private fun OnNewDay(onDay: () -> Unit) {
    val day = rememberToday()
    LaunchedEffect(day) { onDay() }
}

/**
 * Asks for today's "On this day" while it's switched on: each time the app comes back (which also retries after
 * being offline) and when the date changes. Switched off, the card goes at once.
 */
@Composable
private fun OnThisDayLoader(vm: OnThisDayViewModel, enabled: Boolean) {
    val day = rememberToday()
    LifecycleResumeEffect(enabled, day) {
        if (enabled) vm.load() else vm.clear()
        onPauseOrDispose {}
    }
}

@Composable
private fun SettingsTab(
    state: WeatherUiState,
    vm: WeatherViewModel,
    launchModelPicker: (Array<String>) -> Unit,
    habitsWeekStart: java.time.DayOfWeek?,
    onHabitsWeekStartChange: (java.time.DayOfWeek) -> Unit,
    access: ReminderAccess,
    onSaveDate: (PersonalDate?, PersonalDate) -> Unit,
) {
    val context = LocalContext.current
    SettingsScreen(
            settings = state.settings,
            onSavePersonalDate = onSaveDate,
            onRestorePersonalDate = vm::addPersonalDate,
            reminderAccess = access,
            onOpenNotificationSettings = { runCatching { context.startActivity(Reminders.notificationSettings(context)) } },
            onAllowExactAlarms = { Reminders.exactAlarmSettings(context)?.let { runCatching { context.startActivity(it) } } },
            modelStatus = state.modelStatus,
            onUnitChange = vm::setPrimaryUnit,
            onGemmaEnabledChange = vm::setGemmaEnabled,
            onMemesEnabledChange = vm::setMemesEnabled,
            onSkyEnabledChange = vm::setSkyEnabled,
            onHabitsOnHomeChange = vm::setHabitsOnHome,
            onOnThisDayEnabledChange = vm::setOnThisDayEnabled,
            habitsWeekStart = habitsWeekStart,
            onHabitsWeekStartChange = onHabitsWeekStartChange,
            onComingUpEnabledChange = vm::setComingUpEnabled,
            onRemovePersonalDate = vm::removePersonalDate,
            onDownloadModel = vm::downloadModel,
            onCancelDownload = vm::cancelModelDownload,
            onImportModel = { launchModelPicker(arrayOf("*/*")) },
            onRemoveModel = vm::removeModel,
            onBack = null,
        )
}

/**
 * The bar's label style: growing with the font size setting only up to 1.3x, and no bigger than lets the longest
 * label fit [itemWidth] on one line, so five labels fit even on the narrowest phones. The size and letter spacing
 * shrink together, never below [MinLabelSize].
 */
@Composable
private fun cappedLabel(itemWidth: androidx.compose.ui.unit.Dp): androidx.compose.ui.text.TextStyle {
    val style = MaterialTheme.typography.labelMedium
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val widest = Tab.entries.maxOf { measurer.measure(it.label, style, maxLines = 1, softWrap = false).size.width }
    return cappedLabelStyle(style, density.fontScale, with(density) { itemWidth.toPx() }, widest.toFloat())
}

/**
 * The smallest the bar's labels get, however narrow the phone: 9sp as drawn at the default font size. It's a floor
 * on the size on screen, so at the largest font settings (where 9sp is drawn twice as big) the labels can still
 * shrink to fit.
 */
internal val MinLabelSize = 9.sp

/**
 * [style] scaled to fit: at most 1.3x the base size at large font settings, and small enough that the widest label
 * ([widestPx] at [style]) fits [availablePx]. Letter spacing scales with it. Never drawn smaller than [MinLabelSize]
 * (unless the base style already is), so never zero or negative, even with no room at all.
 */
internal fun cappedLabelStyle(
    style: androidx.compose.ui.text.TextStyle,
    fontScale: Float,
    availablePx: Float,
    widestPx: Float,
): androidx.compose.ui.text.TextStyle {
    val capped = if (fontScale <= 1.3f) 1f else 1.3f / fontScale
    val fit = if (widestPx > 0f) availablePx.coerceAtLeast(0f) / widestPx else 1f
    var f = minOf(capped, fit)
    if (f >= 1f || !style.fontSize.isSpecified) return style
    val base = style.fontSize.value * fontScale
    val floor = minOf(MinLabelSize.value, base) / base
    f = f.coerceAtLeast(floor)
    return style.copy(
        fontSize = style.fontSize * f,
        lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * f else style.lineHeight,
        letterSpacing = if (style.letterSpacing.isSpecified) style.letterSpacing * f else style.letterSpacing,
    )
}

/** Home · Weather · Habits · Clocks · Settings, labels always shown, flat on the card colour like the cards themselves. */
@Composable
fun DaybreakNavigationBar(selected: Tab, onSelect: (Tab) -> Unit) {
    BoxWithConstraints {
        // The bar keeps clear of the side insets (landscape navigation buttons, cutouts), spaces its items 8dp apart
        // and pads each label 4dp a side; 4dp more so rounding never clips one.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
        val insets = NavigationBarDefaults.windowInsets
        val sides = with(density) { (insets.getLeft(density, direction) + insets.getRight(density, direction)).toDp() }
        val itemWidth = (maxWidth - sides - 8.dp * (Tab.entries.size - 1)) / Tab.entries.size - 12.dp
        val label = cappedLabel(itemWidth)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
            Tab.entries.forEach { t ->
                val on = t == selected
                NavigationBarItem(
                    selected = on,
                    onClick = { onSelect(t) },
                    // One line always: at the largest font sizes the labels stop growing rather than breaking.
                    label = { Text(t.label, maxLines = 1, softWrap = false, style = label) },
                    icon = {
                        when (t) {
                            Tab.Home -> Icon(if (on) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = null)
                            Tab.Weather -> WeatherTabIcon()
                            Tab.Habits -> HabitsTabIcon(on)
                            Tab.Clocks -> ClocksTabIcon(on)
                            Tab.Settings -> Icon(if (on) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}
