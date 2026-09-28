package com.mazzucci.weather

import com.mazzucci.weather.domain.CurrentConditions
import com.mazzucci.weather.domain.DaySummary
import com.mazzucci.weather.domain.FORECAST_DAYS
import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.HourForecast
import com.mazzucci.weather.domain.Place
import java.time.LocalDateTime

/** Shared sample data for unit and screenshot tests. */
object TestData {
    fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()

    val now: LocalDateTime = LocalDateTime.of(2026, 9, 28, 14, 30)

    val sanFrancisco = Place("geo:5391959", "San Francisco", "California", "United States", 37.7749, -122.4194)
    val london = Place("geo:2643743", "London", "England", "United Kingdom", 51.5085, -0.1257)
    val tokyo = Place("geo:1850147", "Tokyo", "Tokyo", "Japan", 35.6895, 139.6917)

    /** 21.4°C (71°F) partly cloudy now; high 23.6 (74°F), low 13.2 (56°F); rain likely at 6 PM. */
    fun forecast(
        tempC: Double = 21.4,
        rainAt: Int? = 4,
    ): Forecast = Forecast(
        current = CurrentConditions(now, tempC, feelsLikeC = 20.1, humidity = 58, windKmh = 14.2, code = 2),
        days = week(now, DaySummary(now.toLocalDate(), 23.6, 13.2, precipChance = if (rainAt != null) 60 else 5, code = 61, uvIndexMax = 6.2)),
        hours = (0 until 12).map { i ->
            HourForecast(
                time = now.withMinute(0).plusHours(i.toLong()),
                tempC = tempC - i * 0.6,
                precipChance = when {
                    rainAt == null -> 0
                    i == rainAt -> 60
                    i > rainAt -> 40
                    else -> 10
                },
                code = if (rainAt != null && i >= rainAt) 61 else 2,
                windKmh = 14.2 + i,
                gustKmh = 24.0 + i,
            )
        },
    )

    /** 9.8°C (50°F), steady rain at 10:30 PM in London; a few hours of showers then clearing to overcast. */
    fun rainyNight(): Forecast {
        val at = LocalDateTime.of(2026, 9, 28, 22, 30)
        return Forecast(
            current = CurrentConditions(at, tempC = 9.8, feelsLikeC = 7.4, humidity = 91, windKmh = 27.0, code = 63),
            days = week(at, DaySummary(at.toLocalDate(), 14.1, 8.3, precipChance = 90, code = 63, uvIndexMax = 1.4)),
            hours = (0 until 12).map { i ->
                HourForecast(
                    time = at.withMinute(0).plusHours(i.toLong()),
                    tempC = 9.8 - i * 0.2,
                    precipChance = when {
                        i < 3 -> 90
                        i < 6 -> 60
                        else -> 20
                    },
                    code = when {
                        i < 3 -> 63
                        i < 6 -> 80
                        i < 9 -> 3
                        else -> 2
                    },
                )
            },
        )
    }

    /**
     * [today] followed by varied days (a wet one, a sunny one, …), so tests see a realistic multi-day forecast.
     * Every day gets sunrise 7:02 and sunset 18:56 plus wind and UV.
     */
    private fun week(now: LocalDateTime, today: DaySummary): List<DaySummary> =
        (listOf(today) + (1L until FORECAST_DAYS).map { d ->
            val wet = d == 2L
            DaySummary(
                now.toLocalDate().plusDays(d),
                highC = today.highC + listOf(0, 2, -4, 1, 3, -1, 2, 0)[d.toInt()],
                lowC = today.lowC + listOf(0, 1, -2, 0, 2, -1, 1, 0)[d.toInt()],
                precipChance = if (wet) 70 else 10,
                code = if (wet) 63 else listOf(0, 1, 2, 3)[d.toInt() % 4],
                uvIndexMax = if (wet) 2.0 else 5.5,
            )
        }).map { day ->
            day.copy(
                sunrise = day.date.atTime(7, 2),
                sunset = day.date.atTime(18, 56),
                windMaxKmh = 20.0,
                gustMaxKmh = 35.0,
                precipSumMm = if (day.precipChance >= 60) 6.5 else 0.0,
            )
        }
}
