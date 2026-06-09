package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.reader.FooterConfig
import com.shuli.reader.core.reader.HeaderConfig
import com.shuli.reader.core.reader.TitleStyleConfig

/**
 * 合并全局默认 + 本书覆盖 + 会话状态后的最终设置。
 *
 * 所有字段均为非空 —— Resolver 负责把 null（"跟随默认"）替换为默认值。
 * 进入分页与渲染前的唯一最终设置源。
 */
data class ResolvedReaderSettings(
    // 排版（reflow 级）
    val fontSize: Float,
    val lineSpacing: Float,
    val paragraphSpacing: Float,
    val indent: Float,
    val marginHorizontal: Float,
    val marginVertical: Float,
    val letterSpacing: Float,
    val useZhLayout: Boolean,
    val chineseConvert: ChineseConvert,
    val usePanguSpacing: Boolean,
    val bottomJustify: Boolean,
    val titleStyle: TitleStyleConfig = TitleStyleConfig(),

    // 外观（重绘级）
    val readingFont: String,
    val fontWeight: ReaderFontWeight,
    val textAlign: ReaderTextAlign,
    val header: HeaderConfig = HeaderConfig(),
    val footer: FooterConfig = FooterConfig(),
    val headerFooterAlpha: Float = 0.4f,
    val showProgress: Boolean = true,
    val showHeaderLine: Boolean = false,
    val showFooterLine: Boolean = false,
    val headerFontSizeRatio: Float = 0.75f,
    val footerFontSizeRatio: Float = 0.75f,

    // 行为（标志位）
    val brightness: Float,
    val keepScreenOn: Boolean,
    val edgeTurnPage: Boolean,
    val edgeWidthPercent: Float,
    val volumeKeyTurnPage: Boolean = false,
)
