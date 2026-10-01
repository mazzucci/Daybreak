package app.daybreak.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoxBlurTest {
    @Test fun `a flat colour stays flat, and a bright spot spreads out`() {
        val flat = IntArray(25) { 0xFF336699.toInt() }
        boxBlur(flat, 5, 5, radius = 2)
        assertTrue(flat.all { it == 0xFF336699.toInt() })
        val spot = IntArray(25) { 0xFF000000.toInt() }.also { it[12] = 0xFFFFFFFF.toInt() }
        boxBlur(spot, 5, 5, radius = 1)
        // The spot's 255 spread over its 3 x 3 neighbourhood: 255 / 3 / 3 = 28 (0x1C) each.
        assertEquals(0xFF1C1C1C.toInt(), spot[12])
        assertEquals(spot[12], spot[6])
        assertEquals(0xFF000000.toInt(), spot[0])
    }
}
