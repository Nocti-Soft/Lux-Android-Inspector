package com.noctisoft.layoutmeasurement

import android.app.Application
import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "xhdpi", application = Application::class)
class ViewSizePropertiesPanelTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    @Config(sdk = [24, 28, 35])
    fun `XML view shows captured width and height in dp and px without typography`() {
        val view = View(context).apply { layout(27, 41, 268, 164) }
        val panel = ColorDetailsPanel(context)
        panel.render(ViewCapture.captureAll(view, includeColors = true).single())
        val rows = labels(panel)
        assertEquals("120.5dp (241px)", valueAfter(rows, "Width"))
        assertEquals("61.5dp (123px)", valueAfter(rows, "Height"))
        assertTrue(rows.indexOf("Size") < rows.indexOf("Colors"))
        assertFalse("Font size" in rows)
    }

    @Test fun `Compose snapshot uses the same bounds and formatting as the Size tool`() {
        val node = CapturedNode("compose_target", Bounds(-37, -15, 164, 88), Source.COMPOSE)
        val panel = ColorDetailsPanel(context)
        panel.render(node)
        val rows = labels(panel)
        assertEquals("100.5dp (201px)", valueAfter(rows, "Width"))
        assertEquals("51.5dp (103px)", valueAfter(rows, "Height"))
        assertEquals(Geometry.formatPx(node.bounds.width, context.resources.displayMetrics.density), valueAfter(rows, "Width"))
        assertEquals(Geometry.formatPx(node.bounds.height, context.resources.displayMetrics.density), valueAfter(rows, "Height"))
    }

    @Test
    @Config(qualifiers = "hdpi")
    fun `fractional dp retains the existing Size tool precision`() {
        val panel = ColorDetailsPanel(context)
        panel.render(CapturedNode("target", Bounds(0, 0, 200, 101), Source.XML))
        assertEquals("133.3dp (200px)", valueAfter(labels(panel), "Width"))
        assertEquals("67.3dp (101px)", valueAfter(labels(panel), "Height"))
    }

    @Test fun `font scaling does not change the dp dimensions`() {
        val scaled = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 2f })
        val panel = ColorDetailsPanel(scaled)
        panel.render(CapturedNode("target", Bounds(20, 30, 261, 153), Source.XML))
        assertEquals("120.5dp (241px)", valueAfter(labels(panel), "Width"))
        assertEquals("61.5dp (123px)", valueAfter(labels(panel), "Height"))
    }

    @Test fun `size precedes typography and Colors still immediately follows letter spacing`() {
        val panel = ColorDetailsPanel(context)
        panel.render(CapturedNode("text", Bounds(0, 0, 200, 100), Source.XML,
            textProperties = TextProperties(
                fontSize = TextPropertyValue.Known("16.0 sp · 32.0 px"),
                fontFamily = TextPropertyValue.Known("monospace"),
                fontWeight = TextPropertyValue.Known("400 · Normal"),
                fontStyle = TextPropertyValue.Known("Normal"),
                letterSpacing = TextPropertyValue.Known("0.020 em"),
            )))
        val rows = labels(panel)
        assertTrue("Missing Size section: $rows", rows.indexOf("Size") >= 0)
        assertEquals(listOf("Size", "Width", "100.0dp (200px)", "Height", "50.0dp (100px)", "Text"),
            rows.drop(rows.indexOf("Size")).take(6))
        assertEquals(listOf("Letter spacing", "0.020 em", "Colors", "Text color"),
            rows.drop(rows.indexOf("Letter spacing")).take(4))
    }

    @Test fun `size remains a snapshot until selecting the resized view again`() {
        val view = View(context).apply { layout(0, 0, 240, 120) }
        val snapshot = ViewCapture.captureAll(view, includeColors = true).single()
        val panel = ColorDetailsPanel(context)
        view.layout(0, 0, 300, 180)
        panel.render(snapshot)
        assertEquals("120.0dp (240px)", valueAfter(labels(panel), "Width"))
        panel.render(ViewCapture.captureAll(view, includeColors = true).single())
        val rows = labels(panel)
        assertEquals("150.0dp (300px)", valueAfter(rows, "Width"))
        assertEquals("90.0dp (180px)", valueAfter(rows, "Height"))
        assertEquals(1, rows.count { it == "Width" })
        assertEquals(1, rows.count { it == "Height" })
        assertFalse("120.0dp (240px)" in rows)
    }

    @Test fun `clearing selection removes size rows and hides the panel`() {
        val panel = ColorDetailsPanel(context)
        panel.render(CapturedNode("target", Bounds(0, 0, 200, 100), Source.XML))
        assertEquals("100.0dp (200px)", valueAfter(labels(panel), "Width"))
        panel.render(null)
        assertEquals(View.GONE, panel.visibility)
        assertFalse("Width" in labels(panel))
        assertFalse("Height" in labels(panel))
    }

    @Test fun `Size menu keeps its first position bounds and action when Properties is selected`() {
        val controls = FloatingInspectorControl(context)
        controls.setSafeArea(SafeArea(0, 0, 840, 1680))
        val placement = FloatingPlacement(0.5f, 0.5f)
        fun render(mode: MeasureMode) {
            controls.render(FloatingControlState.EXPANDED, placement, mode, true, true)
            controls.measure(View.MeasureSpec.makeMeasureSpec(840, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1680, View.MeasureSpec.EXACTLY))
            controls.layout(0, 0, 840, 1680)
        }
        render(MeasureMode.SIZE)
        val sizeButton = views(controls).single { it.contentDescription == "Size" }
        val menu = sizeButton.parent as ViewGroup
        fun menuLabels() = (0 until menu.childCount).map(menu::getChildAt)
            .filterIsInstance<Button>().map { it.text.toString() }
        fun sizeBounds() = Rect(0, 0, sizeButton.width, sizeButton.height).also {
            controls.offsetDescendantRectToMyCoords(sizeButton, it)
        }
        val before = sizeBounds()
        assertTrue(before.width() > 0 && before.height() > 0)
        assertEquals(listOf("Size", "Gap", "Ruler", "Bounds", "Properties", "Settings", "Stop Inspector"), menuLabels())
        render(MeasureMode.PROPERTIES)
        assertEquals(before, sizeBounds())
        assertSame(sizeButton, menu.getChildAt(0))
        var selected: MeasureMode? = null
        controls.onModeSelected = { selected = it }
        assertTrue(sizeButton.performClick())
        assertEquals(MeasureMode.SIZE, selected)
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
