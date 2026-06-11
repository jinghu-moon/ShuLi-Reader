package com.shuli.reader.core.reader.layout

import com.shuli.reader.core.data.IndentUnit
import com.shuli.reader.core.reader.SimpleTextMeasurer
import com.shuli.reader.core.reader.TextMeasurer
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig

/**
 * 从 [ReaderLayoutInput] 构造 [TextMeasurer] 和 [ReaderLayoutConfig]。
 *
 * 切断 Paginator 对 Canvas Paint 的反向依赖。
 */
internal object ReaderTextMeasurerFactory {

    fun create(@Suppress("UNUSED_PARAMETER") input: ReaderLayoutInput): TextMeasurer {
        return SimpleTextMeasurer()
    }

    fun toLayoutConfig(input: ReaderLayoutInput): ReaderLayoutConfig {
        val textSizePx = input.fontSizeSp * input.density
        // 段首缩进：CHARACTER 模式下 indent 单位为 em（字号倍数），PIXEL 模式下为 dp
        val indentPx = when (input.indentUnit) {
            IndentUnit.CHARACTER -> input.indent * textSizePx
            IndentUnit.PIXEL -> input.indent * input.density
        }
        return ReaderLayoutConfig(
            pageSize = PageSize(input.viewportWidth, input.viewportHeight),
            textSize = textSizePx,
            lineHeight = input.lineSpacing,
            paragraphSpacing = input.paragraphSpacing * textSizePx,
            marginTop = input.marginTopDp * input.density,
            marginBottom = input.marginBottomDp * input.density,
            marginLeft = input.marginLeftDp * input.density,
            marginRight = input.marginRightDp * input.density,
            indent = indentPx,
            density = input.density,
            letterSpacingPx = input.letterSpacing * textSizePx,
            titleStyle = input.titleStyle,
            useZhLayout = input.useZhLayout,
            bottomJustify = input.bottomJustify,
        )
    }
}
