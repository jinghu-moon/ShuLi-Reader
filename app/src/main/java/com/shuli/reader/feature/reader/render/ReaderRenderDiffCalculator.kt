package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.getValueByKey
import com.shuli.reader.feature.reader.settings.ReaderSettingRegistry

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
                    // Phase 5: CONTENT/SHELL/OVERLAY 由 key-diff 驱动，不再通过 scope 失效
                )
            )
        }

        val scopes = mutableSetOf<InvalidationScope>()

        val oldCurrentPage = old.page.currentPage
        val newCurrentPage = new.page.currentPage

        if (oldCurrentPage != null && newCurrentPage == null) {
            return ReaderRenderDiff(emptySet())
        }

        if (oldCurrentPage == null && newCurrentPage != null) {
            scopes += InvalidationScope.PAGE
            // Phase 5: CONTENT/SHELL 由 key-diff 驱动
        }

        if (old.page != new.page && oldCurrentPage != null && newCurrentPage != null) {
            if (old.page.chapterIndex != new.page.chapterIndex) {
                scopes += InvalidationScope.REFLOW
            } else {
                scopes += InvalidationScope.PAGE
            }
        }

        if (old.layout != new.layout) {
            scopes += InvalidationScope.REFLOW
        }

        // Phase 5: visual/shell/overlay 变化由 key-diff 驱动，不再通过 scope 失效

        if (old.page.pageAnimType != new.page.pageAnimType) {
            scopes += InvalidationScope.PAGE_DELEGATE
        }

        return ReaderRenderDiff(scopes)
    }

    /**
     * Registry 驱动的精准字段比对。
     *
     * 遍历 [ReaderSettingRegistry.all]，逐字段检查 old/new [ReaderPreferences] 的值变化，
     * 收集对应的 [InvalidationScope]。与 snapshot 级别比较互补：
     * snapshot 比较粒度粗但高效，此方法粒度精确到单个 Registry 字段。
     *
     * scope 为 null 的字段变更不产生 recorder 失效，自然排除。
     */
    fun registryBasedScopes(
        old: ReaderPreferences,
        new: ReaderPreferences,
    ): Set<InvalidationScope> {
        val scopes = mutableSetOf<InvalidationScope>()
        for (def in ReaderSettingRegistry.all) {
            val scope = def.scope ?: continue
            val oldVal = old.getValueByKey<Any>(def.key)
            val newVal = new.getValueByKey<Any>(def.key)
            if (oldVal != newVal) {
                scopes += scope
            }
        }
        return scopes
    }
}
