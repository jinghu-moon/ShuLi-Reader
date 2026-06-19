package com.shuli.reader.feature.reader.render

/**
 * 渲染失效范围。
 *
 * 每个值自带 [order]（applier 执行顺序）和 [impliedByReflow]（REFLOW 时是否自动隐含）。
 * applier 按 order 升序执行，REFLOW 展开时使用 [REFLOW_IMPLIED]。
 *
 * CONTENT/SHELL/OVERLAY 已由 key-diff 驱动，不再通过 scope 失效。
 */
enum class InvalidationScope(
    val order: Int,
    val impliedByReflow: Boolean,
) {
    PAGE_DELEGATE(0, false),
    REFLOW(1, false),
    PAGE(2, true);

    companion object {
        val REFLOW_IMPLIED: Set<InvalidationScope> = entries.filter { it.impliedByReflow }.toSet()
    }
}
