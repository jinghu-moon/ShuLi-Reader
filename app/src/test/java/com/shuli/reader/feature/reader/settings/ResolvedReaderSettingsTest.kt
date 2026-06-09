package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.ChineseConvert
import com.shuli.reader.core.data.ReaderFontWeight
import com.shuli.reader.core.data.ReaderTextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证 [ResolvedReaderSettings] 与 [ReaderSettingsResolver] 的"默认 + 本书覆盖 + 会话状态"
 * 三层合并语义。null 表示"跟随默认"，非 null 表示"本书覆盖"。
 */
class ResolvedReaderSettingsTest {

    // ── 全 null BookPrefs → 完全跟随默认 ──

    @Test
    fun resolve_bookPrefsAllNull_followsDefaults() {
        val defaults = createDefaults(fontSize = 18f, textAlign = ReaderTextAlign.LEFT)
        val bookPrefs = BookReaderPrefsEntity(bookId = 1L)
        val session = ReaderSessionState()

        val resolved = ReaderSettingsResolver.resolve(defaults, bookPrefs, session)

        assertEquals(18f, resolved.fontSize)
        assertEquals(ReaderTextAlign.LEFT, resolved.textAlign)
        assertEquals("harmony", resolved.readingFont)
        assertEquals(ReaderFontWeight.NORMAL, resolved.fontWeight)
        assertEquals(1.5f, resolved.lineSpacing)
    }

    // ── 非 null 字段覆盖默认 ──

    @Test
    fun resolve_bookPrefsOverridesTextAlign_overridesOnlyThatField() {
        val defaults = createDefaults(fontSize = 18f, textAlign = ReaderTextAlign.LEFT)
        val bookPrefs = BookReaderPrefsEntity(bookId = 1L, textAlign = "justify")
        val session = ReaderSessionState()

        val resolved = ReaderSettingsResolver.resolve(defaults, bookPrefs, session)

        assertEquals(ReaderTextAlign.JUSTIFY, resolved.textAlign)
        assertEquals(18f, resolved.fontSize)
        assertEquals("harmony", resolved.readingFont)
    }

    @Test
    fun resolve_bookPrefsOverridesFontSize_overridesOnlyThatField() {
        val defaults = createDefaults(fontSize = 18f, lineSpacing = 1.5f)
        val bookPrefs = BookReaderPrefsEntity(bookId = 1L, fontSize = 22f)
        val session = ReaderSessionState()

        val resolved = ReaderSettingsResolver.resolve(defaults, bookPrefs, session)

        assertEquals(22f, resolved.fontSize)
        assertEquals(1.5f, resolved.lineSpacing)
    }

    @Test
    fun resolve_bookPrefsOverridesFont_overridesOnlyThatField() {
        val defaults = createDefaults(readingFont = "harmony", fontWeight = ReaderFontWeight.NORMAL)
        val bookPrefs = BookReaderPrefsEntity(
            bookId = 1L,
            readingFont = "source-han",
            fontWeight = "bold",
        )
        val session = ReaderSessionState()

        val resolved = ReaderSettingsResolver.resolve(defaults, bookPrefs, session)

        assertEquals("source-han", resolved.readingFont)
        assertEquals(ReaderFontWeight.BOLD, resolved.fontWeight)
    }

    // ── 清除覆盖 → 回到默认 ──

    @Test
    fun resolve_clearedOverride_followsDefault() {
        val defaults = createDefaults(fontSize = 20f)
        val bookPrefsOverride = BookReaderPrefsEntity(bookId = 1L, fontSize = 16f)
        val bookPrefsCleared = bookPrefsOverride.copy(fontSize = null)
        val session = ReaderSessionState()

        val resolved = ReaderSettingsResolver.resolve(defaults, bookPrefsCleared, session)

        assertEquals(20f, resolved.fontSize)
    }

    // ── 会话状态覆盖（如临时亮度） ──

    @Test
    fun resolve_sessionBrightnessOverrides() {
        val defaults = createDefaults(brightness = -1f)
        val bookPrefs = BookReaderPrefsEntity(bookId = 1L, brightness = 0.5f)
        val session = ReaderSessionState(brightness = 0.8f)

        val resolved = ReaderSettingsResolver.resolve(defaults, bookPrefs, session)

        assertEquals(0.8f, resolved.brightness)
    }

    @Test
    fun resolve_sessionBrightnessNull_fallsBackToBookOrDefault() {
        val defaults = createDefaults(brightness = -1f)
        val bookPrefs = BookReaderPrefsEntity(bookId = 1L, brightness = 0.5f)
        val session = ReaderSessionState(brightness = null)

        val resolved = ReaderSettingsResolver.resolve(defaults, bookPrefs, session)

        assertEquals(0.5f, resolved.brightness)
    }

    // ── BookReaderPrefsEntity 可空语义 ──

