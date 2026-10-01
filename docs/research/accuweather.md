# Daybreak's Weather experience vs AccuWeather: UX deep research

Date: 1 October 2026. Daybreak read from the read-only copy at `/mnt/data/projects/Daybreak-main` (not the working tree). AccuWeather researched from its website (fetched 30 Sep / 1 Oct 2026 through a reader proxy, since accuweather.com returns HTTP 403 to plain fetches), its developer API documentation and open-source API fixtures, its press releases, the App Store and Play Store listings, and reviews on Trustpilot, the App Store, Google Play and review roundups. Open-Meteo variable names were verified against the live API on 30 Sep 2026 as well as the docs.

Contents

1. Summary
2. How AccuWeather is put together (app and website)
3. AccuWeather's full feature inventory
4. Precipitation in AccuWeather, in fine detail
5. What reviewers praise and what they find cluttered
6. Daybreak today
7. Feature × AccuWeather × Daybreak table
8. Precipitation deep-dive: AccuWeather vs Daybreak vs what Open-Meteo can give us
9. Recommendations: how Daybreak should show rain
10. What not to copy, and why
11. Sources

---

## 1. Summary

AccuWeather is a maximalist, ad-funded weather portal with four tabs (Today, Hourly, Daily, Radar & Maps), nine or more website sub-tabs per place, proprietary indices (RealFeel, RealFeel Shade, AccuLumen, Indoor Humidity, 50+ lifestyle indices), radar/satellite/lightning maps, hurricane and winter centres, news and video, three notification systems, ten Android widgets, and two paid tiers. Its one feature users consistently love is **MinuteCast**: a radar-nowcast that answers "will it rain in the next two to four hours, and when does it start or stop?" in one sentence plus a coloured minute-by-minute bar or dial. Its most consistent complaints are **ads that block the forecast**, an **August 2025 redesign reviewers call "an incomprehensible set of data"**, **notification spam**, and MinuteCast promising a precision ("rain starting in 15 minutes") it cannot always deliver.

On precipitation specifically, AccuWeather shows, at every level, **probability and amount side by side**, split by **Day and Night**: `Probability of Precipitation 99%`, `Probability of Thunderstorms 59%`, `Precipitation 0.85 in`, `Rain Amount 0.85 in`, `Total Hours of Precipitation 4`, `Total Hours of Rain 4`, `Cloud Cover 99%`. The hourly view shows the chance on the collapsed row and the amount (`Rain 0.14 in`) only when it is non-zero inside the expanded row. That trio of **chance + amount + duration** is the thing worth learning from. The thirteen-line Day/Night panels, the duplicated fields, the 0%/1%/3% chances printed on every day, and the paywalls are the things to leave behind.

