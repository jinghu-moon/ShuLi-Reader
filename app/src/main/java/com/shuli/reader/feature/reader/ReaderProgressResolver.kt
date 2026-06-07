package com.shuli.reader.feature.reader

import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotResolver
import com.shuli.reader.core.reader.SlotResolution
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 阅读器进度解析：全书进度估算 + 页眉页脚槽位解析。
 *
 * 从 ReaderViewModel 拆出，SRP —— 只负责"进度计算与槽位渲染数据"这一变更轴。
 */
internal class ReaderProgressResolver(
    private val uiState: MutableStateFlow<ReaderUiState>,
) {

    /**
     * 统一解析页眉和页脚槽位为 SlotResolution，避免重复计算进度。
     */
    fun resolveHeaderAndFooterSlots(): Pair<SlotResolution, SlotResolution> {
        val state = uiState.value
        val prefs = state.readerPreferences
        val (currentPos, totalPos, bookProgressPercent) = computeSynchronousBookProgress()

        val header = if (prefs.header.visibility == HeaderVisibility.ALWAYS_HIDE) {
            SlotResolution()
        } else {
            SlotResolver.resolveHeader(
                config = prefs.header,
                chapterTitle = state.chapterTitle,
                bookTitle = state.bookTitle,
                pageNumber = state.pageIndex + 1,
                totalPages = state.totalPages.coerceAtLeast(1),
                bookProgressPercent = bookProgressPercent,
                bookCurrentPosition = currentPos,
                bookTotalPosition = totalPos,
                batteryLevel = 100,
            )
        }

        val footer = if (prefs.footer.visibility == HeaderVisibility.ALWAYS_HIDE) {
            SlotResolution()
        } else {
            SlotResolver.resolveFooter(
                config = prefs.footer,
                chapterTitle = state.chapterTitle,
                bookTitle = state.bookTitle,
                pageNumber = state.pageIndex + 1,
                totalPages = state.totalPages.coerceAtLeast(1),
                bookProgressPercent = bookProgressPercent,
                bookCurrentPosition = currentPos,
                bookTotalPosition = totalPos,
                batteryLevel = 100,
            )
        }

        return Pair(header, footer)
    }

    /**
     * 基于已分页章节数据，估算当前页在全书中的位置和进度。
     * @return Triple(当前页在全书中序号, 全书总页数, 进度百分比 0f..1f)
     */
    fun computeSynchronousBookProgress(): Triple<Long, Long, Float> {
        val state = uiState.value
        val wordCounts = state.chapterWordCounts
        val pageCounts = state.chapterPageCounts
        val currentChapterIndex = state.chapterIndex
        val currentPages = state.totalPages.coerceAtLeast(1)
        val totalChapters = state.totalChapters.coerceAtLeast(1)

        // 加权平均：基于所有已分页章节的真实数据计算 wordsPerPage
        var sampledWords = 0L
        var sampledPages = 0L
        for ((i, p) in pageCounts) {
            if (p > 0) {
                sampledWords += (wordCounts.getOrNull(i) ?: 0).toLong()
                sampledPages += p.toLong()
            }
        }
        // 当前章节也纳入样本（它一定有真实页数）
        val currentChapterWords = wordCounts.getOrNull(currentChapterIndex)?.coerceAtLeast(1) ?: 1
        if (sampledPages == 0L) sampledPages = currentPages.toLong()
        if (sampledWords == 0L) sampledWords = currentChapterWords.toLong()
        val wordsPerPage = sampledWords.toDouble() / sampledPages

        // Fallback：完全无数据时降级为章节索引进度，分数槽位仍显示章节数
        if (wordCounts.isEmpty() && pageCounts.isEmpty()) {
            val progress = ((currentChapterIndex + state.pageIndex.toFloat() / currentPages) / totalChapters).coerceIn(0f, 1f)
            return Triple((currentChapterIndex + 1).toLong(), totalChapters.toLong(), progress)
        }

        // 计算当前页在全书中的位置
        var pagesBeforeCurrent = 0L
        for (i in 0 until currentChapterIndex) {
            val realCount = pageCounts[i]
            if (realCount != null) {
                pagesBeforeCurrent += realCount.toLong()
            } else {
                val words = wordCounts.getOrNull(i)?.toLong() ?: 0L
                pagesBeforeCurrent += (words / wordsPerPage).toLong()
            }
        }
        val currentBookPage = pagesBeforeCurrent + state.pageIndex + 1

        // 计算全书总页数
        var totalBookPages = pagesBeforeCurrent + currentPages
        for (i in (currentChapterIndex + 1) until totalChapters) {
            val realCount = pageCounts[i]
            if (realCount != null) {
                totalBookPages += realCount.toLong()
            } else {
                val words = wordCounts.getOrNull(i)?.toLong() ?: 0L
                totalBookPages += (words / wordsPerPage).toLong()
            }
        }
        totalBookPages = totalBookPages.coerceAtLeast(currentBookPage)

        val progress = if (totalBookPages > 0) currentBookPage.toFloat() / totalBookPages else 0f
        return Triple(currentBookPage, totalBookPages, progress.coerceIn(0f, 1f))
    }
}
