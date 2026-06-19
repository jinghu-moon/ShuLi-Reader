package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.reader.model.BoxInsetsDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetSnapshotTest {

    @Test
    fun fromPreferences_copiesPresetFields() {
        val prefs = ReaderPreferences(
            fontSize = 20f,
            lineSpacing = 1.8f,
            readingFont = "serif",
            bodyBox = BoxInsetsDp(top = 60f, bottom = 60f, left = 24f, right = 24f),
            showProgress = false,
        )
        val snap = PresetSnapshot.fromPreferences(prefs)
        assertEquals(20f, snap.fontSize, 0.001f)
        assertEquals(1.8f, snap.lineSpacing, 0.001f)
        assertEquals("serif", snap.readingFont)
        assertEquals(60f, snap.bodyBox.top, 0.001f)
        assertFalse(snap.showProgress)
    }

    @Test
    fun fromPreferences_excludesOverlayAndBehaviorFields() {
        val prefs = ReaderPreferences(
            colorTemperature = 3500f,
            hapticFeedback = true,
            ttsSpeed = 1.5f,
            pageAnimSpeed = com.shuli.reader.core.data.PageAnimSpeed.FAST,
        )
        val snap = PresetSnapshot.fromPreferences(prefs)
        // These non-preset fields are not in PresetSnapshot; defaults should apply
        assertEquals(16f, snap.fontSize, 0.001f) // unaffected by colorTemperature
        // (PresetSnapshot has no colorTemperature / hapticFeedback / ttsSpeed fields)
    }

    @Test
    fun fromPreferences_excludesPageAnimTypeAndSpeed() {
        val snap = PresetSnapshot.fromPreferences(ReaderPreferences())
        // PresetSnapshot has no pageAnimType / pageAnimSpeed field — verify by field absence
        val fields = PresetSnapshot::class.java.declaredFields.map { it.name }.toSet()
        assertFalse("pageAnimType must not be in PresetSnapshot", "pageAnimType" in fields)
        assertFalse("pageAnimSpeed must not be in PresetSnapshot", "pageAnimSpeed" in fields)
    }

    @Test
    fun jsonRoundTrip_preservesValues() {
        val original = PresetSnapshot.fromPreferences(
            ReaderPreferences(
                fontSize = 22f,
                lineSpacing = 2.0f,
                readingFont = "serif",
                bodyBox = BoxInsetsDp(top = 60f, bottom = 48f, left = 24f, right = 24f),
                bionicReading = true,
            )
        )
        val json = original.toJson()
        val restored = PresetSnapshot.fromJson(json)
        assertEquals(original, restored)
    }

    @Test
    fun fromJson_unknownFieldsIgnored() {
        val json = """{"fontSize": 18.0, "unknownField": 42, "readingFont": "harmony"}"""
        val snap = PresetSnapshot.fromJson(json)
        assertEquals(18f, snap.fontSize, 0.001f)
        assertEquals("harmony", snap.readingFont)
    }

    @Test
    fun applyOnto_updatesPresetFieldsOnly() {
        val base = ReaderPreferences(
            colorTemperature = 4000f, // non-preset, should be preserved
            hapticFeedback = true,    // non-preset, should be preserved
            ttsSpeed = 1.5f,          // non-preset, should be preserved
        )
        val snap = PresetSnapshot(fontSize = 24f, lineSpacing = 2.0f, readingFont = "serif")
        val applied = snap.applyOnto(base)
        assertEquals(24f, applied.fontSize, 0.001f)
        assertEquals(2.0f, applied.lineSpacing, 0.001f)
        assertEquals("serif", applied.readingFont)
        // non-preset fields preserved
        assertEquals(4000f, applied.colorTemperature, 0.001f)
        assertTrue(applied.hapticFeedback)
        assertEquals(1.5f, applied.ttsSpeed, 0.001f)
    }

    @Test
    fun applyOnto_roundTrip_withReaderPreferences() {
        val original = ReaderPreferences(
            fontSize = 19f,
            lineSpacing = 1.6f,
            readingFont = "mono",
            bodyBox = BoxInsetsDp(top = 50f, bottom = 40f, left = 24f, right = 24f),
        )
        val snapshot = PresetSnapshot.fromPreferences(original)
        val restored = snapshot.applyOnto(ReaderPreferences())
        assertEquals(original.fontSize, restored.fontSize, 0.001f)
        assertEquals(original.lineSpacing, restored.lineSpacing, 0.001f)
        assertEquals(original.readingFont, restored.readingFont)
        assertEquals(original.bodyBox.top, restored.bodyBox.top, 0.001f)
        assertEquals(original.bodyBox.bottom, restored.bodyBox.bottom, 0.001f)
    }

    @Test
    fun presetFields_includesLayoutAndStyleAndChromeFields() {
        val snap = PresetSnapshot.fromPreferences(ReaderPreferences())
        // Layout
        assertEquals(16f, snap.fontSize, 0.001f)
        assertEquals(24f, snap.bodyBox.left, 0.001f)
        // Style
        assertEquals("harmony", snap.readingFont)
        // Chrome
        assertTrue(snap.showProgress)
        assertEquals(0.75f, snap.headerFontSizeRatio, 0.001f)
    }

    // ── Registry 一致性验证 ──

    /**
     * PresetSnapshot 的字段集必须与 Registry.presetFields() 对齐。
     *
     * 已知例外：
     * - headerVisibility / footerVisibility：Registry key 为 "header_visibility" / "footer_visibility"，
     *   PresetSnapshot 将其扁平化为独立字段（而非嵌套 HeaderConfig）。
     * - titleFont：Registry 中 "title_font" 对应的 PresetSnapshot 字段。
     */
    @Test
    fun presetSnapshotFields_matchRegistryPresetKeys() {
        val registryKeys = ReaderSettingRegistry.presetFields().map { it.key }.toSet()
        val snapshotFields = PresetSnapshot::class.java.declaredFields
            .filter { it.name != "Companion" && it.name != "\$serialVersionUID" }
            .map { it.name }
            .toSet()

        // Registry key → PresetSnapshot field name 映射
        val keyToField = mapOf(
            "font_size" to "fontSize",
            "line_spacing" to "lineSpacing",
            "paragraph_spacing" to "paragraphSpacing",
            "indent" to "indent",
            "indent_unit" to "indentUnit",
            "letter_spacing" to "letterSpacing",
            "paragraph_divider" to "paragraphDivider",
            "reading_font" to "readingFont",
            "font_weight" to "fontWeight",
            "text_align" to "textAlign",
            "chinese_convert" to "chineseConvert",
            "use_zh_layout" to "useZhLayout",
            "use_pangu_spacing" to "usePanguSpacing",
            "bionic_reading" to "bionicReading",
            "bottom_justify" to "bottomJustify",
            "max_page_width" to "maxPageWidth",
            "remove_empty_lines" to "removeEmptyLines",
            "clean_chapter_title" to "cleanChapterTitle",
            "epub_override_style" to "epubOverrideStyle",
            "body_box" to "bodyBox",
            "header_box" to "headerBox",
            "footer_box" to "footerBox",
            "title_box" to "titleBox",
            "vertical_text" to "verticalText",
            "dual_page_mode" to "dualPageMode",
            "ad_filtering" to "adFiltering",
            "header_visibility" to "headerVisibility",
            "footer_visibility" to "footerVisibility",
            "header_footer_alpha" to "headerFooterAlpha",
            "show_progress" to "showProgress",
            "progress_style" to "progressStyle",
            "show_header_line" to "showHeaderLine",
            "show_footer_line" to "showFooterLine",
            "header_font_size_ratio" to "headerFontSizeRatio",
            "footer_font_size_ratio" to "footerFontSizeRatio",
            "title_font" to "titleFont",
            "title_style" to "titleStyle",
        )

        // 验证所有映射的 Registry key 确实在 Registry 中标记为 preset
        for ((key, _) in keyToField) {
            assertTrue(
                "Registry key '$key' should have includeInPreset=true",
                key in registryKeys,
            )
        }

        // 验证所有映射的 PresetSnapshot 字段确实存在
        for ((_, fieldName) in keyToField) {
            assertTrue(
                "PresetSnapshot should have field '$fieldName'",
                fieldName in snapshotFields,
            )
        }

        // 验证所有 Registry presetKeys 都有对应映射
        val mappedKeys = keyToField.keys
        val unmappedRegistryKeys = registryKeys - mappedKeys
        assertTrue(
            "Registry preset keys without PresetSnapshot mapping: $unmappedRegistryKeys",
            unmappedRegistryKeys.isEmpty(),
        )
    }

    @Test
    fun fromPreferences_defaultMatchesRegistryDefaults() {
        val snap = PresetSnapshot.fromPreferences(ReaderPreferences())
        // fontSize 默认值应与 Registry 一致
        assertEquals(16f, snap.fontSize, 0.001f)
        assertEquals(1.5f, snap.lineSpacing, 0.001f)
        assertEquals(1.0f, snap.paragraphSpacing, 0.001f)
        assertEquals(2.0f, snap.indent, 0.001f)
        assertEquals("CHARACTER", snap.indentUnit)
        assertEquals(0f, snap.letterSpacing, 0.001f)
        assertEquals("harmony", snap.readingFont)
        assertEquals("NORMAL", snap.fontWeight)
        assertEquals("LEFT", snap.textAlign)
        assertEquals("NONE", snap.chineseConvert)
        assertEquals(24f, snap.bodyBox.left, 0.001f)
        assertEquals(48f, snap.bodyBox.top, 0.001f)
        assertEquals(0.4f, snap.headerFooterAlpha, 0.001f)
        assertTrue(snap.showProgress)
        assertEquals("CHAPTER_FRACTION", snap.progressStyle)
        assertEquals(0.75f, snap.headerFontSizeRatio, 0.001f)
        assertEquals(0.75f, snap.footerFontSizeRatio, 0.001f)
    }
}
