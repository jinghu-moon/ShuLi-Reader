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
        val findScope: FindScope = FindScope.CHAPTER,
        val searchProgress: String = "",
        val editState: EditStore.EditState = EditStore.EditState(),
    )

    data class FindMatch(
        val chapterIndex: Int,
        val charStart: Int,
        val charEnd: Int,
        val contextText: String,
        val chapterTitle: String = "",
    )

    /** 查找范围 */
    enum class FindScope {
        CHAPTER,  // 当前章节
        BOOK,     // 全书
    }

    /** 查找历史记录 */
    data class FindHistory(
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    /** 查找历史（最近 20 条） */
    private val findHistory = mutableListOf<FindHistory>()

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

    /** 切换查找范围（本章/全书） */
    fun toggleFindScope() {
        _uiState.update {
            it.copy(findScope = if (it.findScope == FindScope.CHAPTER) FindScope.BOOK else FindScope.CHAPTER)
        }
    }

    /** 获取查找历史 */
    fun getFindHistory(): List<FindHistory> = findHistory.toList()

    /** 添加查找历史 */
    private fun addToHistory(text: String) {
        if (text.isBlank()) return
        findHistory.removeAll { it.text == text }
        findHistory.add(0, FindHistory(text))
        if (findHistory.size > 20) {
            findHistory.removeAt(findHistory.lastIndex)
        }
    }

    /** 在当前章节中查找 */
    fun findInChapter(chapterText: String, chapterIndex: Int, chapterTitle: String = "") {
        val findText = _uiState.value.findText
        if (findText.isEmpty()) {
            _uiState.update { it.copy(matches = emptyList(), currentMatchIndex = -1) }
            return
        }

        addToHistory(findText)

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }

            val matches = findMatchesInText(chapterText, chapterIndex, chapterTitle)

            _uiState.update {
                it.copy(
                    matches = matches,
                    currentMatchIndex = if (matches.isNotEmpty()) 0 else -1,
                    isSearching = false,
                )
            }
        }
    }

    /**
     * 全书查找
     *
     * @param chapters 章节列表 (chapterIndex, chapterText, chapterTitle)
     * @param onProgress 进度回调
     */
    fun findInBook(
        chapters: List<Triple<Int, String, String>>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ) {
        val findText = _uiState.value.findText
        if (findText.isEmpty()) {
            _uiState.update { it.copy(matches = emptyList(), currentMatchIndex = -1) }
            return
        }

        addToHistory(findText)

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchProgress = "搜索中...") }

            val allMatches = mutableListOf<FindMatch>()
            val total = chapters.size

            for ((index, triple) in chapters.withIndex()) {
                val (chapterIndex, chapterText, chapterTitle) = triple
                val matches = findMatchesInText(chapterText, chapterIndex, chapterTitle)
                allMatches.addAll(matches)

                _uiState.update {
                    it.copy(searchProgress = "搜索中 ${index + 1}/$total...")
                }
                onProgress(index + 1, total)
            }

            _uiState.update {
                it.copy(
                    matches = allMatches,
                    currentMatchIndex = if (allMatches.isNotEmpty()) 0 else -1,
                    isSearching = false,
                    searchProgress = if (allMatches.isEmpty()) "未找到匹配" else "找到 ${allMatches.size} 个匹配",
                )
            }
        }
    }

    /** 在文本中查找匹配 */
    private fun findMatchesInText(
        text: String,
        chapterIndex: Int,
        chapterTitle: String,
    ): List<FindMatch> {
        val findText = _uiState.value.findText
        val isRegex = _uiState.value.isRegex
        val isCaseSensitive = _uiState.value.isCaseSensitive
        val matches = mutableListOf<FindMatch>()

        try {
            if (isRegex) {
                val regex = if (isCaseSensitive) {
                    Regex(findText)
                } else {
                    Regex(findText, RegexOption.IGNORE_CASE)
                }
                regex.findAll(text).forEach { match ->
                    matches.add(FindMatch(
                        chapterIndex = chapterIndex,
                        charStart = match.range.first,
                        charEnd = match.range.last + 1,
                        contextText = text.substring(
                            maxOf(0, match.range.first - 10),
                            minOf(text.length, match.range.last + 11)
                        ),
                        chapterTitle = chapterTitle,
                    ))
                }
            } else {
                var start = 0
                while (true) {
                    val idx = if (isCaseSensitive) {
                        text.indexOf(findText, start)
                    } else {
                        text.indexOf(findText, start, ignoreCase = true)
                    }
                    if (idx < 0) break
                    matches.add(FindMatch(
                        chapterIndex = chapterIndex,
                        charStart = idx,
                        charEnd = idx + findText.length,
                        contextText = text.substring(
                            maxOf(0, idx - 10),
                            minOf(text.length, idx + findText.length + 10)
                        ),
                        chapterTitle = chapterTitle,
                    ))
                    start = idx + 1
                }
            }
        } catch (e: Exception) {
            // 正则表达式错误等
        }

        return matches
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

        viewModelScope.launch {
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
    }

    /** 全部替换（当前章节） */
    fun replaceAllInChapter(chapterIndex: Int, chapterText: String) {
        val s = _uiState.value
        if (s.matches.isEmpty()) return

        viewModelScope.launch {
            val batch = BatchEditDelta(
                chapterIndex = chapterIndex,
                findText = s.findText,
                replaceText = s.replaceText,
                ranges = s.matches.map { it.charStart until it.charEnd },  // 使用 until 保持 exclusive
                isRegex = s.isRegex,
            )

            editStore.addBatch(batch)

            _uiState.update { it.copy(matches = emptyList(), currentMatchIndex = -1) }
        }
    }

    /** 撤销 */
    fun undo() {
        viewModelScope.launch {
            editStore.undo()
        }
    }

    /** 重做 */
    fun redo() {
        viewModelScope.launch {
            editStore.redo()
        }
    }

    /** 清空编辑 */
    fun clearEdits() {
        viewModelScope.launch {
            editStore.clear()
        }
    }
}
