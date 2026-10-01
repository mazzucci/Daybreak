# Daybreak: app shell design

This is the target design for turning the weather app into Daybreak, a personal daily app with weather as one section. The layout proposal came from a design review on 2026-09-30. Treat it as the plan, and change it when implementation teaches us better.

Two constraints shaped it:
- `material-icons-core` has no weather, clock or habit glyph. Those three tab icons are hand-drawn, like the rest of the app's icons (`WeatherIcons.kt`).
- Clocks needs each place's `ZoneId`, so that daylight saving is handled. Both the geocoding and the forecast responses include `timezone`; parse it into `Place.zoneId`.

## 1. Bottom bar

**Component:** Material 3 `NavigationBar` in `Scaffold(bottomBar = …)` with five items.
- Labels always shown, on one line: they grow with the font size up to 1.3x, and no more than lets the longest fit its item (so five fit on a 320dp phone).
- `containerColor = surfaceContainer`, `tonalElevation = 0.dp`, no divider.
- The default M3 indicator pill.

**Tabs:**

| Label | Selected | Unselected |
|---|---|---|
| Home | `Icons.Filled.Home` | `Icons.Outlined.Home` |
| Weather | drawn: `BrandMark` (sun behind cloud), cloud filled | cloud as a 1.75dp outline |
| Habits | drawn: filled 18dp rounded square (4.5dp corners), check cut out in `secondaryContainer` | 2dp outline, check in content colour |
| Clocks | drawn: filled 9dp-radius disc, hands cut out in `secondaryContainer`, hour hand at 12 and minute hand at 4 | 2dp ring, hands in content colour |
| Settings | `Icons.Filled.Settings` | `Icons.Outlined.Settings` |

The drawn icons live in `TabIcons.kt` and reuse the `sun()` and `cloud()` primitives.

**Navigation:**
- Keep the hand-rolled navigation: a saveable `Tab { Home, Weather, Habits, Clocks, Settings }` plus a nullable overlay: Search, Places, and later Add clock (Search retitled).
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

**Status bar:** on Home the gradient extends behind it, as it does on the Weather hero. Habits, Clocks and Settings have a plain background bar. Toggle `isAppearanceLightStatusBars` per tab.

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

**Which place:** the first page, unless it's the current location still waiting for permission (`glancePageIndex`).

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
3. "Today's habits": `HabitsHomeCard` (see §3), once there's a habit.
4. "Coming up": `ComingUpCard` with the glance place's holidays plus your dates.
5. "Tonight's sky": `SkyCard`.
6. "On this day" (later)
7. "Word of the day" (later; needs Gemma)
8. "Today's weather meme": `MemeCard` for the glance forecast. It's always last, because it's the tallest card and the least actionable.

`ActivityCard` ("Best time to ride") stays on the Weather tab.

**Spacing:** 16dp between cards, 24dp before a heading, 12dp from a heading to its card.

**Other rules:**
- When only the glance is on, show one quiet line under it: "Turn on more for Home in Settings", which opens the Settings tab.
- Pull-to-refresh refreshes the glance place.

**Future cards:**
- **On this day:** a 36dp disc holding the year (`labelSmall`, primary at 14%), then the event text, then "From Wikipedia". Tapping opens the article.
- **Word of the day:**
  - `titleLarge` word plus its part of speech;
  - an `OutlinedTextField`, "What do you think it means?", with a "Check" button;
  - the verdict in a quiet `surfaceContainerHighest` block, with Wiktionary's definition and "✦ Judged by Gemma on this device".
  - The card keeps the day's verdict.

## 3. Habits

Unlimited habits, each fully configurable: a title, a colour from an 8-colour palette (Blue, Teal, Green, Olive, Amber, Coral, Pink, Purple; each has a light-theme shade that takes white text and a dark-theme one that takes dark text), a kind, and a goal.
- **Build** (a good habit): at least *n* a day or a week. "Drink water", 8 a day; "Ride a bike", 1 a week.
- **Avoid** (a bad habit): at most *n* a day or a week, 0 for none at all. "No takeout", none a week; "Coffee", at most 2 a day.

**Logging:** +1 (a build habit) or "Had one" (an avoid habit) adds one to today. Long-pressing it, or the − beside it, takes back the latest log of this day or week (today's first, or one filed after today while the clock was ahead; never last week's). The log is a count per day, never filed after today.

