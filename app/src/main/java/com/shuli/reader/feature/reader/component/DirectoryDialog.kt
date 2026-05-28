package com.shuli.reader.feature.reader.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.shuli.reader.ui.testing.UiTestTags
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    chapterWordCounts: List<Int>,
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

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it / 4 } + fadeIn(tween(200)) togetherWith
                            slideOutHorizontally { -it / 4 } + fadeOut(tween(150))
                    } else {
                        slideInHorizontally { -it / 4 } + fadeIn(tween(200)) togetherWith
                            slideOutHorizontally { it / 4 } + fadeOut(tween(150))
                    }
                },
                label = "directory-tab-content",
            ) { tab ->
                when (tab) {
                    0 -> ChapterList(
                        chapters = chapters,
                        currentIndex = currentChapterIndex,
                        wordCounts = chapterWordCounts,
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
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ChapterList(
    chapters: List<String>,
    currentIndex: Int,
    wordCounts: List<Int>,
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
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (MaterialTheme.typography.bodyLarge.fontSize.value - 2).sp,
                    ),
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
                if (index < wordCounts.size) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatWordCount(wordCounts[index]),
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.textTertiary,
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
    var bookmarkToDelete by remember { mutableStateOf<BookmarkEntity?>(null) }

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
                        text = bookmark.selectedText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = readerColors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(Date(bookmark.createdTime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.textTertiary,
                    )
                }
                IconButton(onClick = { bookmarkToDelete = bookmark }) {
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

    // 删除确认对话框
    bookmarkToDelete?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { bookmarkToDelete = null },
            title = { Text(strings.deleteBookmarkTitle) },
            text = { Text(strings.deleteBookmarkConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBookmarkDelete(bookmark)
                        bookmarkToDelete = null
                    },
                ) { Text(strings.deleteAction) }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkToDelete = null }) { Text(strings.cancelAction) }
            },
        )
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
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }

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
                        text = note.noteText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = readerColors.textPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${formatByteOffset(note.byteStart)}-${formatByteOffset(note.byteEnd)}  ${dateFormat.format(Date(note.createdTime))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = readerColors.textTertiary,
                    )
                }
                IconButton(onClick = { noteToDelete = note }) {
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

    // 删除确认对话框
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text(strings.deleteNoteTitle) },
            text = { Text(strings.deleteNoteConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onNoteDelete(note)
                        noteToDelete = null
                    },
                ) { Text(strings.deleteAction) }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text(strings.cancelAction) }
            },
        )
    }
}

private fun formatWordCount(count: Int): String {
    return when {
        count >= 10000 -> String.format("%.2f万字", count / 10000.0)
        else -> "${count}字"
    }
}

private fun formatByteOffset(offset: Long): String {
    return when {
        offset >= 1024 * 1024 -> String.format("%.1fMB", offset / (1024.0 * 1024.0))
        offset >= 1024 -> String.format("%.1fKB", offset / 1024.0)
        else -> "${offset}B"
    }
}
