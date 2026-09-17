package com.noctisoft.layoutmeasurement

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
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
@Config(sdk = [35], qualifiers = "440dpi", application = Application::class)
class FloatingInspectorControlRenderTest {
    @Test
    fun `white target pixel bounds and centroid are centered in the circle`() {
        val control = FloatingInspectorControl(RuntimeEnvironment.getApplication())
        control.setSafeArea(SafeArea(0, 0, 1080, 2340))
        control.render(FloatingControlState.COLLAPSED, FloatingPlacement(), MeasureMode.SIZE, false, true)
        layout(control, 1080, 2340)
        val circle = find(control) { it.contentDescription == "Layout inspector controls" }
            ?: error("Missing floating inspector control")
        val bitmap = Bitmap.createBitmap(circle.width, circle.height, Bitmap.Config.ARGB_8888)

        circle.draw(Canvas(bitmap))

        val pixels = buildList {
            repeat(bitmap.height) { y ->
                repeat(bitmap.width) { x ->
                    val color = bitmap.getPixel(x, y)
                    if (android.graphics.Color.red(color) > 240 &&
                        android.graphics.Color.green(color) > 240 &&
                        android.graphics.Color.blue(color) > 240
                    ) add(x to y)
                }
            }
        }
        assertTrue(pixels.isNotEmpty())
        val expectedCenter = (circle.width - 1) / 2f
        assertEquals(expectedCenter, (pixels.minOf { it.first } + pixels.maxOf { it.first }) / 2f, 1f)
        assertEquals(expectedCenter, pixels.map { it.first }.average().toFloat(), 1f)
        assertEquals((circle.height - 1) / 2f, (pixels.minOf { it.second } + pixels.maxOf { it.second }) / 2f, 1f)
        assertEquals((circle.height - 1) / 2f, pixels.map { it.second }.average().toFloat(), 1f)
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root !is ViewGroup) return null
        repeat(root.childCount) { index -> find(root.getChildAt(index), predicate)?.let { return it } }
        return null
    }
}
