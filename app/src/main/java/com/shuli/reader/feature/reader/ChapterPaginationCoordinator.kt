package com.shuli.reader.feature.reader

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import com.shuli.reader.core.reader.cache.CacheManager
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.reader.model.TextPage
import com.shuli.reader.core.reader.Paginator
import com.shuli.reader.core.repository.BookContentRepository
import com.shuli.reader.core.text.ChineseConverter
import com.shuli.reader.core.text.PanguSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 章节分页协调器（从 ReaderViewModel 拆出）
 *
 * 职责：流式分页、reflow、预加载、章节缓存。
 */
class ChapterPaginationCoordinator(
    private val cacheManager: CacheManager,
    private val paginator: Paginator,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val bookContentRepository: BookContentRepository?,
    private val currentBookFilePath: () -> String?,
    private val layoutConfigFor: (ReaderPreferences) -> ReaderLayoutConfig,
    private val persistPageCounts: () -> Unit,
    private val computeLayoutHash: (ReaderPreferences) -> String,
    private val normalizeChapters: BookContent.() -> List<Chapter>,
    private val getChapterText: BookContent.(Chapter) -> String,
    private val loadedBookContentProvider: () -> BookContent?,
    private val cachedChapterTextProvider: () -> String?,
    private val clearCachedChapterText: () -> Unit,
    private val onReflowStart: (String) -> Unit,
    private val logPerf: (String, Long) -> Unit,
) {
    private var reflowJob: Job? = null

    // ── 分页核心 ──────────────────────────────────────────────

    suspend fun paginateChapter(content: BookContent, index: Int): TextChapter? {
        val chapters = content.normalizeChapters()
        val chapter = chapters.getOrNull(index) ?: return null

        val config = layoutConfigFor(uiState.value.readerPreferences)
        val prefs = uiState.value.readerPreferences
        val cacheKey = buildCacheKey(index, config, prefs)
        cacheManager.getChapter(cacheKey)?.let { return it }

        val repository = bookContentRepository
        val filePath = currentBookFilePath()
        val chapterText = if (repository != null && filePath != null) {
            withContext(Dispatchers.IO) {
                repository.getChapterText(File(filePath), index, content)
            }
        } else {
            content.getChapterText(chapter)
        }

        val convertedText = applyChineseConvert(chapterText, prefs.chineseConvert)
        val finalText = if (prefs.usePanguSpacing) PanguSpacing.insert(convertedText) else convertedText

        val showHeader = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE
        val showFooter = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE

        return withContext(Dispatchers.Default) {
            paginator.paginateChapter(
                chapterIndex = index,
                title = chapter.title,
                content = finalText,
                config = config,
                showHeader = showHeader,
                showFooter = showFooter,
            ).also { result ->
                cacheManager.putChapter(cacheKey, result)
            }
        }
    }

    /**
     * 流式分页：首页先显示，其余后台继续
     */
    fun paginateChapterStreaming(
        content: BookContent,
        index: Int,
        targetCharOffset: Int = 0,
        onDone: (() -> Unit)? = null,
        onMergePageCounts: ((oldPageCounts: Map<Int, Int>, newPageSize: Int) -> Map<Int, Int>)? = null,
    ): Job {
        val chapters = content.normalizeChapters()
        val chapterMeta = chapters.getOrNull(index) ?: return Job().apply { complete() }

        val config = layoutConfigFor(uiState.value.readerPreferences)
        val repository = bookContentRepository
        val filePath = currentBookFilePath()

        val prefs = uiState.value.readerPreferences
        val cacheKey = buildCacheKey(index, config, prefs)
        cacheManager.getChapter(cacheKey)?.let { cached ->
            android.util.Log.d(TAG, "openChapter[$index]: 缓存命中, pages=${cached.pageSize}")
            return scope.launch {
                uiState.value = uiState.value.copy(
                    currentChapter = cached,
                    chapterIndex = index,
                    chapterTitle = chapterMeta.title,
                    totalPages = cached.pageSize,
                    chapterPageCounts = uiState.value.chapterPageCounts + (index to cached.pageSize),
                    isLoading = false,
                    isReflowing = false,
                )
                val startPage = if (targetCharOffset > 0) {
                    cached.getPageIndexByCharIndex(targetCharOffset)
                } else {
                    0
                }
                uiState.value = uiState.value.copy(
                    pageIndex = startPage,
                    currentPage = cached.getPage(startPage),
                )
                persistPageCounts()
                onDone?.invoke()
            }
        }

        android.util.Log.d(TAG, "openChapter[$index]: 缓存未命中, 开始加载章节文本")

        return scope.launch {
            val isReflow = uiState.value.currentPage != null
            if (!isReflow) {
                uiState.value = uiState.value.copy(isLoading = true)
            }

            try {
                val textLoadStart = System.currentTimeMillis()
                val cached = cachedChapterTextProvider()
                val chapterText = if (cached != null) {
                    clearCachedChapterText()
                    cached
                } else if (repository != null && filePath != null) {
                    withContext(Dispatchers.IO) {
                        repository.getChapterText(File(filePath), index, content)
                    }
                } else {
                    content.getChapterText(chapterMeta)
                }
                logPerf("getChapterText[$index]", textLoadStart)

                val convertedText = applyChineseConvert(chapterText, uiState.value.readerPreferences.chineseConvert)
                val panguPrefs = uiState.value.readerPreferences
                val finalText = if (panguPrefs.usePanguSpacing) PanguSpacing.insert(convertedText) else convertedText

                val chapter = TextChapter(
                    chapterIndex = index,
                    title = chapterMeta.title,
                    content = finalText,
                )

                chapter.layoutListener = object : TextChapter.LayoutListener {
                    override fun onPageReady(pageIndex: Int, page: TextPage) {
                        val currentState = uiState.value
                        if (currentState.chapterIndex != index) return

                        val isChapterSwitch = currentState.currentPage?.chapterIndex?.let { it != index } ?: false
                        if (pageIndex == 0 && (currentState.currentPage == null || currentState.isReflowing || isChapterSwitch)) {
                            uiState.value = currentState.copy(
                                currentPage = page,
                                pageIndex = 0,
                            )
                        } else if (targetCharOffset > 0 &&
                            page.startCharOffset <= targetCharOffset &&
                            targetCharOffset < page.endCharOffset
                        ) {
                            uiState.value = uiState.value.copy(
                                currentPage = page,
                                pageIndex = pageIndex,
                            )
                        }
                    }

                    override fun onLayoutCompleted() {
                        val currentState = uiState.value
                        if (currentState.chapterIndex != index) return
                        val mergedPageCounts = onMergePageCounts?.invoke(currentState.chapterPageCounts, chapter.pageSize)
                            ?: currentState.chapterPageCounts
                        uiState.value = currentState.copy(
                            totalPages = chapter.pageSize,
                            chapterPageCounts = mergedPageCounts + (index to chapter.pageSize),
                            isLoading = false,
                            isReflowing = false,
                            layoutVersion = currentState.layoutVersion + 1,
                        )
                        cacheManager.putChapter(cacheKey, chapter)
                        persistPageCounts()
                        if (com.shuli.reader.BuildConfig.DEBUG) {
                            android.util.Log.d(TAG, "layoutCompleted[$index]: ${chapter.pageSize} pages")
                        }
                    }
                }

                val isReflow2 = uiState.value.currentPage != null
                uiState.value = uiState.value.copy(
                    currentChapter = chapter,
                    chapterIndex = index,
                    chapterTitle = chapterMeta.title,
                    currentPage = if (isReflow2) uiState.value.currentPage else null,
                    pageIndex = if (isReflow2) uiState.value.pageIndex else 0,
                    totalPages = if (isReflow2) uiState.value.totalPages else 0,
                )

                val showHeader = uiState.value.readerPreferences.header.visibility != HeaderVisibility.ALWAYS_HIDE
                val showFooter = uiState.value.readerPreferences.footer.visibility != HeaderVisibility.ALWAYS_HIDE
                withContext(Dispatchers.Default) {
                    paginator.paginateStreaming(chapter, finalText, config, showHeader = showHeader, showFooter = showFooter).collect { }
                    chapter.markCompleted()
                }

                onDone?.invoke()
            } catch (e: Exception) {
                uiState.value = uiState.value.copy(
                    isLoading = false,
                    error = e.message,
                )
            }
        }
    }

    /**
     * R7: 预加载相邻章节（异步，不阻塞当前流程）
     */
    fun preloadAdjacentChapters(content: BookContent, currentIndex: Int) {
        val chapters = content.normalizeChapters()
        val config = layoutConfigFor(uiState.value.readerPreferences)

        val indicesToPreload = listOfNotNull(
            (currentIndex - 1).takeIf { it >= 0 },
            (currentIndex + 1).takeIf { it < chapters.size },
        )

        val prefs = uiState.value.readerPreferences
        for (index in indicesToPreload) {
            val cacheKey = buildCacheKey(index, config, prefs)
            if (cacheManager.getChapter(cacheKey) != null) continue

            scope.launch(Dispatchers.Default) {
                val chapter = chapters[index]
                val repository = bookContentRepository
                val filePath = currentBookFilePath()
                val chapterText = if (repository != null && filePath != null) {
                    withContext(Dispatchers.IO) {
                        repository.getChapterText(File(filePath), index, content)
                    }
                } else {
                    content.getChapterText(chapter)
                }

                val convertedText = applyChineseConvert(chapterText, prefs.chineseConvert)
                val finalText = if (prefs.usePanguSpacing) PanguSpacing.insert(convertedText) else convertedText

                val result = paginator.paginateChapter(
                    chapterIndex = index,
                    title = chapter.title,
                    content = finalText,
                    config = config,
                )
                cacheManager.putChapter(cacheKey, result)
            }
        }
    }

    fun reflowCurrentChapter(preferences: ReaderPreferences) {
        val state = uiState.value
        val chapter = state.currentChapter ?: return
        val charOffset = state.currentPage?.startCharOffset ?: 0
        val content = loadedBookContentProvider() ?: return
        val oldCurrentPages = (state.chapterPageCounts[chapter.chapterIndex] ?: state.totalPages).coerceAtLeast(1)

        // 通知 ViewModel 更新布局哈希
        onReflowStart(computeLayoutHash(preferences))

        reflowJob?.cancel()
        reflowJob = scope.launch {
            cacheManager.clearBook(state.bookId.toString())

            val mergeFn: (Map<Int, Int>, Int) -> Map<Int, Int> = { oldMap, newPageSize ->
                if (oldCurrentPages > 0 && newPageSize > 0) {
                    val scale = newPageSize.toDouble() / oldCurrentPages
                    oldMap.mapValues { (_, p) -> (p * scale).toInt().coerceAtLeast(1) }
                } else {
                    oldMap
                }
            }
            paginateChapterStreaming(
                content = content,
                index = chapter.chapterIndex,
                targetCharOffset = charOffset,
                onMergePageCounts = mergeFn,
            )
        }
    }

    // ── 内部辅助 ──────────────────────────────────────────────

    private fun buildCacheKey(
        chapterIndex: Int,
        config: ReaderLayoutConfig,
        prefs: ReaderPreferences,
    ): CacheManager.ChapterCacheKey {
        return CacheManager.ChapterCacheKey(
            bookId = uiState.value.bookId.toString(),
            chapterIndex = chapterIndex,
            textSize = config.textSize,
            lineHeight = config.lineHeight,
            pageWidth = config.pageSize.width,
            pageHeight = config.pageSize.height,
            letterSpacingPx = config.letterSpacingPx,
            marginHorizontal = config.marginHorizontal,
            marginVertical = config.marginVertical,
            indent = config.indent,
            showHeader = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE,
            showFooter = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE,
            chineseConvert = prefs.chineseConvert.ordinal,
            usePanguSpacing = prefs.usePanguSpacing,
            titleAlignOrdinal = prefs.titleStyle.align.ordinal,
            titleSizeOffsetSp = prefs.titleStyle.sizeOffsetSp,
            titleMarginTopDp = prefs.titleStyle.marginTopDp,
            titleMarginBottomDp = prefs.titleStyle.marginBottomDp,
        )
    }

    private fun applyChineseConvert(text: String, convert: ChineseConvert): String {
        return when (convert) {
            ChineseConvert.NONE -> text
            ChineseConvert.SIMPLIFIED -> ChineseConverter.toSimplified(text)
            ChineseConvert.TRADITIONAL -> ChineseConverter.toTraditional(text)
        }
    }

    companion object {
        private const val TAG = "ChapterPagination"
    }
}
