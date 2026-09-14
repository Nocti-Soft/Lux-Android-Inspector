package com.noctisoft.layoutmeasurement

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingControlGeometryTest {
    private val safe = SafeArea(left = 20, top = 40, right = 980, bottom = 1880)
    private val size = ControlSize(width = 100, height = 100)

    @Test
    fun `clamp keeps full control inside safe area`() {
        assertEquals(PixelPoint(20, 40), FloatingControlGeometry.clamp(PixelPoint(-50, -20), safe, size))
        assertEquals(PixelPoint(880, 1780), FloatingControlGeometry.clamp(PixelPoint(950, 1900), safe, size))
    }

    @Test
    fun `normalized position round trips across safe area`() {
        val point = PixelPoint(450, 900)
        val normalized = FloatingControlGeometry.toNormalized(point, safe, size)
        assertEquals(point, FloatingControlGeometry.fromNormalized(normalized, safe, size))
    }

    @Test
    fun `near left and right edges choose dock side`() {
        assertEquals(
            DockSide.LEFT,
            FloatingControlGeometry.chooseDockSide(PixelPoint(25, 500), safe, size, thresholdPx = 80),
        )
        assertEquals(
            DockSide.RIGHT,
            FloatingControlGeometry.chooseDockSide(PixelPoint(870, 500), safe, size, thresholdPx = 80),
        )
        assertEquals(
            DockSide.NONE,
            FloatingControlGeometry.chooseDockSide(PixelPoint(450, 500), safe, size, thresholdPx = 80),
        )
    }

    @Test
    fun `docked position leaves half button visible`() {
        assertEquals(
            PixelPoint(-30, 500),
            FloatingControlGeometry.dockedPosition(DockSide.LEFT, 500, safe, size),
        )
        assertEquals(
            PixelPoint(930, 500),
            FloatingControlGeometry.dockedPosition(DockSide.RIGHT, 500, safe, size),
        )
    }
}
