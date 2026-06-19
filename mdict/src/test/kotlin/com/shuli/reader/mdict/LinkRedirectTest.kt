package com.shuli.reader.mdict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * @@@LINK= 重定向测试。对应 docs/38 §5.6。
 * 「踟躇」「彷徨」均重定向到「踟蹰」，readDefinition 应返回目标词的释义。
 */
class LinkRedirectTest {

    private val target = "<b>chí chú</b>：迟疑不前，徘徊不决。"

    @Test
    fun `redirect with trailing whitespace resolves to target`() {
        MdictParser.open(Fixtures.extract("v2_utf8_link.mdx")).use { parser ->
            val entry = parser.lookup("踟躇")
            assertNotNull(entry)
            assertEquals(target, parser.readDefinition(entry!!))
        }
    }

    @Test
    fun `redirect without trailing whitespace resolves to target`() {
        MdictParser.open(Fixtures.extract("v2_utf8_link.mdx")).use { parser ->
            val entry = parser.lookup("彷徨")
            assertNotNull(entry)
            assertEquals(target, parser.readDefinition(entry!!))
        }
    }

    @Test
    fun `direct entry is unaffected by redirect logic`() {
        MdictParser.open(Fixtures.extract("v2_utf8_link.mdx")).use { parser ->
            val entry = parser.lookup("踟蹰")
            assertNotNull(entry)
            assertEquals(target, parser.readDefinition(entry!!))
        }
    }
}
