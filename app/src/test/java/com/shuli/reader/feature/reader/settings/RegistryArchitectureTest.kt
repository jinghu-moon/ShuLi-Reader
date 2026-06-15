package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.feature.reader.render.InvalidationScope
import com.shuli.reader.feature.reader.render.ReaderRenderDiffCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Registry 架构完整性测试（附录 E：T-E.1 ~ T-E.7）。
 *
 * 验证 ReaderSettingRegistry 的结构性约束：
 * key 唯一性、tier 覆盖、uiGroup 覆盖、预设排除完整性等。
 */
class RegistryArchitectureTest {

    // T-E.1: Registry key 全局唯一
    @Test
    fun allKeys_unique() {
        val keys = ReaderSettingRegistry.all.map { it.key }
        val uniqueKeys = keys.distinct()
        assertEquals(
            "Registry 中存在重复 key: ${keys.groupBy { it }.filter { it.value.size > 1 }.keys}",
            keys.size, uniqueKeys.size
        )
    }

    // T-E.2: 所有设置都有 recompositionTier 分类（≥ -1）
    @Test
    fun allSettings_haveValidRecompositionTier() {
        for (def in ReaderSettingRegistry.all) {
            assertTrue(
                "${def.key} 的 recompositionTier (${def.recompositionTier}) 应 ≥ -1",
                def.recompositionTier >= -1
            )
        }
    }

    // T-E.2b: tier 分布覆盖 0/1/2/3/-1
    @Test
    fun recompositionTier_coversAllExpectedTiers() {
        val tiers = ReaderSettingRegistry.all.map { it.recompositionTier }.toSet()
        assertTrue("应包含 tier 0 (Overlay)", 0 in tiers)
        assertTrue("应包含 tier 1 (Chrome)", 1 in tiers)
        assertTrue("应包含 tier 2 (Style)", 2 in tiers)
        assertTrue("应包含 tier 3 (Layout)", 3 in tiers)
        assertTrue("应包含 tier -1 (行为层)", -1 in tiers)
    }

    // T-E.3: 所有设置都有 uiGroup 分组
    @Test
    fun allSettings_haveValidUiGroup() {
        val validGroups = UiGroup.entries.toSet()
        for (def in ReaderSettingRegistry.all) {
            assertTrue(
                "${def.key} 的 uiGroup (${def.uiGroup}) 应在 UiGroup.entries 中",
                def.uiGroup in validGroups
            )
        }
    }

    // T-E.3b: 12 个 UiGroup 至少有一个设置
    @Test
    fun allUiGroups_haveAtLeastOneSetting() {
        for (group in UiGroup.entries) {
            val settings = ReaderSettingRegistry.byUiGroup(group)
            assertTrue(
                "UiGroup.${group.name} 应至少有一个设置",
                settings.isNotEmpty()
            )
        }
    }

    // T-E.4: 验证 Registry 提供的查询 API 覆盖所有 scope
    @Test
    fun byScope_coversAllInvalidationScopes() {
        val scopesInRegistry = ReaderSettingRegistry.all.map { it.scope }.toSet()
        for (scope in scopesInRegistry) {
            val settings = ReaderSettingRegistry.byScope(scope)
            assertTrue(
                "Scope $scope 应至少有一个设置",
                settings.isNotEmpty()
            )
        }
    }

    // T-E.6: 预设快照排除完整性
    @Test
    fun presetFields_excludeOverlayAndBehaviorSettings() {
        val presetKeys = ReaderSettingRegistry.presetFields().map { it.key }.toSet()

        // Overlay 类不应在预设中
        assertFalse("colorTemperature 不应在预设中", "color_temperature" in presetKeys)
        assertFalse("focusLine 不应在预设中", "focus_line" in presetKeys)
        assertFalse("brightness 不应在预设中", "brightness" in presetKeys)

        // 行为类不应在预设中
        assertFalse("hapticFeedback 不应在预设中", "haptic_feedback" in presetKeys)
        assertFalse("orientationLock 不应在预设中", "orientation_lock" in presetKeys)
        assertFalse("gestureConfig 不应在预设中", "gesture_config" in presetKeys)

        // TTS 不应在预设中
        assertFalse("ttsSpeed 不应在预设中", "tts_speed" in presetKeys)
        assertFalse("ttsPitch 不应在预设中", "tts_pitch" in presetKeys)

        // 翻页动画不应在预设中
        assertFalse("pageAnimType 不应在预设中", "page_anim_type" in presetKeys)
        assertFalse("pageAnimSpeed 不应在预设中", "page_anim_speed" in presetKeys)

        // 护眼不应在预设中
        assertFalse("eyeCareReminderInterval 不应在预设中", "eye_care_reminder_interval" in presetKeys)
    }

