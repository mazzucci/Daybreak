package app.daybreak.data

import app.daybreak.TestData.london
import app.daybreak.TestData.sanFrancisco
import app.daybreak.TestData.tokyo
import app.daybreak.domain.AppSettings
import app.daybreak.domain.Clock
import app.daybreak.domain.PersonalDate
import app.daybreak.domain.Place
import app.daybreak.domain.TempUnit
import app.daybreak.narration.Meme
import app.daybreak.narration.MemeMood
import app.daybreak.narration.NarrationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SavedPlacesRepositoryTest {
    private val store = InMemoryStore()
    private val repo = SavedPlacesRepository(store)

    private fun names() = repo.places.value.map { it.name }

    @Test fun `starts empty`() = assertTrue(repo.places.value.isEmpty())

    @Test fun `adds places in order and ignores duplicates`() {
        assertTrue(repo.add(sanFrancisco))
        assertTrue(repo.add(london))
        assertFalse(repo.add(sanFrancisco))
        assertEquals(listOf("San Francisco", "London"), names())
    }

    @Test fun `removes by id`() {
        repo.add(sanFrancisco); repo.add(london)
        repo.remove(sanFrancisco.id)
        assertEquals(listOf("London"), names())
    }

    @Test fun `moves places`() {
        repo.add(sanFrancisco); repo.add(london); repo.add(tokyo)
        repo.move(0, 2)
        assertEquals(listOf("London", "Tokyo", "San Francisco"), names())
        repo.move(2, 1)
        assertEquals(listOf("London", "San Francisco", "Tokyo"), names())
    }

    @Test fun `ignores out-of-range moves`() {
        repo.add(sanFrancisco); repo.add(london)
        repo.move(0, 5)
        repo.move(-1, 0)
        assertEquals(listOf("San Francisco", "London"), names())
    }

    @Test fun `persists all fields across instances`() {
        repo.add(sanFrancisco)
        repo.add(Place("geo:9", "Atlantis", latitude = 1.0, longitude = 2.0))
        repo.move(1, 0)
        val reloaded = SavedPlacesRepository(store).places.value
        assertEquals(listOf(Place("geo:9", "Atlantis", latitude = 1.0, longitude = 2.0), sanFrancisco), reloaded)
    }

    @Test fun `migrates ids saved before the geo prefix`() {
        val old = """[{"id":"4409896","name":"Springfield","region":"Missouri","lat":37.2,"lon":-93.3}]"""
        val store = InMemoryStore(mapOf("saved_places" to old))
        assertEquals("geo:4409896", SavedPlacesRepository(store).places.value.single().id)
        assertTrue(store.getString("saved_places")!!.contains("\"geo:4409896\""))
    }

    @Test fun `corrupt stored data loads as empty`() {
        val repo = SavedPlacesRepository(InMemoryStore(mapOf("saved_places" to "not json")))
        assertTrue(repo.places.value.isEmpty())
    }

    @Test fun `country codes are saved, and older places get one from their country name`() {
        val store = InMemoryStore(mapOf("saved_places" to """[{"id":"geo:1","name":"London","country":"United Kingdom","lat":51.5,"lon":-0.1}]"""))
        assertEquals("GB", SavedPlacesRepository(store).places.value.single().countryCode)
        assertTrue(store.getString("saved_places")!!.contains("\"cc\":\"GB\"")) // persisted once
        SavedPlacesRepository(store).add(tokyo.copy(countryCode = "JP"))
        assertEquals(listOf("GB", "JP"), SavedPlacesRepository(store).places.value.map { it.countryCode })
    }
}

class SettingsRepositoryTest {
    @Test fun `uses defaults until changed, then persists`() {
        val store = InMemoryStore()
        val repo = SettingsRepository(store, AppSettings(primaryUnit = TempUnit.C))
        assertEquals(AppSettings(primaryUnit = TempUnit.C), repo.settings.value)

        repo.update { it.copy(primaryUnit = TempUnit.F, useCurrentLocation = false, gemmaEnabled = false) }

        val reloaded = SettingsRepository(store, AppSettings(primaryUnit = TempUnit.C)).settings.value
        assertEquals(AppSettings(TempUnit.F, useCurrentLocation = false, gemmaEnabled = false), reloaded)
    }

    @Test fun `remembers habits on Home`() {
        val store = InMemoryStore()
        SettingsRepository(store).update { it.copy(habitsOnHome = false) }
        assertFalse(SettingsRepository(store).settings.value.habitsOnHome)
        assertTrue(SettingsRepository(InMemoryStore()).settings.value.habitsOnHome)
    }

    @Test fun `remembers On this day on Home`() {
        val store = InMemoryStore()
        assertTrue(SettingsRepository(store).settings.value.onThisDayEnabled)
        SettingsRepository(store).update { it.copy(onThisDayEnabled = false) }
        assertFalse(SettingsRepository(store).settings.value.onThisDayEnabled)
        SettingsRepository(store).update { it.copy(onThisDayEnabled = true) }
        assertTrue(SettingsRepository(store).settings.value.onThisDayEnabled)
    }

    @Test fun `bad stored values fall back to defaults`() {
        val store = InMemoryStore(mapOf("primary_unit" to "K", "use_current_location" to "maybe"))
        assertEquals(AppSettings(), SettingsRepository(store).settings.value)
    }

