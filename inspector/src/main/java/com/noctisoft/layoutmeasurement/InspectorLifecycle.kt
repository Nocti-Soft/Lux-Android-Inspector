package com.noctisoft.layoutmeasurement

import android.app.Activity
import android.app.Application
import android.os.Bundle

/** Tracks the active Activity and its modal windows without intercepting host callbacks. */
object InspectorLifecycle : Application.ActivityLifecycleCallbacks {
    private val detectors = mutableMapOf<Activity, ShakeDetector>()
    private val windows = mutableMapOf<Activity, InspectorWindowSession>()

    override fun onActivityResumed(activity: Activity) {
        windows.remove(activity)?.stop()
        windows[activity] = InspectorWindowSession(activity).also { it.start() }
        // Re-post each resume: covers permission granted after initialization.
        NotificationTrigger.show(activity)
        detectors.remove(activity)?.stop(activity)
        val detector = ShakeDetector { InspectorController.revealControls(RevealSource.SHAKE) }
        detectors[activity] = detector
        detector.start(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        windows.remove(activity)?.stop()
        detectors.remove(activity)?.stop(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) {
        windows.remove(activity)?.stop()
        detectors.remove(activity)?.stop(activity)
    }
}
