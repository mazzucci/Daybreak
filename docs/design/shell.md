# Daybreak: app shell design

This is the target design for turning the weather app into Daybreak, a personal daily app with weather as one section. The layout proposal came from a design review on 2026-09-30. Treat it as the plan, and change it when implementation teaches us better.

Two constraints shaped it:
- `material-icons-core` has no weather or clock glyph. Those two tab icons are hand-drawn, like the rest of the app's icons (`WeatherIcons.kt`).
- Clocks needs each place's `ZoneId`, so that daylight saving is handled. Both the geocoding and the forecast responses include `timezone`; parse it into `Place.zoneId`.

## 1. Bottom bar

**Component:** Material 3 `NavigationBar` in `Scaffold(bottomBar = …)` with four items.
- Labels always shown.
- `containerColor = surfaceContainer`, `tonalElevation = 0.dp`, no divider.
- The default M3 indicator pill.

**Tabs:**

| Label | Selected | Unselected |
|---|---|---|
| Home | `Icons.Filled.Home` | `Icons.Outlined.Home` |
| Weather | drawn: `BrandMark` (sun behind cloud), cloud filled | cloud as a 1.75dp outline |
| Clocks | drawn: filled 9dp-radius disc, hands cut out in `secondaryContainer`, hour hand at 12 and minute hand at 4 | 2dp ring, hands in content colour |
| Settings | `Icons.Filled.Settings` | `Icons.Outlined.Settings` |

The drawn icons live in `TabIcons.kt` and reuse the `sun()` and `cloud()` primitives.

**Navigation:**
- Keep the hand-rolled navigation: a saveable `Tab { Home, Weather, Clocks, Settings }` plus a nullable overlay: Search, Places, and later Add clock (Search retitled).
- Overlays are full-screen with a back arrow, and the bottom bar is hidden while one is open.
- A `SaveableStateHolder` keeps each tab's state: the pager, the scroll positions and the converter.
- Reselecting a tab scrolls it to the top.

**Back:**
- From any other tab, back goes to Home; from Home it exits.
- An overlay goes back to the tab that opened it.

**Launch:**
- The app opens on Home.
- The location prompt fires from Home, since the weather glance needs it.
- The Weather tab's floating row drops its Settings icon and keeps Refresh, Add and Places.

**Status bar:** on Home the gradient extends behind it, as it does on the Weather hero. Clocks and Settings have a plain background bar. Toggle `isAppearanceLightStatusBars` per tab.

## 2. Home

### Header (hero)

It reuses `Hero()`, but without the floating action row: 16dp top padding and left-aligned content. The gradient is `heroGradient(skyOf(glance code), night, dark)` for the glance place, or `neutralGradient` while there's no forecast. It contains, in white:

1. **Date:** `bodyMedium` at 85% opacity, "Wednesday, September 30".
2. **Greeting:** `headlineLarge`, with no full stop and no name.
   - 05–11: "Good morning"
   - 12–17: "Good afternoon"
   - 18–21: "Good evening"
   - 22–04: "Good night"
3. **One-liner (Gemma greeting, later):** in the `SummaryBlock` style, with "✦ Written by Gemma on this device" beneath. The block is absent until the greeting ships. Don't fill it with the template weather summary: the glance below already covers the weather.

### Weather glance

A tappable `surfaceContainer` card, min 72dp tall, with 16dp padding. Tapping it opens the Weather tab on that place, using the existing `scrollTo`.

**Which place:** the first page that can show weather, the same rule as `commutePage`.

**Content, left to right:**
- a 36dp `WeatherIcon`;
- a column with:
  - `titleMedium` place name, with a 14dp `LocationOn` for the current location, or "My location" until it resolves;
  - `bodyMedium` variant: "Partly cloudy · ↑74° ↓56° · Rain 60%", leaving out rain under 20%;
- `DualTemp` in `headlineMedium` (for example "71°" over "21°C");
- a `KeyboardArrowRight` chevron.

**Screen reader:** one node, "San Francisco, 71 degrees Fahrenheit, 21 degrees Celsius, partly cloudy, high 74, low 56, 60 percent chance of rain. Opens Weather."

**States:**
- **Loading:** two skeleton bars.
- **Needs permission:** "Where are you?", with "Allow location" and "Search for a place instead".
- **No places:** "Pick a place to start", with "Add a place".
- **Failed:** "Couldn't load the weather", with "Try again".

The glance is the one card that can't be turned off.

### Cards

