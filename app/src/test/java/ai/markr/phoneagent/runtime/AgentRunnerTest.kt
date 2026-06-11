package ai.markr.phoneagent.runtime

import ai.markr.phoneagent.agent.FakeDeviceController
import ai.markr.phoneagent.agent.ScriptedLlmClient
import ai.markr.phoneagent.agent.node
import ai.markr.phoneagent.agent.snapshot
import ai.markr.phoneagent.data.AgentSettings
import ai.markr.phoneagent.data.LlmProvider
import ai.markr.phoneagent.data.RunRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentRunnerTest {

    private val configured = AgentSettings(
        provider = LlmProvider.ANTHROPIC,
        apiKey = "k",
        textModel = "m",
        maxSteps = 10,
    )

    private fun gmail() = snapshot(
        "com.google.android.gm",
        node(1, text = "받은편지함"),
        node(2, text = "홍길동 · 회의", clickable = true),
    )

    @Test fun successful_run_reaches_done_and_persists() = runTest {
        val saved = mutableListOf<RunRecord>()
        val controller = FakeDeviceController(listOf(snapshot("launcher"), gmail()))
        val llm = ScriptedLlmClient(
            listOf(
                """{"thought":"열기","action":{"type":"open_app","app":"Gmail"}}""",
                """{"thought":"보고","action":{"type":"done","answer":"새 메일 1개: 홍길동(회의)"}}""",
            ),
        )
        val runner = AgentRunner(
            controller = controller,
            settingsProvider = { configured },
            saveRecord = { saved.add(it) },
            llmFactory = { llm },
            clock = { 123L },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        runner.start("Gmail 새 메일 요약")
        advanceUntilIdle()

        assertEquals(RunStatus.DONE, runner.state.value.status)
        assertTrue(runner.state.value.answer.contains("홍길동"))
        assertEquals(1, saved.size)
        assertEquals("DONE", saved.first().outcome)
    }

    @Test fun blocks_when_not_configured() = runTest {
        val controller = FakeDeviceController(listOf(gmail()))
        val runner = AgentRunner(
            controller = controller,
            settingsProvider = { AgentSettings() }, // no key
            saveRecord = {},
            llmFactory = { ScriptedLlmClient(listOf("{}")) },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        runner.start("작업")
        advanceUntilIdle()
        assertEquals(RunStatus.ERROR, runner.state.value.status)
        assertTrue(runner.state.value.message.contains("설정"))
    }

    @Test fun empty_task_is_rejected() = runTest {
        val runner = AgentRunner(
            controller = FakeDeviceController(listOf(gmail())),
            settingsProvider = { configured },
            saveRecord = {},
            llmFactory = { ScriptedLlmClient(listOf("{}")) },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        runner.start("   ")
        advanceUntilIdle()
        assertEquals(RunStatus.ERROR, runner.state.value.status)
    }

    @Test fun streams_steps_during_run() = runTest {
        val controller = FakeDeviceController(listOf(gmail(), gmail()))
        val llm = ScriptedLlmClient(
            listOf(
                """{"thought":"스크롤","action":{"type":"scroll","direction":"down"}}""",
                """{"thought":"보고","action":{"type":"done","answer":"끝"}}""",
            ),
        )
        val runner = AgentRunner(
            controller = controller,
            settingsProvider = { configured },
            saveRecord = {},
            llmFactory = { llm },
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
        )
        runner.start("작업")
        advanceUntilIdle()
        assertEquals(2, runner.state.value.steps.size)
    }
}
