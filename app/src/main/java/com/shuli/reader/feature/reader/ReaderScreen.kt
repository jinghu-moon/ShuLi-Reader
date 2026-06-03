package com.shuli.reader.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import com.shuli.reader.ui.theme.ReaderDimens
import com.shuli.reader.core.reader.animation.ReaderMotionTokens


import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.core.tts.TtsState
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.ReaderCanvasView
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.WindowManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.toFactoryType
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotResolution
import com.shuli.reader.feature.reader.component.CanvasSlider
import com.shuli.reader.feature.reader.component.DirectoryDialog
import com.shuli.reader.feature.reader.component.QuickSettingsActions
import com.shuli.reader.feature.reader.component.QuickSettingsSheet
import com.shuli.reader.ui.testing.UiTestTags
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme
import com.shuli.reader.core.font.FontManager

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    var batteryLevel by remember { mutableIntStateOf(100) }

    val density = androidx.compose.ui.platform.LocalDensity.current.density
    LaunchedEffect(density) {
        viewModel.setDensity(density)
    }

    LaunchedEffect(bookId) {
        viewModel.openBook(bookId)
    }

    // 内层返回：选区 > 各浮层 > 工具栏，依次回退；都没有时穿透到 Activity 层（退到书架）
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

    // 监听亮度偏好并实时更新 Window 亮度属性
    val brightness = uiState.readerPreferences.brightness
    LaunchedEffect(brightness) {
        activity?.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.screenBrightness = if (brightness < 0f) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                brightness.coerceIn(0.01f, 1f)
            }
            window.attributes = layoutParams
        }
    }

    // 监听屏幕常亮偏好
    val keepScreenOn = uiState.readerPreferences.keepScreenOn
    LaunchedEffect(keepScreenOn) {
        activity?.window?.let { window ->
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // 动态监听系统电量广播
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    batteryLevel = (level.toFloat() / scale.toFloat() * 100).roundToInt()
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val flags = if (android.os.Build.VERSION.SDK_INT >= 33) {
            android.content.Context.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, flags)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.pauseTtsOnBackground()
                    viewModel.pauseReadingSession()
                }
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.resumeReadingSession()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.releaseReaderResources()
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
                // 排版变化检测：在 update 块内追踪 layoutVersion，
                // 确保 crossfade 在新页面实际到达时触发（而非版本号递增时）
                val layoutVersionRef = remember { mutableIntStateOf(uiState.layoutVersion) }

                // 持有 View 引用，用于 LaunchedEffect 直接操作（绕过 recomposition）
                var canvasView by remember { mutableStateOf<ReaderCanvasView?>(null) }

                // ── LaunchedEffect：像亮度一样绕过 recomposition 直接操作 View ──

                // 主题颜色
                val themeColors = uiState.themeColors
                LaunchedEffect(themeColors) {
                    canvasView?.setThemeColors(themeColors)
                }

                // 排版属性（字号/字距/字重/字体/对齐）→ 立即更新 Paint，拖动滑块时实时反馈
                val prefs = uiState.readerPreferences
                LaunchedEffect(prefs.fontSize, prefs.letterSpacing, prefs.fontWeight, prefs.readingFont, prefs.textAlign) {
                    canvasView?.updatePaintSnapshot(
                        textSize = prefs.fontSize * density,
                        letterSpacing = prefs.letterSpacing,
                        fakeBold = prefs.fontWeight == ReaderFontWeight.BOLD,
                        fontKey = prefs.readingFont,
                        textAlign = prefs.textAlign,
                        invalidateContent = true,
                    )
                    canvasView?.textPaint?.let { viewModel.syncTextMeasurerPaint(it) }
                }

                // 页眉页脚
                LaunchedEffect(prefs.headerFooterAlpha, prefs.showProgress, prefs.showHeaderLine, prefs.showFooterLine) {
                    canvasView?.updateHeaderFooter(
                        viewModel.resolveHeaderSlots(),
                        viewModel.resolveFooterSlots(),
                        prefs.headerFooterAlpha,
                        prefs.showProgress,
                        prefs.showHeaderLine,
                        prefs.showFooterLine,
                    )
                }

                // 标题样式
                LaunchedEffect(prefs.titleStyle) {
                    canvasView?.setTitleStyle(prefs.titleStyle)
                }

                // 边缘翻页
                LaunchedEffect(prefs.edgeTurnPage) {
                    canvasView?.setEdgeTurnPageEnabled(prefs.edgeTurnPage)
                }

                // 边缘触摸宽度
                LaunchedEffect(prefs.edgeWidthPercent) {
                    canvasView?.setEdgeWidthPercent(prefs.edgeWidthPercent)
                }

                // 页眉页脚字号比例
                LaunchedEffect(prefs.headerFontSizeRatio, prefs.footerFontSizeRatio) {
                    canvasView?.setHeaderTextRatio(prefs.headerFontSizeRatio)
                    canvasView?.setFooterTextRatio(prefs.footerFontSizeRatio)
                }

                // 电池
                LaunchedEffect(batteryLevel) {
                    canvasView?.setBatteryLevel(batteryLevel)
                }

                // TTS 高亮
                val ttsActiveRange = uiState.ttsActiveRange
                LaunchedEffect(ttsActiveRange) {
                    canvasView?.setTtsActiveRange(ttsActiveRange)
                }

                // 选区清除
                val selectedRange = uiState.selectedRange
                LaunchedEffect(selectedRange) {
                    if (selectedRange == null) {
                        canvasView?.clearSelection()
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize().testTag(UiTestTags.READER_CANVAS).onGloballyPositioned { coordinates ->
                        viewModel.setScreenSize(coordinates.size.width, coordinates.size.height)
                    },
                    factory = { context ->
                        ReaderCanvasView(context).apply {
                            canvasView = this
                            setThemeColors(
                                ReaderTheme.PAPER
                                    .toReaderColorScheme()
                                    .toCanvasThemeColors(),
                            )
                            onPageChanged = viewModel::handlePageDirection
                            onPageChangedSlots = {
                                Pair(viewModel.resolveHeaderSlots(), viewModel.resolveFooterSlots())
                            }
                            onTextSelected = viewModel::selectText
                            onCenterClicked = viewModel::toggleToolbar
                            setPageDelegate(viewModel.pageDelegate)
                        }
                    },
                    update = { view ->
                        // 仅处理页面数据更新（页面引用变化才触发）
                        val page = uiState.currentPage ?: return@AndroidView
                        val nextPage = uiState.currentChapter?.getPage(uiState.pageIndex + 1)
                        val prevPage = uiState.currentChapter?.getPage(uiState.pageIndex - 1)

                        val isLayoutChange = layoutVersionRef.intValue != uiState.layoutVersion
                        if (isLayoutChange) layoutVersionRef.intValue = uiState.layoutVersion
                        view.setPage(page, nextPage, prevPage, uiState.currentChapter?.content ?: "", uiState.pageRenderMode, isLayoutChange = isLayoutChange)
                    },
                )

                // 悬浮顶部工具栏
                AnimatedVisibility(
                    visible = uiState.showToolbar && !uiState.showSearch,
                    enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())) + fadeIn(animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())),
                    exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())) + fadeOut(animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(readerColors.surface.copy(alpha = 0.95f))
                            .statusBarsPadding()
                    ) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = uiState.bookTitle.ifBlank { "${strings.appName} - #$bookId" },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = readerColors.textPrimary
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = onBackClick,
                                    modifier = Modifier.testTag(UiTestTags.READER_BACK_BUTTON),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = strings.backIconDesc,
                                        tint = readerColors.textPrimary
                                    )
                                }
                            },
                            actions = {
                                ReaderSearchControls(
                                    currentIndex = uiState.currentSearchResultIndex,
                                    total = uiState.searchResults.size,
                                    onPrevious = viewModel::goToPreviousSearchResult,
                                    onNext = viewModel::goToNextSearchResult,
                                )
                                IconButton(onClick = viewModel::toggleSearch) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = strings.search,
                                        tint = readerColors.textPrimary,
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                titleContentColor = readerColors.textPrimary,
                                navigationIconContentColor = readerColors.textPrimary,
                                actionIconContentColor = readerColors.textPrimary,
                             ),
                        )
                    }
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
                AnimatedVisibility(
                    visible = uiState.showToolbar && !uiState.showSearch,
                    enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())) + fadeIn(animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())),
                    exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())) + fadeOut(animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    com.shuli.reader.feature.reader.component.VerticalBrightnessSlider(
                        brightness = uiState.readerPreferences.brightness,
                        onBrightnessChange = viewModel::setBrightness,
                        modifier = Modifier.padding(end = 12.dp, top = 24.dp).height(240.dp)
                    )
                }

                // 悬浮底部工具栏
                AnimatedVisibility(
                    visible = uiState.showToolbar && !uiState.showSearch,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())) + fadeIn(animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())) + fadeOut(animationSpec = tween(ReaderMotionTokens.SHORT_MS.toInt())),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Surface(
                        color = readerColors.surface.copy(alpha = 0.95f),
                        contentColor = readerColors.textPrimary,
                        tonalElevation = ReaderDimens.ElevationMedium,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    ) {
                        Column {
                            // 章节快捷跳转
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { if (uiState.chapterIndex > 0) viewModel.openChapter(uiState.chapterIndex - 1) }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.previousChapter, tint = readerColors.textPrimary)
                                }
                                Text(
                                    text = uiState.chapterTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = readerColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                )
                                IconButton(
                                    onClick = { if (uiState.chapterIndex + 1 < uiState.totalChapters) viewModel.openChapter(uiState.chapterIndex + 1) }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = strings.nextChapter, tint = readerColors.textPrimary)
                                }
                            }

                            // 页码进度条
                            if (uiState.totalPages > 1) {
                                var isScrubbing by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${uiState.pageIndex + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = readerColors.textSecondary,
                                        modifier = Modifier.width(32.dp)
                                    )
                                    CanvasSlider(
                                        value = uiState.pageIndex.toFloat(),
                                        onValueChange = { v ->
                                            val p = v.roundToInt()
                                            if (!isScrubbing) {
                                                viewModel.startPageScrub()
                                                isScrubbing = true
                                            }
                                            viewModel.scrubToPage(p)
                                        },
                                        onValueChangeFinished = {
                                            viewModel.commitPageScrub()
                                            isScrubbing = false
                                        },
                                        valueRange = 0f..(uiState.totalPages - 1).coerceAtLeast(1).toFloat(),
                                        thumbColor = readerColors.accent,
                                        activeTrackColor = readerColors.accent,
                                        inactiveTrackColor = readerColors.divider,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${uiState.totalPages}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = readerColors.textSecondary,
                                        modifier = Modifier.width(32.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }

                            // 操作按钮组
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = viewModel::toggleDirectory,
                                    modifier = Modifier.testTag(UiTestTags.READER_DIRECTORY_BUTTON)
                                ) {
                                    @Suppress("DEPRECATION")
                                    Icon(Icons.Outlined.List, contentDescription = strings.directoryTab, tint = readerColors.textPrimary)
                                }
                                IconButton(onClick = { viewModel.cycleTheme() }) {
                                    val isDark = uiState.readerPreferences.backgroundColor == ReaderTheme.DARK
                                            || uiState.readerPreferences.backgroundColor == ReaderTheme.OLED
                                    Icon(
                                        imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                        contentDescription = strings.themeModeLabel,
                                        tint = readerColors.textPrimary,
                                    )
                                }
                                IconButton(onClick = { viewModel.addBookmark() }) {
                                    Icon(
                                        Icons.Outlined.Bookmark,
                                        contentDescription = strings.addBookmarkAction,
                                        tint = readerColors.textPrimary,
                                    )
                                }
                                IconButton(onClick = viewModel::toggleQuickSettings) {
                                    Icon(Icons.Outlined.Settings, contentDescription = strings.readerPreferences, tint = readerColors.textPrimary)
                                }
                            }
                        }
                    }
                }

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
                            .navigationBarsPadding()
                            .padding(ReaderDimens.PaddingMedium),
                    )
                }

                if (uiState.showDirectory) {
                    DirectoryDialog(
                        chapters = uiState.chapterTitles,
                        currentChapterIndex = uiState.chapterIndex,
                        chapterWordCounts = uiState.chapterWordCounts,
                        bookmarks = uiState.bookmarks,
                        notes = uiState.notes,
                        onChapterClick = { index ->
                            viewModel.openChapter(index)
                            viewModel.toggleDirectory()
                        },
                        onBookmarkClick = { bookmark ->
                            viewModel.goToBookmark(bookmark)
                            viewModel.toggleDirectory()
                        },
                        onBookmarkDelete = { bookmark ->
                            viewModel.deleteBookmark(bookmark)
                        },
                        onNoteClick = { note ->
                            viewModel.goToNote(note)
                            viewModel.toggleDirectory()
                        },
                        onNoteDelete = { note ->
                            viewModel.deleteNote(note)
                        },
                        onDismiss = {
                            viewModel.toggleDirectory()
                        }
                    )
                }
            }
        }

        // ModalBottomSheet 放在 Box 外部，作为 Scaffold 直接子项
        // 避免嵌套 Box 导致 enableEdgeToEdge() 下布局异常
        if (uiState.showQuickSettings) {
            val quickSettingsActions = remember(viewModel) {
                QuickSettingsActions(
                    onDismiss = viewModel::toggleQuickSettings,
                    onBrightnessChange = viewModel::setBrightness,
                    onFontSizeChange = viewModel::setFontSize,
                    onLineSpacingChange = viewModel::setLineSpacing,
                    onParagraphSpacingChange = viewModel::setParagraphSpacing,
                    onIndentChange = viewModel::setIndent,
                    onMarginVerticalChange = viewModel::setMarginVertical,
                    onMarginHorizontalChange = viewModel::setMarginHorizontal,
                    onReadingFontChange = viewModel::setReadingFont,
                    onPageAnimTypeChange = { type ->
                        viewModel.setPageAnimType(type.toFactoryType())
                    },
                    onThemeChange = viewModel::setReaderTheme,
                    onLetterSpacingChange = viewModel::setLetterSpacing,
                    onFontWeightChange = viewModel::setFontWeight,
                    onTextAlignChange = viewModel::setTextAlign,
                    onChineseConvertChange = viewModel::setChineseConvert,
                    onUseZhLayoutChange = viewModel::setUseZhLayout,
                    onPanguSpacingChange = viewModel::setPanguSpacing,
                    onApplyPreset = viewModel::applyPreset,
                    onSavePreset = viewModel::saveCurrentAsPreset,
                    onRenamePreset = viewModel::renamePreset,
                    onDeletePreset = viewModel::deletePreset,
                    onResetToDefault = viewModel::resetToDefault,
                    onHeaderVisibilityChange = viewModel::setHeaderVisibility,
                    onHeaderLeftChange = viewModel::setHeaderLeft,
                    onHeaderCenterChange = viewModel::setHeaderCenter,
                    onHeaderRightChange = viewModel::setHeaderRight,
                    onFooterVisibilityChange = viewModel::setFooterVisibility,
                    onFooterLeftChange = viewModel::setFooterLeft,
                    onFooterCenterChange = viewModel::setFooterCenter,
                    onFooterRightChange = viewModel::setFooterRight,
                    onHeaderFooterAlphaChange = viewModel::setHeaderFooterAlpha,
                    onHeaderMarginTopChange = viewModel::setHeaderMarginTop,
                    onFooterMarginBottomChange = viewModel::setFooterMarginBottom,
                    onShowProgressChange = viewModel::setShowProgress,
                    onTitleAlignChange = viewModel::setTitleAlign,
                    onTitleSizeOffsetChange = viewModel::setTitleSizeOffset,
                    onTitleMarginTopChange = viewModel::setTitleMarginTop,
                    onTitleMarginBottomChange = viewModel::setTitleMarginBottom,
                    onKeepScreenOnChange = viewModel::setKeepScreenOn,
                    onVolumeKeyTurnPageChange = viewModel::setVolumeKeyTurnPage,
                    onEdgeTurnPageChange = viewModel::setEdgeTurnPage,
                    onEdgeWidthPercentChange = viewModel::setEdgeWidthPercent,
                    onShowHeaderLineChange = viewModel::setShowHeaderLine,
                    onShowFooterLineChange = viewModel::setShowFooterLine,
                    onHeaderFontSizeRatioChange = viewModel::setHeaderFontSizeRatio,
                    onFooterFontSizeRatioChange = viewModel::setFooterFontSizeRatio,
                    onBottomJustifyChange = viewModel::setBottomJustify,
                    ttsState = uiState.ttsState,
                    onTtsStart = { viewModel.startTts() },
                    onTtsPause = { viewModel.pauseTts() },
                    onTtsStop = { viewModel.stopTts() },
                    onTtsSpeedChange = { viewModel.setTtsSpeed(it) },
                    onTtsPitchChange = { viewModel.setTtsPitch(it) },
                    onImportFont = { viewModel.importFont(it) },
                    onDeleteFont = { viewModel.deleteFont(it) },
                )
            }
            QuickSettingsSheet(
                uiState = uiState,
                actions = quickSettingsActions,
            )
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(ReaderDimens.PaddingSmall),
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

@Composable
private fun ReaderSearchControls(
    currentIndex: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalAppStrings.current
    val readerColors = LocalReaderColorScheme.current
    if (total <= 0 || currentIndex < 0) return

    Row(modifier = modifier) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.testTag(UiTestTags.READER_SEARCH_PREV_BUTTON),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = strings.previousSearchResult,
                tint = readerColors.textPrimary,
            )
        }
        Text(
            text = "${currentIndex + 1}/$total",
            style = MaterialTheme.typography.labelMedium,
            color = readerColors.textSecondary,
            modifier = Modifier.testTag(UiTestTags.READER_SEARCH_RESULT_COUNTER),
        )
        IconButton(
            onClick = onNext,
            modifier = Modifier.testTag(UiTestTags.READER_SEARCH_NEXT_BUTTON),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = strings.nextSearchResult,
                tint = readerColors.textPrimary,
            )
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
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.backIconDesc,
                    tint = readerColors.textPrimary,
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(strings.search, color = readerColors.textTertiary)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = readerColors.textPrimary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
                                tint = readerColors.textTertiary,
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            IconButton(onClick = { onSearch(query) }) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = strings.search,
                    tint = readerColors.textPrimary,
                )
            }
        }
    }
}
