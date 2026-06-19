package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.reader.engine.RenderApplierTarget
import com.shuli.reader.core.reader.model.SlotResolution
import com.shuli.reader.core.reader.model.TitleStyleConfig
import com.shuli.reader.core.reader.engine.animation.PageDelegate
import com.shuli.reader.core.reader.layout.createDefaultLayoutInput
import com.shuli.reader.core.reader.model.PageRenderMode
import com.shuli.reader.core.reader.model.TextPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReaderRenderOrchestratorTest {

    private lateinit var orchestrator: ReaderRenderOrchestrator
    private lateinit var fakeTarget: FakeOrchestratorTarget

    @Before
    fun setup() {
        fakeTarget = FakeOrchestratorTarget()
        orchestrator = ReaderRenderOrchestrator(
            snapshotFactory = ReaderRenderSnapshotFactory(),
        )
    }

    // ── apply() 同步场景 ──

    @Test
    fun apply_firstCall_appliesSnapshot() {
        val input = createDefaultRenderInput()
        orchestrator.apply(fakeTarget, input)
        assertEquals(1, fakeTarget.applyCount)
    }

    @Test
    fun apply_incrementsGeneration() {
        orchestrator.apply(fakeTarget, createDefaultRenderInput())
        orchestrator.apply(fakeTarget, createDefaultRenderInput())
        assertTrue(orchestrator.isCurrent(2))
    }

    @Test
    fun apply_identicalInput_stillApplies() {
        val input = createDefaultRenderInput()
        orchestrator.apply(fakeTarget, input)
        orchestrator.apply(fakeTarget, input)
        assertEquals(2, fakeTarget.applyCount)
    }

    @Test
    fun apply_passesChapterContentToTarget() {
        // 章节正文经 applySnapshot 独立参数透传到 Canvas（不进 snapshot，见 docs/26 §7）
        val input = createDefaultRenderInput().copy(chapterContent = "第一章 章节正文")
        orchestrator.apply(fakeTarget, input)
        assertEquals("第一章 章节正文", fakeTarget.lastChapterContent)
    }

    @Test
    fun apply_passesChapterContentsToTarget() {
        val contents = mapOf(1 to "第一章", 2 to "第二章")
        val input = createDefaultRenderInput().copy(chapterContents = contents)
        orchestrator.apply(fakeTarget, input)
        assertEquals(contents, fakeTarget.lastChapterContents)
    }

    // ── reserveGeneration() + applyAsync() 异步场景 ──

    @Test
    fun reserveGeneration_incrementsGeneration() {
        val gen1 = orchestrator.reserveGeneration()
        val gen2 = orchestrator.reserveGeneration()
        assertEquals(gen1 + 1, gen2)
    }

    @Test
    fun applyAsync_currentGeneration_applies() {
        val gen = orchestrator.reserveGeneration()
        orchestrator.applyAsync(fakeTarget, createDefaultRenderInput(), gen)
        assertEquals(1, fakeTarget.applyCount)
    }

    @Test
    fun applyAsync_staleGeneration_skips() {
        val gen = orchestrator.reserveGeneration()
        orchestrator.apply(fakeTarget, createDefaultRenderInput())
        orchestrator.applyAsync(fakeTarget, createDefaultRenderInput(), gen)
        assertEquals(1, fakeTarget.applyCount)
    }

    // ── isCurrent ──

    @Test
    fun isCurrent_latestGeneration_returnsTrue() {
        orchestrator.apply(fakeTarget, createDefaultRenderInput())
        assertTrue(orchestrator.isCurrent(1))
    }

    @Test
    fun isCurrent_oldGeneration_returnsFalse() {
        orchestrator.apply(fakeTarget, createDefaultRenderInput())
        orchestrator.apply(fakeTarget, createDefaultRenderInput())
        assertFalse(orchestrator.isCurrent(1))
    }

    // ── applyWithFallback ──

    @Test
    fun applyWithFallback_withinBudget_appliesRealInput() {
        orchestrator.applyWithFallback(
            fakeTarget,
            input = createDefaultRenderInput(),
            fallback = createFallbackRenderInput(),
            budgetMs = 1000,
        )
        assertEquals(1, fakeTarget.applyCount)
    }

    // ── Orchestrator 不缓存 canvas ──

    @Test
    fun apply_differentTargets_eachReceivesApply() {
        val target1 = FakeOrchestratorTarget()
        val target2 = FakeOrchestratorTarget()
        orchestrator.apply(target1, createDefaultRenderInput())
        orchestrator.apply(target2, createDefaultRenderInput())
        assertEquals(1, target1.applyCount)
        assertEquals(1, target2.applyCount)
    }
}

// ── Test doubles & helpers ──

private class FakeOrchestratorTarget : RenderApplierTarget {
    var applyCount = 0
    var lastChapterContent: CharSequence = ""
    var lastChapterContents: Map<Int, CharSequence> = emptyMap()

    override fun setPage(
        page: TextPage,
        next: TextPage?,
        prev: TextPage?,
        mode: PageRenderMode,
    ) {}

    override fun invalidateAllPages() {}
    override fun rebuildPageDelegate() {}
    override fun submitRenderTask() {}

    override fun applySnapshot(
        snapshot: Any,
        diff: Any,
        pageDelegate: PageDelegate?,
        chapterContent: CharSequence,
        chapterContents: Map<Int, CharSequence>,
    ) {
        applyCount++
        lastChapterContent = chapterContent
        lastChapterContents = chapterContents
    }
}

private fun createDefaultRenderInput() = ReaderRenderInput(
    page = createDefaultPageSnapshot(),
    settings = createDefaultSettingsSnapshot(),
    overlay = createDefaultOverlaySnapshot(),
    pageDelegate = null,
)

private fun createFallbackRenderInput() = ReaderRenderInput(
    page = createDefaultPageSnapshot(currentPage = null),
    settings = createDefaultSettingsSnapshot(),
    overlay = createDefaultOverlaySnapshot(),
    pageDelegate = null,
)

private fun createDefaultSettingsSnapshot() = ReaderSettingsSnapshot(
    layoutInput = createDefaultLayoutInput(),
    themeColors = ThemeColors(
        0xFFFFFFFF.toInt(),
        0xFF000000.toInt(),
        0xFF666666.toInt(),
        0xFF666666.toInt(),
        0xFF333333.toInt(),
        0xFF1976D2.toInt(),
    ),
    textAlign = ReaderTextAlign.LEFT,
    titleStyle = TitleStyleConfig(),
    headerSlots = SlotResolution(),
    footerSlots = SlotResolution(),
    batteryLevel = 100,
    showProgress = true,
    headerFooterAlpha = 0.4f,
    showHeaderLine = false,
    showFooterLine = false,
    headerFontSizeRatio = 0.75f,
    footerFontSizeRatio = 0.75f,
    edgeTurnPage = true,
    edgeWidthPercent = 0.33f,
    leftZoneRatio = 0.33f,
    fontSizePx = 54f,
    letterSpacing = 0f,
    fontWeight = ReaderFontWeight.NORMAL,
    fontKey = "harmony",
)
