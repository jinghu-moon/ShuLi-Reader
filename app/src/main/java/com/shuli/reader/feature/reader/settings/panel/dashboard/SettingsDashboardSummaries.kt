package com.shuli.reader.feature.reader.settings.panel.dashboard

import androidx.compose.runtime.Immutable
import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.DualPageMode
import com.shuli.reader.core.data.OrientationLock
import com.shuli.reader.core.data.PageAnimSpeed
import com.shuli.reader.core.data.PageAnimType
import com.shuli.reader.core.data.ProgressStyle
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.i18n.ReaderStrings
import com.shuli.reader.core.reader.model.BoxInsetsDp
import com.shuli.reader.core.reader.model.HeaderVisibility
import com.shuli.reader.core.reader.model.SlotContent
import com.shuli.reader.core.reader.model.TitleAlign
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.core.font.FontManager
import com.shuli.reader.feature.reader.settings.panel.defaultSlotDisplays
import com.shuli.reader.feature.reader.settings.panel.controls.MarginPreset
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class SettingsDashboardSummaries(
    val font: FontCardSummary,
    val bodyTypography: BodyTypographySummary,
    val textProcessing: TextProcessingSummary,
    val bodyArea: BodyAreaSummary,
    val titleStyle: TitleStyleSummary,
    val headerFooter: HeaderFooterSummary,
    val marginPreset: MarginPresetSummary,
    val pageTurnMethod: PageTurnMethodSummary,
    val touchZone: TouchZoneSummary,
    val pageTurnAnimation: PageTurnAnimationSummary,
    val eyeCare: EyeCareSummary,
    val screenState: ScreenStateSummary,
    val readingForm: ReadingFormSummary,
)

@Immutable
data class FontCardSummary(
    val fontName: String,
    val weightName: String,
    val contentDescription: String,
)

@Immutable
data class BodyTypographySummary(
    val fontSizeSp: Int,
    val lineSpacing: Float,
    val paragraphSpacing: Float,
    val indent: Float,
    val verticalText: Boolean,
    val contentDescription: String,
)

@Immutable
data class TextProcessingSummary(
    val chips: List<String>,
    val hiddenCount: Int,
    val emptyLabel: String,
    val contentDescription: String,
)

@Immutable
data class BodyAreaSummary(
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int,
    val contentDescription: String,
)

@Immutable
data class TitleStyleSummary(
    val fontSizeSp: Int,
    val sizeOffsetSp: Int,
    val align: TitleAlign,
    val alignLabel: String,
    val contentDescription: String,
)

@Immutable
data class HeaderFooterSummary(
    val headerVisible: Boolean,
    val footerVisible: Boolean,
    val headerSlots: List<String>,
    val footerSlots: List<String>,
    val contentDescription: String,
)

@Immutable
data class MarginPresetSummary(
    val label: String,
    val exactMatch: Boolean,
    val contentDescription: String,
)

@Immutable
data class PageTurnMethodSummary(
    val items: List<StatusLine>,
    val emptyLabel: String,
    val contentDescription: String,
)

@Immutable
data class TouchZoneSummary(
    val leftPercent: Int,
    val rightPercent: Int,
    val centerPercent: Int,
    val hapticEnabled: Boolean,
    val contentDescription: String,
)

@Immutable
data class PageTurnAnimationSummary(
    val type: PageAnimType,
    val typeLabel: String,
    val speed: PageAnimSpeed,
    val speedLabel: String,
    val speedProgress: Float,
    val contentDescription: String,
)

@Immutable
data class EyeCareSummary(
    val colorTemperatureK: Int,
    val warmthPercent: Int,
    val reminderLabel: String?,
    val enabled: Boolean,
    val contentDescription: String,
)

@Immutable
data class ScreenStateSummary(
    val items: List<StatusLine>,
    val contentDescription: String,
)

@Immutable
data class ReadingFormSummary(
    val dualPageMode: DualPageMode,
    val modeLabel: String,
    val textureLabel: String,
    val verticalText: Boolean,
    val contentDescription: String,
)

