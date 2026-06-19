package com.shuli.reader.mdict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 前缀查询测试。对应 docs/38 §9.1。
 * 用 multiblock fixture（word000..word199），覆盖跨块前缀扫描。
 */
class PrefixRangeTest {

    @Test
    fun `prefix matches across blocks in order`() {
        MdictParser.open(Fixtures.extract("multiblock_v2_utf8.mdx")).use { parser ->
            val hits = parser.prefixRange("word01", limit = 100)
            // word010..word019 共 10 个
            assertEquals(10, hits.size)
            val words = hits.map { it.keyword }
            assertEquals((10..19).map { "word0$it" }, words)
        }
    }

    @Test
    fun `limit is respected`() {
        MdictParser.open(Fixtures.extract("multiblock_v2_utf8.mdx")).use { parser ->
            val hits = parser.prefixRange("word", limit = 5)
            assertEquals(5, hits.size)
            assertEquals("word000", hits.first().keyword)
        }
    }

    @Test
    fun `no match returns empty`() {
        MdictParser.open(Fixtures.extract("multiblock_v2_utf8.mdx")).use { parser ->
            assertTrue(parser.prefixRange("zzz", 10).isEmpty())
        }
    }

    @Test
    fun `empty prefix and zero limit return empty`() {
        MdictParser.open(Fixtures.extract("multiblock_v2_utf8.mdx")).use { parser ->
            assertTrue(parser.prefixRange("", 10).isEmpty())
            assertTrue(parser.prefixRange("word", 0).isEmpty())
        }
    }

    @Test
    fun `prefix entries are readable`() {
        MdictParser.open(Fixtures.extract("multiblock_v2_utf8.mdx")).use { parser ->
            val hit = parser.prefixRange("word042", 1).single()
            assertEquals("word042", hit.keyword)
            assertEquals("definition number 42 for word042", parser.readDefinition(hit))
        }
    }
}
