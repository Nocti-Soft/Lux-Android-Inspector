package com.noctisoft.layoutmeasurement

import android.app.Application
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ColorInspectionEdgeCaseTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `custom drawable wrapper does not masquerade as its wrapped solid color`() {
        val view = View(context).apply {
            background = object : DrawableWrapper(ColorDrawable(Color.RED)) {
                override fun draw(canvas: Canvas) { canvas.drawColor(Color.BLUE) }
            }
        }
        val colors = ViewColorCapture.capture(view)
        assertTrue(colors.background is ColorValue.Unavailable)
    }

    @Test fun `custom span subclass cannot execute arbitrary update code during inspection`() {
        val text = SpannableString("AB").apply {
            setSpan(object : ForegroundColorSpan(Color.RED) {
                override fun updateDrawState(tp: TextPaint) { error("Custom span must not execute in inspector") }
            }, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val view = TextView(context).apply { setText(text) }
        assertTrue(ViewColorCapture.capture(view).text is ColorValue.Unavailable)
    }

    @Test fun `shape opacity does not produce inconsistent fill and stroke alpha`() {
        val view = View(context).apply {
            background = GradientDrawable().apply {
                setColor(Color.RED)
                setStroke(3, Color.BLUE)
                alpha = 128
            }
            layout(0, 0, 150, 60)
        }
        val colors = ViewColorCapture.capture(view)
        assertTrue(colors.background is ColorValue.Unavailable)
        assertTrue(colors.border is ColorValue.Unavailable)
    }

    @Test fun `shape color filter never reports unfiltered fill or stroke as an exact color`() {
        val view = View(context).apply {
            background = GradientDrawable().apply {
                setColor(0x80112233.toInt())
                setStroke(3, 0x80112233.toInt())
                colorFilter = PorterDuffColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN)
            }
            layout(0, 0, 150, 60)
        }
        val colors = ViewColorCapture.capture(view)
        assertTrue(colors.background is ColorValue.Unavailable)
        assertTrue(colors.border is ColorValue.Unavailable)
    }

    @Test fun `shape tint applied through a drawing layer remains explicitly unavailable`() {
        val view = View(context).apply {
            background = GradientDrawable().apply {
                setColor(0x80112233.toInt())
                setStroke(3, 0x80112233.toInt())
                setTint(Color.GREEN)
            }
            layout(0, 0, 150, 60)
        }
        val colors = ViewColorCapture.capture(view)
        assertTrue(colors.background is ColorValue.Unavailable)
        assertTrue(colors.border is ColorValue.Unavailable)
    }

    @Test fun `details can be dismissed so components behind the panel can be selected`() {
        val panel = ColorDetailsPanel(context)
        var dismissed = false
        panel.onDismissRequested = { dismissed = true; panel.render(null) }
        panel.render(CapturedNode("Text", Bounds(0, 0, 100, 50), Source.XML,
            ComponentColors(ColorValue.Solid(Color.RED), ColorValue.None, ColorValue.None)))
        val dismiss = find(panel, "Dismiss color details")
        assertNotNull(dismiss)
        dismiss!!.performClick()
        assertTrue(dismissed)
        assertEquals(View.GONE, panel.visibility)
    }

    @Test
    @Config(sdk = [28], application = Application::class)
    fun `older native Android rendering captures a standard shape without hidden framework fields`() {
        val view = View(context).apply {
            background = GradientDrawable().apply { setColor(Color.RED); setStroke(2, Color.BLUE) }
            layout(0, 0, 150, 60)
        }
        val colors = ViewColorCapture.capture(view)
        assertEquals(ColorValue.Solid(Color.RED), colors.background)
        assertEquals(ColorValue.Solid(Color.BLUE), colors.border)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    @Config(sdk = [24], application = Application::class)
    fun `minimum API captures public text and solid background properties`() {
        // Robolectric LEGACY paints on API24 return alpha=0 even for opaque blue;
        // use native API28 and API35 tests for the drawable drawing/stroke oracle.
        val view = TextView(context).apply {
            setTextColor(Color.RED)
            background = ColorDrawable(Color.BLUE)
        }
        val colors = ViewColorCapture.capture(view)
        assertEquals(ColorValue.Solid(Color.RED), colors.text)
        assertEquals(ColorValue.Solid(Color.BLUE), colors.background)
    }

    private fun find(view: View, description: String): View? {
        if (view.contentDescription == description) return view
        if (view is ViewGroup) repeat(view.childCount) { find(view.getChildAt(it), description)?.let { return it } }
        return null
    }
}
