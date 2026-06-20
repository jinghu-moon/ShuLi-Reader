package com.shuli.reader.feature.reader.editor

/**
 * 一条编辑补丁。
 *
 * 定位方式：chapterIndex + 章节内字符偏移（非字节偏移）。
 * 优势：
 * - 与文件编码无关（UTF-8/GBK 均适用）
 * - 不需要 byte↔char 转换
 * - 同一章节内多个 Delta 的偏移互不影响（应用时按降序处理）
 */
data class EditDelta(
    val chapterIndex: Int,
    val charStart: Int,           // 章节内字符起始
    val charEnd: Int,             // 章节内字符结束（exclusive）
    val newText: String,          // 替换后的文本
    val originalText: String = "", // 原始文本（用于显示和验证）
    val timestamp: Long = System.currentTimeMillis(),
) {
    /** 字符长度变化量（用于保存时计算字节偏移增量） */
    fun charLengthDiff(): Int = newText.length - (charEnd - charStart)
}

/**
 * 批量编辑补丁：一次"全部替换"操作的所有匹配。
 *
 * 只记录一条 {findText, replaceText, List<IntRange>}，
 * 而非 N 个独立的 EditDelta。撤销时一次性撤销整批。
 */
data class BatchEditDelta(
    val chapterIndex: Int,
    val findText: String,
    val replaceText: String,
    val ranges: List<IntRange>,   // 每个匹配的 charStart..charEnd
    val isRegex: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
) {
    /** 展开为单个 EditDelta 列表（按降序排列，供应用时使用） */
    fun expand(): List<EditDelta> = ranges.sortedByDescending { it.first }.map { range ->
        EditDelta(
            chapterIndex = chapterIndex,
            charStart = range.first,
            charEnd = range.last + 1,
            newText = replaceText,
            originalText = findText,
            timestamp = timestamp,
        )
    }

    /** 总字符长度变化量 */
    fun totalCharLengthDiff(): Int {
        val originalLen = ranges.sumOf { it.last - it.first + 1 }
        val newLen = replaceText.length * ranges.size
        return newLen - originalLen
    }
}
