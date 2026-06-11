package ai.markr.phoneagent.llm

import ai.markr.phoneagent.agent.LlmClient
import ai.markr.phoneagent.agent.LlmMessage
import java.util.Base64

class GeminiClient(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
    private val visionModel: String? = null,
) : LlmClient {

    override val supportsVision: Boolean get() = visionModel != null

    override suspend fun complete(system: String, messages: List<LlmMessage>, imageJpeg: ByteArray?): String {
        val activeModel = if (imageJpeg != null && visionModel != null) visionModel else model
        val lastUser = messages.indexOfLast { it.role == LlmMessage.Role.USER }
        val contents = messages.mapIndexed { i, m ->
            val parts = mutableListOf<Map<String, Any?>>(mapOf("text" to m.content))
            if (imageJpeg != null && i == lastUser && m.role == LlmMessage.Role.USER) {
                parts.add(
                    mapOf(
                        "inlineData" to mapOf(
                            "mimeType" to "image/jpeg",
                            "data" to Base64.getEncoder().encodeToString(imageJpeg),
                        ),
                    ),
                )
            }
            mapOf("role" to if (m.role == LlmMessage.Role.USER) "user" else "model", "parts" to parts)
        }
        val body = LlmHttp.toJson(
            mapOf(
                "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to system))),
                "contents" to contents,
            ),
        )
        val resp = LlmHttp.post(
            url = "${baseUrl.trimEnd('/')}/v1beta/models/$activeModel:generateContent?key=$apiKey",
            headers = mapOf("content-type" to "application/json"),
            jsonBody = body,
        )
        return extractText(resp)
    }

    private fun extractText(json: String): String {
        val map = LlmHttp.parse(json)
        val candidates = map["candidates"] as? List<*> ?: return ""
        val content = (candidates.firstOrNull() as? Map<*, *>)?.get("content") as? Map<*, *> ?: return ""
        val parts = content["parts"] as? List<*> ?: return ""
        return parts.filterIsInstance<Map<*, *>>()
            .mapNotNull { it["text"] as? String }
            .joinToString("")
    }
}