    @Test fun `memes are remembered per page, day, place and mood`() {
        val store = InMemoryStore()
        val repo = MemeRepository(store)
        val date = LocalDate.of(2026, 9, 28)
        val rain = SavedMeme(Meme("Top", "Bottom", MemeMood.RAIN, NarrationSource.GEMMA), gemmaTried = true)
        val sun = SavedMeme(Meme("Sun", "Fun", MemeMood.SUN, NarrationSource.TEMPLATE), gemmaTried = true)
        repo.put("geo:1", date, "SF", rain)
        repo.put("geo:1", date, "SF", sun) // a mood flip keeps both
        assertEquals(rain, MemeRepository(store).get("geo:1", date, "SF", MemeMood.RAIN))
        assertEquals(sun, repo.get("geo:1", date, "SF", MemeMood.SUN))
        assertNull(repo.get("geo:1", date.plusDays(1), "SF", MemeMood.RAIN))
        assertNull(repo.get("geo:1", date, "Seattle", MemeMood.RAIN))
        assertNull(repo.get("geo:1", date, "SF", MemeMood.FOG))
        assertNull(repo.get("geo:2", date, "SF", MemeMood.RAIN))

        repo.put("geo:1", date.plusDays(1), "SF", rain) // a new day starts a fresh entry
        assertNull(repo.get("geo:1", date, "SF", MemeMood.SUN))

        repo.remove("geo:1")
        assertNull(store.getString("meme:geo:1"))
        store.putString("meme:geo:3", "not json")
        assertNull(repo.get("geo:3", date, "SF", MemeMood.RAIN))
    }

    @Test fun `settings of removed features are cleared once, and your dates are kept`() {
        val store = InMemoryStore(mapOf("tone" to "PIRATE", "commute" to "on-8-17", "primary_unit" to "C"))
        val trip = PersonalDate(LocalDate.of(2026, 10, 12), name = "Lisbon trip")
        // Your dates saved by an earlier version, alongside the old keys, all in the private store the cleanup reads.
        val scratch = InMemoryStore()
        SettingsRepository(InMemoryStore(), privateStore = scratch).update { it.copy(personalDates = listOf(trip)) }
        val private = InMemoryStore(
            mapOf("about_me" to "I cycle", "commute_home" to "{}", "commute_office" to "{}", "personal_dates" to scratch.getString("personal_dates")!!),
        )
        val repo = SettingsRepository(store, privateStore = private)
        listOf("tone", "commute").forEach { assertNull(it, store.getString(it)) }
        listOf("about_me", "commute_home", "commute_office").forEach { assertNull(it, private.getString(it)) }
        assertEquals(TempUnit.C, repo.settings.value.primaryUnit)
        assertEquals(listOf(trip), repo.settings.value.personalDates)
    }

    @Test fun `your dates are kept in the private store, sorted, and a bad entry doesn't lose the rest`() {
        val store = InMemoryStore()
        val private = InMemoryStore()
        val trip = PersonalDate(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16), "Lisbon trip", dayOff = true)
        val birthday = PersonalDate(LocalDate.of(2026, 10, 2), name = "Mum's birthday", yearly = true)
        SettingsRepository(store, privateStore = private).update { it.copy(personalDates = listOf(trip, birthday)) }
        assertNull(store.getString("personal_dates"))
        assertEquals(listOf(birthday, trip), SettingsRepository(store, privateStore = private).settings.value.personalDates)
        private.putString("personal_dates", private.getString("personal_dates")!!.replace("2026-10-02", "not a date"))
        assertEquals(listOf(trip), SettingsRepository(store, privateStore = private).settings.value.personalDates)
        SettingsRepository(store, privateStore = private).update { it.copy(personalDates = emptyList()) }
        assertNull(private.getString("personal_dates"))
    }

    @Test fun `the removed activity setting is cleared once`() {
        val store = InMemoryStore()
        store.putString("activity", "RUNNING")
        store.putString("primary_unit", "C")
        val repo = SettingsRepository(store)
        assertNull(store.getString("activity"))
        assertEquals(TempUnit.C, repo.settings.value.primaryUnit)
        repo.update { it.copy(skyEnabled = false) }
        assertNull(store.getString("activity")) // not written back either
    }

    @Test fun `clocks are saved in order, moved, removed, and a bad entry doesn't lose the rest`() {
        val store = InMemoryStore()
        val bucharest = Clock("geo:683506", "Bucharest", "Bucharest, Romania", "Europe/Bucharest")
        val tokyo = Clock("geo:1850147", "Tokyo", null, "Asia/Tokyo")
        ClocksRepository(store).apply { add(bucharest); add(tokyo); assertFalse(add(tokyo)) }
        assertEquals(listOf(bucharest, tokyo), ClocksRepository(store).clocks.value)
        ClocksRepository(store).move(1, 0)
        assertEquals(listOf(tokyo, bucharest), ClocksRepository(store).clocks.value)
        store.putString("clocks", store.getString("clocks")!!.replace("\"tz\":\"Asia/Tokyo\"", "\"nope\":1"))
        assertEquals(listOf(bucharest), ClocksRepository(store).clocks.value)
        ClocksRepository(store).remove(bucharest.id)
        assertTrue(ClocksRepository(store).clocks.value.isEmpty())
    }
}
