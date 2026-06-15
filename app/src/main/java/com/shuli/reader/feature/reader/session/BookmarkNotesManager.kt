package com.shuli.reader.feature.reader.session
import com.shuli.reader.feature.reader.screen.ReaderUiState

import com.shuli.reader.core.database.dao.BookmarkDao
import com.shuli.reader.core.database.dao.NoteDao
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.reader.model.SelectionRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 书签与笔记管理（从 ReaderViewModel 拆出）
 *
 * 职责：书签/笔记的 CRUD、跳转、导出、可见笔记范围计算。
 */
class BookmarkNotesManager(
    private val bookmarkDao: BookmarkDao?,
    private val noteDao: NoteDao?,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val loadedBookContent: () -> com.shuli.reader.core.parser.model.BookContent?,
    private val charToByteOffset: (Int) -> Int,
    private val byteToCharOffset: (Int) -> Int,
    private val jumpToChapterPosition: (Int, Long) -> Unit,
) {
    private var bookmarksJob: Job? = null
    private var notesJob: Job? = null

    // ── 书签管理 ──────────────────────────────────────────────

    fun addBookmark(selectedText: String? = null) {
        val dao = bookmarkDao ?: return
        val state = uiState.value
        if (state.bookId == 0L) return

        scope.launch {
            val page = state.currentPage
            val chapters = loadedBookContent()?.chapters
            val chapterByteStart = chapters?.getOrNull(state.chapterIndex)?.byteStart ?: 0L
            val charOffset = page?.startCharOffset ?: 0
            val byteOffset = chapterByteStart + charToByteOffset(charOffset)

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
        val chapters = loadedBookContent()?.chapters
        if (chapters.isNullOrEmpty()) return
        val chapterIndex = chapters.indexOfLast { it.byteStart <= bookmark.byteOffset }
            .coerceIn(0, chapters.lastIndex)
        jumpToChapterPosition(chapterIndex, bookmark.byteOffset)
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

    // ── 笔记管理 ──────────────────────────────────────────────

    fun addNote(startPos: Int, endPos: Int, content: String, color: String? = null) {
        val dao = noteDao ?: return
        val state = uiState.value
        if (state.bookId == 0L) return

        scope.launch {
            val chapters = loadedBookContent()?.chapters
            val chapterByteStart = chapters?.getOrNull(state.chapterIndex)?.byteStart ?: 0L
            val byteStart = chapterByteStart + charToByteOffset(startPos)
            val byteEnd = chapterByteStart + charToByteOffset(endPos)

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
        val chapters = loadedBookContent()?.chapters
        if (chapters.isNullOrEmpty()) return
        val chapterIndex = chapters.indexOfLast { it.byteStart <= note.byteStart }
            .coerceIn(0, chapters.lastIndex)
        jumpToChapterPosition(chapterIndex, note.byteStart)
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
        val chapters = loadedBookContent()?.chapters ?: return emptyList()
        val chapterByteStart = chapters.getOrNull(state.chapterIndex)?.byteStart ?: 0L
        val chapterByteEnd = chapters.getOrNull(state.chapterIndex + 1)?.byteStart ?: Long.MAX_VALUE

        return state.notes
            .filter { it.byteStart >= chapterByteStart && it.byteStart < chapterByteEnd && it.color != null }
            .mapNotNull { note ->
                val relativeStart = (note.byteStart - chapterByteStart).toInt().coerceAtLeast(0)
                val relativeEnd = (note.byteEnd - chapterByteStart).toInt().coerceAtLeast(0)
                val charStart = byteToCharOffset(relativeStart)
                val charEnd = byteToCharOffset(relativeEnd)
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
}
