package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.feature.reader.render.InvalidationScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSettingRegistryTest {

    @Test
    fun settingDefinition_hasEightFields() {
        val def = ReaderSettingRegistry.getDefinition<Float>("font_size")
        assertEquals("font_size", def.key)
        assertEquals(16f, def.defaultValue, 0.001f)
        assertEquals(StorageTier.BOTH, def.storageTier)
        assertEquals(InvalidationScope.REFLOW, def.scope)
        assertEquals(3, def.recompositionTier)
        assertEquals(UiGroup.FONT_BASICS, def.uiGroup)
        assertTrue(def.includeInPreset)
        assertEquals(PreviewStrategy.LIVE, def.previewStrategy)
    }

    @Test
    fun storageTier_hasThreeValues() {
        val names = StorageTier.entries.map { it.name }.toSet()
        assertEquals(setOf("GLOBAL", "PER_BOOK", "BOTH"), names)
    }

    @Test
    fun uiGroup_hasTwelveValues() {
        val expected = setOf(
            "FONT_BASICS", "TEXT_LAYOUT", "TEXT_STYLE", "ADVANCED_READING",
            "THEME", "PAGE_CHROME", "DISPLAY_MODE", "VISUAL_AIDS",
            "PAGE_TURN", "GESTURE", "EYE_CARE", "GENERAL",
        )
        assertEquals(expected, UiGroup.entries.map { it.name }.toSet())
    }

    @Test
    fun previewStrategy_hasThreeValues() {
        val names = PreviewStrategy.entries.map { it.name }.toSet()
        assertEquals(setOf("LIVE", "ON_APPLY", "NONE"), names)
    }

    @Test
    fun registryAll_isNotEmpty() {
        assertTrue(ReaderSettingRegistry.all.isNotEmpty())
        assertTrue("Registry should contain 50+ settings", ReaderSettingRegistry.all.size >= 50)
    }

    @Test
    fun noDuplicateKeys() {
        val keys = ReaderSettingRegistry.all.map { it.key }
        assertEquals(keys.distinct().size, keys.size)
    }

    @Test
    fun byRecompositionTier_filtersCorrectly() {
        val tier0 = ReaderSettingRegistry.byRecompositionTier(0)
        assertTrue(tier0.isNotEmpty())
        assertTrue(tier0.all { it.recompositionTier == 0 })
    }

    @Test
    fun byUiGroup_filtersCorrectly() {
        val fontBasics = ReaderSettingRegistry.byUiGroup(UiGroup.FONT_BASICS)
        assertTrue(fontBasics.isNotEmpty())
        assertTrue(fontBasics.all { it.uiGroup == UiGroup.FONT_BASICS })
    }

    @Test
    fun presetFields_allHaveIncludeInPresetTrue() {
        val preset = ReaderSettingRegistry.presetFields()
        assertTrue(preset.isNotEmpty())
        assertTrue(preset.all { it.includeInPreset })
    }

    @Test
    fun presetFields_excludesPageAnimTypeAndSpeed() {
        val presetKeys = ReaderSettingRegistry.presetFields().map { it.key }.toSet()
        assertFalse("page_anim_type must not be in preset", "page_anim_type" in presetKeys)
        assertFalse("page_anim_speed must not be in preset", "page_anim_speed" in presetKeys)
    }

    @Test
    fun presetFields_excludesOverlayAndBehaviorFields() {
        val presetKeys = ReaderSettingRegistry.presetFields().map { it.key }.toSet()
        assertFalse("color_temperature must not be in preset", "color_temperature" in presetKeys)
        assertFalse("haptic_feedback must not be in preset", "haptic_feedback" in presetKeys)
        assertFalse("tts_speed must not be in preset", "tts_speed" in presetKeys)
    }

    @Test
    fun byScope_filtersCorrectly() {
        val reflow = ReaderSettingRegistry.byScope(InvalidationScope.REFLOW)
        assertTrue(reflow.isNotEmpty())
        assertTrue(reflow.all { it.scope == InvalidationScope.REFLOW })
    }

    @Test
    fun byScope_reflow_allHaveIncludeInPresetTrue() {
        val reflow = ReaderSettingRegistry.byScope(InvalidationScope.REFLOW)
        val violating = reflow.filter { !it.includeInPreset }.map { it.key }
        assertTrue("REFLOW keys missing includeInPreset: $violating", violating.isEmpty())
    }

    @Test
    fun byStorageTier_GLOBAL_includesBOTH() {
        val global = ReaderSettingRegistry.byStorageTier(StorageTier.GLOBAL)
        assertTrue(global.any { it.storageTier == StorageTier.BOTH })
        assertTrue(global.all { it.storageTier == StorageTier.GLOBAL || it.storageTier == StorageTier.BOTH })
    }

    @Test
    fun getDefault_fontSize_returns16() {
        val value: Float = ReaderSettingRegistry.getDefault("font_size")
        assertEquals(16f, value, 0.001f)
    }

    @Test
    fun getDefault_colorTemperature_returns6500() {
        val value: Float = ReaderSettingRegistry.getDefault("color_temperature")
        assertEquals(6500f, value, 0.001f)
    }

    @Test
    fun getDefault_unknownKey_throws() {
        try {
            ReaderSettingRegistry.getDefault<Any>("non_existent_key")
            error("should have thrown")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("non_existent_key"))
        }
    }

    @Test
    fun findDefinition_knownKey_returns() {
        assertNotNull(ReaderSettingRegistry.findDefinition("font_size"))
    }

    @Test
    fun findDefinition_unknownKey_returnsNull() {
        assertNull(ReaderSettingRegistry.findDefinition("does_not_exist"))
    }

    @Test
    fun invalidationScope_VIEW_INVALIDATE_exists() {
        assertEquals("VIEW_INVALIDATE", InvalidationScope.VIEW_INVALIDATE.name)
    }

    @Test
    fun invalidationScope_NONE_exists() {
        assertEquals("NONE", InvalidationScope.NONE.name)
    }

    @Test
    fun invalidationScope_VIEW_INVALIDATE_notInReflowImplied() {
        assertFalse(InvalidationScope.VIEW_INVALIDATE in InvalidationScope.REFLOW_IMPLIED)
    }

    @Test
    fun invalidationScope_NONE_notInReflowImplied() {
        assertFalse(InvalidationScope.NONE in InvalidationScope.REFLOW_IMPLIED)
    }

    @Test
    fun colorTemperature_isViewInvalidateScope() {
        val def = ReaderSettingRegistry.getDefinition<Float>("color_temperature")
        assertEquals(InvalidationScope.VIEW_INVALIDATE, def.scope)
    }

    @Test
    fun hapticFeedback_isNoneScope() {
        val def = ReaderSettingRegistry.getDefinition<Boolean>("haptic_feedback")
        assertEquals(InvalidationScope.NONE, def.scope)
    }

    @Test
    fun v51_fourMargins_areRegisteredAsReflow() {
        listOf("margin_top", "margin_bottom", "margin_left", "margin_right").forEach { key ->
            val def = ReaderSettingRegistry.findDefinition(key)
            assertNotNull("$key should be registered", def)
            assertEquals(InvalidationScope.REFLOW, def!!.scope)
            assertEquals(3, def.recompositionTier)
            assertTrue("$key should be in preset", def.includeInPreset)
        }
    }

    @Test
    fun v51_gestureConfig_isRegisteredAsNone() {
        val def = ReaderSettingRegistry.findDefinition("gesture_config")
        assertNotNull(def)
        assertEquals(InvalidationScope.NONE, def!!.scope)
        assertFalse(def.includeInPreset)
    }

    @Test
    fun v51_orientationLock_isRegisteredAsNone() {
        val def = ReaderSettingRegistry.findDefinition("orientation_lock")
        assertNotNull(def)
        assertEquals(InvalidationScope.NONE, def!!.scope)
    }

    @Test
    fun v51_dualPageMode_isRegisteredAsReflow() {
        val def = ReaderSettingRegistry.findDefinition("dual_page_mode")
        assertNotNull(def)
        assertEquals(InvalidationScope.REFLOW, def!!.scope)
    }

    @Test
    fun v51_pageAnimSpeed_isPageDelegateScope() {
        val def = ReaderSettingRegistry.findDefinition("page_anim_speed")
        assertNotNull(def)
        assertEquals(InvalidationScope.PAGE_DELEGATE, def!!.scope)
    }

    @Test
    fun v51_backgroundTexture_isShellScope() {
        val def = ReaderSettingRegistry.findDefinition("background_texture")
        assertNotNull(def)
        assertEquals(InvalidationScope.SHELL, def!!.scope)
    }

    @Test
    fun v51_titleFont_isContentScope() {
        val def = ReaderSettingRegistry.findDefinition("title_font")
        assertNotNull(def)
        assertEquals(InvalidationScope.CONTENT, def!!.scope)
    }

    @Test
    fun defaultsMatchReaderPreferences_forSampledFields() {
        val prefs = ReaderPreferences()
        val regFontSize: Float = ReaderSettingRegistry.getDefault("font_size")
        val regLineSpacing: Float = ReaderSettingRegistry.getDefault("line_spacing")
        val regColorTemp: Float = ReaderSettingRegistry.getDefault("color_temperature")
        assertEquals(regFontSize, prefs.fontSize, 0.001f)
        assertEquals(regLineSpacing, prefs.lineSpacing, 0.001f)
        assertEquals(regColorTemp, prefs.colorTemperature, 0.001f)
    }

    @Test
    fun copy_semantic_onlyModifiesTargetField() {
        val def = ReaderSettingRegistry.getDefinition<Float>("font_size")
        val copied = def.copy(scope = InvalidationScope.REFLOW)
        assertEquals(InvalidationScope.REFLOW, copied.scope)
        assertEquals(def.key, copied.key)
        assertEquals(def.defaultValue, copied.defaultValue)
    }
}
