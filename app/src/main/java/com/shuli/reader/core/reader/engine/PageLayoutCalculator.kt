package com.shuli.reader.core.reader.engine

import com.shuli.reader.core.reader.model.BoxBounds
import com.shuli.reader.core.reader.model.BoxInsetsPx
import com.shuli.reader.core.reader.model.BoxSpec
import com.shuli.reader.core.reader.model.PageLayout
import com.shuli.reader.core.reader.model.PageSize

/**
 * 核心布局引擎：接收 4 个 BoxSpec，返回 PageLayout。
 *
 * 内部按 placement 类型处理：
 * 1. TOP_DOWN 盒子（header、title）：从上往下依次放置，共享 cursorY
 * 2. BOTTOM_UP 盒子（footer）：从底部向上放置
 * 3. FILL 盒子（body）：填充剩余空间
 */
object PageLayoutCalculator {

    fun calculate(
        pageSize: PageSize,
        header: BoxSpec,
        title: BoxSpec,
        body: BoxSpec,
        footer: BoxSpec,
    ): PageLayout {
        val pageWidth = pageSize.width.toFloat()
        val pageHeight = pageSize.height.toFloat()
        var cursorY = 0f

        // ── 第一轮：TOP_DOWN 盒子 ──
        val headerBounds = placeTopDown(header, pageWidth, cursorY)
        if (headerBounds != null) cursorY = headerBounds.bottom + header.insets.bottom

        val titleBounds = placeTopDown(title, pageWidth, cursorY)
        if (titleBounds != null) cursorY = titleBounds.bottom + title.insets.bottom

        // ── 第二轮：BOTTOM_UP 盒子（footer） ──
        val footerBounds = if (footer.visible && footer.placement == BoxSpec.Placement.BOTTOM_UP) {
            val contentH = footer.innerHeight.coerceAtLeast(0f)
            val bottom = pageHeight - footer.insets.bottom
            val top = (bottom - contentH).coerceAtLeast(0f)
            BoxBounds(footer.insets.left, top, pageWidth - footer.insets.right, bottom)
        } else null

        // ── 第三轮：FILL 盒子（body = 剩余空间） ──
        val bodyTop = cursorY + body.insets.top
        val bodyBottom = (footerBounds?.let { it.top - footer.insets.top } ?: pageHeight) - body.insets.bottom

        val bodyBounds = BoxBounds(
            left = body.insets.left,
            top = bodyTop,
            right = pageWidth - body.insets.right,
            bottom = bodyBottom.coerceAtLeast(bodyTop),
        )

        return PageLayout(
            header = headerBounds,
            title = titleBounds,
            body = bodyBounds,
            footer = footerBounds,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
        )
    }

    private fun placeTopDown(spec: BoxSpec, pageWidth: Float, cursorY: Float): BoxBounds? {
        if (!spec.visible || spec.placement != BoxSpec.Placement.TOP_DOWN) return null
        val top = cursorY + spec.insets.top
        val bottom = top + spec.innerHeight.coerceAtLeast(0f)
        return BoxBounds(spec.insets.left, top, pageWidth - spec.insets.right, bottom)
    }

    /**
     * 仅计算 body 宽度（px）。
     * 供 Paginator 在计算标题高度前使用。
     */
    fun bodyWidth(pageSize: PageSize, bodyInsets: BoxInsetsPx): Float {
        return pageSize.width.toFloat() - bodyInsets.left - bodyInsets.right
    }
}
