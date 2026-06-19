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
     * - 去除首尾空格和标点
     * - 转小写（英文）
     * - 保留内部标点（如 don't, ice-cream）
     */
    fun normalize(word: String): String {
        return word.trim()
            .dropWhile { it.isWhitespace() || isPunctuation(it) }
            .dropLastWhile { it.isWhitespace() || isPunctuation(it) }
            .lowercase()
    }

    /**
     * 判断是否为标点符号
     */
    private fun isPunctuation(ch: Char): Boolean {
        return ch.category in setOf(
            CharCategory.DASH_PUNCTUATION,
            CharCategory.START_PUNCTUATION,
            CharCategory.END_PUNCTUATION,
            CharCategory.OTHER_PUNCTUATION,
            CharCategory.CONNECTOR_PUNCTUATION,
            CharCategory.INITIAL_QUOTE_PUNCTUATION,
            CharCategory.FINAL_QUOTE_PUNCTUATION,
        ) || PUNCTUATION_CHARS.contains(ch)
    }

    /** 常见标点符号集合 */
    private val PUNCTUATION_CHARS = setOf(
        '.', ',', ';', ':', '!', '?', '"', '\'', '(', ')', '[', ']', '{', '}',
        '<', '>', '/', '\\', '@', '#', '$', '%', '^', '&', '*', '+', '-', '=', '~', '|', '`',
        '，', '。', '！', '？', '、', '；', '：', '“', '”', '‘', '’', '（', '）', '【', '】', '《', '》'
    )

    /**
     * 中文前向最大匹配（带词典查询）
     *
     * 从位置 start 开始，尝试匹配最长的中文词（最多 maxLen 个字符），
     * 在词典中查找，逐步截短直到命中。
     *
     * @param text 原文
     * @param start 起始位置
     * @param isInDictionary 词典查询函数，返回 true 表示该词在词典中存在
     * @param maxLen 最大词长（默认 6）
     * @return 匹配的词，未匹配返回单个字符
     */
    fun forwardMaxMatch(
        text: String,
        start: Int,
        isInDictionary: (String) -> Boolean,
        maxLen: Int = 6,
    ): String {
        if (start >= text.length) return ""

        val end = minOf(start + maxLen, text.length)

        // 从最长开始尝试，在词典中查找
        for (len in end - start downTo 2) {
            val candidate = text.substring(start, start + len)
            if (isAllChinese(candidate) && isInDictionary(candidate)) {
                return candidate
            }
        }

        // 未匹配到词，返回单个字符
        return text[start].toString()
    }

    /**
     * 简化版前向最大匹配（无词典查询）
     *
     * 用于无词典场景，使用启发式规则：连续 CJK 字符最多匹配 4 个
     */
    fun forwardMaxMatchSimple(text: String, start: Int, maxLen: Int = 4): String {
        if (start >= text.length) return ""

        val end = minOf(start + maxLen, text.length)

        // 尝试匹配 2-maxLen 个连续中文字符
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
        if (text.isEmpty()) return false
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
     * 从文本中提取连续的中文字符序列，使用 FMM 分词
     */
    fun extractChineseWords(text: String, maxWordLen: Int = 4): List<String> {
        val words = mutableListOf<String>()
        var i = 0

        while (i < text.length) {
            if (isChinese(text[i])) {
                val word = forwardMaxMatchSimple(text, i, maxWordLen)
                words.add(word)
                i += word.length
            } else {
                i++
            }
        }

        return words
    }
}
