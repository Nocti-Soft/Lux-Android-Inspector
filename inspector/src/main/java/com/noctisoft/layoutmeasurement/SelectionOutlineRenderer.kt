package com.noctisoft.layoutmeasurement

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

internal object SelectionOutlineRenderer {
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.rgb(255, 0, 168)
    }

    fun draw(canvas: Canvas, bounds: Bounds, density: Float) = draw(canvas, listOf(bounds), density)

    fun draw(canvas: Canvas, bounds: List<Bounds>, density: Float) {
        val paths = bounds.filter { it.width > 0 && it.height > 0 }.map {
            RectF(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat())
        }
        val coreWidth = 2f * density
        haloPaint.strokeWidth = coreWidth + 2f * density
        corePaint.strokeWidth = coreWidth
        paths.forEach { canvas.drawRect(it, haloPaint) }
        paths.forEach { canvas.drawRect(it, corePaint) }
    }
}
