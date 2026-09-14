package com.noctisoft.layoutmeasurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InspectorPlacementCodecTest {
    @Test
    fun `codec round trips normalized placement and dock side`() {
        val source = FloatingPlacement(0.25f, 0.75f, DockSide.RIGHT)
        assertEquals(source, FloatingPlacementCodec.decode(FloatingPlacementCodec.encode(source)))
    }

    @Test
    fun `codec clamps malformed fractions and rejects invalid records`() {
        assertEquals(
            FloatingPlacement(0f, 1f, DockSide.LEFT),
            FloatingPlacementCodec.decode("-2.0|4.0|LEFT"),
        )
        assertNull(FloatingPlacementCodec.decode("not-a-placement"))
        assertNull(FloatingPlacementCodec.decode("0.2|0.4|SIDEWAYS"))
    }
}
