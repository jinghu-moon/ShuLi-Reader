package com.shuli.reader.feature.reader.book

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.database.entity.BookChapterEntity
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.parser.DecodedSegment
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.core.parser.model.Chapter
import com.shuli.reader.core.reader.Paginator
import com.shuli.reader.core.reader.cache.CacheManager
import com.shuli.reader.core.repository.BookRepository
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.feature.reader.pagination.ChapterPaginationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 书籍加载协调器。
 *
 * 职责：书籍打开流程、章节内容加载、章节切换、fallback 处理。
 *
 * 通过 [uiState] 读写共享状态，不反向依赖 ViewModel。
 */
class BookLoadingCoordinator(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val bookRepository: BookRepository?,
    private val paginator: Paginator,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "BookLoadingCoord"
    }

    // ── 回调（由 ViewModel 注入）────────────────────────────────────

    /** 获取当前书籍文件路径 */
    var onGetCurrentBookFilePath: (() -> String?)? = null

    /** 获取当前书籍是否为 EPUB */
    var onIsCurrentBookEpub: (() -> Boolean)? = null

    /** 设置当前书籍文件路径 */
    var onSetCurrentBookFilePath: ((String?) -> Unit)? = null

    /** 设置当前书籍是否为 EPUB */
    var onSetIsCurrentBookEpub: ((Boolean) -> Unit)? = null

    /** 获取已加载的书籍内容 */
    var onGetLoadedBookContent: (() -> BookContent?)? = null

    /** 设置已加载的书籍内容 */
    var onSetLoadedBookContent: ((BookContent?) -> Unit)? = null

    /** 设置当前章节的 utf16IndexToByte 映射 */
    var onSetCurrentChapterUtf16Map: ((IntArray) -> Unit)? = null

    /** 设置缓存的章节文本 */
    var onSetCachedChapterText: ((String?) -> Unit)? = null

    /** 获取当前章节 utf16IndexToByte 映射 */
    var onGetCurrentChapterUtf16Map: (() -> IntArray)? = null

    /** 章节内相对字节偏移转 UTF-16 char index */
    var onByteToCharOffset: ((Int) -> Int)? = null

    /** 缓存管理器（由 ViewModel 在 openBook 时更新） */
    var cacheManager: CacheManager = CacheManager.forMemoryClass(256)

    /** 分页协调器引用 */
    var paginationCoordinator: ChapterPaginationCoordinator? = null

    /** 章节加载 Job（防止快速连续调用导致多个 openChapter 并发） */
    var chapterJob: kotlinx.coroutines.Job? = null

    /** 当前排版参数哈希值 */
    var currentLayoutHash: String = ""

    // ── 回调（由 ViewModel 注入，用于集成其他 Manager）────────────

    /** 结束上一次阅读会话，返回已用时长 */
    var onEndSession: (() -> Long)? = null

    /** 持久化阅读时间 */
    var onPersistReadingTime: ((Long, Long) -> Unit)? = null

    /** 开始新阅读会话 */
    var onStartSession: (() -> Unit)? = null

    /** 加载书签 */
    var onLoadBookmarks: (() -> Unit)? = null

    /** 加载笔记 */
    var onLoadNotes: (() -> Unit)? = null

    /** 保存阅读进度 */
    var onSaveReadingProgress: ((Boolean) -> Unit)? = null

    /** TTS 跨章连续播放：章节加载完成后继续朗读 */
    var onTtsResumeAfterChapterLoad: (() -> Unit)? = null

    /** 性能诊断日志 */
    var onLogPerf: ((String, Long) -> Unit)? = null

    // ── 打开书籍 ──────────────────────────────────────────────────

    fun openBook(bookId: Long) {
        val perfStart = System.currentTimeMillis()

        // R7: 切换书籍时释放旧书籍缓存到 BookCacheStore
        val oldBookId = uiState.value.bookId
        if (oldBookId != 0L && oldBookId != bookId) {
            com.shuli.reader.core.reader.cache.BookCacheStore.releaseBook(oldBookId.toString())
        }

        // 从 BookCacheStore 获取当前书籍的缓存（可能复用之前的分页结果）
        cacheManager = com.shuli.reader.core.reader.cache.BookCacheStore.getBookCache(bookId.toString())

        // R7: 结束上一次阅读会话，开始新会话
        val sessionElapsed = onEndSession?.invoke() ?: 0L
        if (oldBookId != 0L && sessionElapsed > 0L) {
            onPersistReadingTime?.invoke(oldBookId, sessionElapsed)
        }

        scope.launch {
            uiState.value = uiState.value.copy(isLoading = true, error = null, bookId = bookId)

            try {
                val repository = bookRepository
                if (repository == null) {
                    openFallbackBook(bookId)
                    onLoadBookmarks?.invoke()
                    onLoadNotes?.invoke()
                    return@launch
                }

                // 1. 获取书籍元数据
                val dbStart = System.currentTimeMillis()
                val book = withContext(Dispatchers.IO) {
                    repository.getBookById(bookId).first()
                } ?: run {
                    uiState.value = uiState.value.copy(
                        isLoading = false,
                        error = "Book not found: $bookId",
                    )
                    return@launch
                }
                onLogPerf?.invoke("getBookById", dbStart)

                // 2. 确保章节目录索引存在（从 DB 加载或重新解析）
                val indexStart = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    repository.ensureChapterIndex(bookId)
                }
                val chapterIndexList = withContext(Dispatchers.IO) {
                    repository.getChapterIndex(bookId)
                }
                onLogPerf?.invoke("ensureChapterIndex [${book.fileType}]", indexStart)

                onSetCurrentBookFilePath?.invoke(book.filePath)
                onSetIsCurrentBookEpub?.invoke(book.fileType == "EPUB")

                val chapterCount = chapterIndexList.size
                if (chapterCount == 0) {
                    uiState.value = uiState.value.copy(isLoading = false)
                    return@launch
                }

                // v4: 通过 durByteOffset 查找当前章节（取代已删除的 durChapterIndex）
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
                    chapterWordCounts = emptyList(), // 延迟计算，不阻塞首屏
                )

                // 3. 加载当前章节内容（从 DB 索引按需读取），同时获取 utf16IndexToByte 映射
                val parseStart = System.currentTimeMillis()
                val (content, segment) = loadChapterContent(repository, book, chapterIndexList, chapterIndex)
                onSetLoadedBookContent?.invoke(content)
                onSetCurrentChapterUtf16Map?.invoke(segment?.utf16IndexToByte ?: IntArray(0))
                onSetCachedChapterText?.invoke(content.content.ifEmpty { null })
                onLogPerf?.invoke("loadChapterContent", parseStart)

                withContext(Dispatchers.IO) {
                    repository.updateLastReadTime(bookId)
                }
                onLoadBookmarks?.invoke()
                onLoadNotes?.invoke()
                onStartSession?.invoke()

                onLogPerf?.invoke("openBook.preparation", perfStart)

                // v4: 将 durByteOffset 转为章节内字符偏移，用于分页跳转
                val chapterByteStart = chapterIndexList[chapterIndex].byteStart
                val relativeByteOffset = (durByteOffset - chapterByteStart).toInt().coerceAtLeast(0)
                val targetCharOffset = onByteToCharOffset?.invoke(relativeByteOffset) ?: 0

                // 4. 流式分页：首页秒开，目标位置自动跳转
                val paginateStart = System.currentTimeMillis()
                // 计算布局哈希，用于持久化 chapterPageCounts
                currentLayoutHash = paginationCoordinator?.computeLayoutHash(uiState.value.readerPreferences) ?: ""
                chapterJob = paginationCoordinator?.paginateChapterStreaming(
                    content = content,
                    index = chapterIndex,
                    targetCharOffset = targetCharOffset,
                    onDone = {
                        onLogPerf?.invoke("firstPageReady", perfStart)
                        paginationCoordinator?.preloadAdjacentChapters(content, chapterIndex)
                        // TXT: 从 DB 章节索引直接计算字数
                        if (onIsCurrentBookEpub?.invoke() != true) {
                            paginationCoordinator?.computeChapterWordCounts(chapterIndexList)
                        }
                        // 加载已持久化的页数缓存（异步，不阻塞首屏）
                        scope.launch(Dispatchers.Default) {
                            val ctx = uiState.value.let { null } // appContext 通过 ViewModel 注入
                            // 注意：页数持久化加载由 ViewModel 直接处理
                        }
                    },
                )
            } catch (e: Exception) {
                uiState.value = uiState.value.copy(
                    isLoading = false,
                    error = e.message,
                )
            }
        }
    }

    /**
     * 根据 DB 章节索引加载当前章节内容。
     * TXT: 使用 byteStart/byteEnd 按需读取，返回 DecodedSegment（含 utf16IndexToByte）
     * EPUB: 使用 spineIndex 解析 XHTML
     */
    private suspend fun loadChapterContent(
        repository: BookRepository,
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

        if (onIsCurrentBookEpub?.invoke() == true) {
            val chapterText = repository.getChapterText(file, chapterIndex, chapters)
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
            val segment = repository.loadChapterText(book.id, chapterIndex)
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

    // ── 打开章节 ──────────────────────────────────────────────────

    fun openChapter(index: Int, targetToLastPage: Boolean = false, targetByteOffset: Long = -1L) {
        val content = onGetLoadedBookContent?.invoke()
        if (content == null) {
            android.util.Log.w(TAG, "openChapter[$index]: loadedBookContent 为 null，走 fallback")
            scope.launch { openFallbackChapter(index) }
            return
        }
        val normalizedChapters = paginationCoordinator?.run { content.normalizedChapters() } ?: emptyList()
        android.util.Log.d(TAG, "openChapter[$index]: loadedBookContent 非 null，章节数=${normalizedChapters.size}")

        val safeIndex = index.coerceIn(0, (normalizedChapters.size - 1).coerceAtLeast(0))

        // M1: 取消上一次章节加载，防止快速连续调用导致并发冲突
        chapterJob?.cancel()

        val targetCharOffset = if (targetToLastPage) {
            -1
        } else if (targetByteOffset >= 0) {
            -1
        } else {
            -1
        }

        chapterJob = paginationCoordinator?.paginateChapterStreaming(
            content = content,
            index = safeIndex,
            targetCharOffset = targetCharOffset,
            onDone = {
                // v4: 如果有 targetByteOffset，加载完章节后用映射转为 charOffset 再跳转
                if (targetByteOffset >= 0 && !targetToLastPage) {
                    val chapters = paginationCoordinator?.run { content.normalizedChapters() } ?: emptyList()
                    val chapterByteStart = chapters.getOrNull(safeIndex)?.byteStart ?: 0L
                    val relativeByte = (targetByteOffset - chapterByteStart).toInt().coerceAtLeast(0)
                    val charOffset = onByteToCharOffset?.invoke(relativeByte) ?: 0
                    val chapter = uiState.value.currentChapter
                    if (chapter != null && chapter.pageSize > 0) {
                        val pi = chapter.getPageIndexByCharIndex(charOffset)
                        uiState.value = uiState.value.copy(
                            pageIndex = pi,
                            currentPage = chapter.getPage(pi),
                        )
                    }
                }
                // targetToLastPage：分页完成后跳转到末页
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
                onSaveReadingProgress?.invoke(true)
                paginationCoordinator?.preloadAdjacentChapters(content, safeIndex)
                // TTS 跨章连续播放：章节加载完成后从首页继续朗读
                onTtsResumeAfterChapterLoad?.invoke()
            },
        )
    }

    // ── Fallback ──────────────────────────────────────────────────

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
}
