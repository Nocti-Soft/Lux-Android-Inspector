package com.noctisoft.layoutmeasurement

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class MeasureCanvas(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private var nodes: List<CapturedNode> = emptyList()
    private var selectedA: CapturedNode? = null
    private var selectedB: CapturedNode? = null
    private var rulerStart: Pair<Float, Float>? = null
    private var rulerEnd: Pair<Float, Float>? = null
    private var nextIsA = true

    private val boundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.rgb(255, 0, 168)
    }
    private val allBoundsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = Color.rgb(0, 229, 255)
    }
    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        color = Color.rgb(255, 196, 0)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
    }
    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
    }

    fun clearSelection() {
        nodes = emptyList()
        selectedA = null
        selectedB = null
        rulerStart = null
        rulerEnd = null
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
            MeasureMode.RULER -> when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    rulerStart = event.x to event.y
                    rulerEnd = null
                }
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> rulerEnd = event.x to event.y
                else -> Unit
            }.also { invalidate() }
            MeasureMode.BOUNDS -> if (event.action == MotionEvent.ACTION_DOWN) {
                recapture()
                invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        if (!InspectorController.isActive) return
        when (InspectorController.mode) {
            MeasureMode.SIZE -> selectedA?.let { drawSize(canvas, it) }
            MeasureMode.GAP -> drawGap(canvas)
            MeasureMode.RULER -> drawRuler(canvas)
            MeasureMode.BOUNDS -> drawAllBounds(canvas)
        }
    }

    private fun drawSize(canvas: Canvas, node: CapturedNode) {
        val text = "${node.label}: ${Geometry.formatPx(node.bounds.width, density)} × " +
            Geometry.formatPx(node.bounds.height, density)
        drawLabel(canvas, text, node.bounds.left.toFloat(), max(node.bounds.top - 8, 40).toFloat())
        drawBounds(canvas, node.bounds, boundsPaint)
    }

    private fun drawGap(canvas: Canvas) {
        val a = selectedA ?: return
        val b = selectedB
        if (b == null) {
            drawBounds(canvas, a.bounds, boundsPaint)
            return
        }
        val horizontalGap = Geometry.horizontalGap(a.bounds, b.bounds)
        if (horizontalGap > 0) {
            val x1 = min(a.bounds.right, b.bounds.right).toFloat()
            val x2 = max(a.bounds.left, b.bounds.left).toFloat()
            val y = (max(a.bounds.top, b.bounds.top) + min(a.bounds.bottom, b.bounds.bottom)) / 2f
            canvas.drawLine(x1, y, x2, y, gapPaint)
            drawLabel(canvas, Geometry.formatPx(horizontalGap, density), (x1 + x2) / 2, y - 8)
        }
        val verticalGap = Geometry.verticalGap(a.bounds, b.bounds)
        if (verticalGap > 0) {
            val y1 = min(a.bounds.bottom, b.bounds.bottom).toFloat()
            val y2 = max(a.bounds.top, b.bounds.top).toFloat()
            val x = (max(a.bounds.left, b.bounds.left) + min(a.bounds.right, b.bounds.right)) / 2f
            canvas.drawLine(x, y1, x, y2, gapPaint)
            drawLabel(canvas, Geometry.formatPx(verticalGap, density), x + 8, (y1 + y2) / 2)
        }
        if (horizontalGap == 0 && verticalGap == 0) {
            drawLabel(canvas, "overlapping (gap 0)", a.bounds.left.toFloat(), max(a.bounds.top - 8, 40).toFloat())
        }
        SelectionOutlineRenderer.draw(canvas, listOf(a.bounds, b.bounds), density)
    }

    private fun drawRuler(canvas: Canvas) {
        val start = rulerStart ?: return
        val end = rulerEnd ?: return
        canvas.drawLine(start.first, start.second, end.first, end.second, gapPaint)
        val length = hypot(end.first - start.first, end.second - start.second).roundToInt()
        drawLabel(canvas, Geometry.formatPx(length, density), (start.first + end.first) / 2, (start.second + end.second) / 2 - 8)
    }

    private fun drawAllBounds(canvas: Canvas) {
        if (nodes.isEmpty()) recapture()
        nodes.forEach { drawBounds(canvas, it.bounds, allBoundsPaint) }
    }

    private fun drawBounds(canvas: Canvas, bounds: Bounds, paint: Paint) {
        if (paint === boundsPaint) {
            SelectionOutlineRenderer.draw(canvas, bounds, density)
        } else {
            canvas.drawRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), paint)
        }
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float) {
        val width = textPaint.measureText(text)
        val height = textPaint.textSize
        val padding = 6f
        val clampedX = min(max(x, 0f), this.width - width - 2 * padding)
        canvas.drawRect(clampedX - padding, y - height - padding, clampedX + width + padding, y + padding, textBgPaint)
        canvas.drawText(text, clampedX, y - padding / 2, textPaint)
    }
}
