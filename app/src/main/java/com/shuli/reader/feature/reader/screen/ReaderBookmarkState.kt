package com.shuli.reader.feature.reader.screen
import com.shuli.reader.feature.reader.screen.ReaderBookmarkState

import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity

/**
 * 阅读器书签/笔记状态（按需收集）。
 *
 * 不参与 Canvas 首帧渲染，AndroidView.update 不观察此 StateFlow。
 */
data class ReaderBookmarkState(
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
)
