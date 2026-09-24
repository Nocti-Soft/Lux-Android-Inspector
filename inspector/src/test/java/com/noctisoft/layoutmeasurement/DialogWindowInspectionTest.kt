package com.noctisoft.layoutmeasurement

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.graphics.Color
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DialogWindowInspectionTest {
    private lateinit var controller: ActivityController<Activity>
    private lateinit var activity: Activity
    private val dialogs = mutableListOf<Dialog>()

    @Before fun setUp() {
        InspectorController.stopInspection()
        InspectorController.hideControlsForStartup()
        InspectorController.restorePlacement(FloatingPlacement())
        controller = Robolectric.buildActivity(Activity::class.java).setup().visible()
        activity = controller.get()
        activity.setContentView(TextView(activity).apply { text = "Activity behind the sheet" })
        layout(activity.window.decorView)
        InspectorLifecycle.onActivityResumed(activity)
        InspectorController.revealControls(RevealSource.SHAKE)
        settle()
    }

    @After fun tearDown() {
        InspectorLifecycle.onActivityPaused(activity)
        dialogs.asReversed().forEach { it.dismiss() }
        InspectorLifecycle.onActivityDestroyed(activity)
        controller.pause().stop().destroy()
        InspectorController.stopInspection()
        InspectorController.hideControlsForStartup()
        InspectorController.restorePlacement(FloatingPlacement())
    }

    @Test fun `dialog gets the only overlay and actual floating button touches open the menu`() {
        val dialog = showDialog()
        val root = dialog.window!!.decorView as ViewGroup
        val overlay = overlay(root)
        assertNotNull("Inspector must move into the modal window", overlay)
        assertNull(overlay(activity.window.decorView as ViewGroup))
        val circle = find(overlay!!, "Layout inspector controls")!!
        tap(root, circle)
        assertEquals(FloatingControlState.EXPANDED, InspectorController.controlState)
        assertNotNull(find(overlay, "Properties"))
    }

    @Test fun `dialog dismissal restores one activity overlay with tool and placement retained`() {
        val placement = FloatingPlacement(0.15f, 0.72f)
        InspectorController.restorePlacement(placement)
        InspectorController.selectMode(MeasureMode.GAP)
        val dialog = showDialog()
        val instance = overlay(dialog.window!!.decorView as ViewGroup)
        assertNotNull(instance)
        dialog.dismiss()
        settle()
        assertSame(instance, overlay(activity.window.decorView as ViewGroup))
        assertEquals(MeasureMode.GAP, InspectorController.mode)
        assertEquals(placement, InspectorController.placement)
        assertTrue(InspectorController.isActive)
    }

    @Test fun `nested dialogs return through the stack without leaving duplicate overlays`() {
        val first = showDialog()
        val second = showDialog()
        assertNotNull(overlay(second.window!!.decorView as ViewGroup))
        assertNull(overlay(first.window!!.decorView as ViewGroup))
        second.dismiss()
        settle()
        assertNotNull(overlay(first.window!!.decorView as ViewGroup))
        first.dismiss()
        settle()
        assertNotNull(overlay(activity.window.decorView as ViewGroup))
        val reopened = showDialog()
        assertNotNull(overlay(reopened.window!!.decorView as ViewGroup))
        assertNull(overlay(activity.window.decorView as ViewGroup))
    }

    @Test fun `pause removes modal overlay and resume restores it without stale selection`() {
        val dialog = showDialog()
        val root = dialog.window!!.decorView as ViewGroup
        assertNotNull(overlay(root))
        InspectorLifecycle.onActivityPaused(activity)
        settle()
        assertNull(overlay(root))
        InspectorLifecycle.onActivityResumed(activity)
        settle()
        assertNotNull(overlay(root))
        assertNull(overlay(activity.window.decorView as ViewGroup))
    }

    @Test fun `unrelated activity dialog cannot steal this activity inspector`() {
        val other = Robolectric.buildActivity(Activity::class.java).setup().visible()
        try {
            val dialog = Dialog(other.get()).apply { setContentView(TextView(other.get())); show() }
            try {
                layout(dialog.window!!.decorView)
                settle()
                assertNull(overlay(dialog.window!!.decorView as ViewGroup))
                assertNotNull(overlay(activity.window.decorView as ViewGroup))
            } finally { dialog.dismiss() }
        } finally { other.pause().stop().destroy() }
    }

    @Test fun `dialog retains window flags and dismiss listener`() {
        val dialog = Dialog(activity).apply { requestWindowFeature(Window.FEATURE_NO_TITLE); setContentView(TextView(activity)) }
        dialogs += dialog
        var dismissed = 0
        dialog.setOnDismissListener { dismissed++ }
        dialog.show()
        val flags = dialog.window!!.attributes.flags
        layout(dialog.window!!.decorView)
        settle()
        assertNotNull(overlay(dialog.window!!.decorView as ViewGroup))
        assertEquals(flags, dialog.window!!.attributes.flags)
        dialog.dismiss()
        settle()
        assertEquals(1, dismissed)
    }

    @Test
    @Config(sdk = [24, 28], application = Application::class)
    fun `legacy Android also discovers dialog windows`() {
        val dialog = showDialog()
        assertNotNull(overlay(dialog.window!!.decorView as ViewGroup))
    }

    @Test fun `color hit testing uses canvas local coordinates inside an offset padded dialog`() {
        val dialog = showDialog()
        val root = dialog.window!!.decorView as ViewGroup
        val overlay = overlay(root)
        assertNotNull(overlay)
        val canvas = overlay!!.getChildAt(0) as MeasureCanvas
        val text = dialog.findViewById<TextView>(android.R.id.text1)
        InspectorController.selectMode(MeasureMode.COLORS)
        settle()
        // A dialog decor can inset its overlay independently of its content root.
        canvas.layout(30, 45, 650, 900)
        val textPosition = IntArray(2).also { text.getLocationInWindow(it) }
        val canvasPosition = IntArray(2).also { canvas.getLocationInWindow(it) }
        val x = textPosition[0] - canvasPosition[0] + text.width / 2f
        val y = textPosition[1] - canvasPosition[1] + text.height / 2f
        sendTap(canvas, x, y)
        val selected = canvas.javaClass.getDeclaredField("selectedA").apply { isAccessible = true }.get(canvas) as? CapturedNode
        assertNotNull("Should select dialog text at its on-screen position", selected)
        assertTrue(selected!!.label.endsWith("/text1"))
        assertEquals(textPosition[0] - canvasPosition[0], selected.bounds.left)
        assertEquals(textPosition[1] - canvasPosition[1], selected.bounds.top)
        assertEquals(ColorValue.Solid(Color.RED), selected.colors!!.text)
        dialog.dismiss()
        settle()
        val restored = overlay(activity.window.decorView as ViewGroup)!!.getChildAt(0)
        assertNull(restored.javaClass.getDeclaredField("selectedA").apply { isAccessible = true }.get(restored))
    }

    @Test fun `hidden dialog is ignored and showing it again restores the overlay`() {
        val dialog = showDialog()
        assertNotNull(overlay(dialog.window!!.decorView as ViewGroup))
        dialog.hide()
        settle()
        assertNotNull(overlay(activity.window.decorView as ViewGroup))
        dialog.show()
        settle()
        assertNotNull(overlay(dialog.window!!.decorView as ViewGroup))
        assertNull(overlay(activity.window.decorView as ViewGroup))
    }

    @Test fun `revealing idle controls while a sheet is already open finds its window`() {
        InspectorController.stopInspection()
        InspectorController.hideControlsForStartup()
        settle()
        val dialog = showDialog()
        assertNull(overlay(dialog.window!!.decorView as ViewGroup))
        InspectorController.revealControls(RevealSource.NOTIFICATION)
        settle()
        assertNotNull(overlay(dialog.window!!.decorView as ViewGroup))
        InspectorController.selectMode(MeasureMode.SIZE)
        InspectorController.hideControls()
        settle()
        assertNotNull(overlay(dialog.window!!.decorView as ViewGroup))
        assertTrue(InspectorController.isActive)
    }

    @Test fun `repeated resume does not stack overlays in the same dialog`() {
        val dialog = showDialog()
        repeat(2) { InspectorLifecycle.onActivityResumed(activity); settle() }
        assertNotNull(overlay(dialog.window!!.decorView as ViewGroup))
        assertNull(overlay(activity.window.decorView as ViewGroup))
    }

    private fun showDialog(): Dialog = Dialog(activity).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val content = FrameLayout(activity).apply {
            addView(TextView(activity).apply {
                id = android.R.id.text1
                text = "Modal text"
                setTextColor(Color.RED)
                setBackgroundColor(Color.WHITE)
            }, FrameLayout.LayoutParams(200, 100).apply { leftMargin = 100; topMargin = 160 })
        }
        setContentView(content)
        dialogs += this
        show()
        window!!.setLayout(700, 1000)
        layout(window!!.decorView)
        settle()
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        layout(activity.window.decorView)
        dialogs.filter { it.isShowing }.forEach { layout(it.window!!.decorView) }
        shadowOf(Looper.getMainLooper()).idle()
    }
    private fun layout(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 700, 1000)
    }
    private fun overlay(root: ViewGroup): InspectorOverlay? =
        (0 until root.childCount).map(root::getChildAt).filterIsInstance<InspectorOverlay>().singleOrNull()
    private fun find(root: View, description: String): View? {
        if (root.contentDescription == description) return root
        if (root is ViewGroup) repeat(root.childCount) { find(root.getChildAt(it), description)?.let { return it } }
        return null
    }
    private fun tap(root: View, target: View) {
        val origin = IntArray(2).also(root::getLocationInWindow)
        val pos = IntArray(2).also(target::getLocationInWindow)
        sendTap(root, (pos[0] - origin[0]) + target.width / 2f, (pos[1] - origin[1]) + target.height / 2f)
    }
    private fun sendTap(view: View, x: Float, y: Float) {
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach {
            val event = MotionEvent.obtain(0, 10, it, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }
}
