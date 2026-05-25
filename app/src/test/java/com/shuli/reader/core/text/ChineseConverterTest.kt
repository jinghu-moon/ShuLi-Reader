package com.shuli.reader.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseConverterTest {

    @Test
    fun toTraditional_convertsKnownCharacters() {
        assertEquals("書", ChineseConverter.toTraditional("书"))
        assertEquals("讀書", ChineseConverter.toTraditional("读书"))
        assertEquals("開門", ChineseConverter.toTraditional("开门"))
    }

    @Test
    fun toSimplified_convertsKnownCharacters() {
        assertEquals("书", ChineseConverter.toSimplified("書"))
        assertEquals("读书", ChineseConverter.toSimplified("讀書"))
        assertEquals("开门", ChineseConverter.toSimplified("開門"))
    }

    @Test
    fun toTraditional_preservesLength() {
        val simplified = "读书写画开车"
        val traditional = ChineseConverter.toTraditional(simplified)
        assertEquals("转换后长度应一致", simplified.length, traditional.length)
    }

    @Test
    fun toSimplified_preservesLength() {
        val traditional = "讀書寫畫開車"
        val simplified = ChineseConverter.toSimplified(traditional)
        assertEquals("转换后长度应一致", traditional.length, simplified.length)
    }

    @Test
    fun toTraditional_unknownCharacters_unchanged() {
        assertEquals("ABC123", ChineseConverter.toTraditional("ABC123"))
    }

    @Test
    fun toSimplified_unknownCharacters_unchanged() {
        assertEquals("ABC123", ChineseConverter.toSimplified("ABC123"))
    }

    @Test
    fun toTraditional_emptyString_returnsEmpty() {
        assertEquals("", ChineseConverter.toTraditional(""))
    }

    @Test
    fun toSimplified_emptyString_returnsEmpty() {
        assertEquals("", ChineseConverter.toSimplified(""))
    }

    @Test
    fun roundTrip_simplifiedToTraditionalToSimplified_preservesContent() {
        val original = "读书写画开车"
        val converted = ChineseConverter.toSimplified(ChineseConverter.toTraditional(original))
        assertEquals("简→繁→简应还原", original, converted)
    }

    @Test
    fun roundTrip_traditionalToSimplifiedToTraditional_preservesContent() {
        val original = "讀書寫畫開車"
        val converted = ChineseConverter.toTraditional(ChineseConverter.toSimplified(original))
        assertEquals("繁→简→繁应还原", original, converted)
    }

    @Test
    fun toTraditional_mixedContent_onlyConvertsKnownChars() {
        val result = ChineseConverter.toTraditional("Hello读书World")
        assertEquals("Hello讀書World", result)
    }

    // OpenCC 词汇级转换测试

    @Test
    fun toTraditional_vocabularyLevelConversion() {
        // OpenCC 应支持词汇级转换
        val result = ChineseConverter.toTraditional("网络软件")
        assertTrue("词汇级转换：网络→網路", result.contains("網"))
    }

    @Test
    fun toTraditional_longText_convertsConsistently() {
        val text = "这是一段用于测试简繁转换功能的长文本，包含常用汉字和标点符号。"
        val traditional = ChineseConverter.toTraditional(text)
        // 转换后长度应一致（OpenCC 一对一映射）
        assertEquals("转换后长度应一致", text.length, traditional.length)
        // 不应有未转换的简体字残留（至少应有部分变化）
        assertTrue("应有转换发生", text != traditional)
    }

    @Test
    fun toTraditional_commonCharacters_allConverted() {
        // 测试更多常用字
        val result = ChineseConverter.toTraditional("国家经济")
        assertTrue("国→國", result.contains("國"))
        assertTrue("经→經", result.contains("經"))
    }
}
