package com.shuli.reader.feature.reader.settings.panel.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.core.i18n.LocalAppStrings
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.feature.reader.settings.SettingsTab

@Composable
internal fun SettingsDashboardGrid(
    prefs: ReaderPreferences,
    selectedTab: SettingsTab,
    customFonts: List<FontManager.FontEntry>,
    gestureConfig: GestureConfig,
    onOpenDetail: (SettingsDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
    footer: (LazyGridScope.() -> Unit)? = null,
) {
    val strings = LocalAppStrings.current.reader
    val summaries = buildSettingsDashboardSummaries(
        prefs = prefs,
        strings = strings,
        customFonts = customFonts,
        gestureConfig = gestureConfig,
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (selectedTab) {
            SettingsTab.TYPESETTING -> {
                item {
                    FontDashboardCard(
                        title = SettingsDetailDestination.Font.title(strings),
                        summary = summaries.font,
                        onOpenDetail = onOpenDetail,
                    )
                }
                item {
                    BodyTypographyDashboardCard(
                        title = SettingsDetailDestination.BodyTypography.title(strings),
                        summary = summaries.bodyTypography,
                        onOpenDetail = onOpenDetail,
                    )
                }
                fullSpanItem {
                    TextProcessingDashboardCard(
                        title = SettingsDetailDestination.TextProcessing.title(strings),
                        summary = summaries.textProcessing,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }

            SettingsTab.LAYOUT -> {
                item {
                    BodyAreaDashboardCard(
                        title = SettingsDetailDestination.BodyArea.title(strings),
                        summary = summaries.bodyArea,
                        onOpenDetail = onOpenDetail,
                    )
                }
                item {
                    TitleStyleDashboardCard(
                        title = SettingsDetailDestination.TitleStyle.title(strings),
                        summary = summaries.titleStyle,
                        onOpenDetail = onOpenDetail,
                    )
                }
                fullSpanItem {
                    HeaderFooterDashboardCard(
                        title = SettingsDetailDestination.HeaderFooter.title(strings),
                        summary = summaries.headerFooter,
                        onOpenDetail = onOpenDetail,
                    )
                }
                item {
                    MarginPresetDashboardCard(
                        title = SettingsDetailDestination.MarginPreset.title(strings),
                        summary = summaries.marginPreset,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }

            SettingsTab.PAGE_TURN -> {
                item {
                    PageTurnMethodDashboardCard(
                        title = SettingsDetailDestination.PageTurnMethod.title(strings),
                        summary = summaries.pageTurnMethod,
                        onOpenDetail = onOpenDetail,
                    )
                }
                item {
                    TouchZoneDashboardCard(
                        title = SettingsDetailDestination.TouchZone.title(strings),
                        summary = summaries.touchZone,
                        onOpenDetail = onOpenDetail,
                    )
                }
                fullSpanItem {
                    PageTurnAnimationDashboardCard(
                        title = SettingsDetailDestination.PageTurnAnimation.title(strings),
                        summary = summaries.pageTurnAnimation,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }

            SettingsTab.AUXILIARY -> {
                item {
                    EyeCareDashboardCard(
                        title = SettingsDetailDestination.EyeCare.title(strings),
                        summary = summaries.eyeCare,
                        onOpenDetail = onOpenDetail,
                    )
                }
                item {
                    ScreenStateDashboardCard(
                        title = SettingsDetailDestination.ScreenState.title(strings),
                        summary = summaries.screenState,
                        onOpenDetail = onOpenDetail,
                    )
                }
                fullSpanItem {
                    ReadingFormDashboardCard(
                        title = SettingsDetailDestination.ReadingForm.title(strings),
                        summary = summaries.readingForm,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }
        }
        footer?.invoke(this)
    }
}

private fun LazyGridScope.fullSpanItem(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        content()
    }
}
