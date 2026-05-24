package com.shuli.reader.core.reader.model

/**
 * 选区范围值对象，书签和笔记共享。
 * 用于定位文本在章节中的位置。
 */
data class SelectionRange(
    val chapterIndex: Int,
    val startPos: Int,
    val endPos: Int,
    val selectedText: String? = null,
) {
    init {
        require(startPos >= 0) { "startPos must be >= 0, got $startPos" }
        require(endPos >= startPos) { "endPos must be >= startPos, got endPos=$endPos startPos=$startPos" }
    }

    /** 选区长度 */
    val length: Int get() = endPos - startPos

    /** 是否为空选区（光标位置） */
    val isEmpty: Boolean get() = startPos == endPos

    companion object {
        /**
         * 创建光标位置（空选区）
         */
        fun cursor(chapterIndex: Int, pos: Int) = SelectionRange(
            chapterIndex = chapterIndex,
            startPos = pos,
            endPos = pos,
        )
    }
}
