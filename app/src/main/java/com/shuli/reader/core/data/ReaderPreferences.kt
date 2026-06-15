package com.shuli.reader.core.data

import com.shuli.reader.core.reader.model.FooterConfig
import com.shuli.reader.core.reader.model.HeaderConfig
import com.shuli.reader.core.reader.model.TitleStyleConfig
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.feature.reader.settings.ReaderSettingRegistry
import kotlinx.serialization.Serializable

/**
 * 阅读器显示偏好数据类
 * 用于统一管理阅读页的显示设置
 */
@Serializable
data class ReaderPreferences(
    // ── 所有默认值统一从 ReaderSettingRegistry 读取，保证单一真相源 ──
    val fontSize: Float = ReaderSettingRegistry.getDefault("font_size"),
    val lineSpacing: Float = ReaderSettingRegistry.getDefault("line_spacing"),
    val paragraphSpacing: Float = ReaderSettingRegistry.getDefault("paragraph_spacing"),
    val indent: Float = ReaderSettingRegistry.getDefault("indent"),
    val preserveOriginalIndent: Boolean = ReaderSettingRegistry.getDefault("preserve_original_indent"),
    val indentUnit: IndentUnit = ReaderSettingRegistry.getDefault("indent_unit"),
    val pageAnimType: PageAnimType = ReaderSettingRegistry.getDefault("page_anim_type"),
    val backgroundColor: ReaderTheme = ReaderSettingRegistry.getDefault("background_color"),
    val marginHorizontal: Float = ReaderSettingRegistry.getDefault("margin_horizontal"),
    val marginVertical: Float = ReaderSettingRegistry.getDefault("margin_vertical"),
    val brightness: Float = ReaderSettingRegistry.getDefault("brightness"),
    val readingFont: String = ReaderSettingRegistry.getDefault("reading_font"),
    val optimizeRender: Boolean = ReaderSettingRegistry.getDefault("optimize_render"),
    // 阶段三新增字段
    val letterSpacing: Float = ReaderSettingRegistry.getDefault("letter_spacing"),
    val fontWeight: ReaderFontWeight = ReaderSettingRegistry.getDefault("font_weight"),
    val textAlign: ReaderTextAlign = ReaderSettingRegistry.getDefault("text_align"),
    val chineseConvert: ChineseConvert = ReaderSettingRegistry.getDefault("chinese_convert"),
    // 阶段五新增字段（无 Registry 条目的复合类型保留硬编码）
    val titleStyle: TitleStyleConfig = TitleStyleConfig(),
    val titleFont: String = ReaderSettingRegistry.getDefault("title_font"),
    val header: HeaderConfig = HeaderConfig(),
    val footer: FooterConfig = FooterConfig(),
    val headerFooterAlpha: Float = ReaderSettingRegistry.getDefault("header_footer_alpha"),
    val showProgress: Boolean = ReaderSettingRegistry.getDefault("show_progress"),
    // 排版增强
    val useZhLayout: Boolean = ReaderSettingRegistry.getDefault("use_zh_layout"),
    val usePanguSpacing: Boolean = ReaderSettingRegistry.getDefault("use_pangu_spacing"),
    // 页眉页脚增强
    val showHeaderLine: Boolean = ReaderSettingRegistry.getDefault("show_header_line"),
    val showFooterLine: Boolean = ReaderSettingRegistry.getDefault("show_footer_line"),
    val headerFontSizeRatio: Float = ReaderSettingRegistry.getDefault("header_font_size_ratio"),
    val footerFontSizeRatio: Float = ReaderSettingRegistry.getDefault("footer_font_size_ratio"),
    // 排版增强2
    val bottomJustify: Boolean = ReaderSettingRegistry.getDefault("bottom_justify"),
    // 阶段六新增字段
    val keepScreenOn: Boolean = ReaderSettingRegistry.getDefault("keep_screen_on"),
    val volumeKeyTurnPage: Boolean = ReaderSettingRegistry.getDefault("volume_key_turn_page"),
    val edgeTurnPage: Boolean = ReaderSettingRegistry.getDefault("edge_turn_page"),
    val edgeWidthPercent: Float = ReaderSettingRegistry.getDefault("edge_width_percent"),
    val immersiveMode: Boolean = ReaderSettingRegistry.getDefault("immersive_mode"),
    // TTS 设置
    val ttsSpeed: Float = ReaderSettingRegistry.getDefault("tts_speed"),
    val ttsPitch: Float = ReaderSettingRegistry.getDefault("tts_pitch"),
    // P1: 排版增强
    val maxPageWidth: Float = ReaderSettingRegistry.getDefault("max_page_width"),
    val removeEmptyLines: Boolean = ReaderSettingRegistry.getDefault("remove_empty_lines"),
    val cleanChapterTitle: Boolean = ReaderSettingRegistry.getDefault("clean_chapter_title"),
    // P1: 进度显示样式
    val progressStyle: ProgressStyle = ReaderSettingRegistry.getDefault("progress_style"),
    // P2: 低频增强
    val autoPageTurn: Boolean = ReaderSettingRegistry.getDefault("auto_page_turn"),
    val autoPageTurnInterval: Float = ReaderSettingRegistry.getDefault("auto_page_turn_interval"),
    val epubOverrideStyle: Boolean = ReaderSettingRegistry.getDefault("epub_override_style"),
    // P0: 触控热区
    val leftZoneRatio: Float = ReaderSettingRegistry.getDefault("left_zone_ratio"),
    // P1: 自定义主题颜色
    val customBackgroundColor: Int? = ReaderSettingRegistry.getDefault("custom_background_color"),
    val customTextColor: Int? = ReaderSettingRegistry.getDefault("custom_text_color"),
    val customTitleColor: Int? = ReaderSettingRegistry.getDefault("custom_title_color"),
    val customHeaderFooterColor: Int? = ReaderSettingRegistry.getDefault("custom_header_footer_color"),
    // ── v5.1 新增字段 ──
    val colorTemperature: Float = ReaderSettingRegistry.getDefault("color_temperature"),
    val paragraphDivider: Boolean = ReaderSettingRegistry.getDefault("paragraph_divider"),
    val marginTop: Float? = ReaderSettingRegistry.getDefault("margin_top"),
    val marginBottom: Float? = ReaderSettingRegistry.getDefault("margin_bottom"),
    val marginLeft: Float? = ReaderSettingRegistry.getDefault("margin_left"),
    val marginRight: Float? = ReaderSettingRegistry.getDefault("margin_right"),
    val bionicReading: Boolean = ReaderSettingRegistry.getDefault("bionic_reading"),
    val verticalText: Boolean = ReaderSettingRegistry.getDefault("vertical_text"),
    val dualPageMode: DualPageMode = ReaderSettingRegistry.getDefault("dual_page_mode"),
    val hapticFeedback: Boolean = ReaderSettingRegistry.getDefault("haptic_feedback"),
    val orientationLock: OrientationLock = ReaderSettingRegistry.getDefault("orientation_lock"),
    val pageAnimSpeed: PageAnimSpeed = PageAnimSpeed.fromDurationMs(
        ReaderSettingRegistry.getDefault<Int>("page_anim_speed")
    ),
    val adFiltering: Boolean = ReaderSettingRegistry.getDefault("ad_filtering"),
    val ttsVoice: String = ReaderSettingRegistry.getDefault("tts_voice"),
    val ttsAutoPage: Boolean = ReaderSettingRegistry.getDefault("tts_auto_page"),
    val ttsTimer: Int = ReaderSettingRegistry.getDefault("tts_timer"),
    val eyeCareReminderInterval: Int = ReaderSettingRegistry.getDefault("eye_care_reminder_interval"),
    val backgroundTexture: String? = ReaderSettingRegistry.getDefault("background_texture"),
    // 手势配置（类型安全：@Serializable GestureConfig）
    val gestureConfig: GestureConfig = ReaderSettingRegistry.getDefault("gesture_config"),
)

