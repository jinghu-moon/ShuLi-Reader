package com.shuli.reader.feature.bookshelf.export

import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.ReadingHistoryEntity
import com.shuli.reader.core.database.entity.TagEntity
import com.shuli.reader.core.reading.ReadingStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BookDataExporter {

    fun exportMarkdown(
        books: List<BookEntity>,
        tagsByBook: Map<Long, List<TagEntity>>,
        historyByBook: Map<Long, List<ReadingHistoryEntity>>,
        outputFile: File,
    ) {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        sb.appendLine("# ${dateFormat.format(Date())}")
        sb.appendLine()
        sb.appendLine("## Summary")
        sb.appendLine()
        sb.appendLine("- Total books: ${books.size}")
        sb.appendLine("- Reading: ${books.count { ReadingStatus.fromDb(it.readingStatus) == ReadingStatus.READING }}")
        sb.appendLine("- Finished: ${books.count { ReadingStatus.fromDb(it.readingStatus) == ReadingStatus.FINISHED }}")
        sb.appendLine("- Reread: ${books.count { it.readCount > 1 }}")
        sb.appendLine()

        sb.appendLine("## Books")
        sb.appendLine()

        for (book in books.sortedByDescending { it.lastReadTime ?: 0L }) {
            val status = ReadingStatus.fromDb(book.readingStatus)
            sb.appendLine("### ${book.title}")
            sb.appendLine()
            if (book.author != null) sb.appendLine("- **Author**: ${book.author}")
            sb.appendLine("- **Status**: ${status.name}")
            sb.appendLine("- **Read count**: ${book.readCount}")
            sb.appendLine("- **Progress**: ${(book.readingProgress * 100).toInt()}%")

            val tags = tagsByBook[book.id]
            if (!tags.isNullOrEmpty()) {
                sb.appendLine("- **Tags**: ${tags.joinToString(", ") { "#${it.name}" }}")
            }

            val history = historyByBook[book.id]
            if (!history.isNullOrEmpty()) {
                sb.appendLine("- **Reading history**:")
                for (entry in history) {
                    sb.appendLine("  - Read #${entry.readCount}: finished ${dateFormat.format(Date(entry.finishedAt))}")
                }
            }

            sb.appendLine()
        }

        outputFile.writeText(sb.toString())
    }

    fun exportCsv(
        books: List<BookEntity>,
        tagsByBook: Map<Long, List<TagEntity>>,
        outputFile: File,
    ) {
        val sb = StringBuilder()

        sb.appendLine("Title,Author,Status,Read Count,Progress,File Type,File Size,Tags")

        for (book in books.sortedByDescending { it.lastReadTime ?: 0L }) {
            val status = ReadingStatus.fromDb(book.readingStatus)
            val tags = tagsByBook[book.id]?.joinToString(";") { it.name } ?: ""

            sb.appendLine(
                buildString {
                    append(csvEscape(book.title))
                    append(",")
                    append(csvEscape(book.author ?: ""))
                    append(",")
                    append(status.name)
                    append(",")
                    append(book.readCount)
                    append(",")
                    append("${(book.readingProgress * 100).toInt()}%")
                    append(",")
                    append(book.fileType)
                    append(",")
                    append(book.fileSize)
                    append(",")
                    append(csvEscape(tags))
                },
            )
        }

        outputFile.writeText(sb.toString())
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
