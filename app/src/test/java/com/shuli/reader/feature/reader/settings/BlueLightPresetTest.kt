package com.shuli.reader.feature.reader.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BlueLightPresetTest {

    // T-1.2.1: 蓝光开关将色温设为 3400K
    @Test
    fun toggleBlueLightFilter_on_setsTemperatureTo3400() {
        val result = BlueLightPreset.toggle(5500f)
        assertEquals(3400f, result, 0.01f)
    }

    @Test
    fun toggleBlueLightFilter_fromDefault_setsTo3400() {
        val result = BlueLightPreset.toggle(6500f)
        assertEquals(3400f, result, 0.01f)
    }

    // T-1.2.2: 蓝光关闭恢复上次色温
    @Test
    fun toggleBlueLightFilter_off_restoresLastTemperature() {
        val lastManual = 5500f
        val result = BlueLightPreset.toggle(3400f, lastManual)
        assertEquals(5500f, result, 0.01f)
    }

    @Test
    fun toggleBlueLightFilter_off_restoresDefaultWhenNoManual() {
        val result = BlueLightPreset.toggle(3400f)
        assertEquals(6500f, result, 0.01f)
    }

    @Test
    fun toggleBlueLightFilter_isActive_below3400() {
        assert(BlueLightPreset.isActive(3400f))
        assert(BlueLightPreset.isActive(3000f))
    }

    @Test
    fun toggleBlueLightFilter_isNotActive_above3400() {
        assert(!BlueLightPreset.isActive(5500f))
        assert(!BlueLightPreset.isActive(6500f))
    }

    // T-1.2.3: Registry 中无 blueLightFilter 独立定义
    @Test
    fun registry_noBlueLightFilterKey() {
        val found = ReaderSettingRegistry.all.any { it.key == "blue_light_filter" }
        assert(!found) { "blue_light_filter should not be in Registry" }
    }
}
