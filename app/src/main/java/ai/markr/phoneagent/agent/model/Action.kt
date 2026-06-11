package ai.markr.phoneagent.agent.model

/**
 * One action the LLM can request per turn. Pure data, no Android dependency,
 * so the whole agent loop is unit-testable on the JVM.
 */
sealed interface Action {
    data class OpenApp(val app: String) : Action
    data class Tap(val id: Int) : Action
    data class TapXy(val x: Int, val y: Int) : Action
    data class SetText(val id: Int, val text: String) : Action
    /** Press the IME action (enter/search/send) on an editable field. */
    data class Enter(val id: Int?) : Action
    data class Scroll(val direction: ScrollDirection, val id: Int?) : Action
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val durationMs: Int) : Action
    data class Global(val name: GlobalAction) : Action
    data object Screenshot : Action
    data class Wait(val ms: Int) : Action
    data class Done(val answer: String) : Action
    data class Abort(val reason: String) : Action
}

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }

enum class GlobalAction { BACK, HOME, RECENTS, NOTIFICATIONS }
