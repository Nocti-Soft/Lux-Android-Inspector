package com.noctisoft.layoutmeasurement

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/** A bounded native readout; only an explicit tap on a known code writes the clipboard. */
internal class ColorDetailsPanel(context: Context) : ScrollView(context) {
    var onDismissRequested: () -> Unit = {}
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var maximumWidth = Int.MAX_VALUE
    private var maximumHeight = Int.MAX_VALUE
    private var hasSelection = false

    init {
        visibility = GONE
        isFillViewport = false
        isClickable = true
        elevation = dp(8).toFloat()
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0xFFF7F7FF.toInt())
            setStroke(dp(1), 0xFF4F46E5.toInt())
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setMaximumSize(width: Int, height: Int) {
        maximumWidth = width.coerceAtLeast(0)
        maximumHeight = height.coerceAtLeast(0)
        updateVisibility()
        requestLayout()
    }

    fun render(node: CapturedNode?) {
        content.removeAllViews()
        scrollTo(0, 0)
        hasSelection = node != null
        updateVisibility()
        if (node == null) return
        content.addView(TextView(context).apply {
            text = "Close"
            textSize = 14f
            setTextColor(0xFF4F46E5.toInt())
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            minimumHeight = dp(48)
            setPadding(dp(8), 0, dp(8), 0)
            isClickable = true
            isFocusable = true
            contentDescription = "Dismiss color details"
            setOnClickListener { onDismissRequested() }
        }, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        content.addView(label("Colors · ${node.label}", 15f, true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        content.addView(label("${node.source} · ARGB #AARRGGBB · tap a code to copy", 11f))
        val unavailable = ColorValue.Unavailable("Color snapshot unavailable")
        node.colors?.textOrigin?.let { content.addView(label(it, 11f, true)) }
        colorRow("Text color", node.colors?.text ?: unavailable)
        colorRow("Background color", node.colors?.background ?: unavailable)
        colorRow("Border color", node.colors?.border ?: unavailable)
        content.addView(label("Component properties, not composited pixels. Descendant text is labeled. Tap another component to refresh.", 11f))
    }

    private fun colorRow(title: String, value: ColorValue) {
        content.addView(label(title, 12f, true).apply { setPadding(0, dp(10), 0, dp(2)) })
        val colors = when (value) {
            is ColorValue.Solid -> listOf(value.argb)
            is ColorValue.Multiple -> value.argb
            else -> emptyList()
        }
        if (colors.isNotEmpty()) {
            if (colors.size > 1) content.addView(label("Multiple colors / layers", 11f))
            colors.take(128).forEach { argb ->
                val code = ColorCode.format(argb)
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(48)
                    setPadding(dp(4), 0, dp(4), 0)
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Copy ${title.lowercase()} $code"
                    setOnClickListener {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText(title, code))
                            Toast.makeText(context, "Copied $code", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                row.addView(ColorSwatch(context, argb), LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
                row.addView(label(code, 15f).apply {
                    typeface = Typeface.MONOSPACE
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                content.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }
        } else {
            val message = when (value) {
                ColorValue.None -> if (title == "Border color") "No visible border" else "None on this component"
                ColorValue.NotApplicable -> "Not applicable to this node"
                is ColorValue.Unavailable -> "Unavailable · ${value.reason}"
                else -> "No color values"
            }
            content.addView(label(message, 13f))
        }
    }

    private fun label(text: String, size: Float, bold: Boolean = false) = TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(0xFF25236D.toInt())
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = true
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun updateVisibility() {
        visibility = if (hasSelection && maximumWidth > 0 && maximumHeight > 0) VISIBLE else GONE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(capped(widthMeasureSpec, maximumWidth), capped(heightMeasureSpec, maximumHeight))
    }

    private fun capped(spec: Int, maximum: Int): Int {
        val mode = MeasureSpec.getMode(spec)
        val size = if (mode == MeasureSpec.UNSPECIFIED) maximum else MeasureSpec.getSize(spec).coerceAtMost(maximum)
        return MeasureSpec.makeMeasureSpec(size, if (mode == MeasureSpec.UNSPECIFIED) MeasureSpec.AT_MOST else mode)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private class ColorSwatch(context: Context, private val argb: Int) : View(context) {
        private val paint = Paint()
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onDraw(canvas: Canvas) {
            val step = (6 * resources.displayMetrics.density).coerceAtLeast(1f)
            var y = 0f
            var row = 0
            while (y < height) {
                var x = 0f
                var col = 0
                while (x < width) {
                    paint.color = if ((row + col) % 2 == 0) Color.WHITE else 0xFFBBBBBB.toInt()
                    canvas.drawRect(x, y, (x + step).coerceAtMost(width.toFloat()), (y + step).coerceAtMost(height.toFloat()), paint)
                    x += step
                    col++
                }
                y += step
                row++
            }
            paint.color = argb
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.color = 0xFF77758F.toInt()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = resources.displayMetrics.density
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.style = Paint.Style.FILL
        }
    }
}
