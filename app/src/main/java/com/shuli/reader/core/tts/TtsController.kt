package com.shuli.reader.core.tts

data class TtsConfig(
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val autoPage: Boolean = false,
    val highlightSentence: Boolean = false,
)

enum class TtsState {
    IDLE,
    READY,
    PLAYING,
    PAUSED,
    STOPPED,
    ERROR,
}

interface TtsEngine {
    fun interface Listener {
        fun onUtteranceCompleted()
    }

    fun configure(config: TtsConfig)
    fun setListener(listener: Listener?)
    fun speak(text: String)
    fun stop()
    fun shutdown()
}

class TtsController(
    private val engine: TtsEngine,
    private val onPageEnd: () -> Unit = {},
    private val onUtteranceCompleted: () -> Unit = {},
) {
    var state: TtsState = TtsState.IDLE
        private set

    var config: TtsConfig = TtsConfig()
        private set

    var activeText: String = ""
        private set

    init {
        engine.setListener(TtsEngine.Listener { handleUtteranceCompleted() })
    }

    fun initialize(config: TtsConfig = TtsConfig()) {
        this.config = config
        engine.configure(config)
        state = TtsState.READY
    }

    fun play(text: String) {
        if (text.isBlank()) {
            state = TtsState.ERROR
            return
        }
        activeText = text
        engine.configure(config)
        engine.speak(text)
        state = TtsState.PLAYING
    }

    fun pause() {
        if (state != TtsState.PLAYING) return
        engine.stop()
        state = TtsState.PAUSED
    }

    fun resume() {
        if (state != TtsState.PAUSED || activeText.isBlank()) return
        engine.speak(activeText)
        state = TtsState.PLAYING
    }

    fun stop() {
        engine.stop()
        activeText = ""
        state = TtsState.STOPPED
    }

    fun release() {
        engine.setListener(null)
        engine.shutdown()
        activeText = ""
        state = TtsState.IDLE
    }

    private fun handleUtteranceCompleted() {
        if (state != TtsState.PLAYING) return

        activeText = ""
        state = TtsState.READY
        onUtteranceCompleted()
        if (config.autoPage) {
            onPageEnd()
        }
    }
}
