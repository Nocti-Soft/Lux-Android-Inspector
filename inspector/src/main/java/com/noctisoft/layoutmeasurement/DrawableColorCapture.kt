package com.noctisoft.layoutmeasurement

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

/** Public drawing APIs only: never reflect into Android's hidden drawable fields. */
internal object DrawableColorCapture {
    data class Result(val background: ColorValue, val border: ColorValue)

    fun capture(drawable: Drawable?, resources: Resources, width: Int, height: Int): Result =
        try {
            captureSupported(drawable, resources, width, height, 0)
        } catch (_: RuntimeException) {
            unknown("Drawable inspection failed")
        } catch (_: LinkageError) {
            unknown("Drawable API unavailable")
        }

    private fun captureSupported(drawable: Drawable?, resources: Resources, width: Int, height: Int, depth: Int): Result {
        if (drawable == null) return Result(ColorValue.None, ColorValue.None)
        if (depth >= 8) return unknown("Drawable nesting limit")
        if (drawable.javaClass == StateListDrawable::class.java) {
            val current = drawable.current
            return if (current !== drawable) captureSupported(current, resources, width, height, depth + 1)
            else unknown("Selector state unavailable")
        }
        if (drawable is DrawableWrapper && drawable.javaClass.name in knownWrappers) {
            return captureSupported(drawable.drawable, resources, width, height, depth + 1)
        }
        // Custom subclasses can replace draw(), so do not infer their drawing from their base class.
        if (drawable.javaClass != ColorDrawable::class.java && drawable.javaClass != GradientDrawable::class.java) {
            return unknown("Unsupported ${drawable.javaClass.simpleName.ifBlank { "drawable" }}")
        }
        if (drawable.colorFilter != null) return unknown("Color filter changes the drawable")
        if (drawable is GradientDrawable && drawable.alpha != 255) return unknown("Drawable opacity requires compositing")
        val copy = drawable.constantState?.newDrawable(resources)?.mutate()
            ?: return unknown("Drawable cannot be safely copied")
        copy.state = drawable.state.clone()
        copy.level = drawable.level
        copy.layoutDirection = drawable.layoutDirection
        copy.alpha = drawable.alpha
        copy.colorFilter = drawable.colorFilter
        val bounds = drawable.bounds
        copy.bounds = if (!bounds.isEmpty) Rect(bounds) else Rect(0, 0, width.coerceIn(1, 4096), height.coerceIn(1, 4096))
        val recorder = PaintRecorder()
        copy.draw(recorder)
        recorder.layerFailure?.let { return unknown(it) }
        val fill = when {
            recorder.fillFailure != null -> ColorValue.Unavailable(recorder.fillFailure!!)
            copy is ColorDrawable -> ColorValue.Solid(copy.color)
            copy is GradientDrawable && copy.color != null -> {
                val list = copy.color!!
                ColorValue.Solid(list.getColorForState(copy.state, list.defaultColor))
            }
            recorder.fills.isNotEmpty() -> ColorValue.fromColors(recorder.fills)
            copy is GradientDrawable -> ColorValue.Unavailable("Gradient or unspecified fill")
            else -> ColorValue.None
        }
        val border = recorder.strokeFailure?.let(ColorValue::Unavailable)
            ?: ColorValue.fromColors(recorder.strokes)
        return Result(fill, border)
    }

    private val knownWrappers = setOf(
        "android.graphics.drawable.InsetDrawable", "android.graphics.drawable.ScaleDrawable",
        "android.graphics.drawable.ClipDrawable", "android.graphics.drawable.RotateDrawable",
    )

    private fun unknown(reason: String) = Result(ColorValue.Unavailable(reason), ColorValue.Unavailable(reason))

    /** Records paint properties, not pixels. No host bitmap or screenshot is allocated. */
    private class PaintRecorder : Canvas() {
        val fills = mutableListOf<Int>()
        val strokes = mutableListOf<Int>()
        var fillFailure: String? = null
        var strokeFailure: String? = null
        var layerFailure: String? = null

        private fun record(paint: Paint, forceStroke: Boolean = false) {
            val stroke = forceStroke || paint.style == Paint.Style.STROKE
            val reason = when {
                paint.shader != null -> "Gradient or shader"
                paint.colorFilter != null -> "Tint or color filter"
                paint.xfermode != null -> "Custom blend mode"
                else -> null
            }
            if (reason != null) {
                if (stroke) strokeFailure = reason else fillFailure = reason
                return
            }
            if (stroke) strokes.add(paint.color) else fills.add(paint.color)
            if (paint.style == Paint.Style.FILL_AND_STROKE) strokes.add(paint.color)
        }

        private fun recordLayer(paint: Paint?) {
            if (paint?.colorFilter != null || paint?.shader != null || paint?.xfermode != null) {
                layerFailure = "Layer tint or blend effect"
            } else if (paint != null && paint.alpha != 255) {
                layerFailure = "Layer opacity requires compositing"
            }
        }

        override fun saveLayer(bounds: RectF?, paint: Paint?): Int {
            recordLayer(paint)
            return super.save()
        }
        override fun saveLayer(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?): Int {
            recordLayer(paint)
            return super.save()
        }
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun saveLayer(bounds: RectF?, paint: Paint?, saveFlags: Int): Int {
            recordLayer(paint)
            return super.save()
        }
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun saveLayer(left: Float, top: Float, right: Float, bottom: Float, paint: Paint?, saveFlags: Int): Int {
            recordLayer(paint)
            return super.save()
        }

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = record(paint)
        override fun drawRect(rect: RectF, paint: Paint) = record(paint)
        override fun drawRect(rect: Rect, paint: Paint) = record(paint)
        override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) = record(paint)
        override fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) = record(paint)
        override fun drawOval(oval: RectF, paint: Paint) = record(paint)
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = record(paint)
        override fun drawPath(path: Path, paint: Paint) = record(paint)
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = record(paint, true)
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = record(paint)
        override fun drawPaint(paint: Paint) = record(paint)
    }
}
