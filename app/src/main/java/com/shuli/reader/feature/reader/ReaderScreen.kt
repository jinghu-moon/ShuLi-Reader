package com.shuli.reader.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.reader.ReaderCanvasView
import com.shuli.reader.feature.bookshelf.component.BookDetailsActions
import com.shuli.reader.feature.bookshelf.component.BookDetailsSheet
import com.shuli.reader.feature.bookshelf.component.BookDetailsTagActions
import com.shuli.reader.feature.bookshelf.component.BookDetailsTagState
import com.shuli.reader.feature.reader.effects.ReaderCanvasEffects
import com.shuli.reader.feature.reader.overlays.ReaderBottomBar
import com.shuli.reader.feature.reader.overlays.ReaderOverlayPanels
import com.shuli.reader.feature.reader.overlays.ReaderTopBar
import com.shuli.reader.ui.testing.UiTestTags
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.ReaderDimens
import com.shuli.reader.core.data.ReaderFontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = run {
        val context = LocalContext.current
        remember { ReaderViewModel(bookId = bookId, fontManager = FontManager(context)) }
    },
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()
    val currentBookItem by viewModel.currentBookItem.collectAsState()
    val currentBookTags by viewModel.getBookTags().collectAsState(initial = emptyList())
    val allTags by viewModel.allTags.collectAsState()
    val tagSuggestions by viewModel.tagSuggestions.collectAsState()
    val density = LocalDensity.current.density
    var showBookDetailsSheet by remember { mutableStateOf(false) }

    // 内层返回：选区 > 各浮层 > 工具栏，依次回退
    BackHandler(
        enabled = uiState.selectedRange != null
            || uiState.showDirectory
            || uiState.showQuickSettings
            || uiState.showToolbar,
    ) {
        when {
            uiState.selectedRange != null -> viewModel.navigationCoordinator.clearTextSelection()
            uiState.showDirectory -> viewModel.navigationCoordinator.toggleDirectory()
            uiState.showQuickSettings -> viewModel.navigationCoordinator.toggleQuickSettings()
            uiState.showToolbar -> viewModel.navigationCoordinator.toggleToolbar()
        }
    }

    // 必须始终在组合树中：不能放在 isLoading 条件分支内，
    // 否则每次 isLoading 切换都会 dispose LaunchedEffect，导致 openBook 反复触发。
    LaunchedEffect(bookId) {
        viewModel.openBook(bookId)
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
                LoadingIndicator(readerColors = readerColors, text = strings.common.loading)
            } else if (uiState.error != null) {
                ErrorDisplay(error = uiState.error.orEmpty(), readerColors = readerColors)
            } else {
                var canvasView by remember { mutableStateOf<ReaderCanvasView?>(null) }

                // 所有 LaunchedEffect 副作用
                ReaderCanvasEffects(viewModel = viewModel, canvasView = canvasView)

                // Canvas 渲染
                val layoutVersionRef = remember { mutableIntStateOf(uiState.layoutVersion) }
                AndroidView(
                    modifier = Modifier.fillMaxSize().testTag(UiTestTags.READER_CANVAS).onGloballyPositioned { coordinates ->
                        viewModel.setScreenSize(coordinates.size.width, coordinates.size.height)
                    },
                    factory = { context ->
                        ReaderCanvasView(context).apply {
                            canvasView = this
                            onPageChanged = viewModel.navigationCoordinator::handlePageDirection
                            onPageChangedSlots = { viewModel.readerProgressResolver.resolveHeaderAndFooterSlots() }
                            onTextSelected = viewModel.navigationCoordinator::selectText
                            onCenterClicked = viewModel.navigationCoordinator::toggleToolbar
                            applyInitialReaderCanvasState(uiState, viewModel, density)
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

                // 顶部工具栏
                ReaderTopBar(
                    uiState = uiState,
                    bookId = bookId,
                    onBackClick = onBackClick,
                    onToggleSearch = viewModel.navigationCoordinator::toggleSearch,
                    onPreviousSearchResult = viewModel.readerSearchManager::goToPreviousSearchResult,
                    onNextSearchResult = viewModel.readerSearchManager::goToNextSearchResult,
                    onShowBookInfo = { showBookDetailsSheet = true },
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                // 搜索输入栏
                if (uiState.showSearch) {
                    ReaderSearchBar(
                        onSearch = { query -> viewModel.readerSearchManager.searchInCurrentBook(query) },
                        onClose = viewModel.navigationCoordinator::toggleSearch,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }

                // 侧边亮度条
                AnimatedVisibility(
                    visible = uiState.showToolbar && !uiState.showSearch,
                    enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())) + fadeIn(animationSpec = tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())),
                    exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())) + fadeOut(animationSpec = tween(com.shuli.reader.core.reader.animation.ReaderMotionTokens.SHORT_MS.toInt())),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    com.shuli.reader.feature.reader.component.VerticalBrightnessSlider(
                        brightness = uiState.readerPreferences.brightness,
                        onBrightnessChange = viewModel.readerSettingsManager::setBrightness,
                        modifier = Modifier.padding(end = 12.dp, top = 24.dp).height(240.dp)
                    )
                }

                // 底部工具栏
                ReaderBottomBar(
                    uiState = uiState,
                    onToggleDirectory = viewModel.navigationCoordinator::toggleDirectory,
                    onCycleTheme = viewModel.readerSettingsManager::cycleTheme,
                    onAddBookmark = { viewModel.bookmarkNotesManager.addBookmark() },
                    onToggleQuickSettings = viewModel.navigationCoordinator::toggleQuickSettings,
                    onOpenChapter = viewModel::openChapter,
                    onStartPageScrub = viewModel.navigationCoordinator::startPageScrub,
                    onScrubToPage = viewModel.navigationCoordinator::scrubToPage,
                    onCommitPageScrub = viewModel.navigationCoordinator::commitPageScrub,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                // 选区操作栏
                uiState.selectedRange?.let { range ->
                    ReaderSelectionActionBar(
                        onCopy = {
                            range.selectedText?.takeIf { it.isNotBlank() }?.let { text ->
                                clipboardManager.setText(AnnotatedString(text))
                            }
                            viewModel.navigationCoordinator.clearTextSelection()
                        },
                        onBookmark = viewModel::addBookmarkFromSelection,
                        onNote = viewModel::addNoteFromSelection,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(ReaderDimens.PaddingMedium),
                    )
                }
            }
        }

        // 弹出面板（放在 Box 外部，避免 enableEdgeToEdge 布局异常）
        ReaderOverlayPanels(uiState = uiState, viewModel = viewModel)
    }

    // 书籍详情弹窗（P0+P1）
    if (showBookDetailsSheet) {
        currentBookItem?.let { book ->
            BookDetailsSheet(
                book = book,
                actions = BookDetailsActions(
                    onStatusChange = viewModel::updateStatus,
                    onExportNotes = { /* P3 */ },
                    onDeleteBook = { /* handled by caller */ },
                    onContinueReading = null,
                ),
                tagState = BookDetailsTagState(
                    tags = currentBookTags,
                    suggestions = allTags,
                ),
                tagActions = BookDetailsTagActions(
                    onTagAdd = { tagName -> viewModel.addTag(tagName) },
                    onTagRemove = { tagId -> viewModel.removeTag(tagId) },
                    onTagClick = { /* P1: navigate to tag filter - no-op in reader context */ },
                    onSearchTags = { prefix -> viewModel.searchTagSuggestions(prefix) },
                ),
                onDismiss = { showBookDetailsSheet = false },
            )
        }
    }
}

