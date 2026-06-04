package com.shuli.reader.feature.reader.prefs

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
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 阅读器偏好设置的统一入口。
 *
 * 从 [com.shuli.reader.feature.reader.ReaderViewModel] 抽出，承担：
 * - 所有 `setXxx(...)` 偏好更新函数
 * - [updatePrefs] 辅助（更新 state + 触发 reflow/invalidate + 持久化）
 * - 主题切换
 * - 字体的 load/import/delete（通过 [fontOps]）
 *
 * **设计**：ViewModel 仍保留同名公共 API 作为委托，UI 调用方无需修改。
 *
 * @param uiState 与 ViewModel 共享的 UI 状态
 * @param scope 协程作用域（用于持久化 launch）
 * @param userPreferences DataStore 偏好访问入口
 * @param reflow 触发当前章节重排
 * @param invalidate 触发当前页 recorder 失效
 * @param resetToolbarAutoHide 重置工具栏自动隐藏计时器
 * @param fontOps 字体相关操作（load/import/delete），由 ViewModel 注入
 */
class ReaderPreferencesBridge(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val userPreferences: UserPreferences?,
    private val reflow: (ReaderPreferences) -> Unit,
    private val invalidate: () -> Unit,
    private val resetToolbarAutoHide: () -> Unit,
    private val fontOps: FontOps,
) {

    /** 字体操作回调（原属 ReaderViewModel 的字体管理部分） */
    interface FontOps {
        fun loadCustomFonts()
        fun importFont(uri: android.net.Uri, displayName: String?)
        fun deleteFont(fontId: String)
    }

    // ── 通用偏好更新辅助 ─────────────────────────────────────

    /** 更新 ReaderPreferences 并同步持久化，需要 reflow 时触发重排。 */
    fun updatePrefs(
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
        if (reflow) this.reflow(updated)
        if (invalidate) this.invalidate()
        scope.launch { userPreferences?.let { save(it) } }
    }

    // ── 主题 ─────────────────────────────────────────────────

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

    // ── 字体管理（委托）───────────────────────────────────────

    fun loadCustomFonts() = fontOps.loadCustomFonts()
    fun importFont(uri: android.net.Uri, displayName: String? = null) = fontOps.importFont(uri, displayName)
    fun deleteFont(fontId: String) = fontOps.deleteFont(fontId)

    // ── 排版参数（变化时需 reflow）────────────────────────────

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

    fun setReadingFont(font: String) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(readingFont = font) }, { it.setReadingFont(font) })
    }

    fun setLetterSpacing(spacing: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(letterSpacing = spacing) }, { it.setLetterSpacing(spacing) }, reflow = true)
    }

    fun setFontWeight(weight: ReaderFontWeight) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(fontWeight = weight) }, { it.setFontWeight(weight.toStorageString()) }, invalidate = true)
    }

    fun setTextAlign(align: ReaderTextAlign) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(textAlign = align) }, { it.setTextAlign(align.toStorageString()) }, invalidate = true)
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
        updatePrefs({ it.copy(bottomJustify = enabled) }, { it.setBottomJustify(enabled) }, reflow = true)
    }

    // ── 行为 / 外观（不影响排版）─────────────────────────────

    fun setBrightness(brightness: Float, finished: Boolean = false) {
        resetToolbarAutoHide()
        updatePrefs(
            { it.copy(brightness = brightness) },
            { if (finished) it.setBrightness(brightness) },
        )
    }

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

    // ── TTS 参数 ─────────────────────────────────────────────

    fun setTtsSpeed(speed: Float) {
        updatePrefs({ it.copy(ttsSpeed = speed) }, { it.setTtsSpeed(speed) })
    }

    fun setTtsPitch(pitch: Float) {
        updatePrefs({ it.copy(ttsPitch = pitch) }, { it.setTtsPitch(pitch) })
    }

    // ── 页眉页脚 ─────────────────────────────────────────────

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

    // ── 标题样式 ─────────────────────────────────────────────

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
}
