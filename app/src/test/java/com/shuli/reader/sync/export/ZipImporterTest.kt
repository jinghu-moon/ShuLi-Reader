// Part of T-31 ZIP 导入
package com.shuli.reader.sync.export

import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipImporterTest {

    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "zip_import_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    private fun buildTestZip(bookmarks: List<BookmarkEntity> = emptyList()): File {
        val zipFile = File(tempDir, "test_import.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            // manifest.json
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"version":1,"exportedAt":${System.currentTimeMillis()},"appVersion":"0.1.0"}""".toByteArray())
            zip.closeEntry()

            // books.json
            zip.putNextEntry(ZipEntry("books.json"))
            zip.write("""[{"bookKey":"test-book","title":"测试书籍","author":"测试","filePath":"/test.txt","fileType":"TXT","fileSize":1024,"addedTime":${System.currentTimeMillis()},"lastReadTime":0,"readingProgress":0.0}]""".toByteArray())
            zip.closeEntry()

            // bookmarks
            for (bm in bookmarks) {
                zip.putNextEntry(ZipEntry("bookmarks/test-book.json"))
                zip.write("""[{"byteOffset":${bm.byteOffset},"selectedText":"${bm.selectedText ?: ""}","createdTime":${bm.createdTime}}]""".toByteArray())
                zip.closeEntry()
            }
        }
        return zipFile
    }

    @Test
    fun `import OVERWRITE strategy clears local data first`() = runTest {
        val localBookmarks = mutableListOf(
            BookmarkEntity(
                id = 1,
                bookId = 1,
                createdTime = System.currentTimeMillis(),
                byteOffset = 100L,
                selectedText = "本地书签",
            ),
        )
        val fakeDb = FakeImportDatabase(bookmarks = localBookmarks)
        val importer = ZipImporter(db = fakeDb)
        val zipFile = buildTestZip(bookmarks = emptyList())

        importer.import(zipFile, strategy = ImportStrategy.OVERWRITE)

        assertTrue("OVERWRITE 策略应清空本地书签", fakeDb.getBookmarks().isEmpty())
    }

    @Test
    fun `import MERGE strategy keeps newer local items`() = runTest {
        val localBookmarks = mutableListOf(
            BookmarkEntity(
                id = 1,
                bookId = 1,
                createdTime = 9999L,
                byteOffset = 50L,
                selectedText = "本地书签（较新）",
                updatedAt = 9999L,
            ),
        )
        val fakeDb = FakeImportDatabase(bookmarks = localBookmarks)
        val importer = ZipImporter(db = fakeDb)

        // 创建包含旧书签的 ZIP
        val zipFile = buildTestZip(
            bookmarks = listOf(
                BookmarkEntity(
                    id = 1,
                    bookId = 1,
                    createdTime = 1000L,
                    byteOffset = 999L,
                    selectedText = "ZIP 书签（较旧）",
                    updatedAt = 1000L,
                ),
            ),
        )

        importer.import(zipFile, strategy = ImportStrategy.SMART_MERGE)

        assertEquals("SMART_MERGE 应保留较新的本地书签", 9999L, fakeDb.getBookmarks().first().updatedAt)
    }

    @Test
    fun `import IMPORT_ONLY_NEW ignores already-existing items`() = runTest {
        val localBookmarks = mutableListOf(
            BookmarkEntity(
                id = 1,
                bookId = 1,
                createdTime = System.currentTimeMillis(),
                byteOffset = 50L,
                selectedText = "已存在的书签",
            ),
        )
        val fakeDb = FakeImportDatabase(bookmarks = localBookmarks)
        val importer = ZipImporter(db = fakeDb)

        val zipFile = buildTestZip(
            bookmarks = listOf(
                BookmarkEntity(
                    id = 1,
                    bookId = 1,
                    createdTime = System.currentTimeMillis(),
                    byteOffset = 999L,
                    selectedText = "ZIP 中的同 ID 书签",
                ),
            ),
        )

        importer.import(zipFile, strategy = ImportStrategy.IMPORT_ONLY_NEW)

        assertEquals(
            "IMPORT_ONLY_NEW 应保留原有书签的 byteOffset",
            50L,
            fakeDb.getBookmarks().first().byteOffset,
        )
    }
}

/**
 * 用于测试的假导入数据库实现。
 */
class FakeImportDatabase(
    private val books: MutableList<BookEntity> = mutableListOf(),
    bookmarks: MutableList<BookmarkEntity> = mutableListOf(),
) : ImportDatabase {
    private val bookmarks: MutableList<BookmarkEntity> = bookmarks

    override suspend fun getAllBooks(): List<BookEntity> = books
    override suspend fun getAllBookmarks(): List<BookmarkEntity> = bookmarks
    override suspend fun getAllNotes() = emptyList<com.shuli.reader.core.database.entity.NoteEntity>()
    override suspend fun getAllProgress() = emptyList<com.shuli.reader.core.database.entity.ReadingProgressEntity>()

    override suspend fun clearBookmarks() {
        bookmarks.clear()
    }

    override suspend fun addBookmark(bookmark: BookmarkEntity) {
        bookmarks.add(bookmark)
    }

    fun getBookmarks(): List<BookmarkEntity> = bookmarks
}
