// Part of T-32 加密 ZIP 导出/导入
package com.shuli.reader.sync.backup

import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.sync.crypto.AesGcmCipher
import com.shuli.reader.sync.crypto.KeyDerivation
import com.shuli.reader.sync.crypto.KeyDerivationParams
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class EncryptedZipTest {

    private lateinit var tempDir: File
    private val testPassword = "TestPassword123!"
    private val salt = ByteArray(16) { it.toByte() } // 固定盐用于测试

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "encrypted_zip_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `export with password produces encrypted file that cannot be read as plain ZIP`() = runTest {
        val fakeDb = FakeExportDatabase(
            books = listOf(
                BookEntity(
                    id = 1,
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
            bookmarks = listOf(
                BookmarkEntity(
                    id = 1,
                    bookId = 1,
                    createdTime = System.currentTimeMillis(),
                    byteOffset = 100L,
                    selectedText = "测试书签",
                ),
            ),
        )
        val exporter = ZipExporter(db = fakeDb)
        val outputFile = File(tempDir, "encrypted_export.bin")

        exporter.export(outputFile, ExportOptions(encryptionPassword = testPassword))

        assertTrue("加密导出文件应存在", outputFile.exists())
        assertTrue("加密导出文件应大于0", outputFile.length() > 0)

        // 尝试作为普通 ZIP 读取应失败
        var exceptionThrown = false
        try {
            ZipFile(outputFile).use { it.entries() }
        } catch (e: Exception) {
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
        val exporter = ZipExporter(db = fakeDb)
        val encryptedFile = File(tempDir, "encrypted.bin")

        // 导出加密文件
        exporter.export(encryptedFile, ExportOptions(encryptionPassword = testPassword))

        // 解密并导入
        val importDb = FakeImportDatabase()
        val importer = ZipImporter(db = importDb)
        importer.import(encryptedFile, ImportStrategy.OVERWRITE, testPassword)

        val importedBookmarks = importDb.getBookmarks()
        assertTrue("应导入书签", importedBookmarks.isNotEmpty())
        assertArrayEquals(
            "导入的书签 byteOffset 应与原始一致",
            originalBookmarks.map { it.byteOffset }.toLongArray(),
            importedBookmarks.map { it.byteOffset }.toLongArray(),
        )
    }

    @Test
    fun `import with wrong password throws exception`() = runTest {
        val fakeDb = FakeExportDatabase(
            books = listOf(
                BookEntity(
                    id = 1,
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
        val exporter = ZipExporter(db = fakeDb)
        val encryptedFile = File(tempDir, "encrypted.bin")

        exporter.export(encryptedFile, ExportOptions(encryptionPassword = testPassword))

        val importDb = FakeImportDatabase()
        val importer = ZipImporter(db = importDb)

        var exceptionThrown = false
        try {
            importer.import(encryptedFile, ImportStrategy.OVERWRITE, "WrongPassword!")
        } catch (e: Exception) {
            exceptionThrown = true
        }
        assertTrue("错误密码应抛出异常", exceptionThrown)
    }
}
