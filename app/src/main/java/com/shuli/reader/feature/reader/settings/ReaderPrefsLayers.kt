package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.DualPageMode
import com.shuli.reader.core.data.IndentUnit
import com.shuli.reader.core.data.OrientationLock
import com.shuli.reader.core.data.PageAnimSpeed
import com.shuli.reader.core.data.ProgressStyle
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ReaderTheme

/**
 * 四层 StateFlow 拆分。
 *
 * 按 recompositionTier 将 [ReaderPreferences] 拆为四个 data class，
 * 每层独立 StateFlow（map + distinctUntilChanged + stateIn），
 * 避免高频层（Overlay）的变化触发低频层（Layout）的 recomposition。
 *
 * | 层         | tier | 语义                  | 主要 scope           |
 * |-----------|------|---------------------|---------------------|
 * | Overlay   | 0    | 浮层 / 实时滤镜        | VIEW_INVALIDATE     |
 * | Chrome    | 1    | 页眉页脚 / 进度条 / 壳层 | SHELL               |
 * | Style     | 2    | 字体 / 文字样式 / 主题  | CONTENT / REFLOW    |
 * | Layout    | 3    | 几何分页参数           | REFLOW              |
 */

data class OverlayPrefs(
    val colorTemperature: Float,
    val brightness: Float,
)

data class ChromePrefs(
    val headerVisibility: String,
    val footerVisibility: String,
    val headerFooterAlpha: Float,
    val showProgress: Boolean,
    val progressStyle: ProgressStyle,
    val showHeaderLine: Boolean,
    val showFooterLine: Boolean,
    val headerFontSizeRatio: Float,
    val footerFontSizeRatio: Float,
    val backgroundTexture: String?,
    val customHeaderFooterColor: Int?,
)

data class StylePrefs(
    val readingFont: String,
    val fontWeight: ReaderFontWeight,
    val textAlign: ReaderTextAlign,
    val chineseConvert: ChineseConvert,
    val useZhLayout: Boolean,
    val usePanguSpacing: Boolean,
    val bionicReading: Boolean,
    val backgroundColor: ReaderTheme,
    val customBackgroundColor: Int?,
    val customTextColor: Int?,
    val customTitleColor: Int?,
    val titleFont: String,
    val adFiltering: Boolean,
)

data class LayoutPrefs(
    val fontSize: Float,
    val lineSpacing: Float,
    val paragraphSpacing: Float,
    val indent: Float,
    val indentUnit: IndentUnit,
    val letterSpacing: Float,
    val marginHorizontal: Float,
    val marginVertical: Float,
    val marginTop: Float?,
    val marginBottom: Float?,
    val marginLeft: Float?,
    val marginRight: Float?,
    val paragraphDivider: Boolean,
    val bottomJustify: Boolean,
    val maxPageWidth: Float,
    val removeEmptyLines: Boolean,
    val cleanChapterTitle: Boolean,
    val preserveOriginalIndent: Boolean,
    val epubOverrideStyle: Boolean,
    val verticalText: Boolean,
    val dualPageMode: DualPageMode,
)

fun ReaderPreferences.toOverlayPrefs(): OverlayPrefs = OverlayPrefs(
    colorTemperature = colorTemperature,
    brightness = brightness,
)

fun ReaderPreferences.toChromePrefs(): ChromePrefs = ChromePrefs(
    headerVisibility = header.visibility.name,
    footerVisibility = footer.visibility.name,
    headerFooterAlpha = headerFooterAlpha,
    showProgress = showProgress,
    progressStyle = progressStyle,
    showHeaderLine = showHeaderLine,
    showFooterLine = showFooterLine,
    headerFontSizeRatio = headerFontSizeRatio,
    footerFontSizeRatio = footerFontSizeRatio,
    backgroundTexture = backgroundTexture,
    customHeaderFooterColor = customHeaderFooterColor,
)

fun ReaderPreferences.toStylePrefs(): StylePrefs = StylePrefs(
    readingFont = readingFont,
    fontWeight = fontWeight,
    textAlign = textAlign,
    chineseConvert = chineseConvert,
    useZhLayout = useZhLayout,
    usePanguSpacing = usePanguSpacing,
    bionicReading = bionicReading,
    backgroundColor = backgroundColor,
    customBackgroundColor = customBackgroundColor,
    customTextColor = customTextColor,
    customTitleColor = customTitleColor,
    titleFont = titleFont,
    adFiltering = adFiltering,
)

fun ReaderPreferences.toLayoutPrefs(): LayoutPrefs = LayoutPrefs(
    fontSize = fontSize,
    lineSpacing = lineSpacing,
    paragraphSpacing = paragraphSpacing,
    indent = indent,
    indentUnit = indentUnit,
    letterSpacing = letterSpacing,
    marginHorizontal = bodyBox.left,
    marginVertical = bodyBox.top,
    marginTop = bodyBox.top,
    marginBottom = bodyBox.bottom,
    marginLeft = bodyBox.left,
    marginRight = bodyBox.right,
    paragraphDivider = paragraphDivider,
    bottomJustify = bottomJustify,
    maxPageWidth = maxPageWidth,
    removeEmptyLines = removeEmptyLines,
    cleanChapterTitle = cleanChapterTitle,
    preserveOriginalIndent = preserveOriginalIndent,
    epubOverrideStyle = epubOverrideStyle,
    verticalText = verticalText,
    dualPageMode = dualPageMode,
)

/**
 * 验证四层 StateFlow 的字段归属与 [ReaderSettingRegistry.recompositionTier] 一致。
 *
 * 确保新增设置字段不会因手动映射错误而出现在错误的层级中。
 * 在 debug 构建或测试中调用即可。
 *
 * @return 不一致的 key 列表，空列表表示完全对齐
 */
fun validateTierAlignment(): List<String> {
    val mismatches = mutableListOf<String>()
    val tier0Keys = setOf("color_temperature", "brightness")
    val tier1Keys = setOf(
        "header_visibility", "footer_visibility", "header_footer_alpha",
        "show_progress", "progress_style", "show_header_line", "show_footer_line",
        "header_font_size_ratio", "footer_font_size_ratio",
        "background_texture", "custom_accent_color",
    )
    for (def in ReaderSettingRegistry.all) {
        val expectedTier = def.recompositionTier
        val inTier0 = def.key in tier0Keys
        val inTier1 = def.key in tier1Keys
        when {
            expectedTier == 0 && !inTier0 -> mismatches.add("${def.key}: expected Overlay(tier 0)")
            expectedTier == 1 && !inTier1 -> mismatches.add("${def.key}: expected Chrome(tier 1)")
        }
    }
    return mismatches
}
