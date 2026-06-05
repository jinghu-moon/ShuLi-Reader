package com.shuli.reader.feature.reader.prefs

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.data.toChineseConvert
import com.shuli.reader.core.data.toFactoryType
import com.shuli.reader.core.data.toFontWeight
import com.shuli.reader.core.data.toHeaderVisibility
import com.shuli.reader.core.data.toPageAnimType
import com.shuli.reader.core.data.toSlotContent
import com.shuli.reader.core.data.toStorageString
import com.shuli.reader.core.data.toTextAlign
import com.shuli.reader.core.data.toTitleAlign
import com.shuli.reader.core.reader.FooterConfig
import com.shuli.reader.core.reader.HeaderConfig
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.core.reader.TitleAlign
import com.shuli.reader.core.reader.TitleStyleConfig
import com.shuli.reader.core.reader.animation.PageDelegate
import com.shuli.reader.core.reader.animation.PageDelegateFactory
import com.shuli.reader.feature.reader.ReaderUiState
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 排版参数（变化时需 reflow）
 */
private data class ReaderLayoutPrefs(
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
)

/**
 * 外观参数（变化时仅重绘）
 */
private data class ReaderVisualPrefs(
    val readingFont: String,
    val fontWeight: ReaderFontWeight,
    val textAlign: ReaderTextAlign,
    val pageAnimType: com.shuli.reader.core.data.PageAnimType,
    val header: HeaderConfig,
    val footer: FooterConfig,
    val headerFooterAlpha: Float,
    val showProgress: Boolean,
    val showHeaderLine: Boolean,
    val showFooterLine: Boolean,
    val headerFontSizeRatio: Float,
    val footerFontSizeRatio: Float,
)

/**
 * 行为参数（变化时仅更新标志位）
 */
private data class ReaderBehaviorPrefs(
    val brightness: Float,
    val keepScreenOn: Boolean,
    val volumeKeyTurnPage: Boolean,
    val edgeTurnPage: Boolean,
    val edgeWidthPercent: Float,
)

/**
 * 阅读器偏好设置管理器。
 *
 * 职责：ReaderPreferences 的所有 setter 方法、UserPreferences 流监听、
 *       主题切换、翻页动画类型、偏好应用与持久化。
 *
 * 通过 [uiState] 读写共享状态，不反向依赖 ViewModel。
 */
