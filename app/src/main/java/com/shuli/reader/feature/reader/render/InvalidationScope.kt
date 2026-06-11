package com.shuli.reader.feature.reader.render

/**
 * 渲染失效范围。
 *
 * 每个值自带 [order]（applier 执行顺序）和 [impliedByReflow]（REFLOW 时是否自动隐含）。
 * applier 按 order 升序执行，REFLOW 展开时使用 [REFLOW_IMPLIED]。
 */
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
