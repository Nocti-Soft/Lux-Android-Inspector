package com.noctisoft.layoutmeasurement

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector

/** Finds app-owned modal roots without changing host callbacks, focus, or window flags. */
internal object InspectorWindowRoots {
    private var warnedAboutDiscovery = false

    fun topRoot(activity: Activity): ViewGroup? {
        val base = activity.window.decorView as? ViewGroup ?: return null
        val roots = globalViews().filterIsInstance<ViewGroup>().filter { root ->
            root.isAttachedToWindow && root.isShown &&
                belongsTo(root, activity, base) && isInspectableWindow(root)
        }
        // Focus is authoritative once Android has dispatched it; registration order is a
        // fallback during show/dismiss transitions (and for unfocused test windows).
        return roots.lastOrNull { it.hasWindowFocus() }
            ?: roots.lastOrNull()
            ?: base
    }

    private fun belongsTo(root: ViewGroup, activity: Activity, base: ViewGroup): Boolean {
        if (root === base) return true
        if (root.display?.displayId != base.display?.displayId) return false
        val owner = activityFrom(root.context)
        if (owner != null) return owner === activity
        // Dialogs created with a window context may not expose an Activity wrapper.
        // Require an explicit matching app token, never just the process/package name.
        val token = (root.layoutParams as? WindowManager.LayoutParams)?.token ?: return false
        return token == activity.window.attributes.token || token == base.applicationWindowToken ||
            token == base.windowToken
    }

    private fun isInspectableWindow(root: ViewGroup): Boolean {
        val params = root.layoutParams as? WindowManager.LayoutParams ?: return false
        return params.type in WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW..WindowManager.LayoutParams.LAST_APPLICATION_WINDOW &&
            params.flags and (WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0
    }

    private fun activityFrom(context: Context): Activity? {
        var current = context
        repeat(16) {
            if (current is Activity) return current as Activity
            val next = (current as? ContextWrapper)?.baseContext ?: return null
            if (next === current) return null
            current = next
        }
        return null
    }

    private fun globalViews(): List<View> = try {
        if (Build.VERSION.SDK_INT >= 29) WindowInspector.getGlobalWindowViews() else legacyViews()
    } catch (error: Exception) {
        warnOnce(error)
        emptyList()
    } catch (error: LinkageError) {
        warnOnce(error)
        emptyList()
    }

    // API 24-28 has no public equivalent of WindowInspector. This is a guarded read,
    // confined to old Android versions; no hidden-API exemptions or framework writes.
    // OEM restrictions fall back to Activity-only inspection with a one-time warning.
    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun legacyViews(): List<View> {
        val type = Class.forName("android.view.WindowManagerGlobal")
        val manager = type.getDeclaredMethod("getInstance").invoke(null)
        val field = type.getDeclaredField("mViews").apply { isAccessible = true }
        return (field.get(manager) as? List<*>)?.filterIsInstance<View>().orEmpty()
    }

    private fun warnOnce(error: Throwable) {
        if (warnedAboutDiscovery) return
        warnedAboutDiscovery = true
        Log.w("LayoutInspector", "Dialog window discovery unavailable; using Activity window", error)
    }
}
