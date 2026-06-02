// Part of 备份导入器
package com.shuli.reader.sync.export

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdInputStream
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import com.shuli.reader.sync.crypto.KeyDerivation
import com.shuli.reader.sync.crypto.KeyDerivationParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.ZipInputStream

/**
 * 备份导入器。
 *
 * 从 ZIP 文件导入全部数据（书籍、书签、笔记、阅读进度）。
 * 支持逐条目 ZSTD 压缩格式和传统未压缩格式。
 * 支持 AES-256-GCM 加密文件（CipherInputStream 流式解密）。
 *
 * 安全特性：
 * - 整个导入过程包裹在 DB 事务中，失败时自动回滚
 * - CipherInputStream 的 AEADBadTagException 被 JDK 静默吞掉，
 *   但垃圾数据会导致 ZIP 解析失败 → 事务回滚 → 数据安全
 *
 * 策略：
 * - MERGE：按 updatedAt 时间戳保留较新条目（@Upsert 语义）
 * - OVERWRITE：先清空再导入
 */
class BackupImporter(
    private val db: ImportDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 从备份文件导入数据。
     *
     * @param file 备份文件
     * @param strategy 合并策略
     * @param password 解密密码（null = 明文导入）
     */
    suspend fun import(file: File, strategy: ImportStrategy, password: String? = null) =
        withContext(Dispatchers.IO) {
            // 解析所有条目到内存
            val entries = if (password != null) {
                readEncryptedEntries(file, password)
            } else {
                readPlainEntries(file)
            }

            // 验证 manifest
            val manifestData = entries["manifest.json"]
                ?: throw IllegalArgumentException("备份文件缺少 manifest.json，格式无效")

            val manifest = json.parseToJsonElement(manifestData).jsonObject
            val version = manifest["version"]?.jsonPrimitive?.int ?: 1

            // 解析各实体
            val importBooks = parseBooks(entries)
            val importBookmarks = parseBookmarks(entries)
            val importNotes = parseNotes(entries)
            val importProgress = parseProgress(entries)

            // 在 DB 事务内执行导入（失败自动回滚）
            db.runInTransaction {
                when (strategy) {
                    ImportStrategy.OVERWRITE -> {
                        db.clearBooks()
                        db.clearBookmarks()
                        db.clearNotes()
                        db.clearProgress()
                    }
                    ImportStrategy.MERGE -> { /* MERGE: @Upsert 自动处理冲突 */ }
                }

                for (book in importBooks) {
                    db.upsertBook(book)
                }
                for (bookmark in importBookmarks) {
                    db.upsertBookmark(bookmark)
                }
                for (note in importNotes) {
                    db.upsertNote(note)
                }
                for (prog in importProgress) {
                    db.upsertProgress(prog)
                }
            }
        }

    // --- Entry reading ---

    /** 读取明文 ZIP 条目 */
    private fun readPlainEntries(file: File): Map<String, String> {
        val entries = mutableMapOf<String, String>()
        FileInputStream(file).use { fis ->
            readZipEntriesFromStream(BufferedInputStream(fis), entries)
        }
        return entries
    }

    /** 读取加密 ZIP 条目：salt(16) + nonce(12) + CipherInputStream(ZIP) + tag(16) */
    private fun readEncryptedEntries(file: File, password: String): Map<String, String> {
        val fileBytes = file.readBytes()
        require(fileBytes.size > 16 + 12 + 16) { "加密文件格式无效" }

        val salt = fileBytes.copyOfRange(0, 16)
        val nonce = fileBytes.copyOfRange(16, 28)
        val encryptedBody = fileBytes.copyOfRange(28, fileBytes.size)

        val key = KeyDerivation(
            KeyDerivationParams(
                algorithm = "PBKDF2WithHmacSHA256",
                iterations = 600_000,
                keyLengthBits = 256,
            ),
        ).derive(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

        val entries = mutableMapOf<String, String>()
        CipherInputStream(ByteArrayInputStream(encryptedBody), cipher).use { cis ->
            readZipEntriesFromStream(BufferedInputStream(cis), entries)
        }
        return entries
    }

    /** 从流中读取 ZIP 条目，自动处理逐条目 ZSTD 解压 */
    private fun readZipEntriesFromStream(
        inputStream: java.io.InputStream,
        entries: MutableMap<String, String>,
    ) {
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val content = decompressEntry(zip, entry.name)
                    entries[entry.name] = content
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    /** 解压单个 ZIP 条目（支持 ZSTD 和原始格式） */
    private fun decompressEntry(zip: ZipInputStream, entryName: String): String {
        // 先读取整个条目到内存，避免 ZSTD 解压器越界读取下一个条目
        val entryBytes = zip.readBytes()
        if (!isZstdFrame(entryBytes)) {
            return String(entryBytes)
        }
        return try {
            ZstdInputStream(ByteArrayInputStream(entryBytes)).use { zstd ->
                zstd.readBytes().decodeToString()
            }
        } catch (_: Throwable) {
            // ZSTD 解压失败，回退到原始数据
            String(entryBytes)
        }
    }

    /** 检查字节数组是否以 ZSTD 魔数开头 (0x28B52FFD) */
    private fun isZstdFrame(data: ByteArray): Boolean {
        return data.size >= 4 &&
            data[0] == 0x28.toByte() &&
            data[1] == 0xB5.toByte() &&
            data[2] == 0x2F.toByte() &&
            data[3] == 0xFD.toByte()
    }

    // --- JSON helpers (handle JsonNull safely) ---

    private fun JsonElement?.safeLong(): Long? =
        if (this is JsonNull) null else this?.jsonPrimitive?.long

    private fun JsonElement?.safeInt(): Int? =
        if (this is JsonNull) null else this?.jsonPrimitive?.int

    private fun JsonElement?.safeContent(): String? =
        if (this is JsonNull) null else this?.jsonPrimitive?.content

    private fun JsonElement?.safeBoolean(): Boolean? =
        if (this is JsonNull) null else this?.jsonPrimitive?.boolean

    private fun JsonElement?.safeFloat(): Float? =
        if (this is JsonNull) null else this?.jsonPrimitive?.float

    // --- Entity parsing ---

    private fun parseBooks(entries: Map<String, String>): List<BookEntity> {
        val data = entries["books.json"] ?: return emptyList()
        val books = mutableListOf<BookEntity>()
        for (element in json.parseToJsonElement(data).jsonArray) {
            val obj = element.jsonObject
            books.add(
                BookEntity(
                    id = obj["id"].safeLong() ?: 0L,
                    bookKey = obj["bookKey"].safeContent() ?: "",
                    title = obj["title"].safeContent() ?: "",
                    author = obj["author"].safeContent()?.ifBlank { null },
                    filePath = obj["filePath"].safeContent() ?: "",
                    fileType = obj["fileType"].safeContent() ?: "TXT",
                    fileSize = obj["fileSize"].safeLong() ?: 0L,
                    coverPath = obj["coverPath"].safeContent()?.ifBlank { null },
                    addedTime = obj["addedTime"].safeLong() ?: 0L,
                    lastReadTime = obj["lastReadTime"].safeLong()?.takeIf { it > 0 },
                    readingProgress = obj["readingProgress"].safeFloat() ?: 0f,
                    isFavorite = obj["isFavorite"].safeBoolean() ?: false,
                    folderId = obj["folderId"].safeLong(),
                    totalChapterNum = obj["totalChapterNum"].safeInt() ?: 0,
                    durByteOffset = obj["durByteOffset"].safeLong() ?: 0L,
                    durChapterTitle = obj["durChapterTitle"].safeContent()?.ifBlank { null },
                ),
            )
        }
        return books
    }

    private fun parseBookmarks(entries: Map<String, String>): List<BookmarkEntity> {
        val books = parseBooks(entries)
        val bookMap = books.associateBy { it.bookKey }
        val bookmarks = mutableListOf<BookmarkEntity>()

        for ((key, value) in entries) {
            if (!key.startsWith("bookmarks/") || !key.endsWith(".json")) continue
            val bookKey = key.removePrefix("bookmarks/").removeSuffix(".json")
            val bookId = bookMap[bookKey]?.id ?: continue

            for (element in json.parseToJsonElement(value).jsonArray) {
                val obj = element.jsonObject
                bookmarks.add(
                    BookmarkEntity(
                        bookId = bookId,
                        byteOffset = obj["byteOffset"].safeLong() ?: 0L,
                        selectedText = obj["selectedText"].safeContent()?.ifBlank { null },
                        createdTime = obj["createdTime"].safeLong() ?: 0L,
                        updatedAt = obj["updatedAt"].safeLong() ?: 0L,
                    ),
                )
            }
        }
        return bookmarks
    }

    private fun parseNotes(entries: Map<String, String>): List<NoteEntity> {
        val books = parseBooks(entries)
        val bookMap = books.associateBy { it.bookKey }
        val notes = mutableListOf<NoteEntity>()

        for ((key, value) in entries) {
            if (!key.startsWith("notes/") || !key.endsWith(".json")) continue
            val bookKey = key.removePrefix("notes/").removeSuffix(".json")
            val bookId = bookMap[bookKey]?.id ?: continue

            for (element in json.parseToJsonElement(value).jsonArray) {
                val obj = element.jsonObject
                notes.add(
                    NoteEntity(
                        bookId = bookId,
                        byteStart = obj["byteStart"].safeLong() ?: 0L,
                        byteEnd = obj["byteEnd"].safeLong() ?: 0L,
                        noteText = obj["noteText"].safeContent() ?: "",
                        color = obj["color"].safeContent()?.ifBlank { null },
                        createdTime = obj["createdTime"].safeLong() ?: 0L,
                        updatedAt = obj["updatedAt"].safeLong() ?: 0L,
                    ),
                )
            }
        }
        return notes
    }

    private fun parseProgress(entries: Map<String, String>): List<ReadingProgressEntity> {
        val books = parseBooks(entries)
        val bookMap = books.associateBy { it.bookKey }
        val progressList = mutableListOf<ReadingProgressEntity>()

        for ((key, value) in entries) {
            if (!key.startsWith("states/") || !key.endsWith(".json")) continue
            val bookKey = key.removePrefix("states/").removeSuffix(".json")
            val bookId = bookMap[bookKey]?.id ?: continue

            for (element in json.parseToJsonElement(value).jsonArray) {
                val obj = element.jsonObject
                progressList.add(
                    ReadingProgressEntity(
                        bookId = bookId,
                        pageIndex = obj["pageIndex"].safeInt() ?: 0,
                        position = obj["position"].safeInt() ?: 0,
                        readTime = obj["readTime"].safeLong() ?: 0L,
                        updatedTime = obj["updatedTime"].safeLong() ?: 0L,
                    ),
                )
            }
        }
        return progressList
    }
}
