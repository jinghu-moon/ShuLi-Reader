package com.shuli.reader.feature.reader.screen
import com.shuli.reader.feature.reader.screen.PageDirection
import com.shuli.reader.feature.reader.screen.ReaderSettingKey
import com.shuli.reader.feature.reader.screen.ReaderSettingValue
import com.shuli.reader.feature.reader.screen.ReaderIntent

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
import androidx.compose.runtime.DisposableEffect
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
import com.shuli.reader.core.reader.engine.ReaderCanvasView
import com.shuli.reader.feature.bookshelf.component.BookDetailsActions
import com.shuli.reader.feature.bookshelf.component.BookDetailsSheet
import com.shuli.reader.feature.bookshelf.component.BookDetailsTagActions
import com.shuli.reader.feature.bookshelf.component.BookDetailsTagState
import com.shuli.reader.feature.reader.render.ReaderCanvasEffects
import com.shuli.reader.feature.reader.settings.panel.GestureZoneEditorOverlay
import com.shuli.reader.feature.reader.render.toFallbackRenderInput
import com.shuli.reader.feature.reader.render.toRenderInput
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
    val orchestrator = remember { com.shuli.reader.feature.reader.render.ReaderRenderOrchestrator() }
    var batteryLevel by remember { mutableIntStateOf(100) }
    val dispatch = viewModel::dispatch

    // 电量广播（Screen 层采集，传给 toRenderInput 与 Canvas）
    val batteryContext = LocalContext.current
    DisposableEffect(batteryContext) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    batteryLevel = (level.toFloat() / scale.toFloat() * 100).toInt()
                }
            }
        }
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val flags = if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.content.Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        androidx.core.content.ContextCompat.registerReceiver(batteryContext, receiver, filter, flags)
        onDispose { batteryContext.unregisterReceiver(receiver) }
    }

    // 沉浸模式：根据偏好设置隐藏/显示系统栏
    val immersiveMode = uiState.readerPreferences.immersiveMode
    val activityContext = LocalContext.current
    DisposableEffect(immersiveMode) {
        val activity = activityContext as? android.app.Activity ?: return@DisposableEffect onDispose {}
        val window = activity.window
        val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
        if (immersiveMode) {
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            // 离开阅读页时恢复系统栏
            controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    // 内层返回：选区 > 各浮层 > 工具栏，依次回退
    BackHandler(
        enabled = uiState.selectedRange != null
            || uiState.showGestureEditor
            || uiState.showDirectory
            || uiState.showQuickSettings
            || uiState.showToolbar,
    ) {
        when {
            uiState.showGestureEditor -> dispatch(ReaderIntent.CloseGestureZoneEditor)
            uiState.selectedRange != null -> dispatch(ReaderIntent.ClearSelection)
            uiState.showDirectory -> dispatch(ReaderIntent.ToggleDirectory)
            uiState.showQuickSettings -> dispatch(ReaderIntent.ToggleQuickSettings)
            uiState.showToolbar -> dispatch(ReaderIntent.ToggleToolbar)
        }
    }

    // 必须始终在组合树中：不能放在 isLoading 条件分支内，
    // 否则每次 isLoading 切换都会 dispose LaunchedEffect，导致 openBook 反复触发。
    LaunchedEffect(bookId) {
        dispatch(ReaderIntent.OpenBook(bookId))
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
                ReaderCanvasEffects(
                    viewModel = viewModel,
                    canvasView = canvasView,
                )

                // Canvas 渲染：Orchestrator 驱动
                AndroidView(
                    modifier = Modifier.fillMaxSize().testTag(UiTestTags.READER_CANVAS).onGloballyPositioned { coordinates ->
                        dispatch(ReaderIntent.SetScreenSize(coordinates.size.width, coordinates.size.height))
                    },
                    factory = { context ->
                        ReaderCanvasView(context).apply {
                            canvasView = this
                            onPageChanged = { direction ->
                                when (direction) {
                                    com.shuli.reader.core.reader.engine.animation.PageDelegate.Direction.NEXT ->
                                        dispatch(ReaderIntent.TurnPage(PageDirection.NEXT))
                                    com.shuli.reader.core.reader.engine.animation.PageDelegate.Direction.PREV ->
                                        dispatch(ReaderIntent.TurnPage(PageDirection.PREV))
                                    else -> { /* NONE: no-op */ }
                                }
                            }
                            onTextSelected = { range ->
                                viewModel.navigationCoordinator.selectText(range)
                            }
                            onCenterClicked = { dispatch(ReaderIntent.ToggleToolbar) }
                            onGestureAction = { action ->
                                when (action) {
                                    com.shuli.reader.feature.reader.settings.GestureAction.PREV_PAGE ->
                                        dispatch(ReaderIntent.TurnPage(PageDirection.PREV))
                                    com.shuli.reader.feature.reader.settings.GestureAction.NEXT_PAGE ->
                                        dispatch(ReaderIntent.TurnPage(PageDirection.NEXT))
                                    com.shuli.reader.feature.reader.settings.GestureAction.SCROLL_UP ->
                                        dispatch(ReaderIntent.TurnPage(PageDirection.PREV))
                                    com.shuli.reader.feature.reader.settings.GestureAction.SCROLL_DOWN ->
                                        dispatch(ReaderIntent.TurnPage(PageDirection.NEXT))
                                    com.shuli.reader.feature.reader.settings.GestureAction.TOGGLE_TOOLBAR ->
                                        dispatch(ReaderIntent.ToggleToolbar)
                                    com.shuli.reader.feature.reader.settings.GestureAction.TOGGLE_DIRECTORY ->
                                        dispatch(ReaderIntent.ToggleDirectory)
                                    com.shuli.reader.feature.reader.settings.GestureAction.ADD_BOOKMARK ->
                                        dispatch(ReaderIntent.AddBookmark())
                                    com.shuli.reader.feature.reader.settings.GestureAction.TOGGLE_THEME ->
                                        dispatch(ReaderIntent.CycleTheme)
                                    com.shuli.reader.feature.reader.settings.GestureAction.TOGGLE_IMMERSIVE ->
                                        dispatch(
                                            ReaderIntent.UpdateSetting(
                                                ReaderSettingKey.IMMERSIVE_MODE,
                                                ReaderSettingValue.Bool(
                                                    !viewModel.uiState.value.readerPreferences.immersiveMode,
                                                ),
                                            )
                                        )
                                    com.shuli.reader.feature.reader.settings.GestureAction.NONE -> { /* no-op */ }
                                }
                            }
                        }
                    },
                    update = { view ->
                        val (headerRes, footerRes) = viewModel.readerProgressResolver.resolveHeaderAndFooterSlots()
                        val input = uiState.toRenderInput(
                            density = density,
                            headerSlots = headerRes,
                            footerSlots = footerRes,
                            batteryLevel = batteryLevel,
                            pageDelegate = viewModel.pageDelegate,
                        )
                        // §11.1.1.1: T0 fallback — 首帧超预算时用持久化摘要渲染骨架页
                        val digest = uiState.snapshotDigest
                        if (uiState.isLoading && digest != null) {
                            val fallbackInput = digest.toFallbackRenderInput(
                                density = density,
                                layoutVersion = uiState.layoutVersion,
                            )
                            orchestrator.applyWithFallback(view, input, fallbackInput, budgetMs = 200)
                        } else {
                            orchestrator.apply(view, input)
                        }
                    },
                )

                // 顶部工具栏
                ReaderTopBar(
                    uiState = uiState,
                    bookId = bookId,
                    onBackClick = onBackClick,
                    onToggleSearch = { dispatch(ReaderIntent.ToggleSearch) },
                    onPreviousSearchResult = { dispatch(ReaderIntent.PrevSearchResult) },
                    onNextSearchResult = { dispatch(ReaderIntent.NextSearchResult) },
                    onShowBookInfo = { showBookDetailsSheet = true },
                    modifier = Modifier.align(Alignment.TopCenter),
                )

                // 搜索输入栏
                if (uiState.showSearch) {
                    ReaderSearchBar(
                        onSearch = { query -> dispatch(ReaderIntent.Search(query)) },
                        onClose = { dispatch(ReaderIntent.ToggleSearch) },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }

                // 侧边亮度条
                AnimatedVisibility(
                    visible = uiState.showToolbar && !uiState.showSearch,
                    enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(com.shuli.reader.core.reader.engine.animation.ReaderMotionTokens.SHORT_MS.toInt())) + fadeIn(animationSpec = tween(com.shuli.reader.core.reader.engine.animation.ReaderMotionTokens.SHORT_MS.toInt())),
                    exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(com.shuli.reader.core.reader.engine.animation.ReaderMotionTokens.SHORT_MS.toInt())) + fadeOut(animationSpec = tween(com.shuli.reader.core.reader.engine.animation.ReaderMotionTokens.SHORT_MS.toInt())),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    com.shuli.reader.feature.reader.component.VerticalBrightnessSlider(
                        brightness = uiState.readerPreferences.brightness,
                        onBrightnessChange = { value, _ -> dispatch(ReaderIntent.UpdateSetting(ReaderSettingKey.BRIGHTNESS, ReaderSettingValue.Float(value))) },
                        modifier = Modifier.padding(end = 20.dp, top = 20.dp).height(200.dp)
                    )
                }

                // 底部工具栏
                ReaderBottomBar(
                    uiState = uiState,
                    onToggleDirectory = { dispatch(ReaderIntent.ToggleDirectory) },
                    onCycleTheme = { dispatch(ReaderIntent.CycleTheme) },
                    onAddBookmark = { dispatch(ReaderIntent.AddBookmark(pageOnly = true)) },
                    onToggleQuickSettings = { dispatch(ReaderIntent.ToggleQuickSettings) },
                    onOpenChapter = { index -> dispatch(ReaderIntent.OpenChapter(index)) },
                    onStartPageScrub = { dispatch(ReaderIntent.StartPageScrub) },
                    onScrubToPage = { page -> dispatch(ReaderIntent.ScrubToPage(page)) },
                    onCommitPageScrub = { dispatch(ReaderIntent.CommitPageScrub) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )

                // 选区操作栏
                uiState.selectedRange?.let { range ->
                    ReaderSelectionActionBar(
                        onCopy = {
                            range.selectedText?.takeIf { it.isNotBlank() }?.let { text ->
                                clipboardManager.setText(AnnotatedString(text))
                            }
                            dispatch(ReaderIntent.ClearSelection)
                        },
                        onBookmark = { dispatch(ReaderIntent.AddBookmarkFromSelection) },
                        onNote = { dispatch(ReaderIntent.AddNoteFromSelection) },
                        onLookup = {
                            range.selectedText?.takeIf { it.isNotBlank() }?.let { text ->
                                dispatch(ReaderIntent.LookupWord(text, ""))
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(ReaderDimens.PaddingMedium),
                    )
                }

                if (uiState.showGestureEditor) {
                    GestureZoneEditorOverlay(
                        config = uiState.readerPreferences.gestureConfig,
                        onConfigChange = { config ->
                            dispatch(
                                ReaderIntent.UpdateSetting(
                                    ReaderSettingKey.GESTURE_CONFIG,
                                    ReaderSettingValue.GestureConfigValue(config),
                                ),
                            )
                        },
                        onDismiss = { dispatch(ReaderIntent.CloseGestureZoneEditor) },
                    )
                }
            }
        }

        // 弹出面板（放在 Box 外部，避免 enableEdgeToEdge 布局异常）
        ReaderOverlayPanels(uiState = uiState, dispatch = viewModel::dispatch)
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
    onLookup: () -> Unit,
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
            FilledTonalButton(onClick = onLookup, colors = actionButtonColors) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Text(strings.reader.lookupWord)
            }
            // 分隔线
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(readerColors.divider)
            )
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
