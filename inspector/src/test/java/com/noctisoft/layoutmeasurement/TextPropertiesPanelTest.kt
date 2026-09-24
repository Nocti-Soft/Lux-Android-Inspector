package com.noctisoft.layoutmeasurement

import android.app.Application
import android.content.res.Configuration
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextPropertiesPanelTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `properties menu replaces Colors without adding another menu item`() {
        val controls = FloatingInspectorControl(context)
        controls.setSafeArea(SafeArea(0, 0, 400, 800))
        controls.render(FloatingControlState.EXPANDED, FloatingPlacement(), MeasureMode.COLORS, true, true)
        val buttons = labels(controls)
        assertTrue("Properties menu missing: $buttons", "Properties" in buttons)
        assertFalse("Colors" in buttons)
        val properties = views(controls).single { it.contentDescription == "Properties" }
        var selected: MeasureMode? = null
        controls.onModeSelected = { selected = it }
        properties.performClick()
        assertEquals(MeasureMode.COLORS, selected)
    }

    @Test fun `TextView properties show current size typeface style and spacing while keeping colors`() {
        val view = textView().apply {
            typeface = Typeface.create(Typeface.MONOSPACE, 600, true)
            letterSpacing = 0.02f
            setTextColor(0xFF123456.toInt())
        }
        val rows = render(view)
        assertTrue(rows.toString(), "16.0 sp · 16.0 px" in rows)
        assertTrue(rows.toString(), "monospace (system)" in rows)
        assertTrue(rows.toString(), "600 · SemiBold" in rows)
        assertTrue(rows.toString(), "Italic" in rows)
        assertTrue(rows.toString(), "0.020 em" in rows)
        assertTrue(rows.toString(), "#FF123456" in rows)
        assertTrue(rows.any { it.startsWith("Properties ·") })
    }

    @Test fun `XML text properties show Colors immediately after the letter spacing value`() {
        assertColorsFollowLetterSpacing(Source.XML)
    }

    @Test fun `Compose text properties show Colors immediately after the letter spacing value`() {
        assertColorsFollowLetterSpacing(Source.COMPOSE)
    }

    @Test fun `non-text views omit typography rather than borrowing child or parent values`() {
        val view = View(context).apply { layout(0, 0, 100, 60); setBackgroundColor(0xFF112233.toInt()) }
        val rows = render(view)
        assertFalse("Font size" in rows)
        assertTrue("#FF112233" in rows)
        assertTrue(rows.any { it.startsWith("Properties ·") })
    }

    @Test fun `partial text size override is Mixed but unchanged style is not`() {
        val view = textView().apply {
            text = SpannableString("AB").apply {
                setSpan(RelativeSizeSpan(1.5f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val rows = render(view)
        assertEquals("Mixed", valueAfter(rows, "Font size"))
        assertEquals("Normal", valueAfter(rows, "Font style"))
    }

    @Test fun `span covering all text replaces the unused base size`() {
        val view = textView().apply {
            text = SpannableString("AB").apply {
                setSpan(AbsoluteSizeSpan(24, true), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val rows = render(view)
        assertEquals("24.0 sp · 24.0 px", valueAfter(rows, "Font size"))
        assertFalse("Mixed" in rows)
        assertEquals(16f, view.textSize, 0.001f)
    }

    @Test fun `capture is an immutable snapshot and never changes host styling`() {
        val view = textView()
        val node = ViewCapture.captureAll(view, true).single()
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        val panel = ColorDetailsPanel(context)
        panel.render(node)
        assertEquals("16.0 sp · 16.0 px", valueAfter(labels(panel), "Font size"))
        assertEquals("28.0 sp · 28.0 px", valueAfter(render(view), "Font size"))
        assertEquals(28f, view.textSize, 0.001f)
    }

    @Test fun `sp conversion uses the selected view font scale not the inspector context`() {
        val scaled = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 2f })
        val view = TextView(scaled).apply { text = "Large"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f); layout(0, 0, 200, 100) }
        val value = valueAfter(render(view), "Font size")
        assertTrue(value, value.startsWith("40.0 sp · "))
        val expectedPx = String.format(java.util.Locale.ROOT, "%.1f px", view.textSize)
        assertTrue(value, value.endsWith(expectedPx))
    }

    @Test fun `unknown custom span is not executed by the inspector or guessed as uniform`() {
        var calls = 0
        val view = textView().apply {
            text = SpannableString("AB").apply {
                setSpan(object : MetricAffectingSpan() {
                    override fun updateMeasureState(p: TextPaint) { calls++ }
                    override fun updateDrawState(p: TextPaint) { calls++ }
                }, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val beforeCapture = calls // TextView itself can run spans while setting or laying out text.
        assertTrue(valueAfter(render(view), "Font size").startsWith("Unavailable"))
        assertEquals("Inspector must not invoke a custom span", beforeCapture, calls)
    }

    @Test fun `oversized styled text reports unavailable without keeping content in the panel`() {
        val privateContent = "private content".repeat(1300)
        val view = textView().apply { text = SpannableString(privateContent) }
        val rows = render(view)
        assertTrue(valueAfter(rows, "Font size").startsWith("Unavailable"))
        assertFalse(rows.any { it.contains("private content") })
    }

    private fun assertColorsFollowLetterSpacing(source: Source) {
        val panel = ColorDetailsPanel(context)
        panel.render(CapturedNode(
            label = "Text target",
            bounds = Bounds(0, 0, 160, 60),
            source = source,
            textProperties = TextProperties(
                fontSize = TextPropertyValue.Known("16.0 sp · 16.0 px"),
                fontFamily = TextPropertyValue.Known("monospace"),
                fontWeight = TextPropertyValue.Known("400 · Normal"),
                fontStyle = TextPropertyValue.Known("Normal"),
                letterSpacing = TextPropertyValue.Known("0.020 em"),
            ),
        ))
        val rows = labels(panel)
        val spacingIndex = rows.indexOf("Letter spacing")
        assertTrue("Missing letter spacing: $rows", spacingIndex >= 0)
        assertEquals(
            "Colors should follow the letter spacing value without an explanatory note for $source",
            listOf("Letter spacing", "0.020 em", "Colors", "Text color"),
            rows.drop(spacingIndex).take(4),
        )
    }

    private fun textView() = TextView(context).apply {
        text = "AB"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT
        layout(0, 0, 160, 60)
    }
    private fun render(view: View): List<String> {
        val panel = ColorDetailsPanel(context)
        panel.render(ViewCapture.captureAll(view, true).single())
        return labels(panel)
    }
    private fun valueAfter(rows: List<String>, title: String): String {
        val index = rows.indexOf(title)
        assertTrue("Missing $title: $rows", index >= 0 && index + 1 < rows.size)
        return rows[index + 1]
    }
    private fun labels(view: View) = views(view).filterIsInstance<TextView>().map { it.text.toString() }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) }
    } else emptyList()
}
