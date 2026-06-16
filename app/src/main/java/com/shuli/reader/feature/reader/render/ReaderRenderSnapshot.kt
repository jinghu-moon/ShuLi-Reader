package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.reader.model.SlotResolution
import com.shuli.reader.core.reader.model.TitleStyleConfig
import com.shuli.reader.core.reader.engine.animation.PageDelegateFactory
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
// 注：章节正文不进 snapshot。50KB+ 文本参与 data class equals 会引入 O(n) 比较，
// 且 diff 已由 PageSnapshot.contentVersion（O(1)）表达。正文经 applySnapshot 独立参数传入。
// 见 docs/26-reader-first-frame-stability.md §7。

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
    val gestureConfig: com.shuli.reader.feature.reader.settings.GestureConfig =
        com.shuli.reader.feature.reader.settings.GestureConfig(),
    val colorTemperature: Float = 6500f,
    val shellKey: ShellRenderKey,
)

data class OverlaySnapshot(
    val selectedRange: SelectionRange?,
    val noteRanges: List<Pair<SelectionRange, String?>>,
    val overlayKey: OverlayKey,
)
