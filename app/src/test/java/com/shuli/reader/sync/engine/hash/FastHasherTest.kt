package com.shuli.reader.sync.engine.hash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// Part of T-03 fastHash three-point sampling
class FastHasherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `fastHash reads head, mid-third, tail of file`() {
        // 构造 30KB 文件：头部、中段、尾部内容不同
        val file1 = buildFile(headContent = "AAAA", midContent = "BBBB", tailContent = "CCCC", size = 30_000)
        val file2 = buildFile(headContent = "AAAA", midContent = "DDDD", tailContent = "CCCC", size = 30_000)
        val h1 = FastHasher.compute(file1)
        val h2 = FastHasher.compute(file2)
        assertNotEquals("Hash should differ when mid-section differs", h1, h2)
    }

    @Test
    fun `fastHash includes file size in digest`() {
        val f1 = createFileWithContent("same content", size = 1000)
        val f2 = createFileWithContent("same content", size = 2000)
        assertNotEquals("Hash should differ when file size differs", FastHasher.compute(f1), FastHasher.compute(f2))
    }

    @Test
    fun `fastHash is deterministic for same file`() {
        val file = createFileWithContent("deterministic", size = 5000)
        assertEquals(FastHasher.compute(file), FastHasher.compute(file))
    }

    @Test
    fun `fastHash on small file under 8KB does not throw`() {
        val tinyFile = createFileWithContent("tiny", size = 100)
        FastHasher.compute(tinyFile) // Should not throw
    }

    private fun buildFile(headContent: String, midContent: String, tailContent: String, size: Int): File {
        val file = tempFolder.newFile("test_${System.nanoTime()}.bin")
        val content = ByteArray(size)
        val headBytes = headContent.toByteArray()
        val midBytes = midContent.toByteArray()
        val tailBytes = tailContent.toByteArray()

        // Write head
        System.arraycopy(headBytes, 0, content, 0, minOf(headBytes.size, size))
        // Write mid
        val midOffset = size / 3
        System.arraycopy(midBytes, 0, content, midOffset, minOf(midBytes.size, size - midOffset))
        // Write tail
        val tailOffset = maxOf(0, size - tailBytes.size)
        System.arraycopy(tailBytes, 0, content, tailOffset, minOf(tailBytes.size, size - tailOffset))

        file.writeBytes(content)
        return file
    }

    private fun createFileWithContent(content: String, size: Int): File {
        val file = tempFolder.newFile("test_${System.nanoTime()}.bin")
        val bytes = ByteArray(size)
        val contentBytes = content.toByteArray()
        for (i in bytes.indices) {
            bytes[i] = contentBytes[i % contentBytes.size]
        }
        file.writeBytes(bytes)
        return file
    }
}
