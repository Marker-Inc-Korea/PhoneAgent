package ai.markr.phoneagent.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.markr.phoneagent.data.SettingsRepository
import ai.markr.phoneagent.platform.Permissions
import ai.markr.phoneagent.runtime.AgentRunState
import ai.markr.phoneagent.runtime.AgentRunner
import ai.markr.phoneagent.runtime.RunStatus
import ai.markr.phoneagent.voice.SpeechSynthesizer
import ai.markr.phoneagent.voice.SpeechTranscriber
import ai.markr.phoneagent.voice.TranscriptListener
import ai.markr.phoneagent.voice.VoiceAction
import ai.markr.phoneagent.voice.VoiceCoordinator
import ai.markr.phoneagent.voice.VoiceEvent
import ai.markr.phoneagent.voice.VoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class HomeReadiness(
    val accessibilityEnabled: Boolean = false,
    val configured: Boolean = false,
) {
    val ready: Boolean get() = accessibilityEnabled && configured
}

data class VoiceUiState(
    val enabled: Boolean = true,
    val state: VoiceState = VoiceState.IDLE,
    val partial: String = "",
    val ttsReady: Boolean = false,
    val sttAvailable: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runner: AgentRunner,
    private val settingsRepository: SettingsRepository,
    private val tts: SpeechSynthesizer,
    private val stt: SpeechTranscriber,
) : ViewModel() {

    val runState: StateFlow<AgentRunState> = runner.state

    private val _readiness = MutableStateFlow(HomeReadiness())
    val readiness: StateFlow<HomeReadiness> = _readiness.asStateFlow()

    private val _voice = MutableStateFlow(VoiceUiState(sttAvailable = stt.isAvailable))
    val voice: StateFlow<VoiceUiState> = _voice.asStateFlow()

    private val coordinator = VoiceCoordinator()
    private var lastSpokenStatus: RunStatus? = null

    init {
        settingsRepository.settings
            .onEach { s ->
                _readiness.value = _readiness.value.copy(configured = s.isConfigured)
                _voice.value = _voice.value.copy(enabled = s.voiceEnabled)
                tts.setRate(s.speechRate)
            }
            .launchIn(viewModelScope)

        // Speak terminal results once, when voice is on.
        runState
            .onEach { st -> maybeSpeakResult(st) }
            .launchIn(viewModelScope)

        refreshPermissions()
        _voice.value = _voice.value.copy(ttsReady = tts.isReady)
    }

    fun refreshPermissions() {
        _readiness.value = _readiness.value.copy(
            accessibilityEnabled = Permissions.isAccessibilityEnabled(context),
        )
        _voice.value = _voice.value.copy(
            ttsReady = tts.isReady,
            sttAvailable = stt.isAvailable,
        )
    }

    fun run(task: String) = runner.start(task)
    fun stop() = runner.stop()
    fun reset() = runner.reset()

    /** Mic FAB: toggles listening and cuts off any ongoing speech (barge-in). */
    fun onMicTap() = apply(coordinator.onEvent(VoiceEvent.MicTapped))

    private fun maybeSpeakResult(st: AgentRunState) {
        if (!_voice.value.enabled) return
        val terminal = st.status in TERMINAL
        if (!terminal) { lastSpokenStatus = null; return }
        if (lastSpokenStatus == st.status) return
        lastSpokenStatus = st.status
        val toSay = when (st.status) {
            RunStatus.DONE -> st.answer
            else -> st.message
        }
        if (toSay.isNotBlank()) apply(coordinator.onEvent(VoiceEvent.SpeakRequested(toSay)))
    }

    private fun apply(actions: List<VoiceAction>) {
        actions.forEach { action ->
            when (action) {
                is VoiceAction.StartSpeaking -> {
                    _voice.value = _voice.value.copy(state = coordinator.state, partial = "")
                    tts.speak(action.text) {
                        // back to coordinator on completion
                        apply(coordinator.onEvent(VoiceEvent.SpeakFinished))
                        _voice.value = _voice.value.copy(state = coordinator.state)
                    }
                }
                is VoiceAction.StopSpeaking -> tts.stop()
                is VoiceAction.StartListening -> {
                    _voice.value = _voice.value.copy(state = coordinator.state, partial = "")
                    stt.start(transcriptListener)
                }
                is VoiceAction.StopListening -> {
                    stt.stop()
                    _voice.value = _voice.value.copy(state = coordinator.state)
                }
                is VoiceAction.RunTask -> {
                    _voice.value = _voice.value.copy(state = coordinator.state, partial = "")
                    runner.start(action.text)
                }
            }
        }
        _voice.value = _voice.value.copy(state = coordinator.state)
    }

    private val transcriptListener = object : TranscriptListener {
        override fun onSpeechStart() {
            apply(coordinator.onEvent(VoiceEvent.SpeechStarted))
        }
        override fun onPartial(text: String) {
            _voice.value = _voice.value.copy(partial = text)
        }
        override fun onFinal(text: String) {
            apply(coordinator.onEvent(VoiceEvent.TranscriptFinal(text)))
        }
        override fun onError(message: String) {
            apply(coordinator.onEvent(VoiceEvent.TranscriptError(message)))
            _voice.value = _voice.value.copy(partial = "")
        }
    }

    override fun onCleared() {
        stt.shutdown()
        tts.shutdown()
    }

    private companion object {
        val TERMINAL = setOf(
            RunStatus.DONE, RunStatus.ABORTED, RunStatus.ERROR, RunStatus.CANCELLED,
        )
    }
}
