package com.mazzucci.weather.data

import com.mazzucci.weather.TestData.london
import com.mazzucci.weather.TestData.sanFrancisco
import com.mazzucci.weather.TestData.tokyo
import com.mazzucci.weather.domain.AppSettings
import com.mazzucci.weather.domain.Place
import com.mazzucci.weather.domain.TempUnit
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

    @Test fun `memes are remembered per page for one day and one mood`() {
        val store = InMemoryStore()
        val repo = MemeRepository(store)
        val date = LocalDate.of(2026, 9, 28)
        val meme = Meme("Top", "Bottom", MemeMood.RAIN, NarrationSource.GEMMA)
        repo.put("geo:1", date, meme)
        assertEquals(meme, MemeRepository(store).get("geo:1", date, meme.mood))
        assertNull(repo.get("geo:1", date.plusDays(1), meme.mood))
        assertNull(repo.get("geo:1", date, MemeMood.SUN))
        assertNull(repo.get("geo:2", date, meme.mood))
        store.putString("meme:geo:3", "not json")
        assertNull(repo.get("geo:3", date, meme.mood))
    }
}
