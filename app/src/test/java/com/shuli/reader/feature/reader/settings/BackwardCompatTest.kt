package com.shuli.reader.feature.reader.settings

import com.shuli.reader.core.data.ReaderPreferences
import com.shuli.reader.core.data.toLayoutConfig
import com.shuli.reader.core.database.entity.BookReaderPrefsOverrides
import com.shuli.reader.core.reader.model.BoxInsetsDp
import com.shuli.reader.core.reader.layout.ReaderLayoutInput
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 向后兼容性测试（附录 C：T-C.1 ~ T-C.5）。
 *
 * 验证新版本代码能正确处理旧版数据，不引入破坏性变更。
 */
class BackwardCompatTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = false
    }

    // T-C.1: 旧预设加载（缺少新字段）不崩溃
    @Test
    fun oldPreset_missingNewFields_loadsWithDefaults() {
        // 模拟 v4.0 格式的预设 JSON（缺少 bionicReading, paragraphDivider 等新字段）
        val oldPresetJson = """
            {
                "fontSize": 20.0,
                "lineSpacing": 1.8,
                "readingFont": "serif",
                "marginHorizontal": 30.0,
                "marginVertical": 50.0
            }
        """.trimIndent()

        val snapshot = PresetSnapshot.fromJson(oldPresetJson)

        assertNotNull(snapshot)
        assertEquals(20.0f, snapshot.fontSize, 0.001f)
        assertEquals(1.8f, snapshot.lineSpacing, 0.001f)
        assertEquals("serif", snapshot.readingFont)
        // 新字段使用 PresetSnapshot 的默认值
        assertEquals(false, snapshot.bionicReading)
        assertEquals(false, snapshot.paragraphDivider)
    }

    // T-C.2: 旧 BookReaderPrefsOverrides JSON（含未知字段）反序列化成功
    @Test
    fun oldBookOverrides_withUnknownFields_deserializesSuccessfully() {
        // 模拟旧版 JSON，含已废弃的 blueLightFilter 和不存在的字段
        val oldJson = """
            {
                "fontSize": 22.0,
                "blueLightFilter": true,
                "deprecatedField": "garbage",
                "readingFont": "harmony",
                "anotherUnknown": [1, 2, 3]
            }
        """.trimIndent()

        val overrides = json.decodeFromString(BookReaderPrefsOverrides.serializer(), oldJson)

        assertNotNull(overrides)
        assertEquals(22.0f, overrides.fontSize)
        assertEquals("harmony", overrides.readingFont)
    }

    // T-C.3: bodyBox 均匀四边距正确映射到 bodyInsets
    @Test
    fun uniformBodyBox_mapsToEqualBodyInsets() {
        val prefs = ReaderPreferences(
            bodyBox = BoxInsetsDp(top = 48f, bottom = 48f, left = 24f, right = 24f),
        )

        val pageSize = com.shuli.reader.core.reader.model.PageSize(width = 1080, height = 1920)
        val density = 3f
        val config = prefs.toLayoutConfig(pageSize, density)

        assertEquals(48f * density, config.bodyInsets.top, 0.001f)
        assertEquals(48f * density, config.bodyInsets.bottom, 0.001f)
        assertEquals(24f * density, config.bodyInsets.left, 0.001f)
        assertEquals(24f * density, config.bodyInsets.right, 0.001f)
    }

    // T-C.3b: 非均匀 bodyBox 正确映射
    @Test
    fun nonUniformBodyBox_mapsToIndependentInsets() {
        val prefs = ReaderPreferences(
            bodyBox = BoxInsetsDp(top = 60f, bottom = 30f, left = 40f, right = 20f),
        )

        val pageSize = com.shuli.reader.core.reader.model.PageSize(width = 1080, height = 1920)
        val density = 3f
        val config = prefs.toLayoutConfig(pageSize, density)

        assertEquals(60f * density, config.bodyInsets.top, 0.001f)
        assertEquals(30f * density, config.bodyInsets.bottom, 0.001f)
        assertEquals(40f * density, config.bodyInsets.left, 0.001f)
        assertEquals(20f * density, config.bodyInsets.right, 0.001f)
    }

    // T-C.4: LAYOUT_ALGORITHM_VERSION = 2 使旧缓存失效
    @Test
    fun layoutAlgorithmVersion_is2_invalidatesOldCache() {
        assertEquals(2, ReaderLayoutInput.LAYOUT_ALGORITHM_VERSION)
    }

    // T-C.5: 死代码删除后现有测试全部通过
    // （由 CI 全量测试套件验证，此测试断言死代码类不存在）
    @Test
    fun deadCode_removed_noResolverOrEntityInFeaturePackage() {
        // 验证 feature.reader.settings 包中不存在旧的 BookReaderPrefsEntity
        val featureEntityClass = try {
            Class.forName("com.shuli.reader.feature.reader.settings.BookReaderPrefsEntity")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        assertTrue("feature.reader.settings.BookReaderPrefsEntity 应为死代码已删除", !featureEntityClass)

        // 验证旧的 ReaderSettingsResolver（feature.reader.settings 包）已删除
        val oldResolverClass = try {
            Class.forName("com.shuli.reader.feature.reader.settings.ReaderSettingsResolver")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        assertTrue("feature.reader.settings.ReaderSettingsResolver 应为死代码已删除", !oldResolverClass)
    }
}
