package com.noctisoft.layoutmeasurement

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ColorInspectionOverlayTest {
    @After fun reset() {
        InspectorController.stopInspection()
        InspectorController.hideControlsForStartup()
    }

    @Test fun `Colors menu activates color inspection and picking a node reveals codes`() {
        val overlay = fixture()
        InspectorController.revealControls(RevealSource.SHAKE)
        find(overlay, "Layout inspector controls")!!.performClick()
        find(overlay, "Colors")!!.performClick()
        assertEquals(MeasureMode.COLORS, InspectorController.mode)
        tapCanvas(overlay)
        val panel = children(overlay).filterIsInstance<ColorDetailsPanel>().single()
        assertEquals(View.VISIBLE, panel.visibility)
        assertNotNull(find(panel, "Copy text color #FFFF0000"))
        assertTrue(ViewCapture.captureAll(overlay.rootView, true).none { it.label.contains("ColorDetailsPanel") })
    }

    @Test fun `switching away and back clears old colors and Stop hides the panel`() {
        val overlay = fixture()
        InspectorController.selectMode(MeasureMode.COLORS)
        tapCanvas(overlay)
        val panel = children(overlay).filterIsInstance<ColorDetailsPanel>().single()
        assertEquals(View.VISIBLE, panel.visibility)
        InspectorController.selectMode(MeasureMode.SIZE)
        assertEquals(View.GONE, panel.visibility)
        InspectorController.selectMode(MeasureMode.COLORS)
        assertEquals(View.GONE, panel.visibility)
        tapCanvas(overlay)
        InspectorController.hideControls()
        assertEquals(View.VISIBLE, panel.visibility)
        InspectorController.stopInspection()
        assertEquals(View.GONE, panel.visibility)
    }

    @Test fun `color panel respects insets while the canvas keeps full window coordinates`() {
        val overlay = fixture()
        InspectorController.selectMode(MeasureMode.COLORS)
        tapCanvas(overlay)
        val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(15, 25, 20, 30)).build()
        ViewCompat.dispatchApplyWindowInsets(overlay, insets)
        layout(overlay, 320, 480)
        val panel = children(overlay).filterIsInstance<ColorDetailsPanel>().single()
        val canvas = children(overlay).filterIsInstance<MeasureCanvas>().single()
        assertTrue(panel.left >= 15)
        assertTrue(panel.top >= 25)
        assertTrue(panel.right <= 300)
        assertTrue(panel.bottom <= 450)
        assertEquals(320, canvas.width)
        assertEquals(480, canvas.height)
        (overlay.parent as ViewGroup).removeView(overlay)
        assertEquals(View.GONE, panel.visibility)
    }

    @Test fun `floating controls win actual touches when overlapping the color panel`() {
        val overlay = fixture()
        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.updatePlacement(0.5f, 0.9f)
        InspectorController.selectMode(MeasureMode.COLORS)
        tapCanvas(overlay)
        layout(overlay, 320, 480)
        val circle = find(overlay, "Layout inspector controls")!!
        val x = circle.x + circle.width / 2f
        val y = circle.y + circle.height / 2f
        val panel = children(overlay).filterIsInstance<ColorDetailsPanel>().single()
        assertTrue(x >= panel.left && x < panel.right && y >= panel.top && y < panel.bottom)
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            overlay.dispatchTouchEvent(event)
            event.recycle()
        }
        assertEquals(FloatingControlState.EXPANDED, InspectorController.controlState)
    }

    private fun fixture(): InspectorOverlay {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val host = FrameLayout(activity)
        val text = TextView(activity).apply { this.text = "Color sample"; setTextColor(Color.RED) }
        host.addView(text, FrameLayout.LayoutParams(160, 60))
        activity.setContentView(host)
        val overlay = InspectorOverlay(activity)
        host.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        layout(host, 320, 480)
        return overlay
    }

    private fun tapCanvas(overlay: InspectorOverlay) {
        val canvas = children(overlay).filterIsInstance<MeasureCanvas>().single()
        // Convert the target's window coordinates to the existing full-window canvas convention.
        val nodes = ViewCapture.captureAll(overlay.rootView)
        val bounds = nodes.first { it.label == "TextView" }.bounds
        val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, (bounds.left + 5).toFloat(), (bounds.top + 5).toFloat(), 0)
        canvas.onTouchEvent(event)
        event.recycle()
    }
    private fun children(root: ViewGroup) = (0 until root.childCount).map(root::getChildAt)
    private fun find(view: View, description: String): View? {
        if (view.contentDescription == description) return view
        if (view is ViewGroup) repeat(view.childCount) { find(view.getChildAt(it), description)?.let { return it } }
        return null
    }
    private fun layout(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }
}