@Immutable
data class StatusLine(
    val label: String,
    val value: String,
    val enabled: Boolean,
)

internal fun buildSettingsDashboardSummaries(
    prefs: ReaderPreferences,
    strings: ReaderStrings,
    customFonts: List<FontManager.FontEntry>,
    gestureConfig: GestureConfig,
): SettingsDashboardSummaries {
    val fontName = resolveFontName(prefs.readingFont, strings, customFonts)
    val weightName = prefs.fontWeight.label(strings)
    val body = BodyTypographySummary(
        fontSizeSp = prefs.fontSize.roundToInt(),
        lineSpacing = prefs.lineSpacing,
        paragraphSpacing = prefs.paragraphSpacing,
        indent = prefs.indent,
        verticalText = prefs.verticalText,
        contentDescription = "正文排版：${prefs.fontSize.roundToInt()}sp，行距${formatOneDecimal(prefs.lineSpacing)}，段距${formatOneDecimal(prefs.paragraphSpacing)}",
    )

    val textProcessing = enabledTextProcessingChips(prefs, strings)
    val visibleChips = textProcessing.take(5)
    val hiddenCount = (textProcessing.size - visibleChips.size).coerceAtLeast(0)

    val bodyArea = BodyAreaSummary(
        top = prefs.bodyBox.top.roundToInt(),
        bottom = prefs.bodyBox.bottom.roundToInt(),
        left = prefs.bodyBox.left.roundToInt(),
        right = prefs.bodyBox.right.roundToInt(),
        contentDescription = "正文区域：上${prefs.bodyBox.top.roundToInt()}，下${prefs.bodyBox.bottom.roundToInt()}，左${prefs.bodyBox.left.roundToInt()}，右${prefs.bodyBox.right.roundToInt()}",
    )

    val titleAlignLabel = prefs.titleStyle.align.label(strings)
    val headerSlots = listOf(prefs.header.left, prefs.header.center, prefs.header.right).map { it.shortLabel(strings) }
    val footerSlots = listOf(prefs.footer.left, prefs.footer.center, prefs.footer.right).map { it.shortLabel(strings) }
    val margin = detectMarginPreset(prefs, strings)
    val pageTurnItems = buildPageTurnItems(prefs, strings)
    val screenItems = buildScreenStateItems(prefs, strings)
    val pageAnimLabel = prefs.pageAnimType.label(strings)
    val pageSpeedLabel = prefs.pageAnimSpeed.label(strings)
    val warmthPercent = (((6500f - prefs.colorTemperature) / 4500f) * 100f).roundToInt().coerceIn(0, 100)
    val reminderLabel = reminderLabel(prefs.eyeCareReminderInterval, strings)
    val eyeCareEnabled = warmthPercent > 0 || prefs.eyeCareReminderInterval > 0
    val readingMode = prefs.dualPageMode.label(strings)
    val texture = textureLabel(prefs.backgroundTexture, strings)

    return SettingsDashboardSummaries(
        font = FontCardSummary(
            fontName = fontName,
            weightName = weightName,
            contentDescription = "字体：$fontName，字重：$weightName",
        ),
        bodyTypography = body,
        textProcessing = TextProcessingSummary(
            chips = visibleChips,
            hiddenCount = hiddenCount,
            emptyLabel = "所有处理已关闭",
            contentDescription = if (textProcessing.isEmpty()) {
                "文本处理：所有处理已关闭"
            } else {
                "文本处理：${textProcessing.joinToString("，")}"
            },
        ),
        bodyArea = bodyArea,
        titleStyle = TitleStyleSummary(
            fontSizeSp = prefs.titleFontSize.roundToInt(),
            sizeOffsetSp = prefs.titleStyle.sizeOffsetSp,
            align = prefs.titleStyle.align,
            alignLabel = titleAlignLabel,
            contentDescription = "标题样式：${prefs.titleFontSize.roundToInt()}sp，偏移${prefs.titleStyle.sizeOffsetSp}sp，$titleAlignLabel",
        ),
        headerFooter = HeaderFooterSummary(
            headerVisible = prefs.header.visibility != HeaderVisibility.ALWAYS_HIDE,
            footerVisible = prefs.footer.visibility != HeaderVisibility.ALWAYS_HIDE,
            headerSlots = headerSlots,
            footerSlots = footerSlots,
            contentDescription = "页眉页脚：页眉${headerSlots.joinToString("、")}，页脚${footerSlots.joinToString("、")}",
        ),
        marginPreset = MarginPresetSummary(
            label = margin.first?.label ?: "自定义",
            exactMatch = margin.second,
            contentDescription = "边距方案：${margin.first?.label ?: "自定义"}",
        ),
        pageTurnMethod = PageTurnMethodSummary(
            items = pageTurnItems,
            emptyLabel = "仅手势翻页",
            contentDescription = if (pageTurnItems.isEmpty()) {
                "翻页方式：仅手势翻页"
            } else {
                "翻页方式：${pageTurnItems.joinToString("，") { "${it.label}${it.value}" }}"
            },
        ),
        touchZone = TouchZoneSummary(
            leftPercent = (prefs.leftZoneRatio * 100).roundToInt(),
            rightPercent = (prefs.leftZoneRatio * 100).roundToInt(),
            centerPercent = ((1f - prefs.leftZoneRatio * 2f) * 100f).roundToInt().coerceAtLeast(0),
            hapticEnabled = prefs.hapticFeedback,
            contentDescription = "触控区域：左${(prefs.leftZoneRatio * 100).roundToInt()}%，右${(prefs.leftZoneRatio * 100).roundToInt()}%，振动反馈${if (prefs.hapticFeedback) "开启" else "关闭"}",
        ),
        pageTurnAnimation = PageTurnAnimationSummary(
            type = prefs.pageAnimType,
            typeLabel = pageAnimLabel,
            speed = prefs.pageAnimSpeed,
            speedLabel = pageSpeedLabel,
            speedProgress = prefs.pageAnimSpeed.progress(),
            contentDescription = "翻页动效：$pageAnimLabel，速度$pageSpeedLabel",
        ),
        eyeCare = EyeCareSummary(
            colorTemperatureK = prefs.colorTemperature.roundToInt(),
            warmthPercent = warmthPercent,
            reminderLabel = reminderLabel,
            enabled = eyeCareEnabled,
            contentDescription = if (eyeCareEnabled) {
                "护眼：色温${prefs.colorTemperature.roundToInt()}K，提醒${reminderLabel ?: strings.offLabel}"
            } else {
                "护眼：未开启"
            },
        ),
        screenState = ScreenStateSummary(
            items = screenItems,
            contentDescription = "屏幕状态：${screenItems.joinToString("，") { "${it.label}${it.value}" }}",
        ),
        readingForm = ReadingFormSummary(
            dualPageMode = prefs.dualPageMode,
            modeLabel = readingMode,
            textureLabel = texture,
            verticalText = prefs.verticalText,
            contentDescription = "阅读形态：$readingMode，纹理$texture，${if (prefs.verticalText) "竖排" else "横排"}",
        ),
    )
}

