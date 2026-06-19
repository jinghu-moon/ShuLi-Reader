package com.shuli.reader.core.dictionary.engine

/**
 * 单词归一化器
 *
 * 处理单词的标准化形式，提高查词命中率
 */
object WordNormalizer {

    /**
     * 归一化单词
     *
     * - 去除首尾空格
     * - 转小写（英文）
     * - 去除标点
     */
    fun normalize(word: String): String {
        return word.trim()
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]"), "")
    }

    /**
     * 中文前向最大匹配
     *
     * 从位置 start 开始，尝试匹配最长的中文词（最多 maxLen 个字符）
     *
     * @param text 原文
     * @param start 起始位置
     * @param maxLen 最大词长（默认 8）
     * @return 匹配的词，未匹配返回单个字符
     */
    fun forwardMaxMatch(text: String, start: Int, maxLen: Int = 8): String {
        if (start >= text.length) return ""

        val end = minOf(start + maxLen, text.length)

        // 从最长开始尝试
        for (len in end - start downTo 2) {
            val candidate = text.substring(start, start + len)
            if (isAllChinese(candidate)) {
                return candidate
            }
        }

        // 未匹配到词，返回单个字符
        return text[start].toString()
    }

    /**
     * 判断字符串是否全部为中文字符
     */
    fun isAllChinese(text: String): Boolean {
        return text.all { ch ->
            val code = ch.code
            code in 0x4E00..0x9FFF ||   // CJK Unified Ideographs
                code in 0x3400..0x4DBF ||   // CJK Extension A
                code in 0x20000..0x2A6DF || // CJK Extension B
                code in 0xF900..0xFAFF ||   // CJK Compatibility
                code in 0x2F800..0x2FA1F    // CJK Compatibility Supplement
        }
    }

    /**
     * 判断字符是否为中文
     */
    fun isChinese(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0x20000..0x2A6DF ||
            code in 0xF900..0xFAFF ||
            code in 0x2F800..0x2FA1F
    }

    /**
     * 判断是否包含中文
     */
    fun containsChinese(text: String): Boolean {
        return text.any { isChinese(it) }
    }

    /**
     * 提取连续中文词
     *
     * 从文本中提取连续的中文字符序列
     */
    fun extractChineseWords(text: String): List<String> {
        val words = mutableListOf<String>()
        val current = StringBuilder()

        for (ch in text) {
            if (isChinese(ch)) {
                current.append(ch)
            } else {
                if (current.isNotEmpty()) {
                    words.add(current.toString())
                    current.clear()
                }
            }
        }

        if (current.isNotEmpty()) {
            words.add(current.toString())
        }

        return words
    }
}
