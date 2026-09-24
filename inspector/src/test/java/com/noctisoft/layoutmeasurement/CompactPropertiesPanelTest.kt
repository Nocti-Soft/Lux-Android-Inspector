package com.noctisoft.layoutmeasurement

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi", application = Application::class)
class CompactPropertiesPanelTest {
    private val activities = mutableListOf<ActivityController<Activity>>()
    private var clock = 100L

    @After fun cleanup() {
        InspectorController.stopInspection()
        InspectorController.hideControlsForStartup()
        activities.forEach { it.pause().stop().destroy() }
    }

    @Test fun `panel is at most 260 by 240 dp without reducing its scrollable content`() {
        val f = fixture()
        assertEquals(260, f.panel.width)
        assertTrue("Panel height ${f.panel.height} exceeds 240dp", f.panel.height <= 240)
        assertTrue(scroll(f.panel).canScrollVertically(1))
    }

    @Test
    @Config(sdk = [24, 28, 35])
    fun `header drag moves the panel by the finger delta across multiple move events`() {
        val f = fixture()
        val before = position(f.panel)
        drag(f, handle(f), 85f, -210f)
        assertEquals(before.first + 85, f.panel.left)
        assertEquals(before.second - 210, f.panel.top)
        drag(f, handle(f), -25f, 40f)
        assertEquals(before.first + 60, f.panel.left)
        assertEquals(before.second - 170, f.panel.top)
    }

    @Test fun `selecting another component keeps the dragged position and resets only content scroll`() {
        val f = fixture()
        drag(f, handle(f), 85f, -210f)
        val before = position(f.panel)
        scroll(f.panel).scrollTo(0, 300)
        f.canvas.onColorNodeSelected(node("second"))
        layout(f.host, 420, 840)
        assertEquals(before, position(f.panel))
        assertEquals(0, scroll(f.panel).scrollY)
        assertTrue(all(f.panel).any { it is android.widget.TextView && it.text.toString().contains("second") })
    }

    @Test fun `scrolling content does not move the panel or copy a color`() {
        val f = fixture()
        val clipboard = f.overlay.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("existing", "unchanged"))
        val before = position(f.panel)
        val body = scroll(f.panel)
        val bounds = inOverlay(f.overlay, body)
        gesture(f, bounds.centerX().toFloat(), (bounds.bottom - 25).toFloat(), 0f, -90f)
        assertTrue("Body did not scroll", body.scrollY > 0)
        assertEquals(before, position(f.panel))
        assertEquals("unchanged", clipboard.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test fun `header and close button stay visible when the body is scrolled to the bottom`() {
        val f = fixture()
        val headerBefore = inOverlay(f.overlay, handle(f))
        val close = required(f.panel, "Dismiss properties")
        val closeBefore = inOverlay(f.overlay, close)
        scroll(f.panel).scrollTo(0, Int.MAX_VALUE)
        layout(f.host, 420, 840)
        assertTrue(scroll(f.panel).scrollY > 0)
        assertEquals(headerBefore, inOverlay(f.overlay, handle(f)))
        assertEquals(closeBefore, inOverlay(f.overlay, close))
        tap(f, close)
        assertEquals(View.GONE, f.panel.visibility)
    }

    @Test fun `drag clamps all four edges within system insets and safe margins`() {
        val f = fixture()
        ViewCompat.dispatchApplyWindowInsets(f.overlay, WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(20, 30, 25, 40)).build())
        layout(f.host, 420, 840)
        drag(f, handle(f), 5000f, -5000f)
        assertEquals(420 - 25 - 12, f.panel.right)
        assertEquals(30 + 12, f.panel.top)
        drag(f, handle(f), -5000f, 5000f)
        assertEquals(20 + 12, f.panel.left)
        assertEquals(840 - 40 - 12, f.panel.bottom)
    }