    @Test
    fun bookReaderPrefsEntity_defaultConstructor_allFieldsNull() {
        val prefs = BookReaderPrefsEntity(bookId = 1L)

        assertNull(prefs.fontSize)
        assertNull(prefs.lineSpacing)
        assertNull(prefs.paragraphSpacing)
        assertNull(prefs.indent)
        assertNull(prefs.marginHorizontal)
        assertNull(prefs.marginVertical)
        assertNull(prefs.letterSpacing)
        assertNull(prefs.readingFont)
        assertNull(prefs.fontWeight)
        assertNull(prefs.textAlign)
        assertNull(prefs.chineseConvert)
        assertNull(prefs.brightness)
    }

    @Test
    fun bookReaderPrefsEntity_onlyFieldsSet_explicitlyNullOthersRemain() {
        val prefs = BookReaderPrefsEntity(bookId = 1L, fontSize = 20f, textAlign = "JUSTIFY")

        assertEquals(20f, prefs.fontSize)
        assertEquals("JUSTIFY", prefs.textAlign)
        assertNull(prefs.lineSpacing)
        assertNull(prefs.readingFont)
    }

    // ── ResolvedReaderSettings 数据完整性 ──

    @Test
    fun resolvedSettings_allFieldsPopulated() {
        val defaults = createDefaults()
        val bookPrefs = BookReaderPrefsEntity(bookId = 1L)
        val session = ReaderSessionState()

        val resolved = ReaderSettingsResolver.resolve(defaults, bookPrefs, session)

        // 确保 ResolvedReaderSettings 每个字段都有值（非 null）
        resolved.fontSize
        resolved.lineSpacing
        resolved.paragraphSpacing
        resolved.indent
        resolved.marginHorizontal
        resolved.marginVertical
        resolved.letterSpacing
        resolved.readingFont
        resolved.fontWeight
        resolved.textAlign
        resolved.chineseConvert
        resolved.brightness
        resolved.keepScreenOn
        resolved.edgeTurnPage
        resolved.edgeWidthPercent
    }

    @Test
    fun resolve_pureFunction_sameInputs_sameOutput() {
        val defaults = createDefaults(fontSize = 18f)
        val bookPrefs = BookReaderPrefsEntity(bookId = 1L, textAlign = "justify")
        val session = ReaderSessionState()

        val a = ReaderSettingsResolver.resolve(defaults, bookPrefs, session)
        val b = ReaderSettingsResolver.resolve(defaults, bookPrefs, session)

        assertEquals(a, b)
    }

    // ── 多字段同时覆盖 ──

    @Test
    fun resolve_multipleOverrides_eachApplied() {
        val defaults = createDefaults(
            fontSize = 16f,
            lineSpacing = 1.5f,
            textAlign = ReaderTextAlign.LEFT,
            readingFont = "harmony",
            marginHorizontal = 24f,
        )
        val bookPrefs = BookReaderPrefsEntity(
            bookId = 1L,
            fontSize = 20f,
            lineSpacing = 2.0f,
            textAlign = "justify",
            marginHorizontal = 32f,
        )
        val session = ReaderSessionState()

        val resolved = ReaderSettingsResolver.resolve(defaults, bookPrefs, session)

        assertEquals(20f, resolved.fontSize)
        assertEquals(2.0f, resolved.lineSpacing)
        assertEquals(ReaderTextAlign.JUSTIFY, resolved.textAlign)
        assertEquals(32f, resolved.marginHorizontal)
        assertEquals("harmony", resolved.readingFont)
    }

    // ── Entity 直接用作 Room 数据模型 ──

    @Test
    fun bookReaderPrefsEntity_isDataClass() {
        assertTrue(
            "BookReaderPrefsEntity 应为 data class",
            BookReaderPrefsEntity::class.isData,
        )
    }
}

// ── Test helpers ──

internal fun createDefaults(
    fontSize: Float = 16f,
    lineSpacing: Float = 1.5f,
    paragraphSpacing: Float = 1.0f,
    indent: Float = 2.0f,
    marginHorizontal: Float = 24f,
    marginVertical: Float = 48f,
    letterSpacing: Float = 0f,
    readingFont: String = "harmony",
    fontWeight: ReaderFontWeight = ReaderFontWeight.NORMAL,
    textAlign: ReaderTextAlign = ReaderTextAlign.LEFT,
    chineseConvert: ChineseConvert = ChineseConvert.NONE,
    brightness: Float = -1f,
    keepScreenOn: Boolean = false,
    edgeTurnPage: Boolean = true,
    edgeWidthPercent: Float = 0.33f,
    bottomJustify: Boolean = false,
    usePanguSpacing: Boolean = false,
    useZhLayout: Boolean = false,
) = ResolvedReaderSettings(
    fontSize = fontSize,
    lineSpacing = lineSpacing,
    paragraphSpacing = paragraphSpacing,
    indent = indent,
    marginHorizontal = marginHorizontal,
    marginVertical = marginVertical,
    letterSpacing = letterSpacing,
    readingFont = readingFont,
    fontWeight = fontWeight,
    textAlign = textAlign,
    chineseConvert = chineseConvert,
    brightness = brightness,
    keepScreenOn = keepScreenOn,
    edgeTurnPage = edgeTurnPage,
    edgeWidthPercent = edgeWidthPercent,
    bottomJustify = bottomJustify,
    usePanguSpacing = usePanguSpacing,
    useZhLayout = useZhLayout,
)
