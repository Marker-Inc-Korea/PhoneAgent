package ai.markr.phoneagent.agent.model

/** A single interactive/text element extracted from the accessibility tree. */
data class UiNode(
    val id: Int,
    val role: String,
    val text: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val bounds: Bounds,
)

data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

/** A structured screen capture handed to the agent each turn. */
data class Snapshot(
    val packageName: String,
    val activity: String,
    val nodes: List<UiNode>,
    /** True when the text tree is too sparse to act on -> vision fallback. */
    val needsVision: Boolean,
    /** Set when the snapshot could not be taken (service disconnected, etc.). */
    val error: String? = null,
) {
    fun nodeById(id: Int): UiNode? = nodes.firstOrNull { it.id == id }
}
