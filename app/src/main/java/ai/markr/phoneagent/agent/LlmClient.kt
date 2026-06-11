package ai.markr.phoneagent.agent

/** A single chat message in the agent conversation. */
data class LlmMessage(val role: Role, val content: String) {
    enum class Role { USER, ASSISTANT }
}

/**
 * Provider-neutral LLM interface. The loop sends a system prompt plus the
 * running conversation, optionally attaching one JPEG image to the latest
 * user turn for vision models.
 */
interface LlmClient {
    val supportsVision: Boolean

    suspend fun complete(
        system: String,
        messages: List<LlmMessage>,
        imageJpeg: ByteArray? = null,
    ): String
}
