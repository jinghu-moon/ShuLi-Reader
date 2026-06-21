package com.shuli.reader.feature.reader.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * 编辑器覆盖层容器
 *
 * 编排所有编辑子组件的层叠关系，作为 ReaderScreen 的子组件嵌入。
 * 参考 edit-interface-demo.html 的 Z-Index 层级：
 * - Z=99: 查找历史下拉菜单
 * - Z=100: 顶部悬浮工具栏
 * - Z=110: 遮罩层
 * - Z=120: 编辑记录面板
 * - Z=130: 全书查找侧边栏
 * - Z=200: 退出保护对话框
 */
@Composable
fun EditorOverlay(
    editViewModel: TextEditViewModel,
    chapterIndex: Int,
    chapterTitles: List<String>,
    getCurrentChapterText: () -> String,
    getChapterText: suspend (Int) -> String,
    onSave: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by editViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // 计算编辑数量
    val editCount = uiState.editState.patchCount

    Box(modifier = modifier.fillMaxSize()) {
        // 层 1: 悬浮工具栏 (z=100)
        EditorFloatingToolbar(
            uiState = uiState,
            onFindTextChange = { editViewModel.updateFindText(it) },
            onReplaceTextChange = { editViewModel.updateReplaceText(it) },
            onFind = {
                if (uiState.findScope == TextEditViewModel.FindScope.CHAPTER) {
                    // 本章查找
                    val chapterText = getCurrentChapterText()
                    editViewModel.findInChapter(chapterText, chapterIndex)
                } else {
                    // 全书查找
                    editViewModel.startBookSearch(
                        getChapterText = getChapterText,
                        chapterTitles = chapterTitles,
                    )
                }
            },
            onFindNext = { editViewModel.nextMatch() },
            onFindPrev = { editViewModel.prevMatch() },
            onReplace = {
                coroutineScope.launch {
                    editViewModel.replaceCurrent(chapterIndex)
                }
            },
            onReplaceAll = {
                coroutineScope.launch {
                    editViewModel.replaceAllInChapter(chapterIndex)
                }
            },
            onToggleRegex = { editViewModel.toggleRegex() },
            onToggleCaseSensitive = { editViewModel.toggleCaseSensitive() },
            onToggleReplace = { editViewModel.toggleReplace() },
            onToggleHistory = { editViewModel.toggleHistorySheet() },
            onToggleSidebar = { editViewModel.toggleSidebar() },
            onUndo = {
                coroutineScope.launch {
                    editViewModel.undo()
                }
            },
            onRedo = {
                coroutineScope.launch {
                    editViewModel.redo()
                }
            },
            onClose = { editViewModel.showExitDialog() },
        )

        // 层 2: 查找历史下拉 (z=99)
        val searchHistory by editViewModel.searchHistory.collectAsState()
        SearchHistoryDropdown(
            visible = uiState.showSearchHistory,
            history = searchHistory,
            onSelect = { word ->
                editViewModel.useHistoryWord(word)
                editViewModel.toggleSearchHistory()
            },
            onRemove = { editViewModel.removeHistoryItem(it) },
            onClearAll = { editViewModel.clearSearchHistory() },
        )

        // 层 3: 遮罩层 (z=110)
        if (uiState.showHistorySheet || uiState.showSidebar) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EditorTokens.Backdrop)
                    .clickable {
                        if (uiState.showHistorySheet) editViewModel.toggleHistorySheet()
                        if (uiState.showSidebar) editViewModel.toggleSidebar()
                    },
            )
        }

        // 层 4: 编辑记录面板 (z=120)
        EditHistorySheet(
            visible = uiState.showHistorySheet,
            patches = uiState.editState.let { editViewModel.editStore.patches },
            onUndo = {
                coroutineScope.launch {
                    editViewModel.undo()
                }
            },
            onClearAll = {
                coroutineScope.launch {
                    editViewModel.clearEdits()
                }
            },
            onSave = onSave,
            onClose = { editViewModel.toggleHistorySheet() },
        )

        // 层 5: 全书查找侧边栏 (z=130)
        SearchProgressSidebar(
            visible = uiState.showSidebar,
            chapterMatchCounts = uiState.chapterMatchCounts,
            chapterTitles = chapterTitles,
            scanProgress = uiState.scanProgress,
            totalMatches = uiState.matches.size,
            onClose = { editViewModel.toggleSidebar() },
            onChapterClick = { index ->
                // TODO: 跳转到指定章节
                editViewModel.toggleSidebar()
            },
        )

        // 层 6: 退出保护对话框 (z=200)
        ExitProtectionDialog(
            visible = uiState.showExitDialog,
            editCount = editCount,
            onSaveAndExit = {
                editViewModel.dismissExitDialog()
                onSave()
                onExit()
            },
            onDiscardAndExit = {
                coroutineScope.launch {
                    editViewModel.clearEdits()
                }
                editViewModel.dismissExitDialog()
                onExit()
            },
            onDismiss = { editViewModel.dismissExitDialog() },
        )
    }
}