/**
 * 翻页动画类型
 */
@Serializable
enum class PageAnimType {
    NONE,
    COVER,
    HORIZONTAL,
    SIMULATION,
    SCROLL,
}

/**
 * 字重
 */
@Serializable
enum class ReaderFontWeight {
    LIGHT,
    NORMAL,
    MEDIUM,
    BOLD,
}

/**
 * 文本对齐方式
 */
@Serializable
enum class ReaderTextAlign {
    LEFT,
    JUSTIFY,
}

/**
 * 简繁转换
 */
@Serializable
enum class ChineseConvert {
    NONE,
    SIMPLIFIED,
    TRADITIONAL,
}

/**
 * 阅读主题
 */
@Serializable
enum class ReaderTheme {
    LIGHT,    // 浅色
    DARK,     // 暗色
    PAPER,    // 纸质
    GREEN,    // 护眼绿
    OLED,     // OLED
    CUSTOM,   // 自定义
}

/**
 * 段首缩进单位
 */
@Serializable
enum class IndentUnit {
    CHARACTER,  // 字符宽度（em），缩进值 × 字号
    PIXEL,      // 固定像素（dp），缩进值 × density
}

/**
 * 进度显示样式
 */
@Serializable
enum class ProgressStyle {
    CHAPTER_FRACTION,       // 章节进度分数：3/120
    CHAPTER_PERCENT,        // 章节进度百分比：2%
    PAGE_NUMBER,            // 页码：第3页
    BOOK_FRACTION,          // 全书进度分数：156/2000
    BOOK_PERCENT,           // 全书进度百分比：8%
}