internal fun readerMarginPresets(strings: ReaderStrings): List<MarginPreset> = listOf(
    MarginPreset(
        label = strings.marginPresetCompact,
        bodyBox = BoxInsetsDp(32f, 32f, 16f, 16f),
        headerBox = BoxInsetsDp(8f, 0f, 16f, 16f),
        footerBox = BoxInsetsDp(0f, 8f, 16f, 16f),
        titleBox = BoxInsetsDp(6f, 6f, 16f, 16f),
    ),
    MarginPreset(
        label = strings.marginPresetStandard,
        bodyBox = BoxInsetsDp(48f, 48f, 24f, 24f),
        headerBox = BoxInsetsDp(16f, 0f, 24f, 24f),
        footerBox = BoxInsetsDp(0f, 16f, 24f, 24f),
        titleBox = BoxInsetsDp(9f, 10f, 24f, 24f),
    ),
    MarginPreset(
        label = strings.marginPresetRelaxed,
        bodyBox = BoxInsetsDp(64f, 64f, 32f, 32f),
        headerBox = BoxInsetsDp(24f, 0f, 32f, 32f),
        footerBox = BoxInsetsDp(0f, 24f, 32f, 32f),
        titleBox = BoxInsetsDp(12f, 14f, 32f, 32f),
    ),
)

