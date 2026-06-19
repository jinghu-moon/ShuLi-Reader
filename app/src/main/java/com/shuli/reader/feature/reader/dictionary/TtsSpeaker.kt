package com.shuli.reader.feature.reader.dictionary

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * TTS 语音朗读器
 *
 * 封装 Android TextToSpeech，用于单词发音
 */
class TtsSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isReady = true
        }
    }

    /**
     * 朗读文本
     *
     * @param text 要朗读的文本
     * @param locale 语言区域（默认英语）
     */
    fun speak(text: String, locale: Locale = Locale.US) {
        if (!isReady) return
        tts?.language = locale
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dict_tts")
    }

    /**
     * 停止朗读
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * 释放资源
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
