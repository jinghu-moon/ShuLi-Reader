package com.shuli.reader.feature.bookshelf

import com.shuli.reader.core.reading.ReadingStatus
import com.shuli.reader.feature.bookshelf.model.BookItem
import com.shuli.reader.feature.bookshelf.model.BookshelfNode
import com.shuli.reader.feature.bookshelf.model.FilterType
import com.shuli.reader.feature.bookshelf.model.FolderItem
import com.shuli.reader.feature.bookshelf.model.SortOrder

/**
 * 书架排序/过滤/槽位合并纯函数。
 *
 * 从 BookshelfViewModel 拆出，可独立单测，无 ViewModel 依赖。
 */
internal object BookshelfSorting {

    fun List<BookshelfNode>.applyFilter(filter: FilterType): List<BookshelfNode> {
        return when (filter) {
            FilterType.ALL -> filter {
                if (it is BookItem) it.readingStatus != ReadingStatus.ABANDONED else true
            }
            FilterType.WANT_TO_READ -> filter {
                if (it is BookItem) it.readingStatus == ReadingStatus.WANT_TO_READ else false
            }
            FilterType.READING -> filter {
                if (it is BookItem) it.readingStatus == ReadingStatus.READING else false
            }
            FilterType.PAUSED -> filter {
                if (it is BookItem) it.readingStatus == ReadingStatus.PAUSED else false
            }
            FilterType.FINISHED -> filter {
                if (it is BookItem) it.readingStatus == ReadingStatus.FINISHED else false
            }
            FilterType.ABANDONED -> filter {
                if (it is BookItem) it.readingStatus == ReadingStatus.ABANDONED else false
            }
            FilterType.FAVORITE -> filter {
                if (it is BookItem) it.isFavorite else false
            }
        }
    }

    fun List<BookshelfNode>.applySorting(order: SortOrder, isAscending: Boolean): List<BookshelfNode> {
        return when (order) {
            SortOrder.LAST_READ -> {
                val selector: (BookshelfNode) -> Long = { if (it is BookItem) it.lastReadTime ?: 0L else 0L }
                if (isAscending) sortedBy(selector) else sortedByDescending(selector)
            }
            SortOrder.ADD_TIME -> if (isAscending) sortedBy { it.id } else sortedByDescending { it.id }
            SortOrder.TITLE -> if (isAscending) sortedBy { it.title } else sortedByDescending { it.title }
            SortOrder.FILE_SIZE -> {
                val selector: (BookshelfNode) -> Long = { if (it is BookItem) it.readingDurationMinutes else 0L }
                if (isAscending) sortedBy(selector) else sortedByDescending(selector)
            }
            SortOrder.PROGRESS -> {
                val selector: (BookshelfNode) -> Float = { if (it is BookItem) it.readingProgress else 0f }
                if (isAscending) sortedBy(selector) else sortedByDescending(selector)
            }
            SortOrder.READING_STATUS -> {
                val selector: (BookshelfNode) -> Int = {
                    if (it is BookItem) readingStatusPriority(it.readingStatus) else Int.MAX_VALUE
                }
                if (isAscending) sortedByDescending(selector) else sortedBy(selector)
            }
            SortOrder.READ_COUNT -> {
                val selector: (BookshelfNode) -> Int = { if (it is BookItem) it.readCount else 0 }
                if (isAscending) sortedBy(selector) else sortedByDescending(selector)
            }
        }
    }

    private fun readingStatusPriority(status: ReadingStatus): Int = when (status) {
        ReadingStatus.READING -> 0
        ReadingStatus.PAUSED -> 1
        ReadingStatus.WANT_TO_READ -> 2
        ReadingStatus.FINISHED -> 3
        ReadingStatus.ABANDONED -> 4
    }

    /**
     * 槽位合并：固定项占据指定位置，自动项填充空隙。
     * 冲突处理：多个固定项指向同一槽位时，后者顺延到下一个空槽。
     */
    fun mergePinnedSlots(
        pinned: List<BookshelfNode>,
        auto: List<BookshelfNode>,
    ): List<BookshelfNode> {
        if (pinned.isEmpty()) return auto
        if (auto.isEmpty()) return pinned.sortedBy { it.pinnedSlot }

        val total = pinned.size + auto.size
        val result = ArrayList<BookshelfNode>(total)

        val resolvedPinned = mutableMapOf<Int, BookshelfNode>()
        pinned.sortedBy { it.pinnedSlot }.forEach { node ->
            var slot = node.pinnedSlot!!
            while (resolvedPinned.containsKey(slot)) { slot++ }
            resolvedPinned[slot] = node
        }

        val autoIterator = auto.iterator()
        for (i in 0 until total) {
            val pinnedNode = resolvedPinned[i]
            if (pinnedNode != null) {
                result.add(pinnedNode)
            } else if (autoIterator.hasNext()) {
                result.add(autoIterator.next())
            }
        }
        return result
    }
}
