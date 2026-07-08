package dev.pinij.inspector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeometryTest {

    private fun node(l: Int, t: Int, r: Int, b: Int, label: String = "n") =
        CapturedNode(label, Bounds(l, t, r, b), Source.XML)

    @Test fun `horizontal gap between disjoint rects`() {
        val a = Bounds(0, 0, 100, 50)
        val b = Bounds(140, 0, 200, 50)
        assertEquals(40, Geometry.horizontalGap(a, b))
        assertEquals(40, Geometry.horizontalGap(b, a)) // order-independent
    }

    @Test fun `horizontal gap is zero when x-intervals overlap`() {
        val a = Bounds(0, 0, 100, 50)
        val b = Bounds(90, 60, 200, 100)
        assertEquals(0, Geometry.horizontalGap(a, b))
    }

    @Test fun `vertical gap between disjoint rects`() {
        val a = Bounds(0, 0, 100, 50)
        val b = Bounds(0, 80, 100, 120)
        assertEquals(30, Geometry.verticalGap(a, b))
        assertEquals(30, Geometry.verticalGap(b, a))
    }

    @Test fun `vertical gap is zero when y-intervals overlap`() {
        val a = Bounds(0, 0, 100, 50)
        val b = Bounds(200, 40, 300, 90)
        assertEquals(0, Geometry.verticalGap(a, b))
    }

    @Test fun `nested rects have zero gaps`() {
        val outer = Bounds(0, 0, 200, 200)
        val inner = Bounds(50, 50, 100, 100)
        assertEquals(0, Geometry.horizontalGap(outer, inner))
        assertEquals(0, Geometry.verticalGap(outer, inner))
    }

    @Test fun `pxToDp divides by density`() {
        assertEquals(120f, Geometry.pxToDp(360, 3f), 0.001f)
        assertEquals(360f, Geometry.pxToDp(360, 1f), 0.001f)
    }

    @Test fun `formatPx shows dp and px`() {
        assertEquals("120.0dp (360px)", Geometry.formatPx(360, 3f))
    }

    @Test fun `pickAt returns smallest containing node`() {
        val outer = node(0, 0, 200, 200, "outer")
        val inner = node(50, 50, 100, 100, "inner")
        assertEquals(inner, Geometry.pickAt(listOf(outer, inner), 60, 60))
    }

    @Test fun `pickAt returns null when nothing contains point`() {
        assertNull(Geometry.pickAt(listOf(node(0, 0, 10, 10)), 50, 50))
    }

    @Test fun `bounds contains is inclusive left-top exclusive right-bottom`() {
        val b = Bounds(10, 10, 20, 20)
        assertEquals(true, b.contains(10, 10))
        assertEquals(false, b.contains(20, 20))
    }
}
