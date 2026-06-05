package com.shuli.reader.core.i18n

/** 简体中文 — TTS 字符串 */
internal data object ZhHansTts : TtsStrings {
    override val ttsSettings = "朗读设置 (TTS)"
    override val ttsSpeed = "语速调节"
    override val ttsPitch = "音调调节"
    override val ttsAutoPage = "自动翻页"
    override val ttsHighlightSentence = "高亮当前句子"
    override val ttsSkipTitle = "跳过章节标题"
    override val ttsSleepTimer = "定时停止"
    override val ttsSleepTimerOff = "关闭"
    override val ttsSleepTimerRemaining = { seconds: Int -> "${seconds / 60}分${seconds % 60}秒后停止" }
    override val ttsStart = "开始朗读"
    override val ttsPause = "暂停朗读"
    override val ttsStop = "停止朗读"
}

/** 繁体中文 — TTS 字符串 */
internal data object ZhHantTts : TtsStrings {
    override val ttsSettings = "朗讀設定 (TTS)"
    override val ttsSpeed = "語速調節"
    override val ttsPitch = "音調調節"
    override val ttsAutoPage = "自動翻頁"
    override val ttsHighlightSentence = "高亮當前句子"
    override val ttsSkipTitle = "跳過章節標題"
    override val ttsSleepTimer = "定時停止"
    override val ttsSleepTimerOff = "關閉"
    override val ttsSleepTimerRemaining = { seconds: Int -> "${seconds / 60}分${seconds % 60}秒後停止" }
    override val ttsStart = "開始朗讀"
    override val ttsPause = "暫停朗讀"
    override val ttsStop = "停止朗讀"
}

/** English — TTS strings */
internal data object EnTts : TtsStrings {
    override val ttsSettings = "TTS Settings"
    override val ttsSpeed = "Speech Speed"
    override val ttsPitch = "Speech Pitch"
    override val ttsAutoPage = "Auto Page Turn"
    override val ttsHighlightSentence = "Highlight Active Sentence"
    override val ttsSkipTitle = "Skip Chapter Title"
    override val ttsSleepTimer = "Sleep Timer"
    override val ttsSleepTimerOff = "Off"
    override val ttsSleepTimerRemaining = { seconds: Int -> "Stop in ${seconds / 60}m${seconds % 60}s" }
    override val ttsStart = "Start Reading"
    override val ttsPause = "Pause Reading"
    override val ttsStop = "Stop Reading"
}
