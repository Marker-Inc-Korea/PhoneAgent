package ai.markr.phoneagent.agent

import ai.markr.phoneagent.agent.model.Snapshot
import ai.markr.phoneagent.agent.model.UiNode

/**
 * Caps and trims a raw node list so the serialized screen stays small, and
 * decides when the text tree is too sparse to act on (vision fallback).
 */
object SnapshotPolicy {
    const val MAX_NODES = 120
    const val MAX_TEXT = 80
    const val MIN_MEANINGFUL_NODES = 3

    /** Keep visible nodes that carry signal (text, or interactive), cap count. */
    fun reduce(nodes: List<UiNode>): List<UiNode> =
        nodes.asSequence()
            .filter { it.bounds.right > it.bounds.left && it.bounds.bottom > it.bounds.top }
            .filter { it.text.isNotBlank() || it.clickable || it.editable || it.scrollable }
            .take(MAX_NODES)
            .map { it.copy(text = truncate(it.text)) }
            .toList()

    fun needsVision(reduced: List<UiNode>): Boolean =
        reduced.count { it.text.isNotBlank() } < MIN_MEANINGFUL_NODES

    /** Truncate on a codepoint boundary so we never split a surrogate pair. */
    fun truncate(text: String, max: Int = MAX_TEXT): String {
        val collapsed = text.replace('\n', ' ').trim()
        if (collapsed.length <= max) return collapsed
        var end = max
        if (Character.isLowSurrogate(collapsed[end])) end--
        return collapsed.substring(0, end) + "…"
    }

    fun apply(raw: Snapshot): Snapshot {
        if (raw.error != null) return raw
        val reduced = reduce(raw.nodes)
        return raw.copy(nodes = reduced, needsVision = needsVision(reduced))
    }
}
