package ai.markr.phoneagent.voice

/** Callbacks for a single listening session. */
interface TranscriptListener {
    /** Interim hypothesis while the user is still speaking (drives barge-in). */
    fun onPartial(text: String) {}

    /** Final recognized utterance. */
    fun onFinal(text: String)

    /** Listening ended without a usable result. */
    fun onError(message: String)

    /** The user actually started speaking — used to cut off any ongoing TTS. */
    fun onSpeechStart() {}
}

/**
 * Speech-to-text abstraction. Implemented on-device by Android's SpeechRecognizer
 * (primary) or an open-source engine such as sherpa-onnx Whisper/Zipformer
 * (fallback).
 */
interface SpeechTranscriber {
    val isAvailable: Boolean
    fun start(listener: TranscriptListener)
    fun stop()
    fun shutdown()
}
