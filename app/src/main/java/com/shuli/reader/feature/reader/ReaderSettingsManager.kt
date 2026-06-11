package com.shuli.reader.feature.reader

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.IndentUnit
import com.shuli.reader.core.data.ProgressStyle
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.ReaderTextAlign
import com.shuli.reader.core.data.ReaderTheme
import com.shuli.reader.core.data.UserPreferences
import com.shuli.reader.core.data.toStorageString
import com.shuli.reader.core.database.dao.BookReaderPrefsDao
import com.shuli.reader.core.database.entity.BookReaderPrefsEntity
import com.shuli.reader.core.database.entity.BookReaderPrefsOverrides
import com.shuli.reader.core.reader.HeaderVisibility
import com.shuli.reader.core.reader.SlotContent
import com.shuli.reader.core.reader.TitleAlign
import com.shuli.reader.feature.reader.settings.GestureConfig
import com.shuli.reader.ui.theme.resolveCustomColorScheme
import com.shuli.reader.ui.theme.toCanvasThemeColors
import com.shuli.reader.ui.theme.toReaderColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 阅读器偏好设置管理：所有 setXxx 偏好方法 + updatePrefs 通用更新。
 *
 * 从 ReaderViewModel 拆出，SRP —— 只负责"偏好读写与持久化"这一变更轴。
 * 支持两级作用域：GLOBAL（全局默认）和 BOOK（本书覆盖）。
 */
