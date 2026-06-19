package com.shuli.reader.core.dictionary.engine

/**
 * 英文词干提取器（简化版 Porter Stemmer）
 *
 * 通过后缀剥离将英文单词还原为词干形式
 * 适用于查词时的模糊匹配
 */
object EnglishStemmer {

    /** 不规则动词映射 */
    private val IRREGULAR_VERBS = mapOf(
        "ran" to "run",
        "sang" to "sing",
        "sung" to "sing",
        "sank" to "sink",
        "sunk" to "sink",
        "drank" to "drink",
        "drunk" to "drink",
        "began" to "begin",
        "begun" to "begin",
        "spoke" to "speak",
        "spoken" to "speak",
        "broke" to "break",
        "broken" to "break",
        "chose" to "choose",
        "chosen" to "choose",
        "froze" to "freeze",
        "frozen" to "freeze",
        "woke" to "wake",
        "woken" to "wake",
        "wrote" to "write",
        "written" to "write",
        "rode" to "ride",
        "ridden" to "ride",
        "drove" to "drive",
        "driven" to "drive",
        "gave" to "give",
        "given" to "give",
        "took" to "take",
        "taken" to "take",
        "fell" to "fall",
        "fallen" to "fall",
        "went" to "go",
        "gone" to "go",
        "ate" to "eat",
        "eaten" to "eat",
        "did" to "do",
        "done" to "do",
        "saw" to "see",
        "seen" to "see",
        "got" to "get",
        "gotten" to "get",
        "made" to "make",
        "came" to "come",
        "became" to "become",
        "left" to "leave",
        "felt" to "feel",
        "found" to "find",
        "held" to "hold",
        "kept" to "keep",
        "led" to "lead",
        "lost" to "lose",
        "meant" to "mean",
        "met" to "meet",
        "paid" to "pay",
        "said" to "say",
        "sent" to "send",
        "sat" to "sit",
        "stood" to "stand",
        "taught" to "teach",
        "told" to "tell",
        "thought" to "think",
        "understood" to "understand",
        "won" to "win",
    )

    /**
     * 生成词干候选列表
     *
     * 按优先级返回可能的词干形式，依次尝试每个候选
     *
     * @param word 英文单词
     * @return 候选词干列表（第一个为最可能的词干）
     */
    fun stemCandidates(word: String): List<String> {
        if (word.length < 3) return listOf(word)

        val lower = word.lowercase()
        val candidates = mutableListOf<String>()

        // 原词
        candidates.add(lower)

        // 不规则动词
        IRREGULAR_VERBS[lower]?.let { candidates.add(it) }

        // 词干提取
        val stemmed = stem(lower)
        if (stemmed != lower && stemmed.length >= 2) {
            candidates.add(stemmed)
        }

        // 添加常见变体（用于反向匹配）
        candidates.addAll(generateVariants(lower))

        return candidates.distinct().filter { it.length >= 2 }
    }

