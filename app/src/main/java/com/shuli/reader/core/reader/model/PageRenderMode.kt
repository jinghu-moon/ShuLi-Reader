package com.shuli.reader.core.reader.model

/**
 * 页面渲染模式
 */
enum class PageRenderMode {
    /** 顺序阅读：渲染 prev/cur/next */
    SEQUENTIAL,
    /** 跳转：仅 cur，禁用动画 */
    JUMP,
    /** 拖动进度条：仅 cur，节流，禁用动画 */
    SCRUBBING,
}