The order is fixed and not user-reorderable. A card that is off, or has nothing to show, disappears along with its heading.

1. Hero
2. Weather glance
3. `CommuteCard`. No heading: its eyebrow does that job.
4. "Coming up": `ComingUpCard` with the glance place's holidays plus your dates.
5. "On this day" (later)
6. "Word of the day" (later; needs Gemma)
7. "Today's weather meme": `MemeCard` for the glance forecast. It's always last, because it's the tallest card and the least actionable.

`ActivityCard` ("Best time to ride") stays on the Weather tab.

**Spacing:** 16dp between cards, 24dp before a heading, 12dp from a heading to its card.

**Other rules:**
- When only the glance is on, show one quiet line under it: "Turn on more for Home in Settings", which opens the Settings tab.
- Pull-to-refresh refreshes the glance place and the commute.

**Future cards:**
- **On this day:** a 36dp disc holding the year (`labelSmall`, primary at 14%), then the event text, then "From Wikipedia". Tapping opens the article.
- **Word of the day:**
  - `titleLarge` word plus its part of speech;
  - an `OutlinedTextField`, "What do you think it means?", with a "Check" button;
  - the verdict in the `VoicePreview` block style, with Wiktionary's definition and "✦ Judged by Gemma on this device".
  - The card keeps the day's verdict.

## 3. Clocks

**Top bar:** a `TopAppBar` titled "Clocks", with "Edit"/"Done" and an Add button.

**Device block** (no card):
- `displayMedium` time, "10:42";
- `bodyMedium` variant: "Los Angeles · your phone · UTC−7";
- ticks each minute.

**Clock list:** one card, with rows divided by insets of 64dp. Each row has:
- **Left:** a 36dp disc. Day shows a sun on amber; night shows a moon on primary. Use sunrise and sunset when the place is also a weather place, otherwise 06–18.
- **Middle:** the name, over "Tomorrow · +10 h", "Today · same time as you", "+5½ h".
- **Right:** `headlineSmall` time over `labelSmall` "UTC+3". No zone abbreviations, and no temperature.
- **Screen reader:** "Bucharest, 8:42 PM, tomorrow, 10 hours ahead, night".
- **Empty:** "No clocks yet", with an "Add a clock" button.

**Editing the list:**
- **Add:** the Search overlay titled "Add a clock", with "From your places" (saved weather places) listed above the search.
- **Edit:** each row's right side becomes the Places controls (up, down, delete). The device row can't be edited.

**Convert** (a heading, then one card):
- **Chips:** time "12:00 PM" (opens a `TimePicker`), day "Today" (Today, Tomorrow or Pick a date), and "in Los Angeles" (the device or any clock).
- **Result rows:** "At 12:00 PM on Wednesday in Los Angeles it's…", then a row for every other clock, with "Thu · next day" when the day differs.
- **Ask box:** under a divider, a field labelled "Ask", with the placeholder "What time is it in Romania at noon my time?" and a Send button.
  - While working: a progress bar and "Working it out…".
  - The answer appears in the `VoicePreview` block: "It'll be 10:00 PM on Wednesday in Bucharest.", with "✦ Written by Gemma on this device".
  - On failure: "Gemma couldn't work that one out. Try the picker above."
  - **The answer sets the chips.** When Gemma's tools resolve a moment and a zone, the chips are set to them, so the result rows show the same conversion in every clock. The prose is the answer; the rows are the proof.
  - A place that isn't a saved clock is added to the results for that answer only, with an "Add clock" button.
  - Without Gemma: "Ask in plain words once Gemma is installed", with "Set up".

## 4. Settings, grouped by tab

Settings becomes a tab with no back arrow. Its sections:

1. **Temperature** (°F first or °C first). It applies everywhere.
2. **Home:** "Office or home?", "Holidays and countdowns", "Your dates", "Daily weather meme", and later On this day, Word of the day and Greeting. The copy should say "on Home" rather than "on the first page".
3. **Weather:** a Places row that opens the Places screen, and Activity.
4. **Voice** (was "Summary style"): "Gemma writes the summary and the Home greeting in this voice."
5. **Gemma** (was "AI summary"): a "Use Gemma on this device" switch, then the model section.
6. **Footer:** "Daybreak 1.5.0".

There's no Clocks section until a clock setting exists.

## 5. The name

- The launcher label is "Daybreak". The app icon stays as it is for now.
- No top bar shows the app name. The name appears only in the launcher, the Settings footer, and the first-run empty hero: "Daybreak / Your day, at a glance".
