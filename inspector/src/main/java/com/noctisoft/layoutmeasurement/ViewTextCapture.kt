package com.noctisoft.layoutmeasurement

import android.graphics.Typeface
import android.os.Build
import android.text.Spanned
import android.text.TextPaint
import android.text.style.*
import android.view.View
import android.widget.TextView
import androidx.core.util.TypedValueCompat

/** Reads public TextView/Paint APIs without changing the host or executing custom spans. */
internal object ViewTextCapture {
    fun capture(view: View): TextProperties? {
        if (view !is TextView) return null
        return try {
            captureText(view)
        } catch (_: RuntimeException) {
            TextProperties.unavailable("Text properties unavailable")
        } catch (_: LinkageError) {
            TextProperties.unavailable("Text API unavailable")
        }
    }

    private fun captureText(view: TextView): TextProperties {
        val text = view.text
        if (text !is Spanned || text.isEmpty()) return properties(view.paint, view)
        if (text.length > 16_384) return TextProperties.unavailable("Styled text exceeds inspection limit")
        val spans = text.getSpans(0, text.length, CharacterStyle::class.java)
        if (spans.size > 128) return TextProperties.unavailable("Too many text spans")
        if (spans.any { it.underlying.javaClass !in supportedSpans }) {
            return TextProperties.unavailable("Custom text span")
        }
        val points = (listOf(0, text.length) + spans.flatMap {
            listOf(text.getSpanStart(it).coerceIn(0, text.length), text.getSpanEnd(it).coerceIn(0, text.length))
        }).distinct().sorted()
        return TextProperties.combine(points.zipWithNext().map { (start, end) ->
            val paint = TextPaint(view.paint).apply { drawableState = view.drawableState }
            // Apply each known span exactly once; relative sizes must not be multiplied twice.
            text.getSpans(start, end, CharacterStyle::class.java).forEach { it.updateDrawState(paint) }
            properties(paint, view)
        })
    }

    private fun properties(paint: TextPaint, view: TextView): TextProperties {
        val typeface = paint.typeface ?: Typeface.DEFAULT
        val family = if (Build.VERSION.SDK_INT >= 34) {
            typeface.systemFontFamilyName?.takeIf { it.isNotBlank() }?.let {
                TextPropertyValue.Known("${it.take(128)} (system)")
            } ?: TextPropertyValue.Unavailable("Custom font family name not exposed")
        } else TextPropertyValue.Unavailable("System family name requires API 34")
        val weight = if (Build.VERSION.SDK_INT >= 28) {
            TextPropertyFormat.weight(typeface.weight, paint.isFakeBoldText)
        } else TextPropertyValue.Unavailable("Numeric weight requires API 28${if (typeface.isBold || paint.isFakeBoldText) "; bold style" else ""}")
        val style = when {
            typeface.isItalic -> "Italic"
            paint.textSkewX != 0f -> "Normal (synthetic skew ${paint.textSkewX})"
            else -> "Normal"
        }
        return TextProperties(
            // Core delegates to the nonlinear platform converter on API 34+.
            fontSize = TextPropertyFormat.size(TypedValueCompat.pxToSp(paint.textSize, view.resources.displayMetrics), paint.textSize),
            fontFamily = family,
            fontWeight = weight,
            fontStyle = TextPropertyValue.Known(style),
            letterSpacing = TextPropertyFormat.spacing(paint.letterSpacing, "em"),
        )
    }

    private val supportedSpans = setOf(
        ForegroundColorSpan::class.java, BackgroundColorSpan::class.java, TextAppearanceSpan::class.java,
        StyleSpan::class.java, TypefaceSpan::class.java, AbsoluteSizeSpan::class.java, RelativeSizeSpan::class.java,
        UnderlineSpan::class.java, StrikethroughSpan::class.java, SuperscriptSpan::class.java, SubscriptSpan::class.java,
        ScaleXSpan::class.java,
    )
}