internal fun detectMarginPreset(
    prefs: ReaderPreferences,
    strings: ReaderStrings,
): Pair<MarginPreset?, Boolean> {
    val presets = readerMarginPresets(strings)
    val currentPreset = presets.minByOrNull {
        prefs.bodyBox.distanceTo(it.bodyBox) +
            prefs.headerBox.distanceTo(it.headerBox) +
            prefs.footerBox.distanceTo(it.footerBox) +
            prefs.titleBox.distanceTo(it.titleBox)
    }
    val exact = currentPreset != null &&
        prefs.bodyBox == currentPreset.bodyBox &&
        prefs.headerBox == currentPreset.headerBox &&
        prefs.footerBox == currentPreset.footerBox &&
        prefs.titleBox == currentPreset.titleBox
    return currentPreset.takeIf { exact } to exact
}

private fun resolveFontName(
    key: String,
    strings: ReaderStrings,
    customFonts: List<FontManager.FontEntry>,
): String = when (key) {
    "harmony" -> strings.fontHarmonyShort
    "system" -> strings.fontSystemShort
    else -> customFonts.firstOrNull { it.key == key }?.name ?: strings.fontSystemShort
}

private fun enabledTextProcessingChips(
    prefs: ReaderPreferences,
    strings: ReaderStrings,
): List<String> = buildList {
    when (prefs.chineseConvert) {
        ChineseConvert.SIMPLIFIED -> add(strings.chineseConvertSimplified)
        ChineseConvert.TRADITIONAL -> add(strings.chineseConvertTraditional)
        ChineseConvert.NONE -> Unit
    }
    if (prefs.usePanguSpacing) add(strings.panguSpacingLabel)
    if (prefs.useZhLayout) add(strings.useZhLayoutLabel)
    if (prefs.removeEmptyLines) add(strings.removeEmptyLinesShortLabel)
    if (prefs.cleanChapterTitle) add(strings.cleanChapterTitleShortLabel)
    if (prefs.paragraphDivider) add(strings.paragraphDividerLabel)
    if (prefs.bionicReading) add(strings.bionicReadingLabel)
    if (prefs.preserveOriginalIndent) add(strings.preserveOriginalIndentLabel)
    if (prefs.epubOverrideStyle) add(strings.epubOverrideStyleShortLabel)
    if (prefs.adFiltering) add("广告过滤")
}

private fun buildPageTurnItems(
    prefs: ReaderPreferences,
    strings: ReaderStrings,
): List<StatusLine> = buildList {
    if (prefs.volumeKeyTurnPage) add(StatusLine(strings.volumeKeyTurnPageLabel, "", true))
    if (prefs.edgeTurnPage) {
        add(StatusLine(strings.edgeTurnPageLabel, "${(prefs.edgeWidthPercent * 100).roundToInt()}%", true))
    }
    if (prefs.autoPageTurn) {
        add(StatusLine(strings.autoPageTurnLabel, "${prefs.autoPageTurnInterval.roundToInt()}s", true))
    }
}

private fun buildScreenStateItems(
    prefs: ReaderPreferences,
    strings: ReaderStrings,
): List<StatusLine> = listOf(
    StatusLine(strings.immersiveModeLabel, if (prefs.immersiveMode) "✓" else "×", prefs.immersiveMode),
    StatusLine(strings.keepScreenOnShortLabel, if (prefs.keepScreenOn) "✓" else "×", prefs.keepScreenOn),
    StatusLine(
        strings.orientationLockLabel,
        prefs.orientationLock.label(strings),
        prefs.orientationLock != OrientationLock.SYSTEM,
    ),
)

