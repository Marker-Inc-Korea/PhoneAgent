package ai.markr.phoneagent.llm

import ai.markr.phoneagent.agent.LlmClient
import ai.markr.phoneagent.agent.LlmMessage
import java.util.Base64

/** OpenAI Chat Completions, also compatible with OpenAI-style endpoints. */
class OpenAiClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://api.openai.com",
    private val visionModel: String? = null,
) : LlmClient {

    override val supportsVision: Boolean get() = visionModel != null

    override suspend fun complete(system: String, messages: List<LlmMessage>, imageJpeg: ByteArray?): String {
        val activeModel = if (imageJpeg != null && visionModel != null) visionModel else model
        val chat = mutableListOf<Map<String, Any?>>(mapOf("role" to "system", "content" to system))
        val lastUser = messages.indexOfLast { it.role == LlmMessage.Role.USER }
        messages.forEachIndexed { i, m ->
            val role = if (m.role == LlmMessage.Role.USER) "user" else "assistant"
            if (imageJpeg != null && i == lastUser) {
                val dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageJpeg)
                chat.add(
                    mapOf(
                        "role" to role,
                        "content" to listOf(
                            mapOf("type" to "text", "text" to m.content),
                            mapOf("type" to "image_url", "image_url" to mapOf("url" to dataUrl)),
                        ),
                    ),
                )
            } else {
                chat.add(mapOf("role" to role, "content" to m.content))
            }
        }
        val body = LlmHttp.toJson(mapOf("model" to activeModel, "messages" to chat))
        val resp = LlmHttp.post(
            url = "${baseUrl.trimEnd('/')}/v1/chat/completions",
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "content-type" to "application/json",
            ),
            jsonBody = body,
        )
        return extractText(resp)
    }

    private fun extractText(json: String): String {
        val map = LlmHttp.parse(json)
        val choices = map["choices"] as? List<*> ?: return ""
        val message = (choices.firstOrNull() as? Map<*, *>)?.get("message") as? Map<*, *> ?: return ""
        return message["content"] as? String ?: ""
    }
}
