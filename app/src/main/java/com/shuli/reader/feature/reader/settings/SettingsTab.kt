package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.i18n.ReaderStrings

/**
 * 设置面板的顶层 Tab 分类。
 *
 * 每个 Tab 关联一组 [UiGroup]，由 [ReaderSettingRegistry.byUiGroup] 驱动渲染。
 * 所有 UiGroup 必须全部被分配到某个 Tab（[allGroupsCovered] 测试保证）。
 *
 * | Tab        | 包含 UiGroup                                           |
 * |------------|--------------------------------------------------------|
 * | TYPESetting | FONT_BASICS, TEXT_LAYOUT, TEXT_STYLE, ADVANCED_READING  |
 * | LAYOUT     | PAGE_CHROME, PAGE_CONTENT                               |
 * | PAGE_TURN  | PAGE_TURN, GESTURE                                      |
 * | AUXILIARY  | EYE_CARE, GENERAL, DISPLAY_MODE, VISUAL_AIDS, THEME    |
 */
enum class SettingsTab(val groups: List<UiGroup>) {
    TYPESETTING(
        listOf(
            UiGroup.FONT_BASICS,
            UiGroup.TEXT_LAYOUT,
            UiGroup.TEXT_STYLE,
            UiGroup.ADVANCED_READING,
        )
    ),
    LAYOUT(
        listOf(
            UiGroup.PAGE_CHROME,
            UiGroup.PAGE_CONTENT,
        )
    ),
    PAGE_TURN(
        listOf(
            UiGroup.PAGE_TURN,
            UiGroup.GESTURE,
        )
    ),
    AUXILIARY(
        listOf(
            UiGroup.EYE_CARE,
            UiGroup.GENERAL,
            UiGroup.DISPLAY_MODE,
            UiGroup.VISUAL_AIDS,
            UiGroup.THEME,
        )
    );

    companion object {
        /** 校验所有 UiGroup 全部被分配到某个 Tab（无遗漏、无重复）。 */
        val allGroupsCovered: Boolean by lazy {
            val covered = entries.flatMap { it.groups }.toSet()
            covered == UiGroup.entries.toSet()
        }

        /** Tab 显示名（用于 TabRow，支持 i18n）。 */
        fun displayName(tab: SettingsTab, strings: ReaderStrings? = null): String = when (tab) {
            TYPESETTING -> strings?.settingsTabTypesetting ?: "排版"
            LAYOUT -> strings?.settingsTabLayout ?: "布局"
            PAGE_TURN -> strings?.settingsTabPageTurn ?: "翻页"
            AUXILIARY -> strings?.settingsTabAuxiliary ?: "辅助"
        }
    }
}
