package com.noctisoft.layoutmeasurement

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/** Dimension guides plus a collision-free readout for narrow or shared inner edges. */
internal class EdgeDistanceRenderer(private val density: Float) {
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 196, 0)
        strokeWidth = density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 0, 0)
    }

    fun draw(canvas: Canvas, outer: Bounds, inner: Bounds, edges: EdgeDistances, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val sameBounds = outer == inner
        if (!sameBounds) drawGuides(canvas, outer, inner)
        SelectionOutlineRenderer.draw(canvas, listOf(outer, inner).distinct(), density)
        // Final foreground pass; this is canvas content, not a touch-intercepting panel.
        drawReadout(canvas, outer, inner, edges, width, height, if (sameBounds) "Same bounds" else "Inside · edge distances")
    }

    private fun drawGuides(canvas: Canvas, outer: Bounds, inner: Bounds) {
        val centerX = inner.left + inner.width / 2f
        val centerY = inner.top + inner.height / 2f
        val tick = 3f * density
        fun horizontal(from: Int, to: Int) {
            canvas.drawLine(from.toFloat(), centerY, to.toFloat(), centerY, guidePaint)
            for (x in listOf(from, to)) canvas.drawLine(x.toFloat(), centerY - tick, x.toFloat(), centerY + tick, guidePaint)
        }
        fun vertical(from: Int, to: Int) {
            canvas.drawLine(centerX, from.toFloat(), centerX, to.toFloat(), guidePaint)
            for (y in listOf(from, to)) canvas.drawLine(centerX - tick, y.toFloat(), centerX + tick, y.toFloat(), guidePaint)
        }
        horizontal(outer.left, inner.left)
        horizontal(inner.right, outer.right)
        vertical(outer.top, inner.top)
        vertical(inner.bottom, outer.bottom)
    }

    private fun drawReadout(canvas: Canvas, outer: Bounds, inner: Bounds, edges: EdgeDistances, width: Int, height: Int, title: String) {
        val lines = listOf(title,
            "Left: ${Geometry.formatPx(edges.left, density)}",
            "Top: ${Geometry.formatPx(edges.top, density)}",
            "Right: ${Geometry.formatPx(edges.right, density)}",
            "Bottom: ${Geometry.formatPx(edges.bottom, density)}",
        )
        val margin = min(8f * density, min(width, height) / 16f)
        val padding = min(6f * density, min(width, height) / 16f)
        val paint = Paint(textPaint)
        val usableWidth = width - 2f * (margin + padding)
        val usableHeight = height - 2f * (margin + padding)
        val largestText = lines.maxOf(paint::measureText)
        val naturalLineHeight = paint.fontMetrics.bottom - paint.fontMetrics.top + 2f * density
        val scale = min(1f, min(usableWidth / largestText, usableHeight / (lines.size * naturalLineHeight)))
        if (scale <= 0f) return
        paint.textSize *= scale
        val metrics = paint.fontMetrics
        val lineHeight = metrics.bottom - metrics.top + 2f * density * scale
        val panelWidth = lines.maxOf(paint::measureText) + padding * 2f
        val panelHeight = lines.size * lineHeight + padding * 2f
        val maxX = max(margin, width - margin - panelWidth)
        val maxY = max(margin, height - margin - panelHeight)
        val (x, y) = when {
            outer.right + margin + panelWidth <= width - margin ->
                (outer.right + margin) to outer.top.toFloat().coerceIn(margin, maxY)
            outer.left - margin - panelWidth >= margin ->
                (outer.left - margin - panelWidth) to outer.top.toFloat().coerceIn(margin, maxY)
            outer.top - margin - panelHeight >= margin ->
                outer.left.toFloat().coerceIn(margin, maxX) to (outer.top - margin - panelHeight)
            outer.bottom + margin + panelHeight <= height - margin ->
                outer.left.toFloat().coerceIn(margin, maxX) to (outer.bottom + margin)
            else -> outer.left.toFloat().coerceIn(margin, maxX) to
                (inner.top - margin - panelHeight).coerceIn(margin, maxY)
        }
        canvas.drawRoundRect(RectF(x, y, x + panelWidth, y + panelHeight), 4f * density, 4f * density, backgroundPaint)
        lines.forEachIndexed { index, text ->
            canvas.drawText(text, x + padding, y + padding - metrics.top + index * lineHeight, paint)
        }
    }
}
