package ai.markr.phoneagent.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import java.util.Locale

/**
 * Device SpeechRecognizer (on-device when available). Emits partial results so
 * the host can cut off TTS the instant the user starts speaking. Must be created
 * and driven on the main thread.
 */
class AndroidStt(
    private val context: Context,
    private val locale: Locale = Locale.KOREAN,
) : SpeechTranscriber {

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listener: TranscriptListener? = null
    private var sawSpeech = false

    override val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start(listener: TranscriptListener) {
        this.listener = listener
        main.post {
            if (!isAvailable) { listener.onError("음성 인식을 사용할 수 없습니다."); return@post }
            sawSpeech = false
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(context)
            r.setRecognitionListener(recognitionListener)
            recognizer = r
            r.startListening(buildIntent())
        }
    }

    override fun stop() {
        main.post {
            recognizer?.stopListening()
        }
    }

    override fun shutdown() {
        main.post {
            recognizer?.destroy()
            recognizer = null
        }
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {
            sawSpeech = true
            listener?.onSpeechStart()
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { if (it.isNotBlank()) listener?.onPartial(it) }
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results).orEmpty()
            if (text.isBlank()) listener?.onError("들리지 않았어요.") else listener?.onFinal(text)
        }

        override fun onError(error: Int) {
            listener?.onError(errorText(error))
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH -> "들리지 않았어요."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "말이 없어 종료했어요."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한이 필요합니다."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 오류로 인식 실패."
        else -> "음성 인식 오류($code)."
    }
}