    /**
     * 提取词干（单一结果）
     *
     * @param word 英文单词
     * @return 词干形式
     */
    fun stem(word: String): String {
        if (word.length < 3) return word

        val lower = word.lowercase()

        // 不规则动词直接查表
        IRREGULAR_VERBS[lower]?.let { return it }

        // 特殊后缀处理
        when {
            // -ness 名词后缀
            lower.endsWith("ness") && lower.length > 5 -> {
                val base = lower.dropLast(4)
                if (base.length >= 2) return base
            }

            // -ment 名词后缀
            lower.endsWith("ment") && lower.length > 5 -> {
                val base = lower.dropLast(4)
                if (base.length >= 2) return base
            }

            // -tion/-sion 名词后缀
            lower.endsWith("tion") && lower.length > 5 -> {
                val base = lower.dropLast(4)
                if (base.length >= 2) return base
            }
            lower.endsWith("sion") && lower.length > 5 -> {
                val base = lower.dropLast(4)
                if (base.length >= 2) return base
            }

            // -able/-ible 形容词后缀
            lower.endsWith("able") && lower.length > 5 -> {
                val base = lower.dropLast(4)
                if (base.length >= 2) return base
            }
            lower.endsWith("ible") && lower.length > 5 -> {
                val base = lower.dropLast(4)
                if (base.length >= 2) return base
            }

            // -ful 形容词后缀
            lower.endsWith("ful") && lower.length > 4 -> {
                val base = lower.dropLast(3)
                if (base.length >= 2) return base
            }

            // -less 形容词后缀
            lower.endsWith("less") && lower.length > 5 -> {
                val base = lower.dropLast(4)
                if (base.length >= 2) return base
            }

            // -ous 形容词后缀
            lower.endsWith("ous") && lower.length > 4 -> {
                val base = lower.dropLast(3)
                if (base.length >= 2) return base
            }

            // -ive 形容词后缀
            lower.endsWith("ive") && lower.length > 4 -> {
                val base = lower.dropLast(3)
                if (base.length >= 2) return base
            }

            // -ly 副词后缀
            lower.endsWith("ly") && lower.length > 3 -> {
                val base = lower.dropLast(2)
                if (base.length >= 2) return base
            }
        }

        // 复数形式
        if (lower.endsWith("ies") && lower.length > 4) {
            return lower.dropLast(3) + "y"
        }
        if (lower.endsWith("ses") || lower.endsWith("xes") ||
            lower.endsWith("zes") || lower.endsWith("ches") ||
            lower.endsWith("shes")) {
            return lower.dropLast(2)
        }
        if (lower.endsWith("s") && !lower.endsWith("ss") &&
            !lower.endsWith("us") && !lower.endsWith("is") &&
            !lower.endsWith("os") && lower.length > 3) {
            return lower.dropLast(1)
        }

        // 过去式和过去分词
        if (lower.endsWith("ied") && lower.length > 4) {
            return lower.dropLast(3) + "y"
        }
        if (lower.endsWith("ed") && lower.length > 4) {
            val base = lower.dropLast(2)
            // 处理双写辅音：stopped -> stop, running -> run
            if (base.length >= 3 && isConsonant(base, base.length - 1) &&
                base[base.length - 1] == base[base.length - 2]) {
                return base.dropLast(1)
            }
            // 处理 silent e: hoped -> hope
            if (base.length >= 2 && isConsonant(base, base.length - 1) &&
                !isConsonant(base, base.length - 2)) {
                return base + "e"
            }
            if (base.length >= 3) return base
        }

        // 现在分词
        if (lower.endsWith("ing") && lower.length > 5) {
            val base = lower.dropLast(3)
            // 处理双写辅音：running -> run
            if (base.length >= 3 && isConsonant(base, base.length - 1) &&
                base[base.length - 1] == base[base.length - 2]) {
                return base.dropLast(1)
            }
            // 处理 silent e: hoping -> hope
            if (base.length >= 2 && isConsonant(base, base.length - 1) &&
                !isConsonant(base, base.length - 2)) {
                return base + "e"
            }
            if (base.length >= 3) return base
        }

        // 比较级/最高级
        if (lower.endsWith("ier") && lower.length > 4) {
            return lower.dropLast(3) + "y"
        }
        if (lower.endsWith("er") && lower.length > 3) {
            val base = lower.dropLast(2)
            if (base.length >= 3) return base
        }
        if (lower.endsWith("iest") && lower.length > 5) {
            return lower.dropLast(4) + "y"
        }
        if (lower.endsWith("est") && lower.length > 4) {
            val base = lower.dropLast(3)
            if (base.length >= 3) return base
        }

        return lower
    }

    /**
     * 生成单词的可能变体
     *
     * 用于查词时的模糊匹配
     */
    fun generateVariants(word: String): List<String> {
        val variants = mutableListOf<String>()
        val lower = word.lowercase()

        // 添加常见复数/时态变体
        if (!lower.endsWith("s")) {
            variants.add(lower + "s")
        }
        if (!lower.endsWith("ed")) {
            variants.add(lower + "ed")
        }
        if (!lower.endsWith("ing")) {
            variants.add(lower + "ing")
        }
        if (lower.endsWith("y")) {
            variants.add(lower.dropLast(1) + "ies")
        }
        if (lower.endsWith("e")) {
            variants.add(lower.dropLast(1) + "ing")
            variants.add(lower + "d")
        }

        return variants
    }

    /**
     * 判断指定位置是否为辅音
     *
     * 'y' 在元音后面时视为辅音，否则视为元音
     */
    private fun isConsonant(word: String, index: Int): Boolean {
        if (index < 0 || index >= word.length) return false
        val ch = word[index]
        if (ch in "aeiou") return false
        // 'y' 在元音后面时是辅音
        if (ch == 'y') {
            if (index == 0) return false
            return !isVowel(word, index - 1)
        }
        return true
    }

    /**
     * 判断指定位置是否为元音
     */
    private fun isVowel(word: String, index: Int): Boolean {
        if (index < 0 || index >= word.length) return false
        return word[index] in "aeiou"
    }
}
