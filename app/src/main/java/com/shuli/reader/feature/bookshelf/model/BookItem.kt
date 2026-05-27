package com.shuli.reader.feature.bookshelf.model

import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.BookShelfRow

enum class ViewMode { GRID, LIST, COMPACT_LIST }
enum class SortOrder { LAST_READ, ADD_TIME, TITLE, FILE_SIZE, PROGRESS }
enum class FilterType { ALL, FINISHED, FAVORITE }
enum class FileType { TXT, EPUB }

sealed interface BookshelfNode {
    val id: Long
    val title: String
    val pinnedSlot: Int?
}

data class FolderItem(
    override val id: Long,
    override val title: String,
    override val pinnedSlot: Int?,
    val books: List<BookItem>
) : BookshelfNode

data class BookItem(
    override val id: Long,
    override val title: String,
    override val pinnedSlot: Int?,
    val folderId: Long?,
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
    val customCoverPaletteIndex: Int? = null,
) : BookshelfNode

data class BookshelfUiState(
    val nodes: List<BookshelfNode> = emptyList(),
    val viewMode: ViewMode = ViewMode.GRID,
    val sortOrder: SortOrder = SortOrder.LAST_READ,
    val isAscending: Boolean = false,
    val filterType: FilterType = FilterType.ALL,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val todayReadingTime: String = "0m",
    val todayReadingMinutes: Long = 0L,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
    val isEditMode: Boolean = false,
    val selectedNodeIds: Set<Long> = emptySet(),
    /** 全局统一封面色盘索引；null/-1 表示走自动散列（每本独立色盘）。 */
    val unifiedCoverPaletteIndex: Int? = null,
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
    return toBookItem(
        id = id,
        author = author,
        coverUrl = coverPath,
        filePath = filePath,
        rawFileType = fileType,
        fileSize = fileSize,
        readingProgress = readingProgress,
        readingDurationMinutes = readingDurationMinutes,
        lastReadTime = lastReadTime,
        isFavorite = isFavorite,
        customCoverPaletteIndex = customCoverPaletteIndex,
        folderId = folderId,
        pinnedSlot = pinnedSlot,
    )
}

fun BookShelfRow.toBookItem(readingDurationMinutes: Long = 0): BookItem {
    return toBookItem(
        id = id,
        author = author,
        coverUrl = coverPath,
        filePath = filePath,
        rawFileType = fileType,
        fileSize = fileSize,
        readingProgress = readingProgress,
        readingDurationMinutes = readingDurationMinutes,
        lastReadTime = lastReadTime,
        isFavorite = isFavorite,
        customCoverPaletteIndex = customCoverPaletteIndex,
        folderId = folderId,
        pinnedSlot = pinnedSlot,
    )
}

private fun toBookItem(
    id: Long,
    author: String?,
    coverUrl: String?,
    filePath: String,
    rawFileType: String,
    fileSize: Long,
    readingProgress: Float,
    readingDurationMinutes: Long,
    lastReadTime: Long?,
    isFavorite: Boolean,
    customCoverPaletteIndex: Int?,
    folderId: Long?,
    pinnedSlot: Int?,
): BookItem {
    val type = when {
        rawFileType.equals("EPUB", ignoreCase = true) -> FileType.EPUB
        rawFileType.equals("TXT", ignoreCase = true) -> FileType.TXT
        filePath.endsWith(".epub", ignoreCase = true) -> FileType.EPUB
        filePath.endsWith(".txt", ignoreCase = true) -> FileType.TXT
        else -> FileType.TXT
    }
    val displayTitle = java.io.File(filePath).nameWithoutExtension
    return BookItem(
        id = id,
        title = displayTitle,
        author = author,
        coverUrl = coverUrl,
        filePath = filePath,
        fileType = type,
        fileSize = fileSize.toFormattedFileSize(),
        readingProgress = readingProgress,
        readingDuration = readingDurationMinutes.toReadableDuration(),
        readingDurationMinutes = readingDurationMinutes,
        lastReadTime = lastReadTime,
        isFavorite = isFavorite,
        customCoverPaletteIndex = customCoverPaletteIndex,
        folderId = folderId,
        pinnedSlot = pinnedSlot,
    )
}
