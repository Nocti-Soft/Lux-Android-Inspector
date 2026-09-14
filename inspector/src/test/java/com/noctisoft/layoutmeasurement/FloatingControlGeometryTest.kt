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

    @Test
    fun `zero safe area remains anchored without invalid coordinates`() {
        val zero = SafeArea(10, 20, 10, 20)
        val placement = FloatingControlGeometry.toNormalized(PixelPoint(200, 300), zero, size)

        assertEquals(FloatingPlacement(0f, 0f, DockSide.NONE), placement)
        assertEquals(PixelPoint(10, 20), FloatingControlGeometry.fromNormalized(placement, zero, size))
    }

    @Test
    fun `oversized control clamps to the safe area origin`() {
        val small = SafeArea(20, 40, 60, 70)
        val oversized = ControlSize(100, 100)

        assertEquals(PixelPoint(20, 40), FloatingControlGeometry.clamp(PixelPoint(50, 60), small, oversized))
        assertEquals(PixelPoint(20, 40), FloatingControlGeometry.fromNormalized(FloatingPlacement(1f, 1f), small, oversized))
    }
}
