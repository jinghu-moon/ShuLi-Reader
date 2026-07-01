package com.shuli.reader.feature.reader.settings.panel

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.settings.SettingsScope
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.feature.reader.settings.SettingsTab
import com.shuli.reader.feature.reader.settings.panel.dashboard.SettingsDashboardGrid
import com.shuli.reader.feature.reader.settings.panel.dashboard.SettingsDetailDestination
import com.shuli.reader.feature.reader.settings.panel.tabs.BodyAreaSettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.BodyTypographySettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.EyeCareSettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.FontSettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.HeaderFooterSettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.MarginPresetSettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.PageTurnAnimationSettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.PageTurnMethodSettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.ReadingFormSettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.ScreenStateSettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.TextProcessingSettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.TitleStyleSettingsCard
import com.shuli.reader.feature.reader.settings.panel.tabs.TouchZoneSettingsCard
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
 * 设置面板共享内容：Peek 区 + TabRow + 四 Tab 卡片。
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
    var detailDestination by rememberSaveable { mutableStateOf<SettingsDetailDestination?>(null) }
    BackHandler(enabled = detailDestination != null) {
        detailDestination = null
    }

    AnimatedContent(
        targetState = detailDestination,
        transitionSpec = {
            val springSpec = spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy)
            if (targetState != null) {
                (slideInHorizontally(animationSpec = springSpec) { it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally(animationSpec = springSpec) { -it / 3 } + fadeOut())
            } else {
                (slideInHorizontally(animationSpec = springSpec) { -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally(animationSpec = springSpec) { it / 3 } + fadeOut())
            }
        },
        label = "settings-dashboard-detail",
        modifier = modifier.fillMaxWidth(),
    ) { destination ->
        if (destination == null) {
            SettingsDashboardContent(
                prefs = prefs,
                selectedTab = selectedTab,
                onTabChange = onTabChange,
                onThemeChange = onThemeChange,
                onCustomThemeConfirm = onCustomThemeConfirm,
                onSettingChanged = onSettingChanged,
                settingsScope = settingsScope,
                hasBookOverrides = hasBookOverrides,
                onScopeChange = onScopeChange,
                onResetDefaults = onResetDefaults,
                customFonts = customFonts,
                gestureConfig = gestureConfig,
                onOpenDetail = { detailDestination = it },
            )
        } else {
            SettingsDetailContent(
                destination = destination,
                prefs = prefs,
                onBack = { detailDestination = null },
                onSettingChanged = onSettingChanged,
                onContinuousSettingChanged = onContinuousSettingChanged,
                customFonts = customFonts,
                onImportFont = onImportFont,
                onDeleteFont = onDeleteFont,
                gestureConfig = gestureConfig,
                onOpenGestureZoneEditor = onOpenGestureZoneEditor,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDashboardContent(
    prefs: ReaderPreferences,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onThemeChange: (ReaderTheme) -> Unit,
    onCustomThemeConfirm: (bg: Int, text: Int, title: Int, headerFooter: Int) -> Unit,
    onSettingChanged: (key: String, value: Any) -> Unit,
    settingsScope: SettingsScope,
    hasBookOverrides: Boolean,
    onScopeChange: (SettingsScope) -> Unit,
    onResetDefaults: () -> Unit,
    customFonts: List<FontManager.FontEntry>,
    gestureConfig: GestureConfig,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
) {
    val readerColors = LocalReaderColorScheme.current

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsPeekContent(
            prefs = prefs,
            settingsScope = settingsScope,
            onThemeChange = onThemeChange,
            onCustomThemeConfirm = onCustomThemeConfirm,
            onSettingChanged = onSettingChanged,
            onScopeChange = onScopeChange,
        )

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
                                SettingsTab.displayName(tab, LocalAppStrings.current.reader),
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
                SettingsDashboardGrid(
                    prefs = prefs,
                    selectedTab = SettingsTab.entries[tabIndex],
                    customFonts = customFonts,
                    gestureConfig = gestureConfig,
                    onOpenDetail = onOpenDetail,
                    modifier = Modifier.fillMaxSize(),
                    footer = {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SettingsResetFooter(
                                settingsScope = settingsScope,
                                hasBookOverrides = hasBookOverrides,
                                onResetDefaults = onResetDefaults,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsDetailContent(
    destination: SettingsDetailDestination,
    prefs: ReaderPreferences,
    onBack: () -> Unit,
    onSettingChanged: (String, Any) -> Unit,
    onContinuousSettingChanged: (String, Any, Boolean) -> Unit,
    customFonts: List<FontManager.FontEntry>,
    onImportFont: (android.net.Uri) -> Unit,
    onDeleteFont: (String) -> Unit,
    gestureConfig: GestureConfig,
    onOpenGestureZoneEditor: () -> Unit,
) {
    val strings = LocalAppStrings.current.reader
    val colors = LocalReaderColorScheme.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ReaderSettingsPanel_Detail"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("SettingsDetail_Back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = strings.closeLabel,
                    tint = colors.textPrimary,
                )
            }
            Text(
                text = destination.title(strings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
        ) {
            when (destination) {
                SettingsDetailDestination.Font -> FontSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                    customFonts = customFonts,
                    onImportFont = onImportFont,
                    onDeleteFont = onDeleteFont,
                )
                SettingsDetailDestination.BodyTypography -> BodyTypographySettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                )
                SettingsDetailDestination.TextProcessing -> TextProcessingSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                )
                SettingsDetailDestination.BodyArea -> BodyAreaSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                )
                SettingsDetailDestination.TitleStyle -> TitleStyleSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                )
                SettingsDetailDestination.HeaderFooter -> HeaderFooterSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                )
                SettingsDetailDestination.MarginPreset -> MarginPresetSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                )
                SettingsDetailDestination.PageTurnMethod -> PageTurnMethodSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                )
                SettingsDetailDestination.TouchZone -> TouchZoneSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                    gestureConfig = gestureConfig,
                    onOpenGestureZoneEditor = onOpenGestureZoneEditor,
                )
                SettingsDetailDestination.PageTurnAnimation -> PageTurnAnimationSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                )
                SettingsDetailDestination.EyeCare -> EyeCareSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                    onContinuousSettingChanged = onContinuousSettingChanged,
                )
                SettingsDetailDestination.ScreenState -> ScreenStateSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                )
                SettingsDetailDestination.ReadingForm -> ReadingFormSettingsCard(
                    prefs = prefs,
                    onSettingChanged = onSettingChanged,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingsResetFooter(
    settingsScope: SettingsScope,
    hasBookOverrides: Boolean,
    onResetDefaults: () -> Unit,
) {
    val strings = LocalAppStrings.current.reader
    val readerColors = LocalReaderColorScheme.current
    Text(
        text = if (settingsScope == SettingsScope.BOOK && hasBookOverrides) {
            strings.clearBookSettings
        } else {
            strings.resetToDefaultShort
        },
        style = MaterialTheme.typography.labelMedium,
        color = readerColors.textTertiary,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onResetDefaults() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("SettingsPeek_Reset"),
    )
}
