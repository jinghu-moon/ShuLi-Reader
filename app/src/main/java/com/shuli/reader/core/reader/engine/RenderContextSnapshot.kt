package com.shuli.reader.core.reader.engine

import com.shuli.reader.core.reader.model.SelectionRange
import com.shuli.reader.core.reader.model.SlotResolution

/**
 * 渲染上下文的不可变快照。
 *
 * Phase 3: 在 submitRenderTask 入口处（主线程）从 RenderContext 创建，
 * 传递给后台线程使用。后台线程只读取此快照，不接触主线程可变的 RenderContext。
 *
 * noteRanges 使用 color int 替代 Paint，避免跨线程共享 Paint 实例。
 */
data class RenderContextSnapshot(
    val headerSlots: SlotResolution,
    val footerSlots: SlotResolution,
    val showProgress: Boolean,
    val headerAlpha: Float,
    val footerAlpha: Float,
    val batteryLevel: Int,
    val selectedRange: SelectionRange?,
    val noteRanges: List<Pair<SelectionRange, Int>>,
    val showHeaderLine: Boolean,
    val showFooterLine: Boolean,
    val generation: Long,
)

fun RenderContext.freeze(): RenderContextSnapshot = RenderContextSnapshot(
    headerSlots = headerSlots,
    footerSlots = footerSlots,
    showProgress = showProgress,
    headerAlpha = headerAlpha,
    footerAlpha = footerAlpha,
    batteryLevel = batteryLevel,
    selectedRange = selectedRange,
    noteRanges = noteRanges.map { (range, paint) -> range to paint.color },
    showHeaderLine = showHeaderLine,
    showFooterLine = showFooterLine,
    generation = generation,
)
