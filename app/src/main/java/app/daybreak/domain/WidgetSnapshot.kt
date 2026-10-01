package app.daybreak.domain

import java.time.LocalDateTime

/**
 * Everything the home-screen widget shows, saved so it can draw without the app running. Written by the app
 * whenever its first page changes, and refreshed in the background.
 */
data class WidgetSnapshot(
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val unit: TempUnit,
    val tempC: Double,
    val highC: Double,
    val lowC: Double,
    val precipChance: Int,
    val code: Int,
    val night: Boolean,
    val summary: String,
    /** Forecast time at the place (its local clock), shown as "Updated 9:41 AM". */
    val updatedAt: LocalDateTime,
    /** When this snapshot was written, epoch millis, so a background refresh can tell the app wrote a newer one. */
    val writtenAtMillis: Long,
)

fun widgetSnapshotOf(
    place: Place,
    forecast: Forecast,
    unit: TempUnit,
    summary: String,
    nowMillis: Long,
) = WidgetSnapshot(
    placeName = place.name,
    latitude = place.latitude,
    longitude = place.longitude,
    unit = unit,
    tempC = forecast.current.tempC,
    highC = forecast.today.highC,
    lowC = forecast.today.lowC,
    precipChance = forecast.today.precipChance,
    code = forecast.current.code,
    night = forecast.isNightNow,
    summary = summary,
    updatedAt = forecast.current.time,
    writtenAtMillis = nowMillis,
)

/** A background refresh: new numbers and [summary] from [forecast], for the same place. */
fun refreshedSnapshot(old: WidgetSnapshot, forecast: Forecast, summary: String, nowMillis: Long): WidgetSnapshot =
    old.copy(
        tempC = forecast.current.tempC,
        highC = forecast.today.highC,
        lowC = forecast.today.lowC,
        precipChance = forecast.today.precipChance,
        code = forecast.current.code,
        night = forecast.isNightNow,
        summary = summary,
        updatedAt = forecast.current.time,
        writtenAtMillis = nowMillis,
    )
