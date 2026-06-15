package com.shuli.reader.core.reader.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 断字连字纯逻辑测试。
 *
 * 测试断字引擎和断字点计算，不依赖 Android 渲染。
 */
class HyphenationTest {

    // T-4.2.1: Hyphenation 枚举含三个值
    @Test
    fun hyphenationEnum_hasThreeValues() {
        val values = HyphenationMode.entries
        assertEquals(3, values.size)
        assertTrue(values.contains(HyphenationMode.NONE))
        assertTrue(values.contains(HyphenationMode.AUTO))
        assertTrue(values.contains(HyphenationMode.ENGLISH_ONLY))
    }

    // T-4.2.2: findBreakPoints() 返回断字点
    @Test
    fun findBreakPoints_englishWord_returnsBreakPoints() {
        val breakPoints = HyphenationEngine.findBreakPoints("international")
        // 应该有多个断字点
        assertTrue("should have break points", breakPoints.isNotEmpty())
        // 所有断字点在有效范围内
        for (bp in breakPoints) {
            assertTrue("breakPoint $bp should be >= 2", bp >= 2)
            assertTrue("breakPoint $bp should be < word.length - 1",
                bp < "international".length - 1)
        }
    }

    // T-4.2.3: 断字点至少保留 2 字符在前
    @Test
    fun findBreakPoints_allBreakPointsAtLeast2Chars() {
        val words = listOf("international", "communication", "understanding", "development")
        for (word in words) {
            val breakPoints = HyphenationEngine.findBreakPoints(word)
            for (bp in breakPoints) {
                assertTrue("$word: breakPoint $bp should be >= 2", bp >= 2)
                assertTrue("$word: breakPoint $bp should leave at least 1 char after",
                    bp < word.length - 1)
            }
        }
    }

    // T-4.2.4: 中文不触发断字
    @Test
    fun findBreakPoints_chineseWord_returnsEmpty() {
        val breakPoints = HyphenationEngine.findBreakPoints("国际化的")
        assertEquals("Chinese should have no break points", 0, breakPoints.size)
    }

    // T-4.2.5: 短单词不断字（长度 <= 5）
    @Test
    fun findBreakPoints_shortWord_returnsEmpty() {
        val breakPoints = HyphenationEngine.findBreakPoints("hello")
        // 短单词不应断字
        assertEquals("short word should have no break points", 0, breakPoints.size)
    }

    // T-4.2.7: BreakIterator 效果验证
    @Test
    fun findBreakPoints_longWord_hasValidBreakPoints() {
        val breakPoints = HyphenationEngine.findBreakPoints("internationalization")
        // 应该能找到断字点
        assertTrue("long word should have break points", breakPoints.isNotEmpty())
        // 断字点应该是有序的
        for (i in 1 until breakPoints.size) {
            assertTrue("break points should be sorted",
                breakPoints[i] > breakPoints[i - 1])
        }
    }
}
