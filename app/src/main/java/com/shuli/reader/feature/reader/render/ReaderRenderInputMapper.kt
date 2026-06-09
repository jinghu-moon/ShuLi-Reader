package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.database.dao.SnapshotDigestTuple
import com.shuli.reader.core.reader.SlotResolution
import com.shuli.reader.core.reader.TitleStyleConfig
import com.shuli.reader.core.reader.animation.PageDelegate
import com.shuli.reader.core.reader.layout.ReaderLayoutInput
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.feature.reader.ReaderUiState

/**
 * 将 [ReaderUiState] 映射为 [ReaderRenderInput]。
 *
 * 由 ReaderScreen 在 AndroidView.update 中调用，
 * 替代旧的 applyInitialReaderCanvasState + ReaderCanvasEffects 分散设置。
 */
fun ReaderUiState.toRenderInput(
    density: Float,
    headerSlots: SlotResolution,
    footerSlots: SlotResolution,
    batteryLevel: Int,
    pageDelegate: PageDelegate?,
): ReaderRenderInput {
    val prefs = readerPreferences
    val anchorByteOffset = currentPage?.startCharOffset?.toLong() ?: 0L
    val layoutInput = ReaderLayoutInput(
        layoutVersion = layoutVersion,
        bookId = bookId,
        chapterIndex = chapterIndex,
        anchorByteOffset = anchorByteOffset,
        viewportWidth = 0,
        viewportHeight = 0,
        density = density,
        fontSizeSp = prefs.fontSize,
        fontKey = prefs.readingFont,
        fontWeight = prefs.fontWeight,
        lineSpacing = prefs.lineSpacing,
        paragraphSpacing = prefs.paragraphSpacing,
        letterSpacing = prefs.letterSpacing,
        marginHorizontalDp = prefs.marginHorizontal,
        marginVerticalDp = prefs.marginVertical,
        indent = prefs.indent,
        indentUnit = prefs.indentUnit,
        titleStyle = prefs.titleStyle,
        headerVisibleForLayout = prefs.header.visibility != com.shuli.reader.core.reader.HeaderVisibility.ALWAYS_HIDE,
        footerVisibleForLayout = prefs.footer.visibility != com.shuli.reader.core.reader.HeaderVisibility.ALWAYS_HIDE,
        chineseConvert = prefs.chineseConvert,
        usePanguSpacing = prefs.usePanguSpacing,
        useZhLayout = prefs.useZhLayout,
        bottomJustify = prefs.bottomJustify,
    )

    val settingsSnapshot = ReaderSettingsSnapshot(
        layoutInput = layoutInput,
        themeColors = themeColors,
        textAlign = prefs.textAlign,
        titleStyle = prefs.titleStyle,
        headerSlots = headerSlots,
        footerSlots = footerSlots,
        batteryLevel = batteryLevel,
        showProgress = prefs.showProgress,
        headerFooterAlpha = prefs.headerFooterAlpha,
        showHeaderLine = prefs.showHeaderLine,
        showFooterLine = prefs.showFooterLine,
        headerFontSizeRatio = prefs.headerFontSizeRatio,
        footerFontSizeRatio = prefs.footerFontSizeRatio,
        edgeTurnPage = prefs.edgeTurnPage,
        edgeWidthPercent = prefs.edgeWidthPercent,
        leftZoneRatio = prefs.leftZoneRatio,
        fontSizePx = prefs.fontSize * density,
        letterSpacing = prefs.letterSpacing,
        fontWeight = prefs.fontWeight,
        fontKey = prefs.readingFont,
    )

    val pageSnapshot = PageSnapshot(
        bookId = bookId,
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        anchorByteOffset = anchorByteOffset,
        currentPage = currentPage,
        nextPage = currentChapter?.getPage(pageIndex + 1),
        prevPage = currentChapter?.getPage(pageIndex - 1),
        contentVersion = chapterIndex,
        pageRenderMode = pageRenderMode,
        pageAnimType = pageAnimType,
        canTurnPrev = pageIndex > 0 || chapterIndex > 0,
        canTurnNext = currentChapter?.let {
            pageIndex < it.lastIndex || chapterIndex < totalChapters - 1
        } ?: false,
    )

    val overlaySnapshot = OverlaySnapshot(
        selectedRange = selectedRange,
        noteRanges = emptyList(),
        overlayKey = OverlayKey(selectedRange, 0),
    )

    return ReaderRenderInput(
        page = pageSnapshot,
        settings = settingsSnapshot,
        overlay = overlaySnapshot,
        pageDelegate = pageDelegate,
    )
}

