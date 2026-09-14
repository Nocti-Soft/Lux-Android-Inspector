package com.noctisoft.layoutmeasurement

import kotlin.math.roundToInt

data class PixelPoint(val x: Int, val y: Int)
data class ControlSize(val width: Int, val height: Int)
data class SafeArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
}

object FloatingControlGeometry {
    fun clamp(point: PixelPoint, safe: SafeArea, size: ControlSize): PixelPoint {
        val maxX = (safe.right - size.width).coerceAtLeast(safe.left)
        val maxY = (safe.bottom - size.height).coerceAtLeast(safe.top)
        return PixelPoint(
            x = point.x.coerceIn(safe.left, maxX),
            y = point.y.coerceIn(safe.top, maxY),
        )
    }

    fun toNormalized(point: PixelPoint, safe: SafeArea, size: ControlSize): FloatingPlacement {
        val clamped = clamp(point, safe, size)
        val xRange = (safe.right - safe.left - size.width).coerceAtLeast(1)
        val yRange = (safe.bottom - safe.top - size.height).coerceAtLeast(1)
        return FloatingPlacement(
            xFraction = (clamped.x - safe.left).toFloat() / xRange,
            yFraction = (clamped.y - safe.top).toFloat() / yRange,
            dockSide = DockSide.NONE,
        ).sanitized()
    }

    fun fromNormalized(placement: FloatingPlacement, safe: SafeArea, size: ControlSize): PixelPoint {
        val clean = placement.sanitized()
        val xRange = (safe.right - safe.left - size.width).coerceAtLeast(0)
        val yRange = (safe.bottom - safe.top - size.height).coerceAtLeast(0)
        return clamp(
            PixelPoint(
                x = safe.left + (clean.xFraction * xRange).roundToInt(),
                y = safe.top + (clean.yFraction * yRange).roundToInt(),
            ),
            safe,
            size,
        )
    }

    fun chooseDockSide(
        point: PixelPoint,
        safe: SafeArea,
        size: ControlSize,
        thresholdPx: Int,
    ): DockSide {
        val leftDistance = point.x - safe.left
        val rightDistance = (safe.right - size.width) - point.x
        return when {
            leftDistance <= thresholdPx -> DockSide.LEFT
            rightDistance <= thresholdPx -> DockSide.RIGHT
            else -> DockSide.NONE
        }
    }

    fun dockedPosition(
        side: DockSide,
        y: Int,
        safe: SafeArea,
        size: ControlSize,
    ): PixelPoint {
        val clampedY = y.coerceIn(safe.top, (safe.bottom - size.height).coerceAtLeast(safe.top))
        val x = when (side) {
            DockSide.LEFT -> safe.left - size.width / 2
            DockSide.RIGHT -> safe.right - size.width / 2
            DockSide.NONE -> error("Docked position requires LEFT or RIGHT")
        }
        return PixelPoint(x, clampedY)
    }
}
