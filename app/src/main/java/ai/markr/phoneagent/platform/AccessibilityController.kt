package ai.markr.phoneagent.platform

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import ai.markr.phoneagent.agent.DeviceController
import ai.markr.phoneagent.agent.model.GlobalAction
import ai.markr.phoneagent.agent.model.ScrollDirection
import ai.markr.phoneagent.agent.model.Snapshot
import kotlinx.coroutines.delay

/**
 * [DeviceController] backed by the running [PhoneAccessibilityService]. Holds
 * the handle map from the most recent snapshot so actions can target nodes by id.
 */
class AccessibilityController(private val appContext: Context) : DeviceController {

    private val launcher = AppLauncher(appContext)
    private var handles: Map<Int, AccessibilityNodeInfo> = emptyMap()

    private val service: PhoneAccessibilityService?
        get() = PhoneAccessibilityService.instance

    override val isConnected: Boolean get() = PhoneAccessibilityService.isConnected

    override suspend fun snapshot(): Snapshot {
        val svc = service ?: return Snapshot("", "", emptyList(), needsVision = false, error = "접근성 서비스 미연결")
        val tree = svc.currentTree()
        handles = tree.handles
        return Snapshot(
            packageName = svc.lastPackage,
            activity = svc.lastActivity,
            nodes = tree.nodes,
            needsVision = false,
        )
    }

    override suspend fun screenshotJpeg(): ByteArray? = service?.captureScreenshotJpeg()

    override suspend fun openApp(app: String): String {
        val result = launcher.launch(app)
        delay(1200)
        return result
    }

    override suspend fun click(id: Int): String {
        val node = handles[id] ?: return "대상을 찾을 수 없음(id=$id). 최신 화면을 다시 확인하세요."
        val clickTarget = node.takeIf { it.isClickable } ?: firstClickableAncestor(node)
        if (clickTarget != null && clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "탭 완료(id=$id)"
        }
        val svc = service ?: return "서비스 미연결"
        val b = boundsOf(node)
        val ok = svc.dispatchTap(b.centerX, b.centerY)
        return if (ok) "좌표 탭으로 처리(id=$id)" else "탭 실패(id=$id)"
    }

    override suspend fun setText(id: Int, text: String): String {
        val node = handles[id] ?: return "대상을 찾을 수 없음(id=$id)."
        val editable = node.takeIf { it.isEditable } ?: return "입력 가능한 요소가 아닙니다(id=$id)."
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return if (ok) "입력 완료(id=$id)" else "입력 실패(id=$id)"
    }

    override suspend fun imeEnter(id: Int?): String {
        val node = id?.let { handles[it] }
            ?: handles.values.firstOrNull { it.isEditable && it.isFocused }
            ?: handles.values.firstOrNull { it.isEditable }
            ?: return "엔터를 보낼 입력란을 찾지 못했습니다."
        val ok = node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        return if (ok) "엔터(검색/전송) 실행" else "엔터 실행 실패. 검색/전송 버튼을 탭해 보세요."
    }

    override suspend fun scroll(direction: ScrollDirection, id: Int?): String {
        val target = id?.let { handles[it] } ?: handles.values.firstOrNull { it.isScrollable }
        if (target != null && target.isScrollable) {
            val action = when (direction) {
                ScrollDirection.DOWN, ScrollDirection.RIGHT -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                ScrollDirection.UP, ScrollDirection.LEFT -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
            if (target.performAction(action)) return "스크롤 완료($direction)"
        }
        return gestureScroll(direction)
    }

    private suspend fun gestureScroll(direction: ScrollDirection): String {
        val svc = service ?: return "서비스 미연결"
        val dm = appContext.resources.displayMetrics
        val cx = dm.widthPixels / 2
        val cy = dm.heightPixels / 2
        val dx = dm.widthPixels / 3
        val dy = dm.heightPixels / 3
        val ok = when (direction) {
            ScrollDirection.DOWN -> svc.dispatchSwipe(cx, cy + dy, cx, cy - dy, 300)
            ScrollDirection.UP -> svc.dispatchSwipe(cx, cy - dy, cx, cy + dy, 300)
            ScrollDirection.LEFT -> svc.dispatchSwipe(cx + dx, cy, cx - dx, cy, 300)
            ScrollDirection.RIGHT -> svc.dispatchSwipe(cx - dx, cy, cx + dx, cy, 300)
        }
        return if (ok) "제스처 스크롤($direction)" else "스크롤 실패"
    }

    override suspend fun tapXy(x: Int, y: Int): String {
        val ok = service?.dispatchTap(x, y) ?: return "서비스 미연결"
        return if (ok) "좌표 탭($x,$y)" else "좌표 탭 실패"
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): String {
        val ok = service?.dispatchSwipe(x1, y1, x2, y2, durationMs) ?: return "서비스 미연결"
        return if (ok) "스와이프 완료" else "스와이프 실패"
    }

    override suspend fun global(name: GlobalAction): String {
        val svc = service ?: return "서비스 미연결"
        val action = when (name) {
            GlobalAction.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
            GlobalAction.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
            GlobalAction.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
            GlobalAction.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        }
        val ok = svc.performGlobalAction(action)
        delay(500)
        return if (ok) "전역 동작: $name" else "전역 동작 실패: $name"
    }

    private fun firstClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current = node.parent
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    private fun boundsOf(node: AccessibilityNodeInfo): ai.markr.phoneagent.agent.model.Bounds {
        val r = android.graphics.Rect().also { node.getBoundsInScreen(it) }
        return ai.markr.phoneagent.agent.model.Bounds(r.left, r.top, r.right, r.bottom)
    }
}
