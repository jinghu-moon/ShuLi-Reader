package com.shuli.reader.feature.settings.dictionary

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.shuli.reader.core.database.dao.WordBookDao
import com.shuli.reader.core.database.entity.WordBookEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Anki 导出器
 *
 * 将生词本导出为 TSV 文件，可导入到 Anki
 */
class AnkiExporter(
    private val context: Context,
    private val wordBookDao: WordBookDao,
) {
    /**
     * 导出生词本为 TSV 格式
     *
     * @param uri SAF 选择的输出文件 URI
     * @return 导出的单词数量
     */
    suspend fun exportToAnki(uri: Uri): Int = withContext(Dispatchers.IO) {
        val words = wordBookDao.getUnexported()
        if (words.isEmpty()) return@withContext 0

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            writeTsv(outputStream, words)
        }

        // 标记为已导出
        wordBookDao.markExported(words.map { it.id })

        words.size
    }

    /**
     * 生成导出文件名
     */
    fun generateFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "shuli_vocabulary_$timestamp.tsv"
    }

    /**
     * 写入 TSV 格式数据
     *
     * 格式：单词\t释义\t上下文\t添加时间
     */
    private fun writeTsv(output: OutputStream, words: List<WordBookEntity>) {
        val writer = output.bufferedWriter()

        // 写入表头（Anki 默认忽略第一行，但保留以便其他软件使用）
        writer.write("word\tdefinition\tcontext\tadded_at\n")

        // 写入数据
        words.forEach { word ->
            val line = buildString {
                append(escapeTsv(word.word))
                append("\t")
                append(escapeTsv(word.definition))
                append("\t")
                append(escapeTsv(word.contextSentence))
                append("\t")
                append(word.addedAt)
            }
            writer.write(line)
            writer.newLine()
        }

        writer.flush()
    }

    /**
     * 转义 TSV 特殊字符
     */
    private fun escapeTsv(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\t", "\\t")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
