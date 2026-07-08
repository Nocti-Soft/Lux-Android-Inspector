package dev.pinij.inspector

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup

/** Attaches/detaches the overlay and shake detector as activities resume/pause. */
object InspectorLifecycle : Application.ActivityLifecycleCallbacks {

    private val detectors = mutableMapOf<Activity, ShakeDetector>()

    override fun onActivityResumed(activity: Activity) {
        attachOverlay(activity)
        val detector = ShakeDetector { InspectorController.toggle() }
        detectors[activity] = detector
        detector.start(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        detectors.remove(activity)?.stop(activity)
    }

    private fun attachOverlay(activity: Activity) {
        val decor = activity.window.decorView as? ViewGroup ?: return
        if (decor.findViewWithTag<android.view.View>(InspectorOverlay.TAG) != null) return
        decor.addView(
            InspectorOverlay(activity),
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
