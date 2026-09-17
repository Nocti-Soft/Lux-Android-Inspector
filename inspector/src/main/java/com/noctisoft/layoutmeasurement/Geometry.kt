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
)

object Geometry {
    fun horizontalGap(a: Bounds, b: Bounds): Int =
        max(0, max(a.left, b.left) - min(a.right, b.right))

    fun verticalGap(a: Bounds, b: Bounds): Int =
        max(0, max(a.top, b.top) - min(a.bottom, b.bottom))

    fun pxToDp(px: Int, density: Float): Float = px / density

    fun formatPx(px: Int, density: Float): String {
        val dp = (pxToDp(px, density) * 10).toInt() / 10f
        return "${dp}dp (${px}px)"
    }

    fun pickAt(nodes: List<CapturedNode>, x: Int, y: Int): CapturedNode? =
        nodes.filter { it.bounds.contains(x, y) }.minByOrNull { it.bounds.area }
}
