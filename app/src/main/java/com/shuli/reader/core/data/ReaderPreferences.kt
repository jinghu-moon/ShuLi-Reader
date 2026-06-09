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
    val indentUnit: IndentUnit = IndentUnit.CHARACTER,
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
    // 页眉页脚增强
    val showHeaderLine: Boolean = false,       // 页眉分割线
    val showFooterLine: Boolean = false,       // 页脚分割线
    val headerFontSizeRatio: Float = 0.75f,    // 页眉字号比例
    val footerFontSizeRatio: Float = 0.75f,    // 页脚字号比例
    // 排版增强2
    val bottomJustify: Boolean = false,        // 底部对齐（均匀分布行间距）
    // 阶段六新增字段
    val keepScreenOn: Boolean = false,
    val volumeKeyTurnPage: Boolean = false,
    val edgeTurnPage: Boolean = true,
    val edgeWidthPercent: Float = 0.33f,       // 边缘触摸宽度百分比
    val immersiveMode: Boolean = false,        // 沉浸模式：隐藏状态栏和导航栏
    // TTS 设置
    val ttsSpeed: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    // P1: 排版增强
    val maxPageWidth: Float = 0f,              // 页面最大宽度（dp），0 = 不限制
    val removeEmptyLines: Boolean = false,     // 去除多余空行
    val cleanChapterTitle: Boolean = false,    // 章节标题清理（去除序号、多余空格）
    // P1: 进度显示样式
    val progressStyle: ProgressStyle = ProgressStyle.CHAPTER_FRACTION,
    // P2: 低频增强
    val autoNightMode: Boolean = false,          // 自动夜间模式（跟随系统暗色）
    val autoPageTurn: Boolean = false,           // 自动翻页
    val autoPageTurnInterval: Float = 10f,       // 自动翻页间隔（秒），5..60
    val epubOverrideStyle: Boolean = true,       // EPUB 覆盖样式（true=使用阅读器样式，false=保留EPUB原样式）
    // P0: 触控热区
    val leftZoneRatio: Float = 0.33f,            // 左侧点击区域宽度比例（0.2~0.5），右侧对称，中间为剩余
    // P1: 自定义主题颜色
    val customBackgroundColor: Int? = null,       // 自定义背景色（ARGB），null = 使用默认
    val customTextColor: Int? = null,             // 自定义正文色（ARGB），null = 使用默认
    val customAccentColor: Int? = null,           // 自定义强调色（ARGB），null = 使用默认
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
        "page_number" -> com.shuli.reader.core.reader.SlotContent.CHAPTER_PROGRESS_FRACTION
        "chapter_progress_percent" -> com.shuli.reader.core.reader.SlotContent.CHAPTER_PROGRESS_PERCENT
        "book_progress_fraction" -> com.shuli.reader.core.reader.SlotContent.BOOK_PROGRESS_FRACTION
        "progress" -> com.shuli.reader.core.reader.SlotContent.BOOK_PROGRESS_PERCENT
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
        com.shuli.reader.core.reader.SlotContent.CHAPTER_PROGRESS_FRACTION -> "page_number"
        com.shuli.reader.core.reader.SlotContent.CHAPTER_PROGRESS_PERCENT -> "chapter_progress_percent"
        com.shuli.reader.core.reader.SlotContent.BOOK_PROGRESS_FRACTION -> "book_progress_fraction"
        com.shuli.reader.core.reader.SlotContent.BOOK_PROGRESS_PERCENT -> "progress"
        com.shuli.reader.core.reader.SlotContent.TIME -> "time"
        com.shuli.reader.core.reader.SlotContent.BATTERY -> "battery"
        com.shuli.reader.core.reader.SlotContent.DATE -> "date"
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
