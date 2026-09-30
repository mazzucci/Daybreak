package app.daybreak.ui

import app.daybreak.TestData
import app.daybreak.TestData.london
import app.daybreak.TestData.sanFrancisco
import app.daybreak.domain.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTest {
    private val loaded = PageContent.Loaded(TestData.forecast(), app.daybreak.narration.Narration("", app.daybreak.narration.NarrationSource.TEMPLATE))

    @Test fun `greetings follow the time of day`() {
        assertEquals(listOf("Good night", "Good morning", "Good morning", "Good afternoon", "Good evening", "Good night"), listOf(4, 5, 11, 12, 18, 22).map(::greeting))
    }

    @Test fun `home follows the first page, past one waiting for location permission, but not past an error`() {
        val here = PageUi(Place.CURRENT_LOCATION_ID, null, PageContent.NeedsPermission)
        val sf = PageUi(sanFrancisco.id, sanFrancisco, loaded)
        assertEquals(-1, glancePageIndex(emptyList()))
        assertEquals(1, glancePageIndex(listOf(here, sf)))
        assertEquals(0, glancePageIndex(listOf(here)))
        // A failed refresh keeps Home on its place, showing the error, rather than switching city.
        assertEquals(0, glancePageIndex(listOf(PageUi(sanFrancisco.id, sanFrancisco, PageContent.Failed("offline")), PageUi(london.id, london, loaded))))
    }
}
