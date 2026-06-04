package com.shuli.reader.feature.reader.progress

import android.content.Context
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotResolution
import com.shuli.reader.core.reader.SlotResolver
import com.shuli.reader.core.reader.cache.PageCountPersistence
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.feature.reader.ReaderUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 阅读进度计算 + 页眉页脚槽位解析 + 页数持久化。
 *
 * 从 [com.shuli.reader.feature.reader.ReaderViewModel] 抽出，负责：
 * 1. 计算当前页在全书中的位置（真实页数优先，字数估算兜底）
 * 2. 解析页眉/页脚槽位为 [SlotResolution]
 * 3. 把 `chapterPageCounts` 持久化到磁盘（带 300ms 防抖 + 结构去重）
 * 4. 计算排版哈希（用于区分不同 layout 的缓存）
 *
 * @param uiState 与 ViewModel 共享的 UI 状态
 * @param scope 协程作用域（持久化 Job 在此 scope 内 launch）
 * @param appContext 应用上下文（持久化需要）
 * @param densityProvider 当前屏幕密度（layout 变化时会更新）
 * @param screenSizeProvider 当前屏幕像素尺寸（layout 变化时会更新）
 */
class ReadingProgressTracker(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val appContext: () -> Context?,
    private val densityProvider: () -> Float,
    private val screenSizeProvider: () -> Pair<Int, Int>,
) {

    /** 当前排版哈希，由 ViewModel 在 openBook/reflow 时通过 [updateLayoutHash] 设置 */
    var currentLayoutHash: String = ""
        private set

    // ── 持久化防抖 ────────────────────────────────────────────
    private var lastPersistedCounts: Map<Int, Int> = emptyMap()
    private var persistJob: Job? = null

    // ── 进度计算 ──────────────────────────────────────────────

    /**
     * 计算全书进度：返回 `(当前全书页码, 全书总页数, 百分比[0,1])`。
     *
     * 真实页数优先：已分页章节使用 `chapterPageCounts[i]`；未分页章节用
     * `wordCounts[i] / weightedWordsPerPage` 折算。
     */
    fun computeBookProgress(): Triple<Long, Long, Float> {
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
        val currentChapterWords = wordCounts.getOrNull(currentChapterIndex)?.coerceAtLeast(1) ?: 1
        if (sampledPages == 0L) sampledPages = currentPages.toLong()
        if (sampledWords == 0L) sampledWords = currentChapterWords.toLong()
        val wordsPerPage = sampledWords.toDouble() / sampledPages

        // Fallback：完全无数据时降级为章节索引进度，分数槽位仍显示章节数
        if (wordCounts.isEmpty() && pageCounts.isEmpty()) {
            val progress = ((currentChapterIndex + state.pageIndex.toFloat() / currentPages) / totalChapters)
                .coerceIn(0f, 1f)
            return Triple((currentChapterIndex + 1).toLong(), totalChapters.toLong(), progress)
        }

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

    // ── 页眉页脚 ─────────────────────────────────────────────

    /** 统一解析页眉和页脚槽位为 [SlotResolution] 对，避免重复计算进度。 */
    fun resolveHeaderAndFooterSlots(): Pair<SlotResolution, SlotResolution> {
        val state = uiState.value
        val prefs = state.readerPreferences
        val (currentPos, totalPos, bookProgressPercent) = computeBookProgress()

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

    // ── 排版哈希 + 持久化 ─────────────────────────────────────

    /** 根据当前 preferences 计算排版哈希并缓存到 [currentLayoutHash]。 */
    fun updateLayoutHash(preferences: ReaderPreferences): String {
        val (w, h) = screenSizeProvider()
        val config = buildLayoutConfig(preferences, densityProvider(), w, h)
        val hash = PageCountPersistence.computeLayoutHash(
            config = config,
            showHeader = preferences.header.visibility != HeaderVisibility.ALWAYS_HIDE,
            showFooter = preferences.footer.visibility != HeaderVisibility.ALWAYS_HIDE,
            chineseConvert = preferences.chineseConvert.ordinal,
            usePanguSpacing = preferences.usePanguSpacing,
        )
        currentLayoutHash = hash
        return hash
    }

    /** 为外部（如 [ChapterPaginationCoordinator]）提供一个现成的 [ReaderLayoutConfig]。 */
    fun buildLayoutConfig(preferences: ReaderPreferences): ReaderLayoutConfig {
        val (w, h) = screenSizeProvider()
        return buildLayoutConfig(preferences, densityProvider(), w, h)
    }

    /**
     * 防抖持久化 `chapterPageCounts` 到磁盘：
     * - 与上次写入内容结构相等时直接跳过
     * - 300ms 内多次调用只执行最后一次
     * - 真正落盘成功后才更新 `lastPersistedCounts`，避免进程退出时丢数据
     */
    fun schedulePersist() {
        val ctx = appContext() ?: return
        val state = uiState.value
        val counts = state.chapterPageCounts
        if (counts.isEmpty() || currentLayoutHash.isEmpty()) return
        if (counts == lastPersistedCounts) return
        persistJob?.cancel()
        persistJob = scope.launch(Dispatchers.IO) {
            delay(300)
            try {
                PageCountPersistence.save(ctx, state.bookId.toString(), currentLayoutHash, counts)
                lastPersistedCounts = counts
            } catch (_: Exception) { /* PageCountPersistence.save 内部已记录日志 */ }
        }
    }

    /** 从磁盘异步加载历史页数缓存，与当前 [ReaderUiState.chapterPageCounts] 合并。 */
    fun loadPersistedAsync(onLoaded: (Map<Int, Int>) -> Unit) {
        val ctx = appContext() ?: return
        val hash = currentLayoutHash
        if (hash.isEmpty()) return
        val bookId = uiState.value.bookId.toString()
        scope.launch(Dispatchers.Default) {
            val persisted = PageCountPersistence.load(ctx, bookId, hash)
            if (persisted.isNotEmpty()) {
                onLoaded(persisted)
            }
        }
    }

    /** 进程退出前强制 flush（取消防抖，立即写）。 */
    fun flushPersistSync() {
        val ctx = appContext() ?: return
        val state = uiState.value
        val counts = state.chapterPageCounts
        if (counts.isEmpty() || currentLayoutHash.isEmpty()) return
        persistJob?.cancel()
        try {
            PageCountPersistence.save(ctx, state.bookId.toString(), currentLayoutHash, counts)
            lastPersistedCounts = counts
        } catch (_: Exception) { /* 尽力而为 */ }
    }
}
