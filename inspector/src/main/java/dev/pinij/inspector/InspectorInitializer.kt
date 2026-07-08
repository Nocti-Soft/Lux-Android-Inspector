package dev.pinij.inspector

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

class InspectorInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val app = context.applicationContext as Application
        app.registerActivityLifecycleCallbacks(InspectorLifecycle)
        NotificationTrigger.show(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
