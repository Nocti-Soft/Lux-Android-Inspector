package com.noctisoft.layoutmeasurement

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun `Gap labels cannot erase a selected border`() {
        val view = MeasureCanvas(RuntimeEnvironment.getApplication())
        layout(view, 120, 120)
        setField(view, "selectedA", CapturedNode("A", Bounds(20, 20, 60, 60), Source.XML))
        setField(view, "selectedB", CapturedNode("B", Bounds(65, 20, 105, 60), Source.XML))
        InspectorController.selectMode(MeasureMode.GAP)
        val bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)

        view.draw(Canvas(bitmap))

        assertEquals(Color.rgb(255, 0, 168), bitmap.getPixel(60, 40))
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
