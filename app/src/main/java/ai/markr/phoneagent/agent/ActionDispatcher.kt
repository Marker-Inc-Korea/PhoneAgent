package ai.markr.phoneagent.agent

import ai.markr.phoneagent.agent.model.Action

/**
 * Executes a device-affecting [Action] against the [DeviceController] and
 * returns a result string. Control actions (done/abort/screenshot) are handled
 * by [AgentLoop], not here.
 */
class ActionDispatcher(private val controller: DeviceController) {

    suspend fun dispatch(action: Action): String = when (action) {
        is Action.OpenApp -> controller.openApp(action.app)
        is Action.Tap -> controller.click(action.id)
        is Action.TapXy -> controller.tapXy(action.x, action.y)
        is Action.SetText -> controller.setText(action.id, action.text)
        is Action.Enter -> controller.imeEnter(action.id)
        is Action.Scroll -> controller.scroll(action.direction, action.id)
        is Action.Swipe -> controller.swipe(action.x1, action.y1, action.x2, action.y2, action.durationMs)
        is Action.Global -> controller.global(action.name)
        is Action.Wait -> "대기 ${action.ms}ms"
        is Action.Screenshot -> "스크린샷 요청됨"
        is Action.Done -> "완료"
        is Action.Abort -> "중단: ${action.reason}"
    }
}
