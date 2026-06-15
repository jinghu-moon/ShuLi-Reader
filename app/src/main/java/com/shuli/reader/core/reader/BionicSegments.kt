package com.shuli.reader.core.reader

/**
 * Bionic Reading 文本分段。
 *
 * Bionic Reading 通过加粗每个单词的前半部分来引导眼睛快速扫读。
 * CJK 文本不应用此效果。
 */

/**
 * 文本分段，标识是否加粗。
 */
data class BionicSegment(
    val text: String,
    val bold: Boolean,
)

/**
 * 计算文本的 Bionic Reading 分段。
 *
 * 规则：
 * - 英文单词：前半部分加粗，后半部分正常
 * - CJK 文本：不加粗
 * - 空格和标点：保持原样，不加粗
 *
 * @param text 待分段的文本
 * @return 分段列表
 */
fun calculateBionicSegments(text: String): List<BionicSegment> {
    if (text.isEmpty()) return emptyList()

    // 纯 CJK 文本不应用 Bionic Reading
    if (isCjkText(text) && !containsLatinWord(text)) {
        return listOf(BionicSegment(text, false))
    }

    val segments = mutableListOf<BionicSegment>()
    val wordRegex = Regex("""[\p{L}\p{N}]+|[^\p{L}\p{N}]+""")

    for (match in wordRegex.findAll(text)) {
        val word = match.value
        if (word.isBlank()) {
            segments.add(BionicSegment(word, false))
            continue
        }
        if (isCjkWord(word)) {
            segments.add(BionicSegment(word, false))
        } else {
            // 英文单词：前半加粗
            val boldLen = calculateBoldLength(word.length)
            if (boldLen > 0) {
                segments.add(BionicSegment(word.substring(0, boldLen), true))
            }
            if (boldLen < word.length) {
                segments.add(BionicSegment(word.substring(boldLen), false))
            }
        }
    }

    return segments
}

/**
 * 判断文本是否为 CJK 文本。
 *
 * 包含任意 CJK 字符即视为 CJK 文本。
 */
fun isCjkText(text: String): Boolean {
    if (text.isEmpty()) return false
    return text.any { isCjkChar(it) }
}

/**
 * 判断文本是否包含拉丁字母单词。
 */
private fun containsLatinWord(text: String): Boolean {
    return text.any { it.isLetter() && !isCjkChar(it) }
}

/**
 * 判断单词是否为 CJK 单词（包含任意 CJK 字符）。
 */
private fun isCjkWord(word: String): Boolean {
    return word.any { isCjkChar(it) }
}

/**
 * 判断字符是否为 CJK 字符。
 */
private fun isCjkChar(ch: Char): Boolean {
    val code = ch.code
    return code in 0x4E00..0x9FFF ||   // CJK Unified Ideographs
        code in 0x3400..0x4DBF ||   // CJK Unified Ideographs Extension A
        code in 0xF900..0xFAFF ||   // CJK Compatibility Ideographs
        code in 0x2E80..0x2EFF ||   // CJK Radicals Supplement
        code in 0x3000..0x303F ||   // CJK Symbols and Punctuation
        code in 0xFF00..0xFFEF ||   // Halfwidth and Fullwidth Forms
        code in 0x3040..0x309F ||   // Hiragana
        code in 0x30A0..0x30FF      // Katakana
}

/**
 * 计算加粗部分长度。
 *
 * 规则：单词长度 / 2（向上取整），最少 1 个字符。
 */
private fun calculateBoldLength(wordLength: Int): Int {
    return when {
        wordLength <= 1 -> wordLength
        else -> (wordLength + 1) / 2
    }
}
