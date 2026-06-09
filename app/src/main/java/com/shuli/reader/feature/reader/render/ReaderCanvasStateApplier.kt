package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.reader.RenderApplierTarget

/**
 * 按 diff scopes 分发 Canvas 失效操作。
 *
 * 规则（§23.6.1）：
 * - REFLOW 先显式触发 [RenderApplierTarget.invalidateAllPages]，
 *   再展开为 [InvalidationScope.REFLOW_IMPLIED]（PAGE + CONTENT + SHELL + OVERLAY）
 * - PAGE_DELEGATE 显式触发 [RenderApplierTarget.rebuildPageDelegate]
 * - scopes 按 [InvalidationScope.order] 升序执行
 * - 空 diff 不执行任何操作
 *
 * 纯逻辑类，不持有 Canvas 引用，通过 [RenderApplierTarget] 回调。
 */
class ReaderCanvasStateApplier {
    fun apply(
        target: RenderApplierTarget,
        snapshot: ReaderRenderSnapshot,
        diff: ReaderRenderDiff,
    ) {
        if (diff.scopes.isEmpty()) return

        // REFLOW 显式触发 reflow 级别全失效
        if (InvalidationScope.REFLOW in diff.scopes) {
            target.invalidateAllPages()
            target.submitRenderTask()
        }

        val expanded = if (InvalidationScope.REFLOW in diff.scopes) {
            (diff.scopes + InvalidationScope.REFLOW_IMPLIED) - InvalidationScope.REFLOW
        } else {
            diff.scopes
        }

        expanded.sortedBy { it.order }.forEach { scope ->
            when (scope) {
                InvalidationScope.PAGE_DELEGATE -> {
                    target.rebuildPageDelegate()
                }
                InvalidationScope.PAGE -> {
                    val page = snapshot.page.currentPage ?: return@forEach
                    target.setPage(
                        page = page,
                        next = snapshot.page.nextPage,
                        prev = snapshot.page.prevPage,
                        mode = snapshot.page.pageRenderMode,
                    )
                    target.submitRenderTask()
                }
                InvalidationScope.CONTENT -> {
                    target.invalidateContentOnly()
                    target.submitRenderTask()
                }
                InvalidationScope.SHELL -> {
                    target.invalidateShellOnly()
                    target.submitRenderTask()
                }
                InvalidationScope.OVERLAY -> {
                    target.invalidateOverlayOnly()
                    target.submitRenderTask()
                }
                InvalidationScope.REFLOW -> {
                    // REFLOW 已在上方显式处理
                }
            }
        }
    }
}