**Goal history:** each change of goal (kind, period or target) is kept with the day it was made, and each day or week is judged by the goal in force then, so editing a habit never re-judges the past: nothing earned is lost and nothing is gained after the fact. A change within a period takes the whole of that (still open) period; a switch between daily and weekly takes over from its day, leaving the old goal a part week. Changes on the same day replace each other. A run restarts when a habit switches between build and avoid, or daily and weekly.

**Dates:** the phone's local date, following midnight. The first day of the week is stored with the habits the first time they're saved (from `LocalePreferences.getFirstDayOfWeek()`, which honours Android 14's own setting, else the locale), so changing language or region never re-buckets the past; Settings → Habits → "Weeks start on" (Mon / Sun / Sat) changes it on purpose. Days and weeks before a habit was added count neither for nor against it. A week cut short (added mid-week, or a daily/weekly switch) counts for a build habit only if met, never against it, and earns an avoid habit nothing. Anything dated after today (the clock was ahead) is left out of every count.

**Stats:**
- **Build:** this period's count against the goal, the current streak of met days (or weeks) and the best. Today (or this week) counts as soon as it's met, and doesn't break the streak while it's still open.
- **Avoid, none or a weekly allowance:** "12 days since the last one" ("Not once in 12 days" if there's never been one), this week's count, and "Allowance kept this week" or "Over the allowance this week". No streak is shown, so a slip doesn't read as a broken one.
- **Avoid, a daily allowance:** today's count against it as a ring ("1 of 2 allowed today", no check: reaching an allowance isn't a goal) and "Kept 4 of 5 days this week". Days since the last one would always be 0, so it isn't shown.

**Points, levels and badges** (subtle, never in the way):
- 1 point per logged unit of a build habit, up to its goal for the period and at most 10; 10 per day a daily goal is met or a daily allowance kept; 50 per week for a weekly one. Allowances only count once their day or week is over.
- Bonuses when a run of met (or kept) periods reaches 7 (+50), 30 (+150) and 100 (+500).
- Level *n* starts at 50·*n*·(*n*−1) points, so each level takes 100 more than the last: "Level 4 · 230 to Level 5".
- Badges: First step, 7-day streak, 30-day streak, 100-day streak, 4 good weeks (a weekly goal), A clean month (30 kept days or 4 kept weeks), 100 check-ins (at most the goal a day, or 10 a day for an avoid habit).
- Points are worked out from the log, so undoing a log takes its points back. Deleting a habit banks what its finished days and weeks earned (points and badges); today's and this week's go with it, so deleting and re-adding never earns twice.
- **Celebration:** after a +1, one line in place under the habit, in amber with a star, for about 3.5 seconds (a polite live region); the ring fills and a check pops in. In order of preference: a milestone ("7-day streak! +61", "7-week streak! +101"), a new level ("Level 5! +11"), a new badge ("New badge: First step · +1"), the goal met ("Done for today · +11"). An ordinary +1 and any avoid log get none.

**Tab:**
- **Top bar:** a `TopAppBar` titled "Habits", with "Edit"/"Done" and an Add button.
- **Level card:** a 36dp star disc on amber, "Level 4" in `titleMedium` over "770 pts · 230 to Level 5" in `bodySmall`, a 6dp amber bar, then the first two earned badges as small amber pills (no stars) and "+3 more", which shows the rest. One node for TalkBack, with every badge.
- **Habit cards**, one per habit:
  - top: a 48dp ring in the habit's colour with "5/8" (a daily allowance's ring drains instead: full with none used, empty at the allowance, "3/2" saying why past it; an avoid habit with none or a weekly allowance shows a disc with its clean days), then the title in `titleMedium`, the first line in `bodyMedium` and the rest in `bodySmall`; a met goal's line is in the success green. With none allowed and none this week, "Allowance kept this week" is left out as it adds nothing.
  - bottom: the 12-week heat-map (a column per week, oldest first, 9dp squares), then − (a 40dp tonal disc, `onSurface` at 8%) and the 48dp +1 disc in the habit's colour, or for an avoid habit a 40dp tonal pill "Had one" in its colour (12% fill, no cheer, no success buzz). They move under the map when there's no room beside it.
  - The words and map are one TalkBack node ("Drink water. 8 a day. 12-day streak. Goal met on 40 of the last 84 days."); tapping it edits the habit. The first line ("5 of 8 today") is its own live region, so a log is heard as it lands. The buttons are their own nodes ("Add one to Drink water, 5 of 8 today", "Log one for No takeout", with "Undo" as an action). Each card is keyed by its habit, so state and focus follow it when the order changes.
  - **Heat-map:** each day is judged by the goal in force then. A build habit's day is coloured by its share of the day's goal (a weekly habit's logged days are full); an avoid habit's kept days are a light tint (a daily allowance is kept up to its count) and a slip is an open square outlined in its colour.
- **Edit mode:** each card's buttons become up, down and delete (and TalkBack actions); delete asks first ("Its log and streaks go with it. The points and badges it earned stay.").
- **Add / edit:** a dialog with a name, Build / Avoid, then the goal: the Daily / Weekly switch, then "At least / At most [− n +] a day / a week" ("0 means none at all." under an avoid habit's 0), and the colour swatches. A preset can't be added twice by a quick double tap.
- **Empty:** "Track a habit", a short explanation, then three one-tap presets ("Drink water · 8 a day", "Exercise · 3 a week", "No takeout · none a week") and "Make your own".

**Home card ("Today's habits"):** each build habit as a chip tinted with its colour: a 32dp ring, the name over "5/8 today" or "1/3 this week". A tap logs one (TalkBack hears the new count), a long-press takes one back; after the first log there, a small "Hold to undo" shows until something is taken back, once ever. Then each avoid habit as one line, which opens the Habits tab: "4 days without takeout" when the title says what's avoided ("No …", "Avoid …", "Quit …"), otherwise "Order food · 4 days since the last one", or for a daily allowance "Coffee · 1 of 2 allowed today". Slips are logged on the tab, not from Home. The card's cheer names the habit ("Read: Done for today · +11"). Hidden when there are no habits, or when Settings → Habits → "Habits on Home" is off.

**Storage:** `HabitsRepository` keeps the habits (with their goal history), their logs, the banked points and badges and the first day of the week as one versioned JSON object in the private store (`private.xml`, excluded from backup and device transfer, like your dates). Version 1 data (no version) is migrated at once: one goal from the day each habit was added, and the week start frozen. Nothing is dropped: a habit, log entry, badge or colour that doesn't parse, and any field this version doesn't know, is kept and written back unchanged; a store that doesn't parse at all is copied to `habits.backup` (then `.2`, …) before anything is written over it. Writes are serialised on `Dispatchers.IO`, the newest winning. `HabitsViewModel` is created in `MainActivity` like `ClocksViewModel`; it works out the summary once per change as a `StateFlow` for Home and the tab, and its "today" is injectable for tests.

## 4. Clocks

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
  - The answer appears in a quiet `surfaceContainerHighest` block: "It'll be 10:00 PM on Wednesday in Bucharest.", with "✦ Written by Gemma on this device".
  - On failure: "Gemma couldn't work that one out. Try the picker above."
  - **The answer sets the chips.** When Gemma's tools resolve a moment and a zone, the chips are set to them, so the result rows show the same conversion in every clock. The prose is the answer; the rows are the proof.
  - A place that isn't a saved clock is added to the results for that answer only, with an "Add clock" button.
  - Without Gemma: "Ask in plain words once Gemma is installed", with "Set up".

## 5. Settings, grouped by tab

Settings becomes a tab with no back arrow. Its sections:

1. **Temperature** (°F first or °C first). It applies everywhere.
2. **Home:** "Holidays and countdowns", "Your dates", "Daily weather meme", and later On this day, Word of the day and Greeting. The copy should say "on Home" rather than "on the first page".
   - **Habits** (its own section, before Coming up): "Habits on Home", on by default.
3. **Weather:** a Places row that opens the Places screen, and Activity.
4. **Gemma** (was "AI summary"): a "Use Gemma for the meme" switch, then the model section. Gemma no longer writes the weather summary; the template does.
5. **Footer:** "Daybreak 1.5.0".

There's no Clocks section until a clock setting exists.

## 6. The name

- The launcher label is "Daybreak". The app icon stays as it is for now.
- No top bar shows the app name. The name appears only in the launcher, the Settings footer, and the first-run empty hero: "Daybreak / Your day, at a glance".
