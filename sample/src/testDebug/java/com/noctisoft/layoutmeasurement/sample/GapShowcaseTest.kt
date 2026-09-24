package com.noctisoft.layoutmeasurement.sample

import android.app.Application
import android.os.Looper
import android.view.View
import com.noctisoft.layoutmeasurement.CapturedNode
import com.noctisoft.layoutmeasurement.InspectorController
import com.noctisoft.layoutmeasurement.InspectorInitializer
import com.noctisoft.layoutmeasurement.ViewCapture
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class GapShowcaseTest {
    @After fun reset() {
        InspectorController.stopInspection()
        InspectorController.hideControlsForStartup()
    }

    @Test fun `gap showcase launches from sample home`() {
        val main = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            main.get().findViewById<View>(R.id.open_gap_showcase).performClick()
            assertEquals(GapShowcaseActivity::class.java.name, shadowOf(main.get()).nextStartedActivity.component!!.className)
        } finally { main.pause().stop().destroy() }
    }

    @Test fun `dedicated screen contains selectable XML and Compose parent child examples`() = verifyExamples()

    @Test
    @Config(sdk = [35], qualifiers = "ldrtl", application = Application::class)
    fun `physical left right example distances remain stable in RTL`() = verifyExamples()

    private fun verifyExamples() {
        InspectorInitializer().create(RuntimeEnvironment.getApplication())
        val controller = Robolectric.buildActivity(GapShowcaseActivity::class.java).setup().visible()
        try {
            val decor = controller.get().window.decorView
            repeat(5) {
                decor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
                decor.layout(0, 0, 1080, 2400)
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            }
            val nodes = ViewCapture.captureAll(decor)
            assertFixture(nodes, "gap_parent_xml", "gap_inner_xml", decor.resources.displayMetrics.density)
            assertFixture(nodes, "gap_parent_compose", "gap_inner_compose", decor.resources.displayMetrics.density)
        } finally { controller.pause().stop().destroy() }
    }

    private fun assertFixture(nodes: List<CapturedNode>, parent: String, child: String, density: Float) {
        fun find(name: String) = nodes.single { it.label == name || it.label.endsWith("/$name") }.bounds
        val outer = find(parent)
        val inner = find(child)
        listOf(inner.left - outer.left, inner.top - outer.top, outer.right - inner.right, outer.bottom - inner.bottom)
            .zip(listOf(24f, 32f, 40f, 48f)).forEach { (px, dp) -> assertEquals(dp, px / density, 0.5f) }
        val parentHit = com.noctisoft.layoutmeasurement.Geometry.pickAt(nodes, outer.left + 4, outer.top + 6)
        assertNotNull(parentHit)
        assertTrue(parentHit!!.label == parent || parentHit.label.endsWith("/$parent"))
        val childHit = com.noctisoft.layoutmeasurement.Geometry.pickAt(nodes, inner.left + inner.width / 2, inner.top + inner.height / 2)
        assertNotNull(childHit)
        assertTrue(childHit!!.label == child || childHit.label.endsWith("/$child"))
    }
}
