package com.noctisoft.layoutmeasurement

import android.app.Application
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `invalid safe area rejects collapsed and expanded touch streams`() {
        listOf(FloatingControlState.COLLAPSED, FloatingControlState.EXPANDED).forEach { state ->
            val control = FloatingInspectorControl(RuntimeEnvironment.getApplication())
            var collapsed = 0
            control.onCollapseRequested = { collapsed++ }
            control.setSafeArea(SafeArea(10, 10, 10, 100))
            control.render(state, FloatingPlacement(), MeasureMode.SIZE, false, true)
            layout(control, 200, 200)

            assertFalse(control.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20f, 20f)))
            assertFalse(control.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 20f, 20f)))
            assertEquals(0, collapsed)
        }
    }

    @Test
    fun `expanded menu reanchors after initial layout and resize`() {
        val control = FloatingInspectorControl(RuntimeEnvironment.getApplication())
        control.setSafeArea(SafeArea(0, 0, 500, 800))
        control.render(FloatingControlState.EXPANDED, FloatingPlacement(0.5f, 0.5f), MeasureMode.SIZE, false, true)
        layout(control, 500, 800)

        val initialCircle = circle(control)
        val initialMenu = menu(control)
        assertInsideAndSeparate(initialMenu, initialCircle, 500, 800)

        control.setSafeArea(SafeArea(0, 0, 320, 500))
        layout(control, 320, 500)
        val resizedCircle = circle(control)
        val resizedMenu = menu(control)
        assertInsideAndSeparate(resizedMenu, resizedCircle, 320, 500)
    }

    @Test
    @Config(sdk = [35], qualifiers = "440dpi", application = Application::class)
    fun `normal high density menu stays compact beside the existing circle position`() {
        val control = FloatingInspectorControl(RuntimeEnvironment.getApplication())
        val density = control.resources.displayMetrics.density
        val placement = FloatingPlacement(744f / (1080 - 56 * density), 1403f / (2340 - 56 * density))
        control.setSafeArea(SafeArea(0, 0, 1080, 2340))
        control.render(FloatingControlState.COLLAPSED, placement, MeasureMode.SIZE, true, true)
        layout(control, 1080, 2340)
        val circle = circle(control)
        val originalX = circle.x
        val originalY = circle.y

        control.render(FloatingControlState.EXPANDED, placement, MeasureMode.SIZE, true, true)
        layout(control, 1080, 2340)
        val menu = menu(control)

        assertEquals(originalX, circle.x, 0f)
        assertEquals(originalY, circle.y, 0f)
        assertTrue(menu.width in (180 * density).toInt()..(220 * density).toInt())
        assertTrue(menu.x + menu.width <= circle.x - (8 * density).toInt())
        assertInsideAndSeparate(menu, circle, 1080, 2340)
    }

    @Test
    fun `dispatched circle taps expand and undock exactly once`() {
        val control = visibleControl(FloatingControlState.COLLAPSED)
        var expanded = 0
        control.onExpandRequested = {
            expanded++
            control.render(FloatingControlState.EXPANDED, FloatingPlacement(), MeasureMode.SIZE, false, true)
        }
        tap(control, circle(control))
        assertEquals(1, expanded)

        control.render(FloatingControlState.DOCKED_RIGHT, FloatingPlacement(dockSide = DockSide.RIGHT), MeasureMode.SIZE, false, true)
        var undocked = 0
        control.onUndockRequested = {
            undocked++
            control.render(FloatingControlState.COLLAPSED, FloatingPlacement(), MeasureMode.SIZE, false, true)
        }
        tap(control, circle(control))
        assertEquals(1, undocked)
    }

    @Test
    fun `active pointer loss cancels drag without a click or placement commit`() {
        val control = visibleControl(FloatingControlState.COLLAPSED)
        val circle = circle(control)
        val originalX = circle.x
        val originalY = circle.y
        var expanded = 0
        var committed = 0
        control.onExpandRequested = { expanded++ }
        control.onPlacementChanged = { committed++ }
        control.onDockRequested = { _, _ -> committed++ }
        val point = centerInRoot(circle)

        control.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, point.first, point.second))
        control.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, point.first + 80f, point.second + 80f))
        control.dispatchTouchEvent(pointerUpEvent(point.first + 80f, point.second + 80f))

        assertEquals(originalX, circle.x)
        assertEquals(originalY, circle.y)
        assertEquals(0, expanded)
        assertEquals(0, committed)
    }

    @Test
    fun `expanded and docked drags collapse or undock before committing placement`() {
        val expanded = visibleControl(FloatingControlState.EXPANDED)
        var collapsed = 0
        var expandedPlacement = 0
        expanded.onCollapseRequested = {
            collapsed++
            expanded.render(FloatingControlState.COLLAPSED, FloatingPlacement(), MeasureMode.SIZE, false, true)
        }
        expanded.onPlacementChanged = {
            expandedPlacement++
            expanded.render(FloatingControlState.COLLAPSED, it, MeasureMode.SIZE, false, true)
        }
        dragTo(expanded, circle(expanded), 300f, 500f)
        assertEquals(1, collapsed)
        assertEquals(1, expandedPlacement)
        assertEquals(View.GONE, menu(expanded).visibility)

        val docked = visibleControl(FloatingControlState.DOCKED_LEFT)
        var undocked = 0
        var dockedPlacement = 0
        docked.onUndockRequested = {
            undocked++
            docked.render(FloatingControlState.COLLAPSED, FloatingPlacement(), MeasureMode.SIZE, false, true)
        }
        docked.onPlacementChanged = {
            dockedPlacement++
            docked.render(FloatingControlState.COLLAPSED, it, MeasureMode.SIZE, false, true)
        }
        dragTo(docked, circle(docked), 300f, 500f)
        assertEquals(1, undocked)
        assertEquals(1, dockedPlacement)
        assertEquals(View.GONE, menu(docked).visibility)
    }

    @Test
    fun `circle keeps a stable action and indigo target while state is exposed separately`() {
        val control = visibleControl(FloatingControlState.COLLAPSED, active = false)
        val circle = circle(control)
        val idleBackground = circle.background

        assertEquals("Layout inspector controls", circle.contentDescription)
        assertTrue(circle is ImageButton)
        assertEquals("Inspector idle", ViewCompat.getStateDescription(circle))

        control.render(FloatingControlState.COLLAPSED, FloatingPlacement(), MeasureMode.GAP, true, true)

        assertEquals("Layout inspector controls", circle.contentDescription)
        assertEquals("Inspector active: Gap", ViewCompat.getStateDescription(circle))
        assertEquals(idleBackground.constantState, circle.background.constantState)
    }

    @Test
    fun `quick menu uses readable native styled rows with semantic icons`() {
        val control = visibleControl(FloatingControlState.EXPANDED)

        listOf("Size", "Gap", "Ruler", "Bounds", "Colors", "Settings", "Start Inspector").forEach { label ->
            val button = button(control, label)
            assertTrue(button.textSize >= 14f)
            assertTrue(button.minimumHeight >= (48 * button.resources.displayMetrics.density).toInt())
            assertEquals(Color.WHITE, button.currentTextColor)
            assertNotNull(button.compoundDrawablesRelative[0])
            assertNull(button.backgroundTintList)
            assertNull(button.stateListAnimator)
        }
    }

    @Test
    fun `quick menu keeps modes then settings and a final start or stop action`() {
        val control = visibleControl(FloatingControlState.EXPANDED, active = false, canHide = true)
        val selected = mutableListOf<MeasureMode>()
        control.onModeSelected = { selected += it }

        assertEquals(
            listOf("Size", "Gap", "Ruler", "Bounds", "Colors", "Settings", "Start Inspector"),
            visibleButtonLabels(control),
        )
        button(control, "Start Inspector").performClick()
        assertEquals(listOf(MeasureMode.SIZE), selected)

        control.render(FloatingControlState.EXPANDED, FloatingPlacement(), MeasureMode.GAP, true, true)
        assertEquals(
            listOf("Size", "Gap", "Ruler", "Bounds", "Colors", "Settings", "Stop Inspector"),
            visibleButtonLabels(control),
        )
    }

    @Test
    fun `settings keeps Hide recovery state and notification controls behind Back navigation`() {
        val control = visibleControl(FloatingControlState.EXPANDED, canHide = false)
        var hidden = 0
        var notificationControls = 0
        control.onHideRequested = { hidden++ }
        control.onNotificationControlsRequested = { notificationControls++ }

        button(control, "Settings").performClick()
        assertEquals(listOf("Back", "Hide (notification required)", "Notification Controls"), visibleButtonLabels(control))
        val hide = button(control, "Hide (notification required)")
        assertFalse(hide.isEnabled)
        hide.performClick()
        button(control, "Notification Controls").performClick()
        assertEquals(0, hidden)
        assertEquals(1, notificationControls)
        button(control, "Back").performClick()
        assertEquals(View.VISIBLE, button(control, "Start Inspector").visibility)
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
    @Config(sdk = [24], application = Application::class)
    fun `dragging commits placement on min SDK`() {
        val control = visibleControl(FloatingControlState.COLLAPSED)
        var committed = 0
        control.onPlacementChanged = { committed++ }

        dragTo(control, circle(control), 300f, 500f)

        assertEquals(1, committed)
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
    fun `constrained menu scrolls to and activates its final action`() {
        val control = FloatingInspectorControl(RuntimeEnvironment.getApplication())
        control.setSafeArea(SafeArea(20, 10, 180, 130))
        control.render(FloatingControlState.EXPANDED, FloatingPlacement(0.9f, 0.5f), MeasureMode.SIZE, true, true)
        layout(control, 200, 160)
        val scroll = find(control) { it is ScrollView } as ScrollView
        val panel = scroll.getChildAt(0)
        var stopped = 0
        control.onStopRequested = { stopped++ }

        scroll.scrollTo(0, panel.height)
        layout(control, 200, 160)
        val finalAction = button(control, "Stop Inspector")
        assertTrue(scroll.scrollY > 0)
        assertTrue(finalAction.bottom - scroll.scrollY <= scroll.height)
        finalAction.performClick()
        assertEquals(1, stopped)
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

    private fun circle(control: ViewGroup): View = find(control) {
        it.contentDescription == "Layout inspector controls"
    } ?: error("Missing floating inspector control")

    private fun assertInsideAndSeparate(menu: View, circle: View, width: Int, height: Int) {
        assertTrue(menu.x >= 0)
        assertTrue(menu.y >= 0)
        assertTrue(menu.x + menu.width <= width)
        assertTrue(menu.y + menu.height <= height)
        assertTrue(
            menu.x + menu.width <= circle.x ||
                circle.x + circle.width <= menu.x ||
                menu.y + menu.height <= circle.y ||
                circle.y + circle.height <= menu.y,
        )
    }

    private fun visibleButtonLabels(control: ViewGroup): List<String> = buildList {
        fun collect(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (view is Button) add(view.text.toString())
            if (view is ViewGroup) repeat(view.childCount) { collect(view.getChildAt(it)) }
        }
        collect(control)
    }

    private fun menu(control: ViewGroup): ViewGroup = find(control) { it is ScrollView }?.parent as ViewGroup

    private fun button(control: ViewGroup, label: String): Button = find(control) { it is Button && (it.text == label || it.contentDescription == label) } as Button

    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root !is ViewGroup) return null
        repeat(root.childCount) { index -> find(root.getChildAt(index), predicate)?.let { return it } }
        return null
    }

    private fun tap(control: FloatingInspectorControl, view: View) {
        val point = centerInRoot(view)
        control.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, point.first, point.second))
        control.dispatchTouchEvent(event(MotionEvent.ACTION_UP, point.first, point.second))
    }

    private fun dragTo(control: FloatingInspectorControl, view: View, x: Float, y: Float) {
        val point = centerInRoot(view)
        control.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, point.first, point.second))
        control.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, x, y))
        control.dispatchTouchEvent(event(MotionEvent.ACTION_UP, x, y))
    }

    private fun centerInRoot(view: View): Pair<Float, Float> {
        var x = view.x + view.width / 2f
        var y = view.y + view.height / 2f
        var parent = view.parent
        while (parent is View) {
            x += parent.x - parent.scrollX
            y += parent.y - parent.scrollY
            parent = parent.parent
        }
        return x to y
    }

    private fun pointerUpEvent(x: Float, y: Float): MotionEvent = MotionEvent.obtain(
        0,
        0,
        MotionEvent.ACTION_POINTER_UP,
        1,
        arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
        arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y }),
        0,
        0,
        1f,
        1f,
        0,
        0,
        0,
        0,
    )

    private fun event(action: Int, x: Float, y: Float): MotionEvent = MotionEvent.obtain(0, 0, action, x, y, 0)
}
