package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.database.dao.SnapshotDigestTuple
import com.shuli.reader.core.reader.model.SlotResolution
import com.shuli.reader.core.reader.model.TitleStyleConfig
import com.shuli.reader.core.reader.engine.animation.PageDelegate
import com.shuli.reader.core.reader.layout.ReaderLayoutInput
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.feature.reader.screen.ReaderUiState

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
        marginTopDp = prefs.marginTop ?: prefs.marginVertical,
        marginBottomDp = prefs.marginBottom ?: prefs.marginVertical,
        marginLeftDp = prefs.marginLeft ?: prefs.marginHorizontal,
        marginRightDp = prefs.marginRight ?: prefs.marginHorizontal,
        indent = prefs.indent,
        indentUnit = prefs.indentUnit,
        titleStyle = prefs.titleStyle,
        headerVisibleForLayout = prefs.header.visibility != com.shuli.reader.core.reader.model.HeaderVisibility.ALWAYS_HIDE,
        footerVisibleForLayout = prefs.footer.visibility != com.shuli.reader.core.reader.model.HeaderVisibility.ALWAYS_HIDE,
        headerMarginTopDp = prefs.header.marginTop,
        footerMarginBottomDp = prefs.footer.marginBottom,
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
        gestureConfig = prefs.gestureConfig,
    )

    // A1: 章内下一页为空时（当前章末页）用预加载的下一章首页兜底
    // 必须双重判定：① 章内 getPage(pageIndex+1) 为 null ② currentPage 确实在 currentChapter.lastIndex
    // 否则在 chapterIndex 已切换但 currentPage 仍指向旧章末页的瞬间，会把 N+1[0] 错误塞进 prevPage，
    // 导致翻到下一章首页时动画闪回上一章首页。
    val nextInChapter = currentChapter?.getPage(pageIndex + 1)
    val isAtChapterEnd = currentPage != null &&
        currentChapter != null &&
        currentPage.chapterIndex == currentChapter.chapterIndex &&
        currentPage.pageIndex >= currentChapter.lastIndex
    val adjacentNextPage = nextChapterFirstPage?.takeIf { it.chapterIndex == chapterIndex + 1 }
    val effectiveNextPage = if (nextInChapter == null && isAtChapterEnd) {
        adjacentNextPage
    } else {
        nextInChapter
    }

    // A1: 章内上一页为空时（当前章首页）用预加载的上一章末页兜底，同样双重判定
    val prevInChapter = currentChapter?.getPage(pageIndex - 1)
    val isAtChapterStart = currentPage != null &&
        currentChapter != null &&
        currentPage.chapterIndex == currentChapter.chapterIndex &&
        currentPage.pageIndex == 0
    val adjacentPrevPage = prevChapterLastPage?.takeIf { it.chapterIndex == chapterIndex - 1 }
    val effectivePrevPage = if (prevInChapter == null && isAtChapterStart) {
        adjacentPrevPage
    } else {
        prevInChapter
    }

    if (com.shuli.reader.BuildConfig.DEBUG) {
        android.util.Log.d(
            "RenderInputMapper",
            "ch=$chapterIndex pi=$pageIndex " +
                "curPage[ch=${currentPage?.chapterIndex},pi=${currentPage?.pageIndex}] " +
                "curChapter[ch=${currentChapter?.chapterIndex},last=${currentChapter?.lastIndex}] " +
                "nextInChapter=${nextInChapter?.let { "[ch=${it.chapterIndex},pi=${it.pageIndex}]" }} " +
                "isAtEnd=$isAtChapterEnd effectiveNext=${effectiveNextPage?.let { "[ch=${it.chapterIndex},pi=${it.pageIndex}]" }} " +
                "prevInChapter=${prevInChapter?.let { "[ch=${it.chapterIndex},pi=${it.pageIndex}]" }} " +
                "isAtStart=$isAtChapterStart effectivePrev=${effectivePrevPage?.let { "[ch=${it.chapterIndex},pi=${it.pageIndex}]" }}",
        )
    }

    val pageSnapshot = PageSnapshot(
        bookId = bookId,
        chapterIndex = chapterIndex,
        pageIndex = pageIndex,
        anchorByteOffset = anchorByteOffset,
        currentPage = currentPage,
        nextPage = effectiveNextPage,
        prevPage = effectivePrevPage,
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

    val chapterContents = mutableMapOf<Int, CharSequence>()
    currentChapter?.let { chapterContents[it.chapterIndex] = it.content }
    if (effectiveNextPage != null && nextChapterContent != null) {
        chapterContents[effectiveNextPage.chapterIndex] = nextChapterContent
    }
    if (effectivePrevPage != null && prevChapterContent != null) {
        chapterContents[effectivePrevPage.chapterIndex] = prevChapterContent
    }

    return ReaderRenderInput(
        page = pageSnapshot,
        settings = settingsSnapshot,
        overlay = overlaySnapshot,
        pageDelegate = pageDelegate,
        chapterContent = currentChapter?.content ?: "",
        chapterContents = chapterContents,
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
        marginTopDp = 16f,
        marginBottomDp = 16f,
        marginLeftDp = 16f,
        marginRightDp = 16f,
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
        gestureConfig = com.shuli.reader.feature.reader.settings.GestureConfig(),
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
        pageAnimType = com.shuli.reader.core.reader.engine.animation.PageDelegateFactory.PageAnimType.HORIZONTAL,
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
