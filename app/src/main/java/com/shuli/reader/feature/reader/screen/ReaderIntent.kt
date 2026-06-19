package com.shuli.reader.feature.reader.screen
import com.shuli.reader.feature.reader.settings.SettingsScope

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.IndentUnit
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ProgressStyle
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.reader.model.BoxInsetsDp
import com.shuli.reader.core.reader.model.HeaderVisibility
import com.shuli.reader.core.reader.model.SlotContent
import com.shuli.reader.core.reader.model.TitleAlign

/**
 * 阅读器统一意图 —— UI、快捷键、自动翻页的唯一入口。
 *
 * 所有用户操作通过 [ReaderViewModel.dispatch] 发送，
 * ViewModel 使用穷举 `when` 处理，新增意图时编译器强制覆盖。
 */
sealed interface ReaderIntent {

    // ── 导航 ──

    data class OpenBook(val bookId: Long) : ReaderIntent
    data class OpenChapter(
        val index: Int,
        val targetToLastPage: Boolean = false,
        val targetByteOffset: Long = -1L,
    ) : ReaderIntent
    data class TurnPage(val direction: PageDirection) : ReaderIntent
    object NextPage : ReaderIntent
    object PrevPage : ReaderIntent
    data class JumpToPosition(val chapterIndex: Int, val byteOffset: Long) : ReaderIntent

    // ── UI 开关 ──

    object ToggleToolbar : ReaderIntent
    object ToggleDirectory : ReaderIntent
    object ToggleQuickSettings : ReaderIntent
    object OpenGestureZoneEditor : ReaderIntent
    object CloseGestureZoneEditor : ReaderIntent
    object ToggleSearch : ReaderIntent
    object ClearSelection : ReaderIntent

    // ── 选区操作 ──

    object AddBookmarkFromSelection : ReaderIntent
    object AddNoteFromSelection : ReaderIntent
    data class AddBookmark(val pageOnly: Boolean = false) : ReaderIntent

    // ── 划词查词 ──

    /** 查词 */
    data class LookupWord(val word: String, val contextSentence: String = "") : ReaderIntent
    /** 关闭查词面板 */
    object DismissDictionary : ReaderIntent
    /** 添加到生词本 */
    data class AddToWordBook(val word: String) : ReaderIntent

    // ── 设置（统一入口） ──

    data class UpdateSetting(
        val key: ReaderSettingKey,
        val value: ReaderSettingValue,
    ) : ReaderIntent
    /**
     * 连续控件（滑块 / 步进器）的设置更新。
     *
     * `finished = false`（拖拽中 / 按键中）→ 仅更新内存 uiState，DataStore / 书籍覆盖不写。
     * `finished = true`（抬手 / 按键结束）→ 持久化。
     */
    data class UpdateContinuousSetting(
        val key: ReaderSettingKey,
        val value: ReaderSettingValue,
        val finished: Boolean,
    ) : ReaderIntent
    object CycleTheme : ReaderIntent
    object ResetSettingsToDefault : ReaderIntent

    // ── 设置作用域 ──

    /** 切换设置作用域（全局 ↔ 本书） */
    data class SetSettingsScope(val scope: SettingsScope) : ReaderIntent
    /** 将本书覆盖清除，回退到全局默认 */
    object ResetBookOverrides : ReaderIntent
    /** 将当前全局设置保存为本书覆盖（从全局切换到本书时使用） */
    object CopyGlobalToBook : ReaderIntent

    // ── 预设 ──

    data class ApplyPreset(val presetId: Long) : ReaderIntent
    data class SavePreset(val name: String) : ReaderIntent
    data class RenamePreset(val id: Long, val name: String) : ReaderIntent
    data class DeletePreset(val presetId: Long) : ReaderIntent

    // ── 搜索 ──

    data class Search(val query: String) : ReaderIntent
    object NextSearchResult : ReaderIntent
    object PrevSearchResult : ReaderIntent

    // ── 页面拖动 ──

    object StartPageScrub : ReaderIntent
    data class ScrubToPage(val pageIndex: Int) : ReaderIntent
    object CommitPageScrub : ReaderIntent

    // ── 屏幕 / 排版 ──

    data class SetScreenSize(val width: Int, val height: Int) : ReaderIntent
    data class SetPageAnimType(val type: PageAnimType) : ReaderIntent

    // ── 字体 ──

    data class ImportFont(val uri: android.net.Uri) : ReaderIntent
    data class DeleteFont(val fontKey: String) : ReaderIntent
}

/**
 * 翻页方向（UI 层使用，与 core.reader.animation.PageDelegate.Direction 解耦）。
 */
enum class PageDirection { NEXT, PREV }

