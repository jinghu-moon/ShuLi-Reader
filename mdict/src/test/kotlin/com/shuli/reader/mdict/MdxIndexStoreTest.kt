package com.shuli.reader.mdict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * KeyIndex 落盘缓存测试。对应 docs/38 §8.3。
 */
class MdxIndexStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `cache is created on first open and reused on second`() {
        val dict = Fixtures.extract("multiblock_v2_utf8.mdx")
        val cacheDir = tmp.newFolder("cache")
        val cacheFile = MdxIndexStore.cacheFileFor(dict, cacheDir)

        assertFalse(cacheFile.exists())
        MdictParser.open(dict, cacheDir).use { /* 首次：解析并写缓存 */ }
        assertTrue("cache file should be created", cacheFile.exists())

        // 二次打开：命中缓存，结果一致
        MdictParser.open(dict, cacheDir).use { parser ->
            assertEquals(200L, parser.entryCount)
            assertEquals(
                "definition number 42 for word042",
                parser.readDefinition(parser.lookup("word042")!!),
            )
        }
    }

    @Test
    fun `cached open yields identical lookups as uncached`() {
        val dict = Fixtures.extract("multiblock_v2_utf8.mdx")
        val cacheDir = tmp.newFolder("cache")
        // 预热缓存
        MdictParser.open(dict, cacheDir).use {}

        MdictParser.open(dict).use { uncached ->
            MdictParser.open(dict, cacheDir).use { cached ->
                for (i in listOf(0, 1, 99, 150, 199)) {
                    val w = "word%03d".format(i)
                    assertEquals(
                        uncached.readDefinition(uncached.lookup(w)!!),
                        cached.readDefinition(cached.lookup(w)!!),
                    )
                }
            }
        }
    }

    @Test
    fun `stale cache is ignored when source changes`() {
        val dict = Fixtures.extract("v2_utf8_zlib.mdx")
        val cacheDir = tmp.newFolder("cache")
        MdictParser.open(dict, cacheDir).use {}
        val cacheFile = MdxIndexStore.cacheFileFor(dict, cacheDir)
        assertTrue(cacheFile.exists())

        // 改 mtime 模拟源文件变化 → load 应返回 null（回退完整解析仍能工作）
        dict.setLastModified(dict.lastModified() + 10_000)
        assertEquals(null, MdxIndexStore.load(dict, cacheFile))

        // 仍能正常打开（回退解析），结果与无缓存一致
        MdictParser.open(dict).use { uncached ->
            MdictParser.open(dict, cacheDir).use { fallback ->
                assertEquals(
                    uncached.readDefinition(uncached.lookup("踟蹰")!!),
                    fallback.readDefinition(fallback.lookup("踟蹰")!!),
                )
            }
        }
    }
}
