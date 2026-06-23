package com.shuli.reader.feature.reader.settings
import com.shuli.reader.feature.reader.screen.ReaderUiState

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.IndentUnit
import com.shuli.reader.core.data.PageAnimSpeed
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.data.toChineseConvert
import com.shuli.reader.core.data.toFactoryType
import com.shuli.reader.core.data.toIndentUnit
import com.shuli.reader.core.data.toFontWeight
import com.shuli.reader.core.data.toHeaderVisibility
import com.shuli.reader.core.data.toPageAnimSpeed
import com.shuli.reader.core.data.toPageAnimType
import com.shuli.reader.core.data.toSlotContent
import com.shuli.reader.core.data.toTextAlign
import com.shuli.reader.core.data.toTitleAlign
import com.shuli.reader.core.reader.model.BoxInsetsDp
import com.shuli.reader.core.reader.model.FooterConfig
import com.shuli.reader.core.reader.model.HeaderConfig
import com.shuli.reader.core.reader.model.TitleStyleConfig
import com.shuli.reader.core.reader.engine.animation.PageDelegate
import com.shuli.reader.core.reader.engine.animation.PageDelegateFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 阅读器偏好监控：监听 UserPreferences 变化并更新 ReaderUiState。
 *
 * 从 ReaderViewModel 拆出，SRP —— 只负责"偏好变化监听与状态同步"这一变更轴。
 * 三组偏好：A-排版(reflow) / B-外观(重绘) / C-行为(标志位)。
 */
