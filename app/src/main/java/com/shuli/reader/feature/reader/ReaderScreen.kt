package com.shuli.reader.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reader.ReaderCanvasView
import com.shuli.reader.feature.reader.effects.ReaderLifecycleEffects
import com.shuli.reader.feature.reader.effects.ReaderPrefsEffects
import com.shuli.reader.feature.reader.effects.ReaderRuntimeEffects
import com.shuli.reader.feature.reader.gestures.ReaderCanvasGestures
import com.shuli.reader.feature.reader.overlays.ReaderBottomBar
import com.shuli.reader.feature.reader.overlays.ReaderOverlayPanels
import com.shuli.reader.feature.reader.overlays.ReaderTopBar
import com.shuli.reader.ui.testing.UiTestTags
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.ReaderDimens
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme

/**
 * 阅读器页面。
 * 使用 AndroidView 承载 ReaderCanvasView 进行渲染。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = run {
        val context = LocalContext.current
        remember { ReaderViewModel(fontManager = FontManager(context)) }
    },
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var batteryLevel by remember { mutableIntStateOf(100) }

    val density = androidx.compose.ui.platform.LocalDensity.current.density

    // ── 生命周期与平台适配 Effects ──
    ReaderLifecycleEffects(
        bookId = bookId,
        density = density,
        brightness = uiState.readerPreferences.brightness,
        keepScreenOn = uiState.readerPreferences.keepScreenOn,
        context = context,
        activity = activity,
        viewModel = viewModel,
        onBatteryLevelChanged = { batteryLevel = it },
    )

    // 内层返回：选区 > 各浮层 > 工具栏，依次回退
    BackHandler(
        enabled = uiState.selectedRange != null
            || uiState.showDirectory
            || uiState.showQuickSettings
            || uiState.showToolbar,
    ) {
        when {
            uiState.selectedRange != null -> viewModel.clearTextSelection()
            uiState.showDirectory -> viewModel.toggleDirectory()
            uiState.showQuickSettings -> viewModel.toggleQuickSettings()
            uiState.showToolbar -> viewModel.toggleToolbar()
        }
    }

    Scaffold(
        containerColor = readerColors.background,
    ) { _ ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(readerColors.background)
                .testTag(UiTestTags.READER_SCREEN),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState.isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = readerColors.accent,
                        strokeWidth = 3.dp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = strings.loading,
                        style = MaterialTheme.typography.bodyLarge,
                        color = readerColors.textSecondary,
                    )
                }
            } else if (uiState.error != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = readerColors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.error.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = readerColors.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                val layoutVersionRef = remember { mutableIntStateOf(uiState.layoutVersion) }
                var canvasView by remember { mutableStateOf<ReaderCanvasView?>(null) }

                // ── 排版偏好 Effects ──
                ReaderPrefsEffects(
                    canvasView = canvasView,
                    prefs = uiState.readerPreferences,
                    density = density,
                    viewModel = viewModel,
                )

                // ── 运行时状态 Effects ──
                ReaderRuntimeEffects(
                    canvasView = canvasView,
                    themeColors = uiState.themeColors,
                    batteryLevel = batteryLevel,
                    ttsActiveRange = uiState.ttsActiveRange,
                    selectedRange = uiState.selectedRange,
                    noteHashes = uiState.notes.hashCode() to uiState.chapterIndex,
                    viewModel = viewModel,
                )

                // ── CanvasView ──
                AndroidView(
                    modifier = Modifier.fillMaxSize().testTag(UiTestTags.READER_CANVAS).onGloballyPositioned { coordinates ->
                        viewModel.setScreenSize(coordinates.size.width, coordinates.size.height)
                    },
                    factory = { ctx ->
                        ReaderCanvasView(ctx).apply {
                            canvasView = this
                            setThemeColors(
                                com.shuli.reader.core.data.ReaderTheme.PAPER
                                    .toReaderColorScheme()
                                    .toCanvasThemeColors(),
                            )
                            ReaderCanvasGestures.bindCallbacks(this, viewModel)
                            setPageDelegate(viewModel.pageDelegate)
                        }
                    },
                    update = { view ->
                        val page = uiState.currentPage ?: return@AndroidView
                        val nextPage = uiState.currentChapter?.getPage(uiState.pageIndex + 1)
                        val prevPage = uiState.currentChapter?.getPage(uiState.pageIndex - 1)

                        val isLayoutChange = layoutVersionRef.intValue != uiState.layoutVersion
                        if (isLayoutChange) layoutVersionRef.intValue = uiState.layoutVersion

                        view.canTurnPrev = { uiState.pageIndex > 0 || uiState.chapterIndex > 0 }
                        view.canTurnNext = {
                            val chapter = uiState.currentChapter
                            if (chapter != null) {
                                uiState.pageIndex < chapter.lastIndex || uiState.chapterIndex < uiState.totalChapters - 1
                            } else false
                        }

                        view.setPage(page, nextPage, prevPage, uiState.currentChapter?.content ?: "", uiState.pageRenderMode, isLayoutChange = isLayoutChange)
                    },
                )

                // ── 顶部工具栏 ──
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.showToolbar && !uiState.showSearch,
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { -it }, animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())),
                    exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }, animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    ReaderTopBar(
                        bookTitle = uiState.bookTitle,
                        bookId = bookId,
                        searchResultIndex = uiState.currentSearchResultIndex,
                        searchResultCount = uiState.searchResults.size,
                        onBackClick = onBackClick,
                        onSearchClick = viewModel::toggleSearch,
                        onPreviousSearchResult = viewModel::goToPreviousSearchResult,
                        onNextSearchResult = viewModel::goToNextSearchResult,
                    )
                }

                // 搜索输入栏
                if (uiState.showSearch) {
                    ReaderSearchBar(
                        onSearch = { query -> viewModel.searchInCurrentBook(query) },
                        onClose = viewModel::toggleSearch,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }

                // 侧边悬浮亮度条
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.showToolbar && !uiState.showSearch,
                    enter = androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }, animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())),
                    exit = androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }, animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    com.shuli.reader.feature.reader.component.VerticalBrightnessSlider(
                        brightness = uiState.readerPreferences.brightness,
                        onBrightnessChange = viewModel::setBrightness,
                        modifier = Modifier.padding(end = 12.dp, top = 24.dp).height(240.dp)
                    )
                }

                // ── 底部工具栏 ──
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.showToolbar && !uiState.showSearch,
                    enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())),
                    exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    ReaderBottomBar(
                        uiState = uiState,
                        onPreviousChapter = { if (uiState.chapterIndex > 0) viewModel.openChapter(uiState.chapterIndex - 1) },
                        onNextChapter = { if (uiState.chapterIndex + 1 < uiState.totalChapters) viewModel.openChapter(uiState.chapterIndex + 1) },
                        onPageScrubStart = viewModel::startPageScrub,
                        onPageScrub = viewModel::scrubToPage,
                        onPageScrubCommit = viewModel::commitPageScrub,
                        onToggleDirectory = viewModel::toggleDirectory,
                        onCycleTheme = viewModel::cycleTheme,
                        onAddBookmark = viewModel::addBookmark,
                        onToggleQuickSettings = viewModel::toggleQuickSettings,
                    )
                }

                // 选区操作栏
                uiState.selectedRange?.let { range ->
                    ReaderSelectionActionBar(
                        onCopy = {
                            range.selectedText?.takeIf { it.isNotBlank() }?.let { text ->
                                clipboardManager.setText(AnnotatedString(text))
                            }
                            viewModel.clearTextSelection()
                        },
                        onBookmark = viewModel::addBookmarkFromSelection,
                        onNote = viewModel::addNoteFromSelection,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(ReaderDimens.PaddingMedium),
                    )
                }

                // ── 浮层面板 ──
                ReaderOverlayPanels(uiState = uiState, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ReaderSelectionActionBar(
    onCopy: () -> Unit,
    onBookmark: () -> Unit,
    onNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current

    val actionButtonColors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
        containerColor = readerColors.divider,
        contentColor = readerColors.textPrimary,
    )

    Surface(
        color = readerColors.surface,
        contentColor = readerColors.textPrimary,
        tonalElevation = ReaderDimens.ElevationMedium,
        shadowElevation = ReaderDimens.ElevationMedium + 2.dp,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .wrapContentWidth()
            .testTag(UiTestTags.READER_SELECTION_ACTION_BAR),
    ) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ReaderDimens.PaddingSmall),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = ReaderDimens.PaddingMedium - 4.dp, vertical = ReaderDimens.PaddingSmall),
        ) {
            FilledTonalButton(
                onClick = onCopy,
                colors = actionButtonColors,
                modifier = Modifier.testTag(UiTestTags.READER_COPY_SELECTION_BUTTON),
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Text(strings.copySelection)
            }
            FilledTonalButton(
                onClick = onBookmark,
                colors = actionButtonColors,
                modifier = Modifier.testTag(UiTestTags.READER_BOOKMARK_SELECTION_BUTTON),
            ) {
                Icon(Icons.Outlined.Bookmark, contentDescription = null)
                Text(strings.addBookmarkAction)
            }
            FilledTonalButton(
                onClick = onNote,
                colors = actionButtonColors,
                modifier = Modifier.testTag(UiTestTags.READER_NOTE_SELECTION_BUTTON),
            ) {
                Icon(Icons.AutoMirrored.Outlined.Note, contentDescription = null)
                Text(strings.addNoteAction)
            }
        }
    }
}

/**
 * 阅读器搜索输入栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSearchBar(
    onSearch: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        color = readerColors.surface.copy(alpha = 0.95f),
        contentColor = readerColors.textPrimary,
        tonalElevation = ReaderDimens.ElevationMedium,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            androidx.compose.material3.IconButton(onClick = onClose) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.backIconDesc,
                    tint = readerColors.textPrimary,
                )
            }
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(strings.search, color = readerColors.textTertiary)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = readerColors.textPrimary,
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch(query) }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        androidx.compose.material3.IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.Close,
                                contentDescription = null,
                                tint = readerColors.textTertiary,
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            androidx.compose.material3.IconButton(onClick = { onSearch(query) }) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Outlined.Search,
                    contentDescription = strings.search,
                    tint = readerColors.textPrimary,
                )
            }
        }
    }
}
