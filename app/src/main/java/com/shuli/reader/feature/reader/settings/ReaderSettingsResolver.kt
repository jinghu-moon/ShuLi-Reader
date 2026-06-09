package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.toChineseConvert
import com.shuli.reader.core.data.toFontWeight
import com.shuli.reader.core.data.toHeaderVisibility
import com.shuli.reader.core.data.toSlotContent
import com.shuli.reader.core.data.toTextAlign
import com.shuli.reader.core.data.toTitleAlign
import com.shuli.reader.core.reader.FooterConfig
import com.shuli.reader.core.reader.HeaderConfig
import com.shuli.reader.core.reader.TitleStyleConfig

/**
 * 合并"全局默认 + 本书覆盖 + 会话状态"为最终 [ResolvedReaderSettings]。
 *
 * 纯函数：相同输入总是产生相同输出；不依赖 Android 运行时；可独立在 JVM 上测试。
 *
 * 合并优先级（由高到低）：
 * 1. [session] 会话级覆盖（仅部分字段，如临时亮度）
 * 2. [bookPrefs] 本书覆盖（null 表示跟随默认）
 * 3. [defaults] 全局默认
 */
object ReaderSettingsResolver {

    fun resolve(
        defaults: ResolvedReaderSettings,
        bookPrefs: BookReaderPrefsEntity?,
        session: ReaderSessionState = ReaderSessionState(),
    ): ResolvedReaderSettings {
        val bp = bookPrefs

        // ── 排版 ──
        val titleStyle = if (bp != null && bp.hasTitleStyleOverride()) {
            TitleStyleConfig(
                align = bp.titleAlign?.toTitleAlign() ?: defaults.titleStyle.align,
                sizeOffsetSp = bp.titleSizeOffset ?: defaults.titleStyle.sizeOffsetSp,
                marginTopDp = bp.titleMarginTop ?: defaults.titleStyle.marginTopDp,
                marginBottomDp = bp.titleMarginBottom ?: defaults.titleStyle.marginBottomDp,
            )
        } else {
            defaults.titleStyle
        }

        val header = if (bp != null && bp.hasHeaderOverride()) {
            HeaderConfig(
                visibility = bp.headerVisibility?.toHeaderVisibility() ?: defaults.header.visibility,
                left = bp.headerLeft?.toSlotContent() ?: defaults.header.left,
                center = bp.headerCenter?.toSlotContent() ?: defaults.header.center,
                right = bp.headerRight?.toSlotContent() ?: defaults.header.right,
                marginTop = bp.headerMarginTop ?: defaults.header.marginTop,
            )
        } else {
            defaults.header
        }

        val footer = if (bp != null && bp.hasFooterOverride()) {
            FooterConfig(
                visibility = bp.footerVisibility?.toHeaderVisibility() ?: defaults.footer.visibility,
                left = bp.footerLeft?.toSlotContent() ?: defaults.footer.left,
                center = bp.footerCenter?.toSlotContent() ?: defaults.footer.center,
                right = bp.footerRight?.toSlotContent() ?: defaults.footer.right,
                marginBottom = bp.footerMarginBottom ?: defaults.footer.marginBottom,
            )
        } else {
            defaults.footer
        }

        return ResolvedReaderSettings(
            // 排版
            fontSize = bp?.fontSize ?: defaults.fontSize,
            lineSpacing = bp?.lineSpacing ?: defaults.lineSpacing,
            paragraphSpacing = bp?.paragraphSpacing ?: defaults.paragraphSpacing,
            indent = bp?.indent ?: defaults.indent,
            marginHorizontal = bp?.marginHorizontal ?: defaults.marginHorizontal,
            marginVertical = bp?.marginVertical ?: defaults.marginVertical,
            letterSpacing = bp?.letterSpacing ?: defaults.letterSpacing,
            useZhLayout = bp?.useZhLayout ?: defaults.useZhLayout,
            chineseConvert = bp?.chineseConvert?.toChineseConvert() ?: defaults.chineseConvert,
            usePanguSpacing = bp?.usePanguSpacing ?: defaults.usePanguSpacing,
            bottomJustify = bp?.bottomJustify ?: defaults.bottomJustify,
            titleStyle = titleStyle,

            // 外观
            readingFont = bp?.readingFont ?: defaults.readingFont,
            fontWeight = bp?.fontWeight?.toFontWeight() ?: defaults.fontWeight,
            textAlign = bp?.textAlign?.toTextAlign() ?: defaults.textAlign,
            header = header,
            footer = footer,
            headerFooterAlpha = bp?.headerFooterAlpha ?: defaults.headerFooterAlpha,
            showProgress = bp?.showProgress ?: defaults.showProgress,
            showHeaderLine = bp?.showHeaderLine ?: defaults.showHeaderLine,
            showFooterLine = bp?.showFooterLine ?: defaults.showFooterLine,
            headerFontSizeRatio = bp?.headerFontSizeRatio ?: defaults.headerFontSizeRatio,
            footerFontSizeRatio = bp?.footerFontSizeRatio ?: defaults.footerFontSizeRatio,

            // 行为（session 优先）
            brightness = session.brightness ?: bp?.brightness ?: defaults.brightness,
            keepScreenOn = session.keepScreenOn ?: bp?.keepScreenOn ?: defaults.keepScreenOn,
            edgeTurnPage = bp?.edgeTurnPage ?: defaults.edgeTurnPage,
            edgeWidthPercent = bp?.edgeWidthPercent ?: defaults.edgeWidthPercent,
            volumeKeyTurnPage = bp?.volumeKeyTurnPage ?: defaults.volumeKeyTurnPage,
        )
    }
}

private fun BookReaderPrefsEntity.hasTitleStyleOverride(): Boolean =
    titleAlign != null || titleSizeOffset != null || titleMarginTop != null || titleMarginBottom != null

private fun BookReaderPrefsEntity.hasHeaderOverride(): Boolean =
    headerVisibility != null || headerLeft != null || headerCenter != null ||
        headerRight != null || headerMarginTop != null

private fun BookReaderPrefsEntity.hasFooterOverride(): Boolean =
    footerVisibility != null || footerLeft != null || footerCenter != null ||
        footerRight != null || footerMarginBottom != null
