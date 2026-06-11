package com.shuli.reader.core.reader.text

interface TextProcessor {
    val order: Int
    fun process(text: String, context: ProcessingContext): String
}

data class ProcessingContext(
    val adFiltering: Boolean = false,
    val regexRules: List<RegexRule> = emptyList(),
    val locale: String = "",
    val chineseConvert: com.shuli.reader.core.data.ChineseConvert = com.shuli.reader.core.data.ChineseConvert.NONE,
    val usePanguSpacing: Boolean = false,
)

data class RegexRule(
    val pattern: String,
    val replacement: String,
    val enabled: Boolean = true,
)

class TextProcessingPipeline(processors: List<TextProcessor>) {
    private val sorted: List<TextProcessor>

    init {
        val duplicates = processors.groupBy { it.order }.filter { it.value.size > 1 }
        require(duplicates.isEmpty()) {
            "Duplicate processor orders: ${duplicates.keys}. Each processor must have a unique order."
        }
        sorted = processors.sortedBy { it.order }
    }

    fun process(text: String, context: ProcessingContext): String =
        sorted.fold(text) { acc, proc -> proc.process(acc, context) }
}