/**
 * 主题颜色配置
 */
data class ThemeColors(
    val backgroundColor: Int,
    val textColor: Int,
    val headerColor: Int,
    val footerColor: Int,
    val progressColor: Int,
    val accentColor: Int,
)

/**
 * PageAnimType 转换为 PageDelegateFactory.PageAnimType
 */
fun PageAnimType.toFactoryType(): com.shuli.reader.core.reader.engine.animation.PageDelegateFactory.PageAnimType {
    return when (this) {
        PageAnimType.NONE -> com.shuli.reader.core.reader.engine.animation.PageDelegateFactory.PageAnimType.NONE
        PageAnimType.COVER -> com.shuli.reader.core.reader.engine.animation.PageDelegateFactory.PageAnimType.COVER
        PageAnimType.HORIZONTAL -> com.shuli.reader.core.reader.engine.animation.PageDelegateFactory.PageAnimType.HORIZONTAL
        PageAnimType.SIMULATION -> com.shuli.reader.core.reader.engine.animation.PageDelegateFactory.PageAnimType.SIMULATION
        PageAnimType.SCROLL -> com.shuli.reader.core.reader.engine.animation.PageDelegateFactory.PageAnimType.SCROLL
    }
}

/**
 * 从 UserPreferences 转换为 PageAnimType
 */
fun String.toPageAnimType(): PageAnimType {
    return when (this) {
        PageAnimConst.NONE -> PageAnimType.NONE
        PageAnimConst.OVERLAY -> PageAnimType.COVER
        PageAnimConst.SLIDE -> PageAnimType.HORIZONTAL
        PageAnimConst.SIMULATION -> PageAnimType.SIMULATION
        PageAnimConst.FADE -> PageAnimType.NONE  // 淡入淡出暂用无动画
        else -> PageAnimType.HORIZONTAL
    }
}

/**
 * 从 UserPreferences 字符串转换为 ReaderFontWeight
 */
fun String.toFontWeight(): ReaderFontWeight {
    return when (this) {
        "light" -> ReaderFontWeight.LIGHT
        "normal" -> ReaderFontWeight.NORMAL
        "medium" -> ReaderFontWeight.MEDIUM
        "bold" -> ReaderFontWeight.BOLD
        else -> ReaderFontWeight.NORMAL
    }
}

/**
 * 从 UserPreferences 字符串转换为 ReaderTextAlign
 */
fun String.toTextAlign(): ReaderTextAlign {
    return when (this) {
        "left" -> ReaderTextAlign.LEFT
        "justify" -> ReaderTextAlign.JUSTIFY
        else -> ReaderTextAlign.LEFT
    }
}

/**
 * 从 UserPreferences 字符串转换为 ChineseConvert
 */
fun String.toChineseConvert(): ChineseConvert {
    return when (this) {
        "none" -> ChineseConvert.NONE
        "simplified" -> ChineseConvert.SIMPLIFIED
        "traditional" -> ChineseConvert.TRADITIONAL
        else -> ChineseConvert.NONE
    }
}

/**
 * ReaderFontWeight 转换为字符串
 */
