package com.noctisoft.layoutmeasurement.sample

import android.app.Application
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.noctisoft.layoutmeasurement.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class BottomSheetShowcaseTest {
    private lateinit var controller: ActivityController<BottomSheetShowcaseActivity>
    private lateinit var activity: BottomSheetShowcaseActivity

    @Before fun setUp() {
        InspectorInitializer().create(RuntimeEnvironment.getApplication())
        controller = Robolectric.buildActivity(BottomSheetShowcaseActivity::class.java).setup().visible()
        activity = controller.get()
        InspectorController.revealControls(RevealSource.SHAKE)
        settle()
    }
    @After fun tearDown() {
        controller.pause().stop().destroy()
        InspectorController.stopInspection()
        InspectorController.hideControlsForStartup()
    }

    @Test fun `XML bottom sheet hosts clickable inspector and captures View and Compose colors`() {
        activity.findViewById<View>(R.id.show_xml_bottom_sheet).performClick()
        settle()
        val sheet = activity.supportFragmentManager.findFragmentByTag("xml_showcase_sheet") as XmlShowcaseSheet
        val root = sheet.dialog!!.window!!.decorView as ViewGroup
        val overlay = overlay(root)
        assertNotNull("Inspector should follow real BottomSheetDialogFragment", overlay)
        assertNull(overlay(activity.window.decorView as ViewGroup))
        tap(root, find(overlay!!, "Layout inspector controls")!!)
        assertEquals(FloatingControlState.EXPANDED, InspectorController.controlState)
        find(overlay, "Colors")!!.performClick()
        val nodes = ViewCapture.captureAll(root, true)
        val xml = nodes.single { it.label.endsWith("/sheet_xml_colors") }
        assertEquals(ColorValue.Solid(0xFF25236D.toInt()), xml.colors!!.text)
        assertEquals(ColorValue.Solid(0xFFF1EAFE.toInt()), xml.colors!!.background)
        assertEquals(ColorValue.Solid(0xFF4F46E5.toInt()), xml.colors!!.border)
        val boxA = nodes.single { it.label.endsWith("/sheet_box_a") }
        val boxB = nodes.single { it.label.endsWith("/sheet_box_b") }
        val density = root.resources.displayMetrics.density
        assertEquals(24f, Geometry.horizontalGap(boxA.bounds, boxB.bounds) / density, 0.1f)
        assertEquals(100f, boxA.bounds.width / density, 0.1f)
        assertEquals(48f, boxA.bounds.height / density, 0.1f)
        val compose = nodes.single { it.label == "sheet_compose_colors" }
        assertEquals(Source.COMPOSE, compose.source)
        assertEquals(ColorValue.Solid(0xFF25236D.toInt()), compose.colors!!.text)
        assertTrue(nodes.none { it.label.endsWith("/show_xml_bottom_sheet") })
        tapNode(overlay, compose)
        assertNotNull(find(overlay, "Copy text color #FF25236D"))
        InspectorController.stopInspection()
        sheet.dismissNow()
        settle()
        assertNotNull(overlay(activity.window.decorView as ViewGroup))
    }

    @Test fun `Compose modal bottom sheet also receives the inspector without registration code`() {
        assertTrue(semantics("show_compose_sheet")!!.config[SemanticsActions.OnClick].action!!.invoke())
        settle()
        val root = WindowInspector.getGlobalWindowViews().filterIsInstance<ViewGroup>().single { candidate ->
            ViewCapture.captureAll(candidate).any { it.label == "modal_sheet_title" }
        }
        val overlay = overlay(root)
        assertNotNull(overlay)
        tap(root, find(overlay!!, "Layout inspector controls")!!)
        assertEquals(FloatingControlState.EXPANDED, InspectorController.controlState)
        find(overlay, "Colors")!!.performClick()
        val node = ViewCapture.captureAll(root, true).single { it.label == "modal_sheet_colors" }
        assertEquals(ColorValue.Solid(0xFF25236D.toInt()), node.colors!!.text)
        tapNode(overlay, node)
        assertNotNull(find(overlay, "Copy text color #FF25236D"))
        InspectorController.stopInspection()
        semantics("close_compose_sheet")!!.config[SemanticsActions.OnClick].action!!.invoke()
        settle()
        assertNotNull(overlay(activity.window.decorView as ViewGroup))
    }

    @Test fun `showcase launches from main sample screen`() {
        val main = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            main.get().findViewById<View>(R.id.open_bottom_sheets).performClick()
            val intent = shadowOf(main.get()).nextStartedActivity
            assertEquals(BottomSheetShowcaseActivity::class.java.name, intent.component!!.className)
        } finally { main.pause().stop().destroy() }
    }

    private fun settle() {
        repeat(5) {
            WindowInspector.getGlobalWindowViews().forEach { root ->
                root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 1080, 1920)
            }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(120))
        }
    }
    private fun overlay(root: ViewGroup): InspectorOverlay? =
        (0 until root.childCount).map(root::getChildAt).filterIsInstance<InspectorOverlay>().singleOrNull()
    private fun find(root: View, description: String): View? {
        if (root.contentDescription == description) return root
        if (root is ViewGroup) repeat(root.childCount) { find(root.getChildAt(it), description)?.let { return it } }
        return null
    }
    private fun semantics(tag: String): SemanticsNode? {
        fun node(n: SemanticsNode): SemanticsNode? {
            if (n.config.getOrNull(SemanticsProperties.TestTag) == tag) return n
            return n.children.firstNotNullOfOrNull(::node)
        }
        fun view(v: View): SemanticsNode? {
            (v as? RootForTest)?.semanticsOwner?.unmergedRootSemanticsNode?.let { node(it)?.let { found -> return found } }
            if (v is ViewGroup) repeat(v.childCount) { view(v.getChildAt(it))?.let { found -> return found } }
            return null
        }
        return WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::view)
    }
    private fun tapNode(overlay: InspectorOverlay, node: CapturedNode) {
        val canvas = overlay.getChildAt(0)
        val offset = IntArray(2).also(canvas::getLocationInWindow)
        sendTap(canvas, (node.bounds.left + node.bounds.width / 2 - offset[0]).toFloat(), (node.bounds.top + node.bounds.height / 2 - offset[1]).toFloat())
    }
    private fun tap(root: View, target: View) {
        val origin = IntArray(2).also(root::getLocationInWindow)
        val pos = IntArray(2).also(target::getLocationInWindow)
        sendTap(root, pos[0] - origin[0] + target.width / 2f, pos[1] - origin[1] + target.height / 2f)
    }
    private fun sendTap(view: View, x: Float, y: Float) {
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach {
            val event = MotionEvent.obtain(0, 10, it, x, y, 0)
            view.dispatchTouchEvent(event)
            event.recycle()
        }
    }
}
