package com.shuli.reader.core.data

import com.shuli.reader.core.reader.FooterConfig
import com.shuli.reader.core.reader.HeaderConfig
import com.shuli.reader.core.reader.TitleStyleConfig
import kotlinx.serialization.Serializable

/**
 * 阅读器显示偏好数据类
 * 用于统一管理阅读页的显示设置
 */
@Serializable
data class ReaderPreferences(
    val fontSize: Float = 16f,
    val lineSpacing: Float = 1.5f,
    val paragraphSpacing: Float = 1.0f,
    val indent: Float = 2.0f,
    val pageAnimType: PageAnimType = PageAnimType.HORIZONTAL,
    val backgroundColor: ReaderTheme = ReaderTheme.PAPER,
    val marginHorizontal: Float = 24f,
    val marginVertical: Float = 48f,
    val brightness: Float = -1f,
    val readingFont: String = "harmony",
    val optimizeRender: Boolean = true,  // 渲染优化总开关，关闭则降级为 Bitmap 逐帧绘制
    // 阶段三新增字段
    val letterSpacing: Float = 0f,       // 字距，单位 em（字号倍数），范围 0..0.2
    val fontWeight: ReaderFontWeight = ReaderFontWeight.NORMAL,
    val textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
    val chineseConvert: ChineseConvert = ChineseConvert.NONE,
    // 阶段五新增字段
    val titleStyle: TitleStyleConfig = TitleStyleConfig(),
    val header: HeaderConfig = HeaderConfig(),
    val footer: FooterConfig = FooterConfig(),
    val headerFooterAlpha: Float = 0.4f,
    val showProgress: Boolean = true,
    // 排版增强
    val useZhLayout: Boolean = false,    // 自定义中文分行（标点避头尾）
    val usePanguSpacing: Boolean = false, // 中英文之间自动加空格
    // 阶段六新增字段
    val keepScreenOn: Boolean = false,
    val volumeKeyTurnPage: Boolean = false,
    val edgeTurnPage: Boolean = true,
    // TTS 设置
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
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
    OLED,     // OLED
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
fun PageAnimType.toFactoryType(): com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType {
    return when (this) {
        PageAnimType.NONE -> com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType.NONE
        PageAnimType.COVER -> com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType.COVER
        PageAnimType.HORIZONTAL -> com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType.HORIZONTAL
        PageAnimType.SIMULATION -> com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType.SIMULATION
        PageAnimType.SCROLL -> com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType.SCROLL
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
fun String.toHeaderVisibility(): com.shuli.reader.core.reader.HeaderVisibility {
    return when (this) {
        "always_show" -> com.shuli.reader.core.reader.HeaderVisibility.ALWAYS_SHOW
        "always_hide" -> com.shuli.reader.core.reader.HeaderVisibility.ALWAYS_HIDE
        "hide_when_status_bar" -> com.shuli.reader.core.reader.HeaderVisibility.HIDE_WHEN_STATUS_BAR
        else -> com.shuli.reader.core.reader.HeaderVisibility.HIDE_WHEN_STATUS_BAR
    }
}

/**
 * HeaderVisibility 转换为字符串
 */
fun com.shuli.reader.core.reader.HeaderVisibility.toStorageString(): String {
    return when (this) {
        com.shuli.reader.core.reader.HeaderVisibility.ALWAYS_SHOW -> "always_show"
        com.shuli.reader.core.reader.HeaderVisibility.ALWAYS_HIDE -> "always_hide"
        com.shuli.reader.core.reader.HeaderVisibility.HIDE_WHEN_STATUS_BAR -> "hide_when_status_bar"
    }
}

/**
 * 从字符串转换为 SlotContent
 */
fun String.toSlotContent(): com.shuli.reader.core.reader.SlotContent {
    return when (this) {
        "none" -> com.shuli.reader.core.reader.SlotContent.NONE
        "chapter_title" -> com.shuli.reader.core.reader.SlotContent.CHAPTER_TITLE
        "book_title" -> com.shuli.reader.core.reader.SlotContent.BOOK_TITLE
        "page_number" -> com.shuli.reader.core.reader.SlotContent.PAGE_NUMBER
        "progress" -> com.shuli.reader.core.reader.SlotContent.PROGRESS
        "time" -> com.shuli.reader.core.reader.SlotContent.TIME
        "battery" -> com.shuli.reader.core.reader.SlotContent.BATTERY
        "date" -> com.shuli.reader.core.reader.SlotContent.DATE
        else -> com.shuli.reader.core.reader.SlotContent.NONE
    }
}

/**
 * 字符串转换为 TitleAlign
 */
fun String.toTitleAlign(): com.shuli.reader.core.reader.TitleAlign {
    return when (this) {
        "left" -> com.shuli.reader.core.reader.TitleAlign.LEFT
        "center" -> com.shuli.reader.core.reader.TitleAlign.CENTER
        "hidden" -> com.shuli.reader.core.reader.TitleAlign.HIDDEN
        else -> com.shuli.reader.core.reader.TitleAlign.CENTER
    }
}

/**
 * TitleAlign 转换为字符串
 */
fun com.shuli.reader.core.reader.TitleAlign.toStorageString(): String {
    return when (this) {
        com.shuli.reader.core.reader.TitleAlign.LEFT -> "left"
        com.shuli.reader.core.reader.TitleAlign.CENTER -> "center"
        com.shuli.reader.core.reader.TitleAlign.HIDDEN -> "hidden"
    }
}

/**
 * SlotContent 转换为字符串
 */
fun com.shuli.reader.core.reader.SlotContent.toStorageString(): String {
    return when (this) {
        com.shuli.reader.core.reader.SlotContent.NONE -> "none"
        com.shuli.reader.core.reader.SlotContent.CHAPTER_TITLE -> "chapter_title"
        com.shuli.reader.core.reader.SlotContent.BOOK_TITLE -> "book_title"
        com.shuli.reader.core.reader.SlotContent.PAGE_NUMBER -> "page_number"
        com.shuli.reader.core.reader.SlotContent.PROGRESS -> "progress"
        com.shuli.reader.core.reader.SlotContent.TIME -> "time"
        com.shuli.reader.core.reader.SlotContent.BATTERY -> "battery"
        com.shuli.reader.core.reader.SlotContent.DATE -> "date"
    }
}
