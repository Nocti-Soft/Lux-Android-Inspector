package com.noctisoft.layoutmeasurement

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ColorDetailsPanelTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `known code is only copied after a user clicks the color row`() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("existing", "keep me"))
        val panel = ColorDetailsPanel(context)
        panel.render(node(ColorValue.Solid(0x80123456.toInt())))
        assertEquals("keep me", clipboard.primaryClip!!.getItemAt(0).text)
        val copy = find(panel, "Copy text color #80123456")!!
        assertTrue(copy.performClick())
        assertEquals("#80123456", clipboard.primaryClip!!.getItemAt(0).text)
    }

    @Test fun `unknown rows do not offer a copy action and clearing hides the panel`() {
        val panel = ColorDetailsPanel(context)
        panel.render(node(ColorValue.Unavailable("Custom drawing")))
        assertNull(find(panel, "Copy text color"))
        assertEquals(View.VISIBLE, panel.visibility)
        panel.render(null)
        assertEquals(View.GONE, panel.visibility)
        assertEquals(0, (panel.getChildAt(0) as ViewGroup).childCount)
    }

    @Test fun `long labels and multiple colors remain within bounded scrollable panel`() {
        val panel = ColorDetailsPanel(context)
        panel.setMaximumSize(220, 140)
        panel.render(node(ColorValue.Multiple(listOf(0xFF112233.toInt(), 0xFF445566.toInt()))).copy(label = "long_".repeat(100)))
        panel.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        assertTrue(panel.measuredWidth <= 220)
        assertTrue(panel.measuredHeight <= 140)
        assertTrue(panel.getChildAt(0).height > panel.height)
    }

    private fun node(text: ColorValue) = CapturedNode("title", Bounds(10, 10, 160, 60), Source.XML,
        ComponentColors(text, ColorValue.Solid(0), ColorValue.None))

    private fun find(view: View, description: String): View? {
        if (view.contentDescription == description) return view
        if (view is ViewGroup) repeat(view.childCount) { find(view.getChildAt(it), description)?.let { return it } }
        return null
    }
}
