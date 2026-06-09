package com.shuli.reader.feature.reader.render

/**
 * 纯函数：比较新旧 snapshot，输出失效范围集合。
 */
internal object ReaderRenderDiffCalculator {

    fun diff(
        old: ReaderRenderSnapshot?,
        new: ReaderRenderSnapshot,
    ): ReaderRenderDiff {
        if (old == null) {
            return ReaderRenderDiff(
                setOf(
                    InvalidationScope.PAGE,
                    InvalidationScope.CONTENT,
                    InvalidationScope.SHELL,
                    InvalidationScope.OVERLAY,
                )
            )
        }

        val scopes = mutableSetOf<InvalidationScope>()

        // 空页面 diff 语义（§8.1）：先处理 null 边界情况
        val oldCurrentPage = old.page.currentPage
        val newCurrentPage = new.page.currentPage

        if (oldCurrentPage != null && newCurrentPage == null) {
            // 瞬态：不触发 invalidation
            return ReaderRenderDiff(emptySet())
        }

        if (oldCurrentPage == null && newCurrentPage != null) {
            // 首次有页面：需要 PAGE + CONTENT + SHELL
            scopes += InvalidationScope.PAGE
            scopes += InvalidationScope.CONTENT
            scopes += InvalidationScope.SHELL
        }

        // Page 变化（仅在非 null 边界情况下处理）
        if (old.page != new.page && oldCurrentPage != null && newCurrentPage != null) {
            if (old.page.chapterIndex != new.page.chapterIndex) {
                scopes += InvalidationScope.REFLOW
            } else {
                scopes += InvalidationScope.PAGE
            }
        }

        // Layout 变化 → REFLOW
        if (old.layout != new.layout) {
            scopes += InvalidationScope.REFLOW
        }

        // Visual 变化 → CONTENT（+ SHELL 如果主题变了）
        if (old.visual != new.visual) {
            scopes += InvalidationScope.CONTENT
            if (old.visual.themeColors != new.visual.themeColors) {
                scopes += InvalidationScope.SHELL
            }
        }

        // Shell 变化 → SHELL
        if (old.shell != new.shell) {
            scopes += InvalidationScope.SHELL
        }

        // Overlay 变化 → OVERLAY
        if (old.overlay != new.overlay) {
            scopes += InvalidationScope.OVERLAY
        }

        // PageAnimType 变化 → PAGE_DELEGATE
        if (old.page.pageAnimType != new.page.pageAnimType) {
            scopes += InvalidationScope.PAGE_DELEGATE
        }

        return ReaderRenderDiff(scopes)
    }
}
