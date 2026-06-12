package com.shuli.reader.core.tts

/**
 * TTS 高亮控制器，管理朗读高亮范围。
 *
 * 纯逻辑组件，不依赖 Android 框架。
 * 当朗读进度变化时更新高亮范围，渲染层根据范围绘制高亮背景。
 */
class TtsHighlightController {
    data class HighlightRange(val start: Int, val end: Int)

    var currentRange: HighlightRange? = null
        private set

    /**
     * 更新高亮范围。
     *
     * @param start 起始字符偏移
     * @param end 结束字符偏移
     */
    fun updateHighlight(start: Int, end: Int) {
        currentRange = HighlightRange(start, end)
    }

    /**
     * 清除高亮。
     */
    fun clearHighlight() {
        currentRange = null
    }

    /**
     * 检查给定行范围是否与当前高亮重叠。
     *
     * @param lineStart 行起始字符偏移
     * @param lineEnd 行结束字符偏移
     * @return true 表示重叠
     */
    fun intersectsLine(lineStart: Int, lineEnd: Int): Boolean {
        val range = currentRange ?: return false
        return range.start < lineEnd && range.end > lineStart
    }
}
