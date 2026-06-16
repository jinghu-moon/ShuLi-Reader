package com.shuli.reader.core.reader.engine

import com.shuli.reader.core.reader.engine.animation.PageDelegate
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.TextPage

/**
 * Canvas 渲染应用接口。
 *
 * 将 [com.shuli.reader.feature.reader.render.ReaderCanvasStateApplier] 与
 * [ReaderCanvasView] 解耦：Applier 在 feature.reader.render 中持有 Snapshot/Diff 类型，
 * 通过本接口回调 Canvas 操作，避免 core.reader 反向依赖 feature 包。
 *
 * 所有 invalidation 方法只标记 recorder 失效，不触发 submitRenderTask。
 * Applier 在每个 scope 处理后统一调用 [submitRenderTask]。
 *
 * 测试使用 FakeReaderCanvasView 实现本接口，验证 scope 分发行为。
 */
interface RenderApplierTarget {
    fun setPage(
        page: TextPage,
        next: TextPage?,
        prev: TextPage?,
        mode: PageRenderMode,
    )

    @Deprecated("Phase 5: 由 key-diff 驱动，不再通过 scope 失效", level = DeprecationLevel.WARNING)
    fun invalidateContentOnly()
    @Deprecated("Phase 5: 由 key-diff 驱动，不再通过 scope 失效", level = DeprecationLevel.WARNING)
    fun invalidateShellOnly()
    @Deprecated("Phase 5: 由 key-diff 驱动，不再通过 scope 失效", level = DeprecationLevel.WARNING)
    fun invalidateOverlayOnly()

    /**
     * 触发 reflow 级别的失效：所有页面的 content/shell/overlay recorder 全部失效。
     * 与 [invalidateContentOnly]/[invalidateShellOnly]/[invalidateOverlayOnly] 不同，
     * 此方法同时失效三者，语义上等同于 reflow 事件。
     */
    fun invalidateAllPages()

    /**
     * PAGE_DELEGATE scope：标记翻页动画委托需要重建。
     * 真实 Canvas 中 pageDelegate 由 applySnapshot 在进入 Applier 之前设置，
     * 此处仅作语义标记，用于测试验证 scope 分发。
     */
    fun rebuildPageDelegate()

    fun submitRenderTask()

    /**
     * Orchestrator 唯一调用入口：应用完整快照并按 diff 范围精确失效。
     *
     * 参数类型使用 Any 以避免 core.reader 反向依赖 feature.reader.render 包。
     * 真实实现（ReaderCanvasView）将参数转型为
     * [com.shuli.reader.feature.reader.render.ReaderRenderSnapshot] /
     * [com.shuli.reader.feature.reader.render.ReaderRenderDiff]。
     */
    fun applySnapshot(
        snapshot: Any,
        diff: Any,
        pageDelegate: PageDelegate? = null,
        chapterContent: CharSequence = "",
        chapterContents: Map<Int, CharSequence> = emptyMap(),
    )
}
