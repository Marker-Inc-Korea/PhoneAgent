package ai.markr.phoneagent.agent

import ai.markr.phoneagent.agent.model.Action
import ai.markr.phoneagent.agent.model.GlobalAction
import ai.markr.phoneagent.agent.model.ScrollDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionDispatcherTest {

    private fun controller() = FakeDeviceController(listOf(snapshot("p")))

    @Test fun tap_calls_click() = runTest { // AC-2
        val c = controller()
        ActionDispatcher(c).dispatch(Action.Tap(7))
        assertTrue(c.calls.contains("click:7"))
    }

    @Test fun set_text_calls_setText() = runTest {
        val c = controller()
        ActionDispatcher(c).dispatch(Action.SetText(2, "안녕"))
        assertTrue(c.calls.contains("setText:2=안녕"))
    }

    @Test fun enter_calls_imeEnter() = runTest {
        val c = controller()
        ActionDispatcher(c).dispatch(Action.Enter(3))
        assertTrue(c.calls.contains("imeEnter:3"))
    }

    @Test fun scroll_open_global_swipe_tapxy() = runTest {
        val c = controller()
        val d = ActionDispatcher(c)
        d.dispatch(Action.Scroll(ScrollDirection.DOWN, null))
        d.dispatch(Action.OpenApp("Gmail"))
        d.dispatch(Action.Global(GlobalAction.BACK))
        d.dispatch(Action.Swipe(0, 0, 1, 1, 100))
        d.dispatch(Action.TapXy(5, 6))
        assertEquals(listOf("scroll:DOWN", "openApp:Gmail", "global:BACK", "swipe", "tapXy:5,6"), c.calls)
    }
}
