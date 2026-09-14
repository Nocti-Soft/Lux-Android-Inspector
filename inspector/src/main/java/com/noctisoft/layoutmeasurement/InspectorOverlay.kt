package com.noctisoft.layoutmeasurement

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class InspectorOverlay(
    context: Context,
    private val placementStore: InspectorPlacementStore = InspectorPlacementStore(context),
) : FrameLayout(context) {
    companion object {
        const val TAG = "com.noctisoft.layoutmeasurement.OVERLAY"
    }

    private val canvas = MeasureCanvas(context)
    private val controls = FloatingInspectorControl(context)
    private var lastSafeInsets = Insets.NONE
    private val syncRunnable = Runnable(::sync)
    private val controllerListener: () -> Unit = {
        removeCallbacks(syncRunnable)
        post(syncRunnable)
    }

    init {
        tag = TAG
        setWillNotDraw(true)
        addView(canvas, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        wireControlCallbacks()
        installInsetsListener()
        sync()
    }

    private fun wireControlCallbacks() = with(controls) {
        onModeSelected = { mode ->
            InspectorController.selectMode(mode)
            InspectorController.collapseControls()
        }
        onStopRequested = {
            InspectorController.stopInspection()
            InspectorController.collapseControls()
        }
        onHideRequested = {
            if (NotificationTrigger.isRecoveryAvailable(context)) InspectorController.hideControls()
        }
        onExpandRequested = { InspectorController.expandControls() }
        onCollapseRequested = { InspectorController.collapseControls() }
        onUndockRequested = {
            InspectorController.undockControls()
            placementStore.save(InspectorController.placement)
        }
        onPlacementChanged = { placement ->
            InspectorController.updatePlacement(placement.xFraction, placement.yFraction)
            placementStore.save(InspectorController.placement)
        }
        onDockRequested = { side, placement ->
            InspectorController.updatePlacement(placement.xFraction, placement.yFraction)
            InspectorController.dock(side)
            placementStore.save(InspectorController.placement)
        }
    }

    private fun installInsetsListener() {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            lastSafeInsets = safeInsets(insets)
            updateSafeArea()
            insets
        }
    }

    private fun safeInsets(insets: WindowInsetsCompat): Insets = insets.getInsets(
        WindowInsetsCompat.Type.systemBars() or
            WindowInsetsCompat.Type.displayCutout() or
            WindowInsetsCompat.Type.mandatorySystemGestures(),
    )

    private fun updateSafeArea() {
        if (width == 0 || height == 0) return
        controls.setSafeArea(
            SafeArea(
                left = lastSafeInsets.left,
                top = lastSafeInsets.top,
                right = width - lastSafeInsets.right,
                bottom = height - lastSafeInsets.bottom,
            ),
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateSafeArea()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        InspectorController.addListener(controllerListener)
        ViewCompat.getRootWindowInsets(this)?.let { lastSafeInsets = safeInsets(it) }
        updateSafeArea()
        ViewCompat.requestApplyInsets(this)
        sync()
    }

    override fun onDetachedFromWindow() {
        InspectorController.removeListener(controllerListener)
        removeCallbacks(syncRunnable)
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) sync()
    }

    private fun sync() {
        val active = InspectorController.isActive
        canvas.visibility = if (active) VISIBLE else GONE
        if (!active) canvas.clearSelection()
        canvas.invalidate()
        controls.render(
            state = InspectorController.controlState,
            placement = InspectorController.placement,
            activeMode = InspectorController.mode,
            isActive = active,
            canHide = NotificationTrigger.isRecoveryAvailable(context),
        )
    }
}
