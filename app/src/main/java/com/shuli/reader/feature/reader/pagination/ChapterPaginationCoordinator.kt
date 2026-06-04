package com.shuli.reader.feature.reader.pagination

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import com.shuli.reader.core.reader.ChapterProvider
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.Paginator
import com.shuli.reader.core.reader.cache.CacheManager
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig
import com.shuli.reader.core.reader.model.TextChapter
import com.shuli.reader.core.repository.BookRepository
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.core.text.ChineseConverter
import com.shuli.reader.core.text.PanguSpacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 章节分页协调器。
 *
 * 职责：流式分页、reflow、预加载相邻章节、字数统计、
 *       布局配置构建、字节↔字符坐标转换。
 *
 * 通过 [uiState] 读写共享状态，不反向依赖 ViewModel。
 */
class ChapterPaginationCoordinator(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val paginator: Paginator,
    private val cacheManager: CacheManager,
    private val bookRepository: BookRepository?,
    private val appContext: android.content.Context?,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "ChapterPagCoord"
    }

    // ── 可变外部状态（由 ViewModel 在适当时机设置）──────────────────

    /** 当前书籍文件路径 */
    var currentBookFilePath: String? = null

    /** 当前书籍内容 */
    var loadedBookContent: BookContent? = null

    /** 当前章节的 utf16IndexToByte 映射表 */
    var currentChapterUtf16Map: IntArray = IntArray(0)
        private set

    /** 一次性缓存章节文本，用完即清 */
    var cachedChapterText: String? = null

    var density: Float = 3f
    var screenWidthPx: Int = 1080
    var screenHeightPx: Int = 1920

    // ── reflow 专用 ──────────────────────────────────────────────

    /** reflow 防抖 Job */
    private var reflowJob: Job? = null

    // ── 布局配置 ──────────────────────────────────────────────────

    /** 从 ReaderPreferences 构建 ReaderLayoutConfig */
    fun layoutConfigFor(preferences: ReaderPreferences): ReaderLayoutConfig {
        val textSizePx = preferences.fontSize * density
        return ReaderLayoutConfig(
            pageSize = PageSize(screenWidthPx, screenHeightPx),
            textSize = textSizePx,
            lineHeight = preferences.lineSpacing,
            paragraphSpacing = preferences.paragraphSpacing * textSizePx,
            marginHorizontal = preferences.marginHorizontal * density,
            marginVertical = preferences.marginVertical * density,
            indent = preferences.indent,
            density = density,
            letterSpacingPx = preferences.letterSpacing * textSizePx,
            titleStyle = preferences.titleStyle,
            useZhLayout = preferences.useZhLayout,
            bottomJustify = preferences.bottomJustify,
            headerMarginTop = preferences.header.marginTop * density,
            footerMarginBottom = preferences.footer.marginBottom * density,
        )
    }

    /** 计算当前排版参数的布局哈希 */
    fun computeLayoutHash(preferences: ReaderPreferences): String {
        val config = layoutConfigFor(preferences)
        return com.shuli.reader.core.reader.cache.PageCountPersistence.computeLayoutHash(
            config = config,
            showHeader = preferences.header.visibility != HeaderVisibility.ALWAYS_HIDE,
            showFooter = preferences.footer.visibility != HeaderVisibility.ALWAYS_HIDE,
            chineseConvert = preferences.chineseConvert.ordinal,
            usePanguSpacing = preferences.usePanguSpacing,
        )
    }

    // ── 字节↔字符坐标桥接 ────────────────────────────────────────

    /** UTF-16 char index → 章节内相对字节偏移 */
    fun charToByteOffset(charIndex: Int): Int {
        val map = currentChapterUtf16Map
        if (map.isEmpty()) return charIndex
        val idx = charIndex.coerceIn(0, map.size - 1)
        return map[idx]
    }

    /** 章节内相对字节偏移 → UTF-16 char index */
    fun byteToCharOffset(byteOffset: Int): Int {
        val map = currentChapterUtf16Map
        if (map.isEmpty()) return byteOffset
        for (i in map.indices) {
            if (map[i] > byteOffset) return (i - 1).coerceAtLeast(0)
        }
        return map.size - 1
    }

    /** 更新 utf16IndexToByte 映射（由 ViewModel 在 loadChapterContent 后调用） */
    fun updateUtf16Map(map: IntArray) {
        currentChapterUtf16Map = map
    }

    // ── 章节字数统计 ──────────────────────────────────────────────

    /** 异步计算所有章节字数 */
    fun computeChapterWordCounts(
        chapterEntities: List<com.shuli.reader.core.database.entity.BookChapterEntity>,
    ) {
        scope.launch(Dispatchers.Default) {
            val counts = chapterEntities.map { it.wordCount.coerceAtLeast(0) }
            uiState.value = uiState.value.copy(chapterWordCounts = counts)
        }
    }

    // ── 流式分页 ──────────────────────────────────────────────────

    /**
     * 流式分页：首页先显示，其余后台继续。
     *
     * @param onDone 分页完成后的回调（预加载、字数统计等后续动作）
     * @return 分页协程 Job
     */
    fun paginateChapterStreaming(
        content: BookContent,
        index: Int,
        targetCharOffset: Int = 0,
        onDone: (() -> Unit)? = null,
        /** reflow 场景注入缩放比投影，非 reflow 传 null 走简单 merge */
        onMergePageCounts: ((oldPageCounts: Map<Int, Int>, newPageSize: Int) -> Map<Int, Int>)? = null,
    ): Job {
        val chapters = content.normalizedChapters()
        val chapterMeta = chapters.getOrNull(index) ?: return Job().apply { complete() }

        val config = layoutConfigFor(uiState.value.readerPreferences)

        // 缓存命中快路径
        val prefs = uiState.value.readerPreferences
        val cacheKey = buildChapterCacheKey(index, config, prefs)
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
                val cached = cachedChapterText
                val chapterText = if (cached != null) {
                    cachedChapterText = null
                    cached
                } else if (bookRepository != null && currentBookFilePath != null) {
                    withContext(Dispatchers.IO) {
                        bookRepository.getChapterText(File(currentBookFilePath!!), index, content)
                    }
                } else {
                    content.chapterText(chapterMeta)
                }
                logPerf("getChapterText[$index]", textLoadStart)

                val convertedText = applyTextTransforms(chapterText, prefs)
                val chapter = TextChapter(
                    chapterIndex = index,
                    title = chapterMeta.title,
                    content = convertedText,
                )

                // 设置流式布局监听器
                chapter.layoutListener = StreamingLayoutListener(
                    chapterIndex = index,
                    targetCharOffset = targetCharOffset,
                    onMergePageCounts = onMergePageCounts,
                )

                val isReflow = uiState.value.currentPage != null
                uiState.value = uiState.value.copy(
                    currentChapter = chapter,
                    chapterIndex = index,
                    chapterTitle = chapterMeta.title,
                    currentPage = if (isReflow) uiState.value.currentPage else null,
                    pageIndex = if (isReflow) uiState.value.pageIndex else 0,
                    totalPages = if (isReflow) uiState.value.totalPages else 0,
                )

                val showHeader = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE
                val showFooter = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE
                withContext(Dispatchers.Default) {
                    paginator.paginateStreaming(chapter, convertedText, config, showHeader = showHeader, showFooter = showFooter).collect()
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

    // ── 单章分页（非流式，用于预加载）──────────────────────────────

    suspend fun paginateChapter(content: BookContent, index: Int): TextChapter? {
        val chapters = content.normalizedChapters()
        val chapter = chapters.getOrNull(index) ?: return null

        val config = layoutConfigFor(uiState.value.readerPreferences)
        val prefs = uiState.value.readerPreferences
        val cacheKey = buildChapterCacheKey(index, config, prefs)
        cacheManager.getChapter(cacheKey)?.let { return it }

        val chapterText = loadChapterText(content, chapter, index)
        val convertedText = applyTextTransforms(chapterText, prefs)

        val showHeader = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE
        val showFooter = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE

        return withContext(Dispatchers.Default) {
            paginator.paginateChapter(
                chapterIndex = index,
                title = chapter.title,
                content = convertedText,
                config = config,
                showHeader = showHeader,
                showFooter = showFooter,
            ).also { result ->
                cacheManager.putChapter(cacheKey, result)
            }
        }
    }

    // ── 预加载相邻章节 ────────────────────────────────────────────

    fun preloadAdjacentChapters(content: BookContent, currentIndex: Int) {
        val chapters = content.normalizedChapters()
        val config = layoutConfigFor(uiState.value.readerPreferences)
        val bookId = uiState.value.bookId.toString()

        val indicesToPreload = listOfNotNull(
            (currentIndex - 1).takeIf { it >= 0 },
            (currentIndex + 1).takeIf { it < chapters.size },
        )

        val prefs = uiState.value.readerPreferences
        for (index in indicesToPreload) {
            val cacheKey = buildChapterCacheKey(index, config, prefs, bookId)
            if (cacheManager.getChapter(cacheKey) != null) continue

            scope.launch(Dispatchers.Default) {
                val chapter = chapters[index]
                val chapterText = loadChapterText(content, chapter, index)
                val convertedText = applyTextTransforms(chapterText, prefs)

                val result = paginator.paginateChapter(
                    chapterIndex = index,
                    title = chapter.title,
                    content = convertedText,
                    config = config,
                )
                cacheManager.putChapter(cacheKey, result)
            }
        }
    }

    // ── Reflow ────────────────────────────────────────────────────

    fun reflowCurrentChapter(
        preferences: ReaderPreferences,
        currentLayoutHash: String,
    ) {
        val state = uiState.value
        val chapter = state.currentChapter ?: return
        val charOffset = state.currentPage?.startCharOffset ?: 0
        val content = loadedBookContent ?: return
        val oldCurrentPages = (state.chapterPageCounts[chapter.chapterIndex] ?: state.totalPages).coerceAtLeast(1)

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

    // ── 内部：流式布局监听器 ──────────────────────────────────────

    private inner class StreamingLayoutListener(
        private val chapterIndex: Int,
        private val targetCharOffset: Int,
        private val onMergePageCounts: ((Map<Int, Int>, Int) -> Map<Int, Int>)?,
    ) : TextChapter.LayoutListener {

        override fun onPageReady(pageIndex: Int, page: com.shuli.reader.core.reader.model.TextPage) {
            val currentState = uiState.value
            if (currentState.chapterIndex != chapterIndex) return

            val isChapterSwitch = currentState.currentPage?.chapterIndex?.let { it != chapterIndex } ?: false
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
            if (currentState.chapterIndex != chapterIndex) return

            val chapter = currentState.currentChapter ?: return
            val mergedPageCounts = onMergePageCounts?.invoke(currentState.chapterPageCounts, chapter.pageSize)
                ?: currentState.chapterPageCounts
            uiState.value = currentState.copy(
                totalPages = chapter.pageSize,
                chapterPageCounts = mergedPageCounts + (chapterIndex to chapter.pageSize),
                isLoading = false,
                isReflowing = false,
                layoutVersion = currentState.layoutVersion + 1,
            )

            val config = layoutConfigFor(uiState.value.readerPreferences)
            val prefs = uiState.value.readerPreferences
            val cacheKey = buildChapterCacheKey(chapterIndex, config, prefs)
            cacheManager.putChapter(cacheKey, chapter)
            persistPageCounts()

            if (com.shuli.reader.BuildConfig.DEBUG) {
                android.util.Log.d(TAG, "layoutCompleted[$chapterIndex]: ${chapter.pageSize} pages")
            }
        }
    }

    // ── 内部：缓存 key 构建 ──────────────────────────────────────

    private fun buildChapterCacheKey(
        index: Int,
        config: ReaderLayoutConfig,
        prefs: ReaderPreferences,
        bookId: String = uiState.value.bookId.toString(),
    ): CacheManager.ChapterCacheKey {
        return CacheManager.ChapterCacheKey(
            bookId = bookId,
            chapterIndex = index,
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

    // ── 内部：持久化页数 ──────────────────────────────────────────

    private var lastPersistedCounts: Map<Int, Int> = emptyMap()
    private var persistJob: Job? = null

    private fun persistPageCounts() {
        val ctx = appContext ?: return
        val state = uiState.value
        val counts = state.chapterPageCounts
        val layoutHash = computeLayoutHash(state.readerPreferences)
        if (counts.isEmpty() || layoutHash.isEmpty()) return
        if (counts == lastPersistedCounts) return
        persistJob?.cancel()
        persistJob = scope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            try {
                com.shuli.reader.core.reader.cache.PageCountPersistence.save(
                    ctx, state.bookId.toString(), layoutHash, counts,
                )
                lastPersistedCounts = counts
            } catch (_: Exception) { /* PageCountPersistence.save 内部已记录日志 */ }
        }
    }

    // ── 内部：文本加载与转换 ──────────────────────────────────────

    private suspend fun loadChapterText(
        content: BookContent,
        chapter: Chapter,
        index: Int,
    ): String {
        val repository = bookRepository
        val filePath = currentBookFilePath
        return if (repository != null && filePath != null) {
            withContext(Dispatchers.IO) {
                repository.getChapterText(File(filePath), index, content)
            }
        } else {
            content.chapterText(chapter)
        }
    }

    private fun applyTextTransforms(text: String, prefs: ReaderPreferences): String {
        val converted = when (prefs.chineseConvert) {
            ChineseConvert.NONE -> text
            ChineseConvert.SIMPLIFIED -> ChineseConverter.toSimplified(text)
            ChineseConvert.TRADITIONAL -> ChineseConverter.toTraditional(text)
        }
        return if (prefs.usePanguSpacing) PanguSpacing.insert(converted) else converted
    }

    // ── BookContent 扩展 ─────────────────────────────────────────

    /** 返回规范化章节列表（空章节时构造单章全文） */
    fun BookContent.normalizedChapters(): List<Chapter> {
        if (chapters.isNotEmpty()) return chapters
        return if (content.isNotBlank()) {
            listOf(Chapter(title = "Full Text", byteStart = 0L, byteEnd = content.length.toLong()))
        } else {
            emptyList()
        }
    }

    private fun BookContent.chapterText(chapter: Chapter): String {
        return content
    }

    // ── 内部：性能日志 ────────────────────────────────────────────

    private fun logPerf(label: String, startMs: Long) {
        if (com.shuli.reader.BuildConfig.DEBUG) {
            android.util.Log.d(TAG, "$label: ${System.currentTimeMillis() - startMs}ms")
        }
    }
}
