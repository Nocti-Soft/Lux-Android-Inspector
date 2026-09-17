package com.noctisoft.layoutmeasurement

import android.text.Spanned
import android.text.TextPaint
import android.text.style.*
import android.view.View
import android.widget.TextView

internal object ViewColorCapture {
    fun capture(view: View): ComponentColors {
        val drawable = DrawableColorCapture.capture(view.background, view.resources, view.width, view.height)
        val text = try {
            textColor(view)
        } catch (_: RuntimeException) {
            ColorValue.Unavailable("Text inspection failed")
        } catch (_: LinkageError) {
            ColorValue.Unavailable("Text API unavailable")
        }
        return ComponentColors(text, drawable.background, drawable.border)
    }

    private fun textColor(view: View): ColorValue {
        if (view !is TextView) return ColorValue.NotApplicable
        if (view.paint.shader != null) return ColorValue.Unavailable("Text uses a shader")
        val text = view.text
        if (text !is Spanned || text.isEmpty()) return ColorValue.Solid(view.currentTextColor)
        if (text.length > 16_384) return ColorValue.Unavailable("Styled text exceeds inspection limit")
        val spans = text.getSpans(0, text.length, CharacterStyle::class.java)
        if (spans.size > 128) return ColorValue.Unavailable("Too many text spans")
        if (spans.any { !supportedSpan(it.underlying) }) return ColorValue.Unavailable("Custom text span")
        val points = (listOf(0, text.length) + spans.flatMap {
            listOf(text.getSpanStart(it).coerceIn(0, text.length), text.getSpanEnd(it).coerceIn(0, text.length))
        }).distinct().sorted()
        val colors = points.zipWithNext().mapNotNull { (start, end) ->
            if (start == end) return@mapNotNull null
            val paint = TextPaint(view.paint).apply {
                color = view.currentTextColor
                drawableState = view.drawableState
            }
            text.getSpans(start, end, CharacterStyle::class.java).forEach { it.updateDrawState(paint) }
            paint.color
        }
        return ColorValue.fromColors(colors)
    }

    private fun supportedSpan(span: CharacterStyle): Boolean = span.javaClass in supportedSpanTypes

    private val supportedSpanTypes = setOf(
        ForegroundColorSpan::class.java, BackgroundColorSpan::class.java, TextAppearanceSpan::class.java,
        StyleSpan::class.java, TypefaceSpan::class.java, AbsoluteSizeSpan::class.java, RelativeSizeSpan::class.java,
        UnderlineSpan::class.java, StrikethroughSpan::class.java, SuperscriptSpan::class.java, SubscriptSpan::class.java,
        ScaleXSpan::class.java,
    )
}
