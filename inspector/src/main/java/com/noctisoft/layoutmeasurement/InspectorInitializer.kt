package com.noctisoft.layoutmeasurement

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

class InspectorInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val app = context.applicationContext as Application
        synchronized(this) {
            if (initializedApplication === app) return
            initializedApplication = app
        }
        ComposeCapture.enableColorInspection()
        InspectorController.restorePlacement(InspectorPlacementStore(app).load())
        InspectorController.stopInspection()
        InspectorController.hideControlsForStartup()
        NotificationTrigger.initialize(app)
        app.registerActivityLifecycleCallbacks(InspectorLifecycle)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()

    private companion object {
        var initializedApplication: Application? = null
    }
}
