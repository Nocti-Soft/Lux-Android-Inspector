package dev.pinij.inspector

import java.util.concurrent.CopyOnWriteArrayList

enum class MeasureMode { SIZE, GAP, RULER, BOUNDS }

object InspectorController {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    var isActive: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            notifyListeners()
        }

    var mode: MeasureMode = MeasureMode.SIZE
        set(value) {
            if (field == value) return
            field = value
            notifyListeners()
        }

    fun toggle() { isActive = !isActive }

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    private fun notifyListeners() = listeners.forEach { it() }
}
