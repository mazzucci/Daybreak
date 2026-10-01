package app.daybreak.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLabelTest {
    private val style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp)

    @Test fun `fits unchanged when there's room`() {
        assertEquals(style, cappedLabelStyle(style, fontScale = 1f, availablePx = 200f, widestPx = 100f))
    }

    @Test fun `shrinks size, line height and letter spacing together`() {
        val s = cappedLabelStyle(style, fontScale = 1f, availablePx = 90f, widestPx = 100f)
        assertEquals(10.8f, s.fontSize.value, 0.001f)
        assertEquals(14.4f, s.lineHeight.value, 0.001f)
        assertEquals(0.45f, s.letterSpacing.value, 0.001f)
    }

    @Test fun `stops growing at 1_3x`() {
        val s = cappedLabelStyle(style, fontScale = 2f, availablePx = 1000f, widestPx = 100f)
        assertEquals(12f * 1.3f / 2f, s.fontSize.value, 0.001f)
    }

    @Test fun `never drawn below 9sp, nor zero or negative with no room`() {
        for (available in listOf(10f, 0f, -50f)) {
            val s = cappedLabelStyle(style, fontScale = 1f, availablePx = available, widestPx = 100f)
            assertEquals(MinLabelSize.value, s.fontSize.value, 0.001f)
            assertTrue(s.letterSpacing.value > 0f)
        }
        // At twice the font size, 4.5sp is drawn as large as 9sp is at the default.
        assertEquals(4.5f, cappedLabelStyle(style, fontScale = 2f, availablePx = 0f, widestPx = 100f).fontSize.value, 0.001f)
        // A base style already under the floor isn't made bigger.
        val small = TextStyle(fontSize = 8.sp)
        assertEquals(8f, cappedLabelStyle(small, fontScale = 1f, availablePx = 1f, widestPx = 100f).fontSize.value, 0.001f)
    }
}
