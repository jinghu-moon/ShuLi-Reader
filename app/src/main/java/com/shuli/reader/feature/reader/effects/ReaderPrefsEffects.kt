package com.shuli.reader.feature.reader.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.reader.ReaderCanvasView
import com.shuli.reader.feature.reader.ReaderViewModel

/**
 * 阅读器排版偏好相关 LaunchedEffect 集合。
 *
 * 职责：字号/字距/字重/字体/对齐、页眉页脚、标题样式、
 *       边缘翻页、页眉页脚字号比例。
 * 仅在 CanvasView 非 null 时生效，绕过 recomposition 直接操作 View。
 */
@Composable
fun ReaderPrefsEffects(
    canvasView: ReaderCanvasView?,
    prefs: ReaderPreferences,
    density: Float,
    viewModel: ReaderViewModel,
) {
    // 排版属性（字号/字距/字重/字体/对齐）→ 立即更新 Paint
    LaunchedEffect(prefs.fontSize, prefs.letterSpacing, prefs.fontWeight, prefs.readingFont, prefs.textAlign) {
        canvasView?.updatePaintSnapshot(
            textSize = prefs.fontSize * density,
            letterSpacing = prefs.letterSpacing,
            fakeBold = prefs.fontWeight == ReaderFontWeight.BOLD,
            fontKey = prefs.readingFont,
            textAlign = prefs.textAlign,
            invalidateContent = true,
        )
        canvasView?.textPaint?.let { viewModel.syncTextMeasurerPaint(it) }
    }

    // 页眉页脚
    LaunchedEffect(prefs.headerFooterAlpha, prefs.showProgress, prefs.showHeaderLine, prefs.showFooterLine) {
        val (headerRes, footerRes) = viewModel.resolveHeaderAndFooterSlots()
        canvasView?.updateHeaderFooter(
            headerRes,
            footerRes,
            prefs.headerFooterAlpha,
            prefs.showProgress,
            prefs.showHeaderLine,
            prefs.showFooterLine,
        )
    }

    // 标题样式
    LaunchedEffect(prefs.titleStyle) {
        canvasView?.setTitleStyle(prefs.titleStyle)
    }

    // 边缘翻页
    LaunchedEffect(prefs.edgeTurnPage) {
        canvasView?.setEdgeTurnPageEnabled(prefs.edgeTurnPage)
    }

    // 边缘触摸宽度
    LaunchedEffect(prefs.edgeWidthPercent) {
        canvasView?.setEdgeWidthPercent(prefs.edgeWidthPercent)
    }

    // 页眉页脚字号比例
    LaunchedEffect(prefs.headerFontSizeRatio, prefs.footerFontSizeRatio) {
        canvasView?.setHeaderTextRatio(prefs.headerFontSizeRatio)
        canvasView?.setFooterTextRatio(prefs.footerFontSizeRatio)
    }
}
