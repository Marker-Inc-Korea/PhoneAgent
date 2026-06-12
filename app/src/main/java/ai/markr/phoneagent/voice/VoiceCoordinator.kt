package ai.markr.phoneagent.voice

/** What the voice layer is currently doing (the agent "thinking" is tracked separately). */
enum class VoiceState { IDLE, LISTENING, SPEAKING }

/** Events fed into the coordinator from UI and the speech engines. */
sealed interface VoiceEvent {
    data object MicTapped : VoiceEvent
    /** The user began speaking (partial/VAD) — primary barge-in trigger. */
    data object SpeechStarted : VoiceEvent
    data class TranscriptFinal(val text: String) : VoiceEvent
    data class TranscriptError(val message: String) : VoiceEvent
    /** The agent produced a final answer/report to read out. */
    data class SpeakRequested(val text: String) : VoiceEvent
    data object SpeakFinished : VoiceEvent
}

/** Side effects the host (ViewModel) must perform. */
sealed interface VoiceAction {
    data object StartListening : VoiceAction
    data object StopListening : VoiceAction
    data object StopSpeaking : VoiceAction
    data class StartSpeaking(val text: String) : VoiceAction
    data class RunTask(val text: String) : VoiceAction
}

/**
 * Pure barge-in state machine. Given the current state and an event it returns
 * the next state plus the actions to perform — no Android dependency, fully
 * unit-testable. The key rule: any user speech immediately cuts off the
 * assistant's TTS.
 */
class VoiceCoordinator(initial: VoiceState = VoiceState.IDLE) {
    var state: VoiceState = initial
        private set

    fun onEvent(event: VoiceEvent): List<VoiceAction> {
        val (next, actions) = reduce(state, event)
        state = next
        return actions
    }

    private fun reduce(state: VoiceState, event: VoiceEvent): Pair<VoiceState, List<VoiceAction>> =
        when (event) {
            is VoiceEvent.MicTapped -> when (state) {
                VoiceState.SPEAKING -> VoiceState.LISTENING to listOf(
                    VoiceAction.StopSpeaking, VoiceAction.StartListening,
                )
                VoiceState.LISTENING -> VoiceState.IDLE to listOf(VoiceAction.StopListening)
                VoiceState.IDLE -> VoiceState.LISTENING to listOf(VoiceAction.StartListening)
            }

            is VoiceEvent.SpeechStarted ->
                // User started talking: always kill any ongoing TTS, ensure we're listening.
                VoiceState.LISTENING to if (state == VoiceState.SPEAKING) {
                    listOf(VoiceAction.StopSpeaking)
                } else {
                    emptyList()
                }

            is VoiceEvent.TranscriptFinal ->
                if (event.text.isBlank()) {
                    VoiceState.IDLE to listOf(VoiceAction.StopListening)
                } else {
                    VoiceState.IDLE to listOf(VoiceAction.StopListening, VoiceAction.RunTask(event.text))
                }

            is VoiceEvent.TranscriptError ->
                VoiceState.IDLE to listOf(VoiceAction.StopListening)

            is VoiceEvent.SpeakRequested ->
                // Don't talk over the user if they're mid-utterance.
                if (state == VoiceState.LISTENING) {
                    state to emptyList()
                } else {
                    VoiceState.SPEAKING to listOf(VoiceAction.StartSpeaking(event.text))
                }

            is VoiceEvent.SpeakFinished ->
                if (state == VoiceState.SPEAKING) VoiceState.IDLE to emptyList() else state to emptyList()
        }
}
