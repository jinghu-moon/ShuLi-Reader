package com.shuli.reader.feature.reader

import com.shuli.reader.core.reader.animation.PageDelegate
import com.shuli.reader.core.reader.animation.PageDelegateFactory
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.SelectionRange
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
 * 阅读器导航协调：翻页、scrub、工具栏/浮层切换、搜索开关。
 *
 * 从 ReaderViewModel 拆出，SRP —— 只负责"用户导航交互"这一变更轴。
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
internal class ReaderNavigationCoordinator(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val appContext: android.content.Context?,
    private val stringResolver: () -> com.shuli.reader.core.i18n.AppStrings,
    // ── 回调 ──
    private val saveReadingProgress: (Boolean) -> Unit,
    private val openChapter: (Int, Boolean, Long) -> Unit,
    private val clearSearchResults: () -> Unit,
) {

    companion object {
        private const val TOOLBAR_AUTO_HIDE_DELAY_MS = 5000L
    }

    private var toolbarAutoHideJob: Job? = null

    private val scrubChannel = Channel<Int>(Channel.CONFLATED)

    init {
        scope.launch {
            scrubChannel.consumeAsFlow()
                .sample(80.milliseconds)
                .collect { pageIndex -> emitScrubFrame(pageIndex) }
        }
    }

    // ── 翻页 ──────────────────────────────────────────────

    fun nextPage() {
        val state = uiState.value
        val chapter = state.currentChapter ?: return

        if (state.pageIndex < chapter.lastIndex) {
            uiState.value = state.copy(
                pageIndex = state.pageIndex + 1,
                currentPage = chapter.getPage(state.pageIndex + 1),
            )
            saveReadingProgress(false)
        } else if (state.chapterIndex < state.totalChapters - 1) {
            openChapter(state.chapterIndex + 1, false, -1L)
        } else {
            appContext?.let {
                android.widget.Toast.makeText(it, stringResolver().reader.alreadyLastPage, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun prevPage() {
        val state = uiState.value
        val chapter = state.currentChapter ?: return

        if (state.pageIndex > 0) {
            uiState.value = state.copy(
                pageIndex = state.pageIndex - 1,
                currentPage = chapter.getPage(state.pageIndex - 1),
            )
            saveReadingProgress(false)
        } else if (state.chapterIndex > 0) {
            openChapter(state.chapterIndex - 1, true, -1L)
        } else {
            appContext?.let {
                android.widget.Toast.makeText(it, stringResolver().reader.alreadyFirstPage, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun jumpToPage(pageIndex: Int) {
        val chapter = uiState.value.currentChapter ?: return
        val safe = pageIndex.coerceIn(0, chapter.lastIndex)
        if (safe == uiState.value.pageIndex) return

        uiState.value = uiState.value.copy(
            pageIndex = safe,
            currentPage = chapter.getPage(safe),
            pageRenderMode = PageRenderMode.JUMP,
            selectedRange = null,
        )
        saveReadingProgress(true)
        scope.launch {
            delay(16)
            uiState.value = uiState.value.copy(pageRenderMode = PageRenderMode.SEQUENTIAL)
        }
    }

    // ── Scrub（进度条拖动） ──────────────────────────────────────

    fun startPageScrub() {
        uiState.value = uiState.value.copy(pageRenderMode = PageRenderMode.SCRUBBING)
    }

    fun scrubToPage(pageIndex: Int) {
        val chapter = uiState.value.currentChapter ?: return
        val safe = pageIndex.coerceIn(0, chapter.lastIndex)
        uiState.value = uiState.value.copy(pageIndex = safe)
        scrubChannel.trySend(safe)
    }

    private fun emitScrubFrame(pageIndex: Int) {
        val chapter = uiState.value.currentChapter ?: return
        uiState.value = uiState.value.copy(currentPage = chapter.getPage(pageIndex))
    }

    fun commitPageScrub() {
        val state = uiState.value
        val pi = state.pageIndex
        val chapter = state.currentChapter ?: return
        uiState.value = state.copy(
            currentPage = chapter.getPage(pi),
            pageRenderMode = PageRenderMode.SEQUENTIAL,
        )
        saveReadingProgress(true)
    }

    // ── 工具栏 ──────────────────────────────────────────────

    fun toggleToolbar() {
        toolbarAutoHideJob?.cancel()
        val showing = !uiState.value.showToolbar
        uiState.value = uiState.value.copy(
            showToolbar = showing,
            overlayPanel = OverlayPanel.NONE,
        )
        if (showing) startToolbarAutoHide()
    }

    private fun startToolbarAutoHide() {
        toolbarAutoHideJob?.cancel()
        toolbarAutoHideJob = scope.launch {
            delay(TOOLBAR_AUTO_HIDE_DELAY_MS)
            uiState.value = uiState.value.copy(showToolbar = false)
        }
    }

    fun resetToolbarAutoHide() {
        if (uiState.value.showToolbar) startToolbarAutoHide()
    }

    // ── 浮层面板 ──────────────────────────────────────────────

    fun toggleDirectory() = toggleOverlay(OverlayPanel.DIRECTORY)

    fun toggleQuickSettings() = toggleOverlay(OverlayPanel.QUICK_SETTINGS)

    private fun toggleOverlay(panel: OverlayPanel) {
        resetToolbarAutoHide()
        val current = uiState.value.overlayPanel
        uiState.value = uiState.value.copy(
            overlayPanel = if (current == panel) OverlayPanel.NONE else panel
        )
    }

    fun toggleMenu() {
        uiState.value = uiState.value.copy(showMenu = !uiState.value.showMenu)
    }

    fun toggleSearch() {
        val showing = !uiState.value.showSearch
        uiState.value = uiState.value.copy(
            showSearch = showing,
            overlayPanel = OverlayPanel.NONE,
        )
        if (!showing) clearSearchResults()
    }

    // ── 翻页动画 ──────────────────────────────────────────────

    fun setPageAnimType(type: PageDelegateFactory.PageAnimType, setPageDelegate: (PageDelegate) -> Unit) {
        uiState.value = uiState.value.copy(pageAnimType = type)
        setPageDelegate(PageDelegateFactory.create(type))
    }

    fun handlePageDirection(direction: PageDelegate.Direction) {
        when (direction) {
            PageDelegate.Direction.NEXT -> nextPage()
            PageDelegate.Direction.PREV -> prevPage()
            PageDelegate.Direction.NONE -> { /* 忽略 */ }
        }
    }

    // ── 文本选择 ──────────────────────────────────────────────

    fun selectText(range: SelectionRange) {
        uiState.value = uiState.value.copy(selectedRange = range)
        uiState.value.currentPage?.invalidate()
    }

    fun clearTextSelection() {
        uiState.value = uiState.value.copy(selectedRange = null)
        uiState.value.currentPage?.invalidate()
    }

    // ── 释放 ──────────────────────────────────────────────

    fun release() {
        toolbarAutoHideJob?.cancel()
        toolbarAutoHideJob = null
    }
}
