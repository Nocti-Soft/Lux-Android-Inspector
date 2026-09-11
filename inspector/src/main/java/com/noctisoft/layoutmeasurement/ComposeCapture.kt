package com.noctisoft.layoutmeasurement

import android.view.View
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

/**
 * The ONLY file touching Compose APIs. Reads the unmerged semantics tree of a
 * Compose root view and returns nodes that carry a testTag (spec: tagged-node
 * granularity only for v1).
 */
object ComposeCapture {

    fun capture(view: View): List<CapturedNode> {
        val owner = (view as? RootForTest)?.semanticsOwner ?: return emptyList()
        val result = mutableListOf<CapturedNode>()
        collect(owner.unmergedRootSemanticsNode, result)
        return result
    }

    private fun collect(node: SemanticsNode, out: MutableList<CapturedNode>) {
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
                        source = Source.COMPOSE
                    )
                )
            }
        }
        node.children.forEach { collect(it, out) }
    }
}
