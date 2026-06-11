package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.DualPageMode
import com.shuli.reader.core.data.IndentUnit
import com.shuli.reader.core.data.OrientationLock
import com.shuli.reader.core.data.PageAnimSpeed
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ProgressStyle
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.feature.reader.render.InvalidationScope

/**
 * 设置元数据定义——每个设置的所有属性集中在此。
 * 新增设置只需在此处添加一条记录。
 */
data class SettingDefinition<T>(
    val key: String,
    val defaultValue: T,
    val storageTier: StorageTier,
    val scope: InvalidationScope,
    val recompositionTier: Int,
    val uiGroup: UiGroup,
    val includeInPreset: Boolean,
    val previewStrategy: PreviewStrategy,
)

enum class StorageTier { GLOBAL, PER_BOOK, BOTH }

enum class UiGroup {
    FONT_BASICS, TEXT_LAYOUT, TEXT_STYLE, ADVANCED_READING,
    THEME, PAGE_CHROME, DISPLAY_MODE, VISUAL_AIDS,
    PAGE_TURN, GESTURE, EYE_CARE, GENERAL,
}

enum class PreviewStrategy { LIVE, ON_APPLY, NONE }

/**
 * 全局注册表——所有设置的唯一真相源。
 *
 * 类型安全说明：`all` 使用 star-projection（`List<SettingDefinition<*>>`），
 * 消费方通过 [getValue] / [setValue] 访问，无需自行 `@Suppress("UNCHECKED_CAST")`。
 */
object ReaderSettingRegistry {

