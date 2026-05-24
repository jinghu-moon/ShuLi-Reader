package com.shuli.reader.feature.reader.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.shuli.reader.ui.testing.UiTestTags
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 目录弹窗，支持目录/书签/笔记三个 Tab。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryDialog(
    chapters: List<String>,
    currentChapterIndex: Int,
    bookmarks: List<BookmarkEntity>,
    notes: List<NoteEntity>,
    onChapterClick: (Int) -> Unit,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onBookmarkDelete: (BookmarkEntity) -> Unit,
    onNoteClick: (NoteEntity) -> Unit,
    onNoteDelete: (NoteEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(strings.directoryTab, strings.bookmarksTab, strings.notesTab)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = readerColors.surface,
        contentColor = readerColors.textPrimary,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = readerColors.surface,
                contentColor = readerColors.textPrimary,
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                        selectedContentColor = readerColors.accent,
                        unselectedContentColor = readerColors.textSecondary,
                    )
                }
            }

            when (selectedTab) {
                0 -> ChapterList(
                    chapters = chapters,
                    currentIndex = currentChapterIndex,
                    onChapterClick = onChapterClick,
                )
                1 -> BookmarkList(
                    bookmarks = bookmarks,
                    onBookmarkClick = onBookmarkClick,
                    onBookmarkDelete = onBookmarkDelete,
                )
                2 -> NoteList(
                    notes = notes,
                    onNoteClick = onNoteClick,
                    onNoteDelete = onNoteDelete,
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChapterList(
    chapters: List<String>,
    currentIndex: Int,
    onChapterClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current

    if (chapters.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = strings.loading,
                style = MaterialTheme.typography.bodyMedium,
                color = readerColors.textSecondary,
            )
        }
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex in chapters.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .then(Modifier.heightIn(max = 400.dp))
            .testTag(UiTestTags.READER_DIRECTORY_CHAPTER_LIST),
    ) {
        items(chapters.size) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChapterClick(index) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chapters[index],
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (index == currentIndex) {
                        readerColors.accent
                    } else {
                        readerColors.textPrimary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (index == currentIndex) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.currentChapterLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.accent,
                    )
                }
            }
            if (index < chapters.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = readerColors.divider,
                )
            }
        }
    }
}

@Composable
private fun BookmarkList(
    bookmarks: List<BookmarkEntity>,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onBookmarkDelete: (BookmarkEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    if (bookmarks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Bookmark,
                    contentDescription = null,
                    tint = readerColors.textTertiary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.noBookmarks,
                    style = MaterialTheme.typography.bodyMedium,
                    color = readerColors.textSecondary,
                )
            }
        }
        return
    }

    LazyColumn(modifier = modifier.then(Modifier.heightIn(max = 400.dp))) {
        items(bookmarks, key = { it.id }) { bookmark ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBookmarkClick(bookmark) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bookmark.chapterName ?: bookmark.title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = readerColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!bookmark.selectedText.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = bookmark.selectedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = readerColors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(Date(bookmark.createdTime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.textTertiary,
                    )
                }
                IconButton(onClick = { onBookmarkDelete(bookmark) }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = strings.deleteIconDesc,
                        tint = readerColors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = readerColors.divider,
            )
        }
    }
}

@Composable
private fun NoteList(
    notes: List<NoteEntity>,
    onNoteClick: (NoteEntity) -> Unit,
    onNoteDelete: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    if (notes.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Note,
                    contentDescription = null,
                    tint = readerColors.textTertiary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.noNotes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = readerColors.textSecondary,
                )
            }
        }
        return
    }

    LazyColumn(modifier = modifier.then(Modifier.heightIn(max = 400.dp))) {
        items(notes, key = { it.id }) { note ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNoteClick(note) }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.content ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = readerColors.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = strings.notePosition(
                            note.startPosition,
                            note.endPosition,
                            dateFormat.format(Date(note.createdTime)),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.textTertiary,
                    )
                }
                IconButton(onClick = { onNoteDelete(note) }) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = strings.deleteIconDesc,
                        tint = readerColors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = readerColors.divider,
            )
        }
    }
}
