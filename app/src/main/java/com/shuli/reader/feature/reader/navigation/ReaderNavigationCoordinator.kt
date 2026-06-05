package com.shuli.reader.feature.reader.navigation

import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.parser.model.BookContent
import com.shuli.reader.feature.reader.OverlayPanel
import com.shuli.reader.feature.reader.ReaderUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 阅读器导航协调器。
 *
 * 职责：翻页、进度条 scrub、目录/工具栏/快速设置面板切换。
 * 通过 [uiState] 读写共享状态，不反向依赖 ViewModel。
 */
class ReaderNavigationCoordinator(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val appContext: android.content.Context?,
    private val stringResolver: () -> com.shuli.reader.core.i18n.AppStrings,
) {
    companion object {
        private const val TOOLBAR_AUTO_HIDE_DELAY_MS = 5000L
    }

    // ── 回调（由 ViewModel 注入）────────────────────────────────────

    /** 获取已加载的书籍内容 */
    var onGetLoadedBookContent: (() -> BookContent?)? = null

    /** 打开指定章节 */
    var onOpenChapter: ((Int, Boolean, Long) -> Unit)? = null

    /** 保存阅读进度 */
    var onSaveReadingProgress: ((Boolean) -> Unit)? = null

    /** 字节偏移转字符偏移 */
    var onByteToCharOffset: ((Int) -> Int)? = null

    /** 获取已归一化章节数 */
    var onGetNormalizedChapterCount: (() -> Int)? = null

    // ── 内部状态 ──────────────────────────────────────────────────

    private val scrubChannel = Channel<Int>(Channel.CONFLATED)
    private var toolbarAutoHideJob: Job? = null

    init {
        scope.launch {
            scrubChannel.consumeAsFlow()
                .sample(80.milliseconds)
                .collect { pageIndex -> emitScrubFrame(pageIndex) }
        }
    }

    // ── 翻页 ─────────────────────────────────────────────────────

    /** 下一页 */
    fun nextPage() {
        val state = uiState.value
        val chapter = state.currentChapter ?: return

        if (state.pageIndex < chapter.lastIndex) {
            uiState.value = state.copy(
                pageIndex = state.pageIndex + 1,
                currentPage = chapter.getPage(state.pageIndex + 1),
            )
            onSaveReadingProgress?.invoke(false)
        } else {
            val chapterCount = onGetNormalizedChapterCount?.invoke() ?: 0
            if (state.chapterIndex < chapterCount - 1) {
                onOpenChapter?.invoke(state.chapterIndex + 1, false, -1L)
            } else {
                appContext?.let {
                    android.widget.Toast.makeText(it, stringResolver().alreadyLastPage, android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** 上一页 */
    fun prevPage() {
        val state = uiState.value
        val chapter = state.currentChapter ?: return

        if (state.pageIndex > 0) {
            uiState.value = state.copy(
                pageIndex = state.pageIndex - 1,
                currentPage = chapter.getPage(state.pageIndex - 1),
            )
            onSaveReadingProgress?.invoke(false)
        } else if (state.chapterIndex > 0) {
            onOpenChapter?.invoke(state.chapterIndex - 1, true, -1L)
        } else {
            appContext?.let {
                android.widget.Toast.makeText(it, stringResolver().alreadyFirstPage, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 跳转到指定页码 */
    fun jumpToPage(pageIndex: Int) {
        val chapter = uiState.value.currentChapter ?: return
        val safe = pageIndex.coerceIn(0, chapter.lastIndex)
        if (safe == uiState.value.pageIndex) return

        uiState.value = uiState.value.copy(
            pageIndex = safe,
            currentPage = chapter.getPage(safe),
            pageRenderMode = PageRenderMode.JUMP,
            selectedRange = null,
            ttsActiveRange = null,
        )
        onSaveReadingProgress?.invoke(true)
        // 一帧后回到 SEQUENTIAL，让 View 自然预热邻页
        scope.launch {
            delay(16)
            uiState.value = uiState.value.copy(pageRenderMode = PageRenderMode.SEQUENTIAL)
        }
    }

    /** 跳转到指定章节的字节偏移位置（v4：统一入口） */
    fun jumpToChapterPosition(chapterIndex: Int, byteOffset: Long) {
        val state = uiState.value
        val chapter = state.currentChapter
        if (chapter?.chapterIndex == chapterIndex && chapter.pageSize > 0) {
            val content = onGetLoadedBookContent?.invoke()
            val chapters = content?.chapters
            val chapterByteStart = chapters?.getOrNull(chapterIndex)?.byteStart ?: 0L
            val relativeByte = (byteOffset - chapterByteStart).toInt().coerceAtLeast(0)
            val charOffset = onByteToCharOffset?.invoke(relativeByte) ?: 0
            val pi = chapter.getPageIndexByCharIndex(charOffset)
            jumpToPage(pi)
        } else {
            onOpenChapter?.invoke(chapterIndex, false, byteOffset)
        }
    }

    // ── 进度条 Scrub 接口 ──────────────────────────────────────

    fun startPageScrub() {
        uiState.value = uiState.value.copy(pageRenderMode = PageRenderMode.SCRUBBING)
    }

    fun scrubToPage(pageIndex: Int) {
        val chapter = uiState.value.currentChapter ?: return
        val safe = pageIndex.coerceIn(0, chapter.lastIndex)
        uiState.value = uiState.value.copy(pageIndex = safe)
        scrubChannel.trySend(safe)
    }

    fun commitPageScrub() {
        val state = uiState.value
        val pi = state.pageIndex
        val chapter = state.currentChapter ?: return
        uiState.value = state.copy(
            currentPage = chapter.getPage(pi),
            pageRenderMode = PageRenderMode.SEQUENTIAL,
        )
        onSaveReadingProgress?.invoke(true)
    }

    private fun emitScrubFrame(pageIndex: Int) {
        val chapter = uiState.value.currentChapter ?: return
        uiState.value = uiState.value.copy(currentPage = chapter.getPage(pageIndex))
    }

    // ── 工具栏与面板切换 ────────────────────────────────────────

    /** 显示/隐藏工具栏 */
    fun toggleToolbar() {
        toolbarAutoHideJob?.cancel()
        val showing = !uiState.value.showToolbar
        uiState.value = uiState.value.copy(
            showToolbar = showing,
            overlayPanel = OverlayPanel.NONE,
        )
        if (showing) {
            startToolbarAutoHide()
        }
    }

    /** 重置工具栏自动隐藏计时器 */
    fun resetToolbarAutoHide() {
        if (uiState.value.showToolbar) {
            startToolbarAutoHide()
        }
    }

    /** 显示/隐藏目录 */
    fun toggleDirectory() {
        toggleOverlay(OverlayPanel.DIRECTORY)
    }

    /** 显示/隐藏快速设置 */
    fun toggleQuickSettings() {
        toggleOverlay(OverlayPanel.QUICK_SETTINGS)
    }

    private fun startToolbarAutoHide() {
        toolbarAutoHideJob?.cancel()
        toolbarAutoHideJob = scope.launch {
            delay(TOOLBAR_AUTO_HIDE_DELAY_MS)
            uiState.value = uiState.value.copy(showToolbar = false)
        }
    }

    private fun toggleOverlay(panel: OverlayPanel) {
        resetToolbarAutoHide()
        val current = uiState.value.overlayPanel
        uiState.value = uiState.value.copy(
            overlayPanel = if (current == panel) OverlayPanel.NONE else panel
        )
    }

    /** 释放资源 */
    fun release() {
        toolbarAutoHideJob?.cancel()
        toolbarAutoHideJob = null
        scrubChannel.close()
    }
}
