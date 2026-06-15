// Part of 备份导入器测试
package com.shuli.reader.sync.backup

import com.github.luben.zstd.Zstd
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookTagCrossRef
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import com.shuli.reader.core.database.entity.TagEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupImporterTest {

    private lateinit var tempDir: File
    private val json = Json { prettyPrint = true }

    @Before
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "backup_importer_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    /** 构建测试用 ZIP 文件（逐条目 ZSTD 压缩，与 BackupExporter 格式一致） */
    private fun buildTestZip(
        books: List<BookEntity> = emptyList(),
        bookmarks: List<BookmarkEntity> = emptyList(),
        notes: List<NoteEntity> = emptyList(),
        progress: List<ReadingProgressEntity> = emptyList(),
    ): File {
        val zipFile = File(tempDir, "test_import.zip")
        ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)

            // manifest.json (ZSTD)
            writeZstdEntry(zip, "manifest.json", """{"version":2,"exportedAt":${System.currentTimeMillis()},"appVersion":"0.1.0"}""")

            // books.json (ZSTD)
            if (books.isNotEmpty()) {
                val arr = buildJsonArray {
                    for (book in books) {
                        add(buildJsonObject {
                            put("id", book.id)
                            put("bookKey", book.bookKey)
                            put("title", book.title)
                            put("author", book.author ?: "")
                            put("filePath", book.filePath)
                            put("fileType", book.fileType)
                            put("fileSize", book.fileSize)
                            put("addedTime", book.addedTime)
                            put("lastReadTime", book.lastReadTime ?: 0L)
                            put("readingProgress", book.readingProgress.toDouble())
                            put("isFavorite", book.isFavorite)
                            put("folderId", book.folderId)
                            put("totalChapterNum", book.totalChapterNum)
                            put("durByteOffset", book.durByteOffset)
                            put("durChapterTitle", book.durChapterTitle ?: "")
                        })
                    }
                }
                writeZstdEntry(zip, "books.json", json.encodeToString(JsonElement.serializer(), arr))
            }

            // bookmarks (ZSTD)
            val bmGroups = bookmarks.groupBy { bm ->
                books.find { it.id == bm.bookId }?.bookKey
            }
            for ((bookKey, bmList) in bmGroups) {
                if (bookKey == null) continue
                val arr = buildJsonArray {
                    for (bm in bmList) {
                        add(buildJsonObject {
                            put("byteOffset", bm.byteOffset)
                            put("selectedText", bm.selectedText ?: "")
                            put("createdTime", bm.createdTime)
                            put("updatedAt", bm.updatedAt)
                        })
                    }
                }
                writeZstdEntry(zip, "bookmarks/$bookKey.json", json.encodeToString(JsonElement.serializer(), arr))
            }

            // notes (ZSTD)
            val noteGroups = notes.groupBy { note ->
                books.find { it.id == note.bookId }?.bookKey
            }
            for ((bookKey, noteList) in noteGroups) {
                if (bookKey == null) continue
                val arr = buildJsonArray {
                    for (note in noteList) {
                        add(buildJsonObject {
                            put("byteStart", note.byteStart)
                            put("byteEnd", note.byteEnd)
                            put("noteText", note.noteText)
                            put("color", note.color ?: "")
                            put("createdTime", note.createdTime)
                            put("updatedAt", note.updatedAt)
                        })
                    }
                }
                writeZstdEntry(zip, "notes/$bookKey.json", json.encodeToString(JsonElement.serializer(), arr))
            }

            // progress (ZSTD)
            val progGroups = progress.groupBy { prog ->
                books.find { it.id == prog.bookId }?.bookKey
            }
            for ((bookKey, progList) in progGroups) {
                if (bookKey == null) continue
                val arr = buildJsonArray {
                    for (prog in progList) {
                        add(buildJsonObject {
                            put("pageIndex", prog.pageIndex)
                            put("position", prog.position)
                            put("updatedTime", prog.updatedTime)
                        })
                    }
                }
                writeZstdEntry(zip, "states/$bookKey.json", json.encodeToString(JsonElement.serializer(), arr))
            }
        }
        return zipFile
    }

    private fun writeZstdEntry(zip: ZipOutputStream, entryName: String, content: String) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(Zstd.compress(content.toByteArray(), 3))
        zip.closeEntry()
    }

    @Test
    fun `import OVERWRITE clears local data first`() = runTest {
        val localBooks = mutableListOf(
            BookEntity(id = 1, bookKey = "local-book", title = "本地书籍", author = null, filePath = "/local.txt", fileType = "TXT", fileSize = 100L, coverPath = null, lastReadTime = null, addedTime = 1000L),
        )
        val localBookmarks = mutableListOf(
            BookmarkEntity(id = 1, bookId = 1, createdTime = 1000L, byteOffset = 50L, selectedText = "本地书签"),
        )
        val fakeDb = FakeImportDatabase(books = localBooks, bookmarks = localBookmarks)
        val importer = BackupImporter(db = fakeDb)

        val zipFile = buildTestZip(
            books = listOf(
                BookEntity(id = 10, bookKey = "zip-book", title = "ZIP书籍", author = null, filePath = "/zip.txt", fileType = "TXT", fileSize = 200L, coverPath = null, lastReadTime = null, addedTime = 2000L),
            ),
            bookmarks = listOf(
                BookmarkEntity(id = 10, bookId = 10, createdTime = 2000L, byteOffset = 100L, selectedText = "ZIP书签"),
            ),
        )

        importer.import(zipFile, strategy = ImportStrategy.OVERWRITE)

        assertEquals("OVERWRITE 应清空旧书籍并导入新书籍", 1, fakeDb.getBooks().size)
        assertEquals("OVERWRITE 应清空旧书签并导入新书签", 1, fakeDb.getBookmarks().size)
        assertEquals("新书签应为 ZIP 中的书签", "ZIP书签", fakeDb.getBookmarks().first().selectedText)
    }

    @Test
    fun `import MERGE upserts entities`() = runTest {
        val localBooks = mutableListOf(
            BookEntity(id = 1, bookKey = "existing-book", title = "已有书籍", author = null, filePath = "/existing.txt", fileType = "TXT", fileSize = 100L, coverPath = null, lastReadTime = null, addedTime = 1000L),
        )
        val fakeDb = FakeImportDatabase(books = localBooks)
        val importer = BackupImporter(db = fakeDb)

        // ZIP 包含同 id 的书籍（应更新）和新书籍（应插入）
        val zipFile = buildTestZip(
            books = listOf(
                BookEntity(id = 1, bookKey = "existing-book", title = "更新后的书籍", author = null, filePath = "/updated.txt", fileType = "TXT", fileSize = 100L, coverPath = null, lastReadTime = null, addedTime = 3000L),
                BookEntity(id = 2, bookKey = "new-book", title = "新书籍", author = null, filePath = "/new.txt", fileType = "TXT", fileSize = 200L, coverPath = null, lastReadTime = null, addedTime = 4000L),
            ),
        )

        importer.import(zipFile, strategy = ImportStrategy.MERGE)

        assertEquals("MERGE 应保留并更新已有书籍", 2, fakeDb.getBooks().size)
        val updatedBook = fakeDb.getBooks().find { it.id == 1L }
        assertEquals("同 id 书籍应被更新", "更新后的书籍", updatedBook?.title)
    }

    @Test
    fun `import reads bookmarks with bookKey mapping`() = runTest {
        val fakeDb = FakeImportDatabase()
        val importer = BackupImporter(db = fakeDb)

        val zipFile = buildTestZip(
            books = listOf(
                BookEntity(id = 5, bookKey = "my-book", title = "我的书", author = null, filePath = "/book.txt", fileType = "TXT", fileSize = 500L, coverPath = null, lastReadTime = null, addedTime = 1000L),
            ),
            bookmarks = listOf(
                BookmarkEntity(id = 1, bookId = 5, createdTime = 1000L, byteOffset = 100L, selectedText = "书签1"),
                BookmarkEntity(id = 2, bookId = 5, createdTime = 2000L, byteOffset = 200L, selectedText = "书签2"),
            ),
        )

        importer.import(zipFile, strategy = ImportStrategy.OVERWRITE)

        assertEquals("应导入 2 个书签", 2, fakeDb.getBookmarks().size)
        assertTrue("书签应映射到正确的 bookId", fakeDb.getBookmarks().all { it.bookId == 5L })
    }
}

