package com.shuli.reader.feature.reader.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 文本编辑 ViewModel
 *
 * 管理查找/替换/撤销/重做状态
 */
class TextEditViewModel(
    private val editStore: EditStore,
) : ViewModel() {

    /** 编辑状态 */
    data class EditUiState(
        val findText: String = "",
        val replaceText: String = "",
        val matches: List<FindMatch> = emptyList(),
        val currentMatchIndex: Int = -1,
        val isRegex: Boolean = false,
        val isCaseSensitive: Boolean = false,
        val showReplace: Boolean = false,
        val showHistory: Boolean = false,
        val isSearching: Boolean = false,
        val editState: EditStore.EditState = EditStore.EditState(),
    )

    data class FindMatch(
        val chapterIndex: Int,
        val charStart: Int,
        val charEnd: Int,
        val contextText: String,
    )

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    init {
        // 观察 EditStore 状态
        viewModelScope.launch {
            editStore.editState.collect { editState ->
                _uiState.update { it.copy(editState = editState) }
            }
        }
    }

    /** 更新查找文本 */
    fun updateFindText(text: String) {
        _uiState.update { it.copy(findText = text) }
    }

    /** 更新替换文本 */
    fun updateReplaceText(text: String) {
        _uiState.update { it.copy(replaceText = text) }
    }

    /** 切换正则模式 */
    fun toggleRegex() {
        _uiState.update { it.copy(isRegex = !it.isRegex) }
    }

    /** 切换大小写敏感 */
    fun toggleCaseSensitive() {
        _uiState.update { it.copy(isCaseSensitive = !it.isCaseSensitive) }
    }

    /** 显示/隐藏替换栏 */
    fun toggleReplace() {
        _uiState.update { it.copy(showReplace = !it.showReplace) }
    }

    /** 显示/隐藏编辑历史 */
    fun toggleHistory() {
        _uiState.update { it.copy(showHistory = !it.showHistory) }
    }

    /** 在当前章节中查找 */
    fun findInChapter(chapterText: String, chapterIndex: Int) {
        val findText = _uiState.value.findText
        if (findText.isEmpty()) {
            _uiState.update { it.copy(matches = emptyList(), currentMatchIndex = -1) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }

            val matches = mutableListOf<FindMatch>()
            val isRegex = _uiState.value.isRegex
            val isCaseSensitive = _uiState.value.isCaseSensitive

            try {
                if (isRegex) {
                    val regex = if (isCaseSensitive) {
                        Regex(findText)
                    } else {
                        Regex(findText, RegexOption.IGNORE_CASE)
                    }
                    regex.findAll(chapterText).forEach { match ->
                        matches.add(FindMatch(
                            chapterIndex = chapterIndex,
                            charStart = match.range.first,
                            charEnd = match.range.last + 1,
                            contextText = chapterText.substring(
                                maxOf(0, match.range.first - 10),
                                minOf(chapterText.length, match.range.last + 11)
                            ),
                        ))
                    }
                } else {
                    var start = 0
                    while (true) {
                        val idx = if (isCaseSensitive) {
                            chapterText.indexOf(findText, start)
                        } else {
                            chapterText.indexOf(findText, start, ignoreCase = true)
                        }
                        if (idx < 0) break
                        matches.add(FindMatch(
                            chapterIndex = chapterIndex,
                            charStart = idx,
                            charEnd = idx + findText.length,
                            contextText = chapterText.substring(
                                maxOf(0, idx - 10),
                                minOf(chapterText.length, idx + findText.length + 10)
                            ),
                        ))
                        start = idx + 1
                    }
                }
            } catch (e: Exception) {
                // 正则表达式错误等
            }

            _uiState.update {
                it.copy(
                    matches = matches,
                    currentMatchIndex = if (matches.isNotEmpty()) 0 else -1,
                    isSearching = false,
                )
            }
        }
    }

    /** 跳转到下一个匹配 */
    fun nextMatch() {
        _uiState.update { s ->
            if (s.matches.isEmpty()) s
            else s.copy(currentMatchIndex = (s.currentMatchIndex + 1) % s.matches.size)
        }
    }

    /** 跳转到上一个匹配 */
    fun prevMatch() {
        _uiState.update { s ->
            if (s.matches.isEmpty()) s
            else s.copy(currentMatchIndex = (s.currentMatchIndex - 1 + s.matches.size) % s.matches.size)
        }
    }

    /** 替换当前匹配 */
    fun replaceCurrent(chapterIndex: Int, chapterText: String) {
        val s = _uiState.value
        val match = s.matches.getOrNull(s.currentMatchIndex) ?: return

        editStore.addSingle(EditDelta(
            chapterIndex = chapterIndex,
            charStart = match.charStart,
            charEnd = match.charEnd,
            newText = s.replaceText,
            originalText = s.findText,
        ))

        // 跳到下一个匹配
        nextMatch()
    }

    /** 全部替换（当前章节） */
    fun replaceAllInChapter(chapterIndex: Int, chapterText: String) {
        val s = _uiState.value
        if (s.matches.isEmpty()) return

        val batch = BatchEditDelta(
            chapterIndex = chapterIndex,
            findText = s.findText,
            replaceText = s.replaceText,
            ranges = s.matches.map { it.charStart..it.charEnd },
            isRegex = s.isRegex,
        )

        editStore.addBatch(batch)

        _uiState.update { it.copy(matches = emptyList(), currentMatchIndex = -1) }
    }

    /** 撤销 */
    fun undo() {
        editStore.undo()
    }

    /** 重做 */
    fun redo() {
        editStore.redo()
    }

    /** 清空编辑 */
    fun clearEdits() {
        editStore.clear()
    }
}
