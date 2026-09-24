package com.noctisoft.layoutmeasurement

import android.content.Context
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.roundToInt

class InspectorOverlay(
    context: Context,
    private val placementStore: InspectorPlacementStore = InspectorPlacementStore(context),
) : FrameLayout(context) {
    companion object {
        const val TAG = "com.noctisoft.layoutmeasurement.OVERLAY"
    }

    private val canvas = MeasureCanvas(context)
    private val controls = FloatingInspectorControl(context)
    private val colorPanel = ColorDetailsPanel(context)
    private var renderedMode = InspectorController.mode
    private var lastSafeInsets = Insets.NONE
    private var appliedSafeArea: SafeArea? = null
    private var panelSafeArea = SafeArea(0, 0, 0, 0)
    // Session-local position, independent of the floating tool button and selected component.
    private var panelPosition: Pair<Float, Float>? = null
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
        addView(colorPanel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.LEFT))
        // Keep floating controls above the raised readout for both drawing and touch dispatch.
        controls.elevation = 12 * resources.displayMetrics.density
        addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        canvas.onColorNodeSelected = { node -> colorPanel.render(node) }
        colorPanel.onDismissRequested = { canvas.clearSelection(); canvas.invalidate() }
        colorPanel.onDragBy = { dx, dy ->
            val previous = panelPosition ?: (colorPanel.x to colorPanel.y)
            panelPosition = previous.first + dx to previous.second + dy
            positionColorPanel()
        }
        wireControlCallbacks()
        installInsetsListener()
        sync()
    }

    internal fun clearWindowSelection() {
        canvas.clearSelection()
        canvas.invalidate()
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

    private fun updateSafeArea(availableWidth: Int = width, availableHeight: Int = height) {
        if (availableWidth <= 0 || availableHeight <= 0) return
        val safeArea = SafeArea(
            left = lastSafeInsets.left,
            top = lastSafeInsets.top,
            right = availableWidth - lastSafeInsets.right,
            bottom = availableHeight - lastSafeInsets.bottom,
        )
        if (safeArea == appliedSafeArea) return
        appliedSafeArea = safeArea
        controls.setSafeArea(safeArea)
        val density = resources.displayMetrics.density
        val gapX = (12 * density).roundToInt().coerceAtMost(safeArea.width.coerceAtLeast(0) / 2)
        val gapY = (12 * density).roundToInt().coerceAtMost(safeArea.height.coerceAtLeast(0) / 2)
        panelSafeArea = SafeArea(safeArea.left + gapX, safeArea.top + gapY,
            safeArea.right - gapX, safeArea.bottom - gapY)
        val panelWidth = panelSafeArea.width.coerceIn(0, (ColorDetailsPanel.MAX_WIDTH_DP * density).roundToInt())
        colorPanel.setMaximumSize(panelWidth, panelSafeArea.height.coerceAtLeast(0))
        colorPanel.layoutParams = (colorPanel.layoutParams as LayoutParams).apply {
            width = panelWidth
        }
    }

    private fun positionColorPanel() {
        if (colorPanel.visibility != VISIBLE) return
        val panelWidth = colorPanel.measuredWidth
        val panelHeight = colorPanel.measuredHeight
        val left = panelSafeArea.left.toFloat()
        val top = panelSafeArea.top.toFloat()
        val maxX = (panelSafeArea.right - panelWidth).coerceAtLeast(panelSafeArea.left).toFloat()
        val maxY = (panelSafeArea.bottom - panelHeight).coerceAtLeast(panelSafeArea.top).toFloat()
        val requested = panelPosition ?: (left to maxY)
        val x = requested.first.coerceIn(left, maxX)
        val y = requested.second.coerceIn(top, maxY)
        if (panelPosition != null) panelPosition = x to y
        colorPanel.layout(x.roundToInt(), y.roundToInt(), x.roundToInt() + panelWidth, y.roundToInt() + panelHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        positionColorPanel()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Bound the panel before measuring children, including the first frame after a resize.
        updateSafeArea(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
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
        val mode = InspectorController.mode
        if (mode != renderedMode && (mode == MeasureMode.COLORS || renderedMode == MeasureMode.COLORS)) {
            canvas.clearSelection()
        }
        renderedMode = mode
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