    val all: List<SettingDefinition<*>> = buildList {
        // ── Overlay 层（recompositionTier = 0）──
        add(SettingDefinition(
            key = "color_temperature",
            defaultValue = 6500f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.VIEW_INVALIDATE,
            recompositionTier = 0,
            uiGroup = UiGroup.EYE_CARE,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "focus_line",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.VIEW_INVALIDATE,
            recompositionTier = 0,
            uiGroup = UiGroup.VISUAL_AIDS,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "brightness",
            defaultValue = -1f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.VIEW_INVALIDATE,
            recompositionTier = 0,
            uiGroup = UiGroup.DISPLAY_MODE,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.LIVE,
        ))

        // ── Chrome 层（recompositionTier = 1）──
        add(SettingDefinition(
            key = "header_visibility",
            defaultValue = "hide_when_status_bar",
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.PAGE_CHROME,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "footer_visibility",
            defaultValue = "hide_when_status_bar",
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.PAGE_CHROME,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "header_footer_alpha",
            defaultValue = 0.4f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.PAGE_CHROME,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "show_progress",
            defaultValue = true,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.PAGE_CHROME,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "progress_style",
            defaultValue = ProgressStyle.CHAPTER_FRACTION,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.PAGE_CHROME,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "show_header_line",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.PAGE_CHROME,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "show_footer_line",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.PAGE_CHROME,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "header_font_size_ratio",
            defaultValue = 0.75f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.PAGE_CHROME,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "footer_font_size_ratio",
            defaultValue = 0.75f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.PAGE_CHROME,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))

        // ── Style 层（recompositionTier = 2）──
        add(SettingDefinition(
            key = "reading_font",
            defaultValue = "harmony",
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.FONT_BASICS,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "font_weight",
            defaultValue = ReaderFontWeight.NORMAL,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.FONT_BASICS,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "text_align",
            defaultValue = ReaderTextAlign.LEFT,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.TEXT_STYLE,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "chinese_convert",
            defaultValue = ChineseConvert.NONE,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 2,
            uiGroup = UiGroup.TEXT_STYLE,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "use_zh_layout",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.TEXT_STYLE,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "use_pangu_spacing",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.TEXT_STYLE,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "bionic_reading",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 2,
            uiGroup = UiGroup.ADVANCED_READING,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))

        // ── Layout 层（recompositionTier = 3）──
        add(SettingDefinition(
            key = "font_size",
            defaultValue = 16f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.FONT_BASICS,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "line_spacing",
            defaultValue = 1.5f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "paragraph_spacing",
            defaultValue = 1.0f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "indent",
            defaultValue = 2.0f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "indent_unit",
            defaultValue = IndentUnit.CHARACTER,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "letter_spacing",
            defaultValue = 0f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "margin_horizontal",
            defaultValue = 24f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "margin_vertical",
            defaultValue = 48f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "word_spacing",
            defaultValue = 0f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "paragraph_divider",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "bottom_justify",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_STYLE,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "max_page_width",
            defaultValue = 0f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))

        // ── 主题（recompositionTier = 2，CONTENT scope）──
        add(SettingDefinition(
            key = "background_color",
            defaultValue = ReaderTheme.PAPER,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.THEME,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "auto_night_mode",
            defaultValue = false,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.THEME,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))

        // ── 翻页（PAGE_DELEGATE scope）──
        add(SettingDefinition(
            key = "page_anim_type",
            defaultValue = PageAnimType.HORIZONTAL,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.PAGE_DELEGATE,
            recompositionTier = -1,
            uiGroup = UiGroup.PAGE_TURN,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "page_anim_speed",
            defaultValue = 250,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.PAGE_DELEGATE,
            recompositionTier = -1,
            uiGroup = UiGroup.PAGE_TURN,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))

        // ── 行为层（recompositionTier = -1，不影响 Canvas）──
        add(SettingDefinition(
            key = "keep_screen_on",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GENERAL,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "volume_key_turn_page",
            defaultValue = false,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.PAGE_TURN,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "edge_turn_page",
            defaultValue = true,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.PAGE_TURN,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "edge_width_percent",
            defaultValue = 0.33f,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.PAGE_TURN,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "immersive_mode",
            defaultValue = false,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GENERAL,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "haptic_feedback",
            defaultValue = false,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GESTURE,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "left_zone_ratio",
            defaultValue = 0.33f,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GESTURE,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "auto_page_turn",
            defaultValue = false,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.PAGE_TURN,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "auto_page_turn_interval",
            defaultValue = 10f,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.PAGE_TURN,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))

        // ── TTS（不影响 Canvas）──
        add(SettingDefinition(
            key = "tts_speed",
            defaultValue = 1.0f,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GENERAL,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "tts_pitch",
            defaultValue = 1.0f,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GENERAL,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))

        // ── 护眼（不影响 Canvas）──
        add(SettingDefinition(
            key = "eye_care_reminder_interval",
            defaultValue = 0,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.EYE_CARE,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))

        // ── 排版增强 ──
        add(SettingDefinition(
            key = "remove_empty_lines",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_STYLE,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "clean_chapter_title",
            defaultValue = false,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_STYLE,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "epub_override_style",
            defaultValue = true,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 2,
            uiGroup = UiGroup.TEXT_STYLE,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))

        // ── 四独立边距（v5.1 §0a.9 新增，REFLOW scope）──
        add(SettingDefinition(
            key = "margin_top",
            defaultValue = null as Float?,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "margin_bottom",
            defaultValue = null as Float?,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "margin_left",
            defaultValue = null as Float?,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "margin_right",
            defaultValue = null as Float?,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.TEXT_LAYOUT,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))

        // ── 标题样式（CONTENT scope，不触发 reflow）──
        add(SettingDefinition(
            key = "title_font",
            defaultValue = "",
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.FONT_BASICS,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "title_style",
            defaultValue = com.shuli.reader.core.reader.TitleStyleConfig(),
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.TEXT_STYLE,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))

        // ── 背景纹理（SHELL scope，不影响正文排版）──
        add(SettingDefinition(
            key = "background_texture",
            defaultValue = null as String?,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.THEME,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))

        // ── 显示模式 / 竖排 / 双页（影响分页结果）──
        add(SettingDefinition(
            key = "vertical_text",
            defaultValue = false,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.DISPLAY_MODE,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))
        add(SettingDefinition(
            key = "dual_page_mode",
            defaultValue = DualPageMode.AUTO,
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.REFLOW,
            recompositionTier = 3,
            uiGroup = UiGroup.DISPLAY_MODE,
            includeInPreset = true,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))

        // ── 方向锁定（NONE scope，不影响 Canvas）──
        add(SettingDefinition(
            key = "orientation_lock",
            defaultValue = OrientationLock.SYSTEM,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GENERAL,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))

        // ── 手势配置（NONE scope，行为层）──
        add(SettingDefinition(
            key = "gesture_config",
            defaultValue = GestureConfig(),
            storageTier = StorageTier.BOTH,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GESTURE,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))

        // ── 广告过滤（CONTENT scope：文本预处理行为，不进预设）──
        add(SettingDefinition(
            key = "ad_filtering",
            defaultValue = false,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.TEXT_STYLE,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.ON_APPLY,
        ))

        // ── TTS 扩展字段（NONE scope，不影响 Canvas）──
        add(SettingDefinition(
            key = "tts_voice",
            defaultValue = "",
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GENERAL,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "tts_auto_page",
            defaultValue = true,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GENERAL,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
        add(SettingDefinition(
            key = "tts_timer",
            defaultValue = 0,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GENERAL,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))

        // ── 自定义主题色（SHELL scope，仅影响背景/文字颜色）──
        add(SettingDefinition(
            key = "custom_background_color",
            defaultValue = null as Int?,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.THEME,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "custom_text_color",
            defaultValue = null as Int?,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.CONTENT,
            recompositionTier = 2,
            uiGroup = UiGroup.THEME,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.LIVE,
        ))
        add(SettingDefinition(
            key = "custom_accent_color",
            defaultValue = null as Int?,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.SHELL,
            recompositionTier = 1,
            uiGroup = UiGroup.THEME,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.LIVE,
        ))

        // ── 渲染优化总开关（NONE scope，行为类）──
        add(SettingDefinition(
            key = "optimize_render",
            defaultValue = true,
            storageTier = StorageTier.GLOBAL,
            scope = InvalidationScope.NONE,
            recompositionTier = -1,
            uiGroup = UiGroup.GENERAL,
            includeInPreset = false,
            previewStrategy = PreviewStrategy.NONE,
        ))
    }

    // ── 查询 API ──

    private val byKey: Map<String, SettingDefinition<*>> = all.associateBy { it.key }.also { map ->
        require(map.size == all.size) {
            val dupes = all.groupBy { it.key }.filter { it.value.size > 1 }.keys
            "Duplicate Registry keys: $dupes"
        }
    }

    val keys: Set<String> get() = byKey.keys

    fun findDefinition(key: String): SettingDefinition<*>? = byKey[key]

    fun byRecompositionTier(tier: Int): List<SettingDefinition<*>> =
        all.filter { it.recompositionTier == tier }

    fun byUiGroup(group: UiGroup): List<SettingDefinition<*>> =
        all.filter { it.uiGroup == group }

    fun presetFields(): List<SettingDefinition<*>> =
        all.filter { it.includeInPreset }

    fun byScope(scope: InvalidationScope): List<SettingDefinition<*>> =
        all.filter { it.scope == scope }

    fun byStorageTier(tier: StorageTier): List<SettingDefinition<*>> =
        all.filter { it.storageTier == tier || it.storageTier == StorageTier.BOTH }

    @Suppress("UNCHECKED_CAST")
    fun <T> getDefault(key: String): T {
        val def = byKey[key] ?: error("Unknown setting key: $key")
        return def.defaultValue as T
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getDefinition(key: String): SettingDefinition<T> {
        return byKey[key] as? SettingDefinition<T> ?: error("Unknown setting key: $key")
    }
}
