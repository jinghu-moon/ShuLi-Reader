package com.shuli.reader.core.repository

import com.shuli.reader.core.database.dao.BookChapterDao
import com.shuli.reader.core.database.dao.BookDao
import com.shuli.reader.core.parser.ByteWindowReader
import com.shuli.reader.core.parser.DecodedSegment
import com.shuli.reader.core.parser.EpubParser
import com.shuli.reader.core.parser.StreamDecoder
import com.shuli.reader.core.parser.TxtParser
import com.shuli.reader.core.parser.model.BookContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

/**
 * EPUB/TXT 解析、章节文本获取、字节窗口加载。
 * Actor: EPUB/TXT 解析团队、性能工程师
 */
class BookContentRepository(
    private val bookDao: BookDao,
    private val bookChapterDao: BookChapterDao,
    private val txtParser: TxtParser,
    private val epubParser: EpubParser,
    private val byteWindowReader: ByteWindowReader,
) {
    private val streamDecoder = StreamDecoder()

    /**
     * 解析书籍内容（EPUB 用）。
     * TXT 不再走全量解析，请使用 loadByteWindow / loadChapterText。
     */
    suspend fun parseBookContent(file: File, fullParse: Boolean = false): BookContent {
        return when {
            file.name.endsWith(".epub", ignoreCase = true) -> {
                if (fullParse) epubParser.parseWithContent(file) else epubParser.parse(file)
            }
            else -> throw IllegalArgumentException("Unsupported file format for parseBookContent: ${file.name}")
        }
    }

    suspend fun getChapterText(file: File, chapterIndex: Int, bookContent: BookContent): String {
        return getChapterText(file, chapterIndex, bookContent.chapters, bookContent.bookId)
    }

    /**
     * 获取章节正文。
     * EPUB: 通过 spineIndex 解析 XHTML
     * TXT: 从 DB 读取字节偏移，按需读取
     */
    suspend fun getChapterText(
        file: File,
        chapterIndex: Int,
        chapters: List<com.shuli.reader.core.parser.model.Chapter>,
        bookId: Long = 0L,
    ): String = withContext(Dispatchers.IO) {
        val chapter = chapters.getOrNull(chapterIndex) ?: return@withContext ""
        return@withContext if (file.name.endsWith(".epub", ignoreCase = true)) {
            epubParser.parseChapter(file, chapter.spineIndex)
        } else {
            // TXT: 从 DB 单条查询字节偏移，按需读取
            if (bookId > 0L) {
                val chapterEntity = bookChapterDao.getChapter(bookId, chapterIndex)
                if (chapterEntity != null) {
                    return@withContext readTxtChapterByEntity(file, chapterEntity)
                }
            }
            // 兜底：用字节偏移直接读取
            android.util.Log.w("BookContentRepository", "Fallback: no chapter index for bookId=$bookId, chapterIndex=$chapterIndex")
            val charset = txtParser.detectCharset(file)
            val window = byteWindowReader.loadRange(file, chapter.byteStart, chapter.byteEnd)
            streamDecoder.decode(window, charset).text
        }
    }

    /**
     * 按 BookChapterEntity 的字节偏移读取 TXT 章节。
     */
    private fun readTxtChapterByEntity(
        file: File,
        chapter: com.shuli.reader.core.database.entity.BookChapterEntity,
    ): String {
        return try {
            java.io.RandomAccessFile(file, "r").use { raf ->
                raf.seek(chapter.byteStart)
                val byteLength = (chapter.byteEnd - chapter.byteStart).toInt()
                val bytes = ByteArray(byteLength)
                raf.readFully(bytes)
                String(bytes, java.nio.charset.Charset.forName(chapter.charset))
            }
        } catch (e: Exception) {
            android.util.Log.e("BookContentRepository", "Failed to read chapter by byte offset: ${file.absolutePath}, chapterIndex=${chapter.chapterIndex}", e)
            ""
        }
    }

    /**
     * 从指定字节位置加载文本窗口（含 utf16IndexToByte 映射）。
     * 用途：续读首屏、进度条跳转后展示。
     */
    suspend fun loadByteWindow(bookId: Long, byteOffset: Long): DecodedSegment? {
        val book = bookDao.getBookById(bookId).first() ?: return null
        val file = File(book.filePath)
        if (!file.exists()) return null
        val charset = Charset.forName(book.charset)
        val window = byteWindowReader.loadWindow(file, byteOffset)
        return streamDecoder.decode(window, charset)
    }

    /**
     * 加载指定章节的文本（含 utf16IndexToByte 映射）。
     * 用途：章节切换。
     */
    suspend fun loadChapterText(bookId: Long, chapterIndex: Int): DecodedSegment? {
        val chapter = bookChapterDao.getChapter(bookId, chapterIndex) ?: return null
        val book = bookDao.getBookById(bookId).first() ?: return null
        val file = File(book.filePath)
        if (!file.exists()) return null
        val charset = Charset.forName(chapter.charset)
        val window = byteWindowReader.loadRange(file, chapter.byteStart, chapter.byteEnd)
        return streamDecoder.decode(window, charset)
    }

    /**
     * 根据字节偏移解析当前章节标题。
     * 用途：续读时显示章节标题。
     */
    suspend fun resolveChapterTitle(bookId: Long, byteOffset: Long): String? {
        val chapters = bookChapterDao.getChapters(bookId)
        if (chapters.isEmpty()) return null
        return chapters.lastOrNull { it.byteStart <= byteOffset }?.title
    }
}