// ================= 私有辅助组件 =================

private fun ReaderCanvasView.applyInitialReaderCanvasState(
    uiState: ReaderUiState,
    viewModel: ReaderViewModel,
    density: Float,
) {
    val prefs = uiState.readerPreferences

    setHeaderTextRatio(prefs.headerFontSizeRatio)
    setFooterTextRatio(prefs.footerFontSizeRatio)
    updatePaintSnapshot(
        textSize = prefs.fontSize * density,
        letterSpacing = prefs.letterSpacing,
        fakeBold = prefs.fontWeight == ReaderFontWeight.BOLD,
        fontKey = prefs.readingFont,
        textAlign = prefs.textAlign,
        invalidateContent = false,
    )
    textPaint.let { viewModel.syncTextMeasurerPaint(it) }

    val (headerRes, footerRes) = viewModel.readerProgressResolver.resolveHeaderAndFooterSlots()
    updateHeaderFooter(
        headerRes,
        footerRes,
        prefs.headerFooterAlpha,
        prefs.showProgress,
        prefs.showHeaderLine,
        prefs.showFooterLine,
    )
    setTitleStyle(prefs.titleStyle)
    setEdgeTurnPageEnabled(prefs.edgeTurnPage)
    setEdgeWidthPercent(prefs.edgeWidthPercent)
    setPageDelegate(viewModel.pageDelegate)
    setThemeColors(uiState.themeColors)
}

@Composable
private fun LoadingIndicator(readerColors: com.shuli.reader.ui.theme.ReaderColorScheme, text: String) {
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
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = readerColors.textSecondary,
        )
    }
}

@Composable
private fun ErrorDisplay(error: String, readerColors: com.shuli.reader.ui.theme.ReaderColorScheme) {
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
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = readerColors.textPrimary,
            textAlign = TextAlign.Center,
        )
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
        modifier = modifier.wrapContentWidth().testTag(UiTestTags.READER_SELECTION_ACTION_BAR),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ReaderDimens.PaddingSmall),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = ReaderDimens.PaddingMedium - 4.dp, vertical = ReaderDimens.PaddingSmall),
        ) {
            FilledTonalButton(onClick = onCopy, colors = actionButtonColors, modifier = Modifier.testTag(UiTestTags.READER_COPY_SELECTION_BUTTON)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Text(strings.reader.copySelection)
            }
            FilledTonalButton(onClick = onBookmark, colors = actionButtonColors, modifier = Modifier.testTag(UiTestTags.READER_BOOKMARK_SELECTION_BUTTON)) {
                Icon(Icons.Outlined.Bookmark, contentDescription = null)
                Text(strings.reader.addBookmarkAction)
            }
            FilledTonalButton(onClick = onNote, colors = actionButtonColors, modifier = Modifier.testTag(UiTestTags.READER_NOTE_SELECTION_BUTTON)) {
                Icon(Icons.AutoMirrored.Outlined.Note, contentDescription = null)
                Text(strings.reader.addNoteAction)
            }
        }
    }
}

@Composable
private fun ReaderSearchBar(
    onSearch: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        color = readerColors.surface.copy(alpha = 0.95f),
        contentColor = readerColors.textPrimary,
        tonalElevation = ReaderDimens.ElevationMedium,
        modifier = modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.common.backIconDesc, tint = readerColors.textPrimary)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(strings.common.search, color = readerColors.textTertiary) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = readerColors.textPrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = null, tint = readerColors.textTertiary)
                        }
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            IconButton(onClick = { onSearch(query) }) {
                Icon(Icons.Outlined.Search, contentDescription = strings.common.search, tint = readerColors.textPrimary)
            }
        }
    }
}
