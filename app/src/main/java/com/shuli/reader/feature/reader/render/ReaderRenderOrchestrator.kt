package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.reader.engine.RenderApplierTarget

/**
 * 唯一 Snapshot Owner。Canvas 只做 renderer，不做 store。
 *
 * 同步场景用 [apply]，异步场景用 [reserveGeneration] + [applyAsync]。
 * T0 超预算时用 [applyWithFallback] 渲染骨架页。
 *
 * **关键约束：不持有 canvas 引用。** 每次 apply 由调用方传入当前存活的 view。
 */
class ReaderRenderOrchestrator(
    private val snapshotFactory: ReaderRenderSnapshotFactory = ReaderRenderSnapshotFactory(),
) {
    var currentSnapshot: ReaderRenderSnapshot? = null
        private set

    var generation: Long = 0L
        private set

    /** 同步场景：自动递增 generation 并立即 apply */
    fun apply(target: RenderApplierTarget, input: ReaderRenderInput) {
        val gen = ++generation
        val nextSnapshot = snapshotFactory.build(input, generation = gen)
        val diff = ReaderRenderDiffCalculator.diff(currentSnapshot, nextSnapshot)
        target.applySnapshot(nextSnapshot, diff, input.pageDelegate, input.chapterContent, input.chapterContents)
        currentSnapshot = nextSnapshot
    }

    /** 异步场景：调用方先 reserve generation，后台任务完成后用同一 gen 提交 */
    fun reserveGeneration(): Long = ++generation

    fun applyAsync(
        target: RenderApplierTarget,
        input: ReaderRenderInput,
        generation: Long,
    ) {
        if (generation != this.generation) return
        val nextSnapshot = snapshotFactory.build(input, generation = generation)
        val diff = ReaderRenderDiffCalculator.diff(currentSnapshot, nextSnapshot)
        target.applySnapshot(nextSnapshot, diff, input.pageDelegate, input.chapterContent, input.chapterContents)
        currentSnapshot = nextSnapshot
    }

    /**
     * T0 fallback：超预算时渲染骨架页，后台继续构建真实 snapshot。
     */
    fun applyWithFallback(
        target: RenderApplierTarget,
        input: ReaderRenderInput,
        fallback: ReaderRenderInput?,
        budgetMs: Long = 100,
    ) {
        val gen = ++generation
        val startTime = System.nanoTime() / 1_000_000

        val nextSnapshot = snapshotFactory.build(input, generation = gen)
        val elapsed = System.nanoTime() / 1_000_000 - startTime

        if (elapsed > budgetMs && fallback != null) {
            val fallbackSnapshot = snapshotFactory.build(fallback, generation = gen)
            val diff = ReaderRenderDiffCalculator.diff(currentSnapshot, fallbackSnapshot)
            target.applySnapshot(fallbackSnapshot, diff, fallback.pageDelegate, fallback.chapterContent, fallback.chapterContents)
            currentSnapshot = fallbackSnapshot
            return
        }

        val diff = ReaderRenderDiffCalculator.diff(currentSnapshot, nextSnapshot)
        target.applySnapshot(nextSnapshot, diff, input.pageDelegate, input.chapterContent, input.chapterContents)
        currentSnapshot = nextSnapshot
    }

    fun isCurrent(value: Long): Boolean = value == generation
}
