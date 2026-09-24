package com.noctisoft.layoutmeasurement.sample

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
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
@Config(sdk = [35], qualifiers = "w393dp-h873dp-xxhdpi", application = Application::class)
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
        find(overlay, "Properties")!!.performClick()
        val nodes = ViewCapture.captureAll(root, true)
        val xml = nodes.single { it.label.endsWith("/sheet_xml_colors") }
        assertEquals(ColorValue.Solid(0xFF25236D.toInt()), xml.colors!!.text)
        assertEquals(ColorValue.Solid(0xFFF1EAFE.toInt()), xml.colors!!.background)
        assertEquals(ColorValue.Solid(0xFF4F46E5.toInt()), xml.colors!!.border)
        assertTypography(xml, "monospace (system)")
        val boxA = nodes.single { it.label.endsWith("/sheet_box_a") }
        val boxB = nodes.single { it.label.endsWith("/sheet_box_b") }
        val density = root.resources.displayMetrics.density
        assertEquals(24f, Geometry.horizontalGap(boxA.bounds, boxB.bounds) / density, 0.1f)
        assertEquals(100f, boxA.bounds.width / density, 0.1f)
        assertEquals(48f, boxA.bounds.height / density, 0.1f)
        val compose = nodes.single { it.label == "sheet_compose_colors" }
        assertEquals(Source.COMPOSE, compose.source)
        assertTypography(compose, "monospace (configured)")
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
        val roots = WindowInspector.getGlobalWindowViews().filterIsInstance<ViewGroup>()
        val matches = roots.filter { candidate -> ViewCapture.captureAll(candidate).any { it.label == "modal_sheet_title" } }
        assertEquals("Expected one visible modal sheet root", 1, matches.size)
        val root = matches.single()
        val overlay = overlay(root)
        assertNotNull(overlay)
        tap(root, find(overlay!!, "Layout inspector controls")!!)
        assertEquals(FloatingControlState.EXPANDED, InspectorController.controlState)
        find(overlay, "Properties")!!.performClick()
        val node = ViewCapture.captureAll(root, true).single { it.label == "modal_sheet_colors" }
        assertEquals(ColorValue.Solid(0xFF25236D.toInt()), node.colors!!.text)
        assertTypography(node, "monospace (configured)")
        tapNode(overlay, node)
        assertNotNull(find(overlay, "Copy text color #FF25236D"))
        assertContainedFixture(root, "gap_parent_compose", "gap_inner_compose")
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

    @Test fun `XML sheet demonstrates measured distances between inner view and parent border`() {
        activity.findViewById<View>(R.id.show_xml_bottom_sheet).performClick()
        settle()
        val sheet = activity.supportFragmentManager.findFragmentByTag("xml_showcase_sheet") as XmlShowcaseSheet
        assertContainedFixture(sheet.dialog!!.window!!.decorView as ViewGroup, "gap_parent_xml", "gap_inner_xml")
    }

    private fun assertContainedFixture(root: ViewGroup, parentLabel: String, innerLabel: String) {
        InspectorController.stopInspection()
        if (parentLabel == "gap_parent_compose") {
            scrollComposeSheetToEnd(root)
        } else {
            val parent = root.findViewById<View>(R.id.gap_parent_xml)
            parent.requestRectangleOnScreen(Rect(0, 0, parent.width, parent.height), true)
        }
        settle()
        InspectorController.selectMode(MeasureMode.GAP)
        settle() // The canvas was GONE until the tool was activated.
        val nodes = ViewCapture.captureAll(root)
        val outer = nodes.firstOrNull { it.label == parentLabel || it.label.endsWith("/$parentLabel") }
        val inner = nodes.firstOrNull { it.label == innerLabel || it.label.endsWith("/$innerLabel") }
        assertNotNull("Missing parent edge-distance sample", outer)
        assertNotNull("Missing inner edge-distance sample", inner)
        val a = outer!!.bounds
        val b = inner!!.bounds
        val density = root.resources.displayMetrics.density
        val distances = listOf(b.left - a.left, b.top - a.top, a.right - b.right, a.bottom - b.bottom)
        distances.zip(listOf(24f, 32f, 40f, 48f)).forEach { (px, dp) -> assertEquals(dp, px / density, 0.5f) }
        val canvas = overlay(root)!!.getChildAt(0)
        val offset = IntArray(2).also(canvas::getLocationInWindow)
        val childTap = Pair((b.left + b.width / 2 - offset[0]).toFloat(), (b.top + b.height / 2 - offset[1]).toFloat())
        val parentTap = Pair(a.left - offset[0] + 4 * density, a.top - offset[1] + 6 * density)
        for (taps in listOf(listOf(childTap, parentTap), listOf(parentTap, childTap))) {
            InspectorController.stopInspection()
            InspectorController.selectMode(MeasureMode.GAP)
            settle()
            taps.forEach { (x, y) ->
                assertTrue("Tap target must be visible", x >= 0 && y >= 0 && x < canvas.width && y < canvas.height)
                sendTap(canvas, x, y)
            }
            val labels = mutableListOf<String>()
            val bitmap = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888)
            canvas.draw(object : Canvas(bitmap) {
                override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
                    labels += text
                    super.drawText(text, x, y, paint)
                }
            })
            listOf("Left", "Top", "Right", "Bottom").zip(distances).forEach { (side, px) ->
                assertTrue(labels.toString(), labels.contains("$side: ${Geometry.formatPx(px, density)}"))
            }
            assertFalse(labels.any { it.contains("overlapping") })
            bitmap.recycle()
        }
    }

    private fun assertTypography(node: CapturedNode, family: String) {
        val p = node.textProperties
        assertNotNull("Missing typography on ${node.label}", p)
        assertTrue(p!!.fontSize.displayText(), p.fontSize.displayText().startsWith("18.0 sp ·"))
        assertEquals(family, p.fontFamily.displayText())
        assertEquals("700 · Bold", p.fontWeight.displayText())
        assertEquals("Italic", p.fontStyle.displayText())
        assertEquals("0.020 em", p.letterSpacing.displayText())
    }

    private fun scrollComposeSheetToEnd(root: View) {
        fun node(n: SemanticsNode): Boolean {
            val action = n.config.getOrNull(SemanticsActions.ScrollBy)?.action
            if (action != null) return action(0f, 10000f)
            return n.children.any(::node)
        }
        fun view(v: View): Boolean {
            (v as? RootForTest)?.semanticsOwner?.unmergedRootSemanticsNode?.let { if (node(it)) return true }
            if (v is ViewGroup) return (0 until v.childCount).any { view(v.getChildAt(it)) }
            return false
        }
        assertTrue("Expected the sheet's real Compose scroll action", view(root))
    }

    private fun settle() {
        repeat(5) {
            WindowInspector.getGlobalWindowViews().forEach { root ->
                // Match the actual simulated display, not an unrelated hardcoded viewport.
                val metrics = root.resources.displayMetrics
                root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
                // A real frame includes drawing: Compose completes pending layout during this pass.
                val frame = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(frame))
                frame.recycle()
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
