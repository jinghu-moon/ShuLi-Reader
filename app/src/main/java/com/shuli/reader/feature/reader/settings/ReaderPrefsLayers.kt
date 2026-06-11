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
    val focusLine: Boolean,
    val brightness: Float,
    val hapticFeedback: Boolean,
    val eyeCareReminderInterval: Int,
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
    val customAccentColor: Int?,
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
    val autoNightMode: Boolean,
    val customBackgroundColor: Int?,
    val customTextColor: Int?,
    val titleFont: String,
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
    val wordSpacing: Float,
    val paragraphDivider: Boolean,
    val bottomJustify: Boolean,
    val maxPageWidth: Float,
    val removeEmptyLines: Boolean,
    val cleanChapterTitle: Boolean,
    val epubOverrideStyle: Boolean,
    val adFiltering: Boolean,
    val verticalText: Boolean,
    val dualPageMode: DualPageMode,
    val pageAnimSpeed: PageAnimSpeed,
    val orientationLock: OrientationLock,
)

fun ReaderPreferences.toOverlayPrefs(): OverlayPrefs = OverlayPrefs(
    colorTemperature = colorTemperature,
    focusLine = focusLine,
    brightness = brightness,
    hapticFeedback = hapticFeedback,
    eyeCareReminderInterval = eyeCareReminderInterval,
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
    customAccentColor = customAccentColor,
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
    autoNightMode = autoNightMode,
    customBackgroundColor = customBackgroundColor,
    customTextColor = customTextColor,
    titleFont = "",
)

fun ReaderPreferences.toLayoutPrefs(): LayoutPrefs = LayoutPrefs(
    fontSize = fontSize,
    lineSpacing = lineSpacing,
    paragraphSpacing = paragraphSpacing,
    indent = indent,
    indentUnit = indentUnit,
    letterSpacing = letterSpacing,
    marginHorizontal = marginHorizontal,
    marginVertical = marginVertical,
    marginTop = marginTop,
    marginBottom = marginBottom,
    marginLeft = marginLeft,
    marginRight = marginRight,
    wordSpacing = wordSpacing,
    paragraphDivider = paragraphDivider,
    bottomJustify = bottomJustify,
    maxPageWidth = maxPageWidth,
    removeEmptyLines = removeEmptyLines,
    cleanChapterTitle = cleanChapterTitle,
    epubOverrideStyle = epubOverrideStyle,
    adFiltering = adFiltering,
    verticalText = verticalText,
    dualPageMode = dualPageMode,
    pageAnimSpeed = pageAnimSpeed,
    orientationLock = orientationLock,
)
