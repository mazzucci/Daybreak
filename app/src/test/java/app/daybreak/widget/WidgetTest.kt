package app.daybreak.widget

import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import app.daybreak.TestData
import app.daybreak.data.InMemoryStore
import app.daybreak.data.WidgetStore
import app.daybreak.domain.TempUnit
import app.daybreak.domain.refreshedSnapshot
import app.daybreak.domain.widgetSnapshotOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTest {
    private val snapshot = widgetSnapshotOf(
        TestData.sanFrancisco, TestData.forecast(), TempUnit.F, "A mild afternoon.", nowMillis = 1_000,
    )

    @Test fun `snapshot carries what the widget shows`() {
        assertEquals("San Francisco", snapshot.placeName)
        assertEquals(21.4, snapshot.tempC, 0.001)
        assertEquals(60, snapshot.precipChance)
        assertFalse(snapshot.night)
        assertEquals(TestData.now, snapshot.updatedAt)
    }

    @Test fun `store round-trips and ignores junk`() {
        val kv = InMemoryStore()
        val store = WidgetStore(kv)
        assertNull(store.load())
        store.save(snapshot)
        assertEquals(snapshot, WidgetStore(kv).load())
        kv.putString("widget_snapshot", "{oops")
        assertNull(store.load())
    }

    @Test fun `update is atomic and can decline`() {
        val store = WidgetStore(InMemoryStore())
        store.save(snapshot)
        assertNull(store.update { null }) // declined: unchanged
        assertEquals(snapshot, store.load())
        val moved = store.update { it!!.copy(placeName = "Elsewhere") }
        assertEquals("Elsewhere", store.load()!!.placeName)
        assertEquals(moved, store.load())
        store.clear()
        assertNull(store.load())
    }

    @Test fun `a background refresh brings new numbers and summary for the same place`() {
        val refreshed = refreshedSnapshot(snapshot, TestData.rainyNight(), "Template.", nowMillis = 5_000)
        assertEquals("Template.", refreshed.summary)
        assertEquals(9.8, refreshed.tempC, 0.001)
        assertTrue(refreshed.night)
        assertEquals(5_000, refreshed.writtenAtMillis)
        assertEquals(snapshot.placeName, refreshed.placeName)
    }

    @Test fun `4x2 shows place, both units, today's numbers and the summary`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSize.Full)
        provideComposable { WidgetContent(snapshot, icon = null) }
        onNode(hasText("San Francisco")).assertExists()
        onNode(hasText("71°F")).assertExists()
        onNode(hasText("21°C")).assertExists()
        onNode(hasText("A mild afternoon.")).assertExists()
        onNode(hasText("High 74° · Low 56° · Rain 60%")).assertExists()
        onNode(hasText("Gemma")).assertDoesNotExist()
        onNode(hasText("Updated")).assertDoesNotExist()
    }

    @Test fun `4x1 drops the summary but keeps the place and numbers`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSize.Wide)
        provideComposable { WidgetContent(snapshot, icon = null) }
        onNode(hasText("71°F")).assertExists()
        onNode(hasText("San Francisco")).assertExists()
        onNode(hasText("↑74° ↓56° · Rain 60%")).assertExists()
        onNode(hasText("A mild afternoon.")).assertDoesNotExist()
        onNode(hasText("21°C")).assertDoesNotExist()
    }

    @Test fun `2x2 shows the temperature and place`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSize.Square)
        provideComposable { WidgetContent(snapshot, icon = null) }
        onNode(hasText("71°F")).assertExists()
        onNode(hasText("San Francisco")).assertExists()
        onNode(hasText("High 74°")).assertDoesNotExist()
        onNode(hasText("A mild afternoon.")).assertDoesNotExist()
    }

    @Test fun `2x1 shows only the temperature`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSize.Compact)
        provideComposable { WidgetContent(snapshot, icon = null) }
        onNode(hasText("71°F")).assertExists()
        onNode(hasText("San Francisco")).assertDoesNotExist()
        onNode(hasText("High 74°")).assertDoesNotExist()
    }

    @Test fun `the whole widget is one tappable item with one description`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSize.Full)
        provideComposable { WidgetContent(snapshot, icon = null) }
        onNode(hasContentDescription("San Francisco, 71°F (21°C), Partly cloudy. High 74°, low 56°, 60% chance of rain. A mild afternoon."))
            .assertExists()
        onNode(hasContentDescription("Gemma")).assertDoesNotExist()
        onNode(hasClickAction()).assertExists()
    }

    @Test fun `widget without a snapshot asks to open the app`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSize.Full)
        provideComposable { WidgetContent(null, icon = null) }
        onNode(hasText("Weather")).assertExists()
        onNode(hasText("Open the app to choose a place.")).assertExists()
    }

    @Test fun `a small widget without a snapshot still asks to open the app`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(WidgetSize.Compact)
        provideComposable { WidgetContent(null, icon = null) }
        onNode(hasText("Open the app to choose a place.")).assertExists()
        onNode(hasText("Weather")).assertDoesNotExist()
    }
}
