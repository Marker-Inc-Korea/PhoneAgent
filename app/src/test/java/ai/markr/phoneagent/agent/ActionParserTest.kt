package ai.markr.phoneagent.agent

import ai.markr.phoneagent.agent.model.Action
import ai.markr.phoneagent.agent.model.GlobalAction
import ai.markr.phoneagent.agent.model.ScrollDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionParserTest {
    private val parser = ActionParser()

    @Test fun parses_plain_tap() {
        val r = parser.parse("""{"thought":"열기","action":{"type":"tap","id":3}}""")
        assertEquals(Action.Tap(3), r.getOrThrow().action)
        assertEquals("열기", r.getOrThrow().thought)
    }

    @Test fun parses_inside_markdown_fence() { // AC-4
        val raw = "여기 응답입니다:\n```json\n{\"thought\":\"x\",\"action\":{\"type\":\"done\",\"answer\":\"끝\"}}\n```\n감사합니다"
        assertEquals(Action.Done("끝"), parser.parse(raw).getOrThrow().action)
    }

    @Test fun ignores_braces_inside_strings() {
        val raw = """{"thought":"a } b { c","action":{"type":"set_text","id":2,"text":"x{y}z"}}"""
        assertEquals(Action.SetText(2, "x{y}z"), parser.parse(raw).getOrThrow().action)
    }

    @Test fun parses_open_app_and_global_and_scroll() {
        assertEquals(Action.OpenApp("Gmail"),
            parser.parse("""{"action":{"type":"open_app","app":"Gmail"}}""").getOrThrow().action)
        assertEquals(Action.Global(GlobalAction.BACK),
            parser.parse("""{"action":{"type":"global","name":"back"}}""").getOrThrow().action)
        assertEquals(Action.Scroll(ScrollDirection.DOWN, null),
            parser.parse("""{"action":{"type":"scroll","direction":"down"}}""").getOrThrow().action)
    }

    @Test fun screenshot_and_wait() {
        assertEquals(Action.Screenshot,
            parser.parse("""{"action":{"type":"screenshot"}}""").getOrThrow().action)
        assertEquals(Action.Wait(500),
            parser.parse("""{"action":{"type":"wait","ms":500}}""").getOrThrow().action)
    }

    @Test fun parses_enter_with_and_without_id() {
        assertEquals(Action.Enter(2),
            parser.parse("""{"action":{"type":"enter","id":2}}""").getOrThrow().action)
        assertEquals(Action.Enter(null),
            parser.parse("""{"action":{"type":"submit"}}""").getOrThrow().action)
    }

    @Test fun unknown_type_fails() {
        assertTrue(parser.parse("""{"action":{"type":"levitate"}}""").isFailure)
    }

    @Test fun no_json_fails() {
        assertTrue(parser.parse("아무 JSON도 없습니다").isFailure)
    }

    @Test fun missing_action_fails() {
        assertTrue(parser.parse("""{"thought":"x"}""").isFailure)
    }
}
