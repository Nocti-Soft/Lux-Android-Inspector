package com.noctisoft.layoutmeasurement

import kotlin.math.max
import kotlin.math.min

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Long get() = width.toLong() * height.toLong()
    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
}

enum class Source { XML, COMPOSE }

data class CapturedNode @JvmOverloads constructor(
    val label: String,
    val bounds: Bounds,
    val source: Source,
    val colors: ComponentColors? = null,
    val textProperties: TextProperties? = null,
)

object Geometry {
    fun horizontalGap(a: Bounds, b: Bounds): Int =
        max(0, max(a.left, b.left) - min(a.right, b.right))

    fun verticalGap(a: Bounds, b: Bounds): Int =
        max(0, max(a.top, b.top) - min(a.bottom, b.bottom))

    /** Bounds relationship only: containment does not imply actual View ancestry. */
    internal fun measureGap(a: Bounds, b: Bounds): GapMeasurement {
        if (a.width <= 0 || a.height <= 0 || b.width <= 0 || b.height <= 0) return GapMeasurement.Invalid
        if (a == b) return GapMeasurement.SameBounds
        fun contains(outer: Bounds, inner: Bounds) =
            outer.left <= inner.left && outer.top <= inner.top &&
                outer.right >= inner.right && outer.bottom >= inner.bottom
        val outer = when {
            contains(a, b) -> a
            contains(b, a) -> b
            else -> null
        }
        if (outer != null) {
            val inner = if (outer == a) b else a
            return GapMeasurement.Contained(outer, inner, EdgeDistances(
                inner.left - outer.left, inner.top - outer.top,
                outer.right - inner.right, outer.bottom - inner.bottom,
            ))
        }
        val horizontal = horizontalGap(a, b)
        val vertical = verticalGap(a, b)
        if (horizontal > 0 || vertical > 0) return GapMeasurement.Separated(horizontal, vertical)
        return if (min(a.right, b.right) == max(a.left, b.left) ||
            min(a.bottom, b.bottom) == max(a.top, b.top)
        ) GapMeasurement.Touching else GapMeasurement.Overlapping
    }

    fun pxToDp(px: Int, density: Float): Float = px / density

    fun formatPx(px: Int, density: Float): String {
        val dp = (pxToDp(px, density) * 10).toInt() / 10f
        return "${dp}dp (${px}px)"
    }

    fun pickAt(nodes: List<CapturedNode>, x: Int, y: Int): CapturedNode? =
        // Capture visits parents before children; equal bounds should select the child.
        nodes.asReversed().filter { it.bounds.contains(x, y) }.minByOrNull { it.bounds.area }
}

/** Physical edge distances, not declared layout padding or margins. */
internal data class EdgeDistances(val left: Int, val top: Int, val right: Int, val bottom: Int)

internal sealed interface GapMeasurement {
    data class Contained(val outer: Bounds, val inner: Bounds, val edges: EdgeDistances) : GapMeasurement
    data class Separated(val horizontal: Int, val vertical: Int) : GapMeasurement
    data object SameBounds : GapMeasurement
    data object Touching : GapMeasurement
    data object Overlapping : GapMeasurement
    data object Invalid : GapMeasurement
}
