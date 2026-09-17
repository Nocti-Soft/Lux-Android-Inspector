package com.noctisoft.layoutmeasurement

import android.view.View
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * Compose capture bridge. Reads the unmerged semantics tree of a
 * Compose root view and returns nodes that carry a testTag (spec: tagged-node
 * granularity only for v1).
 */
object ComposeCapture {

    /** Call during Startup, before host compositions create their background modifiers. */
    fun enableColorInspection() {
        isDebugInspectorInfoEnabled = true
    }

    @JvmOverloads
    fun capture(view: View, includeColors: Boolean = false): List<CapturedNode> {
        val owner = (view as? RootForTest)?.semanticsOwner ?: return emptyList()
        val result = mutableListOf<CapturedNode>()
        collect(owner.unmergedRootSemanticsNode, result, includeColors)
        return result
    }

    private fun collect(node: SemanticsNode, out: MutableList<CapturedNode>, includeColors: Boolean) {
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        if (tag != null) {
            val b = node.boundsInWindow
            if (b.width > 0f && b.height > 0f) {
                out.add(
                    CapturedNode(
                        label = tag,
                        bounds = Bounds(
                            b.left.toInt(), b.top.toInt(),
                            b.right.toInt(), b.bottom.toInt()
                        ),
                        source = Source.COMPOSE,
                        colors = if (includeColors) ComposeColorCapture.capture(node) else null,
                    )
                )
            }
        }
        node.children.forEach { collect(it, out, includeColors) }
    }
}
