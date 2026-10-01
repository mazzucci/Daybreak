# Daybreak's Weather tab vs. the professional weather apps

*Research report, 1 October 2026. Sources are linked inline; facts about data APIs were checked against the live documentation on that date. Facts about Daybreak come from the code at `/home/mazzucci/projects/Daybreak` (branch as checked out) and the Paparazzi snapshots.*

---

## 1. What Daybreak's Weather screen does today

### 1.1 Layout, top to bottom (`ui/WeatherScreen.kt`)

| Block | What it shows | How |
|---|---|---|
| **Floating action row** | page dots (or "3 / 12"), Refresh, Add place, Places | white icons over the hero, 56 dp |
| **Hero** (sky gradient, 28 dp bottom corners) | place name + "Region, Country"; **summary** in a translucent block; **big temperature** in the primary unit with the other unit alongside; condition icon + text; three pills **High / Low / Rain %** (Rain opens an explanation) | `heroGradient(sky, night, dark)` from `Sky.kt`: nine looks (clear, partly cloudy, cloudy, fog, drizzle, rain, snow, storm, unknown) × day/night, every colour checked for ≥ 4.5:1 with white text; dark theme pulls the day gradients toward the surface |
| **Summary** | one or two sentences: template ("71° and partly cloudy now, with a high of 74°…") or on-device Gemma 3 1B, marked "✦ Written by Gemma on this device" in amber | five voices (Friendly, Brief, Cheerful, Deadpan, Pirate), optional "about me" note; every number validated against the forecast |
| **Tile row 1** | **Feels like** (other unit under), **Humidity**, **Wind** (gust detail when gusts exceed the wind) | `StatTile` cards on `surfaceContainer`, equal height, info glyph in the label, TalkBack role Button / "Explain" |
| **Next 12 hours** | horizontal strip of cards: hour, icon (day/night aware), dual temperature, rain % (blue when ≥ 30) | `LazyRow`, "Now" card on `primaryContainer`, one width for all cards so large fonts scale evenly |
| **Best time to ride / run / walk** (`ActivityCard.kt`, `domain/Activity.kt`) | best window in the next 24 h ("Now–6 PM · Dry · light wind · 67–71°") or what's in the way ("Not in the next 12 hours · Because of rain"); one green/amber/grey bar per hour | `ActivityScorer` penalises storm, snow, rain, wind, cold, heat, dark per activity profile |
| **Tile row 2** | **Sunrise**, **Sunset** ("next day" detail when it crosses midnight), **UV index** with word (Low/Moderate/High…); polar night and midnight sun handled as "Daylight: None / 24 hours" | |
| **Next 7 days** | one row per day: day, icon, rain % (when ≥ 20), low, **range bar on a week-wide shared scale** (cool blue → warm amber), high; both units | `RangeBar`, columns measured to the widest value so bars line up |

Other states: loading skeleton in the shape of the page, permission card, error card, first-run empty state. Pull to refresh. One page per place in a `HorizontalPager`; the open explanation sheet is kept per place.

### 1.2 Tap-to-explain (`ui/ExplainSheet.kt`, `domain/Glossary.kt`)

Seven terms: **Feels like, Humidity, Wind, UV, Sun, Daylight, Rain chance**. Each sheet has the value large, a detail line, a sentence about *today* ("Colder than the air (71°F): the 19 mph wind carries heat away from your skin"), a "What it means" paragraph, and an optional gauge: a scale bar (Dry→Humid, Calm→Gale, Low→Extreme, 0→100 %) or the **sun arc** with the current position and "Sunset in 4 hours 26 minutes · Tomorrow gets 2 minutes less". The text is hand-written and built from data, never from the model. Notably the glossary already computes the **dew point** (Magnus formula) to decide "muggy", but never shows it.

### 1.3 Data fetched (`data/OpenMeteoApi.kt`, `OpenMeteoParsers.kt`, `domain/Models.kt`)

One Open-Meteo `/v1/forecast` call per place, `timezone=auto`, `forecast_days=8`:

- **current**: `temperature_2m, apparent_temperature, relative_humidity_2m, wind_speed_10m, weather_code, is_day`
- **hourly**: `temperature_2m, precipitation_probability, weather_code, wind_speed_10m, wind_gusts_10m, is_day`
- **daily**: `temperature_2m_max/min, precipitation_probability_max, weather_code, sunrise, sunset, wind_speed_10m_max, wind_gusts_10m_max, precipitation_sum, uv_index_max`

Not fetched: wind direction, pressure, dew point, visibility, cloud cover, precipitation amounts per hour, snowfall, `minutely_15`, `past_days`, air quality, anything from other APIs. `precipitation_sum` is fetched and used only inside the Rain explanation.

### 1.4 Around the tab

- **Widget** (`widget/WeatherWidget.kt`, Jetpack Glance): four responsive sizes (Compact 2×1, Wide 4×1, Square 2×2, Full 4×2) with the sky colour, dual temperature, high/low/rain and the summary; mirrors the app on open and refreshes the numbers every couple of hours via WorkManager, never running Gemma or reading location in the background.
- **Home tab** carries the things a weather app would put in cards: **Tonight's sky** (moon phase drawn, illumination, next full moon by its old name, next meteor shower, "clear enough to look up"), **Office or home?** commute advice, **Coming up** (holidays, long weekends, own dates), and the daily **meme**.
- **Accessibility** is a strength: merged semantic nodes that speak both units, 48 dp touch targets on pills, inline glyphs that scale with font size, snapshot tests at large font and narrow widths, polar-night and pirate-voice snapshots.
- **Not present anywhere in the app**: notifications (the only notification code is the Gemma download), severe-weather alerts, radar or maps, air quality, pollen, pressure, visibility, dew point display, wind direction, charts of the hourly or daily series (the hourly strip is a row of cards; the daily list uses range bars), yesterday/normal comparisons, per-hour detail, reorderable cards, moon in the Weather tab (it lives on Home).

---

## 2. The professional apps

Observations are drawn from reviews and vendor feature pages; where a review's judgement is quoted, it is attributed.