fun ReaderFontWeight.toStorageString(): String {
    return when (this) {
        ReaderFontWeight.LIGHT -> "light"
        ReaderFontWeight.NORMAL -> "normal"
        ReaderFontWeight.MEDIUM -> "medium"
        ReaderFontWeight.BOLD -> "bold"
    }
}

/**
 * ReaderTextAlign 转换为字符串
 */
fun ReaderTextAlign.toStorageString(): String {
    return when (this) {
        ReaderTextAlign.LEFT -> "left"
        ReaderTextAlign.JUSTIFY -> "justify"
    }
}

/**
 * ChineseConvert 转换为字符串
 */
fun ChineseConvert.toStorageString(): String {
    return when (this) {
        ChineseConvert.NONE -> "none"
        ChineseConvert.SIMPLIFIED -> "simplified"
        ChineseConvert.TRADITIONAL -> "traditional"
    }
}

/**
 * 从字符串转换为 HeaderVisibility
 */
fun String.toHeaderVisibility(): com.shuli.reader.core.reader.model.HeaderVisibility {
    return when (this) {
        "always_show" -> com.shuli.reader.core.reader.model.HeaderVisibility.ALWAYS_SHOW
        "always_hide" -> com.shuli.reader.core.reader.model.HeaderVisibility.ALWAYS_HIDE
        "hide_when_status_bar" -> com.shuli.reader.core.reader.model.HeaderVisibility.HIDE_WHEN_STATUS_BAR
        else -> com.shuli.reader.core.reader.model.HeaderVisibility.HIDE_WHEN_STATUS_BAR
    }
}

/**
 * HeaderVisibility 转换为字符串
 */
fun com.shuli.reader.core.reader.model.HeaderVisibility.toStorageString(): String {
    return when (this) {
        com.shuli.reader.core.reader.model.HeaderVisibility.ALWAYS_SHOW -> "always_show"
        com.shuli.reader.core.reader.model.HeaderVisibility.ALWAYS_HIDE -> "always_hide"
        com.shuli.reader.core.reader.model.HeaderVisibility.HIDE_WHEN_STATUS_BAR -> "hide_when_status_bar"
    }
}

/**
 * 从字符串转换为 SlotContent
 */
fun String.toSlotContent(): com.shuli.reader.core.reader.model.SlotContent {
    return when (this) {
        "none" -> com.shuli.reader.core.reader.model.SlotContent.NONE
        "chapter_title" -> com.shuli.reader.core.reader.model.SlotContent.CHAPTER_TITLE
        "book_title" -> com.shuli.reader.core.reader.model.SlotContent.BOOK_TITLE
        "page_number" -> com.shuli.reader.core.reader.model.SlotContent.CHAPTER_PROGRESS_FRACTION
        "chapter_progress_percent" -> com.shuli.reader.core.reader.model.SlotContent.CHAPTER_PROGRESS_PERCENT
        "book_progress_fraction" -> com.shuli.reader.core.reader.model.SlotContent.BOOK_PROGRESS_FRACTION
        "progress" -> com.shuli.reader.core.reader.model.SlotContent.BOOK_PROGRESS_PERCENT
        "time" -> com.shuli.reader.core.reader.model.SlotContent.TIME
        "battery" -> com.shuli.reader.core.reader.model.SlotContent.BATTERY
        "date" -> com.shuli.reader.core.reader.model.SlotContent.DATE
        else -> com.shuli.reader.core.reader.model.SlotContent.NONE
    }
}

/**
 * 字符串转换为 TitleAlign
 */
fun String.toTitleAlign(): com.shuli.reader.core.reader.model.TitleAlign {
    return when (this) {
        "left" -> com.shuli.reader.core.reader.model.TitleAlign.LEFT
        "center" -> com.shuli.reader.core.reader.model.TitleAlign.CENTER
        "hidden" -> com.shuli.reader.core.reader.model.TitleAlign.HIDDEN
        else -> com.shuli.reader.core.reader.model.TitleAlign.CENTER
    }
}

/**
 * TitleAlign 转换为字符串
 */
fun com.shuli.reader.core.reader.model.TitleAlign.toStorageString(): String {
    return when (this) {
        com.shuli.reader.core.reader.model.TitleAlign.LEFT -> "left"
        com.shuli.reader.core.reader.model.TitleAlign.CENTER -> "center"
        com.shuli.reader.core.reader.model.TitleAlign.HIDDEN -> "hidden"
    }
}

