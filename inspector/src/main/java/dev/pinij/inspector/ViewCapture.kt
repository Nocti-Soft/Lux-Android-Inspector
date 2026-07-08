package dev.pinij.inspector

import android.view.View
import android.view.ViewGroup

object ViewCapture {

    private const val OVERLAY_TAG = "dev.pinij.inspector.OVERLAY"

    /** All visible widgets under [root], in window coordinates. */
    fun captureAll(root: View): List<CapturedNode> {
        val out = mutableListOf<CapturedNode>()
        walk(root, out)
        return out
    }

    private fun walk(view: View, out: MutableList<CapturedNode>) {
        if (view.tag == OVERLAY_TAG) return // never measure ourselves
        if (view.visibility != View.VISIBLE || view.width == 0 || view.height == 0) return

        val composeNodes = ComposeCapture.capture(view)
        if (composeNodes.isNotEmpty()) {
            out.add(node(view)) // the island itself
            out.addAll(composeNodes)
            return // spec: don't descend past a Compose root
        }

        out.add(node(view))
        if (view is ViewGroup) {
            // An untagged Compose island: its AndroidComposeView child still walks
            // here and is added as a single leaf rect, which is the spec behavior.
            for (i in 0 until view.childCount) walk(view.getChildAt(i), out)
        }
    }

    private fun node(view: View): CapturedNode {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        return CapturedNode(
            label = label(view),
            bounds = Bounds(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height),
            source = Source.XML
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
