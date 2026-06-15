package com.shuli.reader.core.text

/**
 * 内容清理工具类
 *
 * 负责在分页前对章节文本进行预处理：
 * - [removeEmptyLines]: 移除段落间多余的空行
 * - [preserveOriginalIndent]: 保留原文行首缩进
 * - [cleanChapterTitle]: 清理章节标题中的序号和多余空格
 */
object ContentCleaner {

    /** 匹配连续 2 个及以上换行符（含其间空白） */
    private val MULTI_NEWLINE_REGEX = Regex("""\n[ \t　]*\n(?:[ \t　]*\n)*""")

    /**
     * 移除段落间多余的空行，保留单个换行符作为段落分隔。
     *
     * 处理逻辑：
     * 1. 连续多个换行符（含空白行）→ 合并为单个 `\n`
     * 2. 保留段首缩进（全角空格 `　`）
     *
     * 示例：
     * ```
     * "段落1\n\n\n段落2" → "段落1\n段落2"
     * "段落1\n  \n  \n段落2" → "段落1\n段落2"
     * ```
     */
    fun removeEmptyLines(text: String): String {
        if (text.isEmpty()) return text
        return MULTI_NEWLINE_REGEX.replace(text, "\n")
    }

    /**
     * 保留原文行首缩进，将其转换为特殊标记，防止被 Paginator 的 skipLeadingSpaces 跳过。
     *
     * 处理逻辑：
     * 1. 检测段落开头的全角空格 `　` 或半角空格缩进
     * 2. 将其转换为特殊标记 `​`（零宽空格）+ 原始缩进
     * 3. Paginator 会跳过普通空格，但保留零宽空格
     *
     * 注意：此方法应在 removeEmptyLines 之后调用。
     */
    fun preserveOriginalIndent(text: String): String {
        if (text.isEmpty()) return text

        val result = StringBuilder()
        var i = 0
        val len = text.length

        while (i < len) {
            // 段落开头：行首或换行符之后
            if (i == 0 || text[i - 1] == '\n') {
                // 检测并保留缩进
                val indentStart = i
                while (i < len && text[i] in " \t　") i++
                if (i > indentStart) {
                    // 有缩进，添加零宽空格标记 + 原始缩进
                    result.append('​')
                    result.append(text, indentStart, i)
                }
            } else {
                result.append(text[i])
                i++
            }
        }

        return result.toString()
    }

    /**
     * 清理章节标题中的序号和多余空格。
     *
     * 处理逻辑：
     * 1. 移除标题行首的序号（如 "第一章"、"Chapter 1"）
     * 2. 移除标题中的多余空格
     * 3. 保留标题核心内容
     *
     * 注意：此方法仅处理标题字符串，不处理正文。
     */
    fun cleanChapterTitle(title: String): String {
        if (title.isBlank()) return title

        var cleaned = title.trim()

        // 移除常见序号前缀
        cleaned = cleaned.replace(Regex("""^第\s*[\d〇零一二两三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+\s*[章节回卷集部篇]\s*"""), "")
        cleaned = cleaned.replace(Regex("""^Chapter\s*\d+\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""^Ch\.\s*\d+\s*""", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("""^\d{1,5}\s*[、：:.]\s*"""), "")

        // 移除装饰符号
        cleaned = cleaned.replace(Regex("""^[【\[〈「『〖〔☆★✦✧]\s*"""), "")
        cleaned = cleaned.replace(Regex("""[】\]〉」』〗〕☆★✦✧]\s*$"""), "")

        // 合并多余空格
        cleaned = cleaned.replace(Regex("""\s{2,}"""), " ").trim()

        return cleaned.ifBlank { title.trim() }
    }
}
