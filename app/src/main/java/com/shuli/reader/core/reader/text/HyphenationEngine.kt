package com.shuli.reader.core.reader.text

import android.icu.text.BreakIterator

/**
 * 断字模式枚举。
 */
enum class HyphenationMode {
    NONE,
    AUTO,
    ENGLISH_ONLY,
}

/**
 * 断字引擎。
 *
 * 主路径使用 [BreakIterator.getLineInstance] 查找行断点机会。
 * 对单个拉丁单词，BreakIterator 通常只返回词边界（0 和 length），
 * 因此引擎在 BreakIterator 未找到足够断点时，回退到辅音-元音（C-V）边界扫描
 * 作为音节断点的轻量近似。
 *
 * 设计约束（v5.1 §2.2.4）：
 * - 禁止维护自定义后缀列表或分词规则
 * - 如需更精确的音节断字，Phase 4 可引入标准 Liang 算法模式表（JSON）
 * - CJK 文本不断字
 */
object HyphenationEngine {

    private const val MIN_WORD_LENGTH = 6
    private const val MIN_PREFIX_LENGTH = 2
    private const val MIN_SUFFIX_LENGTH = 1

    private val breakIterator: BreakIterator? by lazy {
        try {
            BreakIterator.getLineInstance()
        } catch (_: Exception) {
            null
        }
    }

    fun findBreakPoints(word: String): List<Int> {
        if (word.length < MIN_WORD_LENGTH) return emptyList()
        if (word.any { isCjkChar(it) }) return emptyList()
        if (!word.any { it.isLetter() }) return emptyList()

        val candidates = mutableSetOf<Int>()

        breakIterator?.let { bi ->
            bi.setText(word)
            var boundary = bi.first()
            while (boundary != BreakIterator.DONE) {
                if (boundary in MIN_PREFIX_LENGTH..(word.length - MIN_SUFFIX_LENGTH - 1)) {
                    candidates.add(boundary)
                }
                boundary = bi.next()
            }
        }

        if (candidates.size < 2) {
            findConsonantVowelBreaks(word.lowercase(), candidates)
        }

        return candidates
            .filter { it >= MIN_PREFIX_LENGTH && it <= word.length - MIN_SUFFIX_LENGTH }
            .sorted()
    }

    private fun findConsonantVowelBreaks(word: String, candidates: MutableSet<Int>) {
        for (i in MIN_PREFIX_LENGTH until word.length - MIN_SUFFIX_LENGTH) {
            if (!isVowel(word[i - 1]) && isVowel(word[i])) {
                candidates.add(i)
            }
        }
    }

    private fun isVowel(ch: Char): Boolean =
        ch.lowercaseChar() in "aeiouy"

    private fun isCjkChar(ch: Char): Boolean {
        val code = ch.code
        return code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0xF900..0xFAFF ||
            code in 0x2E80..0x2EFF ||
            code in 0x3000..0x303F ||
            code in 0xFF00..0xFFEF ||
            code in 0x3040..0x309F ||
            code in 0x30A0..0x30FF
    }
}
