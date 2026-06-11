package com.shuli.reader.feature.reader.settings

/**
 * 当前阅读会话临时状态。
 *
 * 用于表达"会话级覆盖"：例如用户在当前会话临时调整的亮度、
 * 当前打开的设置 Tab、TTS 播放状态等。非 null 字段优先于全局默认值。
 *
 * 生命周期：仅存在于 ViewModel 存活期间，不持久化。
 */
data class ReaderSessionState(
    val brightness: Float? = null,
    val keepScreenOn: Boolean? = null,
)
