package com.shuli.reader.core.dictionary.engine

/**
 * 英文词干提取器（简化版）
 *
 * 通过后缀剥离将英文单词还原为词干形式
 * 适用于查词时的模糊匹配
 */
object EnglishStemmer {

    /**
     * 提取词干
     *
     * @param word 英文单词
     * @return 词干形式
     */
    fun stem(word: String): String {
        if (word.length < 3) return word

        val lower = word.lowercase()

        // 规则顺序很重要，优先匹配更具体的规则
        return when {
            // 复数形式
            lower.endsWith("ies") && lower.length > 4 -> lower.dropLast(3) + "y"
            lower.endsWith("ses") || lower.endsWith("xes") ||
                lower.endsWith("zes") || lower.endsWith("ches") ||
                lower.endsWith("shes") -> lower.dropLast(2)
            lower.endsWith("s") && !lower.endsWith("ss") &&
                !lower.endsWith("us") && !lower.endsWith("is") -> lower.dropLast(1)

            // 过去式和进行时
            lower.endsWith("ied") && lower.length > 4 -> lower.dropLast(3) + "y"
            lower.endsWith("ed") && lower.length > 4 -> {
                val base = lower.dropLast(2)
                if (isConsonant(base, base.length - 1)) base else lower.dropLast(1)
            }
            lower.endsWith("ing") && lower.length > 5 -> {
                val base = lower.dropLast(3)
                if (isConsonant(base, base.length - 1)) base else lower.dropLast(1)
            }

            // 形容词比较级/最高级
            lower.endsWith("ier") && lower.length > 4 -> lower.dropLast(3) + "y"
            lower.endsWith("er") && lower.length > 3 -> lower.dropLast(2)
            lower.endsWith("iest") && lower.length > 5 -> lower.dropLast(4) + "y"
            lower.endsWith("est") && lower.length > 4 -> lower.dropLast(3)

            // 副词
            lower.endsWith("ly") && lower.length > 3 -> lower.dropLast(2)

            // 名词化
            lower.endsWith("tion") && lower.length > 5 -> lower.dropLast(4)
            lower.endsWith("sion") && lower.length > 5 -> lower.dropLast(4)
            lower.endsWith("ment") && lower.length > 5 -> lower.dropLast(4)
            lower.endsWith("ness") && lower.length > 5 -> lower.dropLast(4)
            lower.endsWith("ity") && lower.length > 4 -> lower.dropLast(3)

            // 其他后缀
            lower.endsWith("able") && lower.length > 5 -> lower.dropLast(4)
            lower.endsWith("ible") && lower.length > 5 -> lower.dropLast(4)
            lower.endsWith("ful") && lower.length > 4 -> lower.dropLast(3)
            lower.endsWith("less") && lower.length > 5 -> lower.dropLast(4)
            lower.endsWith("ous") && lower.length > 4 -> lower.dropLast(3)
            lower.endsWith("ive") && lower.length > 4 -> lower.dropLast(3)
            lower.endsWith("al") && lower.length > 3 -> lower.dropLast(2)
            lower.endsWith("ial") && lower.length > 4 -> lower.dropLast(3)

            else -> lower
        }
    }

    /**
     * 生成单词的可能变体
     *
     * 用于查词时的模糊匹配
     */
    fun generateVariants(word: String): List<String> {
        val variants = mutableSetOf<String>()
        val lower = word.lowercase()

        variants.add(lower)
        variants.add(stem(lower))

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

        return variants.toList()
    }

    /**
     * 判断指定位置是否为辅音
     */
    private fun isConsonant(word: String, index: Int): Boolean {
        if (index < 0 || index >= word.length) return false
        val ch = word[index]
        return ch !in "aeiou"
    }
}
