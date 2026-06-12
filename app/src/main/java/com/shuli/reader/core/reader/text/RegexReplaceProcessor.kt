package com.shuli.reader.core.reader.text

/**
 * 正则替换处理器。
 *
 * 按 [RegexRule] 列表对文本执行正则替换。
 * order 值建议使用 100+ 范围，避免与其它处理器冲突。
 */
class RegexReplaceProcessor(
    override val order: Int = 100,
    rules: List<RegexRule> = emptyList(),
) : TextProcessor {

    private val cache = RegexRuleCache(rules)

    override fun process(text: String, context: ProcessingContext): String {
        if (cache.compiled.isEmpty()) return text
        var result = text
        for ((regex, replacement) in cache.compiled) {
            result = regex.replace(result, replacement)
        }
        return result
    }
}

/**
 * 正则规则缓存，预编译启用的规则。
 *
 * 不可变：规则列表变化时创建新实例。
 */
class RegexRuleCache(rules: List<RegexRule>) {
    data class CompiledRule(val regex: Regex, val replacement: String)

    val compiled: List<CompiledRule> = rules
        .filter { it.enabled }
        .mapNotNull { rule ->
            try {
                CompiledRule(Regex(rule.pattern), rule.replacement)
            } catch (_: IllegalArgumentException) {
                // 无效正则跳过
                null
            }
        }
}
