package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.reader.SlotResolution
import com.shuli.reader.core.reader.TitleStyleConfig
import com.shuli.reader.core.reader.animation.PageDelegateFactory
import com.shuli.reader.core.reader.layout.ReaderLayoutInput
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.TextPage

data class ReaderRenderSnapshot(
    val generation: Long,
    val page: PageSnapshot,
    val layout: LayoutSnapshot,
    val visual: VisualSnapshot,
    val shell: ShellSnapshot,
    val overlay: OverlaySnapshot,
)

data class PageSnapshot(
    val bookId: Long,
    val chapterIndex: Int,
    val pageIndex: Int,
    val anchorByteOffset: Long,
    val currentPage: TextPage?,
    val nextPage: TextPage?,
    val prevPage: TextPage?,
    val contentVersion: Int,
    val pageRenderMode: PageRenderMode,
    val pageAnimType: PageDelegateFactory.PageAnimType,
    val canTurnPrev: Boolean,
    val canTurnNext: Boolean,
)

data class LayoutSnapshot(
    val input: ReaderLayoutInput,
    val layoutKey: LayoutKey,
)

data class VisualSnapshot(
    val themeColors: ThemeColors,
    val textAlign: ReaderTextAlign,
    val titleStyle: TitleStyleConfig,
    val contentKey: RenderKey,
)

data class ShellSnapshot(
    val headerSlots: SlotResolution,
    val footerSlots: SlotResolution,
    val batteryLevel: Int,
    val showProgress: Boolean,
    val headerFooterAlpha: Float,
    val showHeaderLine: Boolean = false,
    val showFooterLine: Boolean = false,
    val headerFontSizeRatio: Float = 1f,
    val footerFontSizeRatio: Float = 1f,
    val edgeTurnPage: Boolean = true,
    val edgeWidthPercent: Float = 0.33f,
    val leftZoneRatio: Float = 0.33f,
    val shellKey: RenderKey,
)

data class OverlaySnapshot(
    val selectedRange: SelectionRange?,
    val noteRanges: List<Pair<SelectionRange, String?>>,
    val overlayKey: OverlayKey,
)
