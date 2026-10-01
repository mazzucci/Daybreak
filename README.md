# Daybreak

A personal Android app for the start of your day: the weather for where you are and the places you save (in both °F and °C), holidays and your own dates, the habits you're building or breaking, your clocks, and a few fun things, with an optional on-device Gemma model writing the daily meme. (It began as a weather app; it now has the tabs Home · Weather · Habits · Clocks · Settings, see [docs/design/shell.md](docs/design/shell.md).)

> **Coming from the old "Weather" app?** Daybreak has a new application id (`app.daybreak`), so it installs as a separate app and starts empty: add your places again, import or download the Gemma model again, and re-add your dates. Then uninstall the old Weather app (its widget and background refresh go with it).

- Habits: as many as you like, each with a name, a colour, and a goal: something to **build** (drink water 8 times a day, ride a bike once a week) or to **avoid** (no takeout, or at most two coffees a day). Tap +1 to log (or "Had one" for a slip); long-press it or tap − to take one back. Build habits show today's or this week's progress as a ring and the current and best streak; avoid habits show the days since the last one and whether the allowance is kept this week, so a slip never reads as a broken streak. Each has a 12-week map in its colour. Points for each log (up to the goal) and each day or week the goal is met or the allowance kept, with bonuses when a run reaches 7, 30 and 100, add up to a level, and a few badges mark firsts and long runs; meeting a goal or a milestone gets a one-line cheer in place ("7-day streak! +61"). Changing a goal applies from that day on, never to the past. Weeks start on your region's first day, set when you add your first habit and changeable in Settings → Habits. Today's habits are also on Home, to log with a tap (turn that off in Settings → Habits). Habits are kept on the phone and aren't backed up
- Tonight's sky (on Home): the moon drawn as it is tonight with its phase and how much is lit, the next full moon by its old name (the Harvest Moon, the Hunter's Moon…), the next meteor shower when one peaks within two weeks (and whether a bright moon will wash it out), and whether the forecast says it's clear enough to look up. All worked out on the phone. Turn it off in Settings → Fun
- On this day (on Home): one pleasant or interesting moment from today's date in history, from Wikipedia's "On this day" feed (free, no key; Wikipedia and its image servers see your IP address and the date, nothing else). A free picture from Wikimedia Commons runs across the top of the card when there's a good one (a landscape photo fills it; a portrait, flag or seal is shown whole over a blurred copy of itself), then "1975 · 51 years ago", what happened, and the article to read. Grim items (violence, disasters, deaths, persecution, wars) are filtered out by a tested word list, checked against real items it used to get wrong, and the rest are scored so that science, culture, sport, space, nature and milestones, firsts, openings and good pictures come first, with no two from the same decade. The day's picks (up to five; "Another" slides through them) are fetched once a day, the same all day, and kept with the one showing; pictures are cached on the phone, and a "Picture" link credits each one on Commons. Offline, the card simply isn't there; pull to refresh to try again at once. Tap it to read the article. Turn it off in Settings → Fun
- Clocks: the places you call or work with, each with its time, whether it's today or tomorrow there, how far ahead or behind you it is and its UTC offset, following daylight saving. A converter shows one moment in every clock ("At 12:00 PM today in Los Angeles it's 10:00 PM in Bucharest"). Places come from the same search as Weather, which gives each one's time zone
- Search for any city and save it; swipe between places, reorder or remove them
- Current location is optional: turn it off and use saved places only
- A one-line summary at the top, a large temperature in your preferred unit with the other unit alongside, today's high/low and rain (chance and, when there's enough to mention, the day's total: "Rain 60% · 4 mm"), and the next 12 hours with each hour's chance and amount, and how warm or cold it feels once any hour is 3° or more away from the thermometer ("feels 54°")
- Today's sunrise, sunset, UV index, wind with its direction ("↗ from the SW") and gusts, plus a 10-day list with each day's range on a shared scale and its rain or snow total ("4 mm", "3 cm snow"); the last three days are drawn lighter as less certain, with their date
- Tap any day for its details: the day's condition, high and low in both units and feels-like range; the temperature hour by hour; a rain (or snow) card with a verdict ("Rain likely · about 12 mm over 6 hours"), when it falls ("Mostly in the afternoon, heaviest around 4 PM."), a bar per hour with the chance under it, and the parts of the day (before sunrise, daytime, evening) that add up to the verdict, plus a line when it carries on after midnight; the day's wind, gusts and direction; sunrise, sunset and UV
- One set of rules for rain everywhere ([`Precip.kt`](app/src/main/java/app/daybreak/domain/Precip.kt)): hourly chances from 10% (blue from 40%), daily chances from 20%, hourly amounts from 0.1 mm and day totals from 0.5 mm, an amount only with a 20% chance or from 1 mm ("A small chance of rain · up to 2 mm"); "likely" from 70%, "possible" from 40%. One classifier (dry, possible or likely) judges every day, part of a day and hour from its chance and amount together. Amounts are in mm (snow in cm) with °C and inches with °F, never both
- "Updated 8 min ago" on each Weather page, from when the app fetched it; past 90 minutes it turns amber and suggests pulling to refresh. A refresh that fails keeps the forecast and says so: "Couldn't refresh · updated 2 hours ago"
- Temperatures in both units: the primary one large, the other small alongside or underneath (current, feels-like, hourly strip, 10-day list and day details); screen readers hear both
- "This week": plain advice about being outside, worked out from rules and the forecast (no model). A line about today ("Great day to be outside, best 1–5 PM", "Mixed day: dry until 2 PM, then showers", "Hot day: 34° by 3 PM, best before 11 AM"; in the evening it's about tomorrow, and it's worked out for the hour you look), up to two about the week ("Rainy spell from tomorrow until Monday", "Saturday is the best day this week", "First frost by Tuesday morning"), and a strip of the next 7 days with a bar per day rising from a shared baseline by its score, green for great or good days and blue-grey otherwise, a rain or snow glyph under wet days and "Best" under the best day (also marked in the 10-day list); tap a day for its details, or "How it works" for the rules. Each day scores 0–100 on its best 3 daylight hours in a row (rain, wind and gusts, and temperatures outside 12–26°C / 54–79°F cost points), and its rain words come from the same rules as the rest of the app. The today line also sits quietly under the weather glance on Home
- Tap a tile (Feels like, Humidity, Wind, UV index, Sunrise/Sunset), the Rain pill or a day's rain card for a plain-language explanation of the term and of the value ("Colder than the air: the 19 mph wind carries heat away from your skin")
- A home-screen widget (4×2 by default, resizable down to 2×1) with the first page's place, the temperature in both units, today's high, low and rain chance, and the summary line on the same sky colour as the app; smaller sizes keep the icon and temperature and drop the rest. It mirrors the app whenever you open it and refreshes the numbers and summary every couple of hours in the background. Location isn't read there: the widget keeps the place the app last showed. Its cache (with that location) is excluded from backup
- "Coming up": the next public holiday and long weekend in each place's country (including when a day of leave makes a 4-day weekend), the next season, and that day's forecast when it's within the 10-day forecast. Holidays come from [Nager.Date](https://date.nager.at) (free, no key; it sees your IP address and each place's country, and nothing else) and are cached for a month. Only nationwide holidays are shown, so countries whose holidays are mostly regional (the UK, for example) show fewer. Add your own dates there too (a birthday that comes round every year, a big presentation, a week off): the next few count down on the first page alongside the holidays, and a day off says what kind of break it makes ("Makes a 4-day weekend"). A date can have a time ("Presentation · Thu, Oct 1 · 2:00 PM") and up to three reminders: on the day, a day or a week before for an all-day date; when it starts, 15 minutes, an hour or a day before for a timed one; or a custom number of minutes, hours, days or weeks (an all-day date's at a time of its own, 9 AM unless you change it). The editor says when the next ones go off ("Next reminder: Mon, Sep 28 at 2:00 PM, then Tue, Sep 29 at 1:00 PM"), and a new date starts with the reminders you last picked for that kind of date. A yearly date reminds you every year. Reminders are notifications ("Mom's birthday · Tomorrow", "Presentation · In 15 minutes · 2:00 PM", "Starting now") that open Home, at wall-clock times in whatever time zone the phone is in, so 9 AM stays 9 AM after a flight. Tap a date in Settings, or one of yours in Coming up on Home, to edit or remove it (with Undo). They stay on the phone and aren't backed up. Turn it all off in Settings → Coming up
- A daily weather meme per place, made entirely on the phone (no network): a hand-written caption for the day's mood, or a fresh one from Gemma once it's set up. Turn it off in Settings
- Optional on-device [Gemma](https://ai.google.dev/gemma) model for the meme's caption, run locally with [MediaPipe LLM Inference](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference). No data leaves the phone
- Weather and place search from [Open-Meteo](https://open-meteo.com/) (free, no API key). The app fetches 10 days of hourly and daily data per place in one request, including feels-like, rain and snow amounts, hours of rain, wind with its direction, gusts, sun times and UV
- Uses Android's built-in location service (no Google Play Services required)
- Reminders use one AlarmManager alarm for the soonest one due, exact where Android allows it (Settings → Alarms & reminders; otherwise "Reminders may run a few minutes late" with "Allow exact timing" shows under your dates and in the editor), with an inexact backup in case exact alarms are taken away, and are set again after a restart, a clock or time-zone change and an app update. A late alarm, a late unlock after a restart, or a flight east past a reminder's time still delivers the reminder that was due, once; anything older is skipped rather than shown in a burst. Notifications are asked for when you first save a date with a reminder (Android 13 and up)
- Kotlin + Jetpack Compose, min Android 8.0 (API 26)

## Screenshots

The backdrop follows the conditions and the time of day at each place (clear, cloudy, rain, snow, storm; day or night, from each place's real sunrise and sunset), and the app has its own light and dark palettes.

| Weather | Dark | Home | Rainy night |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_weatherLight_weather_light.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_weatherDark_weather_dark.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_homeFull_home_full.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_weatherRainyNight_weather_rainy_night.png" width="200"> |

| First run | Loading | Permission | Error |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_empty_empty.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_loading_loading.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_permission_permission.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_error_error.png" width="200"> |

| Search | Places | Settings: set up Gemma | Settings: downloading |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_search_search.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_places_places.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_settingsNotInstalled_settings_not_installed.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_settingsDownloading_settings_downloading.png" width="200"> |

| Full page | Full page (dark, °C) | Rain and snow amounts | Weather memes |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_weatherFullPage_weather_full_page.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_weatherFullPageDark_weather_full_page_dark.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_weatherAmounts_weather_amounts.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_memeMoods_meme_moods.png" width="200"> |

| Day: showers | Day: snow (°F) | Day: dry | Day (dark) |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_dayWet_day_wet.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_daySnowy_day_snowy.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_dayDry_day_dry.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_dayDark_day_dark.png" width="200"> |

| This week | This week (dark) | This week, evening | This week, 2x font |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_weekOutlookMixed_week_outlook_mixed.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_weekOutlookDark_week_outlook_dark.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_weekOutlookEvening_week_outlook_evening.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_weekOutlookHugeFont_week_outlook_huge_font.png" width="200"> |

| Your dates | Edit a date | A birthday's reminders | A custom reminder |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_datesCard_dates_card.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_dateEditorTimed_date_editor_timed.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_dateEditorNextReminders_date_editor_next_reminders.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_customReminderWithTime_custom_reminder_with_time.png" width="200"> |

| Habits | Habits (dark) | Add a habit | Habits on Home |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_habitsList_habits.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_habitsDark_habits_dark.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_habitEditorNew_habit_editor_new.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_habitsHomeCard_habits_home_card.png" width="200"> |

| On this day: photo and portrait | Drawings (dark) | Words only, 331 BC | Large font (1.5x, 320dp) |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_onThisDayFill_on_this_day.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_onThisDayPosterDark_on_this_day_poster_dark.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_onThisDayTextOnly_on_this_day_text_only.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_onThisDayLargeFont_on_this_day_large_font.png" width="200"> |

| Settings: installed | Settings: failed (dark) | Rainy night (dark) | App icon |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_settingsInstalled_settings_installed.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_settingsFailed_settings_failed.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_weatherRainyNightDark_weather_rainy_night_dark.png" width="200"> | <img src="app/src/test/snapshots/images/app.daybreak.ui_ScreenshotTest_appIcon_app_icon.png" width="120"> |

These are rendered from the app's real UI code with sample data using [Paparazzi](https://github.com/cashapp/paparazzi). Weather icons and the launcher icon are drawn in code and as vector drawables, so there are no bitmap assets; the "On this day" pictures in the screenshots are stand-ins drawn by the test (a landscape, a portrait, a flag and a seal), since screenshot tests don't use the network. To regenerate the screenshots after UI changes:

```
./gradlew recordPaparazziDebug
```

`./gradlew verifyPaparazziDebug` fails if the UI no longer matches them.

## On-device Gemma meme

The summary line at the top of each page is a built-in template ("71° and partly cloudy now, with a high of 74°…"). Gemma doesn't write it; it writes the caption of the daily weather meme on Home. Without Gemma, a hand-written caption is used.

To set it up, go to **Settings → Gemma** and follow the three steps on screen:

1. Open the model page, [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT), and accept the Gemma license (free Hugging Face account).
2. Create a Hugging Face access token with read permission and paste it into the app. The token is only used once, to start the download; it isn't stored.
3. Download the model (about 550 MB, Wi-Fi recommended). The download runs through the system download manager, so it carries on in the background (and resumes being tracked if the app is closed), and the file is checked against the SHA-256 checksum Hugging Face reports (or its expected size, if no checksum is given) before being installed.

If you already have `gemma3-1b-it-int4.task` on the phone, **Import file…** copies it into the app's own storage instead, so you can delete the original afterwards.

How it works:

- The model runs on the CPU through MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai`), with a 30-second timeout. It's loaded on first use and released after a few idle minutes.
- The prompt ([`Meme.kt`](app/src/main/java/app/daybreak/narration/Meme.kt)) describes the day in words only, and is sampled with a playful temperature and a per-day seed. [`MemeValidator`](app/src/main/java/app/daybreak/narration/Meme.kt) accepts only a two-line `TOP:`/`BOTTOM:` caption with no digits, emoji or rude words; the meme is marked "Written by Gemma on this device".
- Gemma gets one try per place, day and mood; the result, or the hand-written caption if it's rejected or the model is missing, slow or errors out, is cached (`MemeRepository`) so the meme stays the same all day. Gemma's memes need both **Daily weather meme** and **Use Gemma for the meme** switched on.
- Expect a few seconds per caption on recent phones and longer on older ones. MediaPipe's native library makes the APK bigger, so it's built only for 64-bit ARM (phones) and x86_64 (emulators). It needs a 64-bit device.

## Code layout

```
app/src/main/java/app/daybreak/
  domain/     Place, Forecast, AppSettings; unit conversion and formatting; WMO code descriptions;
              Precip (the one rule set for rain and snow: thresholds, the classifier, words, amounts, a day's timing and parts);
              OutdoorScorer (an hour's score for being outside); WeekOutlook ("This week": day scores, tiers, spells,
              the best day and the lines); ComingUp (holidays, long weekends, seasons);
              Habits (streaks, days since, allowances, points, levels, badges, the 12-week map);
              OnThisDay (the grim filter, scoring, the day's picks, the subject and its Commons picture, text tidying);
              Reminders (presets, when each goes off with daylight saving, the notification text)
  data/       HttpClient, Open-Meteo forecast + geocoding API and JSON parsers,
              saved places, settings, clocks, habits and daily meme repositories (SharedPreferences), device location,
              Nager.Date holiday API + HolidayRepository (cached per country and year),
              Wikipedia "On this day" API + OnThisDayRepository (cached per day), ImageLoader (a tiny picture loader: decodes to the slot's size, disk and memory caches)
  narration/  TemplateNarrator (the summary line), GemmaNarrator (MediaPipe),
              GemmaModelStore (model download + import),
              Meme (mood, template captions, prompt, validator, MemeWriter)
  ui/         WeatherViewModel, ClocksViewModel, HabitsViewModel and OnThisDayViewModel (StateFlow), stateless screens (DayScreen: a day's details), WeatherApp (navigation, pickers),
              Theme (palettes, type), Sky (condition -> backdrop), WeatherIcons (Canvas-drawn glyphs),
              MemeCard, WeekOutlookCard ("This week" and its 7-day strip), OnThisDayCard,
              PersonalDates (Your dates, the date editor shared by Settings and Home, its reminders)
  widget/     WeatherWidget (Jetpack Glance), its receiver, WidgetRefreshWorker (WorkManager), WidgetPublisher
  reminders/  ReminderScheduler (the one alarm, what's due, what's skipped; JVM-tested with fakes),
              AlarmManager and notification glue, ReminderReceiver (alarm, boot, time and zone changes, updates;
              runs off the main thread). The glue isn't unit-tested (no Robolectric): it's checked on a phone
```

## Build and test

Requires JDK 17 and the Android SDK (`ANDROID_HOME` or `local.properties` pointing at it).

```
./gradlew testDebugUnitTest verifyPaparazziDebug assembleDebug
```

[GitHub Actions](.github/workflows/ci.yml) runs the same command on every pull request and push to `main`, and uploads the test reports and screenshot diffs if a test or screenshot check fails.

The unit tests cover JSON parsing (with real Open-Meteo and Wikipedia responses as fixtures), the "On this day" filter (with a regression table of real items), scoring, picks, pictures, per-day cache and picture loader, formatting, the rain rules and the day page's verdict, timing and the parts of the day, the "This week" outlook (spells, the best day, the today line in the evening and under polar night or day, with real and made-to-order forecasts), the template narrator, meme caption validation, habit streaks, points and badges, reminders (when they go off across daylight saving, leap years and time zones, through full cycles of alarms; what's shown or skipped after an alarm, a late or doubled one, a restart, a change or a flight; the notification and editor text; storage of old and new dates and their ids), the repositories and the ViewModels (with fakes). The APK ends up in `app/build/outputs/apk/debug/`.

## License

[MIT](LICENSE)
