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
    val marginHorizontalDp: Float,
    val marginVerticalDp: Float,
    val indent: Float,
    val indentUnit: IndentUnit = IndentUnit.CHARACTER,
    val titleStyle: TitleStyleConfig,
    val headerVisibleForLayout: Boolean,
    val footerVisibleForLayout: Boolean,
    val chineseConvert: ChineseConvert,
    val usePanguSpacing: Boolean,
    val useZhLayout: Boolean,
    val bottomJustify: Boolean,
) {
    companion object {
        /**
         * 分页算法版本常量。
         *
         * 当标点避头尾、缩进规则、中文/日文换行、标题占位等分页算法升级时，
         * 提高此值即可一次性废弃旧分页缓存（§14.1.1）。
         *
         * 运行时的 [layoutVersion] 由 reflow 递增，与此常量相加后进入 [LayoutKey]。
         */
        const val LAYOUT_ALGORITHM_VERSION = 1
    }
}
