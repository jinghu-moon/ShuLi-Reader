package com.shuli.reader.core.dictionary.engine

/**
 * 模糊匹配器
 *
 * 使用 Levenshtein 编辑距离 ≤ 1 作为最后一层 fallback
 * 仅对词头长度 ≤ 6 时启用（避免长句的模糊匹配噪音）
 */
object FuzzyMatcher {

    /** 最大编辑距离 */
    private const val MAX_DISTANCE = 1

    /** 最大词头长度（超过此长度不启用模糊匹配） */
    private const val MAX_WORD_LENGTH = 6

    /** 最大返回候选数 */
    private const val MAX_CANDIDATES = 3

    /**
     * 在词典索引中查找与输入词编辑距离 ≤ 1 的词条
     *
     * @param word 输入词
     * @param index 词典索引
     * @return 候选词条列表（最多 3 个）
     */
    fun fuzzyMatch(word: String, index: StardictIndex): List<StardictIndex.IndexEntry> {
        if (word.length > MAX_WORD_LENGTH) return emptyList()

        val candidates = mutableListOf<Pair<Int, StardictIndex.IndexEntry>>()
        val target = word.lowercase()

        // 遍历所有词条（利用 idx 的有序性做范围扫描优化）
        // 由于 idx 已排序，我们可以跳过明显不匹配的词
        val wordCount = index.getWordCount()
        for (i in 0 until wordCount) {
            val entry = index.findByIndex(i) ?: continue
            val candidate = entry.word.lowercase()

            // 长度差异 > 1 的直接跳过
            if (kotlin.math.abs(candidate.length - target.length) > MAX_DISTANCE) continue

            // 计算编辑距离
            val distance = levenshteinDistance(target, candidate)
            if (distance <= MAX_DISTANCE) {
                candidates.add(distance to entry)
                if (candidates.size >= MAX_CANDIDATES * 2) break // 收集足够的候选
            }
        }

        // 按编辑距离排序，返回前 N 个
        return candidates
            .sortedBy { it.first }
            .take(MAX_CANDIDATES)
            .map { it.second }
    }

    /**
     * 计算两个字符串的 Levenshtein 编辑距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length

        // 优化：使用单行数组
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)

        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,      // 删除
                    curr[j - 1] + 1,  // 插入
                    prev[j - 1] + cost // 替换
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }

        return prev[n]
    }
}
