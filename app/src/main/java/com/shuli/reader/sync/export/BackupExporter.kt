// Part of 备份导出器
package com.shuli.reader.sync.export

import android.content.Context
import android.net.Uri
import com.github.luben.zstd.Zstd
import com.shuli.reader.sync.crypto.KeyDerivation
import com.shuli.reader.sync.crypto.KeyDerivationParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 备份导出器。
 *
 * 将书籍元数据、书签、笔记、阅读进度导出为标准 ZIP 文件。
 * 使用逐条目 ZSTD 压缩（级别 3），ZIP 作为纯容器（NO_COMPRESSION）。
 * 支持可选的 AES-256-GCM 流式加密（CipherOutputStream）。
 *
 * 明文格式：ZIP(DEFLATED level=0, 每条目 ZSTD 压缩)
 * 加密格式：salt(16) + nonce(12) + CipherOutputStream(ZIP) + tag(16)
 */
class BackupExporter(
    private val db: ExportDatabase,
    private val context: Context? = null,
) {
    private val json = Json { prettyPrint = true }

    /**
     * 导出数据到文件。
     *
     * @param outputFile 输出文件路径
     * @param options 导出选项
     */
    suspend fun export(outputFile: File, options: ExportOptions = ExportOptions()) =
        withContext(Dispatchers.IO) {
            val books = db.getAllBooks()
            val bookmarks = db.getAllBookmarks()
            val notes = db.getAllNotes()
            val progress = db.getAllProgress()

            if (options.encryptionPassword != null) {
                exportEncrypted(outputFile, options, books, bookmarks, notes, progress)
            } else {
                exportPlain(outputFile, options, books, bookmarks, notes, progress)
            }
        }

    /** 明文导出：ZIP(NO_COMPRESSION) + 逐条目 ZSTD */
    private fun exportPlain(
        outputFile: File,
        options: ExportOptions,
        books: List<com.shuli.reader.core.database.entity.BookEntity>,
        bookmarks: List<com.shuli.reader.core.database.entity.BookmarkEntity>,
        notes: List<com.shuli.reader.core.database.entity.NoteEntity>,
        progress: List<com.shuli.reader.core.database.entity.ReadingProgressEntity>,
    ) {
        FileOutputStream(outputFile).use { fos ->
            writeZipEntries(fos, options, books, bookmarks, notes, progress)
        }
    }

    /** 加密导出：salt(16) + nonce(12) + CipherOutputStream(ZIP) + tag(16) */
    private fun exportEncrypted(
        outputFile: File,
        options: ExportOptions,
        books: List<com.shuli.reader.core.database.entity.BookEntity>,
        bookmarks: List<com.shuli.reader.core.database.entity.BookmarkEntity>,
        notes: List<com.shuli.reader.core.database.entity.NoteEntity>,
        progress: List<com.shuli.reader.core.database.entity.ReadingProgressEntity>,
    ) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)

        val key = KeyDerivation(
            KeyDerivationParams(
                algorithm = "PBKDF2WithHmacSHA256",
                iterations = 600_000,
                keyLengthBits = 256,
            ),
        ).derive(options.encryptionPassword!!, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

        FileOutputStream(outputFile).use { fos ->
            fos.write(salt)
            fos.write(nonce)
            CipherOutputStream(fos, cipher).use { cos ->
                writeZipEntries(cos, options, books, bookmarks, notes, progress)
            }
        }
    }

    /** 将所有条目写入 ZIP 流 */
    private fun writeZipEntries(
        outputStream: java.io.OutputStream,
        options: ExportOptions,
        books: List<com.shuli.reader.core.database.entity.BookEntity>,
        bookmarks: List<com.shuli.reader.core.database.entity.BookmarkEntity>,
        notes: List<com.shuli.reader.core.database.entity.NoteEntity>,
        progress: List<com.shuli.reader.core.database.entity.ReadingProgressEntity>,
    ) {
        ZipOutputStream(outputStream).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)

            writeManifest(zip)
            writeBooks(zip, books)

            if (options.includeBookFiles) {
                writeBookFiles(zip, books)
            }
            if (options.includeProgress) {
                writeProgress(zip, progress, books)
            }
            if (options.includeBookmarks) {
                writeBookmarks(zip, bookmarks, books)
            }
            if (options.includeNotes) {
                writeNotes(zip, notes, books)
            }
            if (options.includeConfig) {
                writeConfig(zip)
            }
        }
    }

    /** 写入单个 ZSTD 压缩条目 */
    private fun writeZstdEntry(zip: ZipOutputStream, entryName: String, data: ByteArray) {
        zip.putNextEntry(ZipEntry(entryName))
        zip.write(Zstd.compress(data, 3))
        zip.closeEntry()
    }

    private fun writeManifest(zip: ZipOutputStream) {
        val manifest = buildJsonObject {
            put("version", 2)
            put("exportedAt", System.currentTimeMillis())
            put("appVersion", "0.1.0")
        }
        writeZstdEntry(zip, "manifest.json", json.encodeToString(JsonElement.serializer(), manifest).toByteArray())
    }

    private fun writeBooks(
        zip: ZipOutputStream,
        books: List<com.shuli.reader.core.database.entity.BookEntity>,
    ) {
        val booksArray = buildJsonArray {
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
        writeZstdEntry(zip, "books.json", json.encodeToString(JsonElement.serializer(), booksArray).toByteArray())
    }

    private fun writeBookFiles(
        zip: ZipOutputStream,
        books: List<com.shuli.reader.core.database.entity.BookEntity>,
    ) {
        for (book in books) {
            try {
                val fileData = readBookFile(book.filePath) ?: continue
                val ext = book.fileType.lowercase()
                zip.putNextEntry(ZipEntry("books/${book.bookKey}.$ext"))
                zip.write(fileData)
                zip.closeEntry()
            } catch (_: Exception) {
                // 跳过无法读取的文件
            }
        }
    }

    private fun writeProgress(
        zip: ZipOutputStream,
        progress: List<com.shuli.reader.core.database.entity.ReadingProgressEntity>,
        books: List<com.shuli.reader.core.database.entity.BookEntity>,
    ) {
        val bookMap = books.associateBy { it.id }
        val groups = progress.groupBy { it.bookId }
        for ((bookId, progList) in groups) {
            val book = bookMap[bookId] ?: continue
            val arr = buildJsonArray {
                for (prog in progList) {
                    add(buildJsonObject {
                        put("pageIndex", prog.pageIndex)
                        put("position", prog.position)
                        put("readTime", prog.readTime)
                        put("updatedTime", prog.updatedTime)
                    })
                }
            }
            writeZstdEntry(zip, "states/${book.bookKey}.json", json.encodeToString(JsonElement.serializer(), arr).toByteArray())
        }
    }

    private fun writeBookmarks(
        zip: ZipOutputStream,
        bookmarks: List<com.shuli.reader.core.database.entity.BookmarkEntity>,
        books: List<com.shuli.reader.core.database.entity.BookEntity>,
    ) {
        val bookMap = books.associateBy { it.id }
        val groups = bookmarks.groupBy { it.bookId }
        for ((bookId, bmList) in groups) {
            val book = bookMap[bookId] ?: continue
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
            writeZstdEntry(zip, "bookmarks/${book.bookKey}.json", json.encodeToString(JsonElement.serializer(), arr).toByteArray())
        }
    }

    private fun writeNotes(
        zip: ZipOutputStream,
        notes: List<com.shuli.reader.core.database.entity.NoteEntity>,
        books: List<com.shuli.reader.core.database.entity.BookEntity>,
    ) {
        val bookMap = books.associateBy { it.id }
        val groups = notes.groupBy { it.bookId }
        for ((bookId, noteList) in groups) {
            val book = bookMap[bookId] ?: continue
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
            writeZstdEntry(zip, "notes/${book.bookKey}.json", json.encodeToString(JsonElement.serializer(), arr).toByteArray())
        }
    }

    private fun writeConfig(zip: ZipOutputStream) {
        writeZstdEntry(zip, "config/settings.json", "{}".toByteArray())
    }

    /** 读取书籍文件内容（支持 content URI 和文件路径） */
    private fun readBookFile(filePath: String): ByteArray? {
        return try {
            if (filePath.startsWith("content://")) {
                val ctx = context ?: return null
                ctx.contentResolver.openInputStream(Uri.parse(filePath))?.use { it.readBytes() }
            } else {
                val file = File(filePath)
                if (file.exists() && file.canRead()) file.readBytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
