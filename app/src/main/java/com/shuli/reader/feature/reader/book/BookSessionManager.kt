package com.shuli.reader.feature.reader.book

import com.shuli.reader.core.database.dao.ReadingProgressDao
import com.shuli.reader.core.database.entity.BookEntity
import com.shuli.reader.core.database.entity.ReadingProgressEntity
import com.shuli.reader.core.reader.ReadingStateManager
import com.shuli.reader.core.repository.BookRepository
import com.shuli.reader.feature.reader.ReaderUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 阅读会话管理器。
 *
 * 职责：阅读进度持久化、阅读时间统计、
 *       阅读会话生命周期管理。
 *
 * 通过 [uiState] 读写共享状态，不反向依赖 ViewModel。
 */
class BookSessionManager(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val bookRepository: BookRepository?,
    private val readingProgressDao: ReadingProgressDao?,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "BookSessionMgr"
    }

    // ── 回调（由 ViewModel 注入）────────────────────────────────────

    /** 获取当前书籍内容的章节数 */
    var onGetLoadedBookContent: (() -> com.shuli.reader.core.parser.model.BookContent?)? = null

    /** 字符偏移转字节偏移 */
    var onCharToByteOffset: ((Int) -> Int)? = null

    /** 是否为 EPUB 格式 */
    var onIsCurrentBookEpub: (() -> Boolean)? = null

    // ── 内部状态 ──────────────────────────────────────────────────

    private lateinit var readingStateManager: ReadingStateManager

    /** 初始化阅读状态管理器（在 ViewModel init 时调用） */
    fun initialize() {
        readingStateManager = ReadingStateManager(
            scope = scope,
            saveAction = { /* 防抖回调，实际持久化由 saveReadingProgress 处理 */ },
        )
    }

    // ── 公开 API ──────────────────────────────────────────────────

    /** R7: 暂停阅读会话（进入后台、锁屏） */
    fun pauseReadingSession() {
        if (::readingStateManager.isInitialized) {
            readingStateManager.pauseSession()
        }
    }

    /** R7: 恢复阅读会话（回到前台） */
    fun resumeReadingSession() {
        if (::readingStateManager.isInitialized) {
            readingStateManager.resumeSession()
        }
    }

    /** 开始新的阅读会话 */
    fun startSession() {
        if (::readingStateManager.isInitialized) {
            readingStateManager.startSession()
        }
    }

    /** 结束当前阅读会话，返回会话时长 */
    fun endSession(): Long {
        return if (::readingStateManager.isInitialized) {
            readingStateManager.endSession()
        } else 0L
    }

    /** 取消阅读状态管理器 */
    fun cancel() {
        if (::readingStateManager.isInitialized) {
            readingStateManager.cancel()
        }
    }

    /**
     * R7: 保存阅读进度到数据库
     * @param immediate true 表示立即保存（翻章、退出），false 表示防抖保存（翻页）
     */
    fun saveReadingProgress(immediate: Boolean) {
        val state = uiState.value
        val page = state.currentPage ?: return
        val bookId = state.bookId
        if (bookId == 0L) return

        val chapterPos = page.startCharOffset
        val chapterTitle = state.chapterTitle

        // v4: ReadingStateManager 内部仍用 charPos 做防抖计时
        if (immediate) {
            readingStateManager.saveReadNow(bookId, state.chapterIndex, chapterPos, chapterTitle)
        } else {
            readingStateManager.saveReadDebounced(bookId, state.chapterIndex, chapterPos, chapterTitle)
        }

        // v4: 通过 utf16IndexToByte 映射将 charOffset 转为 byteOffset，写入 BookEntity
        scope.launch(Dispatchers.IO) {
            bookRepository?.let { repo ->
                val chapters = onGetLoadedBookContent?.invoke()?.chapters ?: emptyList()
                val chapterByteStart = chapters.getOrNull(state.chapterIndex)?.byteStart ?: 0L
                val relativeByte = onCharToByteOffset?.invoke(chapterPos) ?: 0
                val absoluteByteOffset = chapterByteStart + relativeByte

                val progress = if (onIsCurrentBookEpub?.invoke() == true) {
                    val totalChapters = state.totalChapters.coerceAtLeast(1)
                    val chapterContentLength = state.currentChapter?.content?.length?.coerceAtLeast(1) ?: 1
                    val charOffsetRatio = (chapterPos.toFloat() / chapterContentLength).coerceIn(0f, 1f)
                    ((state.chapterIndex + charOffsetRatio) / totalChapters).coerceIn(0f, 1f)
                } else {
                    // TXT: 优先用 estimatedTotalChars 估算字符%，未完成退化为字节%
                    val book = repo.getBookById(bookId).first()
                    computeDisplayProgress(absoluteByteOffset, book)
                }

                repo.updateReadingPosition(
                    bookId = bookId,
                    byteOffset = absoluteByteOffset,
                    chapterTitle = chapterTitle,
                    progress = progress,
                )
            }
        }
    }

    /** 持久化阅读时间 */
    fun persistReadingTime(bookId: Long, elapsedMs: Long) {
        val dao = readingProgressDao ?: return
        if (bookId == 0L || elapsedMs < 1000L) return
        val elapsedSeconds = elapsedMs / 1000L
        scope.launch(Dispatchers.IO) {
            val existing = dao.getReadingDurationByBookId(bookId)
            if (existing != null) {
                dao.updateProgress(
                    bookId = bookId,
                    pageIndex = uiState.value.pageIndex,
                    position = uiState.value.currentPage?.startCharOffset ?: 0,
                    readTime = existing + elapsedSeconds,
                    updatedTime = System.currentTimeMillis(),
                )
            } else {
                dao.insertProgress(
                    ReadingProgressEntity(
                        bookId = bookId,
                        pageIndex = uiState.value.pageIndex,
                        position = uiState.value.currentPage?.startCharOffset ?: 0,
                        readTime = elapsedSeconds,
                        updatedTime = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    // ── 内部：进度计算 ────────────────────────────────────────────

    /**
     * 计算阅读进度百分比（TXT 专用）
     * @param byteOffset 当前字节偏移
     * @param book 书籍实体（含 fileSize、estimatedTotalChars）
     */
    private fun computeDisplayProgress(byteOffset: Long, book: BookEntity?): Float {
        val fileSize = book?.fileSize?.coerceAtLeast(1L) ?: return 0f
        val estimatedTotalChars = book.estimatedTotalChars
        if (estimatedTotalChars <= 0L) {
            // 章节扫描未完成 → 退化为字节%
            return (byteOffset.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f)
        }
        // 用平均 bytesPerChar 估算字符位置
        val avgBpc = fileSize.toFloat() / estimatedTotalChars.toFloat()
        val estimatedCharPos = byteOffset.toFloat() / avgBpc
        return (estimatedCharPos / estimatedTotalChars.toFloat()).coerceIn(0f, 1f)
    }

    // ── 内部：ReadingStateManager 回调处理 ────────────────────────

    /** 释放资源（ViewModel.onCleared 时调用） */
    fun release() {
        if (::readingStateManager.isInitialized) {
            readingStateManager.cancel()
        }
    }
}
