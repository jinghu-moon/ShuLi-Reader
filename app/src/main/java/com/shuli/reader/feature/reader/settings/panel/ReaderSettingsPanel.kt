package com.shuli.reader.feature.reader.settings.panel

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.feature.reader.settings.SettingsScope
import com.shuli.reader.feature.reader.settings.panel.tabs.AppearanceTab
import com.shuli.reader.feature.reader.settings.panel.tabs.BehaviorTab
import com.shuli.reader.feature.reader.settings.panel.tabs.TypeAndFontTab
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.feature.reader.settings.SettingsTab
import com.shuli.reader.ui.theme.LocalReaderColorScheme
import com.shuli.reader.ui.theme.ReaderMaterialTheme

/**
 * 设置面板入口（BottomSheetScaffold 版本，主要供 UI 测试使用）。
 *
 * 实际 App 路径走 [ReaderSettingsModal]，两者共享 [ReaderSettingsSheetContent]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsPanel(
    prefs: ReaderPreferences,
    onThemeChange: (ReaderTheme) -> Unit,
    onCustomThemeConfirm: (bg: Int, text: Int, title: Int, headerFooter: Int) -> Unit,
    onSettingChanged: (key: String, value: Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val readerColors = LocalReaderColorScheme.current
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
        )
    )
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 280.dp,
        containerColor = readerColors.surface,
        sheetContainerColor = readerColors.surface,
        sheetContentColor = readerColors.textPrimary,
        sheetDragHandle = {
            BottomSheetDefaults.DragHandle(color = readerColors.textSecondary)
        },
        sheetContent = {
            ReaderMaterialTheme(readerTheme = prefs.backgroundColor) {
                ReaderSettingsSheetContent(
                    prefs = prefs,
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    onThemeChange = onThemeChange,
                    onCustomThemeConfirm = onCustomThemeConfirm,
                    onSettingChanged = onSettingChanged,
                )
            }
        },
        modifier = modifier.testTag("ReaderSettingsPanel"),
    ) {
        Box(modifier = Modifier.fillMaxSize())
    }
}

/**
 * 设置面板共享内容：Peek 区 + TabRow + 三 Tab 卡片。
 *
 * 写操作经 [onSettingChanged] 泛型通道；作用域 / 字体 / 手势等需要上层数据的功能
 * 通过带默认值的扩展参数注入，测试入口可省略。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheetContent(
    prefs: ReaderPreferences,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onCustomThemeConfirm: (bg: Int, text: Int, title: Int, headerFooter: Int) -> Unit,
    onSettingChanged: (key: String, value: Any) -> Unit,
    modifier: Modifier = Modifier,
    onContinuousSettingChanged: (key: String, value: Any, finished: Boolean) -> Unit = { _, _, _ -> },
    settingsScope: SettingsScope = SettingsScope.GLOBAL,
    hasBookOverrides: Boolean = false,
    onScopeChange: (SettingsScope) -> Unit = {},
    onResetDefaults: () -> Unit = {},
    customFonts: List<FontManager.FontEntry> = emptyList(),
    onImportFont: (android.net.Uri) -> Unit = {},
    onDeleteFont: (String) -> Unit = {},
    gestureConfig: GestureConfig = GestureConfig(),
    onGestureChange: (GestureConfig) -> Unit = {},
    onOpenGestureZoneEditor: () -> Unit = {},
) {
    val readerColors = LocalReaderColorScheme.current

    Column(modifier = modifier.fillMaxWidth()) {
        // ── Peek 区 ──
        SettingsPeekContent(
            prefs = prefs,
            settingsScope = settingsScope,
            onThemeChange = onThemeChange,
            onCustomThemeConfirm = onCustomThemeConfirm,
            onSettingChanged = onSettingChanged,
            onScopeChange = onScopeChange,
        )

        // ── Expanded 区 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("ReaderSettingsPanel_ExpandedContent"),
        ) {
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = readerColors.surface,
                contentColor = readerColors.textPrimary,
                divider = {},
                modifier = Modifier.testTag("ReaderSettingsPanel_TabRow"),
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

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val springSpec = spring<IntOffset>(dampingRatio = Spring.DampingRatioMediumBouncy)
                    if (targetState > initialState) {
                        (slideInHorizontally(animationSpec = springSpec) { it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally(animationSpec = springSpec) { -it / 4 } + fadeOut())
                    } else {
                        (slideInHorizontally(animationSpec = springSpec) { -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally(animationSpec = springSpec) { it / 4 } + fadeOut())
                    }
                },
                label = "settings-tab-content",
                modifier = Modifier.weight(1f),
            ) { tabIndex ->
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState),
                ) {
                    when (SettingsTab.entries[tabIndex]) {
                        SettingsTab.TYPE_AND_FONT -> TypeAndFontTab(
                            prefs = prefs,
                            onSettingChanged = onSettingChanged,
                            customFonts = customFonts,
                            onImportFont = onImportFont,
                            onDeleteFont = onDeleteFont,
                        )
                        SettingsTab.APPEARANCE -> AppearanceTab(
                            prefs = prefs,
                            onSettingChanged = onSettingChanged,
                            onContinuousSettingChanged = onContinuousSettingChanged,
                        )
                        SettingsTab.BEHAVIOR -> BehaviorTab(
                            prefs = prefs,
                            onSettingChanged = onSettingChanged,
                            gestureConfig = gestureConfig,
                            onGestureChange = onGestureChange,
                            onOpenGestureZoneEditor = onOpenGestureZoneEditor,
                        )
                    }

                    // ── 恢复默认 / 清除本书设置 ──
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (settingsScope == SettingsScope.BOOK && hasBookOverrides) "清除本书设置" else "恢复默认",
                        style = MaterialTheme.typography.labelMedium,
                        color = readerColors.textTertiary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onResetDefaults() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("SettingsPeek_Reset"),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
