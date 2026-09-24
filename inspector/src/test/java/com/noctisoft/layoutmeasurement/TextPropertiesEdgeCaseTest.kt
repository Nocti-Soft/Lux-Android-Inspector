package com.noctisoft.layoutmeasurement

import android.app.Application
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
class TextPropertiesEdgeCaseTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    @Config(sdk = [28, 33])
    fun `older Android still shows size and style but does not invent unavailable family names or weights`() {
        val view = TextView(context).apply { textSize = 16f; typeface = Typeface.DEFAULT; letterSpacing = 0.03f }
        val p = ViewTextCapture.capture(view)!!
        assertTrue(p.toString(), p.fontSize.displayText().startsWith("16.0 sp ·"))
        assertEquals("Normal", p.fontStyle.displayText())
        assertEquals("0.030 em", p.letterSpacing.displayText())
        assertTrue(p.fontFamily is TextPropertyValue.Unavailable)
        if (Build.VERSION.SDK_INT < 28) assertTrue(p.fontWeight is TextPropertyValue.Unavailable)
        else assertEquals("400 · Normal", p.fontWeight.displayText())
    }

    @Test
    @Config(sdk = [24])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun `API24 reads plain text properties without newer font APIs`() {
        val view = TextView(context).apply { textSize = 16f; typeface = Typeface.DEFAULT; letterSpacing = 0.03f }
        val p = ViewTextCapture.capture(view)!!
        assertEquals("16.0 sp · 16.0 px", p.fontSize.displayText())
        assertEquals("Normal", p.fontStyle.displayText())
        assertEquals("0.030 em", p.letterSpacing.displayText())
        assertTrue(p.fontFamily is TextPropertyValue.Unavailable)
        assertTrue(p.fontWeight is TextPropertyValue.Unavailable)
    }

    @Test fun `different system typeface spans report a mixed family and weight without changing size`() {
        val view = TextView(context).apply {
            textSize = 16f
            typeface = Typeface.MONOSPACE
            text = SpannableString("AB").apply {
                setSpan(TypefaceSpan("serif"), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(StyleSpan(Typeface.BOLD), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val p = ViewTextCapture.capture(view)!!
        assertEquals(TextPropertyValue.Mixed, p.fontFamily)
        assertEquals(TextPropertyValue.Mixed, p.fontWeight)
        assertEquals("16.0 sp · 16.0 px", p.fontSize.displayText())
    }

    @Test fun `synthetic bold is labeled rather than misreported as actual weight 700`() {
        val view = TextView(context).apply { typeface = Typeface.DEFAULT; paint.isFakeBoldText = true }
        assertEquals("400 · Normal (synthetic bold)", ViewTextCapture.capture(view)!!.fontWeight.displayText())
    }

    @Test fun `custom Compose font identity is unavailable while its configured size is readable`() {
        // A resource-backed family is metadata here: the inspector must not load it or inspect private fields.
        val p = ComposeTextCapture.textStyles(AnnotatedString("A"),
            TextStyle(fontSize = 16.sp, fontFamily = FontFamily(Font(123))), Density(1f), LayoutDirection.Ltr)
        assertTrue(p.fontFamily is TextPropertyValue.Unavailable)
        assertEquals("16.0 sp · 16.0 px", p.fontSize.displayText())
    }

    @Test fun `relative Compose font size is unavailable instead of treated as sp`() {
        val p = ComposeTextCapture.textStyles(AnnotatedString("A"), TextStyle(fontSize = 1.5.em), Density(1f), LayoutDirection.Ltr)
        assertTrue(p.fontSize is TextPropertyValue.Unavailable)
        assertEquals("Default (configured)", p.fontFamily.displayText())
    }

    @Test fun `autosized TextView reports the current paint size instead of the maximum setting`() {
        val view = TextView(context).apply {
            text = "A very long single line that must shrink"
            maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(12, 24, 1, TypedValue.COMPLEX_UNIT_SP)
            measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
            layout(0, 0, 120, 40)
        }
        assertTrue(view.textSize < 24f)
        val p = ViewTextCapture.capture(view)!!
        assertEquals(TextPropertyFormat.size(view.textSize, view.textSize), p.fontSize)
    }

    @Test fun `geometry capture keeps text properties empty and full snapshots contain no user text`() {
        val view = TextView(context).apply { text = "not-to-be-retained"; layout(0, 0, 100, 50) }
        assertNull(ViewCapture.captureAll(view).single().textProperties)
        val node = ViewCapture.captureAll(view, true).single()
        assertNotNull(node.textProperties)
        assertFalse(node.toString().contains("not-to-be-retained"))
    }
}
