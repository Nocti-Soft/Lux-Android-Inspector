package com.noctisoft.layoutmeasurement

import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
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
class ViewColorCaptureTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `TextView uses current disabled text color`() {
        val view = TextView(context).apply {
            setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(Color.GRAY, Color.RED)))
            isEnabled = false
        }
        assertEquals(ColorValue.Solid(Color.GRAY), ViewColorCapture.capture(view).text)
    }

    @Test fun `non text view and missing background are not a guessed black color`() {
        val colors = ViewColorCapture.capture(View(context))
        assertEquals(ColorValue.NotApplicable, colors.text)
        assertEquals(ColorValue.None, colors.background)
        assertEquals(ColorValue.None, colors.border)
    }

    @Test fun `transparent solid background retains its ARGB code`() {
        val view = View(context).apply { background = ColorDrawable(0x00123456) }
        assertEquals(ColorValue.Solid(0x00123456), ViewColorCapture.capture(view).background)
    }

    @Test fun `solid shape fill and stroke are read without altering the original drawable`() {
        val shape = GradientDrawable().apply {
            setColor(0xFF123456.toInt())
            setStroke(4, 0xFFABCDEF.toInt())
            cornerRadius = 12f
            bounds = Rect(7, 9, 127, 69)
        }
        val view = View(context).apply { background = shape; layout(0, 0, 120, 60) }
        val before = Rect(shape.bounds)
        val state = shape.state.clone()
        val colors = ViewColorCapture.capture(view)
        assertEquals(ColorValue.Solid(0xFF123456.toInt()), colors.background)
        assertEquals(ColorValue.Solid(0xFFABCDEF.toInt()), colors.border)
        assertEquals(before, shape.bounds)
        assertArrayEquals(state, shape.state)
        assertEquals(255, shape.alpha)
    }

    @Test fun `current selector drawable is used`() {
        val view = View(context).apply {
            background = StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled), ColorDrawable(Color.GRAY))
                addState(intArrayOf(), ColorDrawable(Color.GREEN))
            }
            isEnabled = false
        }
        assertEquals(ColorValue.Solid(Color.GRAY), ViewColorCapture.capture(view).background)
    }

    @Test fun `gradient fill is not invented as one color but solid stroke remains inspectable`() {
        val view = View(context).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.RED, Color.BLUE)).apply { setStroke(3, Color.GREEN) }
            layout(0, 0, 160, 60)
        }
        val colors = ViewColorCapture.capture(view)
        assertTrue(colors.background is ColorValue.Unavailable)
        assertEquals(ColorValue.Solid(Color.GREEN), colors.border)
    }

    @Test fun `foreground spans report colors for actual text ranges`() {
        val text = SpannableString("AB").apply { setSpan(ForegroundColorSpan(Color.BLUE), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        val view = TextView(context).apply { setTextColor(Color.RED); setText(text) }
        assertEquals(ColorValue.fromColors(listOf(Color.BLUE, Color.RED)), ViewColorCapture.capture(view).text)
    }

    @Test fun `normal captures remain lightweight while requested colors enrich the snapshot`() {
        val view = TextView(context).apply { setTextColor(Color.RED); layout(0, 0, 100, 50) }
        assertNull(ViewCapture.captureAll(view).single().colors)
        assertEquals(ColorValue.Solid(Color.RED), ViewCapture.captureAll(view, includeColors = true).single().colors?.text)
    }
}
