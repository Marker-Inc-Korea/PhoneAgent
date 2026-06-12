package ai.markr.phoneagent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Device TextToSpeech (Google TTS etc.). On-device, free, supports Korean and
 * instant [stop] for barge-in. Long answers are split into sentences so they
 * can be interrupted cleanly mid-report.
 */
class AndroidTts(
    context: Context,
    private val locale: Locale = Locale.KOREAN,
    private var rate: Float = 1.0f,
) : SpeechSynthesizer {

    @Volatile private var ready = false
    @Volatile private var languageOk = false
    private val counter = AtomicInteger(0)
    @Volatile private var activeGroup = -1
    @Volatile private var pendingDone: (() -> Unit)? = null
    @Volatile private var remaining = 0

    private val tts: TextToSpeech = TextToSpeech(
        context.applicationContext,
        TextToSpeech.OnInitListener { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val res = tts.setLanguage(locale)
                languageOk = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
                tts.setSpeechRate(rate)
            }
        },
    )

    override val isReady: Boolean get() = ready && languageOk

    override fun setRate(rate: Float) {
        this.rate = rate
        if (ready) tts.setSpeechRate(rate)
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith("$activeGroup:") == true) {
                    if (--remaining <= 0) {
                        pendingDone?.invoke()
                        pendingDone = null
                    }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
            override fun onError(utteranceId: String?, errorCode: Int) {}
        })
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (!isReady) { onDone(); return }
        val sentences = splitSentences(text)
        if (sentences.isEmpty()) { onDone(); return }
        val group = counter.incrementAndGet()
        activeGroup = group
        pendingDone = onDone
        remaining = sentences.size
        sentences.forEachIndexed { i, sentence ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(sentence, mode, null, "$group:$i")
        }
    }

    override fun stop() {
        activeGroup = -1
        pendingDone = null
        remaining = 0
        tts.stop()
    }

    override fun shutdown() {
        runCatching { tts.shutdown() }
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[.!?。…\\n])\\s+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
