// Part of 加密备份导出/导入测试
package com.shuli.reader.sync.backup

import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class EncryptedBackupTest {

    private lateinit var tempDir: File
    private val testPassword = "TestPassword123!"

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "encrypted_backup_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `export with password produces file that cannot be read as plain ZIP`() = runTest {
        val fakeDb = FakeExportDatabase(
            books = listOf(
                BookEntity(
                    id = 1,
                    bookKey = "test-book",
                    title = "测试书籍",
                    author = "测试",
                    filePath = "/test.txt",
                    fileType = "TXT",
                    fileSize = 1024L,
                    coverPath = null,
                    lastReadTime = null,
                    addedTime = System.currentTimeMillis(),
                ),
            ),
        )
        val exporter = BackupExporter(db = fakeDb)
        val outputFile = File(tempDir, "encrypted_export.bin")

        exporter.export(outputFile, ExportOptions(encryptionPassword = testPassword))

        assertTrue("加密导出文件应存在", outputFile.exists())
        assertTrue("加密导出文件应大于0", outputFile.length() > 0)

        // 尝试作为普通 ZIP 读取应失败
        var exceptionThrown = false
        try {
            ZipFile(outputFile).use { it.entries() }
        } catch (_: Exception) {
            exceptionThrown = true
        }
        assertTrue("加密文件不应能作为普通 ZIP 读取", exceptionThrown)
    }

    @Test
    fun `export then import with same password recovers original data`() = runTest {
        val originalBookmarks = listOf(
            BookmarkEntity(
                id = 1,
                bookId = 1,
                createdTime = 12345L,
                byteOffset = 100L,
                selectedText = "测试书签",
            ),
        )
        val fakeDb = FakeExportDatabase(
            books = listOf(
                BookEntity(
                    id = 1,
                    bookKey = "test-book",
                    title = "测试书籍",
                    author = "测试",
                    filePath = "/test.txt",
                    fileType = "TXT",
                    fileSize = 1024L,
                    coverPath = null,
                    lastReadTime = null,
                    addedTime = System.currentTimeMillis(),
                ),
            ),
            bookmarks = originalBookmarks,
        )
        val exporter = BackupExporter(db = fakeDb)
        val encryptedFile = File(tempDir, "encrypted.bin")

        // 导出加密文件
        exporter.export(encryptedFile, ExportOptions(encryptionPassword = testPassword))

        // 解密并导入
        val importDb = FakeImportDatabase()
        val importer = BackupImporter(db = importDb)
        importer.import(encryptedFile, ImportStrategy.OVERWRITE, testPassword)

        val importedBookmarks = importDb.getBookmarks()
        assertTrue("应导入书签", importedBookmarks.isNotEmpty())
        assertEquals(
            "导入的书签 byteOffset 应与原始一致",
            100L,
            importedBookmarks.first().byteOffset,
        )
        assertEquals(
            "导入的书签 selectedText 应与原始一致",
            "测试书签",
            importedBookmarks.first().selectedText,
        )
    }

    @Test
    fun `import with wrong password throws exception`() = runTest {
        val fakeDb = FakeExportDatabase(
            books = listOf(
                BookEntity(
                    id = 1,
                    bookKey = "test-book",
                    title = "测试书籍",
                    author = "测试",
                    filePath = "/test.txt",
                    fileType = "TXT",
                    fileSize = 1024L,
                    coverPath = null,
                    lastReadTime = null,
                    addedTime = System.currentTimeMillis(),
                ),
            ),
        )
        val exporter = BackupExporter(db = fakeDb)
        val encryptedFile = File(tempDir, "encrypted.bin")

        exporter.export(encryptedFile, ExportOptions(encryptionPassword = testPassword))

        val importDb = FakeImportDatabase()
        val importer = BackupImporter(db = importDb)

        var exceptionThrown = false
        try {
            importer.import(encryptedFile, ImportStrategy.OVERWRITE, "WrongPassword!")
        } catch (_: Exception) {
            exceptionThrown = true
        }
        assertTrue("错误密码应抛出异常", exceptionThrown)
    }
}
