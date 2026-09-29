package com.mazzucci.weather.domain

import java.time.LocalDateTime

/**
 * Everything the home-screen widget shows, saved so it can draw without the app running. Written by the app
 * whenever its first page changes, and refreshed in the background (numbers and the template summary only;
 * Gemma never runs in the background).
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
    val summaryByGemma: Boolean,
    /** Forecast time at the place (its local clock), shown as "Updated 9:41 AM". */
    val updatedAt: LocalDateTime,
    /** When this snapshot was written, epoch millis: how fresh a Gemma summary is. */
    val writtenAtMillis: Long,
)

fun widgetSnapshotOf(
    place: Place,
    forecast: Forecast,
    unit: TempUnit,
    summary: String,
    summaryByGemma: Boolean,
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
    summaryByGemma = summaryByGemma,
    updatedAt = forecast.current.time,
    writtenAtMillis = nowMillis,
)

/**
 * A background refresh: new numbers from [forecast], and [templateSummary] unless the saved summary is Gemma's
 * and still recent (Gemma's line is worth keeping for a while, but not once it describes a different afternoon).
 */
fun refreshedSnapshot(
    old: WidgetSnapshot,
    forecast: Forecast,
    templateSummary: String,
    nowMillis: Long,
    gemmaKeepMillis: Long = GEMMA_KEEP_MILLIS,
): WidgetSnapshot {
    val keepGemma = old.summaryByGemma && nowMillis - old.writtenAtMillis < gemmaKeepMillis
    return old.copy(
        tempC = forecast.current.tempC,
        highC = forecast.today.highC,
        lowC = forecast.today.lowC,
        precipChance = forecast.today.precipChance,
        code = forecast.current.code,
        night = forecast.isNightNow,
        summary = if (keepGemma) old.summary else templateSummary,
        summaryByGemma = keepGemma,
        updatedAt = forecast.current.time,
        // A kept Gemma summary keeps its age; otherwise the snapshot is new.
        writtenAtMillis = if (keepGemma) old.writtenAtMillis else nowMillis,
    )
}

/** Three hours: roughly how long a "next few hours" summary stays true. */
const val GEMMA_KEEP_MILLIS = 3L * 60 * 60 * 1000
