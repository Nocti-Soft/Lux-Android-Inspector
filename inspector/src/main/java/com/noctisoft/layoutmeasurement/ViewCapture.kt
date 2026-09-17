package com.noctisoft.layoutmeasurement

import android.view.View
import android.view.ViewGroup

object ViewCapture {

    private const val OVERLAY_TAG = "com.noctisoft.layoutmeasurement.OVERLAY"

    /** All visible widgets under [root], in window coordinates. */
    @JvmOverloads
    fun captureAll(root: View, includeColors: Boolean = false): List<CapturedNode> {
        val out = mutableListOf<CapturedNode>()
        walk(root, out, includeColors)
        return out
    }

    private fun walk(view: View, out: MutableList<CapturedNode>, includeColors: Boolean) {
        if (view.tag == OVERLAY_TAG) return // never measure ourselves
        if (view.visibility != View.VISIBLE || view.width == 0 || view.height == 0) return

        val composeNodes = ComposeCapture.capture(view, includeColors)
        if (composeNodes.isNotEmpty()) {
            out.add(node(view, includeColors)) // the island itself
            out.addAll(composeNodes)
            return // spec: don't descend past a Compose root
        }

        out.add(node(view, includeColors))
        if (view is ViewGroup) {
            // An untagged Compose island: its AndroidComposeView child still walks
            // here and is added as a single leaf rect, which is the spec behavior.
            for (i in 0 until view.childCount) walk(view.getChildAt(i), out, includeColors)
        }
    }

    private fun node(view: View, includeColors: Boolean): CapturedNode {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        return CapturedNode(
            label = label(view),
            bounds = Bounds(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height),
            source = Source.XML,
            colors = if (includeColors) ViewColorCapture.capture(view) else null,
        )
    }

    private fun label(view: View): String {
        val cls = view.javaClass.simpleName
        val id = view.id
        if (id == View.NO_ID) return cls
        return runCatching { "$cls/${view.resources.getResourceEntryName(id)}" }
            .getOrDefault(cls)
    }
}
