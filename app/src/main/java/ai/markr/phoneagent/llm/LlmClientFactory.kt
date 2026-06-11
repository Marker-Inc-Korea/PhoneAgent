package ai.markr.phoneagent.llm

import ai.markr.phoneagent.agent.LlmClient
import ai.markr.phoneagent.data.AgentSettings
import ai.markr.phoneagent.data.LlmProvider

/** Builds the text and (optional) vision client for the active settings. */
object LlmClientFactory {

    fun create(settings: AgentSettings): LlmClient {
        val model = settings.textModel.ifBlank { AgentSettings.defaultTextModel(settings.provider) }
        val visionModel = if (settings.visionEnabled) settings.visionModel else null
        val base = settings.baseUrl.ifBlank { AgentSettings.defaultBaseUrl(settings.provider) }
        return when (settings.provider) {
            LlmProvider.ANTHROPIC -> AnthropicClient(settings.apiKey, model, base, visionModel)
            LlmProvider.OPENAI -> OpenAiClient(settings.apiKey, model, base, visionModel)
            LlmProvider.GEMINI -> GeminiClient(settings.apiKey, model, base, visionModel)
        }
    }
}
