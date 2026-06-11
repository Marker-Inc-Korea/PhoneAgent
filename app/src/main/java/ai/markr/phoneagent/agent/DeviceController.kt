package ai.markr.phoneagent.agent

import ai.markr.phoneagent.agent.model.GlobalAction
import ai.markr.phoneagent.agent.model.ScrollDirection
import ai.markr.phoneagent.agent.model.Snapshot

/**
 * The agent's view of the device. Implemented on-device by the accessibility
 * service; faked in tests. Every method returns a human-readable result string
 * (or throws nothing) so the loop can feed outcomes back to the LLM.
 */
interface DeviceController {
    val isConnected: Boolean

    suspend fun snapshot(): Snapshot
    suspend fun screenshotJpeg(): ByteArray?

    suspend fun openApp(app: String): String
    suspend fun click(id: Int): String
    suspend fun setText(id: Int, text: String): String
    suspend fun imeEnter(id: Int?): String
    suspend fun scroll(direction: ScrollDirection, id: Int?): String
    suspend fun tapXy(x: Int, y: Int): String
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): String
    suspend fun global(name: GlobalAction): String
}
