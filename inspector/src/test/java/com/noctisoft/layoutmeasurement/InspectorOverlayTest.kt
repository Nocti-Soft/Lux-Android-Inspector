package com.noctisoft.layoutmeasurement

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class InspectorOverlayTest {
    @After
    fun resetController() {
        InspectorController.stopInspection()
        InspectorController.restorePlacement(FloatingPlacement())
        InspectorController.hideControlsForStartup()
    }

    @Test
    fun `overlay composes a full window canvas below an initially hidden floating control`() {
        val fixture = overlay()
        val overlay = fixture.overlay

        assertEquals(InspectorOverlay.TAG, overlay.tag)
        assertEquals(2, overlay.childCount)
        assertEquals("MeasureCanvas", canvas(overlay).javaClass.simpleName)
        assertEquals("FloatingInspectorControl", controls(overlay).javaClass.simpleName)
        assertEquals(View.GONE, canvas(overlay).visibility)
        assertEquals(View.GONE, controls(overlay).visibility)
        assertEquals(0, overlay.paddingLeft)
        assertEquals(0, overlay.paddingTop)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, canvas(overlay).layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, canvas(overlay).layoutParams.height)
    }

    @Test
    fun `integrated mode selection switches directly without stopping the session`() {
        val fixture = overlay()
        InspectorController.revealControls(RevealSource.SHAKE)
        button(fixture.overlay, "Layout inspector controls").performClick()
        button(fixture.overlay, "Size").performClick()
        button(fixture.overlay, "Layout inspector controls").performClick()
        button(fixture.overlay, "Gap").performClick()

        assertTrue(InspectorController.isActive)
        assertEquals(MeasureMode.GAP, InspectorController.mode)
    }

    @Test
    fun `Start resumes the retained mode and Stop clears selection while keeping the circle`() {
        val fixture = overlay()
        InspectorController.selectMode(MeasureMode.GAP)
        InspectorController.stopInspection()
        InspectorController.revealControls(RevealSource.SHAKE)

        button(fixture.overlay, "Layout inspector controls").performClick()
        button(fixture.overlay, "Start Inspector").performClick()
        assertTrue(InspectorController.isActive)
        assertEquals(MeasureMode.GAP, InspectorController.mode)
        assertEquals(FloatingControlState.COLLAPSED, InspectorController.controlState)

        InspectorController.selectMode(MeasureMode.SIZE)
        canvas(fixture.overlay).dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20f, 20f))
        assertNotNull(privateField(canvas(fixture.overlay), "selectedA"))
        button(fixture.overlay, "Layout inspector controls").performClick()
        button(fixture.overlay, "Stop Inspector").performClick()

        assertFalse(InspectorController.isActive)
        assertEquals(FloatingControlState.COLLAPSED, InspectorController.controlState)
        assertEquals(null, privateField(canvas(fixture.overlay), "selectedA"))
        assertEquals(View.VISIBLE, controls(fixture.overlay).visibility)
    }

    @Test
    fun `drag dock and tap undock commit placement to the attached store`() {
        val fixture = overlay()
        val control = controls(fixture.overlay)
        InspectorController.revealControls(RevealSource.SHAKE)
        layout(fixture.overlay, 320, 480)
        val circle = button(fixture.overlay, "Layout inspector controls")
        val start = circle.x + circle.width / 2f to circle.y + circle.height / 2f
        control.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, start.first, start.second))
        control.dispatchTouchEvent(event(MotionEvent.ACTION_MOVE, 319f, start.second))
        control.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 319f, start.second))

        assertEquals(DockSide.RIGHT, InspectorPlacementStore(fixture.activity).load().dockSide)
        button(fixture.overlay, "Layout inspector controls").performClick()
        assertEquals(DockSide.NONE, InspectorPlacementStore(fixture.activity).load().dockSide)
    }

    @Test
    fun `dispatched insets constrain only controls and are not consumed across resize`() {
        val fixture = overlay()
        val originalPadding = intArrayOf(fixture.host.paddingLeft, fixture.host.paddingTop, fixture.host.paddingRight, fixture.host.paddingBottom)
        val hostCoordinates = fixture.host.left to fixture.host.top
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(4, 10, 6, 12))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(8, 3, 2, 1))
            .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), Insets.of(1, 2, 14, 5))
            .build()

        assertSame(insets, ViewCompat.dispatchApplyWindowInsets(fixture.overlay, insets))
        assertEquals(SafeArea(8, 10, 306, 468), privateField(controls(fixture.overlay), "safeArea"))
        assertEquals(hostCoordinates, fixture.host.left to fixture.host.top)
        assertEquals(originalPadding.toList(), listOf(fixture.host.paddingLeft, fixture.host.paddingTop, fixture.host.paddingRight, fixture.host.paddingBottom))
        InspectorController.selectMode(MeasureMode.SIZE)
        layout(fixture.overlay, 320, 480)
        assertEquals(320, canvas(fixture.overlay).width)
        assertEquals(480, canvas(fixture.overlay).height)

        layout(fixture.overlay, 10, 10)
        assertEquals(SafeArea(8, 10, -4, -2), privateField(controls(fixture.overlay), "safeArea"))
        assertFalse(controls(fixture.overlay).dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 5f, 5f)))
        layout(fixture.overlay, 320, 480)
        assertSame(insets, ViewCompat.dispatchApplyWindowInsets(fixture.overlay, insets))
    }

    @Test
    fun `canvas selection survives Hide while stop then immediate restart clears cached selection`() {
        val fixture = overlay()
        val canvas = canvas(fixture.overlay)
        InspectorController.selectMode(MeasureMode.SIZE)
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20f, 20f))
        assertNotNull(privateField(canvas, "selectedA"))

        InspectorController.hideControls()
        assertNotNull(privateField(canvas, "selectedA"))

        InspectorController.stopInspection()
        InspectorController.selectMode(MeasureMode.SIZE)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(null, privateField(canvas, "selectedA"))
        assertEquals(emptyList<CapturedNode>(), privateField(canvas, "nodes"))
    }

    @Test
    fun `measurement consumes canvas touches while floating controls win their own touches`() {
        val fixture = overlay()
        val canvas = canvas(fixture.overlay)
        assertFalse(fixture.overlay.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20f, 20f)))

        InspectorController.revealControls(RevealSource.SHAKE)
        InspectorController.selectMode(MeasureMode.SIZE)
        layout(fixture.overlay, 320, 480)
        assertTrue(fixture.overlay.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20f, 20f)))
        val selected = privateField(canvas, "selectedA")
        assertNotNull(selected)

        val circle = button(fixture.overlay, "Layout inspector controls")
        val point = circle.x + circle.width / 2f to circle.y + circle.height / 2f
        assertTrue(fixture.overlay.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, point.first, point.second)))
        assertTrue(fixture.overlay.dispatchTouchEvent(event(MotionEvent.ACTION_UP, point.first, point.second)))
        assertEquals(selected, privateField(canvas, "selectedA"))
    }

    @Test
    fun `placement commits include notification undock without construction or resize rewrites`() {
        val context = Robolectric.buildActivity(Activity::class.java).setup().get()
        val store = InspectorPlacementStore(context)
        val persisted = FloatingPlacement(0.2f, 0.7f, DockSide.LEFT)
        store.save(persisted)
        InspectorController.restorePlacement(persisted)
        val fixture = overlay(context, store)
        assertEquals(persisted, store.load())

        layout(fixture.overlay, 640, 960)
        assertEquals(persisted, store.load())

        InspectorActionReceiver().onReceive(context, Intent(NotificationTrigger.ACTION_SHOW))
        InspectorActionReceiver().onReceive(context, Intent(NotificationTrigger.ACTION_SHOW))
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(FloatingPlacement(0.2f, 0.7f, DockSide.NONE), store.load())
    }

    @Test
    fun `blocked recovery prevents Hide at click and focus refresh`() {
        val fixture = overlay()
        val manager = fixture.activity.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.createNotificationChannel(
            android.app.NotificationChannel("layout_inspector", "Layout Inspector", android.app.NotificationManager.IMPORTANCE_NONE),
        )
        InspectorController.revealControls(RevealSource.SHAKE)
        button(fixture.overlay, "Layout inspector controls").performClick()
        button(fixture.overlay, "Settings").performClick()
        fixture.overlay.onWindowFocusChanged(true)
        val hide = button(fixture.overlay, "Hide (notification required)") as Button

        assertFalse(hide.isEnabled)
        hide.performClick()
        assertEquals(FloatingControlState.EXPANDED, InspectorController.controlState)
    }

    @Test
    fun `detach cancels queued observer work and reattach restores rendering without capturing overlay`() {
        val fixture = overlay()
        val parent = fixture.overlay.parent as ViewGroup
        parent.removeView(fixture.overlay)
        InspectorController.revealControls(RevealSource.SHAKE)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(View.GONE, controls(fixture.overlay).visibility)

        parent.addView(fixture.overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        layout(fixture.overlay, 320, 480)
        assertEquals(View.VISIBLE, controls(fixture.overlay).visibility)
        assertTrue(ViewCapture.captureAll(fixture.activity.window.decorView).none { it.label == "MeasureCanvas" || it.label == "FloatingInspectorControl" })
    }

    @Test
    fun `detach stop and immediate restart does not restore obsolete selection`() {
        val fixture = overlay()
        val canvas = canvas(fixture.overlay)
        InspectorController.selectMode(MeasureMode.SIZE)
        canvas.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 20f, 20f))
        assertNotNull(privateField(canvas, "selectedA"))

        val parent = fixture.overlay.parent as ViewGroup
        parent.removeView(fixture.overlay)
        InspectorController.stopInspection()
        InspectorController.selectMode(MeasureMode.SIZE)
        parent.addView(fixture.overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        layout(fixture.overlay, 320, 480)

        assertEquals(null, privateField(canvas, "selectedA"))
        assertEquals(emptyList<CapturedNode>(), privateField(canvas, "nodes"))
    }

    private fun overlay(): Fixture {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        return overlay(activity, InspectorPlacementStore(activity))
    }

    private fun overlay(activity: Activity, store: InspectorPlacementStore): Fixture {
        val host = View(activity).apply { setPadding(3, 5, 7, 11) }
        activity.addContentView(host, ViewGroup.LayoutParams(100, 100))
        val overlay = InspectorOverlay(activity, store)
        activity.addContentView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        layout(overlay, 320, 480)
        return Fixture(activity, host, overlay)
    }

    private fun layout(view: View, width: Int, height: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
    }

    private fun canvas(overlay: InspectorOverlay): MeasureCanvas = overlay.getChildAt(0) as MeasureCanvas
    private fun controls(overlay: InspectorOverlay): FloatingInspectorControl = overlay.getChildAt(1) as FloatingInspectorControl
    private fun button(root: View, label: String): View = find(root) {
        it.contentDescription == label || (it is Button && it.text == label)
    } ?: error("Missing control: $label")
    private fun find(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root !is ViewGroup) return null
        repeat(root.childCount) { index -> find(root.getChildAt(index), predicate)?.let { return it } }
        return null
    }
    private fun privateField(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun event(action: Int, x: Float, y: Float): MotionEvent = MotionEvent.obtain(0, 0, action, x, y, 0)
    private data class Fixture(val activity: Activity, val host: View, val overlay: InspectorOverlay)
}
