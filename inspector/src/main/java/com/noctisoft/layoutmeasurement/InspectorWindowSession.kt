package com.noctisoft.layoutmeasurement

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

/** One overlay per resumed Activity, reparented into its active dialog/Activity window. */
internal class InspectorWindowSession(private val activity: Activity) {
    private val handler = Handler(Looper.getMainLooper())
    private val overlay = InspectorOverlay(activity, InspectorPlacementStore(activity.applicationContext))
    private var host: ViewGroup? = null
    private var running = false
    private val refresh = Runnable { refreshWindow() }
    private val stateListener: () -> Unit = { requestRefresh() }
    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { requestRefresh() }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = requestRefresh()
        override fun onViewDetachedFromWindow(view: View) = requestRefresh()
    }

    fun start() {
        if (running) return
        running = true
        InspectorController.addListener(stateListener)
        refreshWindow()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(refresh)
        InspectorController.removeListener(stateListener)
        detach()
    }

    private fun requestRefresh() {
        handler.removeCallbacks(refresh)
        if (running) handler.post(refresh)
    }

    private fun refreshWindow() {
        handler.removeCallbacks(refresh)
        if (!running || activity.isDestroyed || activity.isFinishing) return
        val inspecting = InspectorController.isActive || InspectorController.controlState != FloatingControlState.HIDDEN
        val target = if (inspecting) InspectorWindowRoots.topRoot(activity) else activity.window.decorView as? ViewGroup
        if (target !== host || overlay.parent !== target) {
            detach()
            if (target != null) {
                host = target
                target.addOnAttachStateChangeListener(attachListener)
                target.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
                // App dialogs can contain elevated sheet content. The inspector must be
                // above those children for both Android drawing and touch dispatch.
                overlay.elevation = (0 until target.childCount).maxOfOrNull { target.getChildAt(it).z }
                    ?.coerceAtLeast(0f)?.plus(activity.resources.displayMetrics.density) ?: 0f
                target.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        }
        // Public window-list change callbacks are unavailable on our compileSdk/minSdk.
        // Check roots only while controls/inspection are in use, never in the background.
        if (inspecting) handler.postDelayed(refresh, WINDOW_CHECK_INTERVAL_MS)
    }

    private fun detach() {
        host?.let { old ->
            old.removeOnAttachStateChangeListener(attachListener)
            if (old.viewTreeObserver.isAlive) old.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
        }
        overlay.clearWindowSelection()
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        host = null
    }

    private companion object {
        const val WINDOW_CHECK_INTERVAL_MS = 200L
    }
}
