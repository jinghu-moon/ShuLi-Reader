package com.shuli.reader.feature.reader.session
import com.shuli.reader.feature.reader.screen.ReaderUiState

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.database.entity.BookChapterEntity
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.parser.DecodedSegment
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import com.shuli.reader.feature.reader.session.ReadingStateManager
import com.shuli.reader.core.reader.engine.cache.BookCacheStore
import com.shuli.reader.core.reader.engine.cache.CacheManager
import com.shuli.reader.core.repository.BookContentRepository
import com.shuli.reader.core.repository.BookQueryRepository
import com.shuli.reader.core.repository.ReadingProgressRepository
import com.shuli.reader.core.repository.SearchIndexRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书籍会话管理：开书、章节加载、阅读进度持久化。
 *
 * 从 ReaderViewModel 拆出，SRP —— 只负责"书籍生命周期"这一变更轴。
 */
internal class BookSessionManager(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val bookQueryRepository: BookQueryRepository?,
    private val searchIndexRepository: SearchIndexRepository?,
    private val bookContentRepository: BookContentRepository?,
    private val readingProgressRepository: ReadingProgressRepository?,
    private val readingProgressDao: com.shuli.reader.core.database.dao.ReadingProgressDao?,
    private val chapterReadingStatsDao: com.shuli.reader.core.database.dao.ChapterReadingStatsDao?,
    private val readingSessionDao: com.shuli.reader.core.database.dao.ReadingSessionDao?,
    private val cacheManager: () -> CacheManager,
    private val setCacheManager: (CacheManager) -> Unit,
    private val readingStateManager: () -> ReadingStateManager,
    private val stringResolver: () -> com.shuli.reader.core.i18n.AppStrings,
    private val appContext: android.content.Context?,
    // ── 回调：读写 ViewModel 内部状态 ──
    private val loadedBookContentProvider: () -> BookContent?,
    private val setLoadedBookContent: (BookContent?) -> Unit,
    private val currentBookFilePathProvider: () -> String?,
    private val setCurrentBookFilePath: (String?) -> Unit,
    private val isCurrentBookEpubProvider: () -> Boolean,
    private val setIsCurrentBookEpub: (Boolean) -> Unit,
    private val currentChapterUtf16MapProvider: () -> IntArray,
    private val setCurrentChapterUtf16Map: (IntArray) -> Unit,
    private val cachedChapterTextProvider: () -> String?,
    private val setCachedChapterText: (String?) -> Unit,
    private val currentLayoutHashProvider: () -> String,
    private val setCurrentLayoutHash: (String) -> Unit,
    // ── 回调：协调其他管理器 ──
    private val paginateChapterStreaming: (BookContent, Int, Int, (() -> Unit)?) -> Job,
    private val preloadAdjacentChapters: (BookContent, Int) -> Unit,
    private val loadBookmarks: () -> Unit,
    private val loadNotes: () -> Unit,
    private val onChapterLoaded: () -> Unit,
    private val logPerf: (String, Long) -> Unit,
    private val byteToCharOffset: (Int) -> Int,
    private val charToByteOffset: (Int) -> Int,
    private val computeLayoutHash: (ReaderPreferences) -> String,
) {

    /** 当前活跃的章节加载 Job（防止快速连续调用导致并发冲突） */
    private var chapterJob: Job? = null

    // ── 章节阅读统计 ──────────────────────────────────────

    /** 上一次活跃的章节索引（用于 per-chapter 时间累计） */
    private var lastActiveChapterIndex: Int = -1
    /** 当前章节开始阅读的时间戳（毫秒） */
    private var chapterStartTimestamp: Long = 0L

    // ── 开书 ──────────────────────────────────────────────

    /**
     * 打开书籍：加载元数据 → 章节目录 → 首屏内容 → 流式分页。
     */
    fun openBook(bookId: Long) {
        if (bookId <= 0L) return
        val perfStart = System.currentTimeMillis()

        // 切换书籍时释放旧书籍缓存
        val oldBookId = uiState.value.bookId
        if (oldBookId != 0L && oldBookId != bookId) {
            BookCacheStore.releaseBook(oldBookId.toString())
        }
        setCacheManager(BookCacheStore.getBookCache(bookId.toString()))

        // 结束上一次阅读会话
        val sessionElapsed = readingStateManager().endSession()
        if (oldBookId != 0L && sessionElapsed > 0L) {
            persistReadingPosition(oldBookId, sessionElapsed)
        }

        scope.launch {
            uiState.value = uiState.value.copy(isLoading = true, error = null, bookId = bookId)

            try {
                val queryRepo = bookQueryRepository
                val searchRepo = searchIndexRepository
                val contentRepo = bookContentRepository
                if (queryRepo == null || searchRepo == null || contentRepo == null) {
                    openFallbackBook(bookId)
                    loadBookmarks()
                    loadNotes()
                    return@launch
                }

                // 1. 获取书籍元数据
                val dbStart = System.currentTimeMillis()
                val book = withContext(Dispatchers.IO) {
                    queryRepo.getBookById(bookId).first()
                } ?: run {
                    uiState.value = uiState.value.copy(
                        isLoading = false,
                        error = "Book not found: $bookId",
                    )
                    return@launch
                }
                logPerf("getBookById", dbStart)

                // 2. 确保章节目录索引存在
                val indexStart = System.currentTimeMillis()
                withContext(Dispatchers.IO) { searchRepo.ensureChapterIndex(bookId) }
                val chapterIndexList = withContext(Dispatchers.IO) {
                    searchRepo.getChapterIndex(bookId)
                }
                logPerf("ensureChapterIndex [${book.fileType}]", indexStart)

                // §11.1.1.1: 加载 SnapshotDigest（T0 fallback 用）
                val digest = readingProgressDao?.loadSnapshotDigest(bookId)
                if (digest != null) {
                    uiState.value = uiState.value.copy(snapshotDigest = digest)
                }

                setCurrentBookFilePath(book.filePath)
                setIsCurrentBookEpub(book.fileType == "EPUB")

                val chapterCount = chapterIndexList.size
                if (chapterCount == 0) {
                    uiState.value = uiState.value.copy(isLoading = false)
                    return@launch
                }

                // v4: 通过 durByteOffset 查找当前章节
                val durByteOffset = book.durByteOffset
                val chapterIndex = chapterIndexList
                    .indexOfLast { it.byteStart <= durByteOffset }
                    .coerceIn(0, chapterCount - 1)

                uiState.value = uiState.value.copy(
                    bookTitle = book.title,
                    chapterTitle = book.durChapterTitle.orEmpty(),
                    chapterIndex = chapterIndex,
                    totalChapters = chapterCount,
                    chapterTitles = chapterIndexList.map { it.title },
                    chapterWordCounts = emptyList(),
                )

                // 3. 加载当前章节内容
                val parseStart = System.currentTimeMillis()
                val (content, segment) = loadChapterContent(contentRepo, book, chapterIndexList, chapterIndex)
                setLoadedBookContent(content)
                setCurrentChapterUtf16Map(segment?.utf16IndexToByte ?: IntArray(0))
                setCachedChapterText(content.content.ifEmpty { null })
                logPerf("loadChapterContent", parseStart)

                withContext(Dispatchers.IO) {
                    readingProgressRepository?.updateLastReadTime(bookId)
                    readingProgressRepository?.markOpenedForReading(bookId)
                }
                loadBookmarks()
                loadNotes()
                readingStateManager().startSession()

                // 加载章节阅读统计 + 标记当前章节已访问
                loadChapterStats(bookId)
                markChapterVisited(bookId, chapterIndex)

                logPerf("openBook.preparation", perfStart)

                // v4: 将 durByteOffset 转为章节内字符偏移
                val chapterByteStart = chapterIndexList[chapterIndex].byteStart
                val relativeByteOffset = (durByteOffset - chapterByteStart).toInt().coerceAtLeast(0)
                val targetCharOffset = byteToCharOffset(relativeByteOffset)

                // 4. 流式分页
                val currentLayoutHash = computeLayoutHash(uiState.value.readerPreferences)
                setCurrentLayoutHash(currentLayoutHash)
                chapterJob = paginateChapterStreaming(content, chapterIndex, targetCharOffset) {
                    logPerf("firstPageReady", perfStart)
                    preloadAdjacentChapters(content, chapterIndex)
                    if (!isCurrentBookEpubProvider()) {
                        computeChapterWordCounts(chapterIndexList)
                    }
                    // 加载已持久化的页数缓存
                    scope.launch(Dispatchers.Default) {
                        val ctx = appContext ?: return@launch
                        val persisted = com.shuli.reader.core.reader.engine.cache.PageCountPersistence.load(
                            ctx, bookId.toString(), currentLayoutHash,
                        )
                        if (persisted.isNotEmpty()) {
                            uiState.value = uiState.value.copy(
                                chapterPageCounts = persisted + uiState.value.chapterPageCounts,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                uiState.value = uiState.value.copy(
                    isLoading = false,
                    error = e.message,
                )
            }
        }
    }

    // ── 打开章节 ──────────────────────────────────────────────

    /**
     * 打开指定章节，支持跳转到末页或指定字节偏移。
     */
    fun openChapter(
        index: Int,
        targetToLastPage: Boolean = false,
        targetByteOffset: Long = -1L,
    ) {
        val content = loadedBookContentProvider()
        if (content == null) {
            android.util.Log.w("BookSession", "openChapter[$index]: loadedBookContent 为 null，走 fallback")
            scope.launch { openFallbackChapter(index) }
            return
        }

        val safeIndex = index.coerceIn(0, (content.normalizedChapters().size - 1).coerceAtLeast(0))
        chapterJob?.cancel()

        // 切换章节时：累计上一章阅读时间 + 标记新章已访问
        flushChapterTime()
        markChapterVisited(uiState.value.bookId, safeIndex)

        val targetCharOffset = if (targetToLastPage) -1 else if (targetByteOffset >= 0) -1 else -1

        chapterJob = paginateChapterStreaming(content, safeIndex, targetCharOffset) {
            // v4: 如果有 targetByteOffset，用映射转为 charOffset 再跳转
            if (targetByteOffset >= 0 && !targetToLastPage) {
                val chapters = content.normalizedChapters()
                val chapterByteStart = chapters.getOrNull(safeIndex)?.byteStart ?: 0L
                val relativeByte = (targetByteOffset - chapterByteStart).toInt().coerceAtLeast(0)
                val charOffset = byteToCharOffset(relativeByte)
                val chapter = uiState.value.currentChapter
                if (chapter != null && chapter.pageSize > 0) {
                    val pi = chapter.getPageIndexByCharIndex(charOffset)
                    uiState.value = uiState.value.copy(
                        pageIndex = pi,
                        currentPage = chapter.getPage(pi),
                    )
                }
            }
            if (targetToLastPage) {
                val chapter = uiState.value.currentChapter ?: return@paginateChapterStreaming
                val lastIdx = chapter.lastIndex
                if (lastIdx >= 0) {
                    uiState.value = uiState.value.copy(
                        pageIndex = lastIdx,
                        currentPage = chapter.getPage(lastIdx),
                    )
                }
            }
            saveReadingProgress(immediate = true)
            preloadAdjacentChapters(content, safeIndex)
            onChapterLoaded()
        }
    }

    // ── 进度持久化 ──────────────────────────────────────────────

    /**
     * 保存阅读进度到数据库。
     * @param immediate true = 立即保存（翻章/退出），false = 防抖保存（翻页）
     */
    fun saveReadingProgress(immediate: Boolean) {
        val state = uiState.value
        val page = state.currentPage ?: return
        val bookId = state.bookId
        if (bookId == 0L) return

        val chapterPos = page.startCharOffset
        val chapterTitle = state.chapterTitle

        if (immediate) {
            readingStateManager().saveReadNow(bookId, state.chapterIndex, chapterPos, chapterTitle)
        } else {
            readingStateManager().saveReadDebounced(bookId, state.chapterIndex, chapterPos, chapterTitle)
        }

        scope.launch(Dispatchers.IO) {
            val queryRepo = bookQueryRepository
            val progressRepo = readingProgressRepository
            if (queryRepo != null && progressRepo != null) {
                val chapters = loadedBookContentProvider()?.chapters ?: emptyList()
                val chapterByteStart = chapters.getOrNull(state.chapterIndex)?.byteStart ?: 0L
                val relativeByte = charToByteOffset(chapterPos)
                val absoluteByteOffset = chapterByteStart + relativeByte

                val progress = if (isCurrentBookEpubProvider()) {
                    val totalChapters = state.totalChapters.coerceAtLeast(1)
                    val chapterContentLength = state.currentChapter?.content?.length?.coerceAtLeast(1) ?: 1
                    val charOffsetRatio = (chapterPos.toFloat() / chapterContentLength).coerceIn(0f, 1f)
                    ((state.chapterIndex + charOffsetRatio) / totalChapters).coerceIn(0f, 1f)
                } else {
                    val book = queryRepo.getBookById(bookId).first()
                    computeDisplayProgress(absoluteByteOffset, book)
                }

                progressRepo.updateReadingPositionAndMaybeFinish(
                    bookId = bookId,
                    byteOffset = absoluteByteOffset,
                    chapterTitle = chapterTitle,
                    progress = progress,
                )
            }
        }
    }

    fun persistReadingPosition(bookId: Long, elapsedMs: Long) {
        val dao = readingProgressDao ?: return
        if (bookId == 0L || elapsedMs < 1000L) return
        val snapshotChapterIndex = uiState.value.chapterIndex
        val snapshotThemeBg = uiState.value.themeColors.backgroundColor
        scope.launch(Dispatchers.IO) {
            dao.updateProgress(
                bookId = bookId,
                pageIndex = uiState.value.pageIndex,
                position = uiState.value.currentPage?.startCharOffset ?: 0,
                updatedTime = System.currentTimeMillis(),
                chapterIndex = snapshotChapterIndex,
                themeBackgroundColor = snapshotThemeBg,
            )
        }
    }

    // ── 释放资源 ──────────────────────────────────────────────

    fun releaseResources() {
        saveReadingProgress(immediate = true)
        flushChapterTime()
        readingStateManager().endSession()
        readingStateManager().cancel()
        chapterJob?.cancel()
    }

    // ── 内部辅助 ──────────────────────────────────────────────

    private suspend fun loadChapterContent(
        contentRepository: BookContentRepository,
        book: BookEntity,
        chapterIndexList: List<BookChapterEntity>,
        chapterIndex: Int,
    ): Pair<BookContent, DecodedSegment?> = withContext(Dispatchers.IO) {
        val file = File(book.filePath)
        val chapter = chapterIndexList.getOrNull(chapterIndex)

        if (chapter == null) {
            return@withContext BookContent(
                title = book.title,
                author = book.author,
                encoding = "UTF-8",
                totalLength = 0L,
                chapters = emptyList(),
                content = "",
                bookId = book.id,
            ) to null
        }

        val chapters = chapterIndexList.map { ch ->
            Chapter(
                title = ch.title,
                byteStart = ch.byteStart,
                byteEnd = ch.byteEnd,
                spineIndex = ch.spineIndex,
            )
        }

        if (isCurrentBookEpubProvider()) {
            val chapterText = contentRepository.getChapterText(file, chapterIndex, chapters)
            BookContent(
                title = book.title,
                author = book.author,
                encoding = chapter.charset,
                totalLength = chapterText.length.toLong(),
                chapters = chapters,
                content = chapterText,
                bookId = book.id,
            ) to null
        } else {
            val segment = contentRepository.loadChapterText(book.id, chapterIndex)
            val chapterText = segment?.text ?: ""
            BookContent(
                title = book.title,
                author = book.author,
                encoding = chapter.charset,
                totalLength = chapterText.length.toLong(),
                chapters = chapters,
                content = chapterText,
                bookId = book.id,
            ) to segment
        }
    }

    private fun computeDisplayProgress(byteOffset: Long, book: BookEntity?): Float {
        val fileSize = book?.fileSize?.coerceAtLeast(1L) ?: return 0f
        val estimatedTotalChars = book.estimatedTotalChars
        if (estimatedTotalChars <= 0L) {
            return (byteOffset.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f)
        }
        val avgBpc = fileSize.toFloat() / estimatedTotalChars.toFloat()
        val estimatedCharPos = byteOffset.toFloat() / avgBpc
        return (estimatedCharPos / estimatedTotalChars.toFloat()).coerceIn(0f, 1f)
    }

    private fun openFallbackBook(bookId: Long) {
        uiState.value = uiState.value.copy(
            isLoading = false,
            bookTitle = "Book $bookId",
            chapterTitle = "Chapter 1",
            totalChapters = 10,
            chapterTitles = (1..10).map { "Chapter $it" },
            chapterWordCounts = (1..10).map { (it * 1500) + 500 },
        )
    }

    private fun openFallbackChapter(index: Int) {
        uiState.value = uiState.value.copy(
            isLoading = false,
            chapterIndex = index,
            chapterTitle = "Chapter ${index + 1}",
            pageIndex = 0,
        )
    }

    private fun computeChapterWordCounts(chapterEntities: List<BookChapterEntity>) {
        scope.launch(Dispatchers.Default) {
            val counts = chapterEntities.map { it.wordCount.coerceAtLeast(0) }
            uiState.value = uiState.value.copy(chapterWordCounts = counts)
        }
    }

    private fun BookContent.normalizedChapters(): List<Chapter> {
        if (chapters.isNotEmpty()) return chapters
        return if (content.isNotBlank()) {
            listOf(Chapter(title = "Full Text", byteStart = 0L, byteEnd = content.length.toLong()))
        } else {
            emptyList()
        }
    }

    // ── 章节阅读统计辅助 ──────────────────────────────────────

    /** 加载章节阅读统计到 uiState */
    private fun loadChapterStats(bookId: Long) {
        val dao = chapterReadingStatsDao ?: return
        scope.launch {
            dao.getStatsByBookId(bookId).collect { stats ->
                uiState.value = uiState.value.copy(chapterStats = stats)
            }
        }
    }

    /** 标记章节已访问，开始计时 */
    private fun markChapterVisited(bookId: Long, chapterIndex: Int) {
        val dao = chapterReadingStatsDao ?: return
        lastActiveChapterIndex = chapterIndex
        chapterStartTimestamp = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            dao.markVisitedOrCreate(bookId, chapterIndex)
        }
    }

    /** 累计当前章节的阅读时间到数据库，重置计时起点 */
    internal fun flushChapterTime() {
        val dao = readingSessionDao ?: return
        val bookId = uiState.value.bookId
        val chapterIndex = lastActiveChapterIndex
        val startTime = chapterStartTimestamp
        if (bookId == 0L || chapterIndex < 0 || startTime == 0L) return
        val now = System.currentTimeMillis()
        val elapsedSeconds = (now - startTime) / 1000L
        if (elapsedSeconds < 1L) return
        val calendar = java.util.Calendar.getInstance()
        val dateKey = calendar.get(java.util.Calendar.YEAR) * 10000 +
            (calendar.get(java.util.Calendar.MONTH) + 1) * 100 +
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        scope.launch(Dispatchers.IO) {
            dao.insert(
                com.shuli.reader.core.database.entity.ReadingSessionEntity(
                    bookId = bookId,
                    chapterIndex = chapterIndex,
                    startedAt = startTime,
                    endedAt = now,
                    durationSeconds = elapsedSeconds,
                    dateKey = dateKey,
                    hour = hour,
                ),
            )
        }
        chapterStartTimestamp = System.currentTimeMillis()
    }

    /** 重置计时起点（从暂停恢复时使用，跳过暂停时段） */
    internal fun resetChapterStartTimestamp() {
        chapterStartTimestamp = System.currentTimeMillis()
    }
}