/**
 * §11.1.1.1: 将持久化的 [SnapshotDigestTuple] 转为 T0 fallback 用的 [ReaderRenderInput]。
 *
 * 只填充骨架页渲染所需的最少字段（主题色 + 章节/页码），
 * 其余使用默认值，确保不会因设置不完整而崩溃。
 */
fun SnapshotDigestTuple.toFallbackRenderInput(
    density: Float,
    layoutVersion: Int,
): ReaderRenderInput {
    val themeColors = ThemeColors(
        backgroundColor = themeBackgroundColor,
        textColor = 0xFF000000.toInt(),
        headerColor = 0xFF000000.toInt(),
        footerColor = 0xFF000000.toInt(),
        progressColor = 0xFF000000.toInt(),
        accentColor = 0xFF000000.toInt(),
    )

    val layoutInput = ReaderLayoutInput(
        layoutVersion = layoutVersion,
        bookId = bookId,
        chapterIndex = chapterIndex,
        anchorByteOffset = 0L,
        viewportWidth = 0,
        viewportHeight = 0,
        density = density,
        fontSizeSp = 16f,
        fontKey = "",
        fontWeight = ReaderFontWeight.NORMAL,
        lineSpacing = 1.5f,
        paragraphSpacing = 0f,
        letterSpacing = 0f,
        marginHorizontalDp = 16f,
        marginVerticalDp = 16f,
        indent = 0f,
        indentUnit = com.shuli.reader.core.data.IndentUnit.CHARACTER,
        titleStyle = TitleStyleConfig(),
        headerVisibleForLayout = true,
        footerVisibleForLayout = true,
        chineseConvert = com.shuli.reader.core.data.ChineseConvert.NONE,
        usePanguSpacing = false,
        useZhLayout = false,
        bottomJustify = false,
    )

    val settingsSnapshot = ReaderSettingsSnapshot(
        layoutInput = layoutInput,
        themeColors = themeColors,
        textAlign = ReaderTextAlign.LEFT,
        titleStyle = TitleStyleConfig(),
        headerSlots = SlotResolution(),
        footerSlots = SlotResolution(),
        batteryLevel = 100,
        showProgress = true,
        headerFooterAlpha = 1f,
        showHeaderLine = false,
        showFooterLine = false,
        headerFontSizeRatio = 0.8f,
        footerFontSizeRatio = 0.8f,
        edgeTurnPage = true,
        edgeWidthPercent = 0.33f,
        leftZoneRatio = 0.33f,
        fontSizePx = 16f * density,
        letterSpacing = 0f,
        fontWeight = ReaderFontWeight.NORMAL,
        fontKey = "",
    )

    val pageSnapshot = PageSnapshot(
        bookId = bookId,
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        anchorByteOffset = 0L,
        currentPage = null,
        nextPage = null,
        prevPage = null,
        contentVersion = chapterIndex,
        pageRenderMode = PageRenderMode.SEQUENTIAL,
        pageAnimType = com.shuli.reader.core.reader.animation.PageDelegateFactory.PageAnimType.HORIZONTAL,
        canTurnPrev = false,
        canTurnNext = false,
    )

    val overlaySnapshot = OverlaySnapshot(
        selectedRange = null,
        noteRanges = emptyList(),
        overlayKey = com.shuli.reader.feature.reader.render.OverlayKey(null, 0),
    )

    return ReaderRenderInput(
        page = pageSnapshot,
        settings = settingsSnapshot,
        overlay = overlaySnapshot,
        pageDelegate = null,
    )
}