/**
 * SlotContent 转换为字符串
 */
fun com.shuli.reader.core.reader.model.SlotContent.toStorageString(): String {
    return when (this) {
        com.shuli.reader.core.reader.model.SlotContent.NONE -> "none"
        com.shuli.reader.core.reader.model.SlotContent.CHAPTER_TITLE -> "chapter_title"
        com.shuli.reader.core.reader.model.SlotContent.BOOK_TITLE -> "book_title"
        com.shuli.reader.core.reader.model.SlotContent.CHAPTER_PROGRESS_FRACTION -> "page_number"
        com.shuli.reader.core.reader.model.SlotContent.CHAPTER_PROGRESS_PERCENT -> "chapter_progress_percent"
        com.shuli.reader.core.reader.model.SlotContent.BOOK_PROGRESS_FRACTION -> "book_progress_fraction"
        com.shuli.reader.core.reader.model.SlotContent.BOOK_PROGRESS_PERCENT -> "progress"
        com.shuli.reader.core.reader.model.SlotContent.TIME -> "time"
        com.shuli.reader.core.reader.model.SlotContent.BATTERY -> "battery"
        com.shuli.reader.core.reader.model.SlotContent.DATE -> "date"
    }
}

/**
 * IndentUnit 转换为字符串
 */
fun IndentUnit.toStorageString(): String {
    return when (this) {
        IndentUnit.CHARACTER -> "character"
        IndentUnit.PIXEL -> "pixel"
    }
}

/**
 * 从字符串转换为 IndentUnit
 */
fun String.toIndentUnit(): IndentUnit {
    return when (this) {
        "character" -> IndentUnit.CHARACTER
        "pixel" -> IndentUnit.PIXEL
        else -> IndentUnit.CHARACTER
    }
}

/**
 * ProgressStyle 转换为字符串
 */
fun ProgressStyle.toStorageString(): String {
    return when (this) {
        ProgressStyle.CHAPTER_FRACTION -> "chapter_fraction"
        ProgressStyle.CHAPTER_PERCENT -> "chapter_percent"
        ProgressStyle.PAGE_NUMBER -> "page_number"
        ProgressStyle.BOOK_FRACTION -> "book_fraction"
        ProgressStyle.BOOK_PERCENT -> "book_percent"
    }
}

/**
 * 从字符串转换为 ProgressStyle
 */
fun String.toProgressStyle(): ProgressStyle {
    return when (this) {
        "chapter_fraction" -> ProgressStyle.CHAPTER_FRACTION
        "chapter_percent" -> ProgressStyle.CHAPTER_PERCENT
        "page_number" -> ProgressStyle.PAGE_NUMBER
        "book_fraction" -> ProgressStyle.BOOK_FRACTION
        "book_percent" -> ProgressStyle.BOOK_PERCENT
        else -> ProgressStyle.CHAPTER_FRACTION
    }
}

@Serializable
enum class DualPageMode { AUTO, SINGLE, DUAL }

@Serializable
enum class OrientationLock { SYSTEM, PORTRAIT, LANDSCAPE }

@Serializable
enum class PageAnimSpeed(val durationMs: Int) {
    FAST(100),
    NORMAL(250),
    SLOW(400);

    companion object {
        fun fromDurationMs(ms: Int): PageAnimSpeed =
            entries.firstOrNull { it.durationMs == ms } ?: NORMAL
    }
}

/**
 * 将 ReaderPreferences 转换为 Paginator 使用的 ReaderLayoutConfig。
 * 独立边距使用 nullable fallback：`marginTop ?: marginVertical`。
 */
fun ReaderPreferences.toLayoutConfig(
    pageSize: com.shuli.reader.core.reader.model.PageSize,
    density: Float,
): com.shuli.reader.core.reader.model.ReaderLayoutConfig {
    val textSizePx = fontSize * density
    val mt = (marginTop ?: marginVertical) * density
    val mb = (marginBottom ?: marginVertical) * density
    val ml = (marginLeft ?: marginHorizontal) * density
    val mr = (marginRight ?: marginHorizontal) * density
    val indentPx = when (indentUnit) {
        IndentUnit.CHARACTER -> indent * textSizePx
        IndentUnit.PIXEL -> indent * density
    }
    return com.shuli.reader.core.reader.model.ReaderLayoutConfig(
        pageSize = pageSize,
        textSize = textSizePx,
        lineHeight = lineSpacing,
        paragraphSpacing = paragraphSpacing * textSizePx,
        marginTop = mt,
        marginBottom = mb,
        marginLeft = ml,
        marginRight = mr,
        indent = indentPx,
        density = density,
        letterSpacingPx = letterSpacing * textSizePx,
        titleStyle = titleStyle,
        useZhLayout = useZhLayout,
        bottomJustify = bottomJustify,
        headerMarginTop = header.marginTop * density,
        footerMarginBottom = footer.marginBottom * density,
    )
}

