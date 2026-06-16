package com.shuli.reader.feature.reader.render

/**
 * 渲染失效范围。
 *
 * 每个值自带 [order]（applier 执行顺序）和 [impliedByReflow]（REFLOW 时是否自动隐含）。
 * applier 按 order 升序执行，REFLOW 展开时使用 [REFLOW_IMPLIED]。
 *
 * Phase 5: key-diff 已替代大部分 scope-based invalidation。
 * 仅 PAGE_DELEGATE、PAGE（页面身份变化）、REFLOW（分页参数变化）仍需 scope 处理。
 * CONTENT/SHELL/OVERLAY scope 由 key-diff 自动处理，后续可移除。
 */
@Deprecated(
    message = "Phase 5: key-diff 已替代大部分 scope-based invalidation。仅保留 PAGE_DELEGATE/PAGE/REFLOW。",
    level = DeprecationLevel.WARNING,
)
enum class InvalidationScope(
    val order: Int,
    val impliedByReflow: Boolean,
) {
    PAGE_DELEGATE(0, false),
    REFLOW(1, false),
    PAGE(2, true),
    CONTENT(3, true),
    SHELL(4, true),
    OVERLAY(5, true),
    VIEW_INVALIDATE(6, false),
    NONE(7, false);

    companion object {
        val REFLOW_IMPLIED: Set<InvalidationScope> = entries.filter { it.impliedByReflow }.toSet()
    }
}
