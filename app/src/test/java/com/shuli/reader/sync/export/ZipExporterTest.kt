// Part of T-30 ZIP 明文导出
package com.shuli.reader.sync.export

import com.github.luben.zstd.Zstd
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Collections
import java.util.zip.ZipInputStream

class ZipExporterTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "zip_export_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    /**
     * 读取导出文件并解析 ZIP 条目名称。
     * 导出文件格式为 ZSTD 压缩的 ZIP，需先解压再解析。
     */
    private fun readZipEntryNames(outputFile: File): List<String> {
        val fileBytes = outputFile.readBytes()
        // ZSTD 解压
        val zipBytes = Zstd.decompress(fileBytes, Zstd.decompressedSize(fileBytes).toInt())
        // 解析 ZIP 条目
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    @Test
    fun `export includes progress, bookmarks, notes, config by default`() = runTest {
        val fakeDb = FakeExportDatabase(
            books = listOf(
                BookEntity(
                    id = 1,
                    title = "测试书籍",
                    author = "测试作者",
                    filePath = "/storage/test.txt",
                    fileType = "TXT",
                    fileSize = 1024L,
                    coverPath = null,
                    lastReadTime = null,
                    addedTime = System.currentTimeMillis(),
                ),
            ),
            bookmarks = listOf(
                BookmarkEntity(
                    id = 1,
                    bookId = 1,
                    createdTime = System.currentTimeMillis(),
                    byteOffset = 100L,
                    selectedText = "测试书签",
                ),
            ),
            notes = listOf(
                NoteEntity(
                    id = 1,
                    bookId = 1,
                    createdTime = System.currentTimeMillis(),
                    byteStart = 0L,
                    byteEnd = 50L,
                    noteText = "测试笔记",
                ),
            ),
            progress = listOf(
                ReadingProgressEntity(
                    id = 1,
                    bookId = 1,
                    pageIndex = 0,
                    position = 0,
                    readTime = 60L,
                    updatedTime = System.currentTimeMillis(),
                ),
            ),
        )
        val exporter = ZipExporter(db = fakeDb)
        val outputFile = File(tempDir, "export.zip")

        exporter.export(outputFile, options = ExportOptions())

        assertTrue("导出文件应存在", outputFile.exists())
        assertTrue("导出文件应大于0", outputFile.length() > 0)

        val entries = readZipEntryNames(outputFile)
        assertTrue("应包含 manifest.json", entries.contains("manifest.json"))
        assertTrue("应包含 books.json", entries.contains("books.json"))
        assertTrue("应包含 states/ 目录", entries.any { name -> name.startsWith("states/") })
        assertTrue("应包含 bookmarks/ 目录", entries.any { name -> name.startsWith("bookmarks/") })
        assertTrue("应包含 notes/ 目录", entries.any { name -> name.startsWith("notes/") })
        assertTrue("应包含 config/ 目录", entries.any { name -> name.startsWith("config/") })
    }

    @Test
    fun `export excludes book files when option is false`() = runTest {
        val fakeDb = FakeExportDatabase(
            books = listOf(
                BookEntity(
                    id = 1,
                    title = "测试书籍",
                    author = "测试作者",
                    filePath = "/storage/test.txt",
                    fileType = "TXT",
                    fileSize = 1024L,
                    coverPath = null,
                    lastReadTime = null,
                    addedTime = System.currentTimeMillis(),
                ),
            ),
        )
        val exporter = ZipExporter(db = fakeDb)
        val outputFile = File(tempDir, "export.zip")

        exporter.export(outputFile, ExportOptions(includeBookFiles = false))

        val entries = readZipEntryNames(outputFile)
        assertTrue(
            "不应包含书籍文件",
            entries.none { name -> name.startsWith("books/") || name.endsWith(".txt") || name.endsWith(".epub") },
        )
    }

    @Test
    fun `export file is smaller with ZSTD compression`() = runTest {
        val fakeDb = FakeExportDatabase(
            books = listOf(
                BookEntity(
                    id = 1,
                    title = "测试书籍".repeat(100),
                    author = "测试作者",
                    filePath = "/storage/test.txt",
                    fileType = "TXT",
                    fileSize = 1024L,
                    coverPath = null,
                    lastReadTime = null,
                    addedTime = System.currentTimeMillis(),
                ),
            ),
        )
        val exporter = ZipExporter(db = fakeDb)
        val outputFile = File(tempDir, "export.zip")

        exporter.export(outputFile, options = ExportOptions())

        // 验证 ZSTD 压缩有效：文件应该是 ZSTD 格式
        val fileBytes = outputFile.readBytes()
        val decompressedSize = Zstd.decompressedSize(fileBytes)
        assertTrue("ZSTD 解压后大小应大于压缩后大小", decompressedSize > fileBytes.size)
    }
}

/**
 * 用于测试的假数据库实现。
 */
class FakeExportDatabase(
    private val books: List<BookEntity> = emptyList(),
    private val bookmarks: List<BookmarkEntity> = emptyList(),
    private val notes: List<NoteEntity> = emptyList(),
    private val progress: List<ReadingProgressEntity> = emptyList(),
) : ExportDatabase {
    override suspend fun getAllBooks(): List<BookEntity> = books
    override suspend fun getAllBookmarks(): List<BookmarkEntity> = bookmarks
    override suspend fun getAllNotes(): List<NoteEntity> = notes
    override suspend fun getAllProgress(): List<ReadingProgressEntity> = progress
}