/**
 * 按 Registry key 读取 ReaderPreferences 中对应的值。
 *
 * 用于 PresetSnapshot 等需要按 key 动态遍历的场景。
 * 复合类型（titleStyle/header/footer）返回 null，调用方应特殊处理。
 */
@Suppress("UNCHECKED_CAST")
fun <T> ReaderPreferences.getValueByKey(key: String): T? = when (key) {
    "font_size" -> fontSize
    "line_spacing" -> lineSpacing
    "paragraph_spacing" -> paragraphSpacing
    "indent" -> indent
    "preserve_original_indent" -> preserveOriginalIndent
    "indent_unit" -> indentUnit
    "page_anim_type" -> pageAnimType
    "background_color" -> backgroundColor
    "margin_horizontal" -> marginHorizontal
    "margin_vertical" -> marginVertical
    "brightness" -> brightness
    "reading_font" -> readingFont
    "optimize_render" -> optimizeRender
    "letter_spacing" -> letterSpacing
    "font_weight" -> fontWeight
    "text_align" -> textAlign
    "chinese_convert" -> chineseConvert
    "header_footer_alpha" -> headerFooterAlpha
    "show_progress" -> showProgress
    "use_zh_layout" -> useZhLayout
    "use_pangu_spacing" -> usePanguSpacing
    "show_header_line" -> showHeaderLine
    "show_footer_line" -> showFooterLine
    "header_font_size_ratio" -> headerFontSizeRatio
    "footer_font_size_ratio" -> footerFontSizeRatio
    "bottom_justify" -> bottomJustify
    "keep_screen_on" -> keepScreenOn
    "volume_key_turn_page" -> volumeKeyTurnPage
    "edge_turn_page" -> edgeTurnPage
    "edge_width_percent" -> edgeWidthPercent
    "immersive_mode" -> immersiveMode
    "tts_speed" -> ttsSpeed
    "tts_pitch" -> ttsPitch
    "max_page_width" -> maxPageWidth
    "remove_empty_lines" -> removeEmptyLines
    "clean_chapter_title" -> cleanChapterTitle
    "progress_style" -> progressStyle
    "auto_page_turn" -> autoPageTurn
    "auto_page_turn_interval" -> autoPageTurnInterval
    "epub_override_style" -> epubOverrideStyle
    "left_zone_ratio" -> leftZoneRatio
    "custom_background_color" -> customBackgroundColor
    "custom_text_color" -> customTextColor
    "custom_title_color" -> customTitleColor
    "custom_header_footer_color" -> customHeaderFooterColor
    "color_temperature" -> colorTemperature
    "paragraph_divider" -> paragraphDivider
    "margin_top" -> marginTop
    "margin_bottom" -> marginBottom
    "margin_left" -> marginLeft
    "margin_right" -> marginRight
    "bionic_reading" -> bionicReading
    "vertical_text" -> verticalText
    "dual_page_mode" -> dualPageMode
    "haptic_feedback" -> hapticFeedback
    "orientation_lock" -> orientationLock
    "page_anim_speed" -> pageAnimSpeed.durationMs
    "ad_filtering" -> adFiltering
    "tts_voice" -> ttsVoice
    "tts_auto_page" -> ttsAutoPage
    "tts_timer" -> ttsTimer
    "eye_care_reminder_interval" -> eyeCareReminderInterval
    "background_texture" -> backgroundTexture
    // 复合类型：返回 null，由调用方特殊处理
    "title_style" -> null
    "header_visibility" -> header.visibility
    "footer_visibility" -> footer.visibility
    "title_font" -> titleFont
    "gesture_config" -> gestureConfig
    else -> null
} as T?
