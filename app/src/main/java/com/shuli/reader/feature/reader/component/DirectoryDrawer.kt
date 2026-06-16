package com.shuli.reader.feature.reader.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.database.entity.BookmarkEntity
import com.shuli.reader.core.database.entity.ChapterReadingStatsEntity
import com.shuli.reader.core.database.entity.NoteEntity
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.component.directory.BookmarkList
import com.shuli.reader.feature.reader.component.directory.ChapterList
import com.shuli.reader.feature.reader.component.directory.NoteList
import com.shuli.reader.ui.theme.LocalReaderColorScheme

/**
 * 目录侧边抽屉，支持目录/书签/笔记三个 Tab。
 *
 * 从原来的 ModalBottomSheet 改为 ModalNavigationDrawer，
 * 可以显示更多章节内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryDrawer(
    chapters: List<String>,
    currentChapterIndex: Int,
    chapterWordCounts: List<Int>,
    chapterStats: List<ChapterReadingStatsEntity> = emptyList(),
    bookmarks: List<BookmarkEntity>,
    notes: List<NoteEntity>,
    onChapterClick: (Int) -> Unit,
    onBookmarkClick: (BookmarkEntity) -> Unit,
    onBookmarkDelete: (BookmarkEntity) -> Unit,
    onNoteClick: (NoteEntity) -> Unit,
    onNoteDelete: (NoteEntity) -> Unit,
    onNoteEdit: (NoteEntity, String, String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(strings.reader.directoryTab, strings.reader.bookmarksTab, strings.reader.notesTab)

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Open)

    // 当抽屉关闭时触发 onDismiss
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Closed) {
            onDismiss()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = readerColors.surface,
                drawerContentColor = readerColors.textPrimary,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(),
                ) {
                    // 标题栏
                    Text(
                        text = strings.reader.directoryTab,
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        color = readerColors.textPrimary,
                        modifier = Modifier.padding(16.dp),
                    )

                    // Tab 栏
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

                    // 内容区域（填充剩余空间）
                    AnimatedContent(
                        targetState = selectedTab,
                        modifier = Modifier.weight(1f),
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
                                chapterStats = chapterStats,
                                bookmarks = bookmarks,
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
                                onNoteEdit = onNoteEdit,
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier,
    ) {
        // 内容区域为空，因为抽屉覆盖在阅读界面上
        Box {}
    }
}
