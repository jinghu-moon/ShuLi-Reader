package com.shuli.reader.core.tts

/**
 * TTS 引擎管理器接口。
 *
 * 将 Android TextToSpeech 依赖抽象为接口，便于单元测试。
 * 实现类负责实际的 TTS 引擎初始化和语音合成。
 */
interface TtsManager {
    /** TTS 是否已初始化成功 */
    val isInitialized: Boolean

    /** 初始化错误信息，null 表示无错误 */
    val initError: String?

    /**
     * 初始化 TTS 引擎。
     *
     * @return Result.success 表示成功，Result.failure 包含错误信息
     */
    suspend fun initialize(): Result<Unit>

    /**
     * 朗读文本。
     *
     * @param text 要朗读的文本
     * @param utteranceId 话语 ID，用于回调关联
     * @return true 表示成功开始朗读，false 表示未初始化或失败
     */
    fun speak(text: String, utteranceId: String): Boolean

    /**
     * 停止当前朗读。
     */
    fun stop()

    /**
     * 关闭 TTS 引擎，释放资源。
     */
    fun shutdown()

    /**
     * 设置朗读回调。
     */
    fun setListener(listener: TtsListener?)

    /**
     * 获取可用语音列表。
     */
    fun getAvailableVoices(): List<String>
}

/**
 * TTS 事件回调接口。
 */
interface TtsListener {
    /** 朗读开始 */
    fun onStart(utteranceId: String) {}

    /** 朗读完成 */
    fun onDone(utteranceId: String) {}

    /** 朗读错误 */
    fun onError(utteranceId: String, errorCode: Int) {}

    /** 朗读进度（字符偏移） */
    fun onRangeStart(utteranceId: String, start: Int, end: Int) {}
}
