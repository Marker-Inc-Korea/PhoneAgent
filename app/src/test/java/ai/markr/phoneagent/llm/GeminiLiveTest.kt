package ai.markr.phoneagent.llm

import ai.markr.phoneagent.agent.ActionParser
import ai.markr.phoneagent.agent.LlmMessage
import ai.markr.phoneagent.agent.PromptBuilder
import ai.markr.phoneagent.agent.model.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Real Gemini call, end-to-end through the agent's prompt + parser.
 * Skipped unless a key is supplied so CI/offline runs stay green:
 *
 *   GEMINI_API_KEY=AIza... ./gradlew :app:testDebugUnitTest --tests '*GeminiLiveTest'
 *
 * Optional: GEMINI_MODEL (default gemini-2.0-flash).
 */
class GeminiLiveTest {

    private val apiKey: String? =
        System.getenv("GEMINI_API_KEY") ?: System.getProperty("gemini.api.key")
    private val model: String =
        System.getenv("GEMINI_MODEL") ?: System.getProperty("gemini.model") ?: "gemini-2.0-flash"

    @Test fun gemini_returns_parseable_agent_action() = runBlocking {
        assumeTrue("GEMINI_API_KEY 미설정 — 라이브 테스트 건너뜀", !apiKey.isNullOrBlank())

        val client = GeminiClient(apiKey!!, model)
        val system = PromptBuilder.systemPrompt(
            task = "Gmail에서 새 메일을 확인하고 요약하라.",
            visionAvailable = false,
            voiceConcise = true,
        )
        val observation = """
            SCREEN package=com.android.launcher activity=Home
            [0] "Gmail" (clickable)
            [1] "Chrome" (clickable)
        """.trimIndent()

        val raw = withContext(Dispatchers.IO) {
            client.complete(system, listOf(LlmMessage(LlmMessage.Role.USER, observation)))
        }
        println("Gemini raw response: $raw")

        assertTrue("응답이 비어있음", raw.isNotBlank())
        val parsed = ActionParser().parse(raw)
        assertTrue("Gemini 응답을 액션으로 파싱하지 못함: $raw", parsed.isSuccess)
        // First sensible move on the launcher is to open Gmail.
        val action = parsed.getOrThrow().action
        assertTrue(
            "예상과 다른 첫 액션: $action",
            action is Action.OpenApp || action is Action.Tap || action is Action.TapXy,
        )
    }
}