private fun SlotContent.shortLabel(strings: ReaderStrings): String =
    defaultSlotDisplays(strings)[this]?.label ?: strings.slotBlankLabel

private fun ReaderFontWeight.label(strings: ReaderStrings): String = when (this) {
    ReaderFontWeight.LIGHT -> strings.fontWeightLight
    ReaderFontWeight.NORMAL -> strings.fontWeightNormal
    ReaderFontWeight.MEDIUM -> strings.fontWeightMediumFull
    ReaderFontWeight.BOLD -> strings.fontWeightBold
}

private fun TitleAlign.label(strings: ReaderStrings): String = when (this) {
    TitleAlign.LEFT -> strings.titleAlignLeft
    TitleAlign.CENTER -> strings.titleAlignCenter
    TitleAlign.RIGHT -> strings.titleAlignRight
}

internal fun PageAnimType.label(strings: ReaderStrings): String = when (this) {
    PageAnimType.NONE -> strings.pageAnimNone
    PageAnimType.COVER -> strings.pageAnimOverlay
    PageAnimType.HORIZONTAL -> strings.pageAnimTypeHorizontal
    PageAnimType.SIMULATION -> strings.pageAnimSimulation
    PageAnimType.VERTICAL_SLIDE -> strings.pageAnimTypeVerticalSlide
    PageAnimType.SCROLL -> strings.pageAnimTypeScroll
}

internal fun PageAnimSpeed.label(strings: ReaderStrings): String = when (this) {
    PageAnimSpeed.FAST -> strings.pageAnimSpeedFast.substringBefore(" ")
    PageAnimSpeed.NORMAL -> strings.pageAnimSpeedNormal.substringBefore(" ")
    PageAnimSpeed.SLOW -> strings.pageAnimSpeedSlow.substringBefore(" ")
}

private fun PageAnimSpeed.progress(): Float = when (this) {
    PageAnimSpeed.SLOW -> 0.33f
    PageAnimSpeed.NORMAL -> 0.66f
    PageAnimSpeed.FAST -> 1f
}

private fun DualPageMode.label(strings: ReaderStrings): String = when (this) {
    DualPageMode.SINGLE -> strings.singlePageLabel
    DualPageMode.DUAL -> strings.dualPageLabel
    DualPageMode.AUTO -> strings.autoLabel
}

private fun OrientationLock.label(strings: ReaderStrings): String = when (this) {
    OrientationLock.SYSTEM -> strings.brightnessFollowSystem
    OrientationLock.PORTRAIT -> strings.portraitLockLabel
    OrientationLock.LANDSCAPE -> strings.landscapeLockLabel
}

internal fun ProgressStyle.label(strings: ReaderStrings): String = when (this) {
    ProgressStyle.CHAPTER_FRACTION -> strings.progressStyleChapterFraction
    ProgressStyle.CHAPTER_PERCENT -> strings.progressStyleChapterPercent
    ProgressStyle.PAGE_NUMBER -> strings.progressStylePageNumber
    ProgressStyle.BOOK_FRACTION -> strings.progressStyleBookFraction
    ProgressStyle.BOOK_PERCENT -> strings.progressStyleBookPercent
}

private fun textureLabel(value: String?, strings: ReaderStrings): String = when (value.orEmpty()) {
    "" -> strings.solidColorLabel
    "kraft" -> "Kraft"
    "linen" -> strings.linenTextureLabel
    "grid" -> strings.gridTextureLabel
    else -> value.orEmpty()
}

private fun reminderLabel(minutes: Int, strings: ReaderStrings): String? = when (minutes) {
    0 -> null
    15 -> strings.minutes15
    30 -> strings.minutes30
    45 -> strings.minutes45
    60 -> strings.minutes60
    else -> "${minutes}分钟"
}

private fun BoxInsetsDp.distanceTo(other: BoxInsetsDp): Float =
    abs(top - other.top) + abs(bottom - other.bottom) + abs(left - other.left) + abs(right - other.right)

private fun formatOneDecimal(value: Float): String {
    val rounded = (value * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) rounded.roundToInt().toString() else rounded.toString()
}