Daybreak today shows **only probability**: the hero pill `Rain 60%` (today's max hourly chance), a `%` under every hourly cell, a `%` under the icon on days ≥ 20%, and a sentence in the explain sheet that already mentions the daily total (`About 6.5 mm expected in total.`) because we already fetch `precipitation_sum`. We do not show hourly amounts, hours of rain, snow, or any day/night split, and we fetch none of `precipitation`, `rain`, `showers`, `snowfall`, `precipitation_hours`, `snowfall_sum` or `minutely_15`, all of which Open-Meteo provides free and whose names and units I verified (section 8).

Top recommendations (details and wireframes in section 9):

1. Fetch hourly `precipitation` and `snowfall`, daily `precipitation_hours`, `snowfall_sum` and `precipitation_probability_mean`; keep `precipitation_sum` and the probabilities we have. Skip `minutely_15` for now (coverage is only Central Europe and North America, it is model output not radar, and it carries no probability).
2. Show **chance and amount together, with the amount gated**: amounts appear only when ≥ 0.1 mm for an hour (the same threshold Open-Meteo's probability is defined on) and ≥ 0.5 mm for a day; otherwise the chance stands alone. Units follow the °F/°C setting: `mm` with °C, `in` with °F; snow in `cm`/`in`.
3. Hourly cell: keep the `%` line, add a one-line amount under it (`0.6 mm`) only when gated in, and stop printing `0%`: hide chances under 10% (keep the line's height so cells stay level).
4. Daily row: `%` under the icon as now; add the daily total at the right end of the row, muted (`4 mm`), when gated in; snow days say `3 cm snow`.
5. Day-details page: a **Rain** card with a one-sentence verdict (`Rain likely · about 6 mm over 5 hours, mostly in the afternoon`), a 24-bar hourly amount chart with the chance under it, and **Daytime / Overnight** rows derived from our hourly data (AccuWeather's Day/Night idea, reduced to two lines).
6. Drive the new **"This week"** outlook from the same three numbers (probability max, sum, hours) with explicit thresholds, so the sentence, the row and the details page never disagree.
7. Do not copy MinuteCast's minute-level promise, the thirteen-line panels, the printed 0% chances, thunderstorm probability as a separate figure, or anything that needs ads, accounts or tiers.

---

## 2. How AccuWeather is put together

### 2.1 The app (iOS and Android)

- **Tabs**: Today, Hourly, Daily, Radar (& Maps), since the July 2020 redesign that "puts AccuWeather MinuteCast front and center" and added a "Looking Ahead" section ([AccuWeather press release, 20 Jul 2020](https://www.accuweather.com/en/press/accuweather-launches-redesigned-app-with-enhanced-features-and-better-user-experience/779751); [MediaPost](https://www.mediapost.com/publications/article/353838/accuweather-redesigns-app-based-on-user-feedback.html)). The Today screen's top element is the **MinuteCast dial** with the temperature and RealFeel inside it ("The 'RealFeel®' reading is placed under the more prominent temperature within your MinuteCast dial", [App FAQ](https://www.accuweather.com/en/weather-news/accuweather-app-faq/765568)).
- **August 2025 redesign (v21)**: "over 50 new and enhanced features"; "everything users need on one streamlined Today Screen, all accessible with the swipe of a finger"; "more than a dozen new maps"; up to nine saved locations free, twelve with Premium+ ([AccuWeather, 27 Aug 2025](https://www.accuweather.com/en/weather-news/accuweather-launches-improved-app-with-over-50-new-and-enhanced-features/1809513)). Android Police described the beta as "a noticeably more colorful interface, with elements now placed on top of large, translucent cards, including the current weather wheel", icons "switching from hollow white outlines to colorful, filled-in graphics", a "pill-shaped picker" for location and the hamburger menu moved right ([Android Police](https://www.androidpolice.com/accuweather-facelift-beta/)). Release notes for 21.0.3 list: "A new Home for your favorite places, all in one view", "Your choice of MinuteCast views, Dial or Chart", "Tailor notifications for alerts, lightning and news", "Monitor daily health risks in a new calendar view", "Login to unlock longer range forecasts and more saved locations" ([App Store version history](https://apps.apple.com/us/app/accuweather-weather-forecast/id300048137)).
- **2026 additions** (21.0.22 to 21.0.26): "Know if lightning is nearby, right from Today", "17 health indicators across Today, Daily & Daily Details", "Hurricane Center's new Expert Analysis", "Today & Tomorrow forecast notifications", and "BETA experiences" to "Plan pet walks, peak foliage, sunscreen & bus-stop outfits"; "Coming Soon - Premium+ Family Plans" ([App Store version history](https://apps.apple.com/us/app/accuweather-weather-forecast/id300048137)).
- **Display modes**: Light (48 "real-time weather backgrounds"), Dark, Black ([App FAQ](https://www.accuweather.com/en/weather-news/accuweather-app-faq/765568)).
- **Ratings**: App Store 4.6 (1.5M ratings); Google Play 3.6 (2.38M reviews), "#3 top grossing weather" ([App Store](https://apps.apple.com/us/app/accuweather-weather-forecast/id300048137); [Google Play](https://play.google.com/store/apps/details?id=com.accuweather.android&hl=en_US)).

### 2.2 The website (per place)

Sub-tabs on every place page: `Today · WinterCast · Local {storm} Tracker · Hourly · 10-Day · Radar · MinuteCast® · Monthly · Air Quality · Health & Activities`, plus global `Hurricane Tracker · Severe Weather · Radar & Maps · News · Video · Winter Center`. A banner on every page: "Create Your Account. Unlock extended daily and hourly forecasts — all with your free account", and a `Get Premium+` button (fetched 1 Oct 2026, e.g. [Dallas weather-tomorrow](https://www.accuweather.com/en/us/dallas/75202/weather-tomorrow/351194)). The 10-Day page carries "Unlock extended 90-day forecasts. Activate Your Premium+ Subscription"; the Hourly page "View hourly forecasts 10-days ahead. Activate Your Premium+ Subscription" ([NYC 10-day](https://www.accuweather.com/en/us/new-york/10021/daily-weather-forecast/349727); [Miami hourly](https://www.accuweather.com/en/us/miami/33128/hourly-weather-forecast/347936)).

### 2.3 Paid tiers

| | Launch (May 2022) | App Store (Oct 2026) |
|---|---|---|
| Premium | $0.99/mo, $8.99/yr | $1.99/mo, $12.99/yr |
| Premium+ | $1.99/mo, $19.99/yr | $4.99/mo, $29.99/yr |

Premium removes ads. Premium+ adds **AccuWeather Alerts™** (meteorologist-triggered, three stages: "Potential" for advanced preparation, "Threat" for preparedness to act, "Imminent" when it's time to act), **10-Day Hourly Forecast Graphics / HourCast™**, **90-day daily forecasts**, **Premium Widgets** (Hurricane widget, Air Quality lock screen widget), customised health and activity views, 12 saved locations, and on Android the **temperature in the persistent status-bar notification** (free users see only the icon since 2022). Sources: [PR Newswire, 4 May 2022](https://www.prnewswire.com/news-releases/accuweather-introduces-premium-tier-of-award-winning-app-with-advanced-lifesaving-features-for-threatening-severe-weather-301539832.html); [9to5Google](https://9to5google.com/2022/04/13/accuweather-weather-notifications-subscription/); [XDA](https://www.xda-developers.com/accuweather-hyperlocal-premium-plus/); [Google Play listing](https://play.google.com/store/apps/details?id=com.accuweather.android&hl=en_US); [App Store listing](https://apps.apple.com/us/app/accuweather-weather-forecast/id300048137). One reviewer noted that the old one-off Premium purchase "doesn't get you anything anymore except just ad free. All the best options are subscription now" (Google Play, 11 Aug 2025, 656 helpful votes).

---

## 3. AccuWeather's full feature inventory

Grouped; each item names where it lives and the source.

**Forecast core**
- Current conditions: temperature, RealFeel®, RealFeel Shade™, humidity, wind, gusts, dew point, pressure, visibility, cloud cover, cloud ceiling, UV index, AccuLumen Brightness Index™, Indoor Humidity, Heat Index ([Miami hourly](https://www.accuweather.com/en/us/miami/33128/hourly-weather-forecast/347936); [API fixture](https://raw.githubusercontent.com/bieniu/accuweather/master/tests/fixtures/hourly_forecast_data.json)). "Current weather conditions update every 15 minutes" ([Amazon Appstore listing](https://www.amazon.com/AccuWeather-with-Superior-AccuracyTM/dp/B005K17RU0)).
- **RealFeel®** (sun) and **RealFeel Shade™**: "the only feels-like temperature based on 10 factors, including the impact of sunshine intensity, wind, humidity and air density" ([AccuWeather, 27 Aug 2025](https://www.accuweather.com/en/weather-news/accuweather-launches-improved-app-with-over-50-new-and-enhanced-features/1809513)). Each has a **RealFeel Guide** category with a range and advice, e.g. "Very Warm · 82° to 89° · Older adults, infants, and those with sensitive medical conditions should minimize outdoor activity, especially in the sunshine." or "Pleasant · 63° to 81° · Most consider this temperature range ideal." ([Dallas](https://www.accuweather.com/en/us/dallas/75202/weather-tomorrow/351194)).
- **Hourly**: 72 hours free in the app (FAQ), "through the next 10 days" on the web with an account/Premium+, expandable rows (section 4.5).
- **Daily**: 15-day in the app, "10-Day" on the web, "Monthly" 45-day (now marketed as 90-day with Premium+) ([App FAQ](https://www.accuweather.com/en/weather-news/accuweather-app-faq/765568); [NYC 10-day](https://www.accuweather.com/en/us/new-york/10021/daily-weather-forecast/349727)).
- **Daily Details** page per day with **Day** and **Night** panels, Morning/Afternoon/Evening/Overnight sub-periods, Sun & Moon, Temperature History (section 4.4).
- **Headline** phrases per period, e.g. "Cloudy and humid with a couple of heavy thunderstorms; thunderstorms can bring flash flooding and localized damaging wind gusts" and a daily-forecast headline object ("Expect rainy weather Monday afternoon through Monday evening", `Severity`, `Category: rain`) ([Dallas](https://www.accuweather.com/en/us/dallas/75202/weather-tomorrow/351194); [API fixture](https://raw.githubusercontent.com/bieniu/accuweather/master/tests/fixtures/daily_forecast_data.json)).
- **Temperature History** on each day page: Forecast / Average / Last Year / Record high and low with years ([NYC day 5](https://www.accuweather.com/en/us/new-york/10021/daily-weather-forecast/349727?day=5)).

**Precipitation**
- **MinuteCast®** minute-by-minute precipitation type, intensity, start and end times, "over the upcoming four hours" (marketing) / 120 minutes (API and web page), refreshed every five minutes, 210 countries and territories (section 4.3).
- **Probability of Precipitation**, **Probability of Thunderstorms**, and in the API rain/snow/ice probabilities; **Precipitation**, **Rain Amount**, **Snow**, **Ice** amounts; **Total Hours of Precipitation / Rain / Snow / Ice**; **PrecipitationIntensity** Light/Moderate/Heavy (section 4).
- **WinterCast®** / Snow and Ice Outlook: "forecasted snow and ice accumulations, timing and intensity, all illustrated graphically", up to five days; **Snow Probability** ("the chance for a foot vs. an inch of snow"); **Snow Day Forecast** (school closure chance, "Closures Tomorrow, 10/1 0%") ([MinuteCast 4-hour press release, Dec 2020](https://www.accuweather.com/en/press/accuweather-extends-minutecast-from-2-to-4-hours-most-lengthy-most-accurate-minute-by-minute-forecast-available-anywhere-in-the-world/866154); [NYC WinterCast](https://www.accuweather.com/en/us/new-york/10021/winter-weather-forecast/349727)).

**Maps and radar**
- Radar with a two-hour future animation; layers for clouds, temperature, **RealVue™ satellite**, **Lightning Network™**, **Air Quality Index**, **Smoke Index**, **Wind Flow**; "Simulated radar displayed over oceans, Central and South American countries is generated from satellite data" ([App FAQ](https://www.accuweather.com/en/weather-news/accuweather-app-faq/765568); [AccuWeather, Aug 2025](https://www.accuweather.com/en/weather-news/accuweather-launches-improved-app-with-over-50-new-and-enhanced-features/1809513); [Miami MinuteCast page](https://www.accuweather.com/en/us/miami/33128/minute-weather-forecast/347936)).
- **Hurricane Tracker / Hurricane Center** with "Expert Analysis"; **Local {storm} Tracker** tab; **Severe Weather** centre; **Winter Center**.

**Health, air and lifestyle**
- **Air Quality**: AQI with hourly forecast, "by the minute", Plume Labs data; a reviewer praises it as the "Only AQI that includes the Northwest's major pollutant" (Trustpilot, 7 Oct 2025).
- **Allergies**: Tree Pollen, Ragweed Pollen, Mold, Grass Pollen, Dust & Dander (Low/Moderate/High/Extreme).
- **Health**: Arthritis, Sinus Pressure, Common Cold, Flu, Migraine, Asthma; "17 health indicators" in the app; a calendar view.
- **Outdoor Activities**: Fishing, Running, Golf, Biking & Cycling, Beach & Pool, Stargazing, Hiking (Fair/Good/Ideal). **Travel & Commute**: Air Travel, Driving. **Home & Garden**: Lawn Mowing, Composting, Outdoor Entertaining. **Pests**: Mosquitos, Indoor Pests, Outdoor Pests ([NYC Health & Activities](https://www.accuweather.com/en/us/new-york/10021/health-activities/349727)). The data product behind this has "50+ proprietary health, lifestyle & activity indices" ([AccuWeather blog](https://www.accuweather.com/en/blogs-webinars/free-trial-now-available-for-accuweathers-lifestyle-health-indices-datasets/1865577)).
- **UV Index** "hour-by-hour down to the tenth of a unit"; **AccuLumen Brightness Index™** 1–10 hourly ("1 (Dark)", "6 (Medium)"); **Indoor Humidity Index** with 11 descriptions from "dangerously dry" to "dangerously humid" ([Aug 2025](https://www.accuweather.com/en/weather-news/accuweather-launches-improved-app-with-over-50-new-and-enhanced-features/1809513); [Dec 2020](https://www.accuweather.com/en/press/accuweather-extends-minutecast-from-2-to-4-hours-most-lengthy-most-accurate-minute-by-minute-forecast-available-anywhere-in-the-world/866154)).
- **Sun & Moon**: daylight duration ("11 hrs 51 mins"), sunrise/sunset, moon phase name and rise/set.
- **BETA experiences** (2026): pet walks, peak foliage, sunscreen, bus-stop outfits.

**Alerts and notifications**
- **Government Issued Alerts** (watches/warnings with a map), per location, on by default for favourites; **AccuWeather Alerts™** (Premium+, three stages); **Lightning Alerts** (push when lightning is detected within 10 mi / 16 km, US beta since May 2024); **precipitation / MinuteCast notifications**; **Today & Tomorrow forecast notifications** (2026); **news notifications**; **Persistent Notification** (status-bar temperature, Premium+ on Android); **browser notifications** on the web ([App FAQ](https://www.accuweather.com/en/weather-news/accuweather-app-faq/765568); [Lightning press release](https://www.accuweather.com/en/press/lightning-alerts-now-available-on-the-accuweather-app/1650318); [App Store version history](https://apps.apple.com/us/app/accuweather-weather-forecast/id300048137)).

**Widgets and platforms**
- Android: ten widgets in 3×3, 4×1, 4×2, 4×5, six free, dark or white, adjustable transparency ([Widgetopia guide](https://widgetopia.io/blog/accuweather-android-widget)). iOS: home-screen and lock-screen widgets (UV, Sun, Wind; AQI and Hurricane widgets are Premium+), "conditional backgrounds mirroring current weather conditions", "updated every 15 minutes" ([Dec 2020](https://www.accuweather.com/en/press/accuweather-extends-minutecast-from-2-to-4-hours-most-lengthy-most-accurate-minute-by-minute-forecast-available-anywhere-in-the-world/866154); [Dribbble, Christopher Bonini](https://dribbble.com/shots/23152247-Homescreen-Widgets-AccuWeather): "The user was able to choose a size and theme. I led the design to expand the widget offering giving Premium+ users more reasons to subscribe").
- Apple Watch, Wear OS, Apple TV, Android TV, tablets; "forecasts and weather alerts via satellite connectivity" ([Google Play](https://play.google.com/store/apps/details?id=com.accuweather.android&hl=en_US)).

**Content and commerce**
- News, Video, **AccuWeather NOW** streaming channel, in-app articles; **ads** (full-screen interstitials on launch and tab change, video ads); **accounts** (free account unlocks longer ranges); AI integrations (ChatGPT, Perplexity) ([Wikipedia](https://en.wikipedia.org/wiki/AccuWeather)).
- Crowdsourced observations from app users ([Wikipedia](https://en.wikipedia.org/wiki/AccuWeather)).

---

## 4. Precipitation in AccuWeather, in fine detail

### 4.1 Definitions AccuWeather uses

- **Probability of Precipitation**: "the probability that at least 0.01 of an inch of precipitation will fall on your rooftop if you live in the forecast area"; it "has nothing to do with the length of time precipitation may fall" nor "the intensity" (Geoff Cornish, [AccuWeather explainer](https://www.accuweather.com/en/weather-news/what-does-30-percent-chance-of-rain-mean/906646)). AccuWeather's older **AccuPOP** product: "the percentage chance that a measurable amount of precipitation will fall in every specific 3-hour time period over the next 96 hours" ([AccuWeather press](https://www.accuweather.com/en/press/36917)).
- The daily (Day or Night) probability is the chance of measurable precipitation at any time in that half-day; hourly values are per hour, so daily can be higher than any single hour. This is the same relationship Daybreak's explain sheet already describes ("We show today's highest hour, so the chance of some rain at some point today can be higher").

### 4.2 Probability: where and how it appears

| Level | What is shown | Visual | Taps (app) |
|---|---|---|---|
| Today screen hourly strip | `%` under each hour with a raindrop glyph; **always shown**, including `0%`, `1%`, `3%` | small text under icon | 0 |
| Hourly tab, collapsed row | time, icon, temperature, `RealFeel® 79°` with its Guide word ("Pleasant"), `75%`, phrase ("Thunderstorms") | right-aligned % | 1 |
| Daily tab row | `Thu 10/1 · 76°/66° · 5%` + phrase; **always shown** | % beside temps | 1 |
| Daily Details | `Probability of Precipitation 99%` and `Probability of Thunderstorms 59%` **separately for Day and Night** | labelled text lines | 2 (Daily, then tap a day) |
| API only (not surfaced as labels on the web) | `RainProbability`, `SnowProbability`, `IceProbability` per Day/Night and per hour | – | – |

Sources: [Miami hourly](https://www.accuweather.com/en/us/miami/33128/hourly-weather-forecast/347936) (9 PM `75%` Thunderstorms; 10 PM `49%`; 11 PM `34%`), [NYC 10-day](https://www.accuweather.com/en/us/new-york/10021/daily-weather-forecast/349727) (`0%`, `5%`, `25%`, `8%`, `3%`, `1%`, `58%` on consecutive days), [Dallas day page](https://www.accuweather.com/en/us/dallas/75202/weather-tomorrow/351194), [API fixture](https://raw.githubusercontent.com/bieniu/accuweather/master/tests/fixtures/daily_forecast_data.json). The app "RealFeel Guide" word and the % use the same small type; the % is not colour-coded by magnitude on the web.

### 4.3 Amount, volume and duration

**Daily (per Day and per Night panel)**, exact label order on the Dallas page for Thursday 1 Oct 2026:

```
Day · 10/1 · 78° Hi
RealFeel® 82°  Very Warm        RealFeel Shade™ 81°  Pleasant
Cloudy and humid with a couple of heavy thunderstorms; thunderstorms can bring
flash flooding and localized damaging wind gusts
[alerts] Flood Watch 4:00 PM Wednesday - 7:00 AM Friday
Max UV Index 1.0 (Low)
AccuLumen Brightness Index™ 1 (Dark)
Wind SSE 8 mph
Wind Gusts 16 mph
Probability of Precipitation 99%
Probability of Thunderstorms 59%
Precipitation 0.85 in
Rain Amount 0.85 in
Total Hours of Precipitation 4
Total Hours of Rain 4
Cloud Cover 99%
Morning · Afternoon

Night · 10/1 · 71° Lo
RealFeel® 70°  Pleasant
Humid with periods of rain, some heavy, and a thunderstorm; watch for flash flooding
Wind SW 6 mph
Wind Gusts 12 mph
Probability of Precipitation 98%
Probability of Thunderstorms 59%
Precipitation 0.71 in
Rain Amount 0.71 in
Total Hours of Precipitation 6
Total Hours of Rain 6
Cloud Cover 99%
Evening · Overnight
```

Observations:
- `Precipitation` is the total liquid (API `TotalLiquid`); `Rain Amount`, `Snow`, `Ice` are the type breakdown (`Rain` mm/in, `Snow` cm/in, `Ice` mm/in). On a rain-only day `Precipitation` and `Rain Amount` are identical and both are printed: a redundancy reviewers describe as "too much confusing information".
- On a **dry day** the amount is still printed as `Precipitation 0.00 in`, and the `Rain Amount` and `Total Hours` lines are omitted (Sunday 4 Oct NYC: `Probability of Precipitation 25% · Probability of Thunderstorms 0% · Precipitation 0.00 in · Cloud Cover 70%`) ([NYC day 5](https://www.accuweather.com/en/us/new-york/10021/daily-weather-forecast/349727?day=5)).
- The **10-Day list** adds `Total Hours of Precipitation 1 / Total Hours of Rain 1` to a row only when hours > 0 (Sat 10/10, 58%), but never prints the amount in the list; the amount lives one tap deeper ([NYC 10-day](https://www.accuweather.com/en/us/new-york/10021/daily-weather-forecast/349727)).
- **Day/Night split**: the app's and web's "day" is sunrise-to-sunset-ish (API Day/Night periods). After sunset the Today page shows only the Night panel (NYC on the evening of 30 Sep showed just `Night · 9/30 · 64° Lo`) ([NYC today](https://www.accuweather.com/en/us/new-york/10021/weather-today/349727)).
- **Snow**: the API `PrecipitationIntensity` is "Light"/"Moderate"/"Heavy"; `HoursOfSnow`, `Snow` in cm or inches; WinterCast adds min/max accumulation with probabilities of thresholds ("the chance for a foot vs. an inch") and school-closure chance. Units follow the account setting (`unit=c` in the API link), inches/°F by default for US pages.

**Hourly**: the amount appears only in the **expanded** row and only when non-zero: 9 PM in Miami had `Rain 0.14 in`; the 10 PM and 11 PM rows (49% and 34%) had no `Rain` line at all. There is no `Hours of` figure per hour and no `Probability of Thunderstorms` line per hour, though the API carries `ThunderstormProbability` hourly ([Miami hourly](https://www.accuweather.com/en/us/miami/33128/hourly-weather-forecast/347936); [hourly fixture](https://raw.githubusercontent.com/bieniu/accuweather/master/tests/fixtures/hourly_forecast_data.json)).

### 4.4 Intensity and timing: MinuteCast

**What it is.** "A minute-by-minute precipitation forecast for the next 120 minutes from the present" ([developer.accuweather.com](https://developer.accuweather.com/minutecast)); marketing says "over the upcoming four hours" since December 2020 ([press release](https://www.accuweather.com/en/press/accuweather-extends-minutecast-from-2-to-4-hours-most-lengthy-most-accurate-minute-by-minute-forecast-available-anywhere-in-the-world/866154)); an AccuWeather-authored piece in May 2025 says "the next 60 minutes" ([Yahoo](https://www.yahoo.com/news/understanding-accuweather-apps-minutecast-142055023.html)). The web page I fetched covers 120 minutes. It is radar-derived: "tracks precipitation at approximately one half-mile resolution … forecast refreshes every five minutes" ([AccuWeather press](https://www.accuweather.com/en/press/49568860)).

**Structure of the data** (which dictates the UI), from the schema and Microsoft's Azure Maps mirror of the same product:
- `Summary.Phrase` (~60 chars, 120-min window), `Phrase_60` (next 60 minutes), `WidgetPhrase` (~15 chars), `ShortPhrase` (~25), `BriefPhrase` (~60), `LongPhrase` (60+), `IconCode`.
- `Summaries[]`: contiguous stretches with `StartMinute`, `EndMinute`, `CountMinute`, phrases at three lengths, e.g. `"Rain ending in %minute_value min"` for minutes 0–24, then `"No precipitation for at least %MINUTE_VALUE min"` (short form `"No precip for %MINUTE_VALUE min"`) for 25–119.
- `Intervals[]` per minute (or 5/15-minute buckets): `Minute`, `Dbz` (radar reflectivity), `ShortPhrase` (`"Light Rain"`, `"No Precipitation"`), `PrecipitationType` (`Rain`, `Snow`, `Ice`, `Mix`; absent when dBZ = 0), `Threshold` (`LIGHT`, `LIGHT-MODERATE`, `MODERATE`, `HEAVY`), `Color` (full-spectrum dBZ colour, e.g. `#086202` at 23 dBZ, `#208509` at 18 dBZ), `SimplifiedColor` (one colour per type×threshold, e.g. light rain `#23BE27`), `IconCode`, `CloudCover`, `LightningRate`.
Sources: [MinuteCast schemas](https://developer.accuweather.com/minutecast/~schemas); [MinuteCast colour endpoints](https://developer.accuweather.com/minutecast/metadata-minutecast); [Azure Maps Get Minute Forecast sample response](https://learn.microsoft.com/en-us/rest/api/maps/weather/get-minute-forecast?view=rest-maps-2026-01-01); [search snippets of the colour JSON](https://apidev.accuweather.com/developers/forecasts/guide).

**Headline copy actually seen** on live pages: `Rain may form in the area over the next 120 min` (Miami, 1 Oct 2026); `No precipitation for at least 120 min` (Weather, PA); `Rain starting in 54 min` (New York); `Rain for at least 120 min` (Brooklyn); sample docs also show `Rain ending in 25 min` and `Light snow starting in 10 min`.

**Web page layout** ([Miami MinuteCast](https://www.accuweather.com/en/us/miami/33128/minute-weather-forecast/347936)): active alert chip (`Coastal Flood Statement`) → headline sentence → current time `8:55 PM` and `No Precipitation` → `81°F` and `RealFeel® 85°` → legend `Rain · Snow · Ice · Mix` → radar map → a horizontal timeline grouped in half-hours (`8:55 PM - 9:24 PM`, `9:25 PM - 9:54 PM`, …) with one entry per minute (`8:56 PM · No Precipitation`), coloured by type and intensity when wet.

**App layout.** Two user-selectable views since Aug 2025: **Dial** or **Chart**. The dial is "Designed as a clock, you will see your exact time at the top, moving clockwise, every 15 minutes to complete a full hour. The color-coded key below the dial will show you what type of weather you can expect. Clicking the center of the MinuteCast dial will bring you to a page where you can scroll minute-by-minute through the next two hours." The temperature and RealFeel sit inside the dial ([App FAQ](https://www.accuweather.com/en/weather-news/accuweather-app-faq/765568)). It is the first thing on the Today screen (0 taps), with the detail one tap away. The colour language is the radar's: greens for light rain through yellows/reds for heavy; blues for snow; pinks for ice; purples for mix (the API ships the exact hex per type×threshold; the hex values above are from the sample response).

### 4.5 The hourly detail

Collapsed row (web and app): hour, icon, temperature, `RealFeel® 79°` with its Guide label (`Pleasant`), chance `75%`, phrase `Thunderstorms`, and any alert chip. Expanded (one tap) adds, in this order on the Miami page: `Heat Index 80°`, `Wind E 9 mph`, `Air Quality Fair`, `Wind Gusts 16 mph`, `Humidity 81%`, `Indoor Humidity 81% (Extremely Humid)`, `Dew Point 73° F`, `AccuLumen Brightness Index™ 0 (Dark)`, `Cloud Cover 91%`, `Rain 0.14 in` (only if non-zero), `Visibility 1.00 mi`, `Cloud Ceiling 6000 ft`. Daytime hours add `Max UV Index` and RealFeel Shade. Days are sectioned (`Today`, `Tomorrow`, weekday) and there is a `Further Ahead: Tomorrow · Friday · 10-Day` footer ([Miami hourly](https://www.accuweather.com/en/us/miami/33128/hourly-weather-forecast/347936)). The App FAQ confirms the app mirrors this: "Humidity information can be located … on the Hourly screen by selecting each hour", "The UV Index can also be found on the 'Hourly' screen by selecting each individual hour" ([App FAQ](https://www.accuweather.com/en/weather-news/accuweather-app-faq/765568)). Premium+ replaces the list with **HourCast™ graphs** ("striking visualizations of the temperature by the hour") out to 10 days ([Google Play](https://play.google.com/store/apps/details?id=com.accuweather.android&hl=en_US)); a reviewer mourned the free graphs: "No more hour by hour graphs? … graphs have been removed and replaced by text" (justuseapp).

### 4.6 The daily detail page

Order on the web (the app's "Daily Details" follows the same model): date header → **Day** panel (high, RealFeel + Guide, RealFeel Shade + Guide, phrase, alert chip, Max UV Index, AccuLumen, Wind, Wind Gusts, Probability of Precipitation, Probability of Thunderstorms, Precipitation, Rain Amount, Total Hours of Precipitation, Total Hours of Rain, Cloud Cover, Morning/Afternoon links) → **Night** panel (low, RealFeel + Guide, phrase, Wind, Gusts, PoP, PoT, Precipitation, Rain Amount, hours, Cloud Cover, Evening/Overnight) → **Sun & Moon** (`11 hrs 51 mins`, `Rise 7:21 AM`, `Set 7:12 PM`, `Waning Gibbous`, moon `Rise 10:41 PM`, `Set 1:58 PM`) → **Temperature History** (Forecast / Average / Last Year / Record with year) → Further Ahead (Hourly, 10-Day, Monthly) → ads, news. Reaching it: Daily tab (1) → tap a day (2). Sunrise/sunset "can also be found in 'Daily' by selecting a day" ([App FAQ](https://www.accuweather.com/en/weather-news/accuweather-app-faq/765568)); the "17 health indicators" also appear on Daily Details since 2026.

---

## 5. What reviewers praise and what they find cluttered

**Praised**
- MinuteCast's timing, when it works: "the best of the best thing they have is the clock that you can say 'honey take the dog out to potty because in 6 minutes it will start to rain lightly'" (App Store, Mar 2022); "Minutecast for precipitation is quite accurate in both timing and amounts" (App Store, Jun); "I need reliable information about snowfalls … when they will hit, and how much snow will fall. This one does that" (App Store, Apr 2019). Roundups call it "the best pick for rain timing to the minute" ([SoftPicker](https://softpicker.com/best-weather-apps/); [top10.com](https://www.top10.com/weather-apps)).
- Alerts, radar and metro-area accuracy ([unstar.app roundup](https://unstar.app/blog/accuweather-weather-channel-carrot-apple-weather-underground-weather-apps-ranked-2026)).
- Air quality coverage (Trustpilot, Oct 2025).

**Cluttered / disliked**
- The August 2025 redesign: "New version presents an incomprehensible set of data. Too much confusing information" (Trustpilot, 26 Aug 2025); "Current version 21 is absolutely the worst designed screen I have ever seen" (Trustpilot, Jul 2026); "The updated app is gaudy, irritating and NO IMPROVEMENT" (Trustpilot, Oct 2025); "The look is cheap … the new color scheme is too much" (Google Play, Aug 2025, 656 helpful); "fonts are smaller … for anyone over 40" (Android Police comment); "the layout is so confusing" (justuseapp). An earlier redesign drew "The complete redesign is a step backwards … old version allowed customization of visible weather details" (justuseapp). Sources: [Trustpilot](https://www.trustpilot.com/review/www.accuweather.com?page=4), [Google Play](https://play.google.com/store/apps/details?id=com.accuweather.android&hl=en_US), [Android Police](https://www.androidpolice.com/accuweather-facelift-beta/), [justuseapp](https://justuseapp.com/en/app/300048137/accuweather-weather-alerts/reviews).
- Ads: "Making me watch an ad so I can see if it'll rain tomorrow is ridiculous" (App Store); "full screen ad every time you change screens"; interstitials that "cannot be dismissed during the first few seconds" during severe weather are called "actively dangerous" ([unstar.app](https://unstar.app/blog/accuweather-weather-channel-carrot-apple-weather-underground-weather-apps-ranked-2026)).
- MinuteCast over-promising: "Says rain starting in 15 minutes...already pouring" (Trustpilot, Oct 2025); "I just watched the hour forecast change its mind 4-5 times in a half an hour" (App Store, Nov 2024); "no precipitation in next two hours" 10 minutes before a downpour; less accurate "in rural areas or low-radar countries" ([unstar.app](https://unstar.app/blog/accuweather-weather-channel-carrot-apple-weather-underground-weather-apps-ranked-2026)). The loss of the rotating dial also upset people: "the ability to rotate clockwise … was really the thing I loved" (justuseapp).
- Notifications: "Every pop-up like it's a potential disaster. All treated the same" (justuseapp); duplicate alerts for favourite locations are an FAQ item.
- RealFeel distrust: "off by more than 10-15 degrees"; "proprietary and uncalibrated" ([unstar.app](https://unstar.app/blog/accuweather-weather-channel-carrot-apple-weather-underground-weather-apps-ranked-2026)).
- Long ranges: days 8–15 "change dramatically", "marketing rather than meaningful prediction" ([unstar.app](https://unstar.app/blog/accuweather-weather-channel-carrot-apple-weather-underground-weather-apps-ranked-2026)).
- Widgets that do not refresh (Google Play, Jun 2026).

---

## 6. Daybreak today

From `/mnt/data/projects/Daybreak-main/app/src/main/java/app/daybreak/…` and the snapshots in `app/src/test/snapshots/images/`.

**Data fetched** (`data/OpenMeteoApi.kt`, one request, `timezone=auto`, `forecast_days=8` via `FORECAST_DAYS` in `domain/Models.kt`):
- `current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day`
- `hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m,wind_gusts_10m,is_day`
- `daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code,sunrise,sunset,wind_speed_10m_max,wind_gusts_10m_max,precipitation_sum,uv_index_max`

So on precipitation we hold `HourForecast.precipChance` (null → 0 in `OpenMeteoParsers.kt`), `DaySummary.precipChance` (= `precipitation_probability_max`) and `DaySummary.precipSumMm` (= `precipitation_sum`, optional). No hourly amounts, no rain/showers/snow split, no hours, no 15-minutely.

**Weather page** (`ui/WeatherScreen.kt`; snapshot `weather_full_page.png`): sky-gradient hero with summary block ("71° and partly cloudy now, with a high of 74° and a low of 56°. Rain is likely around 6 PM (60% chance)."), big temperature with the other unit, condition, pills `High 74° · Low 56° · Rain 60% ⓘ` (the Rain pill opens the explain sheet); tiles `Feels like · Humidity · Wind (Gusts …)`; `Next 12 hours` strip, each card: hour, icon, temperature in both units, `10%` (coloured `weatherColors.rain` when ≥ 30%, muted otherwise, **always printed**); activity card (`Best time to ride`, being replaced); `Sunrise · Sunset · UV index`; `Next 7 days` card: day label, icon with `%` under it **only when ≥ 20%**, low, shared-scale range bar (blue→amber), high. Pull to refresh; no "updated X ago"; no tap-a-day.

**Explain sheet** (`ui/ExplainSheet.kt`, `domain/Glossary.kt`): `Term.RAIN_CHANCE` → title "Chance of rain", value `60%`, detail "Highest hourly chance today", gauge amber→blue 0–100%, `now` = (if `precipSumMm ≥ 0.1`) "About 6.5 mm expected in total. " / "About 0.26 inches expected in total. " + umbrella line (≥70 "Take an umbrella.", ≥30 "Worth having an umbrella nearby.", else "Unlikely to need an umbrella."), and a careful `meaning` paragraph. `formatPrecip` already follows the unit setting (`%.1f mm` with °C, `%.2f inches` with °F).

**Home** (`ui/HomeScreen.kt`; `home_full.png`): glance card "San Francisco · Partly cloudy · ↑74° ↓56° · Rain 60%" (chance only when ≥ 20%; the word "Rain" is dropped when the sky is already rain so it reads "60% chance"); commute card ("Office day · A dry ride home · 67°"; `Commute.kt` uses `DRY` and `LIKELY` chance thresholds and copy like "Rain likely on the way home · 80% at 5 PM"); Coming up (holidays with a day icon and high); Tonight's sky; meme.

**Widget** (`widget/WeatherWidget.kt`): `High 74° · Low 56° · Rain 60%` (always printed) and `↑74° ↓56° · Rain 60%` on the 4×1.

**Narration** (`narration/TemplateNarrator.kt`): "Rain is likely around 6 PM (60% chance).", "There's a 40% chance of rain today.", "No rain expected." — chance-only, driven by `RAIN_LIKELY` / `RAIN_POSSIBLE`.

**Threshold inconsistency today**: hourly cells print every value including `0%`; daily rows and the Home glance hide < 20%; the hero pill and the widget always print; the hourly colour switch is at 30%. Section 9 proposes one rule set.

---

## 7. Feature × AccuWeather × Daybreak

| Feature | AccuWeather | Daybreak (main) | Note |
|---|---|---|---|
| Current temp, condition | Yes, in MinuteCast dial | Yes, hero, both units | Daybreak's both-units pairing is unique |
| Feels-like | RealFeel® + RealFeel Shade™, each with a Guide range and advice | `Feels like` tile + explain sheet with a cause ("the 15 km/h wind carries heat away") | Owner plans feels-like vs actual; our explain already does the "why" |
| Humidity, dew point, indoor humidity | All three (+ Heat Index, wet bulb in API) | Humidity tile with dew-point-based "muggy" logic in the explain | Dew point could be a detail line; indoor humidity no |
| Wind, gusts, direction | Speed + direction + gusts everywhere | Speed + gusts, no direction | Direction is a cheap add (`wind_direction_10m`) |
| Hourly forecast | 72 h free; 10-day with Premium+; expandable rows; HourCast graphs (paid) | 12 h strip | 24 h would help the "tonight/tomorrow morning" question; 10 days of hourly is noise |
| Daily forecast | 15 days app / 10 web; 45–90 days paid | 7 days shown (8 fetched) | Owner plans 10; Open-Meteo allows 16 |
| Day details page | Yes: Day/Night panels, Sun & Moon, Temperature History | No (planned) | Section 9.5 |
| Probability of precipitation | Hourly %, daily % (always printed), Day/Night % | Hourly %, daily max % (≥ 20%), hero pill | We have it |
| Thunderstorm probability | Separate % per Day/Night (API hourly too) | No; weather code 95–99 only | Not in Open-Meteo; don't fake it |
| Precipitation amount | `Precipitation`/`Rain Amount` per Day/Night, `Rain` per hour (expanded) | Daily total only, inside the explain sheet | Section 9 |
| Hours of precipitation | `Total Hours of Precipitation/Rain/Snow/Ice` per Day/Night | No | `precipitation_hours` is free |
| Snow and ice | Snow cm/in, Ice, WinterCast charts, Snow Probability, Snow Day index | No (icon only) | `snowfall`, `snowfall_sum` free; WinterCast-style probabilities not available |
| Intensity words | Light/Moderate/Heavy (API), phrases | No | Derive from mm/h |
| Minute-by-minute nowcast | MinuteCast, 120 min (web), radar-based, 5-minute refresh, dial/chart | No | `minutely_15` is model output, regional; see 8.3 |
| Cloud cover | % per hour and per Day/Night | No | `cloud_cover` free, low value |
| UV | Hourly to a tenth; `Max UV Index 4.0 (Moderate)` | Daily max + category + explain with burn times | Comparable |
| Brightness index | AccuLumen 1–10 | No | Proprietary; skip |
| Sunrise/sunset, daylight length | Yes + moon rise/set and phase | Sunrise/sunset tiles, daylight arc in explain; moon on Home's Tonight's sky | Comparable |
| Temperature history / records | Average, last year, record | No | Open-Meteo has a climate/archive API; low priority |
| Narrative summary | Per-period meteorologist phrases ("Cooler with periods of rain") + headline | Template or on-device Gemma summary with tones | Ours is personal; theirs is denser |
| Weekly outlook sentence | Daily headline ("Expect rainy weather Monday afternoon through Monday evening") | Planned "This week" | Section 9.6 |
| Activity/lifestyle indices | 50+; 17 health indicators in app; Running/Cycling/Hiking ratings | Activity card (being replaced), commute card | Keep ours personal and rule-based |
| Allergies / pollen | Tree, Ragweed, Mold, Grass, Dust & Dander | No | Open-Meteo Air Quality API has pollen for Europe only |
| Air quality | AQI, hourly, by the minute, maps, smoke | No | Open-Meteo Air Quality API is free; a candidate later |
| Radar & maps | Radar, 2 h future, satellite RealVue, lightning, AQI, smoke, wind flow, temperature, clouds | No | Not in scope |
| Lightning | Lightning Network, alerts within 10 mi | No | No free source |
| Hurricane / storm tracker | Yes, with Expert Analysis | No | No |
| Severe weather alerts | Government alerts with maps; AccuWeather Alerts (Premium+) | No | Not in Open-Meteo |
| Notifications | Government, AccuWeather Alerts, Lightning, precipitation, Today & Tomorrow, news, persistent temp | None | A single morning "Today" notification would fit Daybreak; keep it one |
| Widgets | 10 Android (6 free), iOS home + lock screen; premium widgets | One Glance widget, four sizes, with summary line | Ours already shows `Rain 60%` |
| Wearables / TV | Watch, Wear OS, TV | No | No |
| Multiple places | 9 free, 12 paid; "Home" of favourites | Unlimited pages, swipe | Ours is more generous |
| Street-address localisation | Yes | Geocoded place | Fine |
| Light/dark modes | Light (48 backgrounds), Dark, Black | Light/dark, sky gradients | Comparable |
| Accounts, ads, subscriptions | Yes, yes, two tiers | None | Our advantage; keep |
| News and video | Yes, prominently | Daily meme | Ours is deliberate fun |
| Tap-to-explain terms | "RealFeel Guide" and "LEARN MORE" per value | Explain sheets with today's value, gauge, "what it means" | Ours is calmer and data-driven |
| "Updated X ago" | Not shown as such; conditions refresh every 15 min (listing copy); MinuteCast refreshes every 5 min | Not shown; pull to refresh | Owner plans it; section 9.7 |

---

## 8. Precipitation deep-dive: AccuWeather vs Daybreak vs Open-Meteo

### 8.1 Side by side

| Question a user has | AccuWeather | Daybreak today | Open-Meteo field(s) available free |
|---|---|---|---|
| Will it rain this hour? | `75%` on the row | `60%` in the cell | `hourly.precipitation_probability` (have) |
| How much, this hour? | `Rain 0.14 in` (expanded, only if > 0) | – | `hourly.precipitation` (mm), `rain`, `showers`, `snowfall` (cm) |
| Will it rain today? | `Probability of Precipitation 99%` for Day, `98%` for Night | `Rain 60%` pill = max hourly | `daily.precipitation_probability_max` (have), `_mean`, `_min` |
| How much today? | `Precipitation 0.85 in` Day + `0.71 in` Night | Explain sheet sentence only | `daily.precipitation_sum` (have), `rain_sum`, `showers_sum`, `snowfall_sum` (cm) |
| How long? | `Total Hours of Precipitation 4` (Day) / `6` (Night) | – | `daily.precipitation_hours` |
| Rain or snow? | `Rain Amount` vs `Snow` vs `Ice`; type in phrases and icons | Icon via `weather_code` | `snowfall`, `snowfall_sum`, `weather_code` |
| How hard? | Light/Moderate/Heavy; phrases ("some heavy") | – | derive from mm per hour |
| When does it start/stop in the next two hours? | MinuteCast sentence and bar | Template line "Rain is likely around 6 PM (60% chance)" | `minutely_15.precipitation` (regional, no probability) |
| Thunder? | `Probability of Thunderstorms 59%` | – | only `weather_code` 95/96/99 |
| Day vs night? | Separate panels | – | derive from hourly + `sunrise`/`sunset` |

### 8.2 Open-Meteo variables, verified

Verified on 30 Sep 2026 both against [the Forecast API docs](https://open-meteo.com/en/docs) and by calling `https://api.open-meteo.com/v1/forecast` with all of the names below (the response echoed each one with its unit).

**Hourly** (`&hourly=`):
- `precipitation_probability` — `%`. Docs: "Probability of precipitation with more than 0.1 mm of the preceding hour. Probability is based on ensemble weather models with 0.25° (~27 km) resolution." (30 members). We fetch it already. Note the **0.1 mm** definition: it is the natural threshold for "show an amount".
- `precipitation` — `mm`. "Total precipitation (rain, showers, snow) sum of the preceding hour".
- `rain` — `mm`. "Rain from large scale weather systems of the preceding hour".
- `showers` — `mm`. "Showers from convective precipitation in millimeters from the preceding hour".
- `snowfall` — `cm`. "Snowfall amount of the preceding hour in centimeters. For the water equivalent in millimeter, divide by 7."
- also `snow_depth` (m), `cloud_cover` (%), `weather_code`.

**Daily** (`&daily=`):
- `precipitation_sum` — `mm` (have). `rain_sum` — `mm`. `showers_sum` — `mm`. `snowfall_sum` — `cm`.
- `precipitation_hours` — `h`. Docs: "The number of hours with rain" (in practice hours with any precipitation).
- `precipitation_probability_max` (have), `precipitation_probability_mean`, `precipitation_probability_min` — `%`.

**15-minutely** (`&minutely_15=` plus `forecast_minutely_15=<steps>` / `past_minutely_15`):
- `precipitation` (mm), `rain` (mm), `showers` (mm), `snowfall` (cm), `weather_code`, plus temperature, humidity, wind, radiation. **No `precipitation_probability` in `minutely_15`.**
- Coverage: natively 15-minute only where high-resolution models exist: "NOAA HRRR for North America and DWD ICON-D2 / Météo-France AROME for Central Europe; other regions use interpolated hourly data" ([docs](https://open-meteo.com/en/docs)). The live call returned a `minutely_15` block for San Francisco.

**Units and ranges**: `precipitation_unit=inch` switches `precipitation`, `precipitation_sum` and `snowfall_sum` to `inch` (verified live: `{'precipitation': 'inch', …, 'snowfall_sum': 'inch'}`). Daybreak requests metric and converts (`formatPrecip`), which is the right call because we show both temperature units and switch at runtime. `forecast_days` max 16 (we use 8; 10 is fine). `models` default "best_match".

### 8.3 Can we do a MinuteCast?

Not honestly. MinuteCast is radar extrapolation refreshed every five minutes with per-minute intensity; `minutely_15` is NWP model output on a 15-minute grid, with no probability, native only in North America and Central Europe, and typically updated hourly. A bar of 15-minute model amounts would look like MinuteCast and fail like MinuteCast does in its worst reviews ("rain starting in 15 minutes...already pouring"), but without the radar to back it. Where it could earn a place later: a **"next 2 hours"** hint inside the Rain explain sheet or the Home glance, worded softly ("Showers around 3:15–4:00 PM" only when the model shows ≥ 0.2 mm in a 15-minute step), and only when `minutely_15` returns native data (detectable because interpolated data never changes between the four steps of an hour). Not for v1.

---

## 9. Recommendations: how Daybreak should show rain

Design constraints kept throughout: cards on `surfaceContainer`, the sky hero, `weatherColors.rain` (blue) for water, `weatherColors.sun` (amber) for warmth, `success` green for "good", `onSurfaceVariant` for secondary text, explain sheets for the "why", both-unit philosophy for temperature only (precipitation gets one unit), label/title/body type scale from `Theme.kt`, 48 dp touch targets, TalkBack strings that read as one sentence.

### 9.1 Data changes (one request, still one call)

Add to the existing URL in `OpenMeteoApi.forecastUrl`:
- `hourly`: `precipitation,snowfall` (keep `precipitation_probability`). Skip `rain`/`showers`: the split means nothing to a reader; `weather_code` already distinguishes showers (80–82) from rain (61–65).
- `daily`: `precipitation_hours,snowfall_sum,precipitation_probability_mean` (keep `precipitation_probability_max,precipitation_sum`).
- `forecast_days=10`.

Model additions: `HourForecast.precipMm: Double?`, `HourForecast.snowCm: Double?`; `DaySummary.precipHours: Double?`, `DaySummary.snowSumCm: Double?`, `DaySummary.precipChanceMean: Int?`. All optional, parsed with the existing `doubleOrNull`, so an older cached response still renders.

### 9.2 One rule set for chance and amount

Thresholds (put them in one `domain/Precip.kt` so the strip, the row, the details page, the outlook sentence, the glance, the widget and the narrator agree):

| Rule | Value | Why |
|---|---|---|
| Show an hourly chance | ≥ 10% | Below that it is noise; AccuWeather's `0%/1%/3%` is a cited irritant. Reserve the line so cells stay level. |
| Colour the chance blue | ≥ 40% (today 30%) | 40% is where "possible" becomes worth planning around; matches the "possible" word band below |
| Show an hourly amount | ≥ 0.1 mm | Open-Meteo's own probability is defined on 0.1 mm; below it is a trace |
| Show a daily total | ≥ 0.5 mm | A tenth of an inch is 2.5 mm; 0.5 mm is the smallest total worth a number. Below: no number (the row has the % already) |
| Show a daily chance in the row | ≥ 20% (as today) | Keep; it already matches Home |
| Show snow instead of rain | `snowfall_sum ≥ 0.5 cm` (hour: ≥ 0.1 cm) and snow is the majority of `precipitation_sum × 7` | Say "snow" when it is mostly snow |
| "Wet day" (for the outlook) | `precipitation_probability_max ≥ 50` and `precipitation_sum ≥ 1.0 mm` | Both must hold: a 60% chance of 0.2 mm is not a rainy day; 3 mm at 30% is a gamble, not a spell |
| "Dry day" | `precipitation_probability_max < 20` or `precipitation_sum < 0.2 mm` | |
| Chance words | ≥ 70 "likely", 40–69 "possible", 20–39 "a small chance", < 20 "unlikely" | Already the shape of `TemplateNarrator` / `Commute.kt` (`LIKELY`, `DRY`) |
| Amount words (daily) | < 1 mm "a few drops", 1–5 mm "light", 5–15 mm "a proper soaking", > 15 mm "heavy rain" | Round words, not categories |
| Intensity (hourly, mm/h) | < 0.5 light, 0.5–4 moderate, > 4 heavy | Met Office-style rain-rate bands; use only in the details page copy |

Units: `mm` with °C (one decimal under 10 mm, whole numbers above: `0.6 mm`, `4 mm`, `18 mm`), `in` with °F (`0.02 in`, `0.15 in`, `0.7 in`; two decimals under 0.1, else one). Snow `cm` with °C (`3 cm`), `in` with °F (`1.2 in`). In prose use "inches" as `formatPrecip` does today; in cells and rows use `in`. Never show both precipitation units: the temperature pairing is the one place we do that, and rain numbers are read for magnitude, not converted.

Accessibility strings: the chance and amount are one phrase: "6 PM, 66°F (19°C), 60% chance of rain, about 0.6 millimetres".

### 9.3 Hourly strip cell

Keep the card; add one muted line; hide noise.

```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│   Now    │   │   3 PM   │   │   6 PM   │   │   7 PM   │
│   ☁☀     │   │   ☀      │   │   🌧     │   │   🌧     │
│   71°    │   │   69°    │   │   66°    │   │   64°    │
│   21°C   │   │   21°C   │   │   19°C   │   │   18°C   │
│          │   │   15%    │   │   60%    │   │   80%    │   ← labelSmall; blue ≥ 40%, muted below; blank < 10% (height kept)
│          │   │          │   │  0.6 mm  │   │  1.8 mm  │   ← labelSmall onSurfaceVariant; only ≥ 0.1 mm; snow: "0.4 cm"
└──────────┘   └──────────┘   └──────────┘   └──────────┘
```

- The amount line is reserved in every card (so cards stay the same height) but drawn only when gated in. `cardWidth` already measures the widest label; include `"1.8 mm"` / `"0.15 in"` in that measurement.
- Optional, if the owner wants a shape rather than numbers: a 3 dp bar across the bottom of each wet card in `weatherColors.rain`, height ∝ `min(precipMm, 4)/4`. I would ship the text line first; the bar is a second step and must not replace the number.
- Don't add a raindrop glyph: the blue colour already says water, and the glyph is the AccuWeather look.

### 9.4 Daily row (10 days)

```
 Today   🌧        56°  ━━━━━━━━━━━━━━━━━━━  74°    4 mm
         60%       13°C                       23°C
 Tue     ☀         58°       ━━━━━━━━━━━━━━   78°
                   14°C                       26°C
 Wed     🌧        52°  ━━━━━━━━━━━━          67°    12 mm
         90%       11°C                       20°C
 Thu     🌨        30°  ━━━━━━━━               41°    3 cm snow
         70%       -1°C                        5°C
```

- Day label, icon with `%` under it (unchanged), low, range bar, high, then a new right-hand cell: the total in `labelSmall` `onSurfaceVariant`, right-aligned, width measured over the week like the other columns, blank when < 0.5 mm. Snow days read `3 cm snow` (or `1.2 in snow`) in the same cell.
- Rows become tappable (`Role.Button`, "Open Thursday") to the day-details page; add a trailing chevron only at the card level (one in the header "Next 10 days ›") rather than ten chevrons.
- TalkBack: "Wednesday, rain, high 67°F (20°C), low 52°F (11°C), 90% chance of rain, about 12 millimetres."
- Do **not** print `0%`/`5%` chances (AccuWeather does; reviewers call the result incomprehensible). Do not add hours to the row; hours go in the details page.

### 9.5 Day-details page: precipitation section

Open from a daily row (1 tap, versus AccuWeather's 2). Page: the place's sky hero reduced to a band with the day name and date, a high/low/condition line, then cards: **Rain** (or **Snow**), **Temperature** (feels-like vs actual, per the owner's plan), **Wind**, **Sun & UV**. The Rain card:

```
┌─────────────────────────────────────────────────────────────┐
│ Rain                                                      ⓘ │  ← titleMedium; ⓘ opens Term.RAIN_DAY explain
│                                                             │
│ Rain likely · about 12 mm over 6 hours                      │  ← titleLarge verdict (chance word + total + hours)
│ Mostly in the afternoon, heaviest around 4 PM.              │  ← bodyMedium, onSurfaceVariant; from the hourly peak
│                                                             │
│  ▁▁▁▁▁▁▁▂▃▅▇█▆▃▂▁▁▁▁▁▁▁▁                                    │  ← 24 bars, 4 dp gap, rain blue; height ∝ mm (cap 4 mm/h)
│  · · · · 20 40 70 90 90 80 60 40 20 · · · · · · ·            │  ← chance under bars at 3-hour ticks only; "·" below 10%
│  12 AM      6 AM       12 PM       6 PM                     │  ← labelSmall axis
│                                                             │
│  Daytime     7 AM–7 PM     90% · 11 mm · 5 h                │  ← two rows derived from hourly + sunrise/sunset
│  Overnight   7 PM–7 AM     40% · 1 mm · 1 h                 │
└─────────────────────────────────────────────────────────────┘
```

- **Verdict line** rules: `<chance word> · about <total> over <hours> hours`. "Rain unlikely" alone when the day is dry; "Showers possible · a few drops" when 40–69% and < 1 mm; "Snow likely · about 3 cm" on snow days. Timing clause from the hourly amounts: "mostly in the morning / afternoon / evening / overnight", "on and off all day" if the wet hours are spread, "heaviest around 4 PM" when one hour holds ≥ 35% of the total.
- **Daytime / Overnight** is AccuWeather's Day/Night panel reduced to two lines: chance = max hourly chance in the window, amount = sum of hourly `precipitation`, hours = count of hours with ≥ 0.1 mm. Overnight runs from this day's sunset to the next day's sunrise, which is the question people actually ask ("will it rain tonight?"); label it with the clock times so it is unambiguous. Omit a row that is dry.
- **Bars** are the only chart on the page; they double as the "intensity" display (AccuWeather's Light/Moderate/Heavy becomes bar height). Cap at 4 mm/h so one downpour doesn't flatten the rest; bars at the cap get the darker `primary` tone.
- The explain sheet for this card (`Term.RAIN_DAY`) says: value `12 mm`, detail "Expected total for Wednesday", gauge amber→blue 0–20 mm, `now` "About 6 hours of rain, mostly in the afternoon. Take a proper coat rather than an umbrella if it's also windy.", `meaning` "The total rain (and melted snow) the forecast expects to fall over the day. 1 mm is a few drops on the pavement; 5 mm wets everything; 20 mm is a wet day. Hours count any hour with at least 0.1 mm."
- Below the Rain card on days ≥ 2 ahead, a one-line hedge in `labelSmall`: "Amounts this far ahead are rough; the chance is the better guide." That is the honest reply to the AccuWeather complaint that extended details are "marketing".

### 9.6 "This week" outlook (replacing the activity card)

Build it from the same thresholds so it never contradicts the rows:

- Classify each of the 10 days: **wet** (`chanceMax ≥ 50 && sum ≥ 1 mm`), **dry** (`chanceMax < 20 || sum < 0.2 mm`), else **mixed**. Snow days are wet days with `snow = true`.
- **Spell**: ≥ 2 consecutive wet days → "Rainy spell from tomorrow until Monday" / "Wet from Thursday to Saturday (about 25 mm in all)". A single wet day → "Rain on Wednesday, dry the rest of the week". No wet days → "A dry week ahead" (or "Dry all week, warmest on Saturday").
- **Best day**: among dry or mixed days in the next 7, score = 0.5·(1 − chanceMax/100) + 0.3·temperature comfort (distance of the high from 21 °C) + 0.2·calm (gust < 30 km/h); ties go to the weekend. Copy: "Saturday is the best day this week: dry, 24° and calm."
- Card copy stays to two sentences, `titleLarge` headline + `bodyMedium` detail, with a ten-dot strip under it (one dot per day, rain blue for wet, amber for the best day, outlineVariant otherwise, labels at Mon/Thu/Sun) rather than a chart.
- Tapping the card scrolls to or opens the daily list; tapping a dot opens that day's details.

### 9.7 Hero, Home, widget and "updated"

- Hero pill: keep `Rain 60% ⓘ`; when the daily total is ≥ 0.5 mm make it `Rain 60% · 4 mm ⓘ`. Not more. Snow: `Snow 70% · 3 cm ⓘ`.
- Explain for `RAIN_CHANCE` (today): keep, but when the total is gated in the `now` line becomes "About 4 mm over 3 hours, mostly this evening. Worth having an umbrella nearby." and the detail under the value stays "Highest hourly chance today".
- Home glance: `Partly cloudy · ↑74° ↓56° · Rain 60%` unchanged; totals do not belong on the glance.
- Widget: the 4×2 `High 74° · Low 56° · Rain 60%` unchanged; totals would not fit at a glance.
- Narration: the template already says "Rain is likely around 6 PM (60% chance)." Add the amount only when ≥ 1 mm: "Rain is likely around 6 PM (60% chance, about 4 mm)." For Gemma, pass `precipitation_sum_mm` and `precipitation_hours` into the prompt JSON (it already gets `max_rain_chance_percent`) and let the validator accept those numbers.
- "Updated X ago": a `labelSmall` line in `onSurfaceVariant` under the hourly-strip heading ("Next 12 hours · updated 8 min ago"), or at the foot of the summary block; turn it amber and say "updated 2 hours ago · pull to refresh" past 90 minutes. Keep the time of the fetch in `PageContent.Loaded`; Open-Meteo's `current.time` is the model time, not our fetch time, so store our own `Instant`.

### 9.8 Copy sheet

| Where | Copy |
|---|---|
| Hourly cell | `60%` · `0.6 mm` / `0.02 in` · `0.4 cm` / `0.2 in` (snow) |
| Daily row total | `4 mm` · `12 mm` · `0.15 in` · `3 cm snow` · `1.2 in snow` |
| Hero pill | `Rain 60% · 4 mm` · `Snow 70% · 3 cm` |
| Details verdict | `Rain likely · about 12 mm over 6 hours` · `Showers possible · a few drops` · `Rain unlikely` · `Snow likely · about 3 cm` |
| Details timing | `Mostly in the afternoon, heaviest around 4 PM.` · `On and off all day.` · `Overnight, clearing by morning.` |
| Day/night rows | `Daytime 7 AM–7 PM · 90% · 11 mm · 5 h` · `Overnight 7 PM–7 AM · 40% · 1 mm · 1 h` |
| Outlook | `Rainy spell from tomorrow until Monday` · `Saturday is the best day this week: dry, 24° and calm.` · `A dry week ahead` |
| Hedge | `Amounts this far ahead are rough; the chance is the better guide.` |
| Updated | `updated 8 min ago` · `updated 2 hours ago · pull to refresh` |

---

## 10. What not to copy, and why

1. **The thirteen-line Day/Night panels.** `Precipitation 0.85 in` and `Rain Amount 0.85 in` on the same card, `Total Hours of Precipitation 4` and `Total Hours of Rain 4` beneath them, plus cloud cover, AccuLumen and two RealFeel Guides: this is the "incomprehensible set of data" reviewers complain about. Give each question one number and one place.
2. **Printing every chance.** `0% · 5% · 25% · 8% · 3% · 1%` down the 10-day list makes the one day that matters (58%) no louder than the rest. Gate at 20% in rows and 10% in cells; let colour do the rest.
3. **Thunderstorm probability as a separate figure.** We do not have the data (Open-Meteo gives only a thunderstorm `weather_code`), and a second percentage next to the first is exactly the kind of pair people misread. Say "thunderstorms possible" in words when the code says so.
4. **MinuteCast's minute precision.** It is AccuWeather's best-loved feature and its most-mocked when wrong. We have no radar nowcast; `minutely_15` is a model and regional. Promise windows ("around 6 PM", "this evening"), not minutes.
5. **A dial.** Users loved the old rotating dial and were angry when it changed; it is also a clock face carrying rain colours, two temperatures and a legend. Our hero already carries the sky; a bar chart on the details page is enough.
6. **The radar colour scale (green→yellow→red) for intensity.** It reads as "alert", fights the calm palette, and needs a legend. One blue at varying heights (and a darker blue at the cap) says the same thing.
7. **Filled, saturated icons and "48 real-time backgrounds".** The 2025 redesign's colour was the top complaint ("the new color scheme is too much", "gaudy"). Our line icons on `surfaceContainer` are the opposite choice; keep them.
8. **Long ranges as a headline feature.** 15/45/90-day forecasts are called "marketing rather than meaningful prediction". Ten days is plenty; hedge amounts beyond day 2.
9. **Separate "RealFeel Guide" blocks with advice per value.** The idea (a category and a sentence) is good and we already do it in the explain sheet; do not inline it on every value.
10. **Notification volume.** Three alert systems plus news and persistent notifications produce "every pop-up like it's a potential disaster". If Daybreak ever notifies, one morning summary and nothing else.
11. **Ads, accounts, tiers, widget paywalls.** Not a design question for us, but worth stating: everything above must stay free of banners, "unlock" prompts and sign-ins, because that absence is the clearest difference a reviewer will notice.
12. **Indoor humidity, brightness index, pests, school-closure odds.** Proprietary or marginal; none answers a question our users ask of a personal daily app.

---

## 11. Sources

AccuWeather consumer pages (fetched 30 Sep – 1 Oct 2026 via a reader proxy; direct fetches return 403):
- [Dallas, TX Weather Tomorrow](https://www.accuweather.com/en/us/dallas/75202/weather-tomorrow/351194) (Day/Night panels, exact labels)
- [New York, NY Weather Today](https://www.accuweather.com/en/us/new-york/10021/weather-today/349727) (night-only panel after sunset; Temperature History)
- [New York, NY 10-Day](https://www.accuweather.com/en/us/new-york/10021/daily-weather-forecast/349727) and [day 5 details](https://www.accuweather.com/en/us/new-york/10021/daily-weather-forecast/349727?day=5)
- [Miami, FL Hourly](https://www.accuweather.com/en/us/miami/33128/hourly-weather-forecast/347936)
- [Miami, FL MinuteCast](https://www.accuweather.com/en/us/miami/33128/minute-weather-forecast/347936)
- [New York, NY WinterCast / Snow and Ice Outlook](https://www.accuweather.com/en/us/new-york/10021/winter-weather-forecast/349727)
- [New York, NY Health & Activities](https://www.accuweather.com/en/us/new-york/10021/health-activities/349727)
- [AccuWeather App FAQ](https://www.accuweather.com/en/weather-news/accuweather-app-faq/765568)
- [What does a '30% chance of rain' actually mean?](https://www.accuweather.com/en/weather-news/what-does-30-percent-chance-of-rain-mean/906646)

AccuWeather press and announcements:
- [Improved app with over 50 new and enhanced features, 27 Aug 2025](https://www.accuweather.com/en/weather-news/accuweather-launches-improved-app-with-over-50-new-and-enhanced-features/1809513)
- [Redesigned app, 20 Jul 2020](https://www.accuweather.com/en/press/accuweather-launches-redesigned-app-with-enhanced-features-and-better-user-experience/779751)
- [MinuteCast extended from 2 to 4 hours, 14 Dec 2020](https://www.accuweather.com/en/press/accuweather-extends-minutecast-from-2-to-4-hours-most-lengthy-most-accurate-minute-by-minute-forecast-available-anywhere-in-the-world/866154)
- [Lightning Alerts, 14 May 2024](https://www.accuweather.com/en/press/lightning-alerts-now-available-on-the-accuweather-app/1650318)
- [Premium+ introduced, 4 May 2022 (PR Newswire)](https://www.prnewswire.com/news-releases/accuweather-introduces-premium-tier-of-award-winning-app-with-advanced-lifesaving-features-for-threatening-severe-weather-301539832.html)
- [MinuteCast press, "full spectrum of colors"](https://www.accuweather.com/en/press/49568860); [AccuPOP](https://www.accuweather.com/en/press/36917); [Lifestyle & Health Indices datasets](https://www.accuweather.com/en/blogs-webinars/free-trial-now-available-for-accuweathers-lifestyle-health-indices-datasets/1865577)

AccuWeather API documentation and fixtures:
- [MinuteCast schemas](https://developer.accuweather.com/minutecast/~schemas); [MinuteCast colour endpoints](https://developer.accuweather.com/minutecast/metadata-minutecast); [MinuteCast overview](https://developer.accuweather.com/minutecast); [Forecasts general info](https://apidev.accuweather.com/developers/forecasts)
- [Azure Maps Get Minute Forecast (AccuWeather data, full sample response)](https://learn.microsoft.com/en-us/rest/api/maps/weather/get-minute-forecast?view=rest-maps-2026-01-01)
- [bieniu/accuweather daily forecast fixture](https://raw.githubusercontent.com/bieniu/accuweather/master/tests/fixtures/daily_forecast_data.json) and [hourly fixture](https://raw.githubusercontent.com/bieniu/accuweather/master/tests/fixtures/hourly_forecast_data.json)

Store listings and reviews:
- [App Store listing, version history, reviews, prices](https://apps.apple.com/us/app/accuweather-weather-forecast/id300048137); [App Store reviews page](https://apps.apple.com/us/app/accuweather-weather-forecast/id300048137?see-all=reviews&platform=iphone)
- [Google Play listing and reviews](https://play.google.com/store/apps/details?id=com.accuweather.android&hl=en_US); [Amazon Appstore listing](https://www.amazon.com/AccuWeather-with-Superior-AccuracyTM/dp/B005K17RU0)
- [Trustpilot reviews](https://www.trustpilot.com/review/www.accuweather.com?page=4); [justuseapp reviews](https://justuseapp.com/en/app/300048137/accuweather-weather-alerts/reviews)
- [unstar.app: weather apps ranked by complaints 2026](https://unstar.app/blog/accuweather-weather-channel-carrot-apple-weather-underground-weather-apps-ranked-2026); [SoftPicker best weather apps](https://softpicker.com/best-weather-apps/); [top10.com](https://www.top10.com/weather-apps)

Design and press coverage:
- [Android Police: AccuWeather for Android gets a bold new look](https://www.androidpolice.com/accuweather-facelift-beta/)
- [MediaPost: AccuWeather redesigns app based on user feedback](https://www.mediapost.com/publications/article/353838/accuweather-redesigns-app-based-on-user-feedback.html)
- [9to5Google: Premium+ and the persistent notification](https://9to5google.com/2022/04/13/accuweather-weather-notifications-subscription/); [XDA: Premium Plus](https://www.xda-developers.com/accuweather-hyperlocal-premium-plus/)
- [Dribbble, Christopher Bonini: Homescreen Widgets](https://dribbble.com/shots/23152247-Homescreen-Widgets-AccuWeather) and [iOS Lock Screen Widgets](https://dribbble.com/shots/23058567-iOS-Lock-Screen-Widgets-AccuWeather); [Widgetopia Android widget guide](https://widgetopia.io/blog/accuweather-android-widget)
- [Yahoo/AccuWeather: Understanding MinuteCast (May 2025)](https://www.yahoo.com/news/understanding-accuweather-apps-minutecast-142055023.html); [Wikipedia: AccuWeather](https://en.wikipedia.org/wiki/AccuWeather)

Comparators mentioned:
- Apple Weather hides chances under 30% and in iOS 18 replaced hourly amounts with words ([Apple Community thread](https://discussions.apple.com/thread/255935833); [weatherstationadvisor](https://weatherstationadvisor.com/what-does-the-percentage-of-rain-mean/)); Weather Strip draws amounts as blue bars only "when more than a slight drizzle is expected" ([Weather Strip support](https://www.weatherstrip.app/support/)); Pixel Weather has precipitation graphs in its hourly card ([Pixel Phone Help](https://support.google.com/pixelphone/answer/15266029?hl=en)).

Open-Meteo:
- [Forecast API docs](https://open-meteo.com/en/docs) (variable descriptions, 15-minutely coverage, `precipitation_unit`, `forecast_days`); live verification call to `api.open-meteo.com/v1/forecast` on 30 Sep 2026 echoing `hourly_units`, `daily_units` and `minutely_15_units` for every variable named in section 8.2.

Daybreak (read-only copy):
- `/mnt/data/projects/Daybreak-main/app/src/main/java/app/daybreak/data/OpenMeteoApi.kt`, `OpenMeteoParsers.kt`; `domain/Models.kt`, `Glossary.kt`, `Formatting.kt`, `Activity.kt`, `Commute.kt`; `ui/WeatherScreen.kt`, `HomeScreen.kt`, `ExplainSheet.kt`, `ActivityCard.kt`, `Theme.kt`; `widget/WeatherWidget.kt`; `narration/TemplateNarrator.kt`, `GemmaPrompt.kt`; snapshots `app/src/test/snapshots/images/*weather_*.png`, `*home_*.png`.