class ReaderPreferencesManager(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val userPreferences: UserPreferences?,
    private val scope: CoroutineScope,
) {
    // ── 回调（由 ViewModel 注入）────────────────────────────────────

    /** 触发当前章节 reflow（排版参数变化时调用） */
    var onReflowCurrentChapter: ((ReaderPreferences) -> Unit)? = null

    /** 通知 ViewModel 创建新的 PageDelegate */
    var onPageDelegateChanged: ((PageDelegate) -> Unit)? = null

    /** 使当前页 recorder 失效并触发重绘 */
    var onCurrentPageInvalidate: (() -> Unit)? = null

    // ── 初始化：监听 UserPreferences 流 ────────────────────────────

    /**
     * 启动 UserPreferences 的三个 combine 流监听。
     * 在 ViewModel init 时调用。
     */
    fun startCollectingFlows() {
        userPreferences?.let { prefs ->
            // Group A: 排版参数 → 变化时需 reflow（重新分页）
            scope.launch {
                combine(
                    prefs.defaultFontSize,
                    prefs.defaultLineSpacing,
                    prefs.defaultParagraphSpacing,
                    prefs.defaultIndent,
                    prefs.marginHorizontal,
                    prefs.marginVertical,
                    prefs.letterSpacing,
                    prefs.useZhLayout,
                    prefs.chineseConvert,
                    prefs.titleAlign,
                    prefs.titleSizeOffset,
                    prefs.titleMarginTop,
                    prefs.titleMarginBottom,
                    prefs.usePanguSpacing,
                    prefs.bottomJustify,
                ) { flows: Array<Any> ->
                    Triple(
                        ReaderLayoutPrefs(
                            fontSize = flows[0] as Float,
                            lineSpacing = flows[1] as Float,
                            paragraphSpacing = flows[2] as Float,
                            indent = flows[3] as Float,
                            marginHorizontal = flows[4] as Float,
                            marginVertical = flows[5] as Float,
                            letterSpacing = flows[6] as Float,
                            useZhLayout = flows[7] as Boolean,
                            chineseConvert = (flows[8] as String).toChineseConvert(),
                            usePanguSpacing = flows[13] as Boolean,
                            bottomJustify = flows[14] as Boolean,
                        ),
                        TitleStyleConfig(
                            align = (flows[9] as String).toTitleAlign(),
                            sizeOffsetSp = flows[10] as Int,
                            marginTopDp = flows[11] as Float,
                            marginBottomDp = flows[12] as Float,
                        ),
                        flows[8] as String, // chineseConvert raw
                    )
                }.collectLatest { (layoutPrefs, titleStyle, chineseConvertRaw) ->
                    val current = uiState.value.readerPreferences
                    val updated = current.copy(
                        fontSize = layoutPrefs.fontSize,
                        lineSpacing = layoutPrefs.lineSpacing,
                        paragraphSpacing = layoutPrefs.paragraphSpacing,
                        indent = layoutPrefs.indent,
                        marginHorizontal = layoutPrefs.marginHorizontal,
                        marginVertical = layoutPrefs.marginVertical,
                        letterSpacing = layoutPrefs.letterSpacing,
                        useZhLayout = layoutPrefs.useZhLayout,
                        chineseConvert = chineseConvertRaw.toChineseConvert(),
                        usePanguSpacing = layoutPrefs.usePanguSpacing,
                        bottomJustify = layoutPrefs.bottomJustify,
                        titleStyle = titleStyle,
                    )
                    uiState.value = uiState.value.copy(readerPreferences = updated)
                    onReflowCurrentChapter?.invoke(updated)
                }
            }

            // Group B: 外观参数 → 变化时仅重绘（不 reflow）
            scope.launch {
                combine(
                    prefs.readingFont,
                    prefs.fontWeight,
                    prefs.textAlign,
                    prefs.defaultPageAnim,
                    prefs.headerVisibility,
                    prefs.headerLeft,
                    prefs.headerCenter,
                    prefs.headerRight,
                    prefs.footerVisibility,
                    prefs.footerLeft,
                    prefs.footerCenter,
                    prefs.footerRight,
                    prefs.headerFooterAlpha,
                    prefs.showProgress,
                    prefs.headerMarginTop,
                    prefs.footerMarginBottom,
                    prefs.showHeaderLine,
                    prefs.showFooterLine,
                    prefs.headerFontSizeRatio,
                    prefs.footerFontSizeRatio,
                ) { flows: Array<Any> ->
                    ReaderVisualPrefs(
                        readingFont = flows[0] as String,
                        fontWeight = (flows[1] as String).toFontWeight(),
                        textAlign = (flows[2] as String).toTextAlign(),
                        pageAnimType = (flows[3] as String).toPageAnimType(),
                        header = HeaderConfig(
                            visibility = (flows[4] as String).toHeaderVisibility(),
                            left = (flows[5] as String).toSlotContent(),
                            center = (flows[6] as String).toSlotContent(),
                            right = (flows[7] as String).toSlotContent(),
                            marginTop = flows[14] as Float,
                        ),
                        footer = FooterConfig(
                            visibility = (flows[8] as String).toHeaderVisibility(),
                            left = (flows[9] as String).toSlotContent(),
                            center = (flows[10] as String).toSlotContent(),
                            right = (flows[11] as String).toSlotContent(),
                            marginBottom = flows[15] as Float,
                        ),
                        headerFooterAlpha = flows[12] as Float,
                        showProgress = flows[13] as Boolean,
                        showHeaderLine = flows[16] as Boolean,
                        showFooterLine = flows[17] as Boolean,
                        headerFontSizeRatio = flows[18] as Float,
                        footerFontSizeRatio = flows[19] as Float,
                    )
                }.collectLatest { visual ->
                    val current = uiState.value.readerPreferences
                    val updated = current.copy(
                        readingFont = visual.readingFont,
                        fontWeight = visual.fontWeight,
                        textAlign = visual.textAlign,
                        pageAnimType = visual.pageAnimType,
                        header = visual.header,
                        footer = visual.footer,
                        headerFooterAlpha = visual.headerFooterAlpha,
                        showProgress = visual.showProgress,
                        showHeaderLine = visual.showHeaderLine,
                        showFooterLine = visual.showFooterLine,
                        headerFontSizeRatio = visual.headerFontSizeRatio,
                        footerFontSizeRatio = visual.footerFontSizeRatio,
                    )
                    val factoryType = visual.pageAnimType.toFactoryType()
                    uiState.value = uiState.value.copy(
                        readerPreferences = updated,
                        pageAnimType = factoryType,
                    )
                    onPageDelegateChanged?.invoke(PageDelegateFactory.create(factoryType))
                }
            }

            // Group C: 行为参数 → 变化时仅更新标志位（不重绘、不 reflow）
            scope.launch {
                combine(
                    prefs.brightness,
                    prefs.keepScreenOn,
                    prefs.volumeKeyTurnPage,
                    prefs.edgeTurnPage,
                    prefs.edgeWidthPercent,
                ) { flows: Array<Any> ->
                    ReaderBehaviorPrefs(
                        brightness = flows[0] as Float,
                        keepScreenOn = flows[1] as Boolean,
                        volumeKeyTurnPage = flows[2] as Boolean,
                        edgeTurnPage = flows[3] as Boolean,
                        edgeWidthPercent = flows[4] as Float,
                    )
                }.collectLatest { behavior ->
                    val current = uiState.value.readerPreferences
                    uiState.value = uiState.value.copy(
                        readerPreferences = current.copy(
                            brightness = behavior.brightness,
                            keepScreenOn = behavior.keepScreenOn,
                            volumeKeyTurnPage = behavior.volumeKeyTurnPage,
                            edgeTurnPage = behavior.edgeTurnPage,
                            edgeWidthPercent = behavior.edgeWidthPercent,
                        ),
                    )
                }
            }
        }
    }

    // ── 偏好设置通用更新辅助 ──────────────────────────────────────

    /** 更新 ReaderPreferences 并同步持久化，需要 reflow 时触发重排 */
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
        if (reflow) onReflowCurrentChapter?.invoke(updated)
        if (invalidate) onCurrentPageInvalidate?.invoke()
        scope.launch { userPreferences?.let { save(it) } }
    }

    // ── 主题 ──────────────────────────────────────────────────────

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

    // ── 排版参数 setter ───────────────────────────────────────────

    fun setFontSize(size: Float) {
        updatePrefs({ it.copy(fontSize = size) }, { it.setDefaultFontSize(size) }, reflow = true)
    }

    fun setLineSpacing(spacing: Float) {
        updatePrefs({ it.copy(lineSpacing = spacing) }, { it.setDefaultLineSpacing(spacing) }, reflow = true)
    }

    fun setParagraphSpacing(spacing: Float) {
        updatePrefs({ it.copy(paragraphSpacing = spacing) }, { it.setDefaultParagraphSpacing(spacing) }, reflow = true)
    }

    fun setIndent(indent: Float) {
        updatePrefs({ it.copy(indent = indent) }, { it.setDefaultIndent(indent) }, reflow = true)
    }

    fun setMarginHorizontal(margin: Float) {
        updatePrefs({ it.copy(marginHorizontal = margin) }, { it.setMarginHorizontal(margin) }, reflow = true)
    }

    fun setMarginVertical(margin: Float) {
        updatePrefs({ it.copy(marginVertical = margin) }, { it.setMarginVertical(margin) }, reflow = true)
    }

    fun setLetterSpacing(spacing: Float) {
        updatePrefs({ it.copy(letterSpacing = spacing) }, { it.setLetterSpacing(spacing) }, reflow = true)
    }

    fun setChineseConvert(convert: ChineseConvert) {
        updatePrefs({ it.copy(chineseConvert = convert) }, { it.setChineseConvert(convert.toStorageString()) }, reflow = true)
    }

    fun setUseZhLayout(enabled: Boolean) {
        updatePrefs({ it.copy(useZhLayout = enabled) }, { it.setUseZhLayout(enabled) }, reflow = true)
    }

    fun setPanguSpacing(enabled: Boolean) {
        updatePrefs({ it.copy(usePanguSpacing = enabled) }, { it.setUsePanguSpacing(enabled) }, reflow = true)
    }

    fun setBottomJustify(enabled: Boolean) {
        updatePrefs({ it.copy(bottomJustify = enabled) }, { it.setBottomJustify(enabled) }, reflow = true)
    }

    // ── 外观参数 setter ───────────────────────────────────────────

    fun setReadingFont(font: String) {
        updatePrefs({ it.copy(readingFont = font) }, { it.setReadingFont(font) })
    }

    fun setFontWeight(weight: ReaderFontWeight) {
        updatePrefs({ it.copy(fontWeight = weight) }, { it.setFontWeight(weight.toStorageString()) }, invalidate = true)
    }

    fun setTextAlign(align: ReaderTextAlign) {
        updatePrefs({ it.copy(textAlign = align) }, { it.setTextAlign(align.toStorageString()) }, invalidate = true)
    }

    fun setPageAnimType(type: PageDelegateFactory.PageAnimType) {
        uiState.value = uiState.value.copy(pageAnimType = type)
        onPageDelegateChanged?.invoke(PageDelegateFactory.create(type))
    }

    // ── 行为参数 setter ───────────────────────────────────────────

    fun setBrightness(brightness: Float, finished: Boolean = false) {
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

    // ── TTS 设置 ──────────────────────────────────────────────────

    fun setTtsSpeed(speed: Float) {
        updatePrefs({ it.copy(ttsSpeed = speed) }, { it.setTtsSpeed(speed) })
    }

    fun setTtsPitch(pitch: Float) {
        updatePrefs({ it.copy(ttsPitch = pitch) }, { it.setTtsPitch(pitch) })
    }

    // ── 页眉页脚 ──────────────────────────────────────────────────

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

    fun setHeaderMarginTop(margin: Float) {
        updatePrefs(
            { it.copy(header = it.header.copy(marginTop = margin)) },
            { it.setHeaderMarginTop(margin) },
        )
    }

    fun setFooterMarginBottom(margin: Float) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(marginBottom = margin)) },
            { it.setFooterMarginBottom(margin) },
        )
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

    // ── 预设应用 ──────────────────────────────────────────────────

    /** 将一组偏好依次通过 setter 应用（由 presetManager.onApplyPreferences 回调） */
    fun applyAllPreferences(prefs: ReaderPreferences) {
        setFontSize(prefs.fontSize)
        setLineSpacing(prefs.lineSpacing)
        setParagraphSpacing(prefs.paragraphSpacing)
        setIndent(prefs.indent)
        setMarginHorizontal(prefs.marginHorizontal)
        setMarginVertical(prefs.marginVertical)
        setReadingFont(prefs.readingFont)
        setPageAnimType(prefs.pageAnimType.toFactoryType())
        setReaderTheme(prefs.backgroundColor)
        setLetterSpacing(prefs.letterSpacing)
        setFontWeight(prefs.fontWeight)
        setTextAlign(prefs.textAlign)
        setChineseConvert(prefs.chineseConvert)
        setBrightness(prefs.brightness)
        setHeaderVisibility(prefs.header.visibility)
        setHeaderLeft(prefs.header.left)
        setHeaderCenter(prefs.header.center)
        setHeaderRight(prefs.header.right)
        setFooterVisibility(prefs.footer.visibility)
        setFooterLeft(prefs.footer.left)
        setFooterCenter(prefs.footer.center)
        setFooterRight(prefs.footer.right)
        setHeaderFooterAlpha(prefs.headerFooterAlpha)
        setShowProgress(prefs.showProgress)
        setTitleAlign(prefs.titleStyle.align)
        setTitleSizeOffset(prefs.titleStyle.sizeOffsetSp)
        setTitleMarginTop(prefs.titleStyle.marginTopDp)
        setTitleMarginBottom(prefs.titleStyle.marginBottomDp)
        setKeepScreenOn(prefs.keepScreenOn)
        setVolumeKeyTurnPage(prefs.volumeKeyTurnPage)
        setEdgeTurnPage(prefs.edgeTurnPage)
        setEdgeWidthPercent(prefs.edgeWidthPercent)
        setShowHeaderLine(prefs.showHeaderLine)
        setShowFooterLine(prefs.showFooterLine)
        setHeaderFontSizeRatio(prefs.headerFontSizeRatio)
        setFooterFontSizeRatio(prefs.footerFontSizeRatio)
        setBottomJustify(prefs.bottomJustify)
    }
}
