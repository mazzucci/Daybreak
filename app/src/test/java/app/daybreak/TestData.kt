package app.daybreak

import app.daybreak.domain.CurrentConditions
import app.daybreak.domain.DaySummary
import app.daybreak.domain.FORECAST_DAYS
import app.daybreak.domain.Forecast
import app.daybreak.domain.HourForecast
import app.daybreak.domain.Place
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos

/** Shared sample data for unit and screenshot tests. */
object TestData {
    fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$name")) { "Missing fixture $name" }.readText()

    val now: LocalDateTime = LocalDateTime.of(2026, 9, 28, 14, 30)

    val sanFrancisco = Place("geo:5391959", "San Francisco", "California", "United States", 37.7749, -122.4194, countryCode = "US")
    val london = Place("geo:2643743", "London", "England", "United Kingdom", 51.5085, -0.1257, countryCode = "GB")
    val tokyo = Place("geo:1850147", "Tokyo", "Tokyo", "Japan", 35.6895, 139.6917, countryCode = "JP")

    /**
     * 21.4°C (71°F) partly cloudy now; high 23.6 (74°F), low 13.2 (56°F); rain possible from 5 PM (60% stamped 6 PM,
     * then 40%), 4.1 mm of it still to come, about 6.5 mm today. From 6 PM the breeze makes it feel 2.5°C colder,
     * enough to show in °F. Wind from the SW.
     */
    fun forecast(
        tempC: Double = 21.4,
        rainAt: Int? = 4,
    ): Forecast = Forecast(
        current = CurrentConditions(now, tempC, feelsLikeC = 20.1, humidity = 58, windKmh = 14.2, code = 2, windDirectionDeg = 235.0),
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
                feelsLikeC = tempC - i * 0.6 - if (rainAt != null && i >= rainAt) 2.5 else 0.5,
                precipMm = if (rainAt == null || i < rainAt) 0.0 else listOf(0.6, 1.8, 1.2, 0.4, 0.1).getOrElse(i - rainAt) { 0.0 },
                windDirectionDeg = 235.0,
            )
        },
        utcOffsetSeconds = -7 * 3600, // PDT
    )

    /** 9.8°C (50°F), steady rain at 10:30 PM in London; a few hours of showers then clearing to overcast. */
    fun rainyNight(): Forecast {
        val at = LocalDateTime.of(2026, 9, 28, 22, 30)
        return Forecast(
            current = CurrentConditions(at, tempC = 9.8, feelsLikeC = 7.4, humidity = 91, windKmh = 27.0, code = 63, windDirectionDeg = 200.0),
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
                    feelsLikeC = 7.4 - i * 0.2,
                    precipMm = when {
                        i < 3 -> 1.6
                        i < 6 -> 0.4
                        else -> 0.0
                    },
                    windDirectionDeg = 200.0,
                )
            },
            utcOffsetSeconds = 3600, // BST
        )
    }

    /**
     * [today] followed by varied days (a wet one, a showery one, a sunny one, …), so tests see a realistic 10-day
     * forecast. Every day gets sunrise 7:02 and sunset 18:56 plus wind and UV.
     */
    private fun week(now: LocalDateTime, today: DaySummary): List<DaySummary> =
        (listOf(today) + (1L until FORECAST_DAYS).map { d ->
            val wet = d == 2L
            val showery = d == 8L
            DaySummary(
                now.toLocalDate().plusDays(d),
                highC = today.highC + listOf(0, 2, -4, 1, 3, -1, 2, 0, -2, 1)[d.toInt()],
                lowC = today.lowC + listOf(0, 1, -2, 0, 2, -1, 1, 0, -1, 0)[d.toInt()],
                precipChance = if (wet) 70 else if (showery) 45 else 10,
                code = if (wet) 63 else if (showery) 80 else listOf(0, 1, 2, 3)[d.toInt() % 4],
                uvIndexMax = if (wet) 2.0 else 5.5,
                precipSumMm = if (showery) 2.4 else null,
                precipHours = if (showery) 3.0 else null,
            )
        }).map { day ->
            day.copy(
                sunrise = day.date.atTime(7, 2),
                sunset = day.date.atTime(18, 56),
                windMaxKmh = 20.0,
                gustMaxKmh = 35.0,
                windDirectionDeg = 250.0,
                precipSumMm = day.precipSumMm ?: if (day.precipChance >= 60) 6.5 else 0.0,
                precipHours = day.precipHours ?: if (day.precipChance >= 60) 5.0 else 0.0,
            )
        }

    /** A real 10-day Open-Meteo response for the Jungfraujoch (3,200 m): showers, a dry spell, then two days of snow. */
    fun alps(): Forecast = app.daybreak.data.parseForecast(fixture("forecast_alps_10day.json"))

    /**
     * One day of a [synthetic] forecast: its low (at 5 AM) and high (at 3 PM), the hours rain falls in ([rainHours],
     * each the start of the hour, 0–22) with their chance and amount, snow instead of rain, and the wind.
     */
    data class DaySpec(
        val highC: Double = 20.0,
        val lowC: Double = 11.0,
        val rainHours: IntRange? = null,
        val chance: Int = 80,
        val mmPerHour: Double = 1.5,
        val snow: Boolean = false,
        val windKmh: Double = 12.0,
        val gustKmh: Double = 25.0,
    )

    /** How the sun behaves in a [synthetic] forecast. */
    enum class Sun { NORMAL, POLAR_NIGHT, MIDNIGHT_SUN }

    /**
     * A forecast with full hourly data for [days] (today first, at [at]), built to order for the outlook's rules: a
     * smooth day from each low to high, rain stamped at the end of the hour it falls in (see Precip) with the weather
     * code at its start (an instant value), sunrise 7 AM and
     * sunset 7 PM unless [sun] says otherwise.
     */
    fun synthetic(days: List<DaySpec>, at: LocalDateTime = LocalDateTime.of(2026, 10, 1, 8, 0), sun: Sun = Sun.NORMAL): Forecast {
        val start = at.toLocalDate()
        fun stamp(d: Int, h: Int) = start.plusDays(d.toLong()).atTime(h, 0)
        val hours = days.flatMapIndexed { d, spec ->
            (0 until 24).map { h ->
                // The rain falling during hour h - 1 is stamped h.
                val wet = spec.rainHours?.contains(h - 1) == true
                val t = spec.lowC + (spec.highC - spec.lowC) * (1 - cos(PI * ((h - 5 + 24) % 24).coerceAtMost(20) / 10.0)) / 2
                HourForecast(
                    time = stamp(d, h),
                    tempC = t,
                    precipChance = if (wet) spec.chance else 5,
                    // The code is instant (Open-Meteo): it's raining at h when rain falls during the hour from h.
                    code = if (spec.rainHours?.contains(h) == true) (if (spec.snow) 73 else 63) else 1,
                    windKmh = spec.windKmh,
                    gustKmh = spec.gustKmh,
                    precipMm = if (wet) spec.mmPerHour else 0.0,
                    snowCm = if (wet && spec.snow) spec.mmPerHour * 0.7 else 0.0,
                )
            }
        }
        val summaries = days.mapIndexed { d, spec ->
            val date: LocalDate = start.plusDays(d.toLong())
            val dayHours = hours.filter { it.time.toLocalDate() == date }
            val (rise, set) = when (sun) {
                Sun.NORMAL -> date.atTime(7, 0) to date.atTime(19, 0)
                Sun.POLAR_NIGHT -> date.atStartOfDay() to date.atStartOfDay()
                Sun.MIDNIGHT_SUN -> date.atStartOfDay() to date.plusDays(1).atStartOfDay()
            }
            DaySummary(
                date, spec.highC, spec.lowC,
                precipChance = dayHours.maxOf { it.precipChance },
                code = if (spec.rainHours != null) (if (spec.snow) 73 else 63) else 1,
                sunrise = rise, sunset = set,
                windMaxKmh = spec.windKmh, gustMaxKmh = spec.gustKmh,
                precipSumMm = dayHours.sumOf { it.precipMm ?: 0.0 },
                precipHours = dayHours.count { (it.precipMm ?: 0.0) > 0.0 }.toDouble(),
                snowSumCm = dayHours.sumOf { it.snowCm ?: 0.0 },
            )
        }
        val now = hours.firstOrNull { !it.time.isAfter(at) && it.time.plusHours(1).isAfter(at) } ?: hours.first()
        return Forecast(
            current = CurrentConditions(at, now.tempC, now.tempC, humidity = 60, windKmh = now.windKmh ?: 0.0, code = now.code),
            days = summaries,
            hours = hours,
        )
    }

    /** Monday, October 5, 2026, 8 AM: the start of the synthetic weeks below. */
    val monday: LocalDateTime = LocalDateTime.of(2026, 10, 5, 8, 0)

    /**
     * A mixed week from [monday]: breezy today, a rainy spell Tuesday to Thursday (Thursday clearing late), a cool
     * Friday and a sunny weekend, then three ordinary days.
     */
    fun mixedWeek(at: LocalDateTime = monday): Forecast = synthetic(
        listOf(
            DaySpec(highC = 16.0, windKmh = 38.0, gustKmh = 50.0),
            DaySpec(highC = 14.0, rainHours = 8..20, chance = 85),
            DaySpec(highC = 12.0, rainHours = 0..22, chance = 90),
            DaySpec(highC = 13.0, rainHours = 5..15, chance = 75),
            DaySpec(highC = 10.0, lowC = 5.0),
            DaySpec(highC = 21.0),
            DaySpec(highC = 19.0, windKmh = 20.0),
        ) + List(3) { DaySpec() },
        at,
    )

    /** Ten days of steady rain and a stiff breeze from [monday]: every day one for staying in. */
    fun wetWeek(): Forecast = synthetic(List(10) { DaySpec(highC = 11.0, lowC = 8.0, rainHours = 0..22, chance = 90, windKmh = 30.0, gustKmh = 55.0) }, monday)
}
