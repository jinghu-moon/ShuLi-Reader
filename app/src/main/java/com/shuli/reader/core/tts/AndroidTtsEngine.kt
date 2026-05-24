package com.shuli.reader.core.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class AndroidTtsEngine(
    context: Context,
    locale: Locale = Locale.getDefault(),
) : TtsEngine {
    private var pendingConfig = TtsConfig()
    private var isReady = false
    private var textToSpeech: TextToSpeech? = null
    private var listener: TtsEngine.Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            val engine = textToSpeech ?: return@TextToSpeech
            isReady = status == TextToSpeech.SUCCESS
            if (isReady) {
                engine.language = locale
                installProgressListener(engine)
                applyConfig(pendingConfig)
            }
        }
    }

    override fun configure(config: TtsConfig) {
        pendingConfig = config
        if (isReady) {
            applyConfig(config)
        }
    }

    override fun setListener(listener: TtsEngine.Listener?) {
        this.listener = listener
        textToSpeech?.let(::installProgressListener)
    }

    override fun speak(text: String) {
        if (!isReady) return
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun stop() {
        textToSpeech?.stop()
    }

    override fun shutdown() {
        listener = null
        textToSpeech?.shutdown()
        textToSpeech = null
        isReady = false
    }

    private fun applyConfig(config: TtsConfig) {
        textToSpeech?.setSpeechRate(config.speed.coerceIn(MIN_RATE, MAX_RATE))
        textToSpeech?.setPitch(config.pitch.coerceIn(MIN_RATE, MAX_RATE))
    }

    private fun installProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId != UTTERANCE_ID) return
                    mainHandler.post {
                        listener?.onUtteranceCompleted()
                    }
                }

                @Deprecated("Deprecated by Android framework")
                override fun onError(utteranceId: String?) = Unit
            },
        )
    }

    private companion object {
        private const val UTTERANCE_ID = "shuli-reader-tts"
        private const val MIN_RATE = 0.5f
        private const val MAX_RATE = 2.0f
    }
}
