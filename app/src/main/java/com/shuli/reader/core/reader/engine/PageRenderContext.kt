package com.shuli.reader.core.reader.engine

import android.graphics.Paint
import com.shuli.reader.core.reader.engine.cache.PageRenderStateStore
import com.shuli.reader.core.reader.model.TextPage

/**
 * 页面渲染上下文，封装渲染所需的全部依赖。
 *
 * 确保 ReaderPageRenderer、选区绘制高亮
 * 使用同一份 content 引用和同一套 paint/metrics，避免数据来源不一致。
 */
class PageRenderContext(
    /** 章节原始文本，所有 TextLine 的 [startCharOffset, endCharOffset) 均相对于此 */
    val content: CharSequence,
    /** 当前渲染的页面 */
    val page: TextPage,
    /** 文本画笔 */
    val textPaint: Paint,
    /** 字距（像素），分页与渲染必须一致 */
    val letterSpacingPx: Float,
    /** 可用内容宽度（减去左右 margin） */
    val availableWidth: Float,
    /** 渲染状态 store，提供行级 recorder */
    val renderStateStore: PageRenderStateStore,
)
