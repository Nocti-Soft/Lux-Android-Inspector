package com.noctisoft.layoutmeasurement.sample

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.noctisoft.layoutmeasurement.CapturedNode
import com.noctisoft.layoutmeasurement.InspectorController
import com.noctisoft.layoutmeasurement.InspectorInitializer
import com.noctisoft.layoutmeasurement.TextProperties
import com.noctisoft.layoutmeasurement.TextPropertyValue
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
class TextPropertiesComposeTest {
    @Before fun initialize() { InspectorInitializer().create(RuntimeEnvironment.getApplication()) }
    @After fun stop() { InspectorController.stopInspection() }

    @Test fun `real Compose Text exposes configured typography without custom annotations`() {
        capture({ Text("Typography", Modifier.testTag("target"), fontSize = 18.sp,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold,
            fontStyle = FontStyle.Italic, letterSpacing = 0.02.em) }) { nodes ->
            val properties = properties(nodes)
            assertEquals("18.0 sp · 18.0 px", properties.fontSize.displayText())
            assertEquals("monospace (configured)", properties.fontFamily.displayText())
            assertEquals("600 · SemiBold", properties.fontWeight.displayText())
            assertEquals("Italic", properties.fontStyle.displayText())
            assertEquals("0.020 em", properties.letterSpacing.displayText())
            assertNull(properties.origin)
        }
    }

    @Test fun `partial spans report Mixed for only the affected properties`() {
        val text = AnnotatedString("AB", listOf(AnnotatedString.Range(SpanStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold), 0, 1)))
        capture({ Text(text, Modifier.testTag("target"), fontSize = 16.sp, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Normal) }) { nodes ->
            val p = properties(nodes)
            assertEquals(TextPropertyValue.Mixed, p.fontSize)
            assertEquals(TextPropertyValue.Mixed, p.fontWeight)
            assertEquals("Normal", p.fontStyle.displayText())
        }
    }

    @Test fun `full-range span replaces base rather than adding an unused value`() {
        val text = AnnotatedString("AB", listOf(AnnotatedString.Range(SpanStyle(fontSize = 22.sp), 0, 2)))
        capture({ Text(text, Modifier.testTag("target"), fontSize = 16.sp) }) { nodes ->
            assertEquals("22.0 sp · 22.0 px", properties(nodes).fontSize.displayText())
        }
    }

    @Test fun `container descendant typography is explicitly attributed and can be mixed`() {
        capture({ Column(Modifier.testTag("target")) {
            Text("A", fontSize = 14.sp, fontFamily = FontFamily.Serif)
            Text("B", fontSize = 20.sp, fontFamily = FontFamily.Monospace)
        } }) { nodes ->
            val p = properties(nodes)
            assertEquals("Descendant text (2 nodes)", p.origin)
            assertEquals(TextPropertyValue.Mixed, p.fontSize)
            assertEquals(TextPropertyValue.Mixed, p.fontFamily)
        }
    }

    @Test fun `text semantics without layout reports unavailable not a guessed default`() {
        capture({ Column(Modifier.testTag("target").size(100.dp).semantics { text = AnnotatedString("Semantic text") }) {} }) { nodes ->
            assertTrue(properties(nodes).fontSize is TextPropertyValue.Unavailable)
        }
    }

    @Test fun `empty editable field exposes typography when layout is available`() {
        capture({ BasicTextField("", {}, Modifier.testTag("target"), textStyle = TextStyle(fontSize = 20.sp, fontFamily = FontFamily.Serif)) }) { nodes ->
            assertEquals("20.0 sp · 20.0 px", properties(nodes).fontSize.displayText())
        }
    }

    @Test fun `non-text Compose nodes do not fabricate text properties`() {
        capture({ Column(Modifier.testTag("target").size(100.dp)) {} }) { nodes ->
            assertNull(nodes.single { it.label == "target" }.textProperties)
        }
    }

    @Test fun `normal capture does not request typography snapshots`() {
        capture({ Text("Text", Modifier.testTag("target")) }, includeProperties = false) { nodes ->
            assertNull(nodes.single { it.label == "target" }.textProperties)
            assertNull(nodes.single { it.label == "target" }.colors)
        }
    }

    private fun properties(nodes: List<CapturedNode>): TextProperties {
        val node = nodes.single { it.label == "target" }
        assertNotNull("No text properties captured", node.textProperties)
        return node.textProperties!!
    }

    private fun capture(content: @Composable () -> Unit, includeProperties: Boolean = true, check: (List<CapturedNode>) -> Unit) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            controller.get().setContent(content = content)
            val root = controller.get().window.decorView
            val bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888)
            try {
                repeat(5) {
                    root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, 400, 800)
                    root.draw(Canvas(bitmap))
                    Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
                }
                check(ViewCapture.captureAll(root, includeProperties))
            } finally { bitmap.recycle() }
        } finally { controller.pause().stop().destroy() }
    }
}
