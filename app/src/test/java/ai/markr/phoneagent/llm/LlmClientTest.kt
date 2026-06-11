package ai.markr.phoneagent.llm

import ai.markr.phoneagent.agent.LlmMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LlmClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    private fun base() = server.url("/").toString().trimEnd('/')

    private val messages = listOf(LlmMessage(LlmMessage.Role.USER, "SCREEN ..."))

    @Test fun anthropic_sends_model_and_parses_text() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"안녕"}]}"""))
        val client = AnthropicClient("k", "claude-x", base())
        val out = withContext(Dispatchers.IO) { client.complete("sys", messages) }
        assertEquals("안녕", out)
        val req = server.takeRequest()
        assertTrue(req.path!!.endsWith("/v1/messages"))
        assertEquals("k", req.getHeader("x-api-key"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"model\":\"claude-x\""))
        assertTrue(body.contains("\"system\":\"sys\""))
    }

    @Test fun openai_parses_choices() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"결과"}}]}"""))
        val client = OpenAiClient("k", "gpt-x", base())
        val out = withContext(Dispatchers.IO) { client.complete("sys", messages) }
        assertEquals("결과", out)
        val req = server.takeRequest()
        assertTrue(req.path!!.endsWith("/v1/chat/completions"))
        assertEquals("Bearer k", req.getHeader("Authorization"))
    }

    @Test fun gemini_parses_candidates_and_uses_key_in_url() = runTest {
        server.enqueue(MockResponse().setBody("""{"candidates":[{"content":{"parts":[{"text":"답"}]}}]}"""))
        val client = GeminiClient("mykey", "gemini-x", base())
        val out = withContext(Dispatchers.IO) { client.complete("sys", messages) }
        assertEquals("답", out)
        val req = server.takeRequest()
        assertTrue(req.path!!.contains("gemini-x:generateContent"))
        assertTrue(req.path!!.contains("key=mykey"))
    }

    @Test fun non_2xx_throws_llm_exception() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad key"}"""))
        val client = OpenAiClient("k", "gpt-x", base())
        var thrown = false
        try {
            withContext(Dispatchers.IO) { client.complete("sys", messages) }
        } catch (e: LlmException) {
            thrown = true
            assertTrue(e.message!!.contains("401"))
        }
        assertTrue(thrown)
    }

    @Test fun vision_model_used_when_image_present() = runTest {
        server.enqueue(MockResponse().setBody("""{"content":[{"type":"text","text":"ok"}]}"""))
        val client = AnthropicClient("k", "text-model", base(), visionModel = "vision-model")
        assertTrue(client.supportsVision)
        withContext(Dispatchers.IO) { client.complete("sys", messages, imageJpeg = byteArrayOf(1, 2, 3)) }
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"model\":\"vision-model\""))
        assertTrue(body.contains("\"type\":\"image\""))
    }
}
