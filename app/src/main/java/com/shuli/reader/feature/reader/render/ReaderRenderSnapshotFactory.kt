package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.reader.model.SlotResolution
import com.shuli.reader.core.reader.model.TitleStyleConfig
import com.shuli.reader.core.reader.engine.animation.PageDelegate
import com.shuli.reader.core.reader.layout.ReaderLayoutInput

/**
 * 渲染输入：ViewModel 传给 Orchestrator 的完整状态。
 */
data class ReaderRenderInput(
    val page: PageSnapshot,
    val settings: ReaderSettingsSnapshot,
    val overlay: OverlaySnapshot,
    val pageDelegate: PageDelegate?,
    val chapterContent: CharSequence = "",
    val chapterContents: Map<Int, CharSequence> = emptyMap(),
)

/**
 * ViewModel 侧的设置快照，Factory 从中构建 LayoutSnapshot / VisualSnapshot / ShellSnapshot。
 * 包含所有影响 Canvas 渲染的设置字段。
 */
data class ReaderSettingsSnapshot(
    val layoutInput: ReaderLayoutInput,
    val themeColors: ThemeColors,
    val textAlign: ReaderTextAlign,
    val titleStyle: TitleStyleConfig,
    val headerSlots: SlotResolution,
    val footerSlots: SlotResolution,
    val batteryLevel: Int,
    val showProgress: Boolean,
    val headerFooterAlpha: Float,
    val showHeaderLine: Boolean,
    val showFooterLine: Boolean,
    val headerFontSizeRatio: Float,
    val footerFontSizeRatio: Float,
    val edgeTurnPage: Boolean,
    val edgeWidthPercent: Float,
    val leftZoneRatio: Float,
    val fontSizePx: Float,
    val letterSpacing: Float,
    val fontWeight: ReaderFontWeight,
    val fontKey: String,
    val gestureConfig: com.shuli.reader.feature.reader.settings.GestureConfig =
        com.shuli.reader.feature.reader.settings.GestureConfig(),
)

/**
 * 从 ReaderRenderInput 构建 ReaderRenderSnapshot。
 * 使用 data class equals() 做子快照缓存，避免不必要的对象创建。
 */
class ReaderRenderSnapshotFactory {
    private var lastLayout: LayoutSnapshot? = null
    private var lastVisual: VisualSnapshot? = null
    private var lastShell: ShellSnapshot? = null

    fun build(input: ReaderRenderInput, generation: Long): ReaderRenderSnapshot {
        val layoutCandidate = buildLayoutSnapshot(input.settings)
        val layout = if (layoutCandidate == lastLayout) lastLayout!! else layoutCandidate.also { lastLayout = it }

        val visualCandidate = buildVisualSnapshot(input.settings)
        val visual = if (visualCandidate == lastVisual) lastVisual!! else visualCandidate.also { lastVisual = it }

        val shellCandidate = buildShellSnapshot(input.settings)
        val shell = if (shellCandidate == lastShell) lastShell!! else shellCandidate.also { lastShell = it }

        return ReaderRenderSnapshot(
            generation = generation,
            page = input.page,
            layout = layout,
            visual = visual,
            shell = shell,
            overlay = input.overlay,
        )
    }

    private fun buildLayoutSnapshot(settings: ReaderSettingsSnapshot): LayoutSnapshot {
        return LayoutSnapshot(
            input = settings.layoutInput,
            layoutKey = ReaderLayoutHasher.hash(settings.layoutInput),
        )
    }

    private fun buildVisualSnapshot(settings: ReaderSettingsSnapshot): VisualSnapshot {
        return VisualSnapshot(
            themeColors = settings.themeColors,
            textAlign = settings.textAlign,
            titleStyle = settings.titleStyle,
            contentKey = RenderKey(
                textAlign = settings.textAlign,
                themeColors = settings.themeColors,
                titleStyle = settings.titleStyle,
                showProgress = settings.showProgress,
                headerFooterAlpha = settings.headerFooterAlpha,
            ),
        )
    }

    private fun buildShellSnapshot(settings: ReaderSettingsSnapshot): ShellSnapshot {
        return ShellSnapshot(
            headerSlots = settings.headerSlots,
            footerSlots = settings.footerSlots,
            batteryLevel = settings.batteryLevel,
            showProgress = settings.showProgress,
            headerFooterAlpha = settings.headerFooterAlpha,
            showHeaderLine = settings.showHeaderLine,
            showFooterLine = settings.showFooterLine,
            headerFontSizeRatio = settings.headerFontSizeRatio,
            footerFontSizeRatio = settings.footerFontSizeRatio,
            edgeTurnPage = settings.edgeTurnPage,
            edgeWidthPercent = settings.edgeWidthPercent,
            leftZoneRatio = settings.leftZoneRatio,
            gestureConfig = settings.gestureConfig,
            shellKey = RenderKey(
                textAlign = ReaderTextAlign.LEFT,
                themeColors = settings.themeColors,
                titleStyle = settings.titleStyle,
                showProgress = settings.showProgress,
                headerFooterAlpha = settings.headerFooterAlpha,
            ),
        )
    }
}
