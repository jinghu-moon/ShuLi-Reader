// Part of fix(sync): Zstd 解压切换至 getFrameContentSize
package com.shuli.reader.sync.backup

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdOutputStream
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookTagCrossRef
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import com.shuli.reader.core.database.entity.TagEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class ZstdDecompressTest {

    /** 最小 ImportDatabase 实现，tryDecompressZstd 不依赖数据库 */
    private object NoOpImportDb : ImportDatabase {
        override suspend fun getAllBooks() = emptyList<BookEntity>()
        override suspend fun getAllBookmarks() = emptyList<BookmarkEntity>()
        override suspend fun getAllNotes() = emptyList<NoteEntity>()
        override suspend fun getAllProgress() = emptyList<ReadingProgressEntity>()
        override suspend fun getAllTags() = emptyList<TagEntity>()
        override suspend fun getAllBookTagCrossRefs() = emptyList<BookTagCrossRef>()
        override suspend fun getAllReadingSessions() = emptyList<com.shuli.reader.core.database.entity.ReadingSessionEntity>()
        override suspend fun upsertBook(book: BookEntity) {}
        override suspend fun clearBooks() {}
        override suspend fun getExistingBookIds(): Set<Long> = emptySet()
        override suspend fun upsertBookmark(bookmark: BookmarkEntity) {}
        override suspend fun clearBookmarks() {}
        override suspend fun getExistingBookmarkIds(): Set<Long> = emptySet()
        override suspend fun upsertNote(note: NoteEntity) {}
        override suspend fun clearNotes() {}
        override suspend fun getExistingNoteIds(): Set<Long> = emptySet()
        override suspend fun upsertProgress(progress: ReadingProgressEntity) {}
        override suspend fun clearProgress() {}
        override suspend fun getExistingProgressBookIds(): Set<Long> = emptySet()
        override suspend fun insertTag(tag: TagEntity): Long = 0
        override suspend fun addTagToBook(crossRef: BookTagCrossRef) {}
        override suspend fun upsertReadingSession(session: com.shuli.reader.core.database.entity.ReadingSessionEntity) {}
        override suspend fun clearReadingSessions() {}
        override suspend fun runInTransaction(block: suspend () -> Unit) {}
    }

    private val importer = ZipImporter(db = NoOpImportDb)

    @Test
    fun `normal frame with known size decompresses correctly`() {
        val original = "Hello, ZSTD compression test! 你好世界".toByteArray()
        val compressed = Zstd.compress(original)

        val result = importer.tryDecompressZstd(compressed)

        assertArrayEquals("正常帧应完整解压", original, result)
    }

    @Test
    fun `streaming frame without content size falls back to stream decompression`() {
        val original = "Streaming mode test content".toByteArray()
        val baos = ByteArrayOutputStream()
        ZstdOutputStream(baos).use { it.write(original) }
        val compressed = baos.toByteArray()

        val frameSize = Zstd.getFrameContentSize(compressed, 0, compressed.size)
        assertEquals("流式帧的 content size 应为 0 或负值", true, frameSize <= 0)

        val result = importer.tryDecompressZstd(compressed)

        assertArrayEquals("流式帧应通过 ZstdInputStream 解压", original, result)
    }

    @Test
    fun `invalid zstd frame returns original data`() {
        // zstd magic number + 损坏的后续字节
        val invalidData = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte(), 0x00, 0x01, 0x02)

        val result = importer.tryDecompressZstd(invalidData)

        assertArrayEquals("损坏帧应返回原始数据", invalidData, result)
    }

    @Test
    fun `non-zstd data returns original data`() {
        val plainData = "This is plain text, not zstd compressed.".toByteArray()

        val result = importer.tryDecompressZstd(plainData)

        assertArrayEquals("非 zstd 数据应原样返回", plainData, result)
    }
}
