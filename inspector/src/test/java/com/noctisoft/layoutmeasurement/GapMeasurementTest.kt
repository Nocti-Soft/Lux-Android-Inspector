package com.noctisoft.layoutmeasurement

import org.junit.Assert.*
import org.junit.Test

class GapMeasurementTest {
    private val outer = Bounds(10, 20, 310, 220)
    private val inner = Bounds(34, 52, 270, 172)

    @Test fun `contained bounds have four independent edge distances`() {
        assertEquals(GapMeasurement.Contained(outer, inner, EdgeDistances(24, 32, 40, 48)), Geometry.measureGap(outer, inner))
    }
    @Test fun `contained measurement is independent of selection order`() {
        assertEquals(Geometry.measureGap(outer, inner), Geometry.measureGap(inner, outer))
    }
    @Test fun `shared right and bottom edges remain a contained measurement`() {
        val child = Bounds(34, 52, 310, 220)
        assertEquals(GapMeasurement.Contained(outer, child, EdgeDistances(24, 32, 0, 0)), Geometry.measureGap(child, outer))
    }
    @Test fun `same width with vertical inset is containment rather than overlap`() {
        val child = Bounds(10, 52, 310, 172)
        assertEquals(GapMeasurement.Contained(outer, child, EdgeDistances(0, 32, 0, 48)), Geometry.measureGap(outer, child))
    }
    @Test fun `same height with horizontal inset is containment rather than overlap`() {
        val child = Bounds(34, 20, 270, 220)
        assertEquals(GapMeasurement.Contained(outer, child, EdgeDistances(24, 0, 40, 0)), Geometry.measureGap(outer, child))
    }
    @Test fun `identical positive bounds are classified separately`() {
        assertEquals(GapMeasurement.SameBounds, Geometry.measureGap(outer, outer.copy()))
    }
    @Test fun `intersecting but non contained rectangles remain overlapping`() {
        assertEquals(GapMeasurement.Overlapping, Geometry.measureGap(outer, Bounds(250, 150, 400, 260)))
    }
    @Test fun `edge touching has a genuine zero separation`() {
        assertEquals(GapMeasurement.Touching, Geometry.measureGap(outer, Bounds(310, 50, 410, 150)))
        assertEquals(GapMeasurement.Touching, Geometry.measureGap(outer, Bounds(30, 220, 150, 250)))
    }
    @Test fun `corner touching is not overlap`() {
        assertEquals(GapMeasurement.Touching, Geometry.measureGap(outer, Bounds(310, 220, 410, 300)))
    }
    @Test fun `separated rectangles retain horizontal and vertical distances`() {
        assertEquals(GapMeasurement.Separated(24, 0), Geometry.measureGap(outer, Bounds(334, 20, 500, 220)))
        assertEquals(GapMeasurement.Separated(0, 32), Geometry.measureGap(outer, Bounds(10, 252, 310, 300)))
        assertEquals(GapMeasurement.Separated(24, 32), Geometry.measureGap(outer, Bounds(334, 252, 500, 300)))
    }
    @Test fun `alignment on one axis is not touching when the other axis is separated`() {
        assertEquals(GapMeasurement.Separated(0, 32), Geometry.measureGap(outer, Bounds(310, 252, 400, 300)))
    }
    @Test fun `invalid rectangles do not masquerade as same bounds or containment`() {
        for (bad in listOf(Bounds(0, 0, 0, 1), Bounds(0, 2, 2, 1))) {
            assertEquals(GapMeasurement.Invalid, Geometry.measureGap(bad, bad))
            assertEquals(GapMeasurement.Invalid, Geometry.measureGap(outer, bad))
        }
    }
    @Test fun `window coordinate translation preserves the distances including negative origins`() {
        fun shift(bounds: Bounds) = Bounds(bounds.left - 700, bounds.top + 100, bounds.right - 700, bounds.bottom + 100)
        val result = Geometry.measureGap(shift(outer), shift(inner)) as GapMeasurement.Contained
        assertEquals(EdgeDistances(24, 32, 40, 48), result.edges)
    }
    @Test fun `all classifications are symmetric`() {
        val cases = listOf(inner, outer, Bounds(250, 150, 400, 260), Bounds(310, 50, 410, 150), Bounds(334, 252, 500, 300))
        cases.forEach { b -> assertEquals(Geometry.measureGap(outer, b), Geometry.measureGap(b, outer)) }
    }
    @Test fun `legacy external gap APIs keep their original containment semantics`() {
        assertEquals(0, Geometry.horizontalGap(outer, inner))
        assertEquals(0, Geometry.verticalGap(outer, inner))
    }
}
