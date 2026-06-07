package com.shuli.reader.feature.reader

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.data.toStorageString
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.core.reader.TitleAlign
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 阅读器偏好设置管理：所有 setXxx 偏好方法 + updatePrefs 通用更新。
 *
 * 从 ReaderViewModel 拆出，SRP —— 只负责"偏好读写与持久化"这一变更轴。
 */
internal class ReaderSettingsManager(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val userPreferences: UserPreferences?,
    // ── 回调 ──
    private val reflowCurrentChapter: (ReaderPreferences) -> Unit,
    private val currentPageInvalidate: () -> Unit,
    private val resetToolbarAutoHide: () -> Unit,
) {

    // ── 主题 ──────────────────────────────────────────────

    fun setReaderTheme(theme: ReaderTheme) {
        val currentPrefs = uiState.value.readerPreferences
        val newPrefs = currentPrefs.copy(backgroundColor = theme)
        uiState.value = uiState.value.copy(
            readerPreferences = newPrefs,
            themeColors = theme.toReaderColorScheme().toCanvasThemeColors(),
        )
    }

    fun cycleTheme() {
        val current = uiState.value.readerPreferences.backgroundColor
        val next = when (current) {
            ReaderTheme.LIGHT -> ReaderTheme.DARK
            ReaderTheme.DARK -> ReaderTheme.PAPER
            ReaderTheme.PAPER -> ReaderTheme.LIGHT
            ReaderTheme.OLED -> ReaderTheme.PAPER
        }
        setReaderTheme(next)
    }

    // ── 排版参数（reflow） ──────────────────────────────────────

    fun setFontSize(size: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(fontSize = size) }, { it.setDefaultFontSize(size) }, reflow = true)
    }

    fun setLineSpacing(spacing: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(lineSpacing = spacing) }, { it.setDefaultLineSpacing(spacing) }, reflow = true)
    }

    fun setParagraphSpacing(spacing: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(paragraphSpacing = spacing) }, { it.setDefaultParagraphSpacing(spacing) }, reflow = true)
    }

    fun setIndent(indent: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(indent = indent) }, { it.setDefaultIndent(indent) }, reflow = true)
    }

    fun setMarginHorizontal(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(marginHorizontal = margin) }, { it.setMarginHorizontal(margin) }, reflow = true)
    }

    fun setMarginVertical(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(marginVertical = margin) }, { it.setMarginVertical(margin) }, reflow = true)
    }

    fun setLetterSpacing(spacing: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(letterSpacing = spacing) }, { it.setLetterSpacing(spacing) }, reflow = true)
    }

    fun setChineseConvert(convert: ChineseConvert) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(chineseConvert = convert) }, { it.setChineseConvert(convert.toStorageString()) }, reflow = true)
    }

    fun setUseZhLayout(enabled: Boolean) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(useZhLayout = enabled) }, { it.setUseZhLayout(enabled) }, reflow = true)
    }

    fun setPanguSpacing(enabled: Boolean) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(usePanguSpacing = enabled) }, { it.setUsePanguSpacing(enabled) }, reflow = true)
    }

    fun setBottomJustify(enabled: Boolean) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(bottomJustify = enabled) }, { it.setBottomJustify(enabled) }, reflow = true)
    }

    // ── 外观参数（仅重绘） ──────────────────────────────────────

    fun setReadingFont(font: String) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(readingFont = font) }, { it.setReadingFont(font) })
    }

    fun setFontWeight(weight: ReaderFontWeight) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(fontWeight = weight) }, { it.setFontWeight(weight.toStorageString()) }, invalidate = true)
    }

    fun setTextAlign(align: ReaderTextAlign) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(textAlign = align) }, { it.setTextAlign(align.toStorageString()) }, invalidate = true)
    }

    // ── 亮度 ──────────────────────────────────────────────

    fun setBrightness(brightness: Float, finished: Boolean = false) {
        resetToolbarAutoHide()
        updatePrefs(
            { it.copy(brightness = brightness) },
            { if (finished) it.setBrightness(brightness) },
        )
    }

    // ── 页眉页脚 ──────────────────────────────────────────────

    fun setHeaderMarginTop(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs(
            { it.copy(header = it.header.copy(marginTop = margin)) },
            { it.setHeaderMarginTop(margin) },
        )
    }

    fun setFooterMarginBottom(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs(
            { it.copy(footer = it.footer.copy(marginBottom = margin)) },
            { it.setFooterMarginBottom(margin) },
        )
    }

    fun setHeaderVisibility(visibility: HeaderVisibility) {
        updatePrefs(
            { it.copy(header = it.header.copy(visibility = visibility)) },
            { it.setHeaderVisibility(visibility.toStorageString()) },
            reflow = true,
        )
    }

    fun setHeaderLeft(slot: SlotContent) {
        updatePrefs(
            { it.copy(header = it.header.copy(left = slot)) },
            { it.setHeaderLeft(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setHeaderCenter(slot: SlotContent) {
        updatePrefs(
            { it.copy(header = it.header.copy(center = slot)) },
            { it.setHeaderCenter(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setHeaderRight(slot: SlotContent) {
        updatePrefs(
            { it.copy(header = it.header.copy(right = slot)) },
            { it.setHeaderRight(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setFooterVisibility(visibility: HeaderVisibility) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(visibility = visibility)) },
            { it.setFooterVisibility(visibility.toStorageString()) },
            reflow = true,
        )
    }

    fun setFooterLeft(slot: SlotContent) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(left = slot)) },
            { it.setFooterLeft(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setFooterCenter(slot: SlotContent) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(center = slot)) },
            { it.setFooterCenter(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setFooterRight(slot: SlotContent) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(right = slot)) },
            { it.setFooterRight(slot.toStorageString()) },
            invalidate = true,
        )
    }

    fun setHeaderFooterAlpha(alpha: Float) {
        updatePrefs({ it.copy(headerFooterAlpha = alpha) }, { it.setHeaderFooterAlpha(alpha) }, invalidate = true)
    }

    fun setShowProgress(show: Boolean) {
        updatePrefs({ it.copy(showProgress = show) }, { it.setShowProgress(show) }, invalidate = true)
    }

    fun setShowHeaderLine(show: Boolean) {
        updatePrefs({ it.copy(showHeaderLine = show) }, { it.setShowHeaderLine(show) }, invalidate = true)
    }

    fun setShowFooterLine(show: Boolean) {
        updatePrefs({ it.copy(showFooterLine = show) }, { it.setShowFooterLine(show) }, invalidate = true)
    }

    fun setHeaderFontSizeRatio(ratio: Float) {
        updatePrefs({ it.copy(headerFontSizeRatio = ratio) }, { it.setHeaderFontSizeRatio(ratio) }, invalidate = true)
    }

    fun setFooterFontSizeRatio(ratio: Float) {
        updatePrefs({ it.copy(footerFontSizeRatio = ratio) }, { it.setFooterFontSizeRatio(ratio) }, invalidate = true)
    }

    // ── 正文标题样式 ──────────────────────────────────────────────

    fun setTitleAlign(align: TitleAlign) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(align = align)) },
            { it.setTitleAlign(align.toStorageString()) },
            reflow = true,
        )
    }

    fun setTitleSizeOffset(offsetSp: Int) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(sizeOffsetSp = offsetSp)) },
            { it.setTitleSizeOffset(offsetSp) },
            reflow = true,
        )
    }

    fun setTitleMarginTop(dp: Float) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(marginTopDp = dp)) },
            { it.setTitleMarginTop(dp) },
            reflow = true,
        )
    }

    fun setTitleMarginBottom(dp: Float) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(marginBottomDp = dp)) },
            { it.setTitleMarginBottom(dp) },
            reflow = true,
        )
    }

    // ── 行为参数 ──────────────────────────────────────────────

    fun setKeepScreenOn(enabled: Boolean) {
        updatePrefs({ it.copy(keepScreenOn = enabled) }, { it.setKeepScreenOn(enabled) })
    }

    fun setVolumeKeyTurnPage(enabled: Boolean) {
        updatePrefs({ it.copy(volumeKeyTurnPage = enabled) }, { it.setVolumeKeyTurnPage(enabled) })
    }

    fun setEdgeTurnPage(enabled: Boolean) {
        updatePrefs({ it.copy(edgeTurnPage = enabled) }, { it.setEdgeTurnPage(enabled) })
    }

    fun setEdgeWidthPercent(percent: Float) {
        updatePrefs({ it.copy(edgeWidthPercent = percent) }, { it.setEdgeWidthPercent(percent) })
    }

    // ── TTS ──────────────────────────────────────────────

    fun setTtsSpeed(speed: Float) {
        updatePrefs({ it.copy(ttsSpeed = speed) }, { it.setTtsSpeed(speed) })
    }

    fun setTtsPitch(pitch: Float) {
        updatePrefs({ it.copy(ttsPitch = pitch) }, { it.setTtsPitch(pitch) })
    }

    // ── 通用更新辅助 ──────────────────────────────────────────────

    private fun updatePrefs(
        transform: (ReaderPreferences) -> ReaderPreferences,
        save: suspend (UserPreferences) -> Unit,
        reflow: Boolean = false,
        invalidate: Boolean = false,
    ) {
        val updated = transform(uiState.value.readerPreferences)
        uiState.value = uiState.value.copy(
            readerPreferences = updated,
            isReflowing = reflow,
        )
        if (reflow) reflowCurrentChapter(updated)
        if (invalidate) currentPageInvalidate()
        scope.launch { userPreferences?.let { save(it) } }
    }
}
