package ai.markr.phoneagent.agent

import ai.markr.phoneagent.agent.model.AgentOutcome
import ai.markr.phoneagent.agent.model.AgentStep
import ai.markr.phoneagent.agent.model.Snapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentLoopTest {

    private val gmailInbox = snapshot(
        "com.google.android.gm",
        node(1, text = "받은편지함"),
        node(2, text = "홍길동 · 회의 일정 안내 · 내일 오후 3시", clickable = true),
        node(3, text = "쿠팡 · 주문하신 상품이 배송됐어요", clickable = true),
        activity = "ConversationListActivity",
    )

    @Test fun completes_gmail_scenario() = runTest { // AC-1
        val controller = FakeDeviceController(listOf(snapshot("com.android.launcher"), gmailInbox, gmailInbox))
        val llm = ScriptedLlmClient(
            listOf(
                """{"thought":"Gmail 실행","action":{"type":"open_app","app":"Gmail"}}""",
                """{"thought":"메일 목록 확인","action":{"type":"scroll","direction":"down"}}""",
                """{"thought":"요약 보고","action":{"type":"done","answer":"새 메일 2개: 홍길동(회의 일정), 쿠팡(배송 알림)."}}""",
            ),
        )
        val steps = mutableListOf<AgentStep>()
        val result = AgentLoop(controller, llm).run("Gmail 새 메일 요약") { steps.add(it) }

        assertEquals(AgentOutcome.DONE, result.outcome)
        assertTrue(result.answer.contains("홍길동"))
        assertTrue(controller.calls.contains("openApp:Gmail"))
        assertTrue(controller.calls.contains("scroll:DOWN"))
        assertEquals(3, steps.size)
    }

    @Test fun retries_once_on_unparseable_then_succeeds() {
        // first response is junk, parser retries and second is valid
        runTest {
            val controller = FakeDeviceController(listOf(gmailInbox))
            val llm = ScriptedLlmClient(
                listOf(
                    "죄송합니다, JSON이 아닙니다",
                    """{"thought":"보고","action":{"type":"done","answer":"완료"}}""",
                ),
            )
            val result = AgentLoop(controller, llm).run("작업")
            assertEquals(AgentOutcome.DONE, result.outcome)
        }
    }

    @Test fun aborts_when_llm_says_abort() {
        runTest {
            val controller = FakeDeviceController(listOf(snapshot("com.android.launcher")))
            val llm = ScriptedLlmClient(
                listOf("""{"thought":"불가","action":{"type":"abort","reason":"Gmail 미설치"}}"""),
            )
            val result = AgentLoop(controller, llm).run("작업")
            assertEquals(AgentOutcome.ABORTED, result.outcome)
            assertEquals("Gmail 미설치", result.answer)
        }
    }

    @Test fun stops_at_max_steps() {
        runTest {
            val controller = FakeDeviceController(listOf(gmailInbox))
            val llm = ScriptedLlmClient(
                listOf("""{"thought":"계속","action":{"type":"scroll","direction":"down"}}"""),
            )
            val result = AgentLoop(controller, llm, config = AgentConfig(maxSteps = 4)).run("작업")
            assertEquals(AgentOutcome.MAX_STEPS, result.outcome)
            assertEquals(4, result.steps.size)
        }
    }

    @Test fun errors_when_service_disconnected() {
        runTest {
            val controller = FakeDeviceController(listOf(gmailInbox), isConnected = false)
            val llm = ScriptedLlmClient(listOf("{}"))
            val result = AgentLoop(controller, llm).run("작업")
            assertEquals(AgentOutcome.ERROR, result.outcome)
        }
    }

    @Test fun uses_vision_when_screenshot_requested() { // AC-6 path via explicit request
        runTest {
            val controller = FakeDeviceController(listOf(gmailInbox, gmailInbox))
            val llm = ScriptedLlmClient(
                listOf(
                    """{"thought":"이미지 확인","action":{"type":"screenshot"}}""",
                    """{"thought":"보고","action":{"type":"done","answer":"끝"}}""",
                ),
                supportsVision = true,
            )
            AgentLoop(controller, llm).run("작업")
            // second LLM call should have received an image
            assertTrue(controller.calls.contains("screenshot"))
            assertTrue(llm.imageSeen.any { it })
        }
    }

    @Test fun screenshot_without_vision_reports_unavailable() {
        runTest {
            val controller = FakeDeviceController(listOf(gmailInbox, gmailInbox))
            val llm = ScriptedLlmClient(
                listOf(
                    """{"thought":"이미지","action":{"type":"screenshot"}}""",
                    """{"thought":"보고","action":{"type":"done","answer":"끝"}}""",
                ),
                supportsVision = false,
            )
            val result = AgentLoop(controller, llm).run("작업")
            assertFalse(llm.imageSeen.any { it })
            assertTrue(result.steps.first().actionResult.contains("비전 모델"))
        }
    }

    @Test fun injects_loop_warning_after_three_repeats() { // AC-8
        runTest {
            val seenWarning = CompletableDeferred<Boolean>()
            val controller = FakeDeviceController(listOf(gmailInbox))
            val llm = object : LlmClient {
                override val supportsVision = false
                var n = 0
                override suspend fun complete(system: String, messages: List<LlmMessage>, imageJpeg: ByteArray?): String {
                    val lastUser = messages.lastOrNull { it.role == LlmMessage.Role.USER }?.content.orEmpty()
                    if (lastUser.contains("반복") && !seenWarning.isCompleted) seenWarning.complete(true)
                    n++
                    return if (n >= 6) """{"thought":"끝","action":{"type":"done","answer":"x"}}"""
                    else """{"thought":"같은 동작","action":{"type":"scroll","direction":"down"}}"""
                }
            }
            AgentLoop(controller, llm).run("작업")
            assertTrue(seenWarning.isCompleted && seenWarning.getCompleted())
        }
    }

    @Test fun history_stays_strictly_alternating_and_starts_with_user() {
        runTest {
            val controller = FakeDeviceController(listOf(gmailInbox, gmailInbox, gmailInbox, gmailInbox))
            val captured = mutableListOf<List<LlmMessage>>()
            val llm = object : LlmClient {
                override val supportsVision = false
                var n = 0
                override suspend fun complete(system: String, messages: List<LlmMessage>, imageJpeg: ByteArray?): String {
                    captured.add(messages.toList())
                    n++
                    return if (n >= 4) """{"thought":"끝","action":{"type":"done","answer":"x"}}"""
                    else """{"thought":"스크롤","action":{"type":"scroll","direction":"down"}}"""
                }
            }
            AgentLoop(controller, llm).run("작업")
            val last = captured.last()
            // first message must be USER, and roles must strictly alternate
            assertEquals(LlmMessage.Role.USER, last.first().role)
            last.zipWithNext().forEach { (a, b) -> assertTrue(a.role != b.role) }
            // the prior action result is folded into the next user observation
            assertTrue(last.any { it.role == LlmMessage.Role.USER && it.content.contains("이전 행동 결과") })
        }
    }

    @Test fun cancellation_propagates() {
        runTest {
            val controller = object : DeviceController by FakeDeviceController(listOf(gmailInbox)) {
                override suspend fun snapshot(): Snapshot {
                    kotlinx.coroutines.delay(10_000); return gmailInbox
                }
            }
            val llm = ScriptedLlmClient(listOf("""{"action":{"type":"done","answer":"x"}}"""))
            val job = launch {
                try {
                    AgentLoop(controller, llm).run("작업")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                }
            }
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
        }
    }
}
