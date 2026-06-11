package ai.markr.phoneagent.agent

import ai.markr.phoneagent.agent.model.Bounds
import ai.markr.phoneagent.agent.model.GlobalAction
import ai.markr.phoneagent.agent.model.ScrollDirection
import ai.markr.phoneagent.agent.model.Snapshot
import ai.markr.phoneagent.agent.model.UiNode

/** Records calls and returns scripted snapshots. */
class FakeDeviceController(
    private val snapshots: List<Snapshot>,
    override val isConnected: Boolean = true,
    private val screenshot: ByteArray? = byteArrayOf(1, 2, 3),
) : DeviceController {
    val calls = mutableListOf<String>()
    private var snapIndex = 0

    override suspend fun snapshot(): Snapshot {
        val s = snapshots[snapIndex.coerceAtMost(snapshots.lastIndex)]
        snapIndex++
        return s
    }

    override suspend fun screenshotJpeg(): ByteArray? {
        calls.add("screenshot")
        return screenshot
    }

    override suspend fun openApp(app: String): String { calls.add("openApp:$app"); return "앱 실행: $app" }
    override suspend fun click(id: Int): String { calls.add("click:$id"); return "탭 완료: $id" }
    override suspend fun setText(id: Int, text: String): String { calls.add("setText:$id=$text"); return "입력 완료" }
    override suspend fun imeEnter(id: Int?): String { calls.add("imeEnter:$id"); return "엔터" }
    override suspend fun scroll(direction: ScrollDirection, id: Int?): String { calls.add("scroll:$direction"); return "스크롤 완료" }
    override suspend fun tapXy(x: Int, y: Int): String { calls.add("tapXy:$x,$y"); return "좌표 탭" }
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): String { calls.add("swipe"); return "스와이프" }
    override suspend fun global(name: GlobalAction): String { calls.add("global:$name"); return "전역: $name" }
}

/** Returns scripted raw responses in order. */
class ScriptedLlmClient(
    private val responses: List<String>,
    override val supportsVision: Boolean = false,
) : LlmClient {
    val imageSeen = mutableListOf<Boolean>()
    private var i = 0

    override suspend fun complete(system: String, messages: List<LlmMessage>, imageJpeg: ByteArray?): String {
        imageSeen.add(imageJpeg != null)
        return responses[i++.coerceAtMost(responses.lastIndex)]
    }
}

fun node(id: Int, text: String = "", clickable: Boolean = false, editable: Boolean = false, scrollable: Boolean = false) =
    UiNode(id, "View", text, clickable, editable, scrollable, Bounds(0, id * 10, 100, id * 10 + 8))

fun snapshot(pkg: String, vararg nodes: UiNode, activity: String = "Main") =
    Snapshot(pkg, activity, nodes.toList(), needsVision = false)
