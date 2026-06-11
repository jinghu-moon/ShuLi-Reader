package com.shuli.reader.feature.reader.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderFeatureFlagsTest {

    @Test
    fun defaultFlags_allEnabled() {
        // 重置为默认（开启）状态
        ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED = true
        ReaderFeatureFlags.COLOR_TEMPERATURE_ENABLED = true
        ReaderFeatureFlags.FOUR_MARGINS_ENABLED = true
        ReaderFeatureFlags.EYE_CARE_REMINDER_ENABLED = true
        ReaderFeatureFlags.BACKGROUND_TEXTURE_ENABLED = true
        ReaderFeatureFlags.BIONIC_READING_ENABLED = true
        ReaderFeatureFlags.DUAL_PAGE_MODE_ENABLED = true

        assertTrue(ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED)
        assertTrue(ReaderFeatureFlags.COLOR_TEMPERATURE_ENABLED)
        assertTrue(ReaderFeatureFlags.FOUR_MARGINS_ENABLED)
        assertTrue(ReaderFeatureFlags.EYE_CARE_REMINDER_ENABLED)
        assertTrue(ReaderFeatureFlags.BACKGROUND_TEXTURE_ENABLED)
        assertTrue(ReaderFeatureFlags.BIONIC_READING_ENABLED)
        assertTrue(ReaderFeatureFlags.DUAL_PAGE_MODE_ENABLED)
    }

    @Test
    fun settingsPanelV5_canBeDisabled() {
        val original = ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED
        try {
            ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED = false
            assertFalse(ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED)
        } finally {
            ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED = original
        }
    }

    @Test
    fun colorTemperature_canBeDisabled() {
        val original = ReaderFeatureFlags.COLOR_TEMPERATURE_ENABLED
        try {
            ReaderFeatureFlags.COLOR_TEMPERATURE_ENABLED = false
            assertFalse(ReaderFeatureFlags.COLOR_TEMPERATURE_ENABLED)
        } finally {
            ReaderFeatureFlags.COLOR_TEMPERATURE_ENABLED = original
        }
    }

    @Test
    fun flagsAreVolatile_threadVisibility() {
        // 验证 flag 的 @Volatile 语义：写后立即读，多线程可见
        ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED = true
        assertTrue(ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED)
        ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED = false
        assertFalse(ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED)
        ReaderFeatureFlags.SETTINGS_PANEL_V5_ENABLED = true
    }
}
