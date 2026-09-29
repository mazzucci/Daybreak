package com.mazzucci.weather.data

import com.mazzucci.weather.TestData.london
import com.mazzucci.weather.TestData.sanFrancisco
import com.mazzucci.weather.TestData.tokyo
import com.mazzucci.weather.domain.Activity
import com.mazzucci.weather.domain.AppSettings
import com.mazzucci.weather.domain.Place
import com.mazzucci.weather.domain.TempUnit
import com.mazzucci.weather.domain.Tone
import com.mazzucci.weather.narration.Meme
import com.mazzucci.weather.narration.MemeMood
import com.mazzucci.weather.narration.NarrationSource
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

    @Test fun `the note is kept in the private store only`() {
        val store = InMemoryStore()
        val private = InMemoryStore()
        SettingsRepository(store, privateStore = private).update { it.copy(aboutMe = "I cycle") }
        assertNull(store.getString("about_me"))
        assertEquals("I cycle", private.getString("about_me"))
        assertEquals("I cycle", SettingsRepository(store, privateStore = private).settings.value.aboutMe)
    }

    @Test fun `activity is saved, including off`() {
        val store = InMemoryStore()
        assertEquals(Activity.CYCLING, SettingsRepository(store).settings.value.activity)
        SettingsRepository(store).update { it.copy(activity = null) }
        assertNull(SettingsRepository(store).settings.value.activity)
        SettingsRepository(store).update { it.copy(activity = Activity.RUNNING) }
        assertEquals(Activity.RUNNING, SettingsRepository(store).settings.value.activity)
        store.putString("activity", "SKIING")
        assertEquals(Activity.CYCLING, SettingsRepository(store).settings.value.activity)
    }

    @Test fun `voice and note are saved, and an unknown voice falls back`() {
        val store = InMemoryStore()
        SettingsRepository(store).update { it.copy(tone = Tone.PIRATE, aboutMe = "I cycle") }
        val loaded = SettingsRepository(store).settings.value
        assertEquals(Tone.PIRATE, loaded.tone)
        assertEquals("I cycle", loaded.aboutMe)
        store.putString("tone", "OPERA")
        assertEquals(Tone.FRIENDLY, SettingsRepository(store).settings.value.tone)
    }
}
