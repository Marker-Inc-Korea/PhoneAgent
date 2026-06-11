package ai.markr.phoneagent.agent

import ai.markr.phoneagent.agent.model.Bounds
import ai.markr.phoneagent.agent.model.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotPolicyTest {

    @Test fun caps_node_count() { // AC-3
        val nodes = (1..200).map { node(it, text = "항목 $it") }
        assertEquals(SnapshotPolicy.MAX_NODES, SnapshotPolicy.reduce(nodes).size)
    }

    @Test fun truncates_long_text_to_max() { // AC-3
        val long = "가".repeat(500)
        val out = SnapshotPolicy.truncate(long)
        assertTrue(out.length <= SnapshotPolicy.MAX_TEXT + 1) // +1 for ellipsis
        assertTrue(out.endsWith("…"))
    }

    @Test fun drops_empty_and_invisible_nodes() {
        val nodes = listOf(
            node(1, text = "보임"),
            UiNode(2, "View", "", false, false, false, Bounds(0, 0, 0, 0)), // zero-size, no signal
            node(3, clickable = true),
        )
        val out = SnapshotPolicy.reduce(nodes)
        assertEquals(listOf(1, 3), out.map { it.id })
    }

    @Test fun flags_vision_when_sparse() { // AC-6
        val sparse = listOf(node(1, clickable = true), node(2, clickable = true))
        assertTrue(SnapshotPolicy.needsVision(SnapshotPolicy.reduce(sparse)))
    }

    @Test fun no_vision_when_text_rich() {
        val rich = (1..10).map { node(it, text = "텍스트$it") }
        assertFalse(SnapshotPolicy.needsVision(SnapshotPolicy.reduce(rich)))
    }
}