/**
 * 用于测试的假导入数据库实现。
 * 实现完整的 ImportDatabase 接口，包括 upsert/clear/transaction。
 */
class FakeImportDatabase(
    private val books: MutableList<BookEntity> = mutableListOf(),
    bookmarks: MutableList<BookmarkEntity> = mutableListOf(),
    private val notes: MutableList<NoteEntity> = mutableListOf(),
    private val progress: MutableList<ReadingProgressEntity> = mutableListOf(),
) : ImportDatabase {
    private val bookmarks: MutableList<BookmarkEntity> = bookmarks

    override suspend fun getAllBooks(): List<BookEntity> = books
    override suspend fun getAllBookmarks(): List<BookmarkEntity> = bookmarks
    override suspend fun getAllNotes(): List<NoteEntity> = notes
    override suspend fun getAllProgress(): List<ReadingProgressEntity> = progress

    override suspend fun upsertBook(book: BookEntity) {
        val idx = books.indexOfFirst { it.id == book.id }
        if (idx >= 0) books[idx] = book else books.add(book)
    }

    override suspend fun clearBooks() { books.clear() }
    override suspend fun getExistingBookIds(): Set<Long> = books.map { it.id }.toSet()

    override suspend fun upsertBookmark(bookmark: BookmarkEntity) {
        if (bookmark.id == 0L) { bookmarks.add(bookmark); return }
        val idx = bookmarks.indexOfFirst { it.id == bookmark.id }
        if (idx >= 0) bookmarks[idx] = bookmark else bookmarks.add(bookmark)
    }

    override suspend fun clearBookmarks() { bookmarks.clear() }
    override suspend fun getExistingBookmarkIds(): Set<Long> = bookmarks.map { it.id }.toSet()

    override suspend fun upsertNote(note: NoteEntity) {
        if (note.id == 0L) { notes.add(note); return }
        val idx = notes.indexOfFirst { it.id == note.id }
        if (idx >= 0) notes[idx] = note else notes.add(note)
    }

    override suspend fun clearNotes() { notes.clear() }
    override suspend fun getExistingNoteIds(): Set<Long> = notes.map { it.id }.toSet()

    override suspend fun upsertProgress(progress: ReadingProgressEntity) {
        if (progress.id == 0L) { this.progress.add(progress); return }
        val idx = this.progress.indexOfFirst { it.id == progress.id }
        if (idx >= 0) this.progress[idx] = progress else this.progress.add(progress)
    }

    override suspend fun clearProgress() { progress.clear() }
    override suspend fun getExistingProgressBookIds(): Set<Long> = progress.map { it.bookId }.toSet()

    // --- Tags (P1) ---
    private val tags = mutableListOf<TagEntity>()
    private val bookTagRefs = mutableListOf<BookTagCrossRef>()
    override suspend fun getAllTags(): List<TagEntity> = tags
    override suspend fun getAllBookTagCrossRefs(): List<BookTagCrossRef> = bookTagRefs
    override suspend fun insertTag(tag: TagEntity): Long { tags.add(tag); return tag.id }
    override suspend fun addTagToBook(crossRef: BookTagCrossRef) { bookTagRefs.add(crossRef) }

    // --- ReadingSession ---
    private val readingSessions = mutableListOf<com.shuli.reader.core.database.entity.ReadingSessionEntity>()
    override suspend fun getAllReadingSessions() = readingSessions.toList()
    override suspend fun upsertReadingSession(session: com.shuli.reader.core.database.entity.ReadingSessionEntity) {
        val idx = readingSessions.indexOfFirst { it.id == session.id }
        if (idx >= 0) readingSessions[idx] = session else readingSessions.add(session)
    }
    override suspend fun clearReadingSessions() { readingSessions.clear() }

    override suspend fun runInTransaction(block: suspend () -> Unit) {
        // 测试用：直接执行，不包裹事务
        block()
    }

    fun getBooks(): List<BookEntity> = books
    fun getBookmarks(): List<BookmarkEntity> = bookmarks
    fun getNotes(): List<NoteEntity> = notes
    fun getProgress(): List<ReadingProgressEntity> = progress
}