    // T-E.6b: 预设包含所有 Layout + Style + Chrome 类字段
    @Test
    fun presetFields_includeLayoutStyleAndChrome() {
        val presetKeys = ReaderSettingRegistry.presetFields().map { it.key }.toSet()

        // Layout 核心字段应在预设中
        assertTrue("fontSize 应在预设中", "font_size" in presetKeys)
        assertTrue("lineSpacing 应在预设中", "line_spacing" in presetKeys)

        // Style 核心字段应在预设中
        assertTrue("readingFont 应在预设中", "reading_font" in presetKeys)
        assertTrue("bionicReading 应在预设中", "bionic_reading" in presetKeys)

        // Chrome 核心字段应在预设中
        assertTrue("progressStyle 应在预设中", "progress_style" in presetKeys)
        assertTrue("headerFooterAlpha 应在预设中", "header_footer_alpha" in presetKeys)
    }

    // T-E.7: 设置变更 → 正确的 InvalidationScope（参数化行为验证）
    @Test
    fun settingChange_producesCorrectScope_reflowSettings() {
        for (def in ReaderSettingRegistry.byScope(InvalidationScope.REFLOW)) {
            assertEquals(
                "${def.key} 应为 REFLOW scope",
                InvalidationScope.REFLOW, def.scope
            )
        }
    }

    @Test
    fun settingChange_producesCorrectScope_viewInvalidateSettings() {
        for (def in ReaderSettingRegistry.byScope(InvalidationScope.VIEW_INVALIDATE)) {
            assertEquals(
                "${def.key} 应为 VIEW_INVALIDATE scope",
                InvalidationScope.VIEW_INVALIDATE, def.scope
            )
            assertFalse(
                "${def.key} (VIEW_INVALIDATE) 不应在预设中",
                def.includeInPreset
            )
        }
    }

    @Test
    fun settingChange_producesCorrectScope_noneSettings() {
        for (def in ReaderSettingRegistry.byScope(InvalidationScope.NONE)) {
            assertEquals(
                "${def.key} 应为 NONE scope",
                InvalidationScope.NONE, def.scope
            )
            assertFalse(
                "${def.key} (NONE) 不应在预设中",
                def.includeInPreset
            )
        }
    }

    // T-E.7b: 参数化行为验证 — 修改具体字段值后 registryBasedScopes() 返回正确 scope
    @Test
    fun settingChange_registryBasedScopes_reflowFieldTriggersReflow() {
        val base = ReaderPreferences()
        val changed = base.copy(fontSize = 24f)
        val scopes = ReaderRenderDiffCalculator.registryBasedScopes(base, changed)
        assertTrue(
            "fontSize 变更应触发 REFLOW scope",
            scopes.contains(InvalidationScope.REFLOW)
        )
    }

    @Test
    fun settingChange_registryBasedScopes_contentFieldTriggersContent() {
        val base = ReaderPreferences()
        val changed = base.copy(readingFont = "serif")
        val scopes = ReaderRenderDiffCalculator.registryBasedScopes(base, changed)
        assertTrue(
            "readingFont 变更应触发 CONTENT scope",
            scopes.contains(InvalidationScope.CONTENT)
        )
    }

    @Test
    fun settingChange_registryBasedScopes_viewInvalidateFieldProducesEmptyScopes() {
        val base = ReaderPreferences()
        val changed = base.copy(colorTemperature = 4000f)
        val scopes = ReaderRenderDiffCalculator.registryBasedScopes(base, changed)
        assertFalse(
            "colorTemperature (VIEW_INVALIDATE) 变更不应产生 recorder scope",
            scopes.contains(InvalidationScope.REFLOW) ||
                scopes.contains(InvalidationScope.CONTENT) ||
                scopes.contains(InvalidationScope.SHELL) ||
                scopes.contains(InvalidationScope.OVERLAY)
        )
    }

    @Test
    fun settingChange_registryBasedScopes_noneFieldProducesEmptyScopes() {
        val base = ReaderPreferences()
        val changed = base.copy(hapticFeedback = true)
        val scopes = ReaderRenderDiffCalculator.registryBasedScopes(base, changed)
        assertTrue(
            "hapticFeedback (NONE) 变更不应产生任何 scope",
            scopes.isEmpty()
        )
    }

    @Test
    fun settingChange_registryBasedScopes_multipleFieldsProduceUnionOfScopes() {
        val base = ReaderPreferences()
        val changed = base.copy(fontSize = 24f, readingFont = "serif")
        val scopes = ReaderRenderDiffCalculator.registryBasedScopes(base, changed)
        assertTrue("应包含 REFLOW (fontSize)", scopes.contains(InvalidationScope.REFLOW))
        assertTrue("应包含 CONTENT (readingFont)", scopes.contains(InvalidationScope.CONTENT))
    }

    // T-E.4b: Registry 驱动的四层分组验证
    @Test
    fun tierAlignment_overlayMatchesRegistry() {
        val mismatches = validateTierAlignment()
        assertTrue(
            "四层 StateFlow 字段归属与 Registry 不一致: $mismatches",
            mismatches.isEmpty()
        )
    }
}
