package com.shuli.reader.mdict

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/**
 * MDD 资源读取测试。对应 docs/38 §5、§9.2。
 * fixture 的 entries 为 路径 → base64（二进制资源的预期值）。
 */
class MddResourceTest {

    private fun decode(b64: String): ByteArray = Base64.getDecoder().decode(b64)

    @Test
    fun `read all resources by exact path`() {
        val spec = Fixtures.byFile("resources_v2.mdd")
        MdictParser.open(Fixtures.extract("resources_v2.mdd")).use { parser ->
            for ((path, expectedB64) in spec.entries) {
                val bytes = parser.readResource(path)
                assertNotNull("missing resource: $path", bytes)
                assertArrayEquals("bytes mismatch: $path", decode(expectedB64), bytes)
            }
        }
    }

    @Test
    fun `path normalization handles forward slashes and missing prefix`() {
        MdictParser.open(Fixtures.extract("resources_v2.mdd")).use { parser ->
            val canonical = parser.readResource("\\red.png")
            assertNotNull(canonical)
            // 正斜杠
            assertArrayEquals(canonical, parser.readResource("/red.png"))
            // 无前导分隔符
            assertArrayEquals(canonical, parser.readResource("red.png"))
            // entry:// 前缀
            assertArrayEquals(canonical, parser.readResource("entry://red.png"))
        }
    }

    @Test
    fun `missing resource returns null`() {
        MdictParser.open(Fixtures.extract("resources_v2.mdd")).use { parser ->
            assertNull(parser.readResource("\\nonexistent.png"))
        }
    }
}
