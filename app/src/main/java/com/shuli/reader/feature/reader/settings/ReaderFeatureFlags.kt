package com.shuli.reader.feature.reader.settings

/**
 * 阅读器功能开关（v5.1 §0a 起）。
 *
 * 用于灰度发布 / 回滚新功能。所有开关均为运行时可变（通过 build 变体或远程配置注入）。
 * 关闭某开关后：UI 入口隐藏 + 逻辑路径跳过，但 DataStore 中存储的用户配置保留。
 */
object ReaderFeatureFlags {
    /** V5 设置面板（BottomSheetScaffold 双态 + TabRow + SettingsCard） */
    @Volatile var SETTINGS_PANEL_V5_ENABLED: Boolean = true

    /** 色温调节（VIEW_INVALIDATE scope） */
    @Volatile var COLOR_TEMPERATURE_ENABLED: Boolean = true

    /** 四独立边距（REFLOW scope） */
    @Volatile var FOUR_MARGINS_ENABLED: Boolean = true

    /** 护眼提醒（NONE scope） */
    @Volatile var EYE_CARE_REMINDER_ENABLED: Boolean = true

    /** 背景纹理（SHELL scope） */
    @Volatile var BACKGROUND_TEXTURE_ENABLED: Boolean = true

    /** Bionic Reading（REFLOW scope） */
    @Volatile var BIONIC_READING_ENABLED: Boolean = true

    /** 双页模式（REFLOW scope） */
    @Volatile var DUAL_PAGE_MODE_ENABLED: Boolean = true
}
