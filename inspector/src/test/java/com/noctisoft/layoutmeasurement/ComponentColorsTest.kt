package com.noctisoft.layoutmeasurement

import org.junit.Assert.*
import org.junit.Test

class ComponentColorsTest {
    @Test fun `format uses uppercase Android ARGB and preserves leading zero alpha`() {
        assertEquals("#80123456", ColorCode.format(0x80123456.toInt()))
        assertEquals("#00123456", ColorCode.format(0x00123456))
        assertEquals("#FFFFFFFF", ColorCode.format(-1))
    }

    @Test fun `transparent color is not missing or unknown`() {
        assertNotEquals(ColorValue.None, ColorValue.Solid(0))
        assertNotEquals(ColorValue.Unavailable("Unknown"), ColorValue.Solid(0))
        assertEquals("#00000000", ColorValue.Solid(0).copyText())
        assertNull(ColorValue.None.copyText())
        assertNull(ColorValue.Unavailable("Custom drawing").copyText())
    }

    @Test fun `multiple values have deterministic distinct codes`() {
        val value = ColorValue.fromColors(listOf(0xFF112233.toInt(), 0xFF445566.toInt(), 0xFF112233.toInt()))
        assertEquals("#FF112233\n#FF445566", value.copyText())
        assertEquals(ColorValue.Solid(7), ColorValue.fromColors(listOf(7, 7)))
    }
}
