package com.shuli.reader.feature.reader.render

import com.shuli.reader.feature.reader.settings.ReaderSettingRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTextureTest {

    // T-3.6.1: backgroundTexture = null 时不绘制纹理
    @Test
    fun textureConfig_null_shouldNotDraw() {
        val config = BackgroundTextureConfig.fromKey(null)
        assertFalse(config.shouldDraw)
    }

    // T-3.6.2: backgroundTexture = "builtin:kraft" 时加载纹理
    @Test
    fun textureConfig_kraft_shouldDraw() {
        val config = BackgroundTextureConfig.fromKey("builtin:kraft")
        assertTrue(config.shouldDraw)
        assertEquals("builtin:kraft", config.textureKey)
    }

    // T-3.6.3: shader 使用 REPEAT 模式（配置验证）
    @Test
    fun textureConfig_tileModeIsRepeat() {
        val config = BackgroundTextureConfig.fromKey("builtin:kraft")
        assertEquals(TileMode.REPEAT, config.tileModeX)
        assertEquals(TileMode.REPEAT, config.tileModeY)
    }

    // T-3.6.4: 纹理 alpha 默认 0.12
    @Test
    fun textureConfig_defaultAlpha() {
        val config = BackgroundTextureConfig.fromKey("builtin:kraft")
        assertEquals(0.12f, config.alpha, 0.001f)
    }

    // T-3.6.5: 纹理变化时重建 shader（key 变化检测）
    @Test
    fun textureConfig_differentKeys_notEqual() {
        val config1 = BackgroundTextureConfig.fromKey("builtin:kraft")
        val config2 = BackgroundTextureConfig.fromKey("builtin:linen")
        assertTrue(config1.shouldRebuild(config2))
    }

    @Test
    fun textureConfig_sameKeys_equal() {
        val config1 = BackgroundTextureConfig.fromKey("builtin:kraft")
        val config2 = BackgroundTextureConfig.fromKey("builtin:kraft")
        assertFalse(config1.shouldRebuild(config2))
    }

    // T-3.6.6: Registry 注册 background_texture 为 SHELL scope
    @Test
    fun registry_backgroundTexture_hasShellScope() {
        val def = ReaderSettingRegistry.all.first { it.key == "background_texture" }
        assertEquals(
            InvalidationScope.SHELL,
            def.scope,
        )
    }

    @Test
    fun registry_backgroundTexture_defaultIsNull() {
        val default = ReaderSettingRegistry.getDefault<String?>("background_texture")
        assertNull(default)
    }
}

enum class TileMode { CLAMP, REPEAT, MIRROR }

/**
 * 背景纹理渲染配置。
 */
data class BackgroundTextureConfig(
    val textureKey: String?,
    val alpha: Float = 0.12f,
    val tileModeX: TileMode = TileMode.REPEAT,
    val tileModeY: TileMode = TileMode.REPEAT,
) {
    val shouldDraw: Boolean get() = textureKey != null

    fun shouldRebuild(other: BackgroundTextureConfig): Boolean =
        textureKey != other.textureKey

    companion object {
        fun fromKey(key: String?): BackgroundTextureConfig = BackgroundTextureConfig(
            textureKey = key,
        )
    }
}