internal class ReaderPreferenceMonitor(
    private val userPreferences: UserPreferences,
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val reflowCurrentChapter: (ReaderPreferences) -> Unit,
    private val onPageAnimTypeChanged: (PageDelegate) -> Unit,
) {

    fun start() {
        monitorLayoutPrefs()
        monitorVisualPrefs()
        monitorBehaviorPrefs()
    }

    /**
     * 在 openBook 之前同步读取 DataStore 偏好，确保 uiState 反映用户保存的值。
     * 解决：combine 首射异步到达前 openBook 已用默认值分页的竞态。
     */
    suspend fun syncFromDataStore() {
        val prefs = userPreferences
        val current = uiState.value.readerPreferences
        val pageAnimType = prefs.defaultPageAnim.first().toPageAnimType()
        val pageAnimSpeed = prefs.pageAnimSpeed.first().toPageAnimSpeed()
        uiState.value = uiState.value.copy(
            readerPreferences = current.copy(
                // Group A
                fontSize = prefs.defaultFontSize.first(),
                lineSpacing = prefs.defaultLineSpacing.first(),
                paragraphSpacing = prefs.defaultParagraphSpacing.first(),
                indent = prefs.defaultIndent.first(),
                indentUnit = prefs.indentUnit.first().toIndentUnit(),
                bodyBox = BoxInsetsDp(
                    top = prefs.marginVertical.first(),
                    bottom = prefs.marginVertical.first(),
                    left = prefs.marginHorizontal.first(),
                    right = prefs.marginHorizontal.first(),
                ),
                letterSpacing = prefs.letterSpacing.first(),
                useZhLayout = prefs.useZhLayout.first(),
                chineseConvert = prefs.chineseConvert.first().toChineseConvert(),
                usePanguSpacing = prefs.usePanguSpacing.first(),
                bottomJustify = prefs.bottomJustify.first(),
                titleStyle = TitleStyleConfig(
                    align = prefs.titleAlign.first().toTitleAlign(),
                    sizeOffsetSp = prefs.titleSizeOffset.first(),
                    marginTopDp = prefs.titleMarginTop.first(),
                    marginBottomDp = prefs.titleMarginBottom.first(),
                ),
                // Group B
                readingFont = prefs.readingFont.first(),
                fontWeight = prefs.fontWeight.first().toFontWeight(),
                textAlign = prefs.textAlign.first().toTextAlign(),
                pageAnimType = pageAnimType,
                pageAnimSpeed = pageAnimSpeed,
                header = HeaderConfig(
                    visibility = prefs.headerVisibility.first().toHeaderVisibility(),
                    left = prefs.headerLeft.first().toSlotContent(),
                    center = prefs.headerCenter.first().toSlotContent(),
                    right = prefs.headerRight.first().toSlotContent(),
                ),
                headerBox = BoxInsetsDp(top = prefs.headerMarginTop.first(), bottom = 0f, left = 24f, right = 24f),
                footer = FooterConfig(
                    visibility = prefs.footerVisibility.first().toHeaderVisibility(),
                    left = prefs.footerLeft.first().toSlotContent(),
                    center = prefs.footerCenter.first().toSlotContent(),
                    right = prefs.footerRight.first().toSlotContent(),
                ),
                footerBox = BoxInsetsDp(top = 0f, bottom = prefs.footerMarginBottom.first(), left = 24f, right = 24f),
                headerFooterAlpha = prefs.headerFooterAlpha.first(),
                showProgress = prefs.showProgress.first(),
                showHeaderLine = prefs.showHeaderLine.first(),
                showFooterLine = prefs.showFooterLine.first(),
                headerFontSizeRatio = prefs.headerFontSizeRatio.first(),
                footerFontSizeRatio = prefs.footerFontSizeRatio.first(),
                // Group C
                brightness = prefs.brightness.first(),
                keepScreenOn = prefs.keepScreenOn.first(),
                volumeKeyTurnPage = prefs.volumeKeyTurnPage.first(),
                edgeTurnPage = prefs.edgeTurnPage.first(),
                edgeWidthPercent = prefs.edgeWidthPercent.first(),
            ),
            pageAnimType = pageAnimType.toFactoryType(),
        )
    }

    /** Group A: 排版参数 → 变化时需 reflow（重新分页） */
    private fun monitorLayoutPrefs() {
        scope.launch {
            combine(
                userPreferences.defaultFontSize,
                userPreferences.defaultLineSpacing,
                userPreferences.defaultParagraphSpacing,
                userPreferences.defaultIndent,
                userPreferences.indentUnit,
                userPreferences.marginHorizontal,
                userPreferences.marginVertical,
                userPreferences.letterSpacing,
                userPreferences.useZhLayout,
                userPreferences.chineseConvert,
                userPreferences.titleAlign,
                userPreferences.titleSizeOffset,
                userPreferences.titleMarginTop,
                userPreferences.titleMarginBottom,
                userPreferences.usePanguSpacing,
                userPreferences.bottomJustify,
            ) { flows: Array<Any> ->
                val mh = flows[5] as Float
                val mv = flows[6] as Float
                Triple(
                    ReaderLayoutPrefs(
                        fontSize = flows[0] as Float,
                        lineSpacing = flows[1] as Float,
                        paragraphSpacing = flows[2] as Float,
                        indent = flows[3] as Float,
                        indentUnit = (flows[4] as String).toIndentUnit(),
                        bodyBox = BoxInsetsDp(top = mv, bottom = mv, left = mh, right = mh),
                        letterSpacing = flows[7] as Float,
                        useZhLayout = flows[8] as Boolean,
                        chineseConvert = (flows[9] as String).toChineseConvert(),
                        usePanguSpacing = flows[14] as Boolean,
                        bottomJustify = flows[15] as Boolean,
                    ),
                    TitleStyleConfig(
                        align = (flows[10] as String).toTitleAlign(),
                        sizeOffsetSp = flows[11] as Int,
                        marginTopDp = flows[12] as Float,
                        marginBottomDp = flows[13] as Float,
                    ),
                    flows[9] as String, // chineseConvert raw
                )
            }
                .collectLatest { (layoutPrefs, titleStyle, chineseConvertRaw) ->
                    val current = uiState.value.readerPreferences
                    val updated = current.copy(
                        fontSize = layoutPrefs.fontSize,
                        lineSpacing = layoutPrefs.lineSpacing,
                        paragraphSpacing = layoutPrefs.paragraphSpacing,
                        indent = layoutPrefs.indent,
                        indentUnit = layoutPrefs.indentUnit,
                        bodyBox = layoutPrefs.bodyBox,
                        letterSpacing = layoutPrefs.letterSpacing,
                        useZhLayout = layoutPrefs.useZhLayout,
                        chineseConvert = chineseConvertRaw.toChineseConvert(),
                        usePanguSpacing = layoutPrefs.usePanguSpacing,
                        bottomJustify = layoutPrefs.bottomJustify,
                        titleStyle = titleStyle,
                    )
                    uiState.value = uiState.value.copy(readerPreferences = updated)
                    reflowCurrentChapter(updated)
                }
        }
    }

    /** Group B: 外观参数 → 变化时仅重绘（不 reflow） */
    private fun monitorVisualPrefs() {
        scope.launch {
            combine(
                userPreferences.readingFont,
                userPreferences.fontWeight,
                userPreferences.textAlign,
                userPreferences.defaultPageAnim,
                userPreferences.pageAnimSpeed,
                userPreferences.headerVisibility,
                userPreferences.headerLeft,
                userPreferences.headerCenter,
                userPreferences.headerRight,
                userPreferences.footerVisibility,
                userPreferences.footerLeft,
                userPreferences.footerCenter,
                userPreferences.footerRight,
                userPreferences.headerFooterAlpha,
                userPreferences.showProgress,
                userPreferences.headerMarginTop,
                userPreferences.footerMarginBottom,
                userPreferences.showHeaderLine,
                userPreferences.showFooterLine,
                userPreferences.headerFontSizeRatio,
                userPreferences.footerFontSizeRatio,
            ) { flows: Array<Any> ->
                ReaderVisualPrefs(
                    readingFont = flows[0] as String,
                    fontWeight = (flows[1] as String).toFontWeight(),
                    textAlign = (flows[2] as String).toTextAlign(),
                    pageAnimType = (flows[3] as String).toPageAnimType(),
                    pageAnimSpeed = (flows[4] as String).toPageAnimSpeed(),
                    header = HeaderConfig(
                        visibility = (flows[5] as String).toHeaderVisibility(),
                        left = (flows[6] as String).toSlotContent(),
                        center = (flows[7] as String).toSlotContent(),
                        right = (flows[8] as String).toSlotContent(),
                    ),
                    headerBox = BoxInsetsDp(top = flows[15] as Float, bottom = 0f, left = 24f, right = 24f),
                    footer = FooterConfig(
                        visibility = (flows[9] as String).toHeaderVisibility(),
                        left = (flows[10] as String).toSlotContent(),
                        center = (flows[11] as String).toSlotContent(),
                        right = (flows[12] as String).toSlotContent(),
                    ),
                    footerBox = BoxInsetsDp(top = 0f, bottom = flows[16] as Float, left = 24f, right = 24f),
                    headerFooterAlpha = flows[13] as Float,
                    showProgress = flows[14] as Boolean,
                    showHeaderLine = flows[17] as Boolean,
                    showFooterLine = flows[18] as Boolean,
                    headerFontSizeRatio = flows[19] as Float,
                    footerFontSizeRatio = flows[20] as Float,
                )
            }
                .collectLatest { visual ->
                    val current = uiState.value.readerPreferences
                    val updated = current.copy(
                        readingFont = visual.readingFont,
                        fontWeight = visual.fontWeight,
                        textAlign = visual.textAlign,
                        pageAnimType = visual.pageAnimType,
                        pageAnimSpeed = visual.pageAnimSpeed,
                        header = visual.header,
                        headerBox = visual.headerBox,
                        footer = visual.footer,
                        footerBox = visual.footerBox,
                        headerFooterAlpha = visual.headerFooterAlpha,
                        showProgress = visual.showProgress,
                        showHeaderLine = visual.showHeaderLine,
                        showFooterLine = visual.showFooterLine,
                        headerFontSizeRatio = visual.headerFontSizeRatio,
                        footerFontSizeRatio = visual.footerFontSizeRatio,
                    )
                    val factoryType = visual.pageAnimType.toFactoryType()
                    // 仅当 pageAnimType 真正变化时才重建 PageDelegate，
                    // 避免 combine 每次发射（含 syncFromDataStore 后的首射与值相同的发射）
                    // 都替换 delegate，从而打断翻页动画 / 丢失已注册的 Callback。
                    val animChanged = uiState.value.pageAnimType != factoryType ||
                        uiState.value.readerPreferences.pageAnimSpeed != visual.pageAnimSpeed
                    uiState.value = uiState.value.copy(
                        readerPreferences = updated,
                        pageAnimType = factoryType,
                    )
                    if (animChanged) {
                        onPageAnimTypeChanged(PageDelegateFactory.create(factoryType, visual.pageAnimSpeed))
                    }
                }
        }
    }

    /** Group C: 行为参数 → 变化时仅更新标志位（不重绘、不 reflow） */
    private fun monitorBehaviorPrefs() {
        scope.launch {
            combine(
                userPreferences.brightness,
                userPreferences.colorTemperature,
                userPreferences.keepScreenOn,
                userPreferences.volumeKeyTurnPage,
                userPreferences.edgeTurnPage,
                userPreferences.edgeWidthPercent,
            ) { flows: Array<Any> ->
                ReaderBehaviorPrefs(
                    brightness = flows[0] as Float,
                    colorTemperature = flows[1] as Float,
                    keepScreenOn = flows[2] as Boolean,
                    volumeKeyTurnPage = flows[3] as Boolean,
                    edgeTurnPage = flows[4] as Boolean,
                    edgeWidthPercent = flows[5] as Float,
                )
            }
                .collectLatest { behavior ->
                    val current = uiState.value.readerPreferences
                    uiState.value = uiState.value.copy(
                        readerPreferences = current.copy(
                            brightness = behavior.brightness,
                            colorTemperature = behavior.colorTemperature,
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

/**
 * 排版参数（变化时需 reflow）
 */
internal data class ReaderLayoutPrefs(
    val fontSize: Float,
    val lineSpacing: Float,
    val paragraphSpacing: Float,
    val indent: Float,
    val indentUnit: IndentUnit,
    val bodyBox: BoxInsetsDp,
    val letterSpacing: Float,
    val useZhLayout: Boolean,
    val chineseConvert: ChineseConvert,
    val usePanguSpacing: Boolean,
    val bottomJustify: Boolean,
)

/**
 * 外观参数（变化时仅重绘）
 */
internal data class ReaderVisualPrefs(
    val readingFont: String,
    val fontWeight: ReaderFontWeight,
    val textAlign: ReaderTextAlign,
    val pageAnimType: com.shuli.reader.core.data.PageAnimType,
    val pageAnimSpeed: PageAnimSpeed,
    val header: HeaderConfig,
    val headerBox: BoxInsetsDp,
    val footer: FooterConfig,
    val footerBox: BoxInsetsDp,
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
internal data class ReaderBehaviorPrefs(
    val brightness: Float,
    val colorTemperature: Float,
    val keepScreenOn: Boolean,
    val volumeKeyTurnPage: Boolean,
    val edgeTurnPage: Boolean,
    val edgeWidthPercent: Float,
)
