package com.shuli.reader.core.reader.layout

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.IndentUnit
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.reader.TitleStyleConfig

/**
 * 分页器的完整布局输入。
 *
 * Paginator 只消费此 data class，不依赖 Canvas Paint 或 View 生命周期。
 * 任何影响分页结果的字段都必须在此声明，并自动进入 LayoutKey。
 */
data class ReaderLayoutInput(
    val layoutVersion: Int,
    val bookId: Long,
    val chapterIndex: Int,
    val anchorByteOffset: Long,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val density: Float,
    val fontSizeSp: Float,
    val fontKey: String,
    val fontWeight: ReaderFontWeight,
    val lineSpacing: Float,
    val paragraphSpacing: Float,
    val letterSpacing: Float,
    val marginTopDp: Float,
    val marginBottomDp: Float,
    val marginLeftDp: Float,
    val marginRightDp: Float,
    val indent: Float,
    val indentUnit: IndentUnit = IndentUnit.CHARACTER,
    val titleStyle: TitleStyleConfig,
    val headerVisibleForLayout: Boolean,
    val footerVisibleForLayout: Boolean,
    val chineseConvert: ChineseConvert,
    val usePanguSpacing: Boolean,
    val useZhLayout: Boolean,
    val bottomJustify: Boolean,
    // v5.1 预留字段（Phase 1-4）
    val paragraphDivider: Boolean = false,
    val hyphenation: Int = 0,
    val vertical: Boolean = false,
    val dualPage: Boolean = false,
    val bionicReading: Boolean = false,
) {
    companion object {
        const val LAYOUT_ALGORITHM_VERSION = 2
    }
}
