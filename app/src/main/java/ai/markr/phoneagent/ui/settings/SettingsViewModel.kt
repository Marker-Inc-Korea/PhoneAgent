package ai.markr.phoneagent.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.markr.phoneagent.agent.LlmMessage
import ai.markr.phoneagent.data.AgentSettings
import ai.markr.phoneagent.data.LlmProvider
import ai.markr.phoneagent.data.SettingsRepository
import ai.markr.phoneagent.llm.LlmClientFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class TestStatus { IDLE, TESTING, SUCCESS, FAILED }

data class SettingsUiState(
    val settings: AgentSettings = AgentSettings(),
    val loaded: Boolean = false,
    val testStatus: TestStatus = TestStatus.IDLE,
    val testMessage: String = "",
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val s = repository.current()
            _ui.value = _ui.value.copy(settings = s, loaded = true)
        }
    }

    fun onProviderChange(provider: LlmProvider) {
        val cur = _ui.value.settings
        _ui.value = _ui.value.copy(
            settings = cur.copy(
                provider = provider,
                baseUrl = AgentSettings.defaultBaseUrl(provider),
            ),
            testStatus = TestStatus.IDLE,
        )
    }

    fun onApiKeyChange(v: String) = update { it.copy(apiKey = v) }
    fun onBaseUrlChange(v: String) = update { it.copy(baseUrl = v) }
    fun onTextModelChange(v: String) = update { it.copy(textModel = v) }
    fun onVisionModelChange(v: String) = update { it.copy(visionModel = v) }
    fun onMaxStepsChange(v: Int) = update { it.copy(maxSteps = v) }
    fun onVoiceEnabledChange(v: Boolean) = update { it.copy(voiceEnabled = v) }
    fun onSpeechRateChange(v: Float) = update { it.copy(speechRate = v) }

    private fun update(transform: (AgentSettings) -> AgentSettings) {
        _ui.value = _ui.value.copy(
            settings = transform(_ui.value.settings),
            testStatus = TestStatus.IDLE,
            saved = false,
        )
    }

    fun save() {
        viewModelScope.launch {
            repository.save(_ui.value.settings)
            _ui.value = _ui.value.copy(saved = true)
        }
    }

    fun testConnection() {
        val settings = _ui.value.settings
        if (!settings.isConfigured) {
            _ui.value = _ui.value.copy(
                testStatus = TestStatus.FAILED,
                testMessage = "API 키와 텍스트 모델을 먼저 입력하세요.",
            )
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(testStatus = TestStatus.TESTING, testMessage = "")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val client = LlmClientFactory.create(settings)
                    client.complete(
                        system = "응답으로 정확히 OK 만 출력하세요.",
                        messages = listOf(LlmMessage(LlmMessage.Role.USER, "ping")),
                    )
                }
            }
            _ui.value = result.fold(
                onSuccess = {
                    _ui.value.copy(testStatus = TestStatus.SUCCESS, testMessage = "연결 성공")
                },
                onFailure = {
                    _ui.value.copy(testStatus = TestStatus.FAILED, testMessage = "실패: ${it.message}")
                },
            )
        }
    }
}
