package com.shuli.reader.core.reader.text

import com.shuli.reader.core.data.ChineseConvert
import org.junit.Assert.assertEquals
import org.junit.Test

class RegexReplaceProcessorTest {

    private val emptyContext = ProcessingContext(
        adFiltering = false,
        regexRules = emptyList(),
        locale = "zh-CN",
        chineseConvert = ChineseConvert.NONE,
        usePanguSpacing = false,
    )

    // T-3.4.1: 单条规则替换
    @Test
    fun singleRule_replacesCorrectly() {
        val processor = RegexReplaceProcessor(
            rules = listOf(RegexRule("foo", "bar", enabled = true)),
        )
        val result = processor.process("foo baz", emptyContext)
        assertEquals("bar baz", result)
    }

    // T-3.4.2: 多条规则链式替换
    @Test
    fun multipleRules_chainApplied() {
        val processor = RegexReplaceProcessor(
            rules = listOf(
                RegexRule("foo", "bar", enabled = true),
                RegexRule("bar", "baz", enabled = true),
            ),
        )
        val result = processor.process("foo", emptyContext)
        assertEquals("baz", result)
    }

    // T-3.4.3: 禁用规则跳过
    @Test
    fun disabledRule_skipped() {
        val processor = RegexReplaceProcessor(
            rules = listOf(RegexRule("foo", "bar", enabled = false)),
        )
        val result = processor.process("foo baz", emptyContext)
        assertEquals("foo baz", result)
    }

    // T-3.4.4: 无效正则跳过
    @Test
    fun invalidRegex_skipped() {
        val processor = RegexReplaceProcessor(
            rules = listOf(
                RegexRule("[invalid", "bar", enabled = true),
                RegexRule("foo", "replaced", enabled = true),
            ),
        )
        val result = processor.process("foo baz", emptyContext)
        assertEquals("replaced baz", result)
    }

    // 空规则列表透传
    @Test
    fun emptyRules_passthrough() {
        val processor = RegexReplaceProcessor(rules = emptyList())
        val result = processor.process("hello world", emptyContext)
        assertEquals("hello world", result)
    }

    // order 值验证
    @Test
    fun order_defaultIs100() {
        val processor = RegexReplaceProcessor()
        assertEquals(100, processor.order)
    }
}