### Apple Weather (iOS 17 → 26)
- **Main screen**: gradient/animated backdrop; big temperature with **"feels like" shown underneath only when it differs noticeably** ("a breezy 60 degrees feels like 50"); a one-line conditions sentence; **next-hour precipitation chart** by the minute; hourly strip; 10-day list with range bars on a shared scale and the current temperature marked on today's bar; a grid of **expandable modules** (UV, Wind with compass, Rainfall, Feels Like with an "Actual vs Feels Like" chart, Humidity with dew point, Visibility, Pressure gauge with trend arrow, Sunrise/Sunset arc, Air Quality with per-pollutant stats, **Averages** "vs normal" for temperature and precipitation, Moon); severe weather and next-hour rain notifications per location; "Home/Work" labels from Contacts. iOS 26 adds **severe-weather alerts and widgets for predicted travel destinations** using on-device route prediction, and live Weather wallpapers in 26.3. ([Gadget Hacks on iOS 18](https://ios.gadgethacks.com/how-to/apples-weather-app-just-got-13-new-features-and-changes-latest-iphone-software-update-0385607/), [Gadget Hacks on the module redesign](https://ios.gadgethacks.com/how-to/your-iphones-weather-app-just-got-14-major-new-features-0385062/), [AppleInsider guide](https://appleinsider.com/inside/ios-19/tips/inside-apple-weather-get-the-most-out-of-apples-own-forecasting-app), [MacRumors on iOS 26](https://www.macrumors.com/2025/06/10/ios-26-severe-weather-predicted-destinations/), [9to5Mac on 26.3 wallpapers](https://9to5mac.com/2025/12/16/ios-26-3-adds-new-iphone-wallpaper-section-expands-gallery/))
- **Praised**: calm design, readability, the expandable-module idea, averages for trip planning. **Criticised**: forecast accuracy since Dark Sky's data was folded in, no Android version, nothing customisable.

