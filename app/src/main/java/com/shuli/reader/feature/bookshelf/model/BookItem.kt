package com.shuli.reader.feature.bookshelf.model

import com.shuli.reader.core.database.entity.BookEntity

enum class ViewMode { GRID, LIST }
enum class SortOrder { LAST_READ, ADD_TIME, TITLE, FILE_SIZE, PROGRESS }
enum class FilterType { ALL, RECENT, FINISHED, FAVORITE }
enum class FileType { TXT, EPUB }

data class BookItem(
    val id: Long,
    val title: String,
    val author: String?,
    val coverUrl: String?,
    val filePath: String,
    val fileType: FileType,
    val fileSize: String,
    val readingProgress: Float,
    val readingDuration: String,
    val readingDurationMinutes: Long,
    val lastReadTime: Long?,
    val isFavorite: Boolean,
    val isRecent: Boolean,
)

data class BookshelfUiState(
    val books: List<BookItem> = emptyList(),
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOrder: SortOrder = SortOrder.LAST_READ,
    val isAscending: Boolean = false,
    val filterType: FilterType = FilterType.ALL,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val todayReadingTime: String = "0m",
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
)

fun Long.toReadableDuration(): String {
    if (this <= 0) return ""
    val hours = this / 60
    val minutes = this % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

fun Long.toFormattedFileSize(): String {
    return when {
        this < 1024 -> "${this}B"
        this < 1024 * 1024 -> "${this / 1024}KB"
        else -> String.format("%.1fMB", this / 1024.0 / 1024.0)
    }
}

fun BookEntity.toBookItem(readingDurationMinutes: Long = 0): BookItem {
    val fileType = when {
        filePath.endsWith(".txt", ignoreCase = true) -> FileType.TXT
        filePath.endsWith(".epub", ignoreCase = true) -> FileType.EPUB
        else -> FileType.TXT
    }
    // 使用文件名作为标题（去掉扩展名）
    val displayTitle = java.io.File(filePath).nameWithoutExtension
    return BookItem(
        id = id,
        title = displayTitle,
        author = author,
        coverUrl = coverPath,
        filePath = filePath,
        fileType = fileType,
        fileSize = fileSize.toFormattedFileSize(),
        readingProgress = readingProgress,
        readingDuration = readingDurationMinutes.toReadableDuration(),
        readingDurationMinutes = readingDurationMinutes,
        lastReadTime = lastReadTime,
        isFavorite = false,
        isRecent = lastReadTime != null && (System.currentTimeMillis() - lastReadTime) < 7 * 24 * 60 * 60 * 1000,
    )
}
