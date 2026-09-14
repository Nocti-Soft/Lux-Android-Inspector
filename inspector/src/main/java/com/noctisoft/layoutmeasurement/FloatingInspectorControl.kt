package com.noctisoft.layoutmeasurement

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.hypot
import kotlin.math.roundToInt

internal class FloatingInspectorControl(context: Context) : FrameLayout(context) {
    var onModeSelected: (MeasureMode) -> Unit = {}
    var onStopRequested: () -> Unit = {}
    var onHideRequested: () -> Unit = {}
    var onExpandRequested: () -> Unit = {}
    var onCollapseRequested: () -> Unit = {}
    var onUndockRequested: () -> Unit = {}
    var onPlacementChanged: (FloatingPlacement) -> Unit = {}
    var onDockRequested: (DockSide, FloatingPlacement) -> Unit = { _, _ -> }

    private val density = resources.displayMetrics.density
    private val buttonSizePx = dp(56)
    private val snapThresholdPx = dp(48)
    private var safeArea = SafeArea(0, 0, 0, 0)
    private var renderedPlacement = FloatingPlacement()
    private var renderedState = FloatingControlState.HIDDEN
    private var renderedMode = MeasureMode.SIZE
    private var renderedIsActive = false
    private var renderedCanHide = false
    private var dragging = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private val dismissLayer = View(context).apply {
        visibility = GONE
        isClickable = true
        contentDescription = "Collapse inspector controls"
        setOnClickListener { onCollapseRequested() }
        setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) view.performClick()
            true
        }
    }
    private val floatingButton = TextView(context).apply {
        text = "◎"
        gravity = Gravity.CENTER
        textSize = 22f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(45, 45, 48))
        }
        elevation = dp(8).toFloat()
        contentDescription = "Layout inspector controls"
        isClickable = true
        isFocusable = true
        setOnClickListener { activateFloatingButton() }
    }
    private lateinit var hideButton: Button
    private lateinit var stopButton: Button
    private lateinit var mainPanel: LinearLayout
    private lateinit var settingsPanel: LinearLayout
    private val modeButtons = linkedMapOf<MeasureMode, Button>()
    private val menu = SafeMenu(context).apply {
        visibility = GONE
        background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(Color.rgb(32, 32, 36))
        }
        setPadding(dp(8), dp(8), dp(8), dp(8))
        addView(ScrollView(context).apply { addView(buildPanels(context)) })
    }

    init {
        visibility = GONE
        clipChildren = false
        clipToPadding = false
        addView(dismissLayer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(menu, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(floatingButton, LayoutParams(buttonSizePx, buttonSizePx))
        installDragGesture()
    }

    fun setSafeArea(area: SafeArea) {
        safeArea = area
        menu.setMaximumSize(area.width, area.height)
        if (!dragging) {
            applyButtonPosition()
            positionMenu()
        }
    }

    fun render(
        state: FloatingControlState,
        placement: FloatingPlacement,
        activeMode: MeasureMode,
        isActive: Boolean,
        canHide: Boolean,
    ) {
        renderedState = state
        renderedPlacement = placement
        renderedMode = activeMode
        renderedIsActive = isActive
        renderedCanHide = canHide
        visibility = if (state == FloatingControlState.HIDDEN) GONE else VISIBLE
        if (visibility == GONE) return

        hideButton.isEnabled = canHide
        hideButton.text = if (canHide) "Hide Inspector" else "Hide (notification required)"
        stopButton.isEnabled = isActive
        modeButtons.forEach { (mode, button) ->
            val label = mode.name.lowercase().replaceFirstChar { it.uppercase() }
            button.text = if (isActive && mode == activeMode) "• $label" else label
        }
        if (!dragging) {
            dismissLayer.visibility = if (state == FloatingControlState.EXPANDED) VISIBLE else GONE
            menu.visibility = if (state == FloatingControlState.EXPANDED) VISIBLE else GONE
            if (state != FloatingControlState.EXPANDED) showMainPanel()
            applyButtonPosition()
            positionMenu()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (!dragging && renderedState == FloatingControlState.EXPANDED) positionMenu()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.save()
        canvas.clipRect(safeArea.left, safeArea.top, safeArea.right, safeArea.bottom)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(save)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (safeArea.width <= 0 || safeArea.height <= 0) {
            cancelDrag()
            return false
        }
        if (renderedState != FloatingControlState.EXPANDED &&
            event.actionMasked == MotionEvent.ACTION_DOWN &&
            (event.x !in safeArea.left.toFloat()..safeArea.right.toFloat() ||
                event.y !in safeArea.top.toFloat()..safeArea.bottom.toFloat())
        ) return false
        return super.dispatchTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun installDragGesture() {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0f
        var startY = 0f
        floatingButton.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = event.getPointerId(0)
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = view.x
                    startY = view.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(activePointerId)
                    if (index < 0) return@setOnTouchListener cancelDrag()
                    val dx = event.getRawX(index) - downRawX
                    val dy = event.getRawY(index) - downRawY
                    if (!dragging && hypot(dx.toDouble(), dy.toDouble()) >= touchSlop) {
                        dragging = true
                        when (renderedState) {
                            FloatingControlState.EXPANDED -> onCollapseRequested()
                            FloatingControlState.DOCKED_LEFT, FloatingControlState.DOCKED_RIGHT -> onUndockRequested()
                            else -> Unit
                        }
                        menu.visibility = GONE
                        dismissLayer.visibility = GONE
                    }
                    if (dragging && safeArea.width > 0 && safeArea.height > 0) {
                        val point = FloatingControlGeometry.clamp(
                            PixelPoint((startX + dx).roundToInt(), (startY + dy).roundToInt()),
                            safeArea,
                            ControlSize(buttonSizePx, buttonSizePx),
                        )
                        view.x = point.x.toFloat()
                        view.y = point.y.toFloat()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (event.findPointerIndex(activePointerId) < 0) return@setOnTouchListener cancelDrag()
                    if (dragging) finishDrag(PixelPoint(view.x.roundToInt(), view.y.roundToInt())) else view.performClick()
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    dragging = false
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.getPointerId(event.actionIndex) == activePointerId) cancelDrag() else true
                }
                MotionEvent.ACTION_CANCEL -> cancelDrag()
                else -> true
            }
        }
    }

    private fun cancelDrag(): Boolean {
        dragging = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        applyButtonPosition()
        return true
    }

    private fun finishDrag(point: PixelPoint) {
        dragging = false
        if (safeArea.width <= 0 || safeArea.height <= 0) {
            applyButtonPosition()
            return
        }
        val size = ControlSize(buttonSizePx, buttonSizePx)
        val clamped = FloatingControlGeometry.clamp(point, safeArea, size)
        val normalized = FloatingControlGeometry.toNormalized(clamped, safeArea, size)
        when (val dockSide = FloatingControlGeometry.chooseDockSide(clamped, safeArea, size, snapThresholdPx)) {
            DockSide.NONE -> onPlacementChanged(normalized)
            else -> onDockRequested(dockSide, normalized.copy(dockSide = dockSide))
        }
    }

    private fun activateFloatingButton() {
        when (renderedState) {
            FloatingControlState.DOCKED_LEFT, FloatingControlState.DOCKED_RIGHT -> onUndockRequested()
            FloatingControlState.HIDDEN -> Unit
            else -> onExpandRequested()
        }
    }

    private fun applyButtonPosition() {
        if (safeArea.width <= 0 || safeArea.height <= 0) return
        val size = ControlSize(buttonSizePx, buttonSizePx)
        val ordinary = FloatingControlGeometry.fromNormalized(renderedPlacement.copy(dockSide = DockSide.NONE), safeArea, size)
        val target = when (renderedState) {
            FloatingControlState.DOCKED_LEFT -> FloatingControlGeometry.dockedPosition(DockSide.LEFT, ordinary.y, safeArea, size)
            FloatingControlState.DOCKED_RIGHT -> FloatingControlGeometry.dockedPosition(DockSide.RIGHT, ordinary.y, safeArea, size)
            else -> ordinary
        }
        floatingButton.x = target.x.toFloat()
        floatingButton.y = target.y.toFloat()
    }

    private fun positionMenu() {
        if (menu.visibility != VISIBLE || safeArea.width <= 0 || safeArea.height <= 0) return
        menu.measure(
            MeasureSpec.makeMeasureSpec(safeArea.width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(safeArea.height, MeasureSpec.AT_MOST),
        )
        val gap = dp(8)
        val buttonCenter = floatingButton.x + floatingButton.width / 2f
        val safeCenter = (safeArea.left + safeArea.right) / 2f
        val preferredX = if (buttonCenter >= safeCenter) floatingButton.x.roundToInt() - gap - menu.measuredWidth else floatingButton.x.roundToInt() + floatingButton.width + gap
        val maxX = (safeArea.right - menu.measuredWidth).coerceAtLeast(safeArea.left)
        val maxY = (safeArea.bottom - menu.measuredHeight).coerceAtLeast(safeArea.top)
        menu.x = preferredX.coerceIn(safeArea.left, maxX).toFloat()
        menu.y = floatingButton.y.roundToInt().coerceIn(safeArea.top, maxY).toFloat()
    }

    private fun activeMenuVisibility() {
        val expanded = renderedState == FloatingControlState.EXPANDED
        dismissLayer.visibility = if (expanded) VISIBLE else GONE
        menu.visibility = if (expanded) VISIBLE else GONE
        positionMenu()
    }

    private fun showMainPanel() {
        settingsPanel.visibility = GONE
        mainPanel.visibility = VISIBLE
    }

    private fun buildPanels(context: Context): FrameLayout = FrameLayout(context).apply {
        mainPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(modeButton("Size", MeasureMode.SIZE))
            addView(modeButton("Gap", MeasureMode.GAP))
            addView(modeButton("Ruler", MeasureMode.RULER))
            addView(modeButton("Bounds", MeasureMode.BOUNDS))
            addView(actionButton("Settings") { mainPanel.visibility = GONE; settingsPanel.visibility = VISIBLE; positionMenu() })
            hideButton = actionButton("Hide Inspector") { onHideRequested() }
            addView(hideButton)
        }
        settingsPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = GONE
            stopButton = actionButton("Stop Inspector") { onStopRequested() }
            addView(stopButton)
            addView(actionButton("Back") { showMainPanel(); positionMenu() })
        }
        addView(mainPanel)
        addView(settingsPanel)
    }

    private fun modeButton(label: String, mode: MeasureMode): Button = actionButton(label) { onModeSelected(mode) }.also { modeButtons[mode] = it }

    private fun actionButton(label: String, action: () -> Unit): Button = Button(context).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setTextColor(Color.WHITE)
        contentDescription = label
        setOnClickListener { action() }
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private class SafeMenu(context: Context) : FrameLayout(context) {
        private var maxWidth = 0
        private var maxHeight = 0

        fun setMaximumSize(width: Int, height: Int) {
            maxWidth = width.coerceAtLeast(0)
            maxHeight = height.coerceAtLeast(0)
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = if (maxWidth > 0) MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST) else widthMeasureSpec
            val height = if (maxHeight > 0) MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST) else heightMeasureSpec
            super.onMeasure(width, height)
        }
    }
}
