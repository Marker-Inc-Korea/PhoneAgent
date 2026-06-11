package ai.markr.phoneagent.llm

import ai.markr.phoneagent.agent.LlmClient
import ai.markr.phoneagent.agent.LlmMessage
import java.util.Base64

class AnthropicClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://api.anthropic.com",
    private val visionModel: String? = null,
) : LlmClient {

    override val supportsVision: Boolean get() = visionModel != null

    override suspend fun complete(system: String, messages: List<LlmMessage>, imageJpeg: ByteArray?): String {
        val activeModel = if (imageJpeg != null && visionModel != null) visionModel else model
        val msgs = messages.mapIndexed { i, m ->
            val isLastUser = i == messages.indexOfLast { it.role == LlmMessage.Role.USER }
            val content = mutableListOf<Map<String, Any?>>()
            if (imageJpeg != null && isLastUser && m.role == LlmMessage.Role.USER) {
                content.add(
                    mapOf(
                        "type" to "image",
                        "source" to mapOf(
                            "type" to "base64",
                            "media_type" to "image/jpeg",
                            "data" to Base64.getEncoder().encodeToString(imageJpeg),
                        ),
                    ),
                )
            }
            content.add(mapOf("type" to "text", "text" to m.content))
            mapOf("role" to if (m.role == LlmMessage.Role.USER) "user" else "assistant", "content" to content)
        }
        val body = LlmHttp.toJson(
            mapOf(
                "model" to activeModel,
                "max_tokens" to 1024,
                "system" to system,
                "messages" to msgs,
            ),
        )
        val resp = LlmHttp.post(
            url = "${baseUrl.trimEnd('/')}/v1/messages",
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
                "content-type" to "application/json",
            ),
            jsonBody = body,
        )
        return extractText(resp)
    }

    private fun extractText(json: String): String {
        val map = LlmHttp.parse(json)
        val content = map["content"] as? List<*> ?: return ""
        return content.filterIsInstance<Map<*, *>>()
            .firstOrNull { it["type"] == "text" }
            ?.get("text") as? String ?: ""
    }
}
