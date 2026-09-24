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
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt
import kotlin.math.hypot

/** A bounded native readout; only an explicit tap on a known code writes the clipboard. */
internal class ColorDetailsPanel(context: Context) : LinearLayout(context) {
    companion object {
        const val MAX_WIDTH_DP = 260
        const val MAX_HEIGHT_DP = 240
    }
    var onDismissRequested: () -> Unit = {}
    var onDragBy: (Float, Float) -> Unit = { _, _ -> }
    private val content = LinearLayout(context).apply { orientation = VERTICAL }
    private val scrollContent = ScrollView(context).apply {
        isFillViewport = false
        isClickable = true
    }
    private val dragHandle = DragHandle(context)
    private var maximumWidth = dp(MAX_WIDTH_DP)
    private var maximumHeight = dp(MAX_HEIGHT_DP)
    private var hasSelection = false

    init {
        visibility = GONE
        orientation = VERTICAL
        isClickable = true
        elevation = dp(8).toFloat()
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0xFFF7F7FF.toInt())
            setStroke(dp(1), 0xFF4F46E5.toInt())
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dragHandle, LayoutParams(0, dp(48), 1f))
            addView(TextView(context).apply {
                text = "×"
                textSize = 22f
                includeFontPadding = false
                gravity = Gravity.CENTER
                setTextColor(0xFF4F46E5.toInt())
                isClickable = true
                isFocusable = true
                contentDescription = "Dismiss properties"
                setOnClickListener { onDismissRequested() }
            }, LayoutParams(dp(48), dp(48)))
        }
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        scrollContent.addView(content, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scrollContent, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setMaximumSize(width: Int, height: Int) {
        maximumWidth = width.coerceIn(0, dp(MAX_WIDTH_DP))
        maximumHeight = height.coerceIn(0, dp(MAX_HEIGHT_DP))
        updateVisibility()
        requestLayout()
    }

    fun render(node: CapturedNode?) {
        content.removeAllViews()
        scrollContent.scrollTo(0, 0)
        dragHandle.cancelGesture()
        hasSelection = node != null
        updateVisibility()
        if (node == null) return
        content.addView(label("Properties · ${node.label}", 15f, true).apply {
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        content.addView(label("${node.source} · ARGB #AARRGGBB · tap a code to copy", 11f))
        node.textProperties?.let { text ->
            content.addView(label("Text", 14f, true).apply { setPadding(0, dp(12), 0, dp(2)) })
            text.origin?.let { content.addView(label(it, 11f, true)) }
            propertyRow("Font size", text.fontSize)
            propertyRow("Font family", text.fontFamily)
            propertyRow("Font weight", text.fontWeight)
            propertyRow("Font style", text.fontStyle)
            propertyRow("Letter spacing", text.letterSpacing)
        }
        content.addView(label("Colors", 14f, true).apply { setPadding(0, dp(12), 0, dp(2)) })
        val unavailable = ColorValue.Unavailable("Color snapshot unavailable")
        node.colors?.textOrigin?.let { content.addView(label(it, 11f, true)) }
        colorRow("Text color", node.colors?.text ?: unavailable)
        colorRow("Background color", node.colors?.background ?: unavailable)
        colorRow("Border color", node.colors?.border ?: unavailable)
        content.addView(label("Component properties, not composited pixels. Descendant text is labeled. Tap another component to refresh.", 11f))
    }

    private fun propertyRow(title: String, value: TextPropertyValue) {
        content.addView(label(title, 12f, true).apply { setPadding(0, dp(8), 0, dp(2)) })
        content.addView(label(value.displayText(), 13f))
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

    /** Only the fixed header owns movement; the body keeps native scrolling/copy gestures. */
    private inner class DragHandle(context: Context) : TextView(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var pointerId = MotionEvent.INVALID_POINTER_ID
        private var downX = 0f
        private var downY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var dragging = false

        init {
            text = "⠿  Properties"
            textSize = 15f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(0xFF25236D.toInt())
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            isClickable = true
            isFocusable = true
            contentDescription = "Move properties panel"
        }

        // Convert local pointer coordinates to the panel's parent, not the device display.
        // This stays stable as the panel moves and works inside inset dialog windows.
        private fun pointerPosition(event: MotionEvent, index: Int): Pair<Float, Float> {
            val header = parent as View
            return Pair(event.getX(index) + x + header.x + this@ColorDetailsPanel.x,
                event.getY(index) + y + header.y + this@ColorDetailsPanel.y)
        }

        private fun resetPointer(event: MotionEvent, index: Int) {
            pointerId = event.getPointerId(index)
            val (px, py) = pointerPosition(event, index)
            downX = px; downY = py
            lastX = px; lastY = py
        }

        private fun move(event: MotionEvent) {
            val index = event.findPointerIndex(pointerId)
            if (index < 0) { cancelGesture(); return }
            val (px, py) = pointerPosition(event, index)
            if (!dragging && hypot(px - downX, py - downY) <= touchSlop) return
            dragging = true
            onDragBy(px - lastX, py - lastY)
            lastX = px; lastY = py
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    resetPointer(event, 0)
                    parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> if (pointerId != MotionEvent.INVALID_POINTER_ID) move(event)
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointerId) {
                    val next = if (event.actionIndex == 0) 1 else 0
                    if (next < event.pointerCount) resetPointer(event, next) else cancelGesture()
                }
                MotionEvent.ACTION_UP -> {
                    if (pointerId != MotionEvent.INVALID_POINTER_ID) {
                        if (dragging) move(event) else performClick()
                    }
                    cancelGesture()
                }
                MotionEvent.ACTION_CANCEL -> cancelGesture()
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        fun cancelGesture() {
            pointerId = MotionEvent.INVALID_POINTER_ID
            dragging = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }

        override fun onDetachedFromWindow() {
            cancelGesture()
            super.onDetachedFromWindow()
        }
    }

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
