package com.shuli.reader.mdict.engine

import com.shuli.reader.mdict.Fixtures
import com.shuli.reader.mdict.UnsupportedDictException
import com.shuli.reader.mdict.io.BlockReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderParserTest {

    private fun parse(file: String): com.shuli.reader.mdict.model.MdxHeader {
        val f = Fixtures.extract(file)
        BlockReader(f).use { return HeaderParser.parse(it) }
    }

    @Test
    fun `v2 utf8 zlib header fields`() {
        val h = parse("v2_utf8_zlib.mdx")
        assertFalse(h.isMdd)
        assertEquals(2.0f, h.version)
        assertEquals(8, h.numberWidth)
        assertEquals(Charsets.UTF_8, h.charset)
        assertEquals(1, h.unitWidth)
        assertEquals(0, h.encrypted)
    }

    @Test
    fun `v12 uses 4-byte number width`() {
        val h = parse("v12_utf8_zlib.mdx")
        assertEquals(1.2f, h.version)
        assertEquals(4, h.numberWidth)
        assertFalse(h.isV2)
    }

    @Test
    fun `utf16 resolves to UTF-16LE with unit width 2`() {
        val h = parse("v2_utf16_zlib.mdx")
        assertEquals(Charsets.UTF_16LE, h.charset)
        assertEquals(2, h.unitWidth)
    }

    @Test
    fun `gbk is promoted to GB18030`() {
        val h = parse("v2_gbk_zlib.mdx")
        assertEquals(java.nio.charset.Charset.forName("GB18030"), h.charset)
        assertEquals(1, h.unitWidth)
    }

    @Test
    fun `encrypt index flag is detected`() {
        val h = parse("v2_utf8_zlib_encindex.mdx")
        assertTrue(h.isKeyIndexEncrypted)
        assertFalse(h.isRecordEncrypted)
    }

    @Test
    fun `mdd is detected and forced to UTF-16LE`() {
        val h = parse("resources_v2.mdd")
        assertTrue(h.isMdd)
        assertEquals(Charsets.UTF_16LE, h.charset)
        assertEquals(2, h.unitWidth)
    }

    @Test
    fun `keySectionStart is past header and adler32`() {
        val h = parse("v2_utf8_zlib.mdx")
        assertTrue("keySectionStart should be > 8", h.keySectionStart > 8)
    }
}
