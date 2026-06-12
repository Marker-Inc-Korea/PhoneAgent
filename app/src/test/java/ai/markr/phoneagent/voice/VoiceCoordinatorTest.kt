package ai.markr.phoneagent.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCoordinatorTest {

    @Test fun mic_tap_from_idle_starts_listening() {
        val c = VoiceCoordinator()
        val actions = c.onEvent(VoiceEvent.MicTapped)
        assertEquals(VoiceState.LISTENING, c.state)
        assertTrue(actions.contains(VoiceAction.StartListening))
    }

    @Test fun mic_tap_while_speaking_barges_in() { // 말하는 중 끼어들기
        val c = VoiceCoordinator(VoiceState.SPEAKING)
        val actions = c.onEvent(VoiceEvent.MicTapped)
        assertEquals(VoiceState.LISTENING, c.state)
        assertEquals(listOf(VoiceAction.StopSpeaking, VoiceAction.StartListening), actions)
    }

    @Test fun speech_start_cuts_off_tts() { // 사용자가 말 시작하면 TTS 즉시 중단
        val c = VoiceCoordinator(VoiceState.SPEAKING)
        val actions = c.onEvent(VoiceEvent.SpeechStarted)
        assertEquals(VoiceState.LISTENING, c.state)
        assertTrue(actions.contains(VoiceAction.StopSpeaking))
    }

    @Test fun final_transcript_runs_task() {
        val c = VoiceCoordinator(VoiceState.LISTENING)
        val actions = c.onEvent(VoiceEvent.TranscriptFinal("Gmail 새 메일 요약"))
        assertEquals(VoiceState.IDLE, c.state)
        assertTrue(actions.contains(VoiceAction.StopListening))
        assertTrue(actions.contains(VoiceAction.RunTask("Gmail 새 메일 요약")))
    }

    @Test fun blank_transcript_does_not_run() {
        val c = VoiceCoordinator(VoiceState.LISTENING)
        val actions = c.onEvent(VoiceEvent.TranscriptFinal("   "))
        assertEquals(VoiceState.IDLE, c.state)
        assertTrue(actions.none { it is VoiceAction.RunTask })
    }

    @Test fun speak_requested_starts_speaking() {
        val c = VoiceCoordinator(VoiceState.IDLE)
        val actions = c.onEvent(VoiceEvent.SpeakRequested("끝났어요"))
        assertEquals(VoiceState.SPEAKING, c.state)
        assertEquals(listOf(VoiceAction.StartSpeaking("끝났어요")), actions)
    }

    @Test fun speak_request_suppressed_while_listening() { // 사용자 말하는 중엔 답변 음성 보류
        val c = VoiceCoordinator(VoiceState.LISTENING)
        val actions = c.onEvent(VoiceEvent.SpeakRequested("답변"))
        assertEquals(VoiceState.LISTENING, c.state)
        assertTrue(actions.isEmpty())
    }

    @Test fun mic_tap_while_listening_stops() {
        val c = VoiceCoordinator(VoiceState.LISTENING)
        val actions = c.onEvent(VoiceEvent.MicTapped)
        assertEquals(VoiceState.IDLE, c.state)
        assertEquals(listOf(VoiceAction.StopListening), actions)
    }

    @Test fun speak_finished_returns_to_idle() {
        val c = VoiceCoordinator(VoiceState.SPEAKING)
        c.onEvent(VoiceEvent.SpeakFinished)
        assertEquals(VoiceState.IDLE, c.state)
    }
}
