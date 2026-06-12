package ai.markr.phoneagent.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test fun system_prompt_includes_task_and_vision_action_when_available() {
        val p = PromptBuilder.systemPrompt("Gmail 새 메일 요약", visionAvailable = true)
        assertTrue(p.contains("Gmail 새 메일 요약"))
        assertTrue(p.contains("screenshot"))
    }

    @Test fun system_prompt_omits_screenshot_without_vision() {
        val p = PromptBuilder.systemPrompt("작업", visionAvailable = false)
        assertFalse(p.contains("\"type\":\"screenshot\""))
    }

    @Test fun voice_mode_adds_conciseness_rule() {
        val p = PromptBuilder.systemPrompt("작업", visionAvailable = false, voiceConcise = true)
        assertTrue(p.contains("음성 모드"))
        assertTrue(p.contains("1~2문장"))
    }

    @Test fun non_voice_mode_omits_conciseness_rule() {
        val p = PromptBuilder.systemPrompt("작업", visionAvailable = false, voiceConcise = false)
        assertFalse(p.contains("음성 모드"))
    }

    @Test fun observation_lists_nodes_with_ids() {
        val snap = snapshot("com.google.android.gm",
            node(1, text = "받은편지함", clickable = true),
            node(2, text = "홍길동 · 회의 일정", clickable = true))
        val o = PromptBuilder.observation(snap, loopWarning = false)
        assertTrue(o.contains("com.google.android.gm"))
        assertTrue(o.contains("[1]"))
        assertTrue(o.contains("받은편지함"))
        assertTrue(o.contains("clickable"))
    }

    @Test fun observation_injects_loop_warning() { // AC-8
        val o = PromptBuilder.observation(snapshot("p", node(1, text = "x")), loopWarning = true)
        assertTrue(o.contains("반복"))
    }

    @Test fun observation_reports_error() {
        val snap = snapshot("p").copy(error = "서비스 끊김")
        val o = PromptBuilder.observation(snap, loopWarning = false)
        assertTrue(o.contains("서비스 끊김"))
    }
}
