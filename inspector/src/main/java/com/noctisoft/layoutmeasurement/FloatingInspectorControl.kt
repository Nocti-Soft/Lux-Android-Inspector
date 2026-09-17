package com.noctisoft.layoutmeasurement

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import kotlin.math.hypot
import kotlin.math.roundToInt

internal class FloatingInspectorControl(context: Context) : FrameLayout(context) {
    var onModeSelected: (MeasureMode) -> Unit = {}
    var onStopRequested: () -> Unit = {}
    var onHideRequested: () -> Unit = {}
    var onNotificationControlsRequested: () -> Unit = {}
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
    private val floatingButton = ImageButton(context).apply {
        scaleType = ImageView.ScaleType.CENTER
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setImageResource(R.drawable.ic_inspector_target)
        background = indigoBackground(MEDIUM_INDIGO, dp(28), BRIGHT_INDIGO)
        backgroundTintList = null
        stateListAnimator = null
        elevation = dp(8).toFloat()
        contentDescription = "Layout inspector controls"
        isClickable = true
        isFocusable = true
        setOnClickListener { activateFloatingButton() }
    }
    private lateinit var hideButton: Button
    private lateinit var sessionButton: Button
    private lateinit var mainPanel: LinearLayout
    private lateinit var settingsPanel: LinearLayout
    private val modeButtons = linkedMapOf<MeasureMode, Button>()
    private val menu = SafeMenu(context).apply {
        visibility = GONE
        background = roundedBackground(DEEP_INDIGO, dp(16))
        elevation = dp(10).toFloat()
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
        menu.setMaximumSize(area.width.coerceAtMost(dp(216)), area.height)
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
        hideButton.contentDescription = hideButton.text
        sessionButton.text = if (isActive) "Stop Inspector" else "Start Inspector"
        sessionButton.contentDescription = sessionButton.text
        sessionButton.setCompoundDrawablesRelativeWithIntrinsicBounds(if (isActive) R.drawable.ic_inspector_stop else R.drawable.ic_inspector_search, 0, 0, 0)
        sessionButton.background = indigoBackground(if (isActive) RED_BADGE else MEDIUM_INDIGO, dp(10), BRIGHT_INDIGO)
        sessionButton.backgroundTintList = null
        ViewCompat.setStateDescription(floatingButton, if (isActive) "Inspector active: ${activeMode.label()}" else "Inspector idle")
        modeButtons.forEach { (mode, button) ->
            button.isSelected = isActive && mode == activeMode
            button.text = mode.label()
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
                    val dx = rawXAt(event, index) - downRawX
                    val dy = rawYAt(event, index) - downRawY
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

    private fun rawXAt(event: MotionEvent, index: Int): Float =
        if (android.os.Build.VERSION.SDK_INT >= 29) event.getRawX(index) else event.rawX + event.getX(index) - event.x

    private fun rawYAt(event: MotionEvent, index: Int): Float =
        if (android.os.Build.VERSION.SDK_INT >= 29) event.getRawY(index) else event.rawY + event.getY(index) - event.y

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
        val gap = dp(8)
        measureMenu(safeArea.height)
        val buttonLeft = floatingButton.x.roundToInt()
        val buttonTop = floatingButton.y.roundToInt()
        val buttonRight = buttonLeft + buttonSizePx
        val buttonBottom = buttonTop + buttonSizePx
        val leftRoom = buttonLeft - safeArea.left - gap
        val rightRoom = safeArea.right - buttonRight - gap
        val preferLeft = renderedPlacement.xFraction >= 0.5f
        val placeLeft = when {
            preferLeft && menu.measuredWidth <= leftRoom -> true
            !preferLeft && menu.measuredWidth <= rightRoom -> false
            menu.measuredWidth <= leftRoom -> true
            menu.measuredWidth <= rightRoom -> false
            else -> null
        }

        if (placeLeft != null) {
            menu.x = (if (placeLeft) buttonLeft - gap - menu.measuredWidth else buttonRight + gap).toFloat()
            menu.y = buttonTop
                .coerceIn(safeArea.top, (safeArea.bottom - menu.measuredHeight).coerceAtLeast(safeArea.top))
                .toFloat()
            return
        }

        val aboveRoom = (buttonTop - safeArea.top - gap).coerceAtLeast(0)
        val belowRoom = (safeArea.bottom - buttonBottom - gap).coerceAtLeast(0)
        val placeAbove = aboveRoom >= belowRoom
        measureMenu(if (placeAbove) aboveRoom else belowRoom)
        menu.x = buttonLeft
            .coerceIn(safeArea.left, (safeArea.right - menu.measuredWidth).coerceAtLeast(safeArea.left))
            .toFloat()
        menu.y = (if (placeAbove) buttonTop - gap - menu.measuredHeight else buttonBottom + gap).toFloat()
    }

    private fun measureMenu(maxHeight: Int) {
        menu.measure(
            MeasureSpec.makeMeasureSpec(safeArea.width.coerceAtMost(dp(216)), MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(maxHeight.coerceAtLeast(0), MeasureSpec.AT_MOST),
        )
        menu.layout(menu.left, menu.top, menu.left + menu.measuredWidth, menu.top + menu.measuredHeight)
    }

    private fun showMainPanel() {
        settingsPanel.visibility = GONE
        mainPanel.visibility = VISIBLE
        menu.background = roundedBackground(DEEP_INDIGO, dp(16))
    }

    private fun showSettingsPanel() {
        mainPanel.visibility = GONE
        settingsPanel.visibility = VISIBLE
        menu.background = roundedBackground(SETTINGS_SURFACE, dp(16))
        positionMenu()
    }

    private fun buildPanels(context: Context): FrameLayout = FrameLayout(context).apply {
        mainPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(modeButton("Size", MeasureMode.SIZE, R.drawable.ic_inspector_size))
            addView(modeButton("Gap", MeasureMode.GAP, R.drawable.ic_inspector_gap))
            addView(modeButton("Ruler", MeasureMode.RULER, R.drawable.ic_inspector_ruler))
            addView(modeButton("Bounds", MeasureMode.BOUNDS, R.drawable.ic_inspector_bounds))
            addView(modeButton("Colors", MeasureMode.COLORS, R.drawable.ic_inspector_colors))
            addView(actionButton("Settings", R.drawable.ic_inspector_settings) { showSettingsPanel() })
            addView(View(context).apply { background = roundedBackground(Color.argb(96, 255, 255, 255), 0) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(dp(8), dp(6), dp(8), dp(6)) })
            sessionButton = actionButton("Start Inspector", R.drawable.ic_inspector_search) {
                if (renderedIsActive) onStopRequested() else onModeSelected(renderedMode)
            }
            addView(sessionButton)
        }
        settingsPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = GONE
            addView(settingsButton("Back", R.drawable.ic_inspector_back) { showMainPanel(); positionMenu() })
            hideButton = settingsButton("Hide Inspector", R.drawable.ic_inspector_eye_off) {
                if (renderedCanHide) onHideRequested()
            }
            addView(hideButton)
            addView(settingsButton("Notification Controls", R.drawable.ic_inspector_bell) { onNotificationControlsRequested() })
        }
        addView(mainPanel)
        addView(settingsPanel)
    }

