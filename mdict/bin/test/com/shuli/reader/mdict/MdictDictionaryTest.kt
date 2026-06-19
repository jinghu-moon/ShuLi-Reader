package com.shuli.reader.mdict

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协程外观测试。对应 docs/38 §8.4。
 */
class MdictDictionaryTest {

    @Test
    fun `suspend lookup and define work`() = runTest {
        val expected = "<b>chí chú</b>：迟疑不前，徘徊不决。亦作「踟躇」。"
        val dict = MdictDictionary.open(Fixtures.extract("v2_utf8_zlib.mdx"), ioDispatcher = Dispatchers.Default)
        dict.use {
            val entry = it.lookup("踟蹰")
            assertNotNull(entry)
            assertEquals(expected, it.readDefinition(entry!!))
            assertEquals(expected, it.define("踟蹰"))
            assertNull(it.define("不存在xyz"))
        }
    }

    @Test
    fun `prefix range via suspend api`() = runTest {
        MdictDictionary.open(Fixtures.extract("multiblock_v2_utf8.mdx"), ioDispatcher = Dispatchers.Default).use {
            val hits = it.prefixRange("word01", 100)
            assertEquals(10, hits.size)
        }
    }

    @Test
    fun `cancellation is honored`() = runTest {
        val dict = MdictDictionary.open(Fixtures.extract("v2_utf8_zlib.mdx"), ioDispatcher = Dispatchers.Default)
        dict.use {
            var cancelled = false
            val job = launch(Dispatchers.Default) {
                cancel() // 先取消本协程
                try {
                    it.lookup("踟蹰") // onIo 的 ensureActive 应观察到取消
                } catch (e: CancellationException) {
                    cancelled = true
                }
            }
            job.join()
            assertTrue("lookup should observe cancellation", cancelled)
        }
    }
}