/**
 * 设置项键名 —— 对应 [ReaderSettingsManager] 的每个 setter。
 */
enum class ReaderSettingKey {
    FONT_SIZE, LINE_SPACING, PARAGRAPH_SPACING, INDENT, INDENT_UNIT,
    BODY_BOX, HEADER_BOX, FOOTER_BOX, TITLE_BOX, LETTER_SPACING,
    READING_FONT, FONT_WEIGHT, TEXT_ALIGN,
    THEME, BRIGHTNESS,
    CHINESE_CONVERT, USE_ZH_LAYOUT, USE_PANGU_SPACING, BOTTOM_JUSTIFY,
    HEADER_VISIBILITY, HEADER_LEFT, HEADER_CENTER, HEADER_RIGHT,
    FOOTER_VISIBILITY, FOOTER_LEFT, FOOTER_CENTER, FOOTER_RIGHT,
    HEADER_FOOTER_ALPHA, SHOW_PROGRESS,
    SHOW_HEADER_LINE, SHOW_FOOTER_LINE,
    HEADER_FONT_SIZE_RATIO, FOOTER_FONT_SIZE_RATIO,
    TITLE_ALIGN, TITLE_SIZE_OFFSET, TITLE_FONT_SIZE, TITLE_MARGIN_TOP, TITLE_MARGIN_BOTTOM,
    KEEP_SCREEN_ON, VOLUME_KEY_TURN_PAGE, EDGE_TURN_PAGE, EDGE_WIDTH_PERCENT,
    IMMERSIVE_MODE,
    MAX_PAGE_WIDTH, REMOVE_EMPTY_LINES, CLEAN_CHAPTER_TITLE, PROGRESS_STYLE,
    AUTO_PAGE_TURN, AUTO_PAGE_TURN_INTERVAL, EPUB_OVERRIDE_STYLE,
    LEFT_ZONE_RATIO,
    CUSTOM_THEME_COLOR,
    // v5.1 Phase 1-4 新增
    COLOR_TEMPERATURE,
    PARAGRAPH_DIVIDER,
    BIONIC_READING, VERTICAL_TEXT, DUAL_PAGE_MODE,
    HAPTIC_FEEDBACK, ORIENTATION_LOCK, PAGE_ANIM_SPEED,
    AD_FILTERING, TTS_VOICE, TTS_AUTO_PAGE, TTS_TIMER,
    EYE_CARE_REMINDER_INTERVAL, BACKGROUND_TEXTURE,
    GESTURE_CONFIG,
}

/**
 * 设置项值 —— 类型安全的密封类，避免 Any? 转型。
 */
sealed interface ReaderSettingValue {
    data class Float(val value: kotlin.Float) : ReaderSettingValue
    data class Int(val value: kotlin.Int) : ReaderSettingValue
    data class Str(val value: String) : ReaderSettingValue
    data class Bool(val value: Boolean) : ReaderSettingValue
    data class Theme(val value: com.shuli.reader.core.data.ReaderTheme) : ReaderSettingValue
    data class TextAlign(val value: com.shuli.reader.core.data.ReaderTextAlign) : ReaderSettingValue
    data class FontWeight(val value: com.shuli.reader.core.data.ReaderFontWeight) : ReaderSettingValue
    data class ChineseConvert(val value: com.shuli.reader.core.data.ChineseConvert) : ReaderSettingValue
    data class HeaderVisibility(val value: com.shuli.reader.core.reader.model.HeaderVisibility) : ReaderSettingValue
    data class SlotContent(val value: com.shuli.reader.core.reader.model.SlotContent) : ReaderSettingValue
    data class TitleAlign(val value: com.shuli.reader.core.reader.model.TitleAlign) : ReaderSettingValue
    data class ProgressStyle(val value: com.shuli.reader.core.data.ProgressStyle) : ReaderSettingValue
    data class IndentUnit(val value: com.shuli.reader.core.data.IndentUnit) : ReaderSettingValue
    data class DualPageMode(val value: com.shuli.reader.core.data.DualPageMode) : ReaderSettingValue
    data class OrientationLock(val value: com.shuli.reader.core.data.OrientationLock) : ReaderSettingValue
    data class PageAnimSpeed(val value: com.shuli.reader.core.data.PageAnimSpeed) : ReaderSettingValue
    data class GestureConfigValue(val value: com.shuli.reader.feature.reader.settings.GestureConfig) : ReaderSettingValue
    data class BoxInsetsDpVal(val value: BoxInsetsDp) : ReaderSettingValue
    data class CustomThemeColor(
        val backgroundColor: kotlin.Int?,
        val textColor: kotlin.Int?,
        val titleColor: kotlin.Int?,
        val headerFooterColor: kotlin.Int?,
    ) : ReaderSettingValue
}
