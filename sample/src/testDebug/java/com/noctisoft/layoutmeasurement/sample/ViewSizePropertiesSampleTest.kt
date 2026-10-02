package com.noctisoft.layoutmeasurement.sample

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.noctisoft.layoutmeasurement.CapturedNode
import com.noctisoft.layoutmeasurement.Geometry
import com.noctisoft.layoutmeasurement.InspectorController
import com.noctisoft.layoutmeasurement.InspectorInitializer
import com.noctisoft.layoutmeasurement.InspectorOverlay
import com.noctisoft.layoutmeasurement.MeasureMode
import com.noctisoft.layoutmeasurement.ViewCapture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi", application = Application::class)
class ViewSizePropertiesSampleTest {
    @Before fun initialize() { InspectorInitializer().create(RuntimeEnvironment.getApplication()) }
    @After fun stop() {
        InspectorController.stopInspection()
        InspectorController.hideControlsForStartup()
    }

    @Test fun `XML sample selection shows its actual component width and height`() {
        withScreen(MainActivity::class.java) { root ->
            selectAndCheck(root) { it.label.endsWith("/box_a") }
        }
    }

    @Test fun `real Compose selection shows component size alongside typography`() {
        withScreen(ComposeActivity::class.java) { root ->
            val rows = selectAndCheck(root) { it.label == "compose_colors" }
            assertTrue("Font size" in rows)
            assertTrue("#FF25236D" in rows)
        }
    }

    @Test fun `mixed screen updates size when switching between XML and Compose nodes`() {
        withScreen(MixedActivity::class.java) { root ->
            val first = selectAndCheck(root) { it.label.endsWith("/xml_button") }
            val second = selectAndCheck(root) { it.label == "island_a" }
            assertNotEquals(first.first { it.startsWith("Properties ·") }, second.first { it.startsWith("Properties ·") })
            assertEquals(1, second.count { it == "Width" })
            assertEquals(1, second.count { it == "Height" })
        }
    }

    private fun selectAndCheck(root: View, matches: (CapturedNode) -> Boolean): List<String> {
        InspectorController.selectMode(MeasureMode.PROPERTIES)
        // Activation changes the canvas from GONE to VISIBLE; dispatch only after layout.
        repeat(2) {
            root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, 400, 800)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
        }
        val node = ViewCapture.captureAll(root, includeColors = true).single(matches)
        val overlay = views(root).filterIsInstance<InspectorOverlay>().single()
        // The sample consumes only public inspector APIs; dispatch the tap through the overlay.
        val origin = IntArray(2).also(overlay::getLocationInWindow)
        val x = node.bounds.left - origin[0] + node.bounds.width / 2f
        val y = node.bounds.top - origin[1] + node.bounds.height / 2f
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 0, action, x, y, 0)
            try {
                assertTrue("Tap $action at $x,$y; active=${InspectorController.isActive}; " +
                    "overlay=${overlay.width}x${overlay.height}; children=" +
                    views(overlay).take(5).map { "${it.javaClass.simpleName}:${it.width}x${it.height}:v=${it.visibility}" },
                    overlay.dispatchTouchEvent(event))
            } finally { event.recycle() }
        }
        val rows = views(overlay).filterIsInstance<TextView>().map { it.text.toString() }
        assertTrue("Unexpected selection: $rows", "Properties · ${node.label}" in rows)
        val density = overlay.resources.displayMetrics.density
        assertEquals(Geometry.formatPx(node.bounds.width, density), valueAfter(rows, "Width"))
        assertEquals(Geometry.formatPx(node.bounds.height, density), valueAfter(rows, "Height"))
        return rows
    }

    private fun <T : Activity> withScreen(type: Class<T>, check: (View) -> Unit) {
        val controller = Robolectric.buildActivity(type).setup().visible()
        val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
        try {
            val root = controller.get().window.decorView
            repeat(5) {
                root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 400, 800)
                root.draw(Canvas(bitmap))
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            }
            check(root)
        } finally {
            bitmap.recycle()
            controller.pause().stop().destroy()
        }
    }

    private fun valueAfter(rows: List<String>, label: String): String {
        val index = rows.indexOf(label)
        assertTrue("Missing $label: $rows", index >= 0 && index + 1 < rows.size)
        return rows[index + 1]
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
    } else emptyList()
}
