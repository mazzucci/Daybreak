# WeatherApp

A simple Android app that shows the weather for your current location and any places you save, in both °F and °C.

- Search for any city and save it; swipe between places, reorder or remove them
- Current location is optional: turn it off and use saved places only
- A one-line summary at the top, a large temperature in your preferred unit with the other unit alongside, today's high/low and rain chance, and the next 12 hours
- Today's sunrise, sunset, UV index and wind gusts, plus a 7-day list with each day's range on a shared scale
- Optional on-device AI summary written by [Gemma](https://ai.google.dev/gemma), run locally with [MediaPipe LLM Inference](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference). No data leaves the phone
- Weather and place search from [Open-Meteo](https://open-meteo.com/) (free, no API key). The app fetches 8 days of hourly and daily data per place, including wind, gusts, sun times and UV
- Uses Android's built-in location service (no Google Play Services required)
- Kotlin + Jetpack Compose, min Android 8.0 (API 26)

## Screenshots

The backdrop follows the conditions and the time of day at each place (clear, cloudy, rain, snow, storm; day or night, from each place's real sunrise and sunset), and the app has its own light and dark palettes.

| Weather | Dark | Gemma summary | Rainy night |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_weatherLight_weather_light.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_weatherDark_weather_dark.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_weatherGemma_weather_gemma.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_weatherRainyNight_weather_rainy_night.png" width="200"> |

| First run | Loading | Permission | Error |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_empty_empty.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_loading_loading.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_permission_permission.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_error_error.png" width="200"> |

| Search | Places | Settings: set up Gemma | Settings: downloading |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_search_search.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_places_places.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_settingsNotInstalled_settings_not_installed.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_settingsDownloading_settings_downloading.png" width="200"> |

| Full page | Full page (dark, °C) |
|:---:|:---:|
| <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_weatherFullPage_weather_full_page.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_weatherFullPageDark_weather_full_page_dark.png" width="200"> |

| Settings: installed | Settings: failed (dark) | Rainy night (dark) | App icon |
|:---:|:---:|:---:|:---:|
| <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_settingsInstalled_settings_installed.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_settingsFailed_settings_failed.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_weatherRainyNightDark_weather_rainy_night_dark.png" width="200"> | <img src="app/src/test/snapshots/images/com.mazzucci.weather.ui_ScreenshotTest_appIcon_app_icon.png" width="120"> |

These are rendered from the app's real UI code with sample data using [Paparazzi](https://github.com/cashapp/paparazzi). Weather icons and the launcher icon are drawn in code and as vector drawables, so there are no bitmap assets. To regenerate the screenshots after UI changes:

```
./gradlew recordPaparazziDebug
```

`./gradlew verifyPaparazziDebug` fails if the UI no longer matches them.

## On-device Gemma summary

The summary line always starts as a built-in template ("71° and partly cloudy now, with a high of 74°…"). Once a Gemma model is installed, the app also asks Gemma to describe the forecast and shows its text instead, marked "Written by Gemma on this device".

To set it up, go to **Settings → AI summary** and follow the three steps on screen:

1. Open the model page, [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT), and accept the Gemma license (free Hugging Face account).
2. Create a Hugging Face access token with read permission and paste it into the app. The token is only used once, to start the download; it isn't stored.
3. Download the model (about 550 MB, Wi-Fi recommended). The download runs through the system download manager, so it carries on in the background (and resumes being tracked if the app is closed), and the file is checked against the SHA-256 checksum Hugging Face reports (or its expected size, if no checksum is given) before being installed.

If you already have `gemma3-1b-it-int4.task` on the phone, **Import file…** copies it into the app's own storage instead, so you can delete the original afterwards.

How it works:

- The model runs on the CPU through MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai`), with low temperature and a 30-second timeout. It's loaded on first use and kept in memory while the app runs.
- The prompt is a short instruction plus the forecast as JSON, in your preferred unit ([`GemmaPrompt`](app/src/main/java/com/mazzucci/weather/narration/GemmaPrompt.kt)).
- Gemma's reply is checked before it's shown ([`NarrationValidator`](app/src/main/java/com/mazzucci/weather/narration/NarrationValidator.kt)). Every temperature, percentage and other number must match the forecast data, and the reply must be at most 2 short sentences of plain text. If the check fails, or the model is missing, slow or errors out, the template summary stays.
- Expect a few seconds per summary on recent phones and longer on older ones. MediaPipe's native library makes the APK bigger, so it's built only for 64-bit ARM (phones) and x86_64 (emulators). It needs a 64-bit device.

## Code layout

```
app/src/main/java/com/mazzucci/weather/
  domain/     Place, Forecast, AppSettings; unit conversion and formatting; WMO code descriptions
  data/       HttpClient, Open-Meteo forecast + geocoding API and JSON parsers,
              saved places + settings repositories (SharedPreferences), device location
  narration/  WeatherNarrator: TemplateNarrator, GemmaNarrator (MediaPipe), GemmaPrompt,
              NarrationValidator, ValidatingNarrator, GemmaModelStore (model download + import)
  ui/         WeatherViewModel (StateFlow), stateless screens, WeatherApp (navigation, pickers),
              Theme (palettes, type), Sky (condition -> backdrop), WeatherIcons (Canvas-drawn glyphs)
```

## Build and test

Requires JDK 17 and the Android SDK (`ANDROID_HOME` or `local.properties` pointing at it).

```
./gradlew testDebugUnitTest verifyPaparazziDebug assembleDebug
```

[GitHub Actions](.github/workflows/ci.yml) runs the same command on every pull request and push to `main`, and uploads the test reports and screenshot diffs if a test or screenshot check fails.

The unit tests cover JSON parsing (with real Open-Meteo responses as fixtures), formatting, the template narrator, LLM output validation, the repositories and the ViewModel (with fakes). The APK ends up in `app/build/outputs/apk/debug/`.

## License

[MIT](LICENSE)
