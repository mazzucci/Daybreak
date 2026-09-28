package com.mazzucci.weather

import com.mazzucci.weather.domain.CurrentConditions
import com.mazzucci.weather.domain.DaySummary
import com.mazzucci.weather.domain.Forecast
import com.mazzucci.weather.domain.HourForecast
import com.mazzucci.weather.domain.Place
import java.time.LocalDateTime

/** Shared sample data for unit and screenshot tests. */
object TestData {
    fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()

    val now: LocalDateTime = LocalDateTime.of(2026, 9, 28, 14, 30)

    val sanFrancisco = Place("5391959", "San Francisco", "California", "United States", 37.7749, -122.4194)
    val london = Place("2643743", "London", "England", "United Kingdom", 51.5085, -0.1257)
    val tokyo = Place("1850147", "Tokyo", "Tokyo", "Japan", 35.6895, 139.6917)

    /** 21.4°C (71°F) partly cloudy now; high 23.6 (74°F), low 13.2 (56°F); rain likely at 6 PM. */
    fun forecast(
        tempC: Double = 21.4,
        rainAt: Int? = 4,
    ): Forecast = Forecast(
        current = CurrentConditions(now, tempC, feelsLikeC = 20.1, humidity = 58, windKmh = 14.2, code = 2),
        today = DaySummary(highC = 23.6, lowC = 13.2, precipChance = if (rainAt != null) 60 else 5, code = 61),
        nextHours = (0 until 12).map { i ->
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
            )
        },
    )
}
