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

**Under that row:** the "This week" today line ("Mixed day: dry until 5 PM, then rain"), one line in `bodySmall` `onSurfaceVariant` that ellipsizes, starting under the place name (`padding(start = 64.dp, top = 6.dp, end = 16.dp, bottom = 14.dp)`), with no dot or other mark. It's worked out for the place's current hour, so a forecast fetched at 2 PM doesn't give afternoon advice at 8 PM. It's the only outlook on Home: the week lines and the strip stay on the Weather tab.

**Screen reader:** one node, "San Francisco, 71 degrees Fahrenheit, 21 degrees Celsius, partly cloudy, high 74, low 56, 60 percent chance of rain. Mixed day: dry until 5 PM, then rain. Opens Weather."

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
6. "On this day": `OnThisDayCard` (see below), the fun section with the meme.
7. "Word of the day" (later; needs Gemma)
8. "Today's weather meme": `MemeCard` for the glance forecast. It's always last, because it's the tallest card and the least actionable.

"This week" (`WeekOutlookSection`) stays on the Weather tab, between the sun tiles and the 10-day list; Home gets only its today line, in the glance.

**Spacing:** 16dp between cards, 24dp before a heading, 12dp from a heading to its card.

**Other rules:**
- When only the glance is on, show one quiet line under it: "Turn on more for Home in Settings", which opens the Settings tab.
- Pull-to-refresh refreshes the glance place.

