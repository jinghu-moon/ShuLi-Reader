package com.shuli.reader.feature.reader.progress

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.reader.model.PageSize
import com.shuli.reader.core.reader.model.ReaderLayoutConfig

/**
 * 把 [ReaderPreferences] + 设备参数转换成影响分页的 [ReaderLayoutConfig]。
 *
 * 原 [com.shuli.reader.feature.reader.ReaderViewModel.layoutConfigFor] 的共享版本，
 * 供 [ReadingProgressTracker] 与 `ChapterPaginationCoordinator` 共同使用，避免重复实现。
 */
fun buildLayoutConfig(
    preferences: ReaderPreferences,
    density: Float,
    screenWidthPx: Int,
    screenHeightPx: Int,
): ReaderLayoutConfig {
    val textSizePx = preferences.fontSize * density
    return ReaderLayoutConfig(
        pageSize = PageSize(screenWidthPx, screenHeightPx),
        textSize = textSizePx,
        lineHeight = preferences.lineSpacing,
        paragraphSpacing = preferences.paragraphSpacing * textSizePx,
        marginHorizontal = preferences.marginHorizontal * density,
        marginVertical = preferences.marginVertical * density,
        indent = preferences.indent,
        density = density,
        letterSpacingPx = preferences.letterSpacing * textSizePx,
        titleStyle = preferences.titleStyle,
        useZhLayout = preferences.useZhLayout,
        bottomJustify = preferences.bottomJustify,
        headerMarginTop = preferences.header.marginTop * density,
        footerMarginBottom = preferences.footer.marginBottom * density,
    )
}
