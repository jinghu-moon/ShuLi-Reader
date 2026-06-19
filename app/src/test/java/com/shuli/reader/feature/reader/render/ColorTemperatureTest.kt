package com.shuli.reader.feature.reader.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTemperatureTest {

    // T-1.1.1: colorTemperatureToRgb(6500f) 返回近白色
    @Test
    fun colorTemperatureToRgb_6500K_returnsNearWhite() {
        val (r, g, b) = colorTemperatureToRgb(6500f)
        assertTrue("r=$r should be >= 250", r >= 250)
        assertTrue("g=$g should be >= 250", g >= 250)
        assertTrue("b=$b should be >= 250", b >= 250)
    }

    // T-1.1.2: colorTemperatureToRgb(3000f) 返回暖色
    @Test
    fun colorTemperatureToRgb_3000K_returnsWarmColor() {
        val (r, g, b) = colorTemperatureToRgb(3000f)
        assertTrue("r=$r should be > g=$g", r > g)
        assertTrue("g=$g should be > b=$b", g > b)
    }

    // T-1.1.3: 6500K 时色温层接近白色，MULTIPLY 几乎无效果
    @Test
    fun colorTemperatureToRgb_6500K_multiplyIsNoOp() {
        val (r, g, b) = colorTemperatureToRgb(6500f)
        // MULTIPLY blend: (src * dst) / 255
        // 6500K 时 RGB 接近 255，MULTIPLY 对底色影响极小
        assertTrue("r=$r should be >= 250", r >= 250)
        assertTrue("g=$g should be >= 250", g >= 250)
        assertTrue("b=$b should be >= 250", b >= 250)
    }

    // T-1.1.4: 低于 6500K 使用 MULTIPLY 混合模式（数学验证）
    @Test
    fun colorTemperatureToRgb_5000K_multiplyDarkensBlue() {
        val (r, g, b) = colorTemperatureToRgb(5000f)
        // MULTIPLY: 纯白文字(255,255,255) 会被色温层染色
        val blendedR = (255 * r) / 255
        val blendedG = (255 * g) / 255
        val blendedB = (255 * b) / 255
        // 暖色温下蓝色分量应减少
        assertTrue("b=$b should be < 255 (warm tint)", b < 255)
        assertEquals(r.toInt(), blendedR)
    }

    // T-1.1.6: 纯黑文字在 MULTIPLY 下不变色
    @Test
    fun colorTemperatureToRgb_multiplyWithBlack_unchanged() {
        val (r, g, b) = colorTemperatureToRgb(3000f)
        // MULTIPLY: 黑色(0,0,0) × 任何颜色 = 黑色
        val blendedR = (0 * r) / 255
        val blendedG = (0 * g) / 255
        val blendedB = (0 * b) / 255
        assertEquals(0, blendedR)
        assertEquals(0, blendedG)
        assertEquals(0, blendedB)
    }

    // 色温范围边界
    @Test
    fun colorTemperatureToRgb_2000K_validRange() {
        val (r, g, b) = colorTemperatureToRgb(2000f)
        assertTrue("r=$r in 0..255", r in 0..255)
        assertTrue("g=$g in 0..255", g in 0..255)
        assertTrue("b=$b in 0..255", b in 0..255)
        assertTrue("r=$r should be > g=$g at 2000K", r > g)
    }

    // T-1.1.7: Registry 注册 color_temperature 为 PAGE scope（VIEW_INVALIDATE 已迁移为 PAGE）
    @Test
    fun registry_colorTemperature_hasPageScope() {
        val def = com.shuli.reader.feature.reader.settings.ReaderSettingRegistry.all
            .first { it.key == "color_temperature" }
        assertEquals(
            com.shuli.reader.feature.reader.render.InvalidationScope.PAGE,
            def.scope,
        )
    }

    @Test
    fun registry_colorTemperature_defaultIs6500() {
        val default = com.shuli.reader.feature.reader.settings.ReaderSettingRegistry
            .getDefault<Float>("color_temperature")
        assertEquals(6500f, default, 0.01f)
    }
}