**On this day** (`OnThisDayCard`, `OnThisDayViewModel`, `OnThisDayRepository`, `domain/OnThisDay.kt`, `data/ImageLoader.kt`):
- **Source:** English Wikipedia's feed, `en.wikipedia.org/api/rest_v1/feed/onthisday/selected/MM/DD` (the same response as api.wikimedia.org's copy), with the User-Agent "Daybreak/<versionName> (https://github.com/mazzucci/Daybreak)" (from `BuildConfig`) as Wikimedia asks, on the feed and the pictures. `HttpClient.get(url, headers)` carries it; its default drops the headers, so a real client must override it (`UrlConnectionHttpClient` does, and a test checks the header goes out through it). The answer is parsed, and the picks chosen and stored, off the main thread.
- **The subject:** the article the pick links and is titled with is the first one whose title (without a "(film)" note) appears, case-sensitively, in the text after any "Topic:" lead-in; else the first named anywhere; else the first.
- **Never grim:** an item is dropped when its text, any linked article's title, or the **first sentence** of the subject's article has a word about violence (killed, massacre, bombing, shooting, executed, beheaded, hanged, burned, murder, genocide, pogrom, dead, victims…), oppression (nazi, fascist, slavery, persecuted, deported, concentration camp…), disaster (crash, earthquake, explosion, sinks, a city's "great fire"…), weapons (nuclear test, detonation, atomic bomb) or war (war, battle, invasion, troops, coup, siege…), or its own text one about death (dies, died, death). Whole words, case-insensitive, ASCII only: "Warsaw" isn't "war", "Die Hard" isn't death. A hyphen is a word break on purpose, so "post-war" and "anti-war" are grim. Let through: "Star Wars", "War of the Worlds", "Battle of the Sexes", "Cold War ends", "Dead Sea", "Grateful Dead", "Day of the Dead". Only the first sentence of the article counts, since that says what the subject is; the rest is often a life story ("served in World War II") that dropped Salinger's novel and the F-86's first flight. A regression table of real items from the feed (02/29, 07/16, 08/06, 10/01, 11/09) keeps both kinds of mistake fixed.
- **Preferred:** a good picture +3 (a photo or painting), or +1 for a logo, seal, emblem, coat of arms or drawing (SVG); 2 for each kind of fun in the text (science and inventions, culture, sport, space and flight, openings and launches, nature and milestones: parks, gardens, islands, mountains, expeditions, canals, towers, independence, suffrage…); 1 for a "first"; 2 off for dry politics, law or unrest (treaty, court, parliament, police, arrest, protest, strike, ruling, viceroy…; not "congress", which as often founds a park).
- **The day's picks:** up to 5, best first, ties shuffled with the date as the seed, so a day's card is the same on every launch and the same date next year can lead with another. Items with no article to link are dropped; one told twice (the same year and an article in common) is kept once, also across the two lists; items scoring 1 or less go while at least 3 others are left; and no two picks share a decade while there are others. The curated `selected` list first; under 3 survivors, `events` makes up the rest.
- **Caching:** fetched once per local date (one entry in the ordinary store, replaced the next day), with the pick showing. Nothing is fetched ahead. Offline or failing, the card isn't shown (never an error, never yesterday's) and a resume doesn't ask again for 10 minutes; pull-to-refresh asks at once. If only `events` fails, the curated few show but aren't kept, and a later resume asks again. A load still running at midnight is replaced by the new day's.
- **Pictures:** only from Wikimedia Commons (never a non-free poster or logo from English Wikipedia), at least 150 px on the original's shorter side. A photo from the subject's article first; else one from another of the item's articles (one about the same thing first, "Allan Hills 84001" for "Allan Hills"); else a drawing; else words only. Wikimedia makes thumbnails only at standard widths (20, 40, 60, 120, 250, 330, 500, 960, 1280, 1920, 3840; others give HTTP 400): an original at least 960 px wide is asked for at 960, a smaller JPEG/PNG/WebP as the original itself, a drawing at 500, anything else at the largest step under the original; the feed's 330 px thumbnail is the fallback.
- **Loader** (`ImageLoader.kt`, rather than Coil): HttpURLConnection, rejecting anything that isn't `image/*`; decoded straight to the slot's size (`ImageDecoder.setTargetSize` on Android 9+, else sampled and scaled) and only then kept on disk (the 12 most recently used in `cacheDir/images`, by a SHA-256 of the URL; spoilt files are deleted and fetched again, and stale `.tmp` files cleaned up); a 10 MB memory cache by `allocationByteCount` (the one showing, the next and a fallback); one load per URL at a time; a failed URL isn't retried for 10 minutes, and a fallback that worked is remembered.
- **Layout:** the plain heading "On this day", like the other cards. In a `surfaceContainer` card, a full-width **hero** 180dp tall at the top (the card's corners clip it; a fixed height, so nothing jumps), in the skeleton tint until the picture fades in over 300 ms:
  - **Fill** (a landscape photo: not a drawing, at least 1.15:1 and 400 px on its shorter side): cropped to fill, top-biased (`BiasAlignment(0f, -0.5f)`).
  - **Poster** (everything else: portraits, squares, flags, seals, maps): the picture whole (`Fit`, 16dp padding, 8dp corners, a 1dp `outlineVariant` border) over a blurred copy of itself (`Modifier.blur(24.dp)` on Android 12+, else a 64 px copy box-blurred in software) under a `surfaceContainer` scrim (40% light, 55% dark).
  - Under it, with 16dp padding: "1975 · 51 years ago" (the year in `titleMedium`, the rest in `bodyMedium` `onSurfaceVariant`; "last year", "331 BC · 2,356 years ago"); the text in `bodyLarge`, at most 5 lines (7 at a font scale of 1.3 or more), "(pictured)" notes removed and cut to its first sentence or clause past 160 characters; the subject's title (`labelLarge`, primary, one line) with an "open in new" arrow.
  - The footer: "From Wikipedia · CC BY-SA" (`labelMedium`), a "Picture" link to the picture's Commons file page (its author and licence) when there's one, and "Another" at the end (only with more than one pick). At a font scale of 1.3 or more the credit takes its own line and the two buttons the one under it.
- **Motion:** the section opens out (`expandVertically` + fade) when the day's picks arrive. "Another" slides the next pick in (fade in 250 ms, sliding from an eighth of the width, while the old one fades out in 150 ms); the next pick's picture is fetched once the current one's is in, so it shows at once.
- **Tap:** the card opens the article (`content_urls.mobile.page`) through `tryOpen`; with no browser, the address shows in the card instead.
- **Screen reader:** the card is one node with the click label "Read on Wikipedia": "1975, 51 years ago. In boxing, Muhammad Ali defeated Joe Frazier… Muhammad Ali, on Wikipedia.", then "From Wikipedia, licensed CC BY-SA". It's a polite live region, so the new pick is read after "Another". The picture is decorative; "Picture" ("Picture's source on Wikimedia Commons") and "Another" ("Another moment from this day") are their own buttons.

**Future cards:**
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
2. **Home:** "Holidays and countdowns", "Your dates", "Daily weather meme", "Tonight's sky", "On this day on Home" (under Fun, on by default), and later Word of the day and Greeting. The copy should say "on Home" rather than "on the first page".
   - **Habits** (its own section, before Coming up): "Habits on Home", on by default.
3. **Weather:** a Places row that opens the Places screen. (The Activity choice is gone: "This week" scores days for being outside in general.)
4. **Gemma** (was "AI summary"): a "Use Gemma for the meme" switch, then the model section. Gemma no longer writes the weather summary; the template does.
5. **Footer:** "Daybreak 1.5.0".

There's no Clocks section until a clock setting exists.

## 6. Your dates and reminders

**Dates:** a day or a run of days with a label, day off and every year (as before), plus an optional time (wall-clock; for a run, the start time on the first day) and up to three reminders. Every date has a stable id (dates saved before ids get one when first read, made from their place in the list and what's stored, and it's written down). Tapping a date in Settings → Your dates, or one of yours (a date or a day off) in Coming up on Home (`clickable(onClickLabel = "Edit")`), opens the same editor prefilled; "Change" picks its days again. There's no "+" on Home. One `PersonalDateEditorHost` holds the editor and range-picker state for both.

**Editor:** a dialog like the habit editor: the date as its title with "Change", the label (wraps to two lines; "Needed unless it's a day off" only once the field has lost focus), Day off, Every year, an "Add a time" row (the shared `TimePickerDialog`, with the 57sp digits; once set, "Starts at 2:00 PM" with a remove cross, and "On the first day" under it for a run of days only), then "Remind me": multi-select `FilterChip`s (checkboxes to TalkBack with "On"/"Off" state), at most three, the rest disabled once three are on; "Custom…" stays in place, disabled, so nothing reflows. The presets swap with a 150ms cross-fade (`AnimatedContent`) when a time is added or taken off.
- All-day presets: "On the day", "1 day before", "1 week before". Timed: "When it starts", "15 minutes before", "1 hour before", "1 day before". The chips leave out 9 AM; a time of its own stays ("3 days before, 6 PM").
- Under the chips, the next one or two reminders worked out as the alarm will: "Next reminder: Mon, Sep 28 at 2:00 PM, then Tue, Sep 29 at 1:00 PM" (with the year when it isn't this one), then "Up to 3 for a date." once full.
- With a reminder under a day before (or any elapsed-time one) on a timed date and no exact alarms, the exact-alarm hint shows under the chips too.
- "Custom…": a number and Minutes/Hours/Days (timed) or Days/Weeks (all day) up to 8 weeks; for an all-day date "before, at 9 AM", where "at 9 AM" (bodyLarge, primary) opens the time picker and makes it `DaysBefore(n, at)`.
- Adding a time turns on the day into when it starts and keeps whole days; removing it turns anything under a day into on the day. A reminder at a time of its own keeps it either way.
- A new date starts with the reminders last saved with a date of its kind (all-day or timed; two prefs), none the first time. Until they're touched, adding or taking off the time swaps in the other kind's last picks.
- Buttons: "Remove" (error colour, only when editing) at the start; Cancel and Save at the end (they wrap under Remove at a large font on a narrow phone). Removing shows "Removed Mum's birthday" with "Undo" in the app's snackbar.

**Labels** use the comma form: "On the day, 9 AM", "3 days before, 6:30 PM", "When it starts", "36 hours before".

**List row:** the label (bodyLarge), "Tue, Sep 29 · 2:00 PM · every year · day off" (bodySmall), and the reminders on their own line after a bell (`Icons.Outlined.Notifications`, 16dp at the default font size, 4dp gap), nearest first with a shared tail: "1 hour and 1 day before", "On the day, 3 days and 1 week before" (one at a time of its own: "3 days before at 6 PM"). A decorative chevron at the end; the whole row is one TalkBack node with "Reminders: …" and "Edit" as its action. **Coming up** shows the time after the day ("Tue, Sep 29 · 2:00 PM").

**Hints** (under the dates; the exact-alarm one in the editor too), one shape: a 16dp icon, a bodySmall line, and a `TextButton` under it with a 48dp target.
- Reminders set but notifications off (or the channel blocked): warning icon, "Notifications are off for Daybreak, so reminders won't show.", "Turn on notifications" (the app's notification settings).
- An elapsed-time reminder on a timed date and no exact alarms: clock icon, "Reminders may run a few minutes late.", "Allow exact timing" (Alarms & reminders).
- Both re-checked whenever the app resumes.

**Your dates' description:** "Birthdays, big days, time off — counted down on Home once they're within four months, with a reminder if you like. Days off count as a break. Kept on this phone, not backed up."

**Scheduling:** one AlarmManager alarm for the soonest reminder of all dates: `setExactAndAllowWhileIdle` when `canScheduleExactAlarms()`, with an inexact `setAndAllowWhileIdle` backup for the same moment under another request code (revoking exact alarms cancels them without a broadcast); else just the inexact one. Worked out again on every change, alarm, restart (BOOT_COMPLETED or QUICKBOOT_POWERON), clock or zone change, app update, start of the app and exact-alarm permission change, always off the main thread (`goAsync` and one background executor) and one run at a time (a lock around the run). Times are resolved in the phone's zone each time: a skipped time moves on by the gap, a doubled one is the first; whole days keep the clock time, minutes and hours are elapsed. Past reminders are never shown in a burst:
- when the alarm goes off or after a restart (whose broadcast only comes at unlock): those of the last two hours;
- on anything else: the one the set alarm promised and any since, once its time has passed; one added after its time was never promised and is skipped;
- whatever the trigger, the promised reminder still shows however late while its date is on (today, or a run of days under way), as standby buckets can hold an alarm back for hours;
- after a change of zone, a reminder still to come in the old zone (after the last run) that has passed in the new one (flying east) shows once while its date is on.
The next alarm and what's been dealt with are saved before anything is posted, and a notification that fails doesn't stop the rest. What went off is remembered for three days by date, time round and local time, so a flight west doesn't repeat it, and a doubled delivery (exact and backup) shows nothing twice.

**Notifications:** channel "Reminders" (default importance; name and description in string resources), a monochrome bell, the label as title and "Tomorrow", "In 1 week · Saturday, Oct 10", "In 15 minutes · 2:00 PM", "Today at 2:00 PM", "Starting now" (up to 5 minutes after), "Started at 2:00 PM" (later than that), "Today", "Today – Wed, Oct 14" (a run's first day) or "Until Wed, Oct 14" as text, timestamped with when it was due. One id per date and time round from a stored counter (not a hash), so a later reminder replaces the earlier; a tap opens Home, also when the app is restored from saved state, and only once (the extra is taken off the intent). POST_NOTIFICATIONS is asked for when a date is first saved with a reminder.

## 7. The name

- The launcher label is "Daybreak". The app icon stays as it is for now.
- No top bar shows the app name. The name appears only in the launcher, the Settings footer, and the first-run empty hero: "Daybreak / Your day, at a glance".
