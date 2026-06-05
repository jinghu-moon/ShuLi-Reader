package com.shuli.reader.core.i18n

/**
 * 朗读（TTS）设置字符串。
 */
interface TtsStrings {
    val ttsSettings: String
    val ttsSpeed: String
    val ttsPitch: String
    val ttsAutoPage: String
    val ttsHighlightSentence: String
    val ttsSkipTitle: String
    val ttsSleepTimer: String
    val ttsSleepTimerOff: String
    val ttsSleepTimerRemaining: (Int) -> String
    val ttsStart: String
    val ttsPause: String
    val ttsStop: String
}