### Google / Pixel Weather (2024 redesign, 2025 Material 3 Expressive)
- **Main screen**: gradient background matching conditions; **AI Weather Report** written by Gemini Nano on-device ("concise, actionable blurbs"); **repositionable cards** (hourly, 10-day, wind with direction, UV, humidity, pressure, visibility, sunrise/sunset, AQI, **Pollen** with three dials Grass/Tree/Weed on a 0–4 scale in the US and five more countries); a **Weather map** with a 6-hour precipitation nowcast (US, UK, most EU); **Nowcast** minute-by-minute rain; rain and severe notifications; widgets. The 2025 Expressive update made the location list "much taller pill-shaped cards". ([Android Authority](https://www.androidauthority.com/pixel-weather-app-3469834/), [9to5Google leak](https://9to5google.com/2024/08/07/pixel-weather-leak-install/), [9to5Google pollen](https://9to5google.com/2025/05/01/pixel-weather-pollen-tracker-us/), [9to5Google Expressive redesign](https://9to5google.com/2025/08/25/pixel-weather-expressive-redesign/), [Google help](https://support.google.com/pixelphone/answer/15266029?hl=en), [How-To Geek](https://www.howtogeek.com/google-pixel-weather-app/))
- **Praised**: "feels less busy overall, without sacrificing detail"; card reordering; on-device AI. **Criticised**: AI summaries "aren't exactly game-changing" (Android Authority); Pixel-only.

### CARROT Weather (v5+, iOS and Android)
- **Main screen**: personality-driven one-liners (Friendly → Overkill); **Interface Maker** to add, remove, rearrange and restyle components, with shareable presets; **contextual cards** ("on a relatively calm day, you might only see sunrise/sunset and moon phase cards", hazards surface when the weather turns); detailed hourly/daily graphing screens; **Weather Time Machine** (70 years back); multiple data sources; rain, lightning and severe alerts; many widgets; secret locations and achievements. ([meetcarrot v5](http://www.meetcarrot.com/weather/v5.html), [AndroidGuys review](https://androidguys.com/reviews/carrot-weather-review/), [Tom's Guide](https://tomsguide.com/round-up/best-weather-apps))
- **Praised**: depth hidden behind a simple front, customisation, humour. **Criticised**: subscription tiers, humour wears thin, Android version lags iOS.

### AccuWeather
- **Main screen**: **MinuteCast** (minute-by-minute precipitation for the next hour, "rain starts in 18 min"), **RealFeel** and RealFeel Shade, hourly and 15-day, radar, severe alerts, allergy/AQI, news. ([Yahoo on MinuteCast](https://www.yahoo.com/news/understanding-accuweather-apps-minutecast-142055023.html), [Play Store](https://play.google.com/store/apps/details/AccuWeather_Weather_Radar?id=com.accuweather.android&hl=en_NZ))
- **Praised**: MinuteCast when it works (one roundup estimates ~70 % hit rate on "will it rain in the next hour"). **Criticised**: "intrusive advertisements completely block the screen", video interstitials of 5–10 s even during severe weather, MinuteCast misses, promotional clutter hiding features. ([unstar.app ranking](https://unstar.app/blog/accuweather-weather-channel-carrot-apple-weather-underground-weather-apps-ranked-2026), [Trustpilot](https://www.trustpilot.com/review/www.accuweather.com))

### The Weather Channel
- **Main screen**: current conditions, hourly/10-day, Doppler radar with future radar, severe alerts, "15-minute forecasts", 72-hour radar, and premium map layers behind $29.99/year; a $9.99/year ad-free tier. ([Play Store](https://play.google.com/store/apps/details?id=com.weather.Weather&hl=en_US), [support page on ads](https://support.weather.com/s/article/Advertising-on-The-Weather-Channel))
- **Praised**: radar quality, alert coverage. **Criticised**: an analysis of 991 recent 1–3 star reviews found 37 % about ads, including "ads standing between users and… a severe weather alert"; slow or broken radar after updates; upgrade nags. ([unstar.app analysis](https://unstar.app/blog/weather-channel-app-ads-before-alerts-radar-reviews-2026))

### Weather Underground
- **Main screen**: current conditions from a chosen **personal weather station** (250 000+), hourly/10-day, "Smart Forecast" per activity, a dense detail block (feels like, wind, rain accumulation today, humidity, dew point, visibility, pressure), AQI, UV, **flu outbreaks**, sunrise/sunset and **moonrise/moonset**, map with station, radar, satellite, heat and rain layers. $2/year to remove ads. ([Play Store](https://play.google.com/store/apps/details?id=com.wunderground.android.weather&hl=en_US), [BGR 2026](https://www.bgr.com/2176330/best-weather-apps-2026/))
- **Praised**: hyperlocal station data, depth for "budding meteorologists", good temperature accuracy. **Criticised**: ads, clutter, dated look.

### Windy.com
- **Main screen**: the **map is the app**: animated wind/rain/temperature/pressure/cloud/wave layers, model switcher (ECMWF, GFS, ICON, AROME, HRRR…), **meteogram and airgram** at a point (temperature, dew point, wind and gusts, pressure, precipitation, cloud layers by altitude), radar and satellite, webcams. 15-day ECMWF AIFS behind Premium. ([windy.com info](https://www.windy.com/info), [Windy articles](https://www.windy.com/articles/43908), [Play Store](https://play.google.com/store/apps/details?id=com.windyty.android))
- **Praised**: "powerful, smooth and fluid", unmatched for pilots, sailors, kiters. **Criticised**: overwhelming for a "do I need a coat" question; a reference tool, not a morning glance.

### Overdrop
- **Main screen**: a card stack (current, hourly, 7-day, feels like, wind, rain/hail/snow, UV, cloud cover, pressure, humidity), radar, **70+ widgets** with AMOLED themes, several data providers. ([Play Store](https://play.google.com/store/apps/details?id=widget.dd.com.overdrop.free&hl=en_US), [How-To Geek](https://www.howtogeek.com/i-finally-found-the-best-android-weather-widget/))
- **Praised**: "sleek, visually stunning", the widgets. **Criticised**: widgets "don't refresh automatically most of the time"; full widget set behind a subscription.

### Today Weather
- **Main screen**: dynamic backgrounds with condition animations, hourly 24 h and daily 10 d, AQI, moon, sun times, severe alerts, 12 selectable data sources (NWS, Met Office, DWD, Météo-France, Open-Meteo, ECMWF…), 23 widgets. ([Android Authority](https://www.androidauthority.com/best-weather-apps-and-weather-widgets-for-android-256942/), [XDA](https://www.xda-developers.com/best-weather-app-widget-android/), [Play Store](https://play.google.com/store/apps/details?id=mobi.lockdown.weather&hl=en_US&gl=US))
- **Praised**: "clean, functional, and quick", the "aesthetic champion of Android weather apps in 2026" for one roundup. **Criticised**: premium gates on sources and map layers; animated backgrounds can distract.

### Merry Sky and the Dark Sky successors
- **Merry Sky** (web, free, open source, data from Pirate Weather): a faithful Dark Sky layout: **next-hour precipitation chart**, a **horizontal week timeline** with rain bands and temperature overlaid, hourly temperature curve, minimal chrome. ([Houston Chronicle](https://www.houstonchronicle.com/business/tech/article/dwight-silverman-dark-sky-merry-sky-17728129.php), [Show HN](https://news.ycombinator.com/item?id=34155191), [U-M tech tip](https://michigan.it.umich.edu/news/2024/06/17/tech-tip-merry-sky))
- **Pirate Weather** itself is a Dark Sky-compatible API (HRRR/GFS/NBM), keyed with a free tier, offering minutely precipitation and alerts ([pirateweather.net](https://pirateweather.net/)).
- **Weather Strip** (iOS, by visualisation researcher Robin Stewart) deserves a mention: the whole week as one **time-series strip**: temperature line, stacked areas for cloud, rain-chance and thunder, blue bars for precipitation amount, UV and warnings on the same axis. ([9to5Mac](https://9to5mac.com/2021/06/03/weather-strip-iphone-and-ipad-app-debuts-with-unique-week-long-hourly-timeline-view/), [FlowingData](https://flowingdata.com/2021/07/23/weather-strip-an-app-that-shows-the-forecast-as-a-time-series/))
- **Praised**: the timeline makes "when does it start and stop" visible at a glance; "the only good weather app" eulogies for Dark Sky ([Defector](https://defector.com/goodbye-to-dark-sky-the-only-good-weather-app)). **Criticised**: Pirate Weather's accuracy outside the US; web-only.

### Weawow
- **Main screen**: a **user-submitted photo matching the current weather**, then a customisable layout of data rows (rain, gusts, pressure, UV…), animated map and radar, switchable providers (NWS, DWD, Météo-France, AEMET, MET Norway…). **No ads, no tracking**, donations optional. ([App Store](https://apps.apple.com/us/app/weather-widget-weawow/id1209810737), [Play Store](https://play.google.com/store/apps/details?id=com.weawow&hl=en_US))
- **Praised**: honest business model, photos, provider choice. **Criticised**: photos are pretty but uninformative; dense settings.

### Mercury Weather
- **Main screen**: current temperature, conditions, humidity, wind and UV on a **gradient that encodes temperature or cloud cover**; a 24-hour hourly section; an 8-day list with highs/lows, sunrise/sunset, UV max, wind and rainfall; **Trip Forecasts** placing upcoming destinations on the daily graph. Apple platforms only. ([MacStories review](https://www.macstories.net/reviews/mercury-weather-a-crystal-clear-design-for-every-apple-device/), [MacStories 2.0](https://www.macstories.net/reviews/mercury-weather-2-0-adds-trip-forecasts/))
- **Praised**: "crystal clear", "packs a lot of information into its main view without overwhelming you with numbers". **Criticised**: "if you're looking for radar and other more advanced features, you should try a different app".

### Newer standouts worth knowing (2025–2026)
- **Breezy Weather** (Android, FOSS, Material 3 Expressive, Open-Meteo by default): forecasts to 16 days, **precipitation in the next hour**, alerts, AQI, pollen, visibility, pressure, sun and moon, **hide or rearrange blocks**, 50+ sources, no trackers; the developer asks for donations to go to Open-Meteo. This is Daybreak's closest philosophical neighbour and a good benchmark for "what can be done keyless". ([GitHub](https://github.com/breezy-weather/breezy-weather), [How-To Geek](https://www.howtogeek.com/i-replaced-google-weather-for-an-open-source-alternative-and-im-never-going-back/), [It's FOSS](https://news.itsfoss.com/breezy-weather/))
- **Apple Weather iOS 26**: alerts for predicted destinations (see above).
- **AccuWeather 2025 redesign** ("50 enhancements", new maps) and **1Weather** keep appearing in 2026 roundups for balance of data and clarity. ([BGR 2026](https://www.bgr.com/2176330/best-weather-apps-2026/), [SoftPicker](https://softpicker.com/best-weather-apps/))

### Themes across the reviews
1. **Ads before alerts** is the single most hated thing in the big free apps (TWC, AccuWeather). Daybreak's "no ads" is a genuine differentiator, not a missing feature.
2. **"When will it rain, and when will it stop"** is what people miss from Dark Sky and praise in MinuteCast/Nowcast; every reviewer wants it, and they punish it when it's wrong.
3. **Calm by default, depth on tap** (Apple's expandable modules, CARROT's contextual cards, Pixel's reorderable cards) is the design pattern of the decade; Daybreak's explain sheets are already this pattern applied to words, not charts.
4. **Plain-language summaries** are now table stakes (Apple's conditions sentence, Pixel's Gemini Nano report, CARROT's one-liners). Daybreak's validated on-device Gemma is ahead here, not behind.
5. **Severe weather alerts** are expected of anything calling itself a weather app, and their absence is the first thing a reviewer would list.

---

## 3. Gap table

Legend: **Y** has it on the main screen, **p** partial or behind a tap/paywall, **–** not present, **?** not confirmed. Daybreak column: **yes / partial / no**.

| Feature | Apple | Pixel | CARROT | AccuW. | TWC | WU | Windy | Overdrop | Today | Merry Sky | Weawow | Mercury | Breezy | **Daybreak** |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Minute-by-minute rain, next hour ("starting in 12 min") | Y | Y | Y | Y | p ($) | p | p | – | p | Y | – | – | Y | **no** |
| Precipitation chart (hourly bars / amounts) | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | p | Y | **partial** (rain % text per hour; daily total only in the Rain sheet) |
| Radar / precipitation map | Y | Y (6 h nowcast) | Y | Y | Y | Y | Y | Y | p ($) | – | Y | – | Y | **no** |
| Severe weather alerts | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | – | Y | **no** |
| Air quality | Y | Y | Y | Y | Y | Y | p | p | Y | – | p | – | Y | **no** |
| Pollen | p | Y (6 countries) | p | Y | Y | p | – | – | – | – | – | – | Y (Europe) | **no** |
| Feels like | Y (hero, when different) | Y | Y | Y (RealFeel) | Y | Y | Y | Y | Y | Y | p | p | Y | **yes** (tile + sheet) |
| Humidity | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | **yes** |
| Dew point | Y (in Humidity) | p | Y | Y | p | Y | Y | p | p | Y | p | – | Y | **no** (computed, not shown) |
| Pressure + trend | Y (gauge, arrow) | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | – | Y | **no** |
| Visibility | Y | Y | Y | Y | Y | Y | Y | p | p | Y | p | – | Y | **no** |
| Cloud cover | p | p | Y | Y | p | p | Y | Y | p | Y | p | Y (gradient) | Y | **no** (only via the condition icon) |
| Wind direction / compass | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | p | Y | **no** (speed and gusts only) |
| Gusts | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | Y | – | Y | **yes** |
| UV index | Y | Y | Y | Y | Y | Y | p | Y | Y | Y | Y | Y | Y | **yes** (daily max + sheet) |
| Sunrise / sunset arc | Y | Y | Y | Y | Y | Y | p | Y | Y | Y | Y | Y | Y | **partial** (times in tiles; arc only in the sheet) |
| Moon phase / moonrise | Y | p | Y | Y | Y | Y | – | p | Y | Y | p | – | Y | **partial** (phase on Home, not in Weather) |
| Hourly temperature curve | Y | Y | Y | Y | Y | Y | Y (meteogram) | Y | Y | Y | Y | Y | Y | **no** (card strip) |
| Daily range bars on a shared scale | Y | Y | Y | p | p | p | – | p | p | Y (timeline) | p | Y | Y | **yes** |
| "Warmer / cooler than yesterday" | p (via Averages) | Y | Y | p | p | – | – | – | – | – | – | – | Y | **no** |
| "Vs normal", averages, records | Y (Averages) | – | Y (Time Machine) | p | p | Y (history) | – | – | – | – | – | – | Y (normals) | **no** |
| Notifications (rain, daily brief, severe) | Y | Y | Y | Y | Y | Y | Y | Y | Y | – | Y | p | Y | **no** |
| Home-screen widgets | Y | Y | Y | Y | Y | Y | Y | Y (70+) | Y (23) | – | Y | Y | Y | **yes** (4 sizes, dual units) |
| Lock-screen / always-on | Y | p | Y | p | – | – | – | p | p | – | – | Y | – | **no** |
| Animated / live backgrounds | Y | p (gradient) | p | p | p | – | Y (map) | p | Y | – | Y (photos) | p (gradient) | p | **partial** (static gradient by condition and time) |
| Map layers (wind, clouds, temp) | Y | p | p | Y | Y | Y | Y | p | p | – | Y | – | – | **no** |
| Per-hour detail on tap | Y | Y | Y | Y | Y | Y | Y | p | Y | Y | p | p | Y | **no** |
| Customisable card order / hide | – | Y | Y | – | – | – | p | Y | p | – | Y | – | Y | **no** |
| Plain-language summary | Y | Y (on-device AI) | Y | p | p | – | – | – | p | Y | – | – | – | **yes** (template or on-device Gemma, validated) |
| Tap-to-explain a term | p | – | – | – | – | – | – | – | – | – | – | – | – | **yes** (unique) |
| Activity windows ("best time to ride") | – | – | p | p (lifestyle) | p | Y (Smart Forecast) | Y (sports layers) | – | – | – | – | – | – | **yes** (unique in this form) |
| Accessibility (screen reader, large font tested) | Y | Y | p | p | p | p | p | p | p | p | p | Y | p | **yes** |
| No ads, no tracking, no account | Y | Y | p ($) | – | – | – | p | p | p | Y | Y | p ($) | Y | **yes** |

---

## 4. Each gap: data, value, effort, fit

Data-source facts were checked on 1 Oct 2026 against the live docs.

### Open-Meteo: what is available, free and keyless

- **Forecast API** ([docs](https://open-meteo.com/en/docs)): hourly `dew_point_2m`, `pressure_msl`, `surface_pressure`, `visibility`, `cloud_cover` (+ `_low/_mid/_high`), `wind_direction_10m`, `precipitation`, `rain`, `showers`, `snowfall`, `snow_depth`, `uv_index`, `uv_index_clear_sky`, `sunshine_duration`, `cape`, `freezing_level_height`, `apparent_temperature`, `relative_humidity_2m`; daily `daylight_duration`, `sunshine_duration`, `precipitation_hours`, `apparent_temperature_max/min`, `wind_direction_10m_dominant`, `rain_sum`, `showers_sum`, `snowfall_sum`; `forecast_days` up to **16**; **`past_days` up to 92** on the same call; `current` can hold any hourly variable.
- **`minutely_15`**: temperature, humidity, dew point, apparent temperature, **precipitation, rain, showers, snowfall**, weather code, wind speed/direction/gusts, visibility, lightning potential. **Native 15-minute data only in Central Europe (ICON-D2) and North America (HRRR); elsewhere it is interpolated from hourly**, so outside those regions it adds smoothness, not information. ([docs](https://open-meteo.com/en/docs))
- **Air Quality API** ([docs](https://open-meteo.com/en/docs/air-quality-api)): `european_aqi`, `us_aqi` (overall and per pollutant), `pm2_5`, `pm10`, `nitrogen_dioxide`, `ozone`, `sulphur_dioxide`, `carbon_monoxide`, `dust`, `ammonia`, `aerosol_optical_depth`, `uv_index`; **pollen: alder, birch, grass, mugwort, olive, ragweed, Europe only, in season, 4-day horizon**; Europe hourly at ~11 km, global 3-hourly at ~45 km, up to 7 days. Attribution to CAMS and Open-Meteo.
- **Historical Weather API** ([docs](https://open-meteo.com/en/docs/historical-weather-api)): ERA5 (25 km, 1940→, **5-day delay**), ERA5-Land (11 km, 1950→), ECMWF IFS (9 km, 2017→, no delay). Suitable for computing a 1991–2020 "normal for this date" per place once and caching it. The **Climate API** ([docs](https://open-meteo.com/en/docs/climate-api)) is CMIP6 model output and explicitly *not* suited to showing actual past weather, so it is the wrong tool for normals.
- **Marine API** ([docs](https://open-meteo.com/en/docs/marine-weather-api)): wave height/period/direction, swell, **sea surface temperature**, currents; global, 7–16 days; "not suitable for coastal navigation". Interesting only for a "sea temperature" line at coastal places.
- **Free tier** ([pricing](https://open-meteo.com/en/pricing)): non-commercial, **10 000 calls/day, 5 000/hour, 600/minute**; a request with more than 10 variables or more than two weeks counts fractionally (e.g. 15 variables × 2 weeks = 1.5 calls). Daybreak's current call carries 22 variables, so it already counts as roughly 2 calls; doubling the variables is still negligible for a personal app. Data is **CC BY 4.0**: Daybreak should keep crediting Open-Meteo (the README does; the app's Settings/About should too).
- **Alerts: none.** Requested since 2023 ([discussion #183](https://github.com/open-meteo/open-meteo/discussions/183), [issue #351](https://github.com/open-meteo/open-meteo/issues/351), [issue #828](https://github.com/open-meteo/open-meteo/issues/828)); the maintainer has said they are not planned for now.

### Alerts outside Open-Meteo
- **United States: NWS API** ([docs](https://www.weather.gov/documentation/services-web-api)): `GET https://api.weather.gov/alerts/active?point=lat,lon`, GeoJSON or CAP; **no key, only a descriptive `User-Agent`** (a key is "planned for the future"); "reasonable rate limits", retry after ~5 s; US only.
- **Europe: MeteoAlarm** ([api.meteoalarm.org](https://api.meteoalarm.org/)): per-country **Atom feeds** (legacy, kept for compatibility), an **OGC-API EDR** with position queries and MQTT real-time, and a Metadata API with region geometries and colour-coded awareness levels; **"free access to the public"**, no key, **CC BY 4.0, "Data provided by EUMETNET members"**. A Python client documents the feed structure ([meteoalarm on PyPI/Zenodo](https://zenodo.org/records/14885078)).
- **Elsewhere**: the WMO CAP aggregation at `severeweather.wmo.int` is what LibreWXR uses for global alerts ([LibreWXR README](https://github.com/honza-tichy/LibreWXR)); coverage and formats vary by country.
- **Verdict**: alerts are feasible, keyless and free for the US and Europe, with honest "no warnings source for this country" elsewhere. Effort M–L (two parsers, polygon or zone matching, caching, expiry).

### Radar
- **RainViewer** public API ([docs](https://www.rainviewer.com/api.html), [Weather Maps API](https://www.rainviewer.com/api/weather-maps-api.html), [transition FAQ](https://www.rainviewer.com/api/transition-faq.html)): `weather-maps.json` lists the **past 2 hours of composite radar at 10-minute steps**, tiles `{host}{path}/{size}/{z}/{x}/{y}/{color}/{smooth}_{snow}.png`. **"Free for personal or educational use only"**, attribution with a link required. Since **1 January 2026**: **no nowcast frames, Universal Blue colour scheme only, zoom capped at 7, 100 requests per IP per minute**; composites for free users were already cut on 1 Sept 2025 and the database API on 31 Dec 2025. Zoom 7 is a tile ~300 km wide at the equator: enough for "is there a band of rain to the west of the city", not for a street-level radar. Daybreak is personal and non-commercial, so the terms fit; the product does not.
- **LibreWXR** ([GitHub](https://github.com/honza-tichy/LibreWXR), [site](https://librewxr.net/)): AGPL, RainViewer-v2-compatible tiles from MRMS (US), MSC (Canada), EUMETNET OPERA (Europe, ~155 radars), JMA, CWA and others, ECMWF precipitation as the global fallback, plus WMO CAP alerts. There is a public instance (`api.librewxr.net`) but **its terms and rate limits are not published**; self-hosting is the honest route and is out of scope for a phone app.
- **Verdict**: a real radar is the one professional feature that is *not* cleanly available keyless at useful resolution in 2026. The better fit for Daybreak is the **next-two-hours precipitation timeline** from `minutely_15`, which answers the same question ("is rain coming, when, how hard") in words and bars rather than a map.

### Pollen outside Europe
Open-Meteo's pollen is CAMS and Europe-only. Google's Pollen API is keyed and billed; AccuWeather's is keyed. There is no good keyless source for US pollen; show the card only where data exists.

### Gap-by-gap

| Gap | Keyless data | Value | Effort | Fit with Daybreak |
|---|---|---|---|---|
| Next-hour / 2-hour rain timeline | Open-Meteo `minutely_15` precipitation + hourly `precipitation_probability` | High: the most-missed Dark Sky feature | **S–M** | Excellent: plain words first ("Rain starting around 3:15, easing by 4"), bars second |
| Hourly temperature curve with rain bars, scrubbable | Already fetched (+ hourly `precipitation`, `apparent_temperature`, `uv_index`, `relative_humidity_2m`) | High: shape of the day at a glance | **M** | Good if calm: one curve, one row of bars, no grid lines |
| Per-hour detail on tap | Same hourly variables | Medium | **S** once the chart exists | Good: reuse the explain sheet |
| Warmer/cooler than yesterday | `past_days=1` on the same call (zero extra calls) | Medium–high: Pixel and CARROT users cite it | **S** | Excellent: one line under the temperature, shown only when ≥ 3° |
| Vs normal for the date | Historical API ERA5, one cached fetch per place per year | Medium: travel planning, "is this unusual?" | **M** | Good, keep to a sentence |
| Severe weather alerts | NWS (US), MeteoAlarm (Europe) | High where it applies | **M–L** | Good if plain-language and never behind anything; honest about coverage |
| Air quality | Open-Meteo Air Quality API, one extra call per place | Medium–high in cities and wildfire season | **S–M** | Excellent with an explain sheet ("Moderate: fine for most, sensitive people may notice") |
| Pollen (Europe) | Same call | Medium for allergy sufferers | **S** on top of AQ | Good, seasonal, hide when absent |
| Wind direction and compass | `wind_direction_10m` (hourly + current) | Medium; high for cyclists | **S** | Excellent: "From the south-west" and an arrow in the Wind tile and sheet |
| Pressure + trend | `pressure_msl` hourly | Medium (weather-watchers, headaches) | **S** | Good as a tile with "Falling: unsettled weather on the way" |
| Dew point | `dew_point_2m` (already computed locally) | Medium in summer | **S** | Excellent inside the Humidity sheet rather than a new tile |
| Visibility | `visibility` | Low–medium (fog, driving) | **S** | Show the tile only when < 5 km |
| Cloud cover | `cloud_cover` | Low on its own; useful for Tonight's sky and sunsets | **S** | Fold into the sky card, not a tile |
| Precipitation amount in the 7-day list, snowfall | Already fetched `precipitation_sum`; add `snowfall_sum` | Medium | **S** | Good: "6 mm" under the rain % when ≥ 1 mm |
| 10-day list | `forecast_days=10` (16 allowed) | Low–medium; skill drops after day 7 | **S** | Fine behind "Show 3 more days" |
| Moon in the Weather tab | On-device (already in `TonightSky.kt`; moonrise/set is an algorithm, no API) | Low–medium | **S–M** | Good as a fourth tile next to sunrise/sunset at night |
| Notifications | Local WorkManager + the same data | Medium–high ("rain at 5 PM on your way home") | **M** | Good only if opt-in, quiet, and never for marketing |
| Lock-screen / more widgets | Glance | Medium | **M** | Good: an hourly-strip widget and a "best time to ride" widget |
| Radar map | RainViewer (zoom ≤ 7, 2 h past only) | Medium, but degraded by the 2026 limits | **M–L** | Poor fit at this resolution; defer |
| Map layers (wind, temperature) | Would need tiles nobody offers keyless | Low for this app | **L** | Poor fit: this is Windy's job |
| Customisable card order / hide | Settings only | Medium | **M** | Good in the "hide" form; order-dragging is nice-to-have |
| Animated backgrounds | None needed | Low; reviewers call them pretty, not useful | **M** | Poor fit: battery, distraction; the static gradient is the calm choice |
| Multiple data providers | Would need keys | Low | **L** | Poor fit: Open-Meteo already blends national models |

---

## 5. Prioritised list: the top ten additions

Each sketch assumes the existing visual language: cards on `surfaceContainer`, 20 dp page margin, the sky-gradient hero with white text, `weatherColors.rain` blue and `weatherColors.sun` amber, the green/amber/grey of the activity bars, `labelMedium` labels with the info glyph, and explain sheets for anything that needs a sentence.

### 1. Rain in the next two hours (S–M) · Open-Meteo `minutely_15`
A card that **appears only when the 15-minute precipitation series has anything in it in the next 120 minutes**, placed between the tiles and the hourly strip. Title "Rain soon" (or "Snow soon" when `snowfall` dominates), then a plain sentence built from the series: "Light rain starting around 3:15 PM, heaviest about 3:45, easing by 4:30." Under it, eight 15-minute bars in rain blue whose height is the amount, with "Now" and "+2 h" at the ends, the same height as the activity bars so the two cards rhyme. Tapping opens an explain sheet that says how this is made and, outside Central Europe and North America, that "the 15-minute detail is estimated from the hourly forecast". Also feed the sentence to the template and Gemma summaries ("Rain is likely around 3 PM" becomes "Rain starting around 3:15"). The widget's Full layout can carry the sentence instead of the generic summary when rain is within two hours.

### 2. "4° warmer than yesterday" (S) · Open-Meteo `past_days=1`
Add `past_days=1` to the forecast call (no extra API cost, yesterday's hourly and daily arrive in the same arrays; the parsers already keep all days). Under the condition line in the hero, a single `bodyMedium` line in white at 85 %: "4° warmer than yesterday at this time", or "About the same as yesterday", following Apple's rule of **showing it only when the difference is 3° or more** (otherwise the line is omitted and nothing shifts). Compare the current hour with the same hour yesterday, and today's high with yesterday's high in the Rain/High pills' explanation. It gives the Gemma prompt one more true fact to mention.

### 3. Wind direction (S) · Open-Meteo `wind_direction_10m`
The Wind tile gains a small arrow glyph drawn in code (like the icons) pointing where the wind is **going**, with the detail line "From the SW · Gusts 15 mph". The explain sheet gets a compass gauge (a circle, N marked, the arrow) next to the existing Calm→Gale bar, and a sentence: "Blowing from the south-west, so it's at your back heading north-east." The activity scorer can later use direction for the commute card (home → office bearing is computable from the two places' coordinates).

### 4. Hourly chart with per-hour detail (M) · data already fetched (+ hourly `precipitation`, `apparent_temperature`, `uv_index`)
Keep the card strip for the first 12 hours; **below it**, offer "Next 24 hours as a chart" (or make the strip itself a single card): a smooth temperature curve in `onSurface`, the night shaded with the hero's night colour at 10 %, sunrise and sunset ticked in amber on the x-axis, rain-amount bars in rain blue along the bottom (Weather Strip's grammar, Mercury's restraint). Dragging a finger shows a floating label "4 PM · 68° · 20 % · 9 mph SW · feels 66°"; tapping an hour opens a per-hour sheet in the explain-sheet style. Expose it to TalkBack as one node summarising peaks ("Warmest 74° at 3 PM, rain likeliest 60 % at 6 PM"), with the strip's cards remaining the per-hour accessible items. Respect reduced-motion for the drag highlight.

### 5. Air quality, and pollen in Europe (S–M) · Open-Meteo Air Quality API
One extra call per place (`european_aqi` or `us_aqi` chosen by country code, `pm2_5`, `pm10`, `ozone`, and the six pollen variables). A fourth tile in the second row, or a 2-tile row "Air quality · Pollen" when pollen exists: value word first ("Good", "Moderate", "Poor"), number small underneath, a five-step scale bar in green → amber → red using the activity-bar colours. The explain sheet gives the plain meaning ("Moderate: fine for most people; if you have asthma, you may notice it on a long ride") and names the main pollutant. Pollen appears **only in Europe and only in season**, as three words "Birch high · Grass low". Feed `Limit.AIR` into the activity scorer as a soft penalty when AQI is poor. Attribute "Air quality: CAMS via Open-Meteo" in Settings → About.

### 6. Severe weather warnings (M–L) · NWS API (US) and MeteoAlarm (Europe)
A **warning card directly under the hero**, before the tiles, on `errorContainer` for red/orange levels and an amber-tinted `surfaceContainer` for yellow: the official event name in plain words ("Wind warning until 9 PM tonight"), one line of the issuer's headline, a chevron. Tap for the full text, the issuing service and times in a sheet. Never a modal, never a notification unless the user opts in (see 9). Fetch with the forecast (US: `alerts/active?point=`; Europe: EDR position query or the country Atom feed filtered by region geometry), cache with the forecast, drop on expiry. In Settings → About, say honestly "Warnings come from the US National Weather Service and from Europe's national services via MeteoAlarm; other countries don't have a free source yet."

### 7. Pressure, dew point, visibility, where they earn their place (S) · Open-Meteo hourly
Don't grow the tile grid for its own sake. **Dew point** goes inside the Humidity sheet ("Dew point 64°F: muggy from here up") using the value the glossary already computes. **Pressure** becomes an optional tile (Settings → Weather → Show pressure) with a trend word from the last three hours: "1013 hPa · Falling", sheet text "Falling steadily: unsettled weather is usually on the way." **Visibility** appears as a tile only when it drops below about 5 km ("Visibility 1.2 km · Fog"), replacing nothing, in the second row.

### 8. Richer 7-day list and the sun/moon row (S) · already fetched + on-device moon
Under each day's rain % show the amount when ≥ 1 mm ("6 mm" / "0.2 in"), snowfall in the snow colour when relevant, and mark **today's current temperature as a dot on today's range bar** (Apple's touch, trivially cheap). Offer "3 more days" to reveal days 8–10 (`forecast_days=10`). At night, swap the UV tile (UV is 0 after sunset) for a **Moon tile** drawn with the same renderer as Home's sky card ("Waxing gibbous · 78 % lit · sets 3:12 AM"), reusing `TonightSky.kt` and adding a moonrise/moonset algorithm; its sheet explains the phase and names the next full moon.

### 9. Quiet notifications and one more widget (M) · local WorkManager
All opt-in, in Settings → Notifications: a **morning brief** at a chosen time with the summary line and the commute verdict; a **rain heads-up** 30 minutes before rain begins at the current place (from the `minutely_15` card's sentence); and **warnings** from item 6. Each is a single, silent, non-sticky notification with the sky colour, never more than one per hour, never promotional, computed by the existing `WidgetRefreshWorker` cycle (no new background budget). Add a **4×2 "today" widget** that shows the hourly strip of the next six hours, and a **2×2 "best time to ride"** widget for the activity card. Keep Gemma out of the background, as now.

### 10. "Vs normal for the date" (M) · Open-Meteo Historical API (ERA5)
Once per place per year, fetch the 1991–2020 daily highs and lows for a ±7-day window around today (30 small calls, cached in the forecast store, refreshed lazily) and derive a normal high and low. Show it only in the High/Low pills' explanation sheet and in the Gemma prompt: "A typical early-October day in San Francisco is 60–70°; today's 74° is on the warm side." It answers "is this unusual?" without a chart, and never claims a record (ERA5 is a reanalysis, not a station).

**Also worth doing, below the fold:** let users **hide** cards (activity, sky, meme already toggle; add tiles row 2, the chart, warnings) rather than drag-reorder; put the Open-Meteo, CAMS, NWS and MeteoAlarm attributions in a Settings → About screen; add a "reduce motion" check before any chart animation.

---

## 6. Not to copy

- **Ads, interstitials, upsell banners, premium nags.** The single most-criticised thing across TWC and AccuWeather reviews, with 37 % of recent negative TWC reviews about ads, many during alerts. Daybreak's absence of all of this is the feature to protect.
- **News feeds, videos, "flu outbreaks", lifestyle indices** (WU, TWC, AccuWeather): clutter that pushes the forecast below the fold.
- **A radar as the centrepiece.** At RainViewer's 2026 free limits (zoom ≤ 7, no nowcast, two hours of history) it would be a blurry blue blob; the 15-minute precipitation card says the same thing in words.
- **Animated or photo backgrounds** (Today Weather, Weawow): pretty in reviews, a battery and attention cost in daily use; Daybreak's condition-and-time gradient with WCAG-checked contrast already does the emotional job.
- **Dozens of widget skins and data-provider toggles** (Overdrop, Today, Weawow): settings sprawl; one provider that blends national models is enough, and more sources mean keys and tracking.
- **Fifteen-day forecasts and minute-accurate claims.** Skill beyond day 7 is poor and MinuteCast-style precision is what reviewers punish when wrong; keep "around 3:15" and "likely", and say when the data is interpolated.
- **Reading Contacts or routes for Home/Work** (Apple): Daybreak already lets the user set home and office explicitly and keeps them off backup; keep it that way.
- **Snark as a default** (CARROT): voices are optional in Daybreak, and the Friendly default should stay the default.
- **Showing "feels like" when it equals the temperature.** Apple's rule, worth adopting in reverse: hide the yesterday/normal lines when there's nothing to say.
- **Duplicating Home on Weather.** Tonight's sky, the commute verdict and Coming up belong on Home; the Weather tab should gain the moon tile only at night and keep one job.

---

## Sources

Daybreak code and snapshots: `/home/mazzucci/projects/Daybreak/app/src/main/java/app/daybreak/{ui/WeatherScreen.kt, ui/ExplainSheet.kt, domain/Glossary.kt, ui/ActivityCard.kt, domain/Activity.kt, ui/Sky.kt, ui/WeatherIcons.kt, widget/WeatherWidget.kt, data/OpenMeteoApi.kt, data/OpenMeteoParsers.kt, domain/Models.kt, domain/TonightSky.kt}`, `README.md`, `app/src/test/snapshots/images/*weather_*.png`.

Data APIs: [Open-Meteo forecast docs](https://open-meteo.com/en/docs) · [Air Quality API](https://open-meteo.com/en/docs/air-quality-api) · [Historical Weather API](https://open-meteo.com/en/docs/historical-weather-api) · [Climate API](https://open-meteo.com/en/docs/climate-api) · [Marine API](https://open-meteo.com/en/docs/marine-weather-api) · [Open-Meteo pricing / free limits](https://open-meteo.com/en/pricing) · [Open-Meteo alerts discussion #183](https://github.com/open-meteo/open-meteo/discussions/183), [issue #351](https://github.com/open-meteo/open-meteo/issues/351), [issue #828](https://github.com/open-meteo/open-meteo/issues/828) · [NWS API](https://www.weather.gov/documentation/services-web-api) · [MeteoAlarm API portal](https://api.meteoalarm.org/) · [RainViewer API](https://www.rainviewer.com/api.html), [Weather Maps API](https://www.rainviewer.com/api/weather-maps-api.html), [transition FAQ](https://www.rainviewer.com/api/transition-faq.html), [terms](https://www.rainviewer.com/terms.html) · [LibreWXR](https://github.com/honza-tichy/LibreWXR) · [Pirate Weather](https://pirateweather.net/).

Apps: [Gadget Hacks, iOS 18 Weather](https://ios.gadgethacks.com/how-to/apples-weather-app-just-got-13-new-features-and-changes-latest-iphone-software-update-0385607/) · [Gadget Hacks, Weather modules](https://ios.gadgethacks.com/how-to/your-iphones-weather-app-just-got-14-major-new-features-0385062/) · [AppleInsider](https://appleinsider.com/inside/ios-19/tips/inside-apple-weather-get-the-most-out-of-apples-own-forecasting-app) · [MacRumors iOS 26](https://www.macrumors.com/2025/06/10/ios-26-severe-weather-predicted-destinations/) · [Android Authority, Pixel Weather](https://www.androidauthority.com/pixel-weather-app-3469834/) · [9to5Google, Pixel Weather leak](https://9to5google.com/2024/08/07/pixel-weather-leak-install/) · [9to5Google, pollen](https://9to5google.com/2025/05/01/pixel-weather-pollen-tracker-us/) · [9to5Google, Expressive redesign](https://9to5google.com/2025/08/25/pixel-weather-expressive-redesign/) · [Google Pixel help](https://support.google.com/pixelphone/answer/15266029?hl=en) · [CARROT v5](http://www.meetcarrot.com/weather/v5.html) · [AndroidGuys CARROT review](https://androidguys.com/reviews/carrot-weather-review/) · [unstar.app, five apps ranked](https://unstar.app/blog/accuweather-weather-channel-carrot-apple-weather-underground-weather-apps-ranked-2026) · [unstar.app, TWC ads analysis](https://unstar.app/blog/weather-channel-app-ads-before-alerts-radar-reviews-2026) · [Yahoo, MinuteCast](https://www.yahoo.com/news/understanding-accuweather-apps-minutecast-142055023.html) · [TWC Play Store](https://play.google.com/store/apps/details?id=com.weather.Weather&hl=en_US) · [WU Play Store](https://play.google.com/store/apps/details?id=com.wunderground.android.weather&hl=en_US) · [BGR best apps 2026](https://www.bgr.com/2176330/best-weather-apps-2026/) · [Windy info](https://www.windy.com/info) · [Overdrop Play Store](https://play.google.com/store/apps/details?id=widget.dd.com.overdrop.free&hl=en_US) · [How-To Geek, Overdrop widget](https://www.howtogeek.com/i-finally-found-the-best-android-weather-widget/) · [Android Authority, best Android weather apps](https://www.androidauthority.com/best-weather-apps-and-weather-widgets-for-android-256942/) · [XDA](https://www.xda-developers.com/best-weather-app-widget-android/) · [Houston Chronicle, Merry Sky](https://www.houstonchronicle.com/business/tech/article/dwight-silverman-dark-sky-merry-sky-17728129.php) · [Show HN, Merry Sky](https://news.ycombinator.com/item?id=34155191) · [Defector on Dark Sky](https://defector.com/goodbye-to-dark-sky-the-only-good-weather-app) · [9to5Mac, Weather Strip](https://9to5mac.com/2021/06/03/weather-strip-iphone-and-ipad-app-debuts-with-unique-week-long-hourly-timeline-view/) · [FlowingData, Weather Strip](https://flowingdata.com/2021/07/23/weather-strip-an-app-that-shows-the-forecast-as-a-time-series/) · [Weawow App Store](https://apps.apple.com/us/app/weather-widget-weawow/id1209810737) · [MacStories, Mercury Weather](https://www.macstories.net/reviews/mercury-weather-a-crystal-clear-design-for-every-apple-device/) · [MacStories, Mercury 2.0](https://www.macstories.net/reviews/mercury-weather-2-0-adds-trip-forecasts/) · [Breezy Weather GitHub](https://github.com/breezy-weather/breezy-weather) · [How-To Geek, Breezy](https://www.howtogeek.com/i-replaced-google-weather-for-an-open-source-alternative-and-im-never-going-back/) · [Tom's Guide best weather apps](https://tomsguide.com/round-up/best-weather-apps) · [SoftPicker 2026](https://softpicker.com/best-weather-apps/).