    @Test fun `a smaller window reclamps a previously dragged panel including its fixed header`() {
        val f = fixture()
        drag(f, handle(f), 500f, -100f)
        layout(f.host, 220, 210)
        assertTrue(f.panel.left >= 12)
        assertTrue(f.panel.top >= 12)
        assertTrue("panel=${f.panel.left},${f.panel.top},${f.panel.width},${f.panel.height}; overlay=${f.overlay.width},${f.overlay.height}", f.panel.right <= 208)
        assertTrue(f.panel.bottom <= 198)
        val close = inOverlay(f.overlay, required(f.panel, "Dismiss properties"))
        assertTrue(Rect(0, 0, 220, 210).contains(close))
        assertTrue(scroll(f.panel).height > 0)
    }

    @Test fun `small header movement is a tap rather than an accidental drag`() {
        val f = fixture()
        val slop = ViewConfiguration.get(f.overlay.context).scaledTouchSlop
        val before = position(f.panel)
        drag(f, handle(f), slop / 3f, 0f)
        assertEquals(before, position(f.panel))
        assertEquals(View.VISIBLE, f.panel.visibility)
    }

    @Test fun `color copy remains an explicit tap after dragging and scrolling`() {
        val f = fixture()
        drag(f, handle(f), 90f, -200f)
        val body = scroll(f.panel)
        val copy = required(f.panel, "Copy text color #FF000000")
        val relative = Rect(0, 0, copy.width, copy.height)
        body.offsetDescendantRectToMyCoords(copy, relative)
        body.scrollTo(0, relative.top)
        val before = position(f.panel)
        tap(f, copy)
        val clipboard = f.overlay.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("#FF000000", clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertEquals(before, position(f.panel))
    }

    @Test fun `cancelled drag releases gesture state before the next drag`() {
        val f = fixture()
        val bounds = inOverlay(f.overlay, handle(f))
        val x = bounds.centerX().toFloat(); val y = bounds.centerY().toFloat()
        val before = position(f.panel)
        pointerEvent(f, MotionEvent.ACTION_DOWN, listOf(0 to (x to y)))
        pointerEvent(f, MotionEvent.ACTION_MOVE, listOf(0 to (x + 30 to y - 30)))
        pointerEvent(f, MotionEvent.ACTION_CANCEL, listOf(0 to (x + 30 to y - 30)))
        drag(f, handle(f), 25f, -25f)
        assertEquals(before.first + 55, f.panel.left)
        assertEquals(before.second - 55, f.panel.top)
    }

    @Test fun `lifting the active finger transfers the drag without a position jump`() {
        val f = fixture()
        val bounds = inOverlay(f.overlay, handle(f))
        val x = bounds.centerX().toFloat(); val y = bounds.centerY().toFloat()
        val before = position(f.panel)
        pointerEvent(f, MotionEvent.ACTION_DOWN, listOf(0 to (x to y)))
        pointerEvent(f, MotionEvent.ACTION_MOVE, listOf(0 to (x + 20 to y - 20)))
        val two = listOf(0 to (x + 20 to y - 20), 7 to (x + 55 to y - 10))
        pointerEvent(f, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), two)
        pointerEvent(f, MotionEvent.ACTION_POINTER_UP, two)
        assertEquals(before.first + 20, f.panel.left)
        assertEquals(before.second - 20, f.panel.top)
        pointerEvent(f, MotionEvent.ACTION_MOVE, listOf(7 to (x + 80 to y - 40)))
        pointerEvent(f, MotionEvent.ACTION_UP, listOf(7 to (x + 80 to y - 40)))
        assertEquals(before.first + 45, f.panel.left)
        assertEquals(before.second - 50, f.panel.top)
    }

