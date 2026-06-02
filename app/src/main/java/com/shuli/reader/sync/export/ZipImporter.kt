// Part of T-31 ZIP 导入
package com.shuli.reader.sync.export

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdInputStream
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.sync.crypto.AesGcmCipher
import com.shuli.reader.sync.crypto.KeyDerivation
import com.shuli.reader.sync.crypto.KeyDerivationParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * ZIP 导入器。
 *
 * 从标准 ZIP 文件导入书签等数据。
 * 支持 ZSTD 压缩格式（整个文件 ZSTD 压缩）和传统未压缩 ZIP 格式。
 * 向后兼容旧版每条目 ZSTD 压缩格式（.json.zst）。
 * 支持三种合并策略：覆盖、智能合并、仅导入新条目。
 */
class ZipImporter(
    private val db: ImportDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** ZSTD 是否可用（运行时检测） */
    private val zstdAvailable: Boolean by lazy {
        try {
            Class.forName("com.github.luben.zstd.Zstd")
            com.github.luben.zstd.Zstd.decompress(ByteArray(0), ByteArray(0))
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 从 ZIP 文件导入数据。
     *
     * @param zipFile ZIP 文件
     * @param strategy 合并策略
     * @param password 解密密码（null = 明文导入）
     */
    suspend fun import(zipFile: File, strategy: ImportStrategy, password: String? = null) =
        withContext(Dispatchers.IO) {
            val entries = if (password != null) {
                readEncryptedZipEntries(zipFile, password)
            } else {
                readZipEntries(zipFile)
            }

            // 解析 manifest（验证格式）- 支持 .json.zst 和 .json 两种格式
            val manifestData = entries["manifest.json.zst"] ?: entries["manifest.json"]
            manifestData
                ?.let { json.parseToJsonElement(it).jsonObject }
                ?: throw IllegalArgumentException("ZIP 文件缺少 manifest.json")

            // 解析 bookmarks - 支持 .json.zst 和 .json 两种格式
            val zipBookmarks = mutableListOf<BookmarkEntity>()
            for ((key, value) in entries) {
                if (key.startsWith("bookmarks/") && (key.endsWith(".json.zst") || key.endsWith(".json"))) {
                    val arr = json.parseToJsonElement(value).jsonArray
                    for (element in arr) {
                        val obj = element.jsonObject
                        zipBookmarks.add(
                            BookmarkEntity(
                                bookId = 0,
                                byteOffset = obj["byteOffset"]!!.jsonPrimitive.long,
                                selectedText = obj["selectedText"]?.jsonPrimitive?.content?.ifBlank { null },
                                createdTime = obj["createdTime"]!!.jsonPrimitive.long,
                                updatedAt = obj["updatedAt"]?.jsonPrimitive?.long ?: 0L,
                            ),
                        )
                    }
                }
            }

            // 获取本地数据
            val localBookmarks = db.getAllBookmarks().toMutableList()

            // 应用合并策略
            val mergedBookmarks = applyStrategy(strategy, localBookmarks, zipBookmarks)

            // 写入合并结果
            db.clearBookmarks()
            for (bookmark in mergedBookmarks) {
                db.upsertBookmark(bookmark)
            }
        }

    /**
     * 读取 ZIP 文件所有条目为 Map<path, content>。
     * 自动检测 ZSTD 压缩并解压整个文件，然后解析 ZIP。
     * 向后兼容未压缩的 ZIP 文件。
     */
    private fun readZipEntries(zipFile: File): Map<String, String> {
        val fileBytes = zipFile.readBytes()

        // 尝试 ZSTD 解压整个文件
        val zipBytes = tryDecompressZstd(fileBytes)

        // 解析 ZIP
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val data = zip.readBytes()
                    entries[entry.name] = decompressData(data, entry.name)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    /**
     * 尝试 ZSTD 解压。如果不是 ZSTD 格式，返回原始数据。
     */
    private fun tryDecompressZstd(data: ByteArray): ByteArray {
        return try {
            val decompressedSize = Zstd.decompressedSize(data)
            if (decompressedSize > 0) {
                Zstd.decompress(data, decompressedSize.toInt())
            } else {
                // 无内容大小信息，使用流式解压
                ZstdInputStream(ByteArrayInputStream(data)).use { zis ->
                    zis.readBytes()
                }
            }
        } catch (_: Exception) {
            // 不是 ZSTD 格式，返回原始数据（向后兼容）
            data
        }
    }

    /**
     * 读取加密 ZIP 文件所有条目为 Map<path, content>。
     *
     * 文件格式：salt(16) + nonce(12) + encrypted(zip_data) + tag(16)
     */
    private fun readEncryptedZipEntries(zipFile: File, password: String): Map<String, String> {
        val fileBytes = zipFile.readBytes()
        require(fileBytes.size > 16 + 12 + 16) { "加密文件格式无效" }

        // 提取 salt
        val salt = fileBytes.copyOfRange(0, 16)
        val encryptedData = fileBytes.copyOfRange(16, fileBytes.size)

        // 派生密钥
        val keyDerivation = KeyDerivation(
            KeyDerivationParams(
                algorithm = "PBKDF2WithHmacSHA256",
                iterations = 600_000,
                keyLengthBits = 256,
            ),
        )
        val key = keyDerivation.derive(password, salt)

        // 解密
        val cipher = AesGcmCipher()
        val decryptedBytes = try {
            cipher.decrypt(encryptedData, key)
        } catch (e: Exception) {
            throw IllegalArgumentException("解密失败：密码错误或文件已损坏", e)
        }

        // ZSTD 解压（加密格式：zstd(zip_data)）
        val zipBytes = tryDecompressZstd(decryptedBytes)

        // 解析 ZIP
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val data = zip.readBytes()
                    entries[entry.name] = decompressData(data, entry.name)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    /**
     * 解压数据（支持 ZSTD 和原始格式）。
     */
    private fun decompressData(data: ByteArray, entryName: String): String {
        if (!entryName.endsWith(".zst") || !zstdAvailable) {
            return String(data)
        }
        return try {
            val inputStream = com.github.luben.zstd.ZstdInputStream(ByteArrayInputStream(data))
            val outputStream = ByteArrayOutputStream()
            inputStream.use { zstd ->
                zstd.copyTo(outputStream)
            }
            outputStream.toByteArray().decodeToString()
        } catch (_: Throwable) {
            // ZSTD 解压失败，尝试直接读取
            String(data)
        }
    }

    /**
     * 应用导入策略，返回合并后的书签列表。
     */
    private fun applyStrategy(
        strategy: ImportStrategy,
        localBookmarks: List<BookmarkEntity>,
        zipBookmarks: List<BookmarkEntity>,
    ): List<BookmarkEntity> {
        return when (strategy) {
            ImportStrategy.OVERWRITE -> {
                // 覆盖：ZIP 数据完全替换本地数据
                zipBookmarks
            }
            ImportStrategy.MERGE -> {
                // 智能合并：按 updatedAt 时间戳，保留较新的条目
                val merged = localBookmarks.toMutableList()
                for (zipBm in zipBookmarks) {
                    val existing = merged.find { it.id == zipBm.id }
                    if (existing == null) {
                        merged.add(zipBm)
                    } else if ((zipBm.updatedAt ?: 0L) > (existing.updatedAt ?: 0L)) {
                        merged.remove(existing)
                        merged.add(zipBm)
                    }
                    // 否则保留本地条目
                }
                merged
            }
        }
    }
}
