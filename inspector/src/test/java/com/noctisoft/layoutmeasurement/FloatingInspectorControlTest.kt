package com.noctisoft.layoutmeasurement

import android.app.Application
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FloatingInspectorControlTest {
    @Test
    fun `new control is hidden before its first render`() {
        assertEquals(View.GONE, FloatingInspectorControl(RuntimeEnvironment.getApplication()).visibility)
    }

    @Test
    fun `circle click expands and docked circle click undocks once`() {
        val control = visibleControl(FloatingControlState.COLLAPSED)
        var expanded = 0
        control.onExpandRequested = { expanded++ }

        circle(control).performClick()

        assertEquals(1, expanded)
        control.render(FloatingControlState.DOCKED_RIGHT, FloatingPlacement(dockSide = DockSide.RIGHT), MeasureMode.SIZE, false, true)
        var undocked = 0
        control.onUndockRequested = { undocked++ }
        circle(control).performClick()
        assertEquals(1, undocked)
    }

    @Test
    fun `mode hide settings and stop buttons invoke their callbacks`() {
        val control = visibleControl(FloatingControlState.EXPANDED, active = true, canHide = true)
        val selected = mutableListOf<MeasureMode>()
        var hidden = 0
        var stopped = 0
        control.onModeSelected = { selected += it }
        control.onHideRequested = { hidden++ }
        control.onStopRequested = { stopped++ }

        button(control, "Size").performClick()
        button(control, "Gap").performClick()
        button(control, "Ruler").performClick()
        button(control, "Bounds").performClick()
        button(control, "Hide Inspector").performClick()
        button(control, "Settings").performClick()
        button(control, "Stop Inspector").performClick()

        assertEquals(listOf(MeasureMode.SIZE, MeasureMode.GAP, MeasureMode.RULER, MeasureMode.BOUNDS), selected)
        assertEquals(1, hidden)
        assertEquals(1, stopped)
    }

    @Test
    fun `hide is unavailable without notification recovery`() {
        val control = visibleControl(FloatingControlState.EXPANDED, canHide = false)
        val hide = button(control, "Hide (notification required)")
        var hidden = 0
        control.onHideRequested = { hidden++ }

        assertFalse(hide.isEnabled)
        hide.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 1f, 1f))
        hide.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 1f, 1f))

        assertEquals(0, hidden)
    }

    @Test
    fun `collapsed control passes through outside touches while expanded dismissal consumes them`() {
        val control = visibleControl(FloatingControlState.COLLAPSED)
        assertFalse(control.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 5f, 5f)))

        control.render(FloatingControlState.EXPANDED, FloatingPlacement(), MeasureMode.SIZE, false, true)
        layout(control, 500, 800)
        var collapsed = 0
        control.onCollapseRequested = { collapsed++ }
        assertTrue(control.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 5f, 5f)))
        assertTrue(control.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 5f, 5f)))
        assertEquals(1, collapsed)
    }

    @Test
    fun `drag docks once without clicking`() {
        val control = visibleControl(FloatingControlState.COLLAPSED)
        var expanded = 0
        var docked: Pair<DockSide, FloatingPlacement>? = null
        control.onExpandRequested = { expanded++ }
        control.onDockRequested = { side, placement -> docked = side to placement }
        val button = circle(control)
        val x = button.x + button.width / 2f
        val y = button.y + button.height / 2f

        control.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, x, y))
        control.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 480f, y))
        control.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 480f, y))

        assertEquals(0, expanded)
        assertNotNull(docked)
        assertEquals(DockSide.RIGHT, docked?.first)
        assertEquals(DockSide.RIGHT, docked?.second?.dockSide)
    }

    @Test
    fun `cancelled drag restores committed position without placement callback`() {
        val control = visibleControl(FloatingControlState.COLLAPSED)
        var placements = 0
        var docks = 0
        control.onPlacementChanged = { placements++ }
        control.onDockRequested = { _, _ -> docks++ }
        val button = circle(control)
        val originalX = button.x
        val originalY = button.y
        val x = originalX + button.width / 2f
        val y = originalY + button.height / 2f

        control.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, x, y))
        control.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 400f, 600f))
        control.dispatchTouchEvent(event(MotionEvent.ACTION_CANCEL, 400f, 600f))

        assertEquals(originalX, button.x)
        assertEquals(originalY, button.y)
        assertEquals(0, placements)
        assertEquals(0, docks)
    }

    @Test
    fun `resize keeps circle and scrollable menu inside safe area`() {
        val control = FloatingInspectorControl(RuntimeEnvironment.getApplication())
        control.setSafeArea(SafeArea(20, 10, 140, 110))
        control.render(FloatingControlState.EXPANDED, FloatingPlacement(), MeasureMode.SIZE, true, true)
        layout(control, 200, 150)

        val button = circle(control)
        val scroll = find(control) { it is ScrollView } as ScrollView
        assertTrue(button.x >= 20f)
        assertTrue(button.y >= 10f)
        assertTrue(scroll.width <= 120)
        assertTrue(scroll.height <= 100)
        assertTrue((scroll.getChildAt(0) as ViewGroup).height >= scroll.height)
    }

    private fun visibleControl(
        state: FloatingControlState,
        active: Boolean = false,
        canHide: Boolean = true,
    ): FloatingInspectorControl = FloatingInspectorControl(RuntimeEnvironment.getApplication()).also {
        it.setSafeArea(SafeArea(0, 0, 500, 800))
        it.render(state, FloatingPlacement(0.5f, 0.5f), MeasureMode.SIZE, active, canHide)
        layout(it, 500, 800)
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, width, height)
    }

    private fun circle(control: ViewGroup): TextView = find(control) { it is TextView && it.text == "◎" } as TextView

    private fun button(control: ViewGroup, label: String): Button = find(control) { it is Button && (it.text == label || it.contentDescription == label) } as Button

    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root !is ViewGroup) return null
        repeat(root.childCount) { index -> find(root.getChildAt(index), predicate)?.let { return it } }
        return null
    }

    private fun event(action: Int, x: Float, y: Float): MotionEvent = MotionEvent.obtain(0, 0, action, x, y, 0)
}