internal class ReaderSettingsManager(
    private val uiState: MutableStateFlow<ReaderUiState>,
    private val scope: CoroutineScope,
    private val userPreferences: UserPreferences?,
    private val bookReaderPrefsDao: BookReaderPrefsDao? = null,
    // ── 回调 ──
    private val reflowCurrentChapter: (ReaderPreferences) -> Unit,
    private val resetToolbarAutoHide: () -> Unit,
) {

    private val json = kotlinx.serialization.json.Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // ── 作用域管理 ──────────────────────────────────────────────

    /** 加载本书覆盖并合并到当前偏好（打开书时调用） */
    fun loadBookOverrides(bookId: Long) {
        val dao = bookReaderPrefsDao ?: return
        scope.launch {
            val entity = dao.getByBookId(bookId)
            val hasOverrides = entity != null
            val globalPrefs = uiState.value.readerPreferences
            val resolved = if (entity != null) {
                try {
                    val overrides = json.decodeFromString(BookReaderPrefsOverrides.serializer(), entity.configJson)
                    overrides.mergeOnto(globalPrefs)
                } catch (_: Exception) {
                    globalPrefs
                }
            } else {
                globalPrefs
            }
            uiState.value = uiState.value.copy(
                readerPreferences = resolved,
                hasBookOverrides = hasOverrides,
            )
        }
    }

    /** 切换设置作用域 */
    fun setSettingsScope(newScope: SettingsScope) {
        uiState.value = uiState.value.copy(settingsScope = newScope)
    }

    /** 将当前全局设置保存为本书覆盖（从 GLOBAL 切换到 BOOK 时） */
    fun copyGlobalToBook() {
        val dao = bookReaderPrefsDao ?: return
        val bookId = uiState.value.bookId
        if (bookId == 0L) return
        scope.launch {
            // 当前 prefs 就是全局值（因为还没有 book override）
            val prefs = uiState.value.readerPreferences
            val overrides = prefsToOverrides(prefs)
            val configJson = json.encodeToString(BookReaderPrefsOverrides.serializer(), overrides)
            dao.upsert(BookReaderPrefsEntity(bookId = bookId, configJson = configJson))
            uiState.value = uiState.value.copy(
                settingsScope = SettingsScope.BOOK,
                hasBookOverrides = true,
            )
        }
    }

    /** 清除本书覆盖，回退到全局默认 */
    fun resetBookOverrides() {
        val dao = bookReaderPrefsDao ?: return
        val bookId = uiState.value.bookId
        if (bookId == 0L) return
        scope.launch {
            dao.deleteByBookId(bookId)
            // 重新加载全局默认
            val globalPrefs = loadGlobalPreferences()
            uiState.value = uiState.value.copy(
                readerPreferences = globalPrefs,
                hasBookOverrides = false,
                settingsScope = SettingsScope.GLOBAL,
            )
            reflowCurrentChapter(globalPrefs)
        }
    }

    /** 从 UserPreferences 加载全局默认 ReaderPreferences */
    private suspend fun loadGlobalPreferences(): ReaderPreferences {
        // 使用默认值（DataStore 的 flow 已在 preferenceMonitor 中处理）
        return ReaderPreferences()
    }

    /** 将 ReaderPreferences 转为 BookReaderPrefsOverrides（全量覆盖） */
    private fun prefsToOverrides(p: ReaderPreferences): BookReaderPrefsOverrides {
        return BookReaderPrefsOverrides(
            fontSize = p.fontSize,
            lineSpacing = p.lineSpacing,
            paragraphSpacing = p.paragraphSpacing,
            indent = p.indent,
            indentUnit = p.indentUnit.toStorageString(),
            marginHorizontal = p.marginHorizontal,
            marginVertical = p.marginVertical,
            marginTop = p.marginTop,
            marginBottom = p.marginBottom,
            marginLeft = p.marginLeft,
            marginRight = p.marginRight,
            letterSpacing = p.letterSpacing,
            wordSpacing = p.wordSpacing,
            paragraphDivider = p.paragraphDivider,
            readingFont = p.readingFont,
            fontWeight = p.fontWeight.toStorageString(),
            textAlign = p.textAlign.toStorageString(),
            chineseConvert = p.chineseConvert.toStorageString(),
            useZhLayout = p.useZhLayout,
            usePanguSpacing = p.usePanguSpacing,
            bionicReading = p.bionicReading,
            bottomJustify = p.bottomJustify,
            maxPageWidth = p.maxPageWidth,
            removeEmptyLines = p.removeEmptyLines,
            cleanChapterTitle = p.cleanChapterTitle,
            adFiltering = p.adFiltering,
            backgroundColor = p.backgroundColor.name,
            customBackgroundColor = p.customBackgroundColor,
            customTextColor = p.customTextColor,
            customAccentColor = p.customAccentColor,
            brightness = p.brightness,
            colorTemperature = p.colorTemperature,
            backgroundTexture = p.backgroundTexture,
            headerVisibility = p.header.visibility.toStorageString(),
            headerLeft = p.header.left.toStorageString(),
            headerCenter = p.header.center.toStorageString(),
            headerRight = p.header.right.toStorageString(),
            footerVisibility = p.footer.visibility.toStorageString(),
            footerLeft = p.footer.left.toStorageString(),
            footerCenter = p.footer.center.toStorageString(),
            footerRight = p.footer.right.toStorageString(),
            headerFooterAlpha = p.headerFooterAlpha,
            headerMarginTop = p.header.marginTop,
            footerMarginBottom = p.footer.marginBottom,
            showProgress = p.showProgress,
            progressStyle = p.progressStyle.toStorageString(),
            titleAlign = p.titleStyle.align.toStorageString(),
            titleSizeOffset = p.titleStyle.sizeOffsetSp,
            titleMarginTop = p.titleStyle.marginTopDp,
            titleMarginBottom = p.titleStyle.marginBottomDp,
            showHeaderLine = p.showHeaderLine,
            showFooterLine = p.showFooterLine,
            headerFontSizeRatio = p.headerFontSizeRatio,
            footerFontSizeRatio = p.footerFontSizeRatio,
            keepScreenOn = p.keepScreenOn,
            volumeKeyTurnPage = p.volumeKeyTurnPage,
            edgeTurnPage = p.edgeTurnPage,
            edgeWidthPercent = p.edgeWidthPercent,
            immersiveMode = p.immersiveMode,
            leftZoneRatio = p.leftZoneRatio,
            autoPageTurn = p.autoPageTurn,
            autoPageTurnInterval = p.autoPageTurnInterval,
            autoNightMode = p.autoNightMode,
            epubOverrideStyle = p.epubOverrideStyle,
            hapticFeedback = p.hapticFeedback,
            orientationLock = p.orientationLock.name,
            pageAnimType = p.pageAnimType.name,
            pageAnimSpeed = p.pageAnimSpeed.name,
            verticalText = p.verticalText,
            dualPageMode = p.dualPageMode.name,
            focusLine = p.focusLine,
            eyeCareReminderInterval = p.eyeCareReminderInterval,
            ttsSpeed = p.ttsSpeed,
            ttsPitch = p.ttsPitch,
            ttsVoice = p.ttsVoice,
            ttsAutoPage = p.ttsAutoPage,
            ttsTimer = p.ttsTimer,
        )
    }

    /** 保存单个字段到本书覆盖 */
    private fun saveBookOverrideField(bookId: Long, transform: (BookReaderPrefsOverrides) -> BookReaderPrefsOverrides) {
        val dao = bookReaderPrefsDao ?: return
        scope.launch {
            val existing = dao.getByBookId(bookId)
            val current = if (existing != null) {
                try {
                    json.decodeFromString(BookReaderPrefsOverrides.serializer(), existing.configJson)
                } catch (_: Exception) {
                    BookReaderPrefsOverrides()
                }
            } else {
                BookReaderPrefsOverrides()
            }
            val updated = transform(current)
            val configJson = json.encodeToString(BookReaderPrefsOverrides.serializer(), updated)
            dao.upsert(BookReaderPrefsEntity(bookId = bookId, configJson = configJson))
            uiState.value = uiState.value.copy(hasBookOverrides = true)
        }
    }

    // ── 主题 ──────────────────────────────────────────────

    fun setReaderTheme(theme: ReaderTheme) {
        val currentPrefs = uiState.value.readerPreferences
        val newPrefs = currentPrefs.copy(backgroundColor = theme)
        uiState.value = uiState.value.copy(
            readerPreferences = newPrefs,
            themeColors = resolveThemeColors(newPrefs),
        )
    }

    /** 更新自定义主题颜色并刷新 */
    fun setCustomThemeColor(
        backgroundColor: Int? = uiState.value.readerPreferences.customBackgroundColor,
        textColor: Int? = uiState.value.readerPreferences.customTextColor,
        accentColor: Int? = uiState.value.readerPreferences.customAccentColor,
    ) {
        val currentPrefs = uiState.value.readerPreferences
        val newPrefs = currentPrefs.copy(
            backgroundColor = ReaderTheme.CUSTOM,
            customBackgroundColor = backgroundColor,
            customTextColor = textColor,
            customAccentColor = accentColor,
        )
        uiState.value = uiState.value.copy(
            readerPreferences = newPrefs,
            themeColors = resolveThemeColors(newPrefs),
        )
        // 持久化
        scope.launch { userPreferences?.setCustomBackgroundColor(backgroundColor) }
        scope.launch { userPreferences?.setCustomTextColor(textColor) }
        scope.launch { userPreferences?.setCustomAccentColor(accentColor) }
        scope.launch { userPreferences?.setBackgroundColor(ReaderTheme.CUSTOM.name) }
    }

    fun cycleTheme() {
        val current = uiState.value.readerPreferences.backgroundColor
        val next = when (current) {
            ReaderTheme.LIGHT -> ReaderTheme.DARK
            ReaderTheme.DARK -> ReaderTheme.PAPER
            ReaderTheme.PAPER -> ReaderTheme.LIGHT
            ReaderTheme.OLED -> ReaderTheme.PAPER
            ReaderTheme.CUSTOM -> ReaderTheme.LIGHT
        }
        setReaderTheme(next)
    }

    // ── 排版参数（reflow） ──────────────────────────────────────

    fun setFontSize(size: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(fontSize = size) }, { it.setDefaultFontSize(size) },
            bookOverride = { o -> o.copy(fontSize = size) }, reflow = true)
    }

    fun setLineSpacing(spacing: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(lineSpacing = spacing) }, { it.setDefaultLineSpacing(spacing) },
            bookOverride = { o -> o.copy(lineSpacing = spacing) }, reflow = true)
    }

    fun setParagraphSpacing(spacing: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(paragraphSpacing = spacing) }, { it.setDefaultParagraphSpacing(spacing) },
            bookOverride = { o -> o.copy(paragraphSpacing = spacing) }, reflow = true)
    }

    fun setIndent(indent: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(indent = indent) }, { it.setDefaultIndent(indent) },
            bookOverride = { o -> o.copy(indent = indent) }, reflow = true)
    }

    fun setIndentUnit(unit: IndentUnit) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(indentUnit = unit) }, { it.setIndentUnit(unit.toStorageString()) },
            bookOverride = { o -> o.copy(indentUnit = unit.toStorageString()) }, reflow = true)
    }

    fun setMarginHorizontal(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(marginHorizontal = margin) }, { it.setMarginHorizontal(margin) },
            bookOverride = { o -> o.copy(marginHorizontal = margin) }, reflow = true)
    }

    fun setMarginVertical(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(marginVertical = margin) }, { it.setMarginVertical(margin) },
            bookOverride = { o -> o.copy(marginVertical = margin) }, reflow = true)
    }

    fun setLetterSpacing(spacing: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(letterSpacing = spacing) }, { it.setLetterSpacing(spacing) },
            bookOverride = { o -> o.copy(letterSpacing = spacing) }, reflow = true)
    }

    fun setChineseConvert(convert: ChineseConvert) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(chineseConvert = convert) }, { it.setChineseConvert(convert.toStorageString()) },
            bookOverride = { o -> o.copy(chineseConvert = convert.toStorageString()) }, reflow = true)
    }

    fun setUseZhLayout(enabled: Boolean) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(useZhLayout = enabled) }, { it.setUseZhLayout(enabled) },
            bookOverride = { o -> o.copy(useZhLayout = enabled) }, reflow = true)
    }

    fun setPanguSpacing(enabled: Boolean) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(usePanguSpacing = enabled) }, { it.setUsePanguSpacing(enabled) },
            bookOverride = { o -> o.copy(usePanguSpacing = enabled) }, reflow = true)
    }

    fun setBottomJustify(enabled: Boolean) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(bottomJustify = enabled) }, { it.setBottomJustify(enabled) },
            bookOverride = { o -> o.copy(bottomJustify = enabled) }, reflow = true)
    }

    // ── 外观参数（仅重绘） ──────────────────────────────────────

    fun setReadingFont(font: String) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(readingFont = font) }, { it.setReadingFont(font) },
            bookOverride = { o -> o.copy(readingFont = font) })
    }

    fun setFontWeight(weight: ReaderFontWeight) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(fontWeight = weight) }, { it.setFontWeight(weight.toStorageString()) },
            bookOverride = { o -> o.copy(fontWeight = weight.toStorageString()) })
    }

    fun setTextAlign(align: ReaderTextAlign) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(textAlign = align) }, { it.setTextAlign(align.toStorageString()) },
            bookOverride = { o -> o.copy(textAlign = align.toStorageString()) })
    }

    // ── 亮度 ──────────────────────────────────────────────

    fun setBrightness(brightness: Float, finished: Boolean = false) {
        resetToolbarAutoHide()
        updatePrefs(
            { it.copy(brightness = brightness) },
            { if (finished) it.setBrightness(brightness) },
            bookOverride = { o -> o.copy(brightness = brightness) },
        )
    }

    // ── 页眉页脚 ──────────────────────────────────────────────

    fun setHeaderMarginTop(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs(
            { it.copy(header = it.header.copy(marginTop = margin)) },
            { it.setHeaderMarginTop(margin) },
            bookOverride = { o -> o.copy(headerMarginTop = margin) },
        )
    }

    fun setFooterMarginBottom(margin: Float) {
        resetToolbarAutoHide()
        updatePrefs(
            { it.copy(footer = it.footer.copy(marginBottom = margin)) },
            { it.setFooterMarginBottom(margin) },
            bookOverride = { o -> o.copy(footerMarginBottom = margin) },
        )
    }

    fun setHeaderVisibility(visibility: HeaderVisibility) {
        updatePrefs(
            { it.copy(header = it.header.copy(visibility = visibility)) },
            { it.setHeaderVisibility(visibility.toStorageString()) },
            bookOverride = { o -> o.copy(headerVisibility = visibility.toStorageString()) },
            reflow = true,
        )
    }

    fun setHeaderLeft(slot: SlotContent) {
        updatePrefs(
            { it.copy(header = it.header.copy(left = slot)) },
            { it.setHeaderLeft(slot.toStorageString()) },
            bookOverride = { o -> o.copy(headerLeft = slot.toStorageString()) },
        )
    }

    fun setHeaderCenter(slot: SlotContent) {
        updatePrefs(
            { it.copy(header = it.header.copy(center = slot)) },
            { it.setHeaderCenter(slot.toStorageString()) },
            bookOverride = { o -> o.copy(headerCenter = slot.toStorageString()) },
        )
    }

    fun setHeaderRight(slot: SlotContent) {
        updatePrefs(
            { it.copy(header = it.header.copy(right = slot)) },
            { it.setHeaderRight(slot.toStorageString()) },
            bookOverride = { o -> o.copy(headerRight = slot.toStorageString()) },
        )
    }

    fun setFooterVisibility(visibility: HeaderVisibility) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(visibility = visibility)) },
            { it.setFooterVisibility(visibility.toStorageString()) },
            bookOverride = { o -> o.copy(footerVisibility = visibility.toStorageString()) },
            reflow = true,
        )
    }

    fun setFooterLeft(slot: SlotContent) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(left = slot)) },
            { it.setFooterLeft(slot.toStorageString()) },
            bookOverride = { o -> o.copy(footerLeft = slot.toStorageString()) },
        )
    }

    fun setFooterCenter(slot: SlotContent) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(center = slot)) },
            { it.setFooterCenter(slot.toStorageString()) },
            bookOverride = { o -> o.copy(footerCenter = slot.toStorageString()) },
        )
    }

    fun setFooterRight(slot: SlotContent) {
        updatePrefs(
            { it.copy(footer = it.footer.copy(right = slot)) },
            { it.setFooterRight(slot.toStorageString()) },
            bookOverride = { o -> o.copy(footerRight = slot.toStorageString()) },
        )
    }

    fun setHeaderFooterAlpha(alpha: Float) {
        updatePrefs({ it.copy(headerFooterAlpha = alpha) }, { it.setHeaderFooterAlpha(alpha) },
            bookOverride = { o -> o.copy(headerFooterAlpha = alpha) })
    }

    fun setShowProgress(show: Boolean) {
        updatePrefs({ it.copy(showProgress = show) }, { it.setShowProgress(show) },
            bookOverride = { o -> o.copy(showProgress = show) })
    }

    fun setShowHeaderLine(show: Boolean) {
        updatePrefs({ it.copy(showHeaderLine = show) }, { it.setShowHeaderLine(show) },
            bookOverride = { o -> o.copy(showHeaderLine = show) })
    }

    fun setShowFooterLine(show: Boolean) {
        updatePrefs({ it.copy(showFooterLine = show) }, { it.setShowFooterLine(show) },
            bookOverride = { o -> o.copy(showFooterLine = show) })
    }

    fun setHeaderFontSizeRatio(ratio: Float) {
        updatePrefs({ it.copy(headerFontSizeRatio = ratio) }, { it.setHeaderFontSizeRatio(ratio) },
            bookOverride = { o -> o.copy(headerFontSizeRatio = ratio) })
    }

    fun setFooterFontSizeRatio(ratio: Float) {
        updatePrefs({ it.copy(footerFontSizeRatio = ratio) }, { it.setFooterFontSizeRatio(ratio) },
            bookOverride = { o -> o.copy(footerFontSizeRatio = ratio) })
    }

    // ── 正文标题样式 ──────────────────────────────────────────────

    fun setTitleAlign(align: TitleAlign) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(align = align)) },
            { it.setTitleAlign(align.toStorageString()) },
            bookOverride = { o -> o.copy(titleAlign = align.toStorageString()) },
            reflow = true,
        )
    }

    fun setTitleSizeOffset(offsetSp: Int) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(sizeOffsetSp = offsetSp)) },
            { it.setTitleSizeOffset(offsetSp) },
            bookOverride = { o -> o.copy(titleSizeOffset = offsetSp) },
            reflow = true,
        )
    }

    fun setTitleMarginTop(dp: Float) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(marginTopDp = dp)) },
            { it.setTitleMarginTop(dp) },
            bookOverride = { o -> o.copy(titleMarginTop = dp) },
            reflow = true,
        )
    }

    fun setTitleMarginBottom(dp: Float) {
        updatePrefs(
            { it.copy(titleStyle = it.titleStyle.copy(marginBottomDp = dp)) },
            { it.setTitleMarginBottom(dp) },
            bookOverride = { o -> o.copy(titleMarginBottom = dp) },
            reflow = true,
        )
    }

    // ── 行为参数 ──────────────────────────────────────────────

    fun setKeepScreenOn(enabled: Boolean) {
        updatePrefs({ it.copy(keepScreenOn = enabled) }, { it.setKeepScreenOn(enabled) },
            bookOverride = { o -> o.copy(keepScreenOn = enabled) })
    }

    fun setVolumeKeyTurnPage(enabled: Boolean) {
        updatePrefs({ it.copy(volumeKeyTurnPage = enabled) }, { it.setVolumeKeyTurnPage(enabled) },
            bookOverride = { o -> o.copy(volumeKeyTurnPage = enabled) })
    }

    fun setEdgeTurnPage(enabled: Boolean) {
        updatePrefs({ it.copy(edgeTurnPage = enabled) }, { it.setEdgeTurnPage(enabled) },
            bookOverride = { o -> o.copy(edgeTurnPage = enabled) })
    }

    fun setEdgeWidthPercent(percent: Float) {
        updatePrefs({ it.copy(edgeWidthPercent = percent) }, { it.setEdgeWidthPercent(percent) },
            bookOverride = { o -> o.copy(edgeWidthPercent = percent) })
    }

    fun setImmersiveMode(enabled: Boolean) {
        updatePrefs({ it.copy(immersiveMode = enabled) }, { it.setImmersiveMode(enabled) },
            bookOverride = { o -> o.copy(immersiveMode = enabled) })
    }

    // ── P1: 排版增强 ──────────────────────────────────────────────

    fun setMaxPageWidth(width: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(maxPageWidth = width) }, { it.setMaxPageWidth(width) },
            bookOverride = { o -> o.copy(maxPageWidth = width) }, reflow = true)
    }

    fun setRemoveEmptyLines(enabled: Boolean) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(removeEmptyLines = enabled) }, { it.setRemoveEmptyLines(enabled) },
            bookOverride = { o -> o.copy(removeEmptyLines = enabled) }, reflow = true)
    }

    fun setCleanChapterTitle(enabled: Boolean) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(cleanChapterTitle = enabled) }, { it.setCleanChapterTitle(enabled) },
            bookOverride = { o -> o.copy(cleanChapterTitle = enabled) }, reflow = true)
    }

    fun setProgressStyle(style: ProgressStyle) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(progressStyle = style) }, { it.setProgressStyle(style.toStorageString()) },
            bookOverride = { o -> o.copy(progressStyle = style.toStorageString()) })
    }

    // ── P2: 低频增强 ──────────────────────────────────────────────

    fun setAutoNightMode(enabled: Boolean) {
        updatePrefs({ it.copy(autoNightMode = enabled) }, { it.setAutoNightMode(enabled) },
            bookOverride = { o -> o.copy(autoNightMode = enabled) })
    }

    fun setAutoPageTurn(enabled: Boolean) {
        updatePrefs({ it.copy(autoPageTurn = enabled) }, { it.setAutoPageTurn(enabled) },
            bookOverride = { o -> o.copy(autoPageTurn = enabled) })
    }

    fun setAutoPageTurnInterval(interval: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(autoPageTurnInterval = interval) }, { it.setAutoPageTurnInterval(interval) },
            bookOverride = { o -> o.copy(autoPageTurnInterval = interval) })
    }

    fun setEpubOverrideStyle(enabled: Boolean) {
        updatePrefs({ it.copy(epubOverrideStyle = enabled) }, { it.setEpubOverrideStyle(enabled) },
            bookOverride = { o -> o.copy(epubOverrideStyle = enabled) }, reflow = true)
    }

    fun setLeftZoneRatio(ratio: Float) {
        resetToolbarAutoHide()
        updatePrefs({ it.copy(leftZoneRatio = ratio) }, { it.setLeftZoneRatio(ratio) },
            bookOverride = { o -> o.copy(leftZoneRatio = ratio) })
    }

    // ── 通用更新辅助 ──────────────────────────────────────────────

    private fun updatePrefs(
        transform: (ReaderPreferences) -> ReaderPreferences,
        save: suspend (UserPreferences) -> Unit,
        bookOverride: ((BookReaderPrefsOverrides) -> BookReaderPrefsOverrides)? = null,
        reflow: Boolean = false,
    ) {
        val updated = transform(uiState.value.readerPreferences)
        uiState.value = uiState.value.copy(
            readerPreferences = updated,
            isReflowing = reflow,
        )
        if (reflow) reflowCurrentChapter(updated)
        val currentScope = uiState.value.settingsScope
        if (currentScope == SettingsScope.BOOK && bookOverride != null) {
            saveBookOverrideField(uiState.value.bookId, bookOverride)
        } else {
            scope.launch { userPreferences?.let { save(it) } }
        }
    }

    /**
     * 通用设置更新（v5.1 Phase 1-4 新增设置的临时入口）。
     * 仅更新 UI 状态和触发 reflow，DataStore/BookOverride 持久化待各 Phase 专用 setter 实现。
     */
    fun updatePrefsGeneric(
        transform: (ReaderPreferences) -> ReaderPreferences,
        reflow: Boolean = false,
    ) {
        val updated = transform(uiState.value.readerPreferences)
        uiState.value = uiState.value.copy(
            readerPreferences = updated,
            isReflowing = reflow,
        )
        if (reflow) reflowCurrentChapter(updated)
    }
}
