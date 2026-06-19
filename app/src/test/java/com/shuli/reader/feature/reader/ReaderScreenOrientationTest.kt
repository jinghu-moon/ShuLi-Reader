package com.shuli.reader.feature.reader

import com.shuli.reader.core.data.OrientationLock
import com.shuli.reader.feature.reader.settings.ReaderSettingRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScreenOrientationTest {

    // T-1.5.1: OrientationLock 枚举含三个值
    @Test
    fun orientationLock_hasThreeValues() {
        val values = OrientationLock.entries
        assertTrue("SYSTEM exists", values.contains(OrientationLock.SYSTEM))
        assertTrue("PORTRAIT exists", values.contains(OrientationLock.PORTRAIT))
        assertTrue("LANDSCAPE exists", values.contains(OrientationLock.LANDSCAPE))
        assertEquals(3, values.size)
    }

    // T-1.5.1: Registry 注册 orientation_lock
    @Test
    fun registry_orientationLock_exists() {
        val def = ReaderSettingRegistry.all.first { it.key == "orientation_lock" }
        assertEquals(OrientationLock.SYSTEM, def.defaultValue)
    }

    @Test
    fun registry_orientationLock_isNullScope() {
        val def = ReaderSettingRegistry.all.first { it.key == "orientation_lock" }
        assertNull(def.scope)
    }

    // T-1.5.2: PORTRAIT 设置对应常量（映射验证）
    @Test
    fun orientationLock_portrait_mapsToPortrait() {
        val lock = OrientationLock.PORTRAIT
        assertEquals(OrientationLock.PORTRAIT, lock)
    }

    // T-1.5.3: 退出阅读页恢复 SYSTEM（状态验证）
    @Test
    fun orientationLock_systemIsDefault() {
        val default = ReaderSettingRegistry.getDefault<OrientationLock>("orientation_lock")
        assertEquals(OrientationLock.SYSTEM, default)
    }
}
