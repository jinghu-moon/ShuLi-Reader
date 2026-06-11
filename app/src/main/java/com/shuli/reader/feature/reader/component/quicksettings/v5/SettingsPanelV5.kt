package com.shuli.reader.feature.reader.component.quicksettings.v5

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.feature.reader.component.quicksettings.ThemeColorRow
import com.shuli.reader.feature.reader.settings.ChromePrefs
import com.shuli.reader.feature.reader.settings.LayoutPrefs
import com.shuli.reader.feature.reader.settings.OverlayPrefs
import com.shuli.reader.feature.reader.settings.SettingsTab
import com.shuli.reader.feature.reader.settings.StylePrefs
import com.shuli.reader.feature.reader.settings.toChromePrefs
import com.shuli.reader.feature.reader.settings.toLayoutPrefs
import com.shuli.reader.feature.reader.settings.toOverlayPrefs
import com.shuli.reader.feature.reader.settings.toStylePrefs
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import kotlinx.coroutines.launch

/**
 * 设置面板 V5 入口：BottomSheetScaffold 双态（Peek 30% / Expanded 70%）。
 *
 * - Peek 态常驻：FontSizeStepper + ThemeColorRow
 * - Expanded 态：TabRow + SettingsCard + 预览区
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPanelV5(
    prefs: ReaderPreferences,
    previewText: String?,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onSettingChanged: (key: String, value: Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readerColors = LocalReaderColorScheme.current
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
        )
    )
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val overlayPrefs: OverlayPrefs = remember(prefs) { prefs.toOverlayPrefs() }
    val chromePrefs: ChromePrefs = remember(prefs) { prefs.toChromePrefs() }
    val stylePrefs: StylePrefs = remember(prefs) { prefs.toStylePrefs() }
    val layoutPrefs: LayoutPrefs = remember(prefs) { prefs.toLayoutPrefs() }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 220.dp,
        containerColor = readerColors.surface,
        sheetContainerColor = readerColors.surface,
        sheetContentColor = readerColors.textPrimary,
        sheetDragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            val target = if (scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded) {
                                SheetValue.Expanded
                            } else {
                                SheetValue.PartiallyExpanded
                            }
                            scaffoldState.bottomSheetState.expand()
                        }
                    }
                    .testTag("SettingsPanelV5_DragHandle"),
                contentAlignment = Alignment.Center,
            ) {
                BottomSheetDefaults.DragHandle(color = readerColors.textSecondary)
            }
        },
        sheetContent = {
            SettingsPanelV5SheetContent(
                prefs = prefs,
                previewText = previewText,
                onFontSizeChange = onFontSizeChange,
                onThemeChange = onThemeChange,
                onSettingChanged = onSettingChanged,
                overlayPrefs = overlayPrefs,
                chromePrefs = chromePrefs,
                stylePrefs = stylePrefs,
                layoutPrefs = layoutPrefs,
                selectedTab = selectedTab,
                onTabChange = { selectedTab = it },
            )
        },
        modifier = modifier.testTag("SettingsPanelV5"),
    ) {
        // 主体内容（由上层 ReaderScreen 决定，此处留空）
        Box(modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPanelV5SheetContent(
    prefs: ReaderPreferences,
    previewText: String?,
    onFontSizeChange: (Float) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onSettingChanged: (key: String, value: Any) -> Unit,
    overlayPrefs: OverlayPrefs,
    chromePrefs: ChromePrefs,
    stylePrefs: StylePrefs,
    layoutPrefs: LayoutPrefs,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current
    val targetExpanded = remember { mutableIntStateOf(selectedTab) }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
        // PeekContent 始终渲染（字号 + 主题色）
        Column(modifier = Modifier.testTag("SettingsPanelV5_PeekContent")) {
            FontSizeStepper(
                value = prefs.fontSize,
                onValueChange = onFontSizeChange,
            )
            ThemeColorRow(
                currentTheme = prefs.backgroundColor,
                onThemeChange = onThemeChange,
                customBackgroundColor = prefs.customBackgroundColor,
            )
        }

        // ExpandedContent：TabRow + Tab content + PreviewArea，随展开状态渐入
        AnimatedVisibility(
            visible = true, // BottomSheetScaffold 控制可见性；此处始终渲染以便测试
            enter = fadeIn() + slideInHorizontally { it / 4 },
            exit = fadeOut() + slideOutHorizontally { it / 4 },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("SettingsPanelV5_ExpandedContent"),
            ) {
                // 预览区
                SettingsPreviewArea(
                    layoutPrefs = layoutPrefs,
                    stylePrefs = stylePrefs,
                    previewText = previewText,
                )

                // TabRow
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = readerColors.surface,
                    contentColor = readerColors.textPrimary,
                    divider = {},
                    modifier = Modifier.testTag("SettingsPanelV5_TabRow"),
                ) {
                    SettingsTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { onTabChange(index) },
                            text = {
                                Text(
                                    SettingsTab.displayName(tab),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }

                // Tab 内容
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { -it / 4 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                                (slideOutHorizontally { it / 4 } + fadeOut())
                        }
                    },
                    label = "settings-tab-content",
                    modifier = Modifier.weight(1f),
                ) { tab ->
                    SettingsTabContent(
                        tab = SettingsTab.entries[tab],
                        prefs = prefs,
                        layoutPrefs = layoutPrefs,
                        stylePrefs = stylePrefs,
                        chromePrefs = chromePrefs,
                        overlayPrefs = overlayPrefs,
                        onSettingChanged = onSettingChanged,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTabContent(
    tab: SettingsTab,
    prefs: ReaderPreferences,
    layoutPrefs: LayoutPrefs,
    stylePrefs: StylePrefs,
    chromePrefs: ChromePrefs,
    overlayPrefs: OverlayPrefs,
    onSettingChanged: (key: String, value: Any) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (tab) {
            SettingsTab.TYPE_AND_FONT -> {
                SettingsCard(title = "基础排版", initiallyExpanded = true) {
                    StepperSlider(
                        value = layoutPrefs.lineSpacing,
                        onValueChange = { onSettingChanged("line_spacing", it) },
                        valueRange = 1.0f..2.5f,
                        step = 0.1f,
                        label = "行距",
                        formatValue = { "%.1f".format(it) },
                    )
                    StepperSlider(
                        value = layoutPrefs.paragraphSpacing,
                        onValueChange = { onSettingChanged("paragraph_spacing", it) },
                        valueRange = 0.5f..2.0f,
                        step = 0.1f,
                        label = "段距",
                        formatValue = { "%.1f".format(it) },
                    )
                    StepperSlider(
                        value = layoutPrefs.letterSpacing,
                        onValueChange = { onSettingChanged("letter_spacing", it) },
                        valueRange = 0f..0.2f,
                        step = 0.01f,
                        label = "字距",
                        formatValue = { "%.2f".format(it) },
                    )
                }
                SettingsCard(title = "边距", initiallyExpanded = true) {
                    VisualMarginControl(
                        margins = MarginValues(
                            top = layoutPrefs.marginTop ?: layoutPrefs.marginVertical,
                            bottom = layoutPrefs.marginBottom ?: layoutPrefs.marginVertical,
                            left = layoutPrefs.marginLeft ?: layoutPrefs.marginHorizontal,
                            right = layoutPrefs.marginRight ?: layoutPrefs.marginHorizontal,
                        ),
                        onMarginsChange = { m ->
                            onSettingChanged("margin_top", m.top)
                            onSettingChanged("margin_bottom", m.bottom)
                            onSettingChanged("margin_left", m.left)
                            onSettingChanged("margin_right", m.right)
                        },
                    )
                }
            }
            SettingsTab.APPEARANCE -> {
                SettingsCard(title = "页眉页脚", initiallyExpanded = true) {
                    HeaderFooterWireframe(
                        headerSlots = Triple(
                            com.shuli.reader.core.reader.SlotContent.NONE,
                            com.shuli.reader.core.reader.SlotContent.CHAPTER_TITLE,
                            com.shuli.reader.core.reader.SlotContent.NONE,
                        ),
                        footerSlots = Triple(
                            com.shuli.reader.core.reader.SlotContent.NONE,
                            com.shuli.reader.core.reader.SlotContent.BOOK_PROGRESS_PERCENT,
                            com.shuli.reader.core.reader.SlotContent.NONE,
                        ),
                        onHeaderSlotChange = { _, _ -> },
                        onFooterSlotChange = { _, _ -> },
                    )
                    StepperSlider(
                        value = chromePrefs.headerFooterAlpha,
                        onValueChange = { onSettingChanged("header_footer_alpha", it) },
                        valueRange = 0.1f..1.0f,
                        step = 0.1f,
                        label = "透明度",
                        formatValue = { "%.0f%%".format(it * 100) },
                    )
                }
            }
            SettingsTab.BEHAVIOR -> {
                SettingsCard(title = "交互行为", initiallyExpanded = true) {
                    Text(
                        text = "（行为类设置项将在此处渲染）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalReaderColorScheme.current.textSecondary,
                    )
                }
                SettingsCard(title = "高级", initiallyExpanded = false) {
                    Text(
                        text = "（高级设置项默认收起）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalReaderColorScheme.current.textSecondary,
                    )
                }
            }
        }
    }
}
