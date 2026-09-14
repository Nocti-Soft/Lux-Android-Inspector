package com.noctisoft.layoutmeasurement

import java.util.concurrent.CopyOnWriteArrayList

object InspectorController {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    var isActive: Boolean = false
        private set

    var mode: MeasureMode = MeasureMode.SIZE
        private set

    var controlState: FloatingControlState = FloatingControlState.HIDDEN
        private set

    var placement: FloatingPlacement = FloatingPlacement()
        private set

    private var notificationRestoreRequired: Boolean = false

    fun selectMode(newMode: MeasureMode) {
        val changed = !isActive || mode != newMode
        isActive = true
        mode = newMode
        if (changed) notifyListeners()
    }

    fun stopInspection() {
        if (!isActive) return
        isActive = false
        notifyListeners()
    }

    fun revealControls(source: RevealSource) {
        if (
            controlState == FloatingControlState.HIDDEN &&
            notificationRestoreRequired &&
            source != RevealSource.NOTIFICATION
        ) return

        val previousState = controlState
        val previousPlacement = placement
        if (source == RevealSource.NOTIFICATION) notificationRestoreRequired = false

        when (controlState) {
            FloatingControlState.HIDDEN -> {
                controlState = when (placement.dockSide) {
                    DockSide.LEFT -> FloatingControlState.DOCKED_LEFT
                    DockSide.RIGHT -> FloatingControlState.DOCKED_RIGHT
                    DockSide.NONE -> FloatingControlState.COLLAPSED
                }
            }
            FloatingControlState.DOCKED_LEFT,
            FloatingControlState.DOCKED_RIGHT -> undockControls(notify = false)
            FloatingControlState.EXPANDED -> controlState = FloatingControlState.COLLAPSED
            FloatingControlState.COLLAPSED -> Unit
        }
        if (previousState != controlState || previousPlacement != placement) notifyListeners()
    }

    fun undockControls() = undockControls(notify = true)

    private fun undockControls(notify: Boolean) {
        if (
            controlState != FloatingControlState.DOCKED_LEFT &&
            controlState != FloatingControlState.DOCKED_RIGHT
        ) return
        placement = placement.copy(dockSide = DockSide.NONE)
        controlState = FloatingControlState.COLLAPSED
        if (notify) notifyListeners()
    }

    fun hideControlsForStartup() {
        notificationRestoreRequired = false
        if (controlState == FloatingControlState.HIDDEN) return
        controlState = FloatingControlState.HIDDEN
        notifyListeners()
    }

    fun expandControls() {
        if (controlState != FloatingControlState.COLLAPSED) return
        controlState = FloatingControlState.EXPANDED
        notifyListeners()
    }

    fun collapseControls() {
        if (controlState != FloatingControlState.EXPANDED) return
        controlState = FloatingControlState.COLLAPSED
        notifyListeners()
    }

    fun dock(side: DockSide) {
        require(side != DockSide.NONE) { "Dock side must be LEFT or RIGHT" }
        val nextState = if (side == DockSide.LEFT) {
            FloatingControlState.DOCKED_LEFT
        } else {
            FloatingControlState.DOCKED_RIGHT
        }
        val nextPlacement = placement.copy(dockSide = side)
        if (controlState == nextState && placement == nextPlacement) return
        controlState = nextState
        placement = nextPlacement
        notifyListeners()
    }

    fun hideControls() {
        notificationRestoreRequired = true
        if (controlState == FloatingControlState.HIDDEN) return
        controlState = FloatingControlState.HIDDEN
        notifyListeners()
    }

    fun updatePlacement(xFraction: Float, yFraction: Float) {
        val next = FloatingPlacement(
            xFraction = xFraction,
            yFraction = yFraction,
            dockSide = DockSide.NONE,
        ).sanitized()
        if (next == placement) return
        placement = next
        notifyListeners()
    }

    fun restorePlacement(saved: FloatingPlacement) {
        val next = saved.sanitized()
        if (next == placement) return
        placement = next
        notifyListeners()
    }

    fun addListener(listener: () -> Unit) = listeners.add(listener)
    fun removeListener(listener: () -> Unit) = listeners.remove(listener)

    private fun notifyListeners() = listeners.forEach { it() }
}
