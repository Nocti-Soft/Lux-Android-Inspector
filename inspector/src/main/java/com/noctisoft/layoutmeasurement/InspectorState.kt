package com.noctisoft.layoutmeasurement

enum class MeasureMode { SIZE, GAP, RULER, BOUNDS, COLORS }

enum class FloatingControlState {
    HIDDEN,
    COLLAPSED,
    EXPANDED,
    DOCKED_LEFT,
    DOCKED_RIGHT,
}

enum class DockSide { NONE, LEFT, RIGHT }

enum class RevealSource { SHAKE, NOTIFICATION }

data class FloatingPlacement(
    val xFraction: Float = 0.85f,
    val yFraction: Float = 0.50f,
    val dockSide: DockSide = DockSide.NONE,
) {
    fun sanitized(): FloatingPlacement = copy(
        xFraction = xFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.85f,
        yFraction = yFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.50f,
    )
}