    private fun pointerEvent(f: Fixture, action: Int, points: List<Pair<Int, Pair<Float, Float>>>) {
        val properties = points.map { (id, _) -> MotionEvent.PointerProperties().apply {
            this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
        } }.toTypedArray()
        val coords = points.map { (_, point) -> MotionEvent.PointerCoords().apply {
            x = point.first; y = point.second; pressure = 1f; size = 1f
        } }.toTypedArray()
        clock += 20
        val event = MotionEvent.obtain(100L, clock, action, points.size, properties, coords,
            0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        assertTrue(f.overlay.dispatchTouchEvent(event))
        event.recycle()
        layout(f.host, f.host.width, f.host.height)
    }

    private data class Fixture(val host: FrameLayout, val overlay: InspectorOverlay, val panel: ColorDetailsPanel, val canvas: MeasureCanvas)
    private fun fixture(): Fixture {
        InspectorController.stopInspection()
        InspectorController.hideControlsForStartup()
        val controller = Robolectric.buildActivity(Activity::class.java).setup().also(activities::add)
        val activity = controller.get()
        val host = FrameLayout(activity)
        activity.setContentView(host)
        val overlay = InspectorOverlay(activity)
        host.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        layout(host, 420, 840)
        shadowOf(Looper.getMainLooper()).idle()
        ViewCompat.dispatchApplyWindowInsets(overlay, WindowInsetsCompat.Builder().build())
        layout(host, 420, 840)
        InspectorController.selectMode(MeasureMode.PROPERTIES)
        val panel = all(overlay).filterIsInstance<ColorDetailsPanel>().single()
        val canvas = all(overlay).filterIsInstance<MeasureCanvas>().single()
        canvas.onColorNodeSelected(node("first"))
        layout(host, 420, 840)
        return Fixture(host, overlay, panel, canvas)
    }
    private fun node(label: String) = CapturedNode(label, Bounds(20, 40, 180, 100), Source.XML,
        ComponentColors(ColorValue.Multiple((0 until 40).map { 0xFF000000.toInt() + it }), ColorValue.None, ColorValue.None))
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { all(view.getChildAt(it)) }
    } else emptyList()
    private fun scroll(panel: ColorDetailsPanel) = all(panel).filterIsInstance<ScrollView>().single()
    private fun required(root: View, description: String): View {
        val view = all(root).firstOrNull { it.contentDescription == description }
        assertNotNull("Missing fixed header control: $description", view)
        return view!!
    }
    private fun handle(f: Fixture) = required(f.panel, "Move properties panel")
    private fun position(view: View) = view.left to view.top
    private fun inOverlay(overlay: InspectorOverlay, view: View): Rect = Rect(0, 0, view.width, view.height).also {
        overlay.offsetDescendantRectToMyCoords(view, it)
    }
    private fun drag(f: Fixture, view: View, dx: Float, dy: Float) {
        val bounds = inOverlay(f.overlay, view)
        gesture(f, bounds.centerX().toFloat(), bounds.centerY().toFloat(), dx, dy)
    }
    private fun tap(f: Fixture, view: View) = drag(f, view, 0f, 0f)
    private fun gesture(f: Fixture, x: Float, y: Float, dx: Float, dy: Float) {
        val down = clock
        fun send(action: Int, px: Float, py: Float) {
            clock += 20
            MotionEvent.obtain(down, clock, action, px, py, 0).also {
                assertTrue("Gesture not consumed", f.overlay.dispatchTouchEvent(it))
                it.recycle()
            }
            layout(f.host, f.host.width, f.host.height)
        }
        send(MotionEvent.ACTION_DOWN, x, y)
        for (step in 1..4) send(MotionEvent.ACTION_MOVE, x + dx * step / 4, y + dy * step / 4)
        send(MotionEvent.ACTION_UP, x + dx, y + dy)
        shadowOf(Looper.getMainLooper()).idle() // View posts performClick after a touch inside a scrolling container.
    }
    private fun layout(view: View, width: Int, height: Int) {
        // Keep scheduled Window traversal consistent with this test's explicit viewport.
        view.layoutParams?.let { params ->
            if (params.width != width || params.height != height) {
                params.width = width; params.height = height
                view.layoutParams = params
            }
        }
        repeat(2) {
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
    }
}
