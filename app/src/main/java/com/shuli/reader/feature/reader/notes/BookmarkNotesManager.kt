package com.shuli.reader.feature.reader.notes

import com.shuli.reader.core.database.dao.BookmarkDao
import com.shuli.reader.core.database.dao.NoteDao
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.feature.reader.ReaderUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 书签与笔记管理器。
 *
 * 职责：书签和笔记的增删改查、跳转、导出、
 *       可见笔记范围计算。
 *
 * 通过 [uiState] 读写共享状态，不反向依赖 ViewModel。
 */
class BookmarkNotesManager(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val bookmarkDao: BookmarkDao?,
    private val noteDao: NoteDao?,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "BookmarkNotesMgr"
    }

    // ── 回调（由 ViewModel 注入）────────────────────────────────────

    /** 获取当前书籍内容（用于章节字节偏移计算） */
    var onGetLoadedBookContent: (() -> com.shuli.reader.core.parser.model.BookContent?)? = null

    /** 字符偏移转字节偏移 */
    var onCharToByteOffset: ((Int) -> Int)? = null

    /** 字节偏移转字符偏移 */
    var onByteToCharOffset: ((Int) -> Int)? = null

    /** 跳转到章节指定字节位置 */
    var onJumpToChapterPosition: ((Int, Long) -> Unit)? = null

    /** 清除文本选区 */
    var onClearTextSelection: (() -> Unit)? = null

    // ── 内部状态 ──────────────────────────────────────────────────

    private var bookmarksJob: Job? = null
    private var notesJob: Job? = null

    // ── 书签管理 ──────────────────────────────────────────────────

    fun addBookmarkFromSelection() {
        val range = uiState.value.selectedRange ?: return
        addBookmark(range)
        onClearTextSelection?.invoke()
    }

    fun addBookmark(selectedText: String? = null) {
        val dao = bookmarkDao ?: return
        val state = uiState.value
        if (state.bookId == 0L) return

        scope.launch {
            val page = state.currentPage
            val chapters = onGetLoadedBookContent?.invoke()?.chapters
            val chapterByteStart = chapters?.getOrNull(state.chapterIndex)?.byteStart ?: 0L
            val charOffset = page?.startCharOffset ?: 0
            val byteOffset = chapterByteStart + (onCharToByteOffset?.invoke(charOffset) ?: 0)

            val bookmark = BookmarkEntity(
                bookId = state.bookId,
                createdTime = System.currentTimeMillis(),
                byteOffset = byteOffset,
                selectedText = selectedText,
                chapterIndex = state.chapterIndex,
                chapterTitle = state.chapterTitle,
            )
            dao.insertBookmark(bookmark)
            loadBookmarks()
        }
    }

    fun addBookmark(range: SelectionRange) {
        addBookmark(range.selectedText)
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        val dao = bookmarkDao ?: return
        scope.launch {
            dao.deleteBookmark(bookmark)
            loadBookmarks()
        }
    }

    fun goToBookmark(bookmark: BookmarkEntity) {
        val chapters = onGetLoadedBookContent?.invoke()?.chapters
        if (chapters.isNullOrEmpty()) return
        val chapterIndex = chapters.indexOfLast { it.byteStart <= bookmark.byteOffset }
            .coerceIn(0, chapters.lastIndex)
        onJumpToChapterPosition?.invoke(chapterIndex, bookmark.byteOffset)
    }

    fun loadBookmarks() {
        val dao = bookmarkDao ?: return
        val state = uiState.value
        if (state.bookId == 0L) return

        bookmarksJob?.cancel()
        bookmarksJob = scope.launch {
            dao.getBookmarksByBookId(state.bookId).collect { bookmarks ->
                uiState.value = uiState.value.copy(bookmarks = bookmarks)
            }
        }
    }

    // ── 笔记管理 ──────────────────────────────────────────────────

    fun addNoteFromSelection() {
        val range = uiState.value.selectedRange ?: return
        val content = range.selectedText.orEmpty()
        if (content.isBlank()) return
        addNote(range, content)
        onClearTextSelection?.invoke()
    }

    fun addNote(startPos: Int, endPos: Int, content: String, color: String? = null) {
        val dao = noteDao ?: return
        val state = uiState.value
        if (state.bookId == 0L) return

        scope.launch {
            val chapters = onGetLoadedBookContent?.invoke()?.chapters
            val chapterByteStart = chapters?.getOrNull(state.chapterIndex)?.byteStart ?: 0L
            val byteStart = chapterByteStart + (onCharToByteOffset?.invoke(startPos) ?: 0)
            val byteEnd = chapterByteStart + (onCharToByteOffset?.invoke(endPos) ?: 0)

            val note = NoteEntity(
                bookId = state.bookId,
                createdTime = System.currentTimeMillis(),
                byteStart = byteStart.toLong(),
                byteEnd = byteEnd.toLong(),
                noteText = content,
                color = color,
            )
            dao.insertNote(note)
            loadNotes()
        }
    }

    fun addNote(range: SelectionRange, content: String, color: String? = null) {
        addNote(range.startPos, range.endPos, content, color)
    }

    fun deleteNote(note: NoteEntity) {
        val dao = noteDao ?: return
        scope.launch {
            dao.deleteNote(note)
            loadNotes()
        }
    }

    fun updateNote(note: NoteEntity, newText: String, newColor: String? = note.color) {
        val dao = noteDao ?: return
        if (newText.isBlank()) return
        scope.launch {
            dao.updateNote(note.copy(noteText = newText, color = newColor))
            loadNotes()
        }
    }

    fun goToNote(note: NoteEntity) {
        val chapters = onGetLoadedBookContent?.invoke()?.chapters
        if (chapters.isNullOrEmpty()) return
        val chapterIndex = chapters.indexOfLast { it.byteStart <= note.byteStart }
            .coerceIn(0, chapters.lastIndex)
        onJumpToChapterPosition?.invoke(chapterIndex, note.byteStart)
    }

    fun loadNotes() {
        val dao = noteDao ?: return
        val state = uiState.value
        if (state.bookId == 0L) return

        notesJob?.cancel()
        notesJob = scope.launch {
            dao.getNotesByBookId(state.bookId).collect { notes ->
                uiState.value = uiState.value.copy(notes = notes)
            }
        }
    }

    fun getVisibleNoteRanges(): List<Pair<SelectionRange, String?>> {
        val state = uiState.value
        val chapter = state.currentChapter ?: return emptyList()
        val chapters = onGetLoadedBookContent?.invoke()?.chapters ?: return emptyList()
        val chapterByteStart = chapters.getOrNull(state.chapterIndex)?.byteStart ?: 0L
        val chapterByteEnd = chapters.getOrNull(state.chapterIndex + 1)?.byteStart ?: Long.MAX_VALUE

        return state.notes
            .filter { it.byteStart >= chapterByteStart && it.byteStart < chapterByteEnd && it.color != null }
            .mapNotNull { note ->
                val relativeStart = (note.byteStart - chapterByteStart).toInt().coerceAtLeast(0)
                val relativeEnd = (note.byteEnd - chapterByteStart).toInt().coerceAtLeast(0)
                val charStart = onByteToCharOffset?.invoke(relativeStart) ?: 0
                val charEnd = onByteToCharOffset?.invoke(relativeEnd) ?: 0
                if (charStart < charEnd) {
                    SelectionRange(
                        chapterIndex = state.chapterIndex,
                        startPos = charStart,
                        endPos = charEnd,
                    ) to note.color
                } else null
            }
    }

    fun exportNotesAsMarkdown(): String? {
        val state = uiState.value
        if (state.notes.isEmpty() || state.bookTitle.isBlank()) return null
        val sb = StringBuilder()
        sb.append("# ${state.bookTitle} — Notes\n\n")
        for (note in state.notes.sortedBy { it.byteStart }) {
            sb.append("## ${note.noteText.take(40)}\n")
            sb.append("- Position: ${note.byteStart}-${note.byteEnd}\n")
            if (note.color != null) sb.append("- Color: ${note.color}\n")
            sb.append("- Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(note.createdTime))}\n")
            sb.append("\n${note.noteText}\n\n---\n\n")
        }
        return sb.toString()
    }

    /** 释放资源（ViewModel.onCleared 时调用） */
    fun release() {
        bookmarksJob?.cancel()
        bookmarksJob = null
        notesJob?.cancel()
        notesJob = null
    }
}
