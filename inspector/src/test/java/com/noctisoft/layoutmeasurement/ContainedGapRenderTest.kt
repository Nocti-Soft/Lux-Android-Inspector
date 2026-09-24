package com.noctisoft.layoutmeasurement

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "xxhdpi", application = Application::class)
class ContainedGapRenderTest {
    @After fun reset() = InspectorController.stopInspection()

    @Test fun `contained views display four edge distances instead of an overlap label`() {
        val labels = render(Bounds(90, 180, 900, 660), Bounds(162, 276, 780, 516)).labels.map { it.text }
        assertTrue(labels.toString(), labels.containsAll(listOf(
            "Left: 24.0dp (72px)", "Top: 32.0dp (96px)",
            "Right: 40.0dp (120px)", "Bottom: 48.0dp (144px)",
        )))
        assertFalse(labels.any { it.contains("overlapping", ignoreCase = true) })
    }

    @Test fun `reversing inner and outer selection produces identical pixels`() {
        val outer = Bounds(90, 180, 900, 660)
        val inner = Bounds(162, 276, 780, 516)
        assertArrayEquals(pixels(render(outer, inner).bitmap), pixels(render(inner, outer).bitmap))
    }

    @Test fun `shared edges retain zero values without losing other distances`() {
        val labels = render(Bounds(90, 180, 900, 660), Bounds(90, 276, 900, 660)).labels.map { it.text }
        assertTrue(labels.toString(), labels.containsAll(listOf(
            "Left: 0.0dp (0px)", "Top: 32.0dp (96px)",
            "Right: 0.0dp (0px)", "Bottom: 0.0dp (0px)",
        )))
    }

    @Test fun `same bounds and edge touching are not reported as overlap`() {
        val bounds = Bounds(90, 180, 390, 360)
        assertTrue(render(bounds, bounds).labels.any { it.text.startsWith("Same bounds") })
        assertTrue(render(bounds, Bounds(390, 180, 690, 360)).labels.any { it.text.startsWith("Touching") })
    }

    @Test fun `partial overlaps still use the existing zero gap label`() {
        val labels = render(Bounds(90, 180, 390, 360), Bounds(300, 240, 600, 420)).labels.map { it.text }
        assertEquals(listOf("overlapping (gap 0)"), labels)
    }

    @Test fun `all four labels fit the viewport without colliding for a narrow inner view`() {
        val result = render(Bounds(0, 0, 600, 700), Bounds(3, 600, 597, 603), width = 600, height = 720)
        val edges = result.labels.filter { it.text.substringBefore(":") in listOf("Left", "Top", "Right", "Bottom") }
        assertEquals(4, edges.size)
        edges.forEach {
            assertTrue("Left clipping: $it", it.x >= 0f)
            assertTrue("Right clipping: $it", it.x + it.paint.measureText(it.text) <= 600.5f)
            assertTrue("Top clipping: $it", it.y + it.paint.fontMetrics.top >= 0f)
            assertTrue("Bottom clipping: $it", it.y + it.paint.fontMetrics.bottom <= 720f)
        }
        edges.sortedBy { it.y }.zipWithNext().forEach { (above, below) ->
            assertTrue("Edge labels must not overlap", above.y + above.paint.fontMetrics.bottom <= below.y + below.paint.fontMetrics.top)
        }
    }

    @Test fun `edge measurement labels are the last foreground drawing operations`() {
        val result = render(Bounds(0, 0, 600, 700), Bounds(3, 600, 597, 603), width = 600, height = 720)
        assertEquals(4, result.labels.count { it.text.contains(":") })
        val lastOutline = result.operations.indexOfLast { it == "outline" }
        assertTrue("Fixture should render a selection outline", lastOutline >= 0)
        assertTrue(result.operations.withIndex().filter { it.value.startsWith("text:") }.all { it.index > lastOutline })
    }

    private data class Label(val text: String, val x: Float, val y: Float, val paint: Paint)
    private class RecordingCanvas(val bitmap: Bitmap) : Canvas(bitmap) {
        val labels = mutableListOf<Label>()
        val operations = mutableListOf<String>()
        override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
            labels += Label(text, x, y, Paint(paint))
            operations += "text:$text"
            super.drawText(text, x, y, paint)
        }
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            if (paint.color == android.graphics.Color.rgb(255, 0, 168)) operations += "outline"
            super.drawRect(left, top, right, bottom, paint)
        }
        override fun drawRect(rect: android.graphics.RectF, paint: Paint) {
            if (paint.color == android.graphics.Color.rgb(255, 0, 168)) operations += "outline"
            super.drawRect(rect, paint)
        }
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
            if (paint.color == android.graphics.Color.rgb(255, 0, 168)) operations += "outline"
            super.drawLine(startX, startY, stopX, stopY, paint)
        }
    }
    private fun render(a: Bounds, b: Bounds, width: Int = 1080, height: Int = 1920): RecordingCanvas {
        val view = MeasureCanvas(RuntimeEnvironment.getApplication())
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        for ((field, node) in listOf("selectedA" to CapturedNode("A", a, Source.XML), "selectedB" to CapturedNode("B", b, Source.XML))) {
            view.javaClass.getDeclaredField(field).apply { isAccessible = true }.set(view, node)
        }
        InspectorController.selectMode(MeasureMode.GAP)
        return RecordingCanvas(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)).also { view.draw(it) }
    }
    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }
}
