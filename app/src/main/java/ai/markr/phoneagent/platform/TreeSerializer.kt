package ai.markr.phoneagent.platform

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import ai.markr.phoneagent.agent.model.Bounds
import ai.markr.phoneagent.agent.model.UiNode

/** A serialized screen plus the live node handles needed to act on it. */
class SerializedTree(
    val nodes: List<UiNode>,
    val handles: Map<Int, AccessibilityNodeInfo>,
)

/**
 * Walks an [AccessibilityNodeInfo] tree (BFS) into flat [UiNode]s, assigning a
 * stable per-snapshot id to each and keeping a handle map so the controller can
 * act on a node by id.
 */
object TreeSerializer {
    private const val MAX_VISIT = 600

    fun serialize(root: AccessibilityNodeInfo?): SerializedTree {
        if (root == null) return SerializedTree(emptyList(), emptyMap())
        val nodes = ArrayList<UiNode>()
        val handles = HashMap<Int, AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var id = 0
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_VISIT) {
            val n = queue.removeFirst()
            visited++
            val text = nodeText(n)
            val clickable = n.isClickable
            val editable = n.isEditable
            val scrollable = n.isScrollable
            if ((text.isNotBlank() || clickable || editable || scrollable) && n.isVisibleToUser) {
                val rect = Rect().also { n.getBoundsInScreen(it) }
                nodes.add(
                    UiNode(
                        id = id,
                        role = role(n),
                        text = text,
                        clickable = clickable,
                        editable = editable,
                        scrollable = scrollable,
                        bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                    ),
                )
                handles[id] = n
                id++
            }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return SerializedTree(nodes, handles)
    }

    private fun nodeText(n: AccessibilityNodeInfo): String {
        val t = n.text?.toString()?.trim().orEmpty()
        if (t.isNotEmpty()) return t
        return n.contentDescription?.toString()?.trim().orEmpty()
    }

    private fun role(n: AccessibilityNodeInfo): String {
        val cls = n.className?.toString()?.substringAfterLast('.').orEmpty()
        return cls.ifBlank { "View" }
    }
}
