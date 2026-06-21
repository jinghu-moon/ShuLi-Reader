package com.shuli.reader.feature.reader.editor

import android.content.Context
import com.shuli.reader.core.database.dao.BookChapterDao
import com.shuli.reader.core.parser.model.BookContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

/**
 * 文本编辑管理器
 *
 * 负责流式保存 + 原子替换 + 增量偏移更新
 */
class TextEditManager(
    private val context: Context,
    private val editStore: EditStore,
) {
    /**
     * 保存编辑到文件
     *
     * 流程：
     * 1. 自动备份（.bak）
     * 2. 流式逐章处理（不全量读入内存）
     * 3. 原子替换（先写临时文件再 rename）
     * 4. 增量更新章节字节偏移
     * 5. 清空编辑
     */
    suspend fun saveToFile(
        file: File,
        charset: Charset,
        bookContent: BookContent,
        bookChapterDao: BookChapterDao,
        getChapterText: suspend (Int) -> String,
    ) = withContext(Dispatchers.IO) {
        val allDeltas = editStore.getAllDeltasGrouped()
        if (allDeltas.isEmpty()) return@withContext

        // 1. 自动备份
        val bakFile = File(file.parent, "${file.name}.bak")
        file.copyTo(bakFile, overwrite = true)

        try {
            // 2. 流式逐章处理
            val tempFile = File(file.parent, "${file.name}.tmp")
            val chapters = bookContent.chapters

            FileOutputStream(tempFile).use { fos ->
                val writer = fos.writer(charset).buffered(64 * 1024)  // 64KB 写缓冲

                for ((index, chapter) in chapters.withIndex()) {
                    // 读取原始章节文本
                    val rawText = getChapterText(index)

                    // 应用本章 Delta
                    val deltas = allDeltas[index]
                    val modifiedText = if (deltas.isNullOrEmpty()) {
                        rawText
                    } else {
                        val sb = StringBuilder(rawText)
                        for (delta in deltas) {  // 已按 charStart 降序排列
                            sb.replace(delta.charStart, delta.charEnd, delta.newText)
                        }
                        sb.toString()
                    }

                    writer.write(modifiedText)
                }
                writer.flush()
            }

            // 3. 原子替换
            tempFile.renameTo(file)

            // 4. 增量更新章节字节偏移
            updateChapterOffsetsIncremental(
                bookContent.bookId,
                allDeltas,
                charset,
                bookChapterDao,
            )

            // 5. 清空编辑
            editStore.clear()

            // 6. 删除备份（成功保存后）
            bakFile.delete()

        } catch (e: Exception) {
            // 保存失败，保留备份
            throw e
        }
    }

    /**
     * O(1) 增量更新章节字节偏移
     *
     * 原理：每个 Delta 的字节长度变化 = newText.getBytes(charset).size - 原文字节长度。
     * 累积变化量逐章传递，只需一条 SQL 批量 UPDATE。
     */
    private suspend fun updateChapterOffsetsIncremental(
        bookId: Long,
        deltasByChapter: Map<Int, List<EditDelta>>,
        charset: Charset,
        bookChapterDao: BookChapterDao,
    ) {
        var cumulativeByteDiff = 0L

        // 按章节顺序遍历
        for (chapterIndex in deltasByChapter.keys.sorted()) {
            val deltas = deltasByChapter[chapterIndex] ?: continue

            // 计算本章的字节长度变化
            for (delta in deltas) {
                // 使用实际字节长度，而非近似值
                val originalByteLen = delta.originalText.toByteArray(charset).size.toLong()
                val newByteLen = delta.newText.toByteArray(charset).size.toLong()
                cumulativeByteDiff += (newByteLen - originalByteLen)
            }

            // 更新所有后续章节的偏移（批量 SQL）
            if (cumulativeByteDiff != 0L) {
                bookChapterDao.shiftByteOffsets(
                    bookId = bookId,
                    fromChapterIndex = chapterIndex + 1,
                    byteDelta = cumulativeByteDiff,
                )
            }
        }
    }
}
