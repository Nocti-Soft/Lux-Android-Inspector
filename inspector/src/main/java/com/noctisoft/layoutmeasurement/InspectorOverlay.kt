package com.noctisoft.layoutmeasurement

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Transparent full-window overlay. Pass-through when inspector inactive.
 * Active: shows a mode toolbar and a drawing canvas handling measurement touch.
 */
class InspectorOverlay(context: Context) : FrameLayout(context) {

    companion object {
        const val TAG = "com.noctisoft.layoutmeasurement.OVERLAY"
    }

    private val canvas = MeasureCanvas(context)
    private val toolbar = buildToolbar(context)
    private val controllerListener: () -> Unit = { post { sync() } }

    init {
        tag = TAG
        setWillNotDraw(true)
        addView(canvas, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(toolbar, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = (16 * resources.displayMetrics.density).toInt()
        })
        sync()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        InspectorController.addListener(controllerListener)
        sync()
    }

    override fun onDetachedFromWindow() {
        InspectorController.removeListener(controllerListener)
        super.onDetachedFromWindow()
    }

    private fun sync() {
        val active = InspectorController.isActive
        canvas.visibility = if (active) VISIBLE else GONE
        toolbar.visibility = if (active) VISIBLE else GONE
        if (!active) canvas.clearSelection()
        canvas.invalidate()
    }

    private fun buildToolbar(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.argb(220, 30, 30, 30))
            fun btn(text: String, onClick: () -> Unit) = addView(Button(context).apply {
                this.text = text
                textSize = 12f
                setOnClickListener { onClick() }
            })
            btn("Size") { InspectorController.selectMode(MeasureMode.SIZE) }
            btn("Gap") { InspectorController.selectMode(MeasureMode.GAP) }
            btn("Ruler") { InspectorController.selectMode(MeasureMode.RULER) }
            btn("Bounds") { InspectorController.selectMode(MeasureMode.BOUNDS) }
            btn("✕") { InspectorController.stopInspection() }
        }

    /** Inner view doing all measurement touch + drawing. */
    private inner class MeasureCanvas(context: Context) : View(context) {

        private val density = resources.displayMetrics.density
        private var nodes: List<CapturedNode> = emptyList()
        private var selectedA: CapturedNode? = null
        private var selectedB: CapturedNode? = null
        private var rulerStart: Pair<Float, Float>? = null
        private var rulerEnd: Pair<Float, Float>? = null
        private var nextIsA = true

        private val boundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.rgb(255, 64, 129)
        }
        private val allBoundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.rgb(0, 176, 255)
        }
        private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeWidth = 3f; color = Color.rgb(255, 196, 0)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 12f * density
        }
        private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 0, 0, 0)
        }

        fun clearSelection() {
            selectedA = null; selectedB = null
            rulerStart = null; rulerEnd = null
            nextIsA = true
        }

        private fun recapture() {
            nodes = ViewCapture.captureAll(rootView)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!InspectorController.isActive) return false
            when (InspectorController.mode) {
                MeasureMode.SIZE -> if (event.action == MotionEvent.ACTION_DOWN) {
                    recapture()
                    selectedA = Geometry.pickAt(nodes, event.x.toInt(), event.y.toInt())
                    selectedB = null
                    invalidate()
                }
                MeasureMode.GAP -> if (event.action == MotionEvent.ACTION_DOWN) {
                    recapture()
                    val hit = Geometry.pickAt(nodes, event.x.toInt(), event.y.toInt())
                    if (hit != null) {
                        if (nextIsA) selectedA = hit else selectedB = hit
                        nextIsA = !nextIsA
                    }
                    invalidate()
                }
                MeasureMode.RULER -> {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> { rulerStart = event.x to event.y; rulerEnd = null }
                        MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> rulerEnd = event.x to event.y
                    }
                    invalidate()
                }
                MeasureMode.BOUNDS -> if (event.action == MotionEvent.ACTION_DOWN) {
                    recapture()
                    invalidate()
                }
            }
            return true
        }

        override fun onDraw(c: Canvas) {
            if (!InspectorController.isActive) return
            when (InspectorController.mode) {
                MeasureMode.SIZE -> selectedA?.let { drawSize(c, it) }
                MeasureMode.GAP -> drawGap(c)
                MeasureMode.RULER -> drawRuler(c)
                MeasureMode.BOUNDS -> drawAllBounds(c)
            }
        }

        private fun drawSize(c: Canvas, n: CapturedNode) {
            drawBounds(c, n.bounds, boundsPaint)
            val text = "${n.label}: ${Geometry.formatPx(n.bounds.width, density)} × " +
                Geometry.formatPx(n.bounds.height, density)
            drawLabel(c, text, n.bounds.left.toFloat(), max(n.bounds.top - 8, 40).toFloat())
        }

        private fun drawGap(c: Canvas) {
            val a = selectedA ?: return
            drawBounds(c, a.bounds, boundsPaint)
            val b = selectedB ?: return
            drawBounds(c, b.bounds, boundsPaint)

            val hGap = Geometry.horizontalGap(a.bounds, b.bounds)
            if (hGap > 0) {
                val x1 = min(a.bounds.right, b.bounds.right).toFloat()
                val x2 = max(a.bounds.left, b.bounds.left).toFloat()
                val y = (max(a.bounds.top, b.bounds.top) +
                    min(a.bounds.bottom, b.bounds.bottom)) / 2f
                c.drawLine(x1, y, x2, y, gapPaint)
                drawLabel(c, Geometry.formatPx(hGap, density), (x1 + x2) / 2, y - 8)
            }
            val vGap = Geometry.verticalGap(a.bounds, b.bounds)
            if (vGap > 0) {
                val y1 = min(a.bounds.bottom, b.bounds.bottom).toFloat()
                val y2 = max(a.bounds.top, b.bounds.top).toFloat()
                val x = (max(a.bounds.left, b.bounds.left) +
                    min(a.bounds.right, b.bounds.right)) / 2f
                c.drawLine(x, y1, x, y2, gapPaint)
                drawLabel(c, Geometry.formatPx(vGap, density), x + 8, (y1 + y2) / 2)
            }
            if (hGap == 0 && vGap == 0) {
                drawLabel(c, "overlapping (gap 0)", a.bounds.left.toFloat(),
                    max(a.bounds.top - 8, 40).toFloat())
            }
        }

        private fun drawRuler(c: Canvas) {
            val s = rulerStart ?: return
            val e = rulerEnd ?: return
            c.drawLine(s.first, s.second, e.first, e.second, gapPaint)
            val len = hypot(e.first - s.first, e.second - s.second).roundToInt()
            drawLabel(c, Geometry.formatPx(len, density),
                (s.first + e.first) / 2, (s.second + e.second) / 2 - 8)
        }

        private fun drawAllBounds(c: Canvas) {
            if (nodes.isEmpty()) recapture()
            nodes.forEach { drawBounds(c, it.bounds, allBoundsPaint) }
        }

        private fun drawBounds(c: Canvas, b: Bounds, p: Paint) {
            c.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), p)
        }

        private fun drawLabel(c: Canvas, text: String, x: Float, y: Float) {
            val w = textPaint.measureText(text)
            val h = textPaint.textSize
            val pad = 6f
            val cx = min(max(x, 0f), width - w - 2 * pad)
            c.drawRect(cx - pad, y - h - pad, cx + w + pad, y + pad, textBgPaint)
            c.drawText(text, cx, y - pad / 2, textPaint)
        }
    }
}
