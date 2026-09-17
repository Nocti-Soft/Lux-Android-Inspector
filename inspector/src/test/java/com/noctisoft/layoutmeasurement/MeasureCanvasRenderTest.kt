package com.noctisoft.layoutmeasurement

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "xxhdpi", application = Application::class)
class MeasureCanvasRenderTest {
    @After
    fun resetInspector() {
        InspectorController.stopInspection()
    }

    @Test
    fun `selected bounds use a visible magenta core and white halo on every side at high density`() {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }

        SelectionOutlineRenderer.draw(Canvas(bitmap), Bounds(20, 20, 100, 100), 3f)

        listOf(bitmap.getPixel(20, 60), bitmap.getPixel(99, 60), bitmap.getPixel(60, 20), bitmap.getPixel(60, 99)).forEach {
            assertEquals(Color.rgb(255, 0, 168), it)
        }
        listOf(bitmap.getPixel(15, 60), bitmap.getPixel(105, 60), bitmap.getPixel(60, 15), bitmap.getPixel(60, 105)).forEach {
            assertEquals(Color.WHITE, it)
        }
    }

    @Test
    fun `magenta core remains visible on a light background`() {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

        SelectionOutlineRenderer.draw(Canvas(bitmap), Bounds(20, 20, 100, 100), 3f)

        listOf(bitmap.getPixel(20, 60), bitmap.getPixel(99, 60), bitmap.getPixel(60, 20), bitmap.getPixel(60, 99)).forEach {
            assertEquals(Color.rgb(255, 0, 168), it)
        }
    }

    @Test
    fun `selection border remains drawable at every viewport edge`() {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }

        SelectionOutlineRenderer.draw(Canvas(bitmap), Bounds(0, 0, 120, 120), 3f)

        listOf(bitmap.getPixel(1, 60), bitmap.getPixel(118, 60), bitmap.getPixel(60, 1), bitmap.getPixel(60, 118)).forEach {
            assertEquals(Color.rgb(255, 0, 168), it)
        }
        listOf(bitmap.getPixel(5, 60), bitmap.getPixel(114, 60), bitmap.getPixel(60, 5), bitmap.getPixel(60, 114)).forEach {
            assertEquals(Color.WHITE, it)
        }
    }

    @Test
    fun `partially offscreen bounds do not invent an outline at the viewport edge`() {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }

        SelectionOutlineRenderer.draw(Canvas(bitmap), Bounds(-20, 20, 80, 100), 3f)

        assertEquals(Color.BLACK, bitmap.getPixel(0, 60))
        assertEquals(Color.rgb(255, 0, 168), bitmap.getPixel(79, 60))
        assertEquals(Color.WHITE, bitmap.getPixel(85, 60))
    }

    @Test
    fun `invalid and fully offscreen bounds do not invent visible edges`() {
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        SelectionOutlineRenderer.draw(canvas, Bounds(30, 30, 30, 50), 3f)
        SelectionOutlineRenderer.draw(canvas, Bounds(-30, 20, -10, 80), 3f)

        assertEquals(0, bitmap.getPixel(0, 40))
        assertEquals(0, bitmap.getPixel(30, 40))
    }

    @Test
    fun `Gap borders remain visible outside the label area`() {
        val view = MeasureCanvas(RuntimeEnvironment.getApplication())
        layout(view, 120, 120)
        setField(view, "selectedA", CapturedNode("A", Bounds(20, 20, 60, 60), Source.XML))
        setField(view, "selectedB", CapturedNode("B", Bounds(65, 20, 105, 60), Source.XML))
        InspectorController.selectMode(MeasureMode.GAP)
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)

        view.draw(Canvas(bitmap))

        assertEquals(Color.rgb(255, 0, 168), bitmap.getPixel(60, 40))
    }

    @Test
    fun `horizontal gap label is drawn above the selection border and halo`() {
        assertGapLabelAboveOutlines(
            a = Bounds(80, 100, 280, 300),
            b = Bounds(292, 100, 492, 300),
            gapPx = 12,
            labelX = 286f,
            labelY = 192f,
        )
    }

    @Test
    fun `vertical gap label is drawn above the selection border and halo`() {
        assertGapLabelAboveOutlines(
            a = Bounds(80, 80, 360, 200),
            b = Bounds(80, 212, 360, 332),
            gapPx = 12,
            labelX = 228f,
            labelY = 206f,
        )
    }

    @Test
    fun `zero gap label is drawn above overlapping selection borders and halos`() {
        assertGapLabelAboveOutlines(
            a = Bounds(80, 20, 320, 180),
            b = Bounds(120, 30, 360, 190),
            gapPx = 0,
            labelX = 80f,
            labelY = 40f,
        )
    }

    private fun assertGapLabelAboveOutlines(
        a: Bounds,
        b: Bounds,
        gapPx: Int,
        labelX: Float,
        labelY: Float,
    ) {
        val width = 720
        val height = 480
        val view = MeasureCanvas(RuntimeEnvironment.getApplication())
        layout(view, width, height)
        setField(view, "selectedA", CapturedNode("A", a, Source.XML))
        setField(view, "selectedB", CapturedNode("B", b, Source.XML))
        InspectorController.selectMode(MeasureMode.GAP)
        val density = view.resources.displayMetrics.density
        val label = if (gapPx == 0) "overlapping (gap 0)" else Geometry.formatPx(gapPx, density)
        val actual = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(actual))

        val outlines = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        SelectionOutlineRenderer.draw(Canvas(outlines), listOf(a, b), density)
        val expected = requireNotNull(outlines.copy(Bitmap.Config.ARGB_8888, true))
        val labelOnly = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Use the real label renderer, but explicitly compose it AFTER the outlines.
        // This isolates layer order without depending on device-specific glyph rasterization.
        val drawLabel = MeasureCanvas::class.java.getDeclaredMethod(
            "drawLabel", Canvas::class.java, String::class.java,
            Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
        ).apply { isAccessible = true }
        drawLabel.invoke(view, Canvas(expected), label, labelX, labelY)
        drawLabel.invoke(view, Canvas(labelOnly), label, labelX, labelY)

        val outlinePixels = pixels(outlines)
        val labelPixels = pixels(labelOnly)
        val expectedPixels = pixels(expected)
        val actualPixels = pixels(actual)
        assertTrue(
            "Fixture must place the label over a magenta selection border",
            labelPixels.indices.any {
                Color.alpha(labelPixels[it]) > 0 && outlinePixels[it] == Color.rgb(255, 0, 168)
            },
        )
        labelPixels.indices.filter { Color.alpha(labelPixels[it]) > 0 }.forEach { pixel ->
            assertEquals(
                "Gap label must be topmost at (${pixel % width}, ${pixel / width})",
                expectedPixels[pixel], actualPixels[pixel],
            )
        }
    }

    private fun pixels(bitmap: Bitmap): IntArray = IntArray(bitmap.width * bitmap.height).also {
        bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private fun setField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }
}
