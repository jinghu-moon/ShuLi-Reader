package com.shuli.reader.core.dictionary.engine

/**
 * CC-CEDICT 条目解析器
 *
 * 解析 CC-CEDICT 格式的中文词条：
 * - 繁简分离
 * - 拼音声调
 * - 义项分割
 *
 * 格式示例：
 * 中國 中国 [Zhong1 guo2] /China/
 * 学習 学习 [xue2 xi2] /to learn/to study/
 */
object CedictEntryParser {

    /**
     * 解析 CEDICT 条目
     *
     * @param line 原始条目行
     * @return 解析结果，解析失败返回 null
     */
    fun parse(line: String): CedictEntry? {
        // 跳过注释和空行
        if (line.isBlank() || line.startsWith("#")) return null

        // 格式：繁體 简体 [拼音] /释义1/释义2/...
        val regex = Regex("""^(\S+)\s+(\S+)\s+\[([^\]]+)\]\s+/(.+)/$""")
        val match = regex.matchEntire(line.trim()) ?: return null

        val traditional = match.groupValues[1]
        val simplified = match.groupValues[2]
        val pinyinRaw = match.groupValues[3]
        val definitionsRaw = match.groupValues[4]

        // 解析拼音（带声调数字）
        val pinyin = parsePinyin(pinyinRaw)

        // 分割义项
        val definitions = definitionsRaw.split("/")
            .filter { it.isNotBlank() }
            .map { it.trim() }

        // 解析义项，提取词性
        val entries = definitions.map { def ->
            // 检测词性标签（如 "n.", "v.", "adj." 等）
            val posPattern = Regex("""^([a-z]+\.)\s*(.+)$""")
            val posMatch = posPattern.matchEntire(def)
            if (posMatch != null) {
                DefinitionEntry(text = posMatch.groupValues[2], partOfSpeech = posMatch.groupValues[1])
            } else {
                DefinitionEntry(text = def, partOfSpeech = null)
            }
        }

        return CedictEntry(
            traditional = traditional,
            simplified = simplified,
            pinyin = pinyin,
            pinyinRaw = pinyinRaw,
            definitions = entries,
        )
    }

    /**
     * 解析拼音字符串
     *
     * 将 "Zhong1 guo2" 转换为带声调标记的拼音
     */
    private fun parsePinyin(raw: String): String {
        return raw.split(" ").joinToString(" ") { syllable ->
            convertToneNumber(syllable)
        }
    }

    /**
     * 将数字声调转换为声调符号
     *
     * "Zhong1" → "Zhōng"
     * "guo2" → "guó"
     * "liu2" → "liú"
     * "gui4" → "guì"
     *
     * 声调优先级规则：
     * 1. a, e, o 优先标调
     * 2. ou 组合标在 o 上
     * 3. 其他情况标在最后一个元音上
     */
    private fun convertToneNumber(syllable: String): String {
        val toneMatch = Regex("""(\d)$""").find(syllable)
        if (toneMatch == null) return syllable

        val tone = toneMatch.groupValues[1].toIntOrNull() ?: return syllable
        val base = syllable.removeSuffix(toneMatch.groupValues[1]).lowercase()

        // 声调符号映射
        val toneMap = mapOf(
            'a' to "āáǎàa",
            'e' to "ēéěèe",
            'i' to "īíǐìi",
            'o' to "ōóǒòo",
            'u' to "ūúǔùu",
            'ü' to "ǖǘǚǜü",
        )

        // 找到要加声调的元音索引
        val targetIndex = findToneTarget(base)

        if (targetIndex < 0) return syllable

        val targetChar = base[targetIndex]
        val toneChars = toneMap[targetChar]
        if (toneChars == null || tone < 1 || tone > 5) return syllable

        val tonedChar = toneChars[tone - 1]
        val result = StringBuilder(base)
        result[targetIndex] = tonedChar

        return result.toString()
    }

    /**
     * 找到声调目标位置
     *
     * 规则：
     * 1. 有 a/e/o → 标在 a/e/o 上
     * 2. 有 ou → 标在 o 上
     * 3. 其他 → 标在最后一个元音上
     */
    private fun findToneTarget(syllable: String): Int {
        val vowels = "aeiouü"

        // 规则 1: 有 a/e/o
        for (v in "aeo") {
            val idx = syllable.indexOf(v)
            if (idx >= 0) return idx
        }

        // 规则 2: 有 ou
        val ouIndex = syllable.indexOf("ou")
        if (ouIndex >= 0) return ouIndex

        // 规则 3: 最后一个元音
        for (i in syllable.length - 1 downTo 0) {
            if (syllable[i] in vowels) return i
        }

        return -1
    }

    /**
     * 从 CEDICT 条目生成 HTML 释义
     */
    fun toHtml(entry: CedictEntry): String {
        val sb = StringBuilder()
        sb.append("<div class=\"cedict-entry\">")

        // 繁简 + 拼音
        sb.append("<div class=\"cedict-head\">")
        sb.append("<span class=\"cedict-simplified\">${entry.simplified}</span>")
        if (entry.traditional != entry.simplified) {
            sb.append(" <span class=\"cedict-traditional\">(${entry.traditional})</span>")
        }
        sb.append(" <span class=\"cedict-pinyin\">[${entry.pinyin}]</span>")
        sb.append("</div>")

        // 义项
        sb.append("<ol class=\"cedict-definitions\">")
        entry.definitions.forEach { def ->
            sb.append("<li>${def.text}</li>")
        }
        sb.append("</ol>")

        sb.append("</div>")
        return sb.toString()
    }

    /**
     * CEDICT 条目
     */
    data class CedictEntry(
        val traditional: String,
        val simplified: String,
        val pinyin: String,
        val pinyinRaw: String,
        val definitions: List<DefinitionEntry>,
    )

    /**
     * 义项条目
     */
    data class DefinitionEntry(
        val text: String,
        val partOfSpeech: String?,
    )
}
