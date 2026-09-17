package com.noctisoft.layoutmeasurement.sample

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.View
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import com.noctisoft.layoutmeasurement.CapturedNode
import com.noctisoft.layoutmeasurement.ColorValue
import com.noctisoft.layoutmeasurement.InspectorController
import com.noctisoft.layoutmeasurement.InspectorInitializer
import com.noctisoft.layoutmeasurement.Source
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
@Config(sdk = [35], application = Application::class)
class ColorInspectionSampleTest {
    @Before fun initializeInspectorBeforeComposition() {
        InspectorInitializer().create(RuntimeEnvironment.getApplication())
        assertTrue(isDebugInspectorInfoEnabled)
    }

    @After fun stopInspector() = InspectorController.stopInspection()

    @Test fun `XML fixture captures text fill and stroke from actual sample resources`() {
        withNodes(MainActivity::class.java) { nodes ->
            val node = nodes.firstOrNull { it.label.endsWith("/color_sample") }
            assertNotNull("Missing XML color fixture", node)
            assertEquals(Source.XML, node!!.source)
            assertEquals(ColorValue.Solid(0xFF25236D.toInt()), node.colors!!.text)
            assertEquals(ColorValue.Solid(0xFFF1EAFE.toInt()), node.colors!!.background)
            assertEquals(ColorValue.Solid(0xFF4F46E5.toInt()), node.colors!!.border)
        }
    }

    @Test fun `real Compose text exposes all three properties without manual color metadata`() {
        withNodes(ComposeActivity::class.java) { nodes ->
            val node = nodes.firstOrNull { it.label == "compose_colors" }
            assertNotNull("Missing Compose color fixture; captured ${nodes.map { it.label }}", node)
            assertEquals(Source.COMPOSE, node!!.source)
            assertEquals(ColorValue.Solid(0xFF25236D.toInt()), node.colors!!.text)
            assertEquals(ColorValue.Solid(0xFFF1EAFE.toInt()), node.colors!!.background)
            assertEquals(ColorValue.Solid(0xFF4F46E5.toInt()), node.colors!!.border)
            assertTrue(nodes.any { it.label == "compose_button" })
            assertFalse(nodes.any { it.source == Source.COMPOSE && it.label.contains("Untagged") })
        }
    }

    @Test fun `mixed screen preserves XML capture and reads tagged Compose island colors`() {
        withNodes(MixedActivity::class.java) { nodes ->
            val node = nodes.single { it.label == "island_a" }
            assertEquals(ColorValue.Solid(0xFFFFFFFF.toInt()), node.colors!!.text)
            assertEquals(ColorValue.Solid(0xFF25236D.toInt()), node.colors!!.background)
            assertEquals(ColorValue.Solid(0xFF4F46E5.toInt()), node.colors!!.border)
            assertTrue(nodes.any { it.source == Source.XML && it.label.endsWith("/xml_button") })
        }
    }

    @Test fun `tagged button reports its child text with explicit provenance`() {
        withNodes(ComposeActivity::class.java) { nodes ->
            val button = nodes.single { it.label == "compose_button" }
            assertTrue("Button text must not be reported as absent", button.colors!!.text is ColorValue.Solid)
            assertEquals("Descendant text (1 node)", button.colors!!.textOrigin)
            val ownText = nodes.single { it.label == "compose_colors" }
            assertNull(ownText.colors!!.textOrigin)
        }
    }

    private fun <T : Activity> withNodes(activityClass: Class<T>, check: (List<CapturedNode>) -> Unit) {
        val controller = Robolectric.buildActivity(activityClass).setup().visible()
        try {
            val decor = controller.get().window.decorView
            repeat(3) {
                decor.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
                decor.layout(0, 0, 1080, 1920)
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            }
            check(ViewCapture.captureAll(decor, includeColors = true))
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
