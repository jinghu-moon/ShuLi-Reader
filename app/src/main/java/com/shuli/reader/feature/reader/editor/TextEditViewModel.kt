package com.shuli.reader.feature.reader.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑器 ViewModel — 唯一状态源
 *
 * 统一管理查找/替换/撤销/重做/全书查找/编辑记录等全部状态
 */
class TextEditViewModel(
    internal val editStore: EditStore,
) : ViewModel() {

    // ── 数据模型 ──────────────────────────────────────

    /** 查找匹配 */
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

    // ── UI 状态 ───────────────────────────────────────

    data class EditUiState(
        // 查找/替换
        val findText: String = "",
        val replaceText: String = "",
        val matches: List<FindMatch> = emptyList(),
        val currentMatchIndex: Int = -1,
        val isRegex: Boolean = false,
        val isCaseSensitive: Boolean = false,

        // 面板显示状态
        val showReplace: Boolean = false,
        val showHistorySheet: Boolean = false,
        val showSidebar: Boolean = false,
        val showSearchHistory: Boolean = false,
        val showExitDialog: Boolean = false,

        // 查找状态
        val isSearching: Boolean = false,
        val findScope: FindScope = FindScope.CHAPTER,
        val searchProgress: String = "",

        // 全书查找
        val chapterMatchCounts: Map<Int, Int?> = emptyMap(),
        val scanProgress: Float = 0f,

        // 编辑状态
        val editState: EditStore.EditState = EditStore.EditState(),
    )

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    /** 查找历史 */
    private val _searchHistory = MutableStateFlow<List<FindHistory>>(emptyList())
    val searchHistory: StateFlow<List<FindHistory>> = _searchHistory.asStateFlow()

    init {
        // 观察 EditStore 状态
        viewModelScope.launch {
            editStore.editState.collect { editState ->
                _uiState.update { it.copy(editState = editState) }
            }
        }
    }

    // ── 查找/替换操作 ────────────────────────────────

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

    /** 切换查找范围（本章/全书） */
    fun toggleFindScope() {
        _uiState.update {
            it.copy(findScope = if (it.findScope == FindScope.CHAPTER) FindScope.BOOK else FindScope.CHAPTER)
        }
    }

    /** 显示/隐藏编辑记录面板 */
    fun toggleHistorySheet() {
        _uiState.update { it.copy(showHistorySheet = !it.showHistorySheet) }
    }

    /** 显示/隐藏全书查找侧边栏 */
    fun toggleSidebar() {
        _uiState.update { it.copy(showSidebar = !it.showSidebar) }
    }

    /** 显示/隐藏查找历史 */
    fun toggleSearchHistory() {
        _uiState.update { it.copy(showSearchHistory = !it.showSearchHistory) }
    }

    /** 显示退出保护对话框 */
    fun showExitDialog() {
        _uiState.update { it.copy(showExitDialog = true) }
    }

    /** 隐藏退出保护对话框 */
    fun dismissExitDialog() {
        _uiState.update { it.copy(showExitDialog = false) }
    }

    // ── 查找操作 ─────────────────────────────────────

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

            val matches = withContext(Dispatchers.Default) {
                findMatchesInText(chapterText, chapterIndex, chapterTitle)
            }

            _uiState.update {
                it.copy(
                    matches = matches,
                    currentMatchIndex = if (matches.isNotEmpty()) 0 else -1,
                    isSearching = false,
                    searchProgress = if (matches.isEmpty()) "未找到" else "${matches.size} 个匹配",
                )
            }
        }
    }

    /**
     * 全书查找
     *
     * @param getChapterText 获取章节文本的挂起函数
     * @param chapterTitles 章节标题列表
     * @param onProgress 进度回调
     */
    fun startBookSearch(
        getChapterText: suspend (Int) -> String,
        chapterTitles: List<String>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ) {
        val findText = _uiState.value.findText
        if (findText.isEmpty()) return

        addToHistory(findText)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    chapterMatchCounts = emptyMap(),
                    scanProgress = 0f,
                    searchProgress = "搜索中...",
                )
            }

            val total = chapterTitles.size
            val allMatches = mutableListOf<FindMatch>()
            val matchCounts = mutableMapOf<Int, Int?>()

            for (i in 0 until total) {
                // 标记当前章节为扫描中
                matchCounts[i] = null
                _uiState.update {
                    it.copy(
                        chapterMatchCounts = matchCounts.toMap(),
                        scanProgress = i.toFloat() / total,
                        searchProgress = "搜索中 ${i + 1}/$total...",
                    )
                }

                val text = getChapterText(i)
                val matches = withContext(Dispatchers.Default) {
                    findMatchesInText(text, i, chapterTitles[i])
                }

                matchCounts[i] = matches.size
                allMatches.addAll(matches)

                onProgress(i + 1, total)
            }

            _uiState.update {
                it.copy(
                    matches = allMatches,
                    currentMatchIndex = if (allMatches.isNotEmpty()) 0 else -1,
                    isSearching = false,
                    chapterMatchCounts = matchCounts.toMap(),
                    scanProgress = 1f,
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
                            maxOf(0, match.range.first - 20),
                            minOf(text.length, match.range.last + 21)
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
                            maxOf(0, idx - 20),
                            minOf(text.length, idx + findText.length + 20)
                        ),
                        chapterTitle = chapterTitle,
                    ))
                    start = idx + 1
                }
            }
        } catch (_: Exception) {
            // 正则表达式错误等
        }

        return matches
    }

    // ── 导航 ─────────────────────────────────────────

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

    // ── 替换操作 ─────────────────────────────────────

    /** 替换当前匹配 */
    suspend fun replaceCurrent(chapterIndex: Int) {
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
    suspend fun replaceAllInChapter(chapterIndex: Int) {
        val s = _uiState.value
        if (s.matches.isEmpty()) return

        val batch = BatchEditDelta(
            chapterIndex = chapterIndex,
            findText = s.findText,
            replaceText = s.replaceText,
            ranges = s.matches.map { it.charStart until it.charEnd },
            isRegex = s.isRegex,
        )

        editStore.addBatch(batch)

        _uiState.update { it.copy(matches = emptyList(), currentMatchIndex = -1) }
    }

    // ── 撤销/重做 ────────────────────────────────────

    /** 撤销 */
    suspend fun undo() {
        editStore.undo()
    }

    /** 撤销单条记录 */
    suspend fun undoSingle(patch: EditStore.Patch) {
        editStore.undoSingle(patch)
    }

    /** 重做 */
    suspend fun redo() {
        editStore.redo()
    }

    /** 清空编辑 */
    suspend fun clearEdits() {
        editStore.clear()
    }

    // ── 查找历史 ─────────────────────────────────────

    /** 添加到查找历史 */
    private fun addToHistory(text: String) {
        if (text.isBlank()) return
        _searchHistory.update { history ->
            val newHistory = history.filter { it.text != text }.toMutableList()
            newHistory.add(0, FindHistory(text))
            if (newHistory.size > 20) newHistory.removeAt(newHistory.lastIndex)
            newHistory
        }
    }

    /** 清空查找历史 */
    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
    }

    /** 使用历史记录中的词 */
    fun useHistoryWord(word: String) {
        _uiState.update { it.copy(findText = word) }
    }

    /** 删除历史记录中的一条 */
    fun removeHistoryItem(text: String) {
        _searchHistory.update { history -> history.filter { it.text != text } }
    }
}
