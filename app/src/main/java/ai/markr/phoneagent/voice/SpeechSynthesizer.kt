package ai.markr.phoneagent.voice

/**
 * Text-to-speech abstraction. Implemented on-device by Android's TextToSpeech
 * (primary) or an open-source engine such as sherpa-onnx (fallback). Kept as an
 * interface so the orchestration layer and tests don't depend on Android.
 */
interface SpeechSynthesizer {
    /** True once the engine is initialized and a usable voice/locale is available. */
    val isReady: Boolean

    /** Speak [text], flushing anything currently queued (barge-in friendly). */
    fun speak(text: String, onDone: () -> Unit = {})

    /** Immediately stop talking — used when the user barges in. */
    fun stop()

    /** Speaking speed multiplier (1.0 = normal). No-op for engines that ignore it. */
    fun setRate(rate: Float) {}

    fun shutdown()
}
