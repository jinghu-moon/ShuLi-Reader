// Part of 备份导出器测试
package com.shuli.reader.sync.export

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdInputStream
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
import java.util.zip.ZipInputStream

class BackupExporterTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "backup_exporter_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    /** 读取导出文件的 ZIP 条目名称（逐条目 ZSTD，无外层压缩） */
    private fun readZipEntryNames(outputFile: File): List<String> {
        val entries = mutableListOf<String>()
        outputFile.inputStream().use { fis ->
            ZipInputStream(fis).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries.add(entry.name)
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return entries
    }

    /** 读取导出文件中指定条目的内容（自动 ZSTD 解压） */
    private fun readZipEntryContent(outputFile: File, entryName: String): String? {
        outputFile.inputStream().use { fis ->
            ZipInputStream(fis).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == entryName) {
                        val bytes = zip.readBytes()
                        return if (entryName.endsWith(".json")) {
                            // 条目经过 ZSTD 压缩
                            val decompressedSize = Zstd.decompressedSize(bytes)
                            if (decompressedSize > 0) {
                                Zstd.decompress(bytes, decompressedSize.toInt()).decodeToString()
                            } else {
                                ZstdInputStream(ByteArrayInputStream(bytes)).use { zstd ->
                                    zstd.readBytes().decodeToString()
                                }
                            }
                        } else {
                            String(bytes)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }

    @Test
    fun `export includes all entry types by default`() = runTest {
        val fakeDb = FakeExportDatabase(
            books = listOf(
                BookEntity(
                    id = 1,
                    bookKey = "test-book",
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
                    updatedTime = System.currentTimeMillis(),
                ),
            ),
        )
        val exporter = BackupExporter(db = fakeDb)
        val outputFile = File(tempDir, "export.zip")

        exporter.export(outputFile, options = ExportOptions())

        assertTrue("导出文件应存在", outputFile.exists())
        assertTrue("导出文件应大于0", outputFile.length() > 0)

        val entries = readZipEntryNames(outputFile)
        assertTrue("应包含 manifest.json", entries.contains("manifest.json"))
        assertTrue("应包含 books.json", entries.contains("books.json"))
        assertTrue("应包含 states/ 目录", entries.any { it.startsWith("states/") })
        assertTrue("应包含 bookmarks/ 目录", entries.any { it.startsWith("bookmarks/") })
        assertTrue("应包含 notes/ 目录", entries.any { it.startsWith("notes/") })
        assertTrue("应包含 config/ 目录", entries.any { it.startsWith("config/") })
    }

    @Test
    fun `export excludes book files when option is false`() = runTest {
        val fakeDb = FakeExportDatabase(
            books = listOf(
                BookEntity(
                    id = 1,
                    bookKey = "test-book",
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
        val exporter = BackupExporter(db = fakeDb)
        val outputFile = File(tempDir, "export.zip")

        exporter.export(outputFile, ExportOptions(includeBookFiles = false))

        val entries = readZipEntryNames(outputFile)
        assertTrue(
            "不应包含书籍文件",
            entries.none { it.startsWith("books/") && !it.endsWith(".json") },
        )
    }

    @Test
    fun `export produces valid ZIP with ZSTD compressed entries`() = runTest {
        val fakeDb = FakeExportDatabase(
            books = listOf(
                BookEntity(
                    id = 1,
                    bookKey = "test-book",
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
        val exporter = BackupExporter(db = fakeDb)
        val outputFile = File(tempDir, "export.zip")

        exporter.export(outputFile, options = ExportOptions())

        // 验证 manifest.json 内容可读
        val manifest = readZipEntryContent(outputFile, "manifest.json")
        assertTrue("manifest 应包含 version", manifest?.contains("\"version\"") == true)

        // 验证 books.json 内容可读
        val booksJson = readZipEntryContent(outputFile, "books.json")
        assertTrue("books.json 应包含书名", booksJson?.contains("测试书籍") == true)
    }
}
