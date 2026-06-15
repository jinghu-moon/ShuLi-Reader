// Part of T-30 ZIP 明文导出
package com.shuli.reader.sync.backup

import android.content.Context
import android.net.Uri
import com.github.luben.zstd.Zstd
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.sync.crypto.AesGcmCipher
import com.shuli.reader.sync.crypto.KeyDerivation
import com.shuli.reader.sync.crypto.KeyDerivationParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ZIP 导出器。
 *
 * 将书籍元数据、书签、笔记、阅读进度导出为标准 ZIP 文件。
 * 使用 ZSTD 压缩（级别 3），对文本数据有极高压缩率和速度。
 * 支持可选的 AES-256-GCM 加密。
 *
 * ZIP 结构：
 * - manifest.json: 导出元信息（版本、时间戳）
 * - books.json: 书籍列表
 * - books/{bookKey}.{ext}: 书籍文件（可选）
 * - states/{bookKey}.json: 阅读进度
 * - bookmarks/{bookKey}.json: 书签
 * - notes/{bookKey}.json: 笔记
 * - config/: 阅读器配置
 *
 * 文件格式：zstd(zip_data)
 * 加密文件格式：salt(16) + nonce(12) + encrypted(zstd(zip_data)) + tag(16)
 */
class ZipExporter(
    private val db: ExportDatabase,
    private val context: Context? = null,
) {
    private val json = Json { prettyPrint = true }

    /**
     * 导出数据到 ZIP 文件。
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

            // 创建 ZIP 内容（DEFLATE 最高压缩级别）
            val zipBytes = ByteArrayOutputStream()
            ZipOutputStream(zipBytes).use { zip ->
                // 内部 ZIP 使用 DEFLATE 最高压缩
                zip.setLevel(9)

                // manifest.json
                val manifest = buildJsonObject {
                    put("version", 1)
                    put("exportedAt", System.currentTimeMillis())
                    put("appVersion", "0.1.0")
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(json.encodeToString(manifest).toByteArray())
                zip.closeEntry()

                // books.json
                val booksArray = buildJsonArray {
                    for (book in books) {
                        addJsonObject {
                            put("bookKey", book.bookKey)
                            put("title", book.title)
                            put("author", book.author ?: "")
                            put("filePath", book.filePath)
                            put("fileType", book.fileType)
                            put("fileSize", book.fileSize)
                            put("addedTime", book.addedTime)
                            put("lastReadTime", book.lastReadTime ?: 0L)
                            put("readingProgress", book.readingProgress.toDouble())
                        }
                    }
                }
                zip.putNextEntry(ZipEntry("books.json"))
                zip.write(json.encodeToString(booksArray).toByteArray())
                zip.closeEntry()

                // books/{bookKey}.{ext} - 书籍文件
                if (options.includeBookFiles) {
                    for (book in books) {
                        try {
                            val fileData = readBookFile(book.filePath)
                            if (fileData != null) {
                                val ext = book.fileType.lowercase()
                                val entryName = "books/${book.bookKey}.$ext"
                                zip.putNextEntry(ZipEntry(entryName))
                                zip.write(fileData)
                                zip.closeEntry()
                            }
                        } catch (_: Exception) {
                            // 跳过无法读取的文件
                        }
                    }
                }

                // states/{bookKey}.json - 阅读进度
                if (options.includeProgress) {
                    for (prog in progress) {
                        val book = books.find { it.id == prog.bookId } ?: continue
                        val state = buildJsonObject {
                            put("bookKey", book.bookKey)
                            put("pageIndex", prog.pageIndex)
                            put("position", prog.position)
                            put("updatedTime", prog.updatedTime)
                        }
                        zip.putNextEntry(ZipEntry("states/${book.bookKey}.json"))
                        zip.write(json.encodeToString(state).toByteArray())
                        zip.closeEntry()
                    }
                }

                // bookmarks/{bookKey}.json
                if (options.includeBookmarks) {
                    val bmGroups: Map<Long, List<BookmarkEntity>> = bookmarks.groupBy { it.bookId }
                    for (entry in bmGroups.entries) {
                        val bookId = entry.key
                        val bmList = entry.value
                        val book = books.find { it.id == bookId } ?: continue
                        val arr = buildJsonArray {
                            for (bm in bmList) {
                                addJsonObject {
                                    put("byteOffset", bm.byteOffset)
                                    put("selectedText", bm.selectedText ?: "")
                                    put("createdTime", bm.createdTime)
                                }
                            }
                        }
                        zip.putNextEntry(ZipEntry("bookmarks/${book.bookKey}.json"))
                        zip.write(json.encodeToString(arr).toByteArray())
                        zip.closeEntry()
                    }
                }

                // notes/{bookKey}.json
                if (options.includeNotes) {
                    val noteGroups: Map<Long, List<NoteEntity>> = notes.groupBy { it.bookId }
                    for (entry in noteGroups.entries) {
                        val bookId = entry.key
                        val noteList = entry.value
                        val book = books.find { it.id == bookId } ?: continue
                        val arr = buildJsonArray {
                            for (note in noteList) {
                                addJsonObject {
                                    put("byteStart", note.byteStart)
                                    put("byteEnd", note.byteEnd)
                                    put("noteText", note.noteText)
                                    put("color", note.color ?: "")
                                    put("createdTime", note.createdTime)
                                }
                            }
                        }
                        zip.putNextEntry(ZipEntry("notes/${book.bookKey}.json"))
                        zip.write(json.encodeToString(arr).toByteArray())
                        zip.closeEntry()
                    }
                }

                // config/ - 阅读器配置（占位）
                if (options.includeConfig) {
                    zip.putNextEntry(ZipEntry("config/settings.json"))
                    zip.write("{}".toByteArray())
                    zip.closeEntry()
                }
            }

            val zipData = zipBytes.toByteArray()

            // ZSTD 压缩（级别 3，速度与压缩率平衡）
            val compressedData = Zstd.compress(zipData, 3)

            // 写入文件（可选加密）
            if (options.encryptionPassword != null) {
                // 加密模式：salt(16) + nonce(12) + encrypted(zstd(zip_data)) + tag(16)
                val salt = ByteArray(16)
                SecureRandom().nextBytes(salt)

                val keyDerivation = KeyDerivation(
                    KeyDerivationParams(
                        algorithm = "PBKDF2WithHmacSHA256",
                        iterations = 600_000,
                        keyLengthBits = 256,
                    ),
                )
                val key = keyDerivation.derive(options.encryptionPassword, salt)

                val cipher = AesGcmCipher()
                val encryptedData = cipher.encrypt(compressedData, key)

                FileOutputStream(outputFile).use { fos ->
                    fos.write(salt)
                    fos.write(encryptedData)
                }
            } else {
                // 明文模式：写入 ZSTD 压缩的 ZIP
                FileOutputStream(outputFile).use { fos ->
                    fos.write(compressedData)
                }
            }
        }

    /**
     * 读取书籍文件内容。
     * 支持文件路径和 content URI。
     */
    private fun readBookFile(filePath: String): ByteArray? {
        return try {
            if (filePath.startsWith("content://")) {
                // content URI 需要 Context
                val ctx = context ?: return null
                val uri = Uri.parse(filePath)
                ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } else {
                // 普通文件路径
                val file = File(filePath)
                if (file.exists() && file.canRead()) {
                    file.readBytes()
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
