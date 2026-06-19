package com.shuli.reader.mdict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 端到端解析测试：用 fixture 的 manifest 对照表逐词条验证 lookup + readDefinition。
 * 覆盖 Step 1 全部 zlib/raw 词典 + key-index 加密词典。
 */
class MdictParserTest {

    /** 对一本 MDX fixture：每个词条都能查到且释义与 manifest 一致。 */
    private fun verifyMdx(file: String) {
        val spec = Fixtures.byFile(file)
        MdictParser.open(Fixtures.extract(file)).use { parser ->
            assertEquals("entryCount mismatch for $file", spec.entryCount.toLong(), parser.entryCount)
            for ((word, expected) in spec.entries) {
                val entry = parser.lookup(word)
                assertNotNull("[$file] lookup miss: $word", entry)
                assertEquals("[$file] definition mismatch for $word", expected, parser.readDefinition(entry!!))
            }
        }
    }

    @Test fun `v2 utf8 zlib`() = verifyMdx("v2_utf8_zlib.mdx")
    @Test fun `v2 utf16 zlib`() = verifyMdx("v2_utf16_zlib.mdx")
    @Test fun `v2 gbk zlib`() = verifyMdx("v2_gbk_zlib.mdx")
    @Test fun `v2 big5 zlib`() = verifyMdx("v2_big5_zlib.mdx")
    @Test fun `v2 utf8 none`() = verifyMdx("v2_utf8_none.mdx")
    @Test fun `v12 utf8 zlib`() = verifyMdx("v12_utf8_zlib.mdx")
    @Test fun `v12 utf16 zlib`() = verifyMdx("v12_utf16_zlib.mdx")
    @Test fun `multiblock v2 utf8`() = verifyMdx("multiblock_v2_utf8.mdx")
    @Test fun `v2 utf8 zlib encrypted index`() = verifyMdx("v2_utf8_zlib_encindex.mdx")
    @Test fun `v2 utf8 lzo`() = verifyMdx("v2_utf8_lzo.mdx")
    @Test fun `multiblock v2 lzo`() = verifyMdx("multiblock_v2_lzo.mdx")

    @Test
    fun `lookup missing word returns null`() {
        MdictParser.open(Fixtures.extract("v2_utf8_zlib.mdx")).use { parser ->
            assertNull(parser.lookup("不存在的词条xyz"))
        }
    }
}
