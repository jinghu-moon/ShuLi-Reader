package com.shuli.reader.feature.reader.settings.panel.dashboard

import com.shuli.reader.core.i18n.ReaderStrings
import com.shuli.reader.feature.reader.settings.SettingsTab

/**
 * 设置仪表盘的详情入口。
 *
 * 每个入口对应一张仪表盘卡片，也对应详情页里的一个设置分组。
 */
enum class SettingsDetailDestination(
    val tab: SettingsTab,
    val span: DashboardCardSpan,
) {
    Font(SettingsTab.TYPESETTING, DashboardCardSpan.Half),
    BodyTypography(SettingsTab.TYPESETTING, DashboardCardSpan.Half),
    TextProcessing(SettingsTab.TYPESETTING, DashboardCardSpan.Full),

    BodyArea(SettingsTab.LAYOUT, DashboardCardSpan.Half),
    TitleStyle(SettingsTab.LAYOUT, DashboardCardSpan.Half),
    HeaderFooter(SettingsTab.LAYOUT, DashboardCardSpan.Full),
    MarginPreset(SettingsTab.LAYOUT, DashboardCardSpan.Half),

    PageTurnMethod(SettingsTab.PAGE_TURN, DashboardCardSpan.Half),
    TouchZone(SettingsTab.PAGE_TURN, DashboardCardSpan.Half),
    PageTurnAnimation(SettingsTab.PAGE_TURN, DashboardCardSpan.Full),

    EyeCare(SettingsTab.AUXILIARY, DashboardCardSpan.Half),
    ScreenState(SettingsTab.AUXILIARY, DashboardCardSpan.Half),
    ReadingForm(SettingsTab.AUXILIARY, DashboardCardSpan.Full);

    fun title(strings: ReaderStrings): String = when (this) {
        Font -> strings.fontCardTitle
        BodyTypography -> "正文排版"
        TextProcessing -> strings.advancedTypesettingCard
        BodyArea -> "正文区域"
        TitleStyle -> strings.titleStyleLabel
        HeaderFooter -> strings.headerFooterCard
        MarginPreset -> strings.marginCardTitle
        PageTurnMethod -> strings.pageTurnCard
        TouchZone -> strings.touchZoneCard
        PageTurnAnimation -> "翻页动效"
        EyeCare -> strings.eyeCareCard
        ScreenState -> "屏幕状态"
        ReadingForm -> "阅读形态"
    }
}

enum class DashboardCardSpan {
    Half,
    Full,
}
