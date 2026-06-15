package com.shuli.reader.feature.reader

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
import com.shuli.reader.core.data.toChineseConvert
import com.shuli.reader.core.data.toFontWeight
import com.shuli.reader.core.data.toHeaderVisibility
import com.shuli.reader.core.data.toIndentUnit
import com.shuli.reader.core.data.toPageAnimType
import com.shuli.reader.core.data.toProgressStyle
import com.shuli.reader.core.data.toSlotContent
import com.shuli.reader.core.data.toTextAlign
import com.shuli.reader.core.data.toTitleAlign
import com.shuli.reader.core.database.entity.BookReaderPrefsOverrides
import com.shuli.reader.core.reader.FooterConfig
import com.shuli.reader.core.reader.HeaderConfig
import com.shuli.reader.core.reader.TitleStyleConfig

/**
 * 设置作用域。
 *
 * - GLOBAL: 修改全局默认（DataStore），对所有书生效
 * - BOOK:   修改本书覆盖（Room），仅对当前书生效
 */
enum class SettingsScope { GLOBAL, BOOK }

/**
 * 将 [BookReaderPrefsOverrides] 合并到全局 [ReaderPreferences] 上。
 *
 * 规则：override 字段非 null → 使用 override；否则保留全局值。
 */
fun BookReaderPrefsOverrides.mergeOnto(global: ReaderPreferences): ReaderPreferences {
    return ReaderPreferences(
        fontSize = this.fontSize ?: global.fontSize,
        lineSpacing = this.lineSpacing ?: global.lineSpacing,
        paragraphSpacing = this.paragraphSpacing ?: global.paragraphSpacing,
        indent = this.indent ?: global.indent,
        preserveOriginalIndent = this.preserveOriginalIndent ?: global.preserveOriginalIndent,
        indentUnit = this.indentUnit?.toIndentUnit() ?: global.indentUnit,
        pageAnimType = this.pageAnimType?.toPageAnimType() ?: global.pageAnimType,
        pageAnimSpeed = this.pageAnimSpeed?.let {
            try { PageAnimSpeed.valueOf(it) } catch (_: Exception) { null }
        } ?: global.pageAnimSpeed,
        backgroundColor = this.backgroundColor?.let {
            try { ReaderTheme.valueOf(it) } catch (_: Exception) { null }
        } ?: global.backgroundColor,
        customBackgroundColor = this.customBackgroundColor ?: global.customBackgroundColor,
        customTextColor = this.customTextColor ?: global.customTextColor,
        customTitleColor = this.customTitleColor ?: global.customTitleColor,
        customHeaderFooterColor = this.customHeaderFooterColor ?: global.customHeaderFooterColor,
        marginHorizontal = this.marginHorizontal ?: global.marginHorizontal,
        marginVertical = this.marginVertical ?: global.marginVertical,
        marginTop = this.marginTop ?: global.marginTop,
        marginBottom = this.marginBottom ?: global.marginBottom,
        marginLeft = this.marginLeft ?: global.marginLeft,
        marginRight = this.marginRight ?: global.marginRight,
        brightness = this.brightness ?: global.brightness,
        colorTemperature = this.colorTemperature ?: global.colorTemperature,
        backgroundTexture = this.backgroundTexture ?: global.backgroundTexture,
        readingFont = this.readingFont ?: global.readingFont,
        optimizeRender = global.optimizeRender,
        letterSpacing = this.letterSpacing ?: global.letterSpacing,
        paragraphDivider = this.paragraphDivider ?: global.paragraphDivider,
        fontWeight = this.fontWeight?.toFontWeight() ?: global.fontWeight,
        textAlign = this.textAlign?.toTextAlign() ?: global.textAlign,
        chineseConvert = this.chineseConvert?.toChineseConvert() ?: global.chineseConvert,
        bionicReading = this.bionicReading ?: global.bionicReading,
        verticalText = this.verticalText ?: global.verticalText,
        dualPageMode = this.dualPageMode?.let {
            try { DualPageMode.valueOf(it) } catch (_: Exception) { null }
        } ?: global.dualPageMode,
        adFiltering = this.adFiltering ?: global.adFiltering,
        titleStyle = TitleStyleConfig(
            align = this.titleAlign?.toTitleAlign() ?: global.titleStyle.align,
            sizeOffsetSp = this.titleSizeOffset ?: global.titleStyle.sizeOffsetSp,
            marginTopDp = this.titleMarginTop ?: global.titleStyle.marginTopDp,
            marginBottomDp = this.titleMarginBottom ?: global.titleStyle.marginBottomDp,
        ),
        header = HeaderConfig(
            visibility = this.headerVisibility?.toHeaderVisibility() ?: global.header.visibility,
            left = this.headerLeft?.toSlotContent() ?: global.header.left,
            center = this.headerCenter?.toSlotContent() ?: global.header.center,
            right = this.headerRight?.toSlotContent() ?: global.header.right,
            marginTop = this.headerMarginTop ?: global.header.marginTop,
        ),
        footer = FooterConfig(
            visibility = this.footerVisibility?.toHeaderVisibility() ?: global.footer.visibility,
            left = this.footerLeft?.toSlotContent() ?: global.footer.left,
            center = this.footerCenter?.toSlotContent() ?: global.footer.center,
            right = this.footerRight?.toSlotContent() ?: global.footer.right,
            marginBottom = this.footerMarginBottom ?: global.footer.marginBottom,
        ),
        headerFooterAlpha = this.headerFooterAlpha ?: global.headerFooterAlpha,
        showProgress = this.showProgress ?: global.showProgress,
        useZhLayout = this.useZhLayout ?: global.useZhLayout,
        usePanguSpacing = this.usePanguSpacing ?: global.usePanguSpacing,
        showHeaderLine = this.showHeaderLine ?: global.showHeaderLine,
        showFooterLine = this.showFooterLine ?: global.showFooterLine,
        headerFontSizeRatio = this.headerFontSizeRatio ?: global.headerFontSizeRatio,
        footerFontSizeRatio = this.footerFontSizeRatio ?: global.footerFontSizeRatio,
        bottomJustify = this.bottomJustify ?: global.bottomJustify,
        keepScreenOn = this.keepScreenOn ?: global.keepScreenOn,
        volumeKeyTurnPage = this.volumeKeyTurnPage ?: global.volumeKeyTurnPage,
        edgeTurnPage = this.edgeTurnPage ?: global.edgeTurnPage,
        edgeWidthPercent = this.edgeWidthPercent ?: global.edgeWidthPercent,
        immersiveMode = this.immersiveMode ?: global.immersiveMode,
        hapticFeedback = this.hapticFeedback ?: global.hapticFeedback,
        orientationLock = this.orientationLock?.let {
            try { OrientationLock.valueOf(it) } catch (_: Exception) { null }
        } ?: global.orientationLock,
        ttsSpeed = this.ttsSpeed ?: global.ttsSpeed,
        ttsPitch = this.ttsPitch ?: global.ttsPitch,
        ttsVoice = this.ttsVoice ?: global.ttsVoice,
        ttsAutoPage = this.ttsAutoPage ?: global.ttsAutoPage,
        ttsTimer = this.ttsTimer ?: global.ttsTimer,
        maxPageWidth = this.maxPageWidth ?: global.maxPageWidth,
        removeEmptyLines = this.removeEmptyLines ?: global.removeEmptyLines,
        cleanChapterTitle = this.cleanChapterTitle ?: global.cleanChapterTitle,
        progressStyle = this.progressStyle?.toProgressStyle() ?: global.progressStyle,
        autoNightMode = this.autoNightMode ?: global.autoNightMode,
        autoPageTurn = this.autoPageTurn ?: global.autoPageTurn,
        autoPageTurnInterval = this.autoPageTurnInterval ?: global.autoPageTurnInterval,
        epubOverrideStyle = this.epubOverrideStyle ?: global.epubOverrideStyle,
        leftZoneRatio = this.leftZoneRatio ?: global.leftZoneRatio,
        focusLine = this.focusLine ?: global.focusLine,
        eyeCareReminderInterval = this.eyeCareReminderInterval ?: global.eyeCareReminderInterval,
        gestureConfig = this.gestureConfig ?: global.gestureConfig,
    )
}
