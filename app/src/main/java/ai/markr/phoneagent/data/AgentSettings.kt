package ai.markr.phoneagent.data

enum class LlmProvider { ANTHROPIC, OPENAI, GEMINI }

/** User-configured model/provider settings. apiKey is stored encrypted at rest. */
data class AgentSettings(
    val provider: LlmProvider = LlmProvider.ANTHROPIC,
    val apiKey: String = "",
    val baseUrl: String = "",
    val textModel: String = "",
    val visionModel: String = "",
    val maxSteps: Int = 20,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank() && textModel.isNotBlank()
    val visionEnabled: Boolean get() = visionModel.isNotBlank()

    companion object {
        fun defaultTextModel(p: LlmProvider) = when (p) {
            LlmProvider.ANTHROPIC -> "claude-sonnet-4-6"
            LlmProvider.OPENAI -> "gpt-4o"
            LlmProvider.GEMINI -> "gemini-2.0-flash"
        }

        fun defaultVisionModel(p: LlmProvider) = when (p) {
            LlmProvider.ANTHROPIC -> "claude-sonnet-4-6"
            LlmProvider.OPENAI -> "gpt-4o"
            LlmProvider.GEMINI -> "gemini-2.0-flash"
        }

        fun defaultBaseUrl(p: LlmProvider) = when (p) {
            LlmProvider.ANTHROPIC -> "https://api.anthropic.com"
            LlmProvider.OPENAI -> "https://api.openai.com"
            LlmProvider.GEMINI -> "https://generativelanguage.googleapis.com"
        }
    }
}
