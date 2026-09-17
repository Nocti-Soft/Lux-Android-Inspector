package com.noctisoft.layoutmeasurement

import android.content.Context
import android.os.Looper
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
    private var observedPlacement = InspectorController.placement
    private var observedActive = InspectorController.isActive
    @Volatile private var pendingStopReset = false
    private val syncRunnable = Runnable(::sync)
    private val controllerListener: () -> Unit = {
        val active = InspectorController.isActive
        val placement = InspectorController.placement
        if (observedActive && !active) pendingStopReset = true
        if (placement != observedPlacement) placementStore.save(placement)
        observedActive = active
        observedPlacement = placement
        if (Looper.myLooper() == Looper.getMainLooper()) {
            sync()
        } else {
            removeCallbacks(syncRunnable)
            post(syncRunnable)
        }
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
        onNotificationControlsRequested = { NotificationTrigger.openNotificationControls(context) }
        onExpandRequested = { InspectorController.expandControls() }
        onCollapseRequested = { InspectorController.collapseControls() }
        onUndockRequested = { InspectorController.undockControls() }
        onPlacementChanged = { placement ->
            InspectorController.updatePlacement(placement.xFraction, placement.yFraction)
        }
        onDockRequested = { side, placement ->
            InspectorController.updatePlacement(placement.xFraction, placement.yFraction)
            InspectorController.dock(side)
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
        observedPlacement = InspectorController.placement
        observedActive = InspectorController.isActive
        InspectorController.addListener(controllerListener)
        ViewCompat.getRootWindowInsets(this)?.let { lastSafeInsets = safeInsets(it) }
        updateSafeArea()
        ViewCompat.requestApplyInsets(this)
        sync()
    }

    override fun onDetachedFromWindow() {
        InspectorController.removeListener(controllerListener)
        removeCallbacks(syncRunnable)
        canvas.clearSelection()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) sync()
    }

    private fun sync() {
        val active = InspectorController.isActive
        canvas.visibility = if (active) VISIBLE else GONE
        if (pendingStopReset || !active) {
            canvas.clearSelection()
            pendingStopReset = false
        }
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
