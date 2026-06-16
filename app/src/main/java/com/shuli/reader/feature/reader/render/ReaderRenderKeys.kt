package com.shuli.reader.feature.reader.render

import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ThemeColors
import com.shuli.reader.core.reader.model.SlotResolution
import com.shuli.reader.core.reader.model.TitleStyleConfig
import com.shuli.reader.core.reader.layout.ReaderLayoutInput
import com.shuli.reader.core.reader.model.SelectionRange

/**
 * 影响分页结果的缓存 key。
 * layoutVersion 确保分页算法升级时旧缓存自动失效。
 */
data class LayoutKey(
    val layoutVersion: Int,
    val inputHash: String,
)

/**
 * 影响正文/壳层录制的缓存 key（不改变分页）。
 */
data class RenderKey(
    val textAlign: ReaderTextAlign,
    val themeColors: ThemeColors,
    val titleStyle: TitleStyleConfig,
    val showProgress: Boolean,
    val headerFooterAlpha: Float,
)

/**
 * 影响覆盖层录制的缓存 key。
 */
data class OverlayKey(
    val selectedRange: SelectionRange?,
    val noteRangesHash: Int,
)

/**
 * 影响壳层录制的完整缓存 key。
 *
 * 覆盖 renderShell() 读取的全部参数：页眉页脚槽位、主题色、透明度、
 * 分割线、字号比例、进度条、电量。任一字段变化均触发壳层重录。
 */
data class ShellRenderKey(
    val headerSlots: SlotResolution,
    val footerSlots: SlotResolution,
    val themeColors: ThemeColors,
    val showProgress: Boolean,
    val headerFooterAlpha: Float,
    val showHeaderLine: Boolean,
    val showFooterLine: Boolean,
    val headerFontSizeRatio: Float,
    val footerFontSizeRatio: Float,
    val batteryLevel: Int,
)

internal object ReaderLayoutHasher {
    fun hash(input: ReaderLayoutInput): LayoutKey {
        val sb = StringBuilder()
        sb.append(input.bookId).append('|')
        sb.append(input.chapterIndex).append('|')
        sb.append(input.viewportWidth).append('x').append(input.viewportHeight).append('|')
        sb.append(input.density).append('|')
        sb.append(input.fontSizeSp).append('|')
        sb.append(input.fontKey).append('|')
        sb.append(input.fontWeight).append('|')
        sb.append(input.lineSpacing).append('|')
        sb.append(input.paragraphSpacing).append('|')
        sb.append(input.letterSpacing).append('|')
        sb.append(input.marginTopDp).append('|')
        sb.append(input.marginBottomDp).append('|')
        sb.append(input.marginLeftDp).append('|')
        sb.append(input.marginRightDp).append('|')
        sb.append(input.indent).append('|')
        sb.append(input.indentUnit).append('|')
        sb.append(input.titleStyle).append('|')
        sb.append(input.headerVisibleForLayout).append('|')
        sb.append(input.footerVisibleForLayout).append('|')
        sb.append(input.chineseConvert).append('|')
        sb.append(input.usePanguSpacing).append('|')
        sb.append(input.useZhLayout).append('|')
        sb.append(input.bottomJustify).append('|')
        // v5.1 预留字段（一次性加入 hash，避免后续多次 bump VERSION）
        sb.append(input.paragraphDivider).append('|')
        sb.append(input.hyphenation).append('|')
        sb.append(input.vertical).append('|')
        sb.append(input.dualPage).append('|')
        sb.append(input.bionicReading)
        val combinedVersion = ReaderLayoutInput.LAYOUT_ALGORITHM_VERSION * 1000 + input.layoutVersion
        return LayoutKey(
            layoutVersion = combinedVersion,
            inputHash = sb.toString(),
        )
    }
}

internal object RenderKeyFactory {
    fun from(visual: VisualSnapshot): RenderKey = RenderKey(
        textAlign = visual.textAlign,
        themeColors = visual.themeColors,
        titleStyle = visual.titleStyle,
        showProgress = true,
        headerFooterAlpha = 1f,
    )

    fun fromShell(shell: ShellSnapshot): RenderKey = RenderKey(
        textAlign = ReaderTextAlign.LEFT,
        themeColors = ThemeColors(0, 0, 0, 0, 0, 0),
        titleStyle = TitleStyleConfig(),
        showProgress = shell.showProgress,
        headerFooterAlpha = shell.headerFooterAlpha,
    )
}

internal object OverlayKeyFactory {
    fun from(overlay: OverlaySnapshot): OverlayKey = OverlayKey(
        selectedRange = overlay.selectedRange,
        noteRangesHash = overlay.noteRanges.hashCode(),
    )
}
