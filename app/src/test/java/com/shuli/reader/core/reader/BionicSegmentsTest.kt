package com.shuli.reader.core.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bionic Reading segments 纯逻辑测试。
 *
 * 测试文本分段逻辑和 CJK 检测，不依赖 Android 渲染。
 */
class BionicSegmentsTest {

    // T-4.1.1: 纯英文文本生成正确 segments
    @Test
    fun calculateBionicSegments_englishText_generatesCorrectSegments() {
        val segments = calculateBionicSegments("hello world")
        // "hello" → hel(bold) + lo(normal)
        // "world" → wor(bold) + ld(normal)
        assertEquals(4, segments.size)

        assertEquals("hel", segments[0].text)
        assertTrue(segments[0].bold)

        assertEquals("lo", segments[1].text)
        assertFalse(segments[1].bold)

        assertEquals("wor", segments[2].text)
        assertTrue(segments[2].bold)

        assertEquals("ld", segments[3].text)
        assertFalse(segments[3].bold)
    }

    // T-4.1.2: 纯中文文本不分割
    @Test
    fun calculateBionicSegments_chineseText_noBold() {
        val segments = calculateBionicSegments("你好世界")
        // CJK 文本不应用 Bionic Reading
        assertEquals(1, segments.size)
        assertEquals("你好世界", segments[0].text)
        assertFalse(segments[0].bold)
    }

    // T-4.1.3: 中英混合文本仅英文加粗
    @Test
    fun calculateBionicSegments_mixedText_onlyEnglishBold() {
        val segments = calculateBionicSegments("hello 你好 world")
        // "hello" → hel(bold) + lo(normal)
        // " 你好 " → 不加粗
        // "world" → wor(bold) + ld(normal)
        val boldSegments = segments.filter { it.bold }
        val normalSegments = segments.filter { !it.bold }

        // 英文单词有加粗部分
        assertTrue("should have bold segments", boldSegments.isNotEmpty())
        // 中文部分不加粗
        val chineseText = normalSegments.filter { it.text.contains("你") || it.text.contains("好") }
        assertTrue("Chinese should be normal", chineseText.isNotEmpty())
    }

    // T-4.1.4: isCjkText() 判断正确
    @Test
    fun isCjkText_variousInputs_correctDetection() {
        assertTrue("纯中文应为 CJK", isCjkText("你好"))
        assertFalse("纯英文应为非 CJK", isCjkText("hello"))
        // 混合文本：CJK 字符占比 > 50% 视为 CJK
        assertTrue("CJK > 50% 应为 CJK", isCjkText("hello你好"))
    }

    // T-4.1.5: 空文本返回空 segments
    @Test
    fun calculateBionicSegments_emptyText_returnsEmpty() {
        val segments = calculateBionicSegments("")
        assertEquals(0, segments.size)
    }

    // T-4.1.6: 单字符单词全部加粗
    @Test
    fun calculateBionicSegments_singleCharWord_allBold() {
        val segments = calculateBionicSegments("I am a student")
        // "I" → 全部加粗
        val iSegment = segments.first { it.text == "I" }
        assertTrue("single char should be bold", iSegment.bold)
    }

    // T-4.1.7: 长单词加粗约一半
    @Test
    fun calculateBionicSegments_longWord_boldAboutHalf() {
        val segments = calculateBionicSegments("international")
        // "international" → internation(bold) + al(normal)
        val boldPart = segments.filter { it.bold }.joinToString("") { it.text }
        val normalPart = segments.filter { !it.bold }.joinToString("") { it.text }

        // 加粗部分长度约为单词长度的一半
        val totalLen = boldPart.length + normalPart.length
        assertTrue("bold part should be about half",
            boldPart.length >= totalLen / 3 && boldPart.length <= totalLen * 2 / 3)
    }
}