    private fun modeButton(label: String, mode: MeasureMode, iconRes: Int): Button =
        actionButton(label, iconRes) { onModeSelected(mode) }.also { modeButtons[mode] = it }

    private fun settingsButton(label: String, iconRes: Int, action: () -> Unit): Button =
        actionButton(label, iconRes, action).apply {
            background = indigoBackground(
                normal = SETTINGS_ROW,
                radius = dp(10),
                selected = SETTINGS_PRESSED,
                disabled = SETTINGS_DISABLED,
                pressed = SETTINGS_PRESSED,
            )
            backgroundTintList = null
            setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(DISABLED_TEXT, DEEP_INDIGO)))
        }

    private fun actionButton(label: String, iconRes: Int, action: () -> Unit): Button = Button(context).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        minHeight = dp(48)
        minimumHeight = dp(48)
        gravity = Gravity.CENTER_VERTICAL
        includeFontPadding = false
        setPadding(dp(16), 0, dp(16), 0)
        compoundDrawablePadding = dp(12)
        setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0)
        background = indigoBackground(MEDIUM_INDIGO, dp(10), BRIGHT_INDIGO)
        backgroundTintList = null
        stateListAnimator = null
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(Color.argb(160, 255, 255, 255), Color.WHITE)))
        contentDescription = label
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(3), 0, dp(3))
        }
        setOnClickListener { action() }
    }

    private fun indigoBackground(
        normal: Int,
        radius: Int,
        selected: Int,
        disabled: Int = DISABLED_INDIGO,
        pressed: Int = BRIGHT_INDIGO,
    ): Drawable = StateListDrawable().apply {
        addState(intArrayOf(-android.R.attr.state_enabled), roundedBackground(disabled, radius))
        addState(intArrayOf(android.R.attr.state_pressed), roundedBackground(pressed, radius))
        addState(intArrayOf(android.R.attr.state_selected), roundedBackground(selected, radius))
        addState(intArrayOf(), roundedBackground(normal, radius))
    }

    private fun roundedBackground(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun MeasureMode.label(): String = name.lowercase().replaceFirstChar { it.uppercase() }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private companion object {
        const val DEEP_INDIGO = 0xFF25236D.toInt()
        const val MEDIUM_INDIGO = 0xFF37358F.toInt()
        const val BRIGHT_INDIGO = 0xFF4F46E5.toInt()
        const val DISABLED_INDIGO = 0xFF4B4A70.toInt()
        const val RED_BADGE = 0xFFD32F2F.toInt()
        const val SETTINGS_SURFACE = 0xFFF7F7FF.toInt()
        const val SETTINGS_ROW = 0xFFFFFFFF.toInt()
        const val SETTINGS_PRESSED = 0xFFE5E3FF.toInt()
        const val SETTINGS_DISABLED = 0xFFE8E8F0.toInt()
        const val DISABLED_TEXT = 0xFF77758F.toInt()
    }

    private class SafeMenu(context: Context) : FrameLayout(context) {
        private var maxWidth = Int.MAX_VALUE
        private var maxHeight = Int.MAX_VALUE

        fun setMaximumSize(width: Int, height: Int) {
            maxWidth = width.coerceAtLeast(0)
            maxHeight = height.coerceAtLeast(0)
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(capped(widthMeasureSpec, maxWidth), capped(heightMeasureSpec, maxHeight))
        }

        private fun capped(spec: Int, maximum: Int): Int {
            val mode = MeasureSpec.getMode(spec)
            val size = if (mode == MeasureSpec.UNSPECIFIED) maximum else MeasureSpec.getSize(spec).coerceAtMost(maximum)
            return MeasureSpec.makeMeasureSpec(size, if (mode == MeasureSpec.UNSPECIFIED) MeasureSpec.AT_MOST else mode)
        }
    }
}
