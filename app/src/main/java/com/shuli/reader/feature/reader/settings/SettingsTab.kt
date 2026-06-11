package com.shuli.reader.feature.reader.settings

/**
 * 设置面板的顶层 Tab 分类（v5.1 §0b.2）。
 *
 * 每个 Tab 关联一组 [UiGroup]，由 [ReaderSettingRegistry.byUiGroup] 驱动渲染。
 * 12 个 UiGroup 必须全部被分配到某个 Tab（[allGroupsCovered] 测试保证）。
 *
 * | Tab           | 包含 UiGroup                                                        |
 * |--------------|---------------------------------------------------------------------|
 * | TYPE_AND_FONT | FONT_BASICS, TEXT_LAYOUT, TEXT_STYLE, ADVANCED_READING              |
 * | APPEARANCE    | THEME, PAGE_CHROME, DISPLAY_MODE, VISUAL_AIDS                       |
 * | BEHAVIOR      | PAGE_TURN, GESTURE, EYE_CARE, GENERAL                               |
 */
enum class SettingsTab(val groups: List<UiGroup>) {
    TYPE_AND_FONT(
        listOf(
            UiGroup.FONT_BASICS,
            UiGroup.TEXT_LAYOUT,
            UiGroup.TEXT_STYLE,
            UiGroup.ADVANCED_READING,
        )
    ),
    APPEARANCE(
        listOf(
            UiGroup.THEME,
            UiGroup.PAGE_CHROME,
            UiGroup.DISPLAY_MODE,
            UiGroup.VISUAL_AIDS,
        )
    ),
    BEHAVIOR(
        listOf(
            UiGroup.PAGE_TURN,
            UiGroup.GESTURE,
            UiGroup.EYE_CARE,
            UiGroup.GENERAL,
        )
    );

    companion object {
        /** 校验 12 个 UiGroup 全部被分配到某个 Tab（无遗漏、无重复）。 */
        val allGroupsCovered: Boolean by lazy {
            val covered = entries.flatMap { it.groups }.toSet()
            covered == UiGroup.entries.toSet()
        }

        /** Tab 显示名（用于 TabRow，本地化由 AppStrings 在上层替换）。 */
        fun displayName(tab: SettingsTab): String = when (tab) {
            TYPE_AND_FONT -> "字体排版"
            APPEARANCE -> "外观显示"
            BEHAVIOR -> "行为交互"
        }
    }
}
